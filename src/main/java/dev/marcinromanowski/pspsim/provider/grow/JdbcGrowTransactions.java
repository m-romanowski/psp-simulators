package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.Ledgers;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.money.OperationOf;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeLog;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeTable;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class JdbcGrowTransactions implements GrowTransactions {

  static final KeyedExchangeTable EXCHANGES =
      KeyedExchangeTable.underANamedKey("grow_keyed_exchange", "merchant_id", true);

  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper json;
  private final KeyedExchangeLog exchanges;

  JdbcGrowTransactions(
      JdbcClient jdbc,
      TransactionTemplate transactions,
      ObjectMapper json,
      KeyedExchangeLog exchanges) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.json = json;
    this.exchanges = exchanges;
  }

  @Override
  public boolean insertIfAbsent(GrowTransaction transaction) {
    return Boolean.TRUE.equals(transactions.execute(status -> insertInOneTransaction(transaction)));
  }

  private boolean insertInOneTransaction(GrowTransaction transaction) {
    if (!GrowObjectId.TRANSACTION.equals(
        theKindTheClaimHolds(transaction.testRunId(), transaction.transactionId()))) {
      return false;
    }
    int inserted =
        jdbc.sql(
                """
                insert into grow_transaction
                    (test_run_id, kind, transaction_id, transaction_token, merchant_id, process_id,
                     refunded_transaction_id, asmachta, payment_date, description, payer_full_name,
                     payer_phone, payer_email, card_suffix, card_type, card_type_code, card_brand,
                     card_brand_code, card_exp, amount, origin, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, transaction_id) do nothing
                """)
            .param(transaction.testRunId().value())
            .param(transaction.kind().name())
            .param(transaction.transactionId())
            .param(transaction.transactionToken())
            .param(transaction.merchantId())
            .param(transaction.processId())
            .param(transaction.refundedTransactionId())
            .param(transaction.asmachta())
            .param(transaction.paymentDate())
            .param(transaction.description())
            .param(transaction.payerFullName())
            .param(transaction.payerPhone())
            .param(transaction.payerEmail())
            .param(transaction.card().suffix())
            .param(transaction.card().type())
            .param(transaction.card().typeCode())
            .param(transaction.card().brand())
            .param(transaction.card().brandCode())
            .param(transaction.card().expiry())
            .param(transaction.amount().value())
            .param(transaction.origin().name())
            .param(transaction.createdAt().atOffset(ZoneOffset.UTC))
            .param(transaction.updatedAt().atOffset(ZoneOffset.UTC))
            .update();
    if (inserted != 1) {
      return false;
    }
    for (MonetaryOperation operation : transaction.ledger().operations()) {
      insert(transaction, operation);
    }
    return true;
  }

  @Override
  public boolean claimTransactionId(TestRunId testRunId, String transactionId) {
    return jdbc.sql(
                """
                insert into grow_object_id_claim (test_run_id, object_id, kind)
                values (?, ?, ?)
                on conflict (test_run_id, object_id) do nothing
                """)
            .params(testRunId.value(), transactionId, GrowObjectId.TRANSACTION)
            .update()
        == 1;
  }

  private String theKindTheClaimHolds(TestRunId testRunId, String transactionId) {
    return jdbc.sql(
            """
            insert into grow_object_id_claim (test_run_id, object_id, kind)
            values (?, ?, ?)
            on conflict (test_run_id, object_id)
                do update set kind = grow_object_id_claim.kind
            returning kind
            """)
        .params(testRunId.value(), transactionId, GrowObjectId.TRANSACTION)
        .query(String.class)
        .single();
  }

  @Override
  public Charged charge(GrowTransaction transaction, GrowStoredCardCharge submission) {
    return Objects.requireNonNull(
        transactions.execute(status -> chargeInOneTransaction(status, transaction, submission)),
        "the unit of work answered nothing where its callback answers a Charged on every path");
  }

  private Charged chargeInOneTransaction(
      TransactionStatus status, GrowTransaction transaction, GrowStoredCardCharge submission) {
    Submitted submitted = submit(submission);
    if (submitted != Submitted.APPLIED) {
      return submitted == Submitted.GROUP_ALREADY_CHARGED
          ? Charged.GROUP_ALREADY_CHARGED
          : Charged.UNIQUE_IDENTIFIER_TAKEN;
    }
    if (!insertInOneTransaction(transaction)) {
      status.setRollbackOnly();
      return Charged.TRANSACTION_ID_TAKEN;
    }
    return Charged.APPLIED;
  }

  @Override
  public Submitted submit(GrowStoredCardCharge submission) {
    String group = submission.groupIdentifier();
    if (group != null && aChargedPaymentCarries(submission, group)) {
      return Submitted.GROUP_ALREADY_CHARGED;
    }
    int claimed =
        jdbc.sql(
                """
                insert into grow_stored_card_charge
                    (test_run_id, merchant_id, unique_identifier, group_identifier,
                     transaction_id, custom_fields, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, merchant_id, unique_identifier) do nothing
                """)
            .param(submission.testRunId().value())
            .param(submission.merchantId())
            .param(submission.uniqueIdentifier())
            .param(submission.groupIdentifier())
            .param(submission.transactionId())
            .param(json.writeValueAsString(submission.customFields()))
            .param(submission.createdAt().atOffset(ZoneOffset.UTC))
            .update();
    return claimed == 1 ? Submitted.APPLIED : Submitted.UNIQUE_IDENTIFIER_TAKEN;
  }

  private boolean aChargedPaymentCarries(GrowStoredCardCharge submission, String group) {
    return !jdbc.sql(
            """
            select unique_identifier from grow_stored_card_charge
            where test_run_id = ? and merchant_id = ? and group_identifier = ?
              and transaction_id is not null
            """)
        .params(submission.testRunId().value(), submission.merchantId(), group)
        .query(String.class)
        .list()
        .isEmpty();
  }

  @Override
  public void observed(KeyedObservation observation) {
    exchanges.record(observation);
  }

  @Override
  public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
    List<RecordedKeyedRequest> rows =
        jdbc.sql(
                """
                select e.merchant_id, e.key_name, e.key_value, e.arrival, e.seq, e.operation,
                       e.fingerprint, e.verdict, e.http_status, e.content_type, e.body,
                       e.provider_object_id, e.state_changed, e.at
                from grow_keyed_exchange e
                join (select merchant_id, key_name, key_value, arrival
                      from grow_keyed_exchange
                      where test_run_id = ? and seq = 0 and arrival < ?
                      order by arrival desc
                      limit ?) first
                  on first.merchant_id = e.merchant_id
                 and first.key_name = e.key_name
                 and first.key_value = e.key_value
                where e.test_run_id = ?
                order by first.arrival desc, e.seq
                """)
            .param(query.testRunId().value())
            .param(query.after())
            .param(query.limit())
            .param(query.testRunId().value())
            .query(JdbcGrowTransactions::toRecordedRequest)
            .list();
    return KeyedExchanges.pageOf(GrowKeyMechanisms.MECHANISMS, query.testRunId(), rows);
  }

  private static RecordedKeyedRequest toRecordedRequest(ResultSet rs, int row) throws SQLException {
    return new RecordedKeyedRequest(
        rs.getString("merchant_id"),
        rs.getString("key_name"),
        rs.getString("key_value"),
        rs.getLong("arrival"),
        rs.getInt("seq"),
        ExchangeVerdict.valueOf(rs.getString("verdict")),
        rs.getString("fingerprint"),
        rs.getString("operation"),
        rs.getObject("http_status", Integer.class),
        rs.getString("content_type"),
        rs.getString("body"),
        rs.getString("provider_object_id"),
        rs.getBoolean("state_changed"),
        null,
        rs.getObject("at", OffsetDateTime.class).toInstant());
  }

  @Override
  public Optional<GrowStoredCardCharge> submissionOf(TestRunId testRunId, String transactionId) {
    return jdbc.sql(
            """
            select * from grow_stored_card_charge
            where test_run_id = ? and transaction_id = ?
            """)
        .params(testRunId.value(), transactionId)
        .query(this::toSubmission)
        .optional();
  }

  @Override
  public Optional<GrowStoredCardCharge> submissionUnder(
      TestRunId testRunId, String merchantId, String uniqueIdentifier) {
    return jdbc.sql(
            """
            select * from grow_stored_card_charge
            where test_run_id = ? and merchant_id = ? and unique_identifier = ?
            """)
        .params(testRunId.value(), merchantId, uniqueIdentifier)
        .query(this::toSubmission)
        .optional();
  }

  private GrowStoredCardCharge toSubmission(ResultSet rs, int row) throws SQLException {
    return new GrowStoredCardCharge(
        TestRunId.of(rs.getString("test_run_id")),
        rs.getString("merchant_id"),
        rs.getString("unique_identifier"),
        rs.getString("group_identifier"),
        rs.getString("transaction_id"),
        json.readValue(rs.getString("custom_fields"), STRING_MAP),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  @Override
  public Optional<GrowTransaction> find(
      TestRunId testRunId, String merchantId, String transactionId, String transactionToken) {
    return jdbc.sql(
            """
            select * from grow_transaction
            where test_run_id = ? and merchant_id = ? and transaction_id = ?
              and transaction_token = ?
            """)
        .params(testRunId.value(), merchantId, transactionId, transactionToken)
        .query(this::toTransaction)
        .optional()
        .map(this::withItsOperations);
  }

  @Override
  public Optional<GrowTransaction> findByTransactionId(TestRunId testRunId, String transactionId) {
    return jdbc.sql("select * from grow_transaction where test_run_id = ? and transaction_id = ?")
        .params(testRunId.value(), transactionId)
        .query(this::toTransaction)
        .optional()
        .map(this::withItsOperations);
  }

  @Override
  public List<GrowTransaction> allByTransactionId(
      TestRunId testRunId, List<String> transactionIds) {
    List<GrowTransaction> held =
        jdbc.sql(
                """
                select * from grow_transaction
                where test_run_id = ? and transaction_id = any(?)
                order by arrival desc
                """)
            .params(testRunId.value(), transactionIds.toArray(String[]::new))
            .query(this::toTransaction)
            .list();
    return withTheirOperations(testRunId, held);
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String transactionId) {
    return jdbc.sql(
            "select arrival from grow_transaction where test_run_id = ? and transaction_id = ?")
        .params(testRunId.value(), transactionId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, transactionId));
  }

  @Override
  public List<ArrivalOf<GrowTransaction>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    List<ArrivalOf<GrowTransaction>> found =
        jdbc.sql(
                """
                select * from grow_transaction where test_run_id = ? and arrival < ?
                order by arrival desc limit ?
                """)
            .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
            .query(this::toArrivedTransaction)
            .list();
    Map<String, Ledger> ledgers =
        theLedgersOf(
            testRunId, found.stream().map(arrived -> arrived.row().transactionId()).toList());
    List<ArrivalOf<GrowTransaction>> withLedgers = new ArrayList<>();
    for (ArrivalOf<GrowTransaction> arrived : found) {
      GrowTransaction transaction = arrived.row();
      withLedgers.add(
          new ArrivalOf<>(
              arrived.arrival(),
              with(transaction, Ledgers.theLedgerOf(ledgers, transaction.transactionId()))));
    }
    return List.copyOf(withLedgers);
  }

  private ArrivalOf<GrowTransaction> toArrivedTransaction(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toTransaction(rs, row));
  }

  @Override
  public LedgerClaim append(GrowTransaction transaction, MonetaryOperation operation) {
    return Objects.requireNonNull(
        transactions.execute(status -> claim(transaction, () -> insert(transaction, operation))),
        "the unit of work answered nothing where its callback answers a LedgerClaim on every path");
  }

  private LedgerClaim claim(GrowTransaction transaction, IntSupplier statement) {
    return LedgerClaim.of(() -> stillStores(transaction), statement, () -> touch(transaction));
  }

  private boolean stillStores(GrowTransaction transaction) {
    return jdbc.sql("select 1 from grow_transaction where test_run_id = ? and transaction_id = ?")
        .params(transaction.testRunId().value(), transaction.transactionId())
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  @Override
  public Refunded refund(GrowTransaction refunded, MonetaryOperation refund, GrowTransaction row) {
    return Objects.requireNonNull(
        transactions.execute(status -> refundInOneTransaction(status, refunded, refund, row)),
        "the unit of work answered nothing where its callback answers a Refunded on every path");
  }

  private Refunded refundInOneTransaction(
      TransactionStatus status,
      GrowTransaction refunded,
      MonetaryOperation refund,
      GrowTransaction row) {
    if (refunded.transactionId().equals(row.transactionId())) {
      return Refunded.ID_TAKEN;
    }
    return switch (claim(refunded, () -> insert(refunded, refund))) {
      case CLAIM_TAKEN -> Refunded.CLAIM_TAKEN;
      case OBJECT_GONE -> Refunded.OBJECT_GONE;
      case APPLIED -> theRefundItDrewAnIdFor(status, row);
    };
  }

  private Refunded theRefundItDrewAnIdFor(TransactionStatus status, GrowTransaction row) {
    if (!insertInOneTransaction(row)) {
      status.setRollbackOnly();
      return Refunded.ID_TAKEN;
    }
    return Refunded.APPLIED;
  }

  private void touch(GrowTransaction transaction) {
    jdbc.sql(
            "update grow_transaction set updated_at = ? where test_run_id = ? and transaction_id = ?")
        .param(transaction.updatedAt().atOffset(ZoneOffset.UTC))
        .param(transaction.testRunId().value())
        .param(transaction.transactionId())
        .update();
  }

  private int insert(GrowTransaction transaction, MonetaryOperation operation) {
    return jdbc.sql(
            """
            insert into grow_transaction_operation
                (test_run_id, transaction_id, seq, kind, amount, closes_authorization,
                 created_object_id, native_status, normalized_status, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, transaction_id, seq) do nothing
            """)
        .param(transaction.testRunId().value())
        .param(transaction.transactionId())
        .param(operation.sequence())
        .param(operation.kind().name())
        .param(operation.amount().value())
        .param(operation.closesAuthorization())
        .param(operation.createdObjectId())
        .param(operation.nativeStatus())
        .param(operation.normalizedStatus().name())
        .param(operation.at().atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from grow_keyed_exchange where test_run_id = ?")
        .param(testRunId.value())
        .update();
    jdbc.sql("delete from grow_stored_card_charge where test_run_id = ?")
        .param(testRunId.value())
        .update();
    jdbc.sql("delete from grow_object_id_claim where test_run_id = ? and kind = ?")
        .params(testRunId.value(), GrowObjectId.TRANSACTION)
        .update();
    return jdbc.sql("delete from grow_transaction where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from grow_keyed_exchange").update();
    jdbc.sql("delete from grow_stored_card_charge").update();
    jdbc.sql("delete from grow_object_id_claim where kind = ?")
        .param(GrowObjectId.TRANSACTION)
        .update();
    return jdbc.sql("delete from grow_transaction").update();
  }

  private GrowTransaction withItsOperations(GrowTransaction transaction) {
    Map<String, Ledger> ledgers =
        theLedgersOf(transaction.testRunId(), List.of(transaction.transactionId()));
    return with(transaction, Ledgers.theLedgerOf(ledgers, transaction.transactionId()));
  }

  private List<GrowTransaction> withTheirOperations(
      TestRunId testRunId, List<GrowTransaction> held) {
    Map<String, Ledger> ledgers =
        theLedgersOf(testRunId, held.stream().map(GrowTransaction::transactionId).toList());
    return held.stream()
        .map(
            transaction ->
                with(transaction, Ledgers.theLedgerOf(ledgers, transaction.transactionId())))
        .toList();
  }

  private Map<String, Ledger> theLedgersOf(TestRunId testRunId, List<String> transactionIds) {
    return Ledgers.keyedByObject(
        jdbc.sql(
                """
                select * from grow_transaction_operation
                where test_run_id = ? and transaction_id = any(?)
                order by transaction_id, seq
                """)
            .params(testRunId.value(), transactionIds.toArray(String[]::new))
            .query(JdbcGrowTransactions::toStoredOperation)
            .list());
  }

  private static OperationOf toStoredOperation(ResultSet rs, int row) throws SQLException {
    return new OperationOf(rs.getString("transaction_id"), toOperation(rs));
  }

  private static GrowTransaction with(GrowTransaction transaction, Ledger ledger) {
    return new GrowTransaction(
        transaction.testRunId(),
        transaction.kind(),
        transaction.transactionId(),
        transaction.transactionToken(),
        transaction.merchantId(),
        transaction.processId(),
        transaction.refundedTransactionId(),
        transaction.asmachta(),
        transaction.paymentDate(),
        transaction.description(),
        transaction.payerFullName(),
        transaction.payerPhone(),
        transaction.payerEmail(),
        transaction.card(),
        transaction.amount(),
        ledger,
        transaction.origin(),
        transaction.createdAt(),
        transaction.updatedAt());
  }

  private static GrowRecordedCard toCard(ResultSet rs) throws SQLException {
    return new GrowRecordedCard(
        rs.getString("card_suffix"),
        rs.getString("card_type"),
        rs.getString("card_type_code"),
        rs.getString("card_brand"),
        rs.getString("card_brand_code"),
        rs.getString("card_exp"));
  }

  private GrowTransaction toTransaction(ResultSet rs, int row) throws SQLException {
    return new GrowTransaction(
        TestRunId.of(rs.getString("test_run_id")),
        GrowTransactionKind.valueOf(rs.getString("kind")),
        rs.getString("transaction_id"),
        rs.getString("transaction_token"),
        rs.getString("merchant_id"),
        rs.getString("process_id"),
        rs.getString("refunded_transaction_id"),
        rs.getString("asmachta"),
        rs.getString("payment_date"),
        rs.getString("description"),
        rs.getString("payer_full_name"),
        rs.getString("payer_phone"),
        rs.getString("payer_email"),
        toCard(rs),
        Amount.of(rs.getString("amount")),
        Ledger.empty(),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static MonetaryOperation toOperation(ResultSet rs) throws SQLException {
    return new MonetaryOperation(
        rs.getInt("seq"),
        OperationKind.valueOf(rs.getString("kind")),
        Amount.of(rs.getString("amount")),
        rs.getObject("closes_authorization", Boolean.class),
        rs.getString("created_object_id"),
        rs.getString("native_status"),
        NormalizedStatus.valueOf(rs.getString("normalized_status")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
