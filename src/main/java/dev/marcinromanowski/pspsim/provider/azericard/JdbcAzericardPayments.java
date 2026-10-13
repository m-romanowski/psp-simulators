package dev.marcinromanowski.pspsim.provider.azericard;

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
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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

final class JdbcAzericardPayments implements AzericardPayments {

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  JdbcAzericardPayments(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public boolean claimIntRef(TestRunId testRunId, String intRef) {
    return jdbc.sql(
                """
                insert into azericard_int_ref_claim (test_run_id, int_ref)
                values (?, ?)
                on conflict (test_run_id, int_ref) do nothing
                """)
            .params(testRunId.value(), intRef)
            .update()
        == 1;
  }

  @Override
  public boolean claimOrderTrace(
      TestRunId testRunId, String terminalId, LocalDate day, String trace) {
    return jdbc.sql(
                """
                insert into azericard_order_trace (test_run_id, terminal_id, trace_day, trace)
                values (?, ?, ?, ?)
                on conflict (test_run_id, terminal_id, trace_day, trace) do nothing
                """)
            .params(testRunId.value(), terminalId, day, trace)
            .update()
        == 1;
  }

  @Override
  public boolean holdOrderTrace(
      TestRunId testRunId, String terminalId, LocalDate day, String trace, String sessionId) {
    return jdbc.sql(
            """
            insert into azericard_order_trace
                (test_run_id, terminal_id, trace_day, trace, held_by_session)
            values (?, ?, ?, ?, ?)
            on conflict (test_run_id, terminal_id, trace_day, trace) do nothing
            returning true
            """)
        .params(testRunId.value(), terminalId, day, trace, sessionId)
        .query(Boolean.class)
        .optional()
        .isPresent();
  }

  @Override
  public Optional<String> theSessionHoldingOrderTrace(
      TestRunId testRunId, String terminalId, LocalDate day, String trace) {
    return jdbc.sql(
            """
            select held_by_session from azericard_order_trace
            where test_run_id = ? and terminal_id = ? and trace_day = ? and trace = ?
              and held_by_session is not null
            """)
        .params(testRunId.value(), terminalId, day, trace)
        .query(String.class)
        .optional();
  }

  @Override
  public boolean takeOverOrderTrace(TestRunId testRunId, String sessionId) {
    return jdbc.sql(
            """
            update azericard_order_trace set held_by_session = null
            where test_run_id = ? and held_by_session = ?
            returning true
            """)
        .params(testRunId.value(), sessionId)
        .query(Boolean.class)
        .optional()
        .isPresent();
  }

  @Override
  public boolean insertIfAbsent(AzericardPayment payment) {
    return Boolean.TRUE.equals(transactions.execute(status -> insertInOneTransaction(payment)));
  }

  private boolean insertInOneTransaction(AzericardPayment payment) {
    int inserted =
        jdbc.sql(
                """
                insert into azericard_payment
                    (test_run_id, int_ref, terminal_id, merchant_id, order_id, trtype, amount,
                     currency, approval, rrn, action_code, response_code, hold_reversed_at,
                     backref, email, token, card_last4, ext_net_ref, origin, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, int_ref) do nothing
                """)
            .param(payment.testRunId().value())
            .param(payment.intRef())
            .param(payment.terminalId())
            .param(payment.merchantId())
            .param(payment.order())
            .param(payment.transactionType().wireValue())
            .param(payment.amount().value())
            .param(payment.currency())
            .param(payment.approval())
            .param(payment.rrn())
            .param(payment.action())
            .param(payment.responseCode())
            .param(reversedAt(payment))
            .param(payment.backref())
            .param(payment.email())
            .param(payment.token())
            .param(payment.cardLast4())
            .param(payment.extNetRef())
            .param(payment.origin().name())
            .param(payment.createdAt().atOffset(ZoneOffset.UTC))
            .param(payment.updatedAt().atOffset(ZoneOffset.UTC))
            .update();
    if (inserted != 1) {
      return false;
    }
    claimIntRef(payment.testRunId(), payment.intRef());
    for (MonetaryOperation operation : payment.ledger().operations()) {
      insert(payment, operation);
    }
    return true;
  }

  private static @Nullable OffsetDateTime reversedAt(AzericardPayment payment) {
    Instant reversed = payment.holdReversedAt();
    return reversed == null ? null : reversed.atOffset(ZoneOffset.UTC);
  }

  private int insert(AzericardPayment payment, MonetaryOperation operation) {
    return jdbc.sql(
            """
            insert into azericard_payment_operation
                (test_run_id, int_ref, seq, kind, amount, closes_authorization,
                 created_object_id, native_status, normalized_status, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, int_ref, seq) do nothing
            """)
        .param(payment.testRunId().value())
        .param(payment.intRef())
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
  public Optional<AzericardPayment> findByIntRef(TestRunId testRunId, String intRef) {
    return jdbc.sql("select * from azericard_payment where test_run_id = ? and int_ref = ?")
        .params(testRunId.value(), intRef)
        .query(JdbcAzericardPayments::toPayment)
        .optional()
        .map(this::withItsOperations);
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String intRef) {
    return jdbc.sql("select arrival from azericard_payment where test_run_id = ? and int_ref = ?")
        .params(testRunId.value(), intRef)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, intRef));
  }

  @Override
  public List<ArrivalOf<AzericardPayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    List<ArrivalOf<AzericardPayment>> found =
        jdbc.sql(
                """
                select * from azericard_payment where test_run_id = ? and arrival < ?
                order by arrival desc limit ?
                """)
            .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
            .query(JdbcAzericardPayments::toArrivedPayment)
            .list();
    Map<String, Ledger> ledgers =
        theLedgersOf(testRunId, found.stream().map(arrived -> arrived.row().intRef()).toList());
    List<ArrivalOf<AzericardPayment>> withLedgers = new ArrayList<>();
    for (ArrivalOf<AzericardPayment> arrived : found) {
      AzericardPayment payment = arrived.row();
      withLedgers.add(
          new ArrivalOf<>(
              arrived.arrival(), with(payment, Ledgers.theLedgerOf(ledgers, payment.intRef()))));
    }
    return List.copyOf(withLedgers);
  }

  private static ArrivalOf<AzericardPayment> toArrivedPayment(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toPayment(rs, row));
  }

  @Override
  public List<AzericardPayment> byOrder(TestRunId testRunId, String terminalId, String order) {
    List<AzericardPayment> held =
        jdbc.sql(
                """
                select * from azericard_payment
                where test_run_id = ? and terminal_id = ? and order_id = ?
                order by created_at, int_ref
                """)
            .params(testRunId.value(), terminalId, order)
            .query(JdbcAzericardPayments::toPayment)
            .list();
    return withTheirLedgers(testRunId, held);
  }

  @Override
  public List<AzericardPayment> allByIntRef(TestRunId testRunId, List<String> intRefs) {
    List<AzericardPayment> held =
        jdbc.sql(
                """
                select * from azericard_payment
                where test_run_id = ? and int_ref = any(?)
                order by created_at, int_ref
                """)
            .params(testRunId.value(), intRefs.toArray(String[]::new))
            .query(JdbcAzericardPayments::toPayment)
            .list();
    return withTheirLedgers(testRunId, held);
  }

  private List<AzericardPayment> withTheirLedgers(
      TestRunId testRunId, List<AzericardPayment> held) {
    Map<String, Ledger> ledgers =
        theLedgersOf(testRunId, held.stream().map(AzericardPayment::intRef).toList());
    return held.stream()
        .map(payment -> with(payment, Ledgers.theLedgerOf(ledgers, payment.intRef())))
        .toList();
  }

  @Override
  public Optional<AzericardPayment> findAddressed(
      TestRunId testRunId, String terminalId, String order, String rrn, String intRef) {
    return jdbc.sql(
            """
            select * from azericard_payment
            where test_run_id = ? and terminal_id = ? and order_id = ? and rrn = ? and int_ref = ?
            """)
        .params(testRunId.value(), terminalId, order, rrn, intRef)
        .query(JdbcAzericardPayments::toPayment)
        .optional()
        .map(this::withItsOperations);
  }

  @Override
  public boolean hasSeen(TestRunId testRunId, String accountId, String keyName, String keyValue) {
    return !jdbc.sql(
            """
            select seq from azericard_keyed_exchange
            where test_run_id = ? and account_id = ? and key_name = ? and key_value = ?
            """)
        .params(testRunId.value(), accountId, keyName, keyValue)
        .query(Integer.class)
        .list()
        .isEmpty();
  }

  @Override
  public void observed(AzericardObservedRequest request) {
    jdbc.sql(
            """
            insert into azericard_keyed_exchange
                (test_run_id, account_id, key_name, key_value, seq, operation, fingerprint,
                 verdict, http_status, content_type, body, provider_object_id, state_changed, at)
            select ?, ?, ?, ?, coalesce(max(seq) + 1, 0), ?, ?, ?, ?, ?, ?, ?, ?, ?
            from azericard_keyed_exchange
            where test_run_id = ? and account_id = ? and key_name = ? and key_value = ?
            """)
        .param(request.testRunId().value())
        .param(request.accountId())
        .param(request.keyName())
        .param(request.keyValue())
        .param(request.operation())
        .param(request.fingerprint())
        .param(request.verdict().name())
        .param(request.httpStatus())
        .param(request.contentType())
        .param(request.body())
        .param(request.providerObjectId())
        .param(request.stateChanged())
        .param(request.at().atOffset(ZoneOffset.UTC))
        .param(request.testRunId().value())
        .param(request.accountId())
        .param(request.keyName())
        .param(request.keyValue())
        .update();
  }

  @Override
  public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
    List<RecordedKeyedRequest> rows =
        jdbc.sql(
                """
                select e.account_id, e.key_name, e.key_value, e.arrival, e.seq, e.operation,
                       e.fingerprint, e.verdict, e.http_status, e.content_type, e.body,
                       e.provider_object_id, e.state_changed, e.at
                from azericard_keyed_exchange e
                join (select account_id, key_name, key_value, arrival
                      from azericard_keyed_exchange
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
            .query(JdbcAzericardPayments::toRecordedRequest)
            .list();
    return KeyedExchanges.pageOf(AzericardKeyMechanisms.MECHANISMS, query.testRunId(), rows);
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

  @Override
  public LedgerClaim append(AzericardPayment payment, MonetaryOperation operation, Instant at) {
    return claim(payment, () -> insert(payment, operation), at);
  }

  @Override
  public LedgerClaim reverseTheHold(AzericardPayment payment, Instant at) {
    return claim(payment, () -> releaseTheHold(payment, at), at);
  }

  private LedgerClaim claim(AzericardPayment payment, IntSupplier statement, Instant at) {
    return Objects.requireNonNull(
        transactions.execute(
            status ->
                LedgerClaim.of(() -> stillStores(payment), statement, () -> touch(payment, at))),
        "the unit of work answered nothing where its callback answers a LedgerClaim on every path");
  }

  private boolean stillStores(AzericardPayment payment) {
    return jdbc.sql("select 1 from azericard_payment where test_run_id = ? and int_ref = ?")
        .params(payment.testRunId().value(), payment.intRef())
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  private int releaseTheHold(AzericardPayment payment, Instant at) {
    return jdbc.sql(
            """
            update azericard_payment set hold_reversed_at = ?
            where test_run_id = ? and int_ref = ? and hold_reversed_at is null
            """)
        .params(at.atOffset(ZoneOffset.UTC), payment.testRunId().value(), payment.intRef())
        .update();
  }

  private void touch(AzericardPayment payment, Instant at) {
    jdbc.sql("update azericard_payment set updated_at = ? where test_run_id = ? and int_ref = ?")
        .params(at.atOffset(ZoneOffset.UTC), payment.testRunId().value(), payment.intRef())
        .update();
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from azericard_keyed_exchange where test_run_id = ?")
        .param(testRunId.value())
        .update();
    jdbc.sql("delete from azericard_order_trace where test_run_id = ? and held_by_session is null")
        .param(testRunId.value())
        .update();
    jdbc.sql("delete from azericard_int_ref_claim where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from azericard_payment where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from azericard_keyed_exchange").update();
    jdbc.sql("delete from azericard_order_trace where held_by_session is null").update();
    jdbc.sql("delete from azericard_int_ref_claim").update();
    return jdbc.sql("delete from azericard_payment").update();
  }

  private AzericardPayment withItsOperations(AzericardPayment payment) {
    Map<String, Ledger> ledgers = theLedgersOf(payment.testRunId(), List.of(payment.intRef()));
    return with(payment, Ledgers.theLedgerOf(ledgers, payment.intRef()));
  }

  private Map<String, Ledger> theLedgersOf(TestRunId testRunId, List<String> intRefs) {
    return Ledgers.keyedByObject(
        jdbc.sql(
                """
                select * from azericard_payment_operation
                where test_run_id = ? and int_ref = any(?)
                order by int_ref, seq
                """)
            .params(testRunId.value(), intRefs.toArray(String[]::new))
            .query(JdbcAzericardPayments::toStoredOperation)
            .list());
  }

  private static OperationOf toStoredOperation(ResultSet rs, int row) throws SQLException {
    return new OperationOf(rs.getString("int_ref"), toOperation(rs));
  }

  private static AzericardPayment with(AzericardPayment payment, Ledger ledger) {
    return new AzericardPayment(
        payment.testRunId(),
        payment.intRef(),
        payment.terminalId(),
        payment.merchantId(),
        payment.order(),
        payment.transactionType(),
        payment.amount(),
        payment.currency(),
        payment.approval(),
        payment.rrn(),
        payment.action(),
        payment.responseCode(),
        ledger,
        payment.holdReversedAt(),
        payment.backref(),
        payment.email(),
        payment.token(),
        payment.cardLast4(),
        payment.extNetRef(),
        payment.origin(),
        payment.createdAt(),
        payment.updatedAt());
  }

  private static @Nullable Instant instantOf(ResultSet rs, String column) throws SQLException {
    OffsetDateTime at = rs.getObject(column, OffsetDateTime.class);
    return at == null ? null : at.toInstant();
  }

  private static AzericardPayment toPayment(ResultSet rs, int row) throws SQLException {
    return new AzericardPayment(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("int_ref"),
        rs.getString("terminal_id"),
        rs.getString("merchant_id"),
        rs.getString("order_id"),
        AzericardTransactionType.storedType(rs.getString("trtype")).orElseThrow(),
        Amount.of(rs.getString("amount")),
        rs.getString("currency"),
        rs.getString("approval"),
        rs.getString("rrn"),
        rs.getString("action_code"),
        rs.getString("response_code"),
        Ledger.empty(),
        instantOf(rs, "hold_reversed_at"),
        rs.getString("backref"),
        rs.getString("email"),
        rs.getString("token"),
        rs.getString("card_last4"),
        rs.getString("ext_net_ref"),
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
