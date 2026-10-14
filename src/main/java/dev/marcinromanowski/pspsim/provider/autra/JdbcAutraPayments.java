package dev.marcinromanowski.pspsim.provider.autra;

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
import dev.marcinromanowski.pspsim.kernel.spi.SequencedRows;
import dev.marcinromanowski.pspsim.kernel.spi.SequencedTable;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
import org.springframework.transaction.support.TransactionTemplate;

final class JdbcAutraPayments implements AutraPayments {

  static final KeyedExchangeTable EXCHANGES =
      KeyedExchangeTable.underANamedKey("autra_keyed_exchange", "account_id", false);

  private static final String APPENDING_AN_EVENT =
      new SequencedTable("autra_payment_event", List.of("test_run_id", "payment_id"))
          .appendingAtTheNextFreeSequence(
              List.of("event_type", "status", "amount", "error_code", "msg", "at"));

  static final String HELD = "HELD_BEFORE_THE_ACQUIRER_WAS_CALLED";

  static final String HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES =
      "HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES";

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final KeyedExchangeLog exchanges;
  private final SequencedRows sequenced;

  JdbcAutraPayments(
      JdbcClient jdbc,
      TransactionTemplate transactions,
      KeyedExchangeLog exchanges,
      SequencedRows sequenced) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.exchanges = exchanges;
    this.sequenced = sequenced;
  }

  @Override
  public boolean claimPaymentId(TestRunId testRunId, String paymentId) {
    return jdbc.sql(
                """
                insert into autra_payment_id_claim (test_run_id, payment_id)
                values (?, ?)
                on conflict (test_run_id, payment_id) do nothing
                """)
            .params(testRunId.value(), paymentId)
            .update()
        == 1;
  }

  @Override
  public AutraOrderClaim claimOrder(
      TestRunId testRunId, String accountId, String documentId, String orderId, Instant at) {
    int taken =
        jdbc.sql(
                """
                insert into autra_order_claim
                    (test_run_id, document_id, order_id, account_id, claim_state, payment_id, at)
                values (?, ?, ?, ?, ?, null, ?)
                on conflict (test_run_id, document_id, order_id) do nothing
                """)
            .param(testRunId.value())
            .param(documentId)
            .param(orderId)
            .param(accountId)
            .param(HELD)
            .param(at.atOffset(ZoneOffset.UTC))
            .update();
    if (taken == 1) {
      return new AutraOrderClaim.Fresh();
    }
    return jdbc.sql(
            """
            select claim_state, payment_id from autra_order_claim
            where test_run_id = ? and document_id = ? and order_id = ?
            """)
        .params(testRunId.value(), documentId, orderId)
        .query(JdbcAutraPayments::toClaim)
        .optional()
        .orElseGet(AutraOrderClaim.Fresh::new);
  }

  private static AutraOrderClaim toClaim(ResultSet rs, int row) throws SQLException {
    String paymentId = rs.getString("payment_id");
    if (HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES.equals(rs.getString("claim_state"))
        && paymentId != null) {
      return new AutraOrderClaim.AlreadyPreAuthorized(paymentId);
    }
    return new AutraOrderClaim.HeldByARequestStillRunning();
  }

  @Override
  public void releaseOrder(TestRunId testRunId, String documentId, String orderId) {
    jdbc.sql(
            """
            delete from autra_order_claim
            where test_run_id = ? and document_id = ? and order_id = ?
            """)
        .params(testRunId.value(), documentId, orderId)
        .update();
  }

  @Override
  public void settleOrder(
      TestRunId testRunId, String documentId, String orderId, String paymentId) {
    jdbc.sql(
            """
            update autra_order_claim set claim_state = ?, payment_id = ?
            where test_run_id = ? and document_id = ? and order_id = ?
            """)
        .params(
            HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES,
            paymentId,
            testRunId.value(),
            documentId,
            orderId)
        .update();
  }

  @Override
  public boolean insertIfAbsent(AutraPayment payment) {
    return Boolean.TRUE.equals(transactions.execute(status -> insertInOneTransaction(payment)));
  }

  private boolean insertInOneTransaction(AutraPayment payment) {
    int inserted =
        jdbc.sql(
                """
                insert into autra_payment
                    (test_run_id, payment_id, account_id, document_id, order_id, payment_type,
                     transaction_type, amount, currency, installments, native_status,
                     authorization_code, rrn, tracking_number, slug_token, slug_stored_card,
                     hold_released_at, origin, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, payment_id) do nothing
                """)
            .param(payment.testRunId().value())
            .param(payment.paymentId())
            .param(payment.accountId())
            .param(payment.documentId())
            .param(payment.orderId())
            .param(payment.type())
            .param(payment.transactionType())
            .param(payment.amount().value())
            .param(payment.currency())
            .param(payment.installments())
            .param(payment.nativeStatus())
            .param(payment.authorizationCode())
            .param(payment.rrn())
            .param(payment.trackingNumber())
            .param(payment.slugToken())
            .param(payment.slugStoredCard())
            .param(releasedAt(payment))
            .param(payment.origin().name())
            .param(payment.createdAt().atOffset(ZoneOffset.UTC))
            .param(payment.updatedAt().atOffset(ZoneOffset.UTC))
            .update();
    if (inserted != 1) {
      return false;
    }
    claimPaymentId(payment.testRunId(), payment.paymentId());
    for (MonetaryOperation operation : payment.ledger().operations()) {
      insert(payment, operation);
    }
    return true;
  }

  private static @Nullable OffsetDateTime releasedAt(AutraPayment payment) {
    Instant released = payment.holdReleasedAt();
    return released == null ? null : released.atOffset(ZoneOffset.UTC);
  }

  private int insert(AutraPayment payment, MonetaryOperation operation) {
    return jdbc.sql(
            """
            insert into autra_payment_operation
                (test_run_id, payment_id, seq, kind, amount, closes_authorization,
                 created_object_id, native_status, normalized_status, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, payment_id, seq) do nothing
            """)
        .param(payment.testRunId().value())
        .param(payment.paymentId())
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
  public Optional<AutraPayment> findById(TestRunId testRunId, String paymentId) {
    return jdbc.sql("select * from autra_payment where test_run_id = ? and payment_id = ?")
        .params(testRunId.value(), paymentId)
        .query(JdbcAutraPayments::toPayment)
        .optional()
        .map(this::withItsOperations);
  }

  @Override
  public List<AutraPayment> ofMerchant(TestRunId testRunId, String documentId) {
    List<AutraPayment> held =
        jdbc.sql(
                """
                select * from autra_payment
                where test_run_id = ? and document_id = ?
                order by arrival desc
                """)
            .params(testRunId.value(), documentId)
            .query(JdbcAutraPayments::toPayment)
            .list();
    return withTheirLedgers(testRunId, held);
  }

  @Override
  public void recordEvent(AutraPaymentEvent event) {
    sequenced.recorded(
        "the " + event.type() + " event of payment " + event.paymentId(),
        () ->
            jdbc.sql(APPENDING_AN_EVENT)
                .param(event.testRunId().value())
                .param(event.paymentId())
                .param(event.type())
                .param(event.status())
                .param(event.amount().value())
                .param(event.errorCode())
                .param(event.msg())
                .param(event.at().atOffset(ZoneOffset.UTC))
                .param(event.testRunId().value())
                .param(event.paymentId())
                .update());
  }

  @Override
  public List<AutraPaymentEvent> timelineOf(TestRunId testRunId, String paymentId) {
    return jdbc.sql(
            """
            select * from autra_payment_event
            where test_run_id = ? and payment_id = ?
            order by seq
            """)
        .params(testRunId.value(), paymentId)
        .query(JdbcAutraPayments::toEvent)
        .list();
  }

  @Override
  public List<AutraPaymentEvent> everyEventOf(TestRunId testRunId, List<String> paymentIds) {
    return jdbc.sql(
            """
            select * from autra_payment_event
            where test_run_id = ? and payment_id = any(?)
            order by payment_id, seq
            """)
        .params(testRunId.value(), paymentIds.toArray(String[]::new))
        .query(JdbcAutraPayments::toEvent)
        .list();
  }

  @Override
  public List<AutraPayment> allById(TestRunId testRunId, List<String> paymentIds) {
    List<AutraPayment> held =
        jdbc.sql(
                """
                select * from autra_payment
                where test_run_id = ? and payment_id = any(?)
                order by arrival desc
                """)
            .params(testRunId.value(), paymentIds.toArray(String[]::new))
            .query(JdbcAutraPayments::toPayment)
            .list();
    return withTheirLedgers(testRunId, held);
  }

  private List<AutraPayment> withTheirLedgers(TestRunId testRunId, List<AutraPayment> held) {
    Map<String, Ledger> ledgers =
        theLedgersOf(testRunId, held.stream().map(AutraPayment::paymentId).toList());
    return held.stream()
        .map(payment -> with(payment, Ledgers.theLedgerOf(ledgers, payment.paymentId())))
        .toList();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentId) {
    return jdbc.sql("select arrival from autra_payment where test_run_id = ? and payment_id = ?")
        .params(testRunId.value(), paymentId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, paymentId));
  }

  @Override
  public List<ArrivalOf<AutraPayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    List<ArrivalOf<AutraPayment>> found =
        jdbc.sql(
                """
                select * from autra_payment where test_run_id = ? and arrival < ?
                order by arrival desc limit ?
                """)
            .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
            .query(JdbcAutraPayments::toArrivedPayment)
            .list();
    Map<String, Ledger> ledgers =
        theLedgersOf(testRunId, found.stream().map(arrived -> arrived.row().paymentId()).toList());
    List<ArrivalOf<AutraPayment>> withLedgers = new ArrayList<>();
    for (ArrivalOf<AutraPayment> arrived : found) {
      AutraPayment payment = arrived.row();
      withLedgers.add(
          new ArrivalOf<>(
              arrived.arrival(), with(payment, Ledgers.theLedgerOf(ledgers, payment.paymentId()))));
    }
    return List.copyOf(withLedgers);
  }

  @Override
  public LedgerClaim capture(AutraPayment payment, MonetaryOperation operation, Instant at) {
    return claim(
        payment,
        () -> {
          int written = insert(payment, operation);
          return written == 1 ? markStatus(payment, AutraStatus.CAPTURED) : written;
        },
        at);
  }

  @Override
  public LedgerClaim reverse(
      AutraPayment payment, MonetaryOperation operation, String nativeStatus, Instant at) {
    return claim(
        payment,
        () -> {
          int written = insert(payment, operation);
          return written == 1 ? markStatus(payment, nativeStatus) : written;
        },
        at);
  }

  @Override
  public LedgerClaim releaseTheHold(AutraPayment payment, Instant at) {
    return claim(
        payment,
        () ->
            jdbc.sql(
                    """
                    update autra_payment set hold_released_at = ?, native_status = ?
                    where test_run_id = ? and payment_id = ? and hold_released_at is null
                    """)
                .params(
                    at.atOffset(ZoneOffset.UTC),
                    AutraStatus.VOIDED,
                    payment.testRunId().value(),
                    payment.paymentId())
                .update(),
        at);
  }

  private int markStatus(AutraPayment payment, String nativeStatus) {
    return jdbc.sql(
            "update autra_payment set native_status = ? where test_run_id = ? and payment_id = ?")
        .params(nativeStatus, payment.testRunId().value(), payment.paymentId())
        .update();
  }

  private LedgerClaim claim(AutraPayment payment, IntSupplier statement, Instant at) {
    return Objects.requireNonNull(
        transactions.execute(
            status ->
                LedgerClaim.of(() -> stillStores(payment), statement, () -> touch(payment, at))),
        "the unit of work answered nothing where its callback answers a LedgerClaim on every path");
  }

  private boolean stillStores(AutraPayment payment) {
    return jdbc.sql("select 1 from autra_payment where test_run_id = ? and payment_id = ?")
        .params(payment.testRunId().value(), payment.paymentId())
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  private void touch(AutraPayment payment, Instant at) {
    jdbc.sql("update autra_payment set updated_at = ? where test_run_id = ? and payment_id = ?")
        .params(at.atOffset(ZoneOffset.UTC), payment.testRunId().value(), payment.paymentId())
        .update();
  }

  @Override
  public Optional<String> theApprovedPaymentUnder(
      TestRunId testRunId, String documentId, String orderId) {
    return jdbc.sql(
            """
            select payment_id from autra_payment
            where test_run_id = ? and document_id = ? and order_id = ?
              and native_status in (?, ?)
            order by arrival limit 1
            """)
        .params(testRunId.value(), documentId, orderId, AutraStatus.ACCEPTED, AutraStatus.CAPTURED)
        .query(String.class)
        .optional();
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
                select e.account_id, e.key_name, e.key_value, e.arrival, e.seq, e.operation,
                       e.fingerprint, e.verdict, e.http_status, e.content_type, e.body,
                       e.provider_object_id, e.state_changed, e.at
                from autra_keyed_exchange e
                join (select account_id, key_name, key_value, arrival
                      from autra_keyed_exchange
                      where test_run_id = ? and seq = 0 and arrival < ?
                      order by arrival desc
                      limit ?) first
                  on first.account_id = e.account_id
                 and first.key_name = e.key_name
                 and first.key_value = e.key_value
                where e.test_run_id = ?
                order by first.arrival desc, e.seq
                """)
            .param(query.testRunId().value())
            .param(query.after())
            .param(query.limit())
            .param(query.testRunId().value())
            .query(JdbcAutraPayments::toRecordedRequest)
            .list();
    return KeyedExchanges.pageOf(AutraKeyMechanisms.MECHANISMS, query.testRunId(), rows);
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from autra_keyed_exchange where test_run_id = ?")
        .param(testRunId.value())
        .update();
    jdbc.sql("delete from autra_order_claim where test_run_id = ?")
        .param(testRunId.value())
        .update();
    jdbc.sql("delete from autra_payment_id_claim where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from autra_payment where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from autra_keyed_exchange").update();
    jdbc.sql("delete from autra_order_claim").update();
    jdbc.sql("delete from autra_payment_id_claim").update();
    return jdbc.sql("delete from autra_payment").update();
  }

  private AutraPayment withItsOperations(AutraPayment payment) {
    Map<String, Ledger> ledgers = theLedgersOf(payment.testRunId(), List.of(payment.paymentId()));
    return with(payment, Ledgers.theLedgerOf(ledgers, payment.paymentId()));
  }

  private Map<String, Ledger> theLedgersOf(TestRunId testRunId, List<String> paymentIds) {
    return Ledgers.keyedByObject(
        jdbc.sql(
                """
                select * from autra_payment_operation
                where test_run_id = ? and payment_id = any(?)
                order by payment_id, seq
                """)
            .params(testRunId.value(), paymentIds.toArray(String[]::new))
            .query(JdbcAutraPayments::toStoredOperation)
            .list());
  }

  private static OperationOf toStoredOperation(ResultSet rs, int row) throws SQLException {
    return new OperationOf(rs.getString("payment_id"), toOperation(rs));
  }

  private static AutraPayment with(AutraPayment payment, Ledger ledger) {
    return new AutraPayment(
        payment.testRunId(),
        payment.paymentId(),
        payment.accountId(),
        payment.documentId(),
        payment.orderId(),
        payment.type(),
        payment.transactionType(),
        payment.amount(),
        payment.currency(),
        payment.installments(),
        payment.nativeStatus(),
        payment.authorizationCode(),
        payment.rrn(),
        payment.trackingNumber(),
        payment.slugToken(),
        payment.slugStoredCard(),
        ledger,
        payment.holdReleasedAt(),
        payment.origin(),
        payment.createdAt(),
        payment.updatedAt());
  }

  private static ArrivalOf<AutraPayment> toArrivedPayment(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toPayment(rs, row));
  }

  private static @Nullable Instant instantOf(ResultSet rs, String column) throws SQLException {
    OffsetDateTime at = rs.getObject(column, OffsetDateTime.class);
    return at == null ? null : at.toInstant();
  }

  private static AutraPayment toPayment(ResultSet rs, int row) throws SQLException {
    return new AutraPayment(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("payment_id"),
        rs.getString("account_id"),
        rs.getString("document_id"),
        rs.getString("order_id"),
        rs.getString("payment_type"),
        rs.getString("transaction_type"),
        Amount.of(rs.getString("amount")),
        rs.getString("currency"),
        rs.getInt("installments"),
        rs.getString("native_status"),
        rs.getString("authorization_code"),
        rs.getString("rrn"),
        rs.getString("tracking_number"),
        rs.getString("slug_token"),
        rs.getString("slug_stored_card"),
        Ledger.empty(),
        instantOf(rs, "hold_released_at"),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static AutraPaymentEvent toEvent(ResultSet rs, int row) throws SQLException {
    return new AutraPaymentEvent(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("payment_id"),
        rs.getString("event_type"),
        rs.getString("status"),
        Amount.of(rs.getString("amount")),
        rs.getString("error_code"),
        rs.getString("msg"),
        rs.getObject("at", OffsetDateTime.class).toInstant());
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

  private static RecordedKeyedRequest toRecordedRequest(ResultSet rs, int row) throws SQLException {
    return new RecordedKeyedRequest(
        rs.getString("account_id"),
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
}
