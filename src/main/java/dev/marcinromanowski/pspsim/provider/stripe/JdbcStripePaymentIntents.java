package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.Ledgers;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.money.OperationOf;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
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

final class JdbcStripePaymentIntents implements StripePaymentIntents {

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  JdbcStripePaymentIntents(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public boolean claimIntentId(TestRunId testRunId, String intentId) {
    return jdbc.sql(
                """
                insert into stripe_intent_id_claim (test_run_id, intent_id)
                values (?, ?)
                on conflict (test_run_id, intent_id) do nothing
                """)
            .params(testRunId.value(), intentId)
            .update()
        == 1;
  }

  @Override
  public boolean insertIfAbsent(StripePaymentIntent intent, @Nullable StripeCharge charge) {
    return Boolean.TRUE.equals(
        transactions.execute(status -> insertInOneTransaction(intent, charge)));
  }

  private boolean insertInOneTransaction(
      StripePaymentIntent intent, @Nullable StripeCharge charge) {
    int inserted =
        jdbc.sql(
                """
                insert into stripe_payment_intent
                    (test_run_id, intent_id, account_id, amount, currency, capture_method, status,
                     payment_method, customer, receipt_email, client_secret, last_payment_error,
                     cancellation_reason, capture_expires_at, settles_at, settles_with, origin,
                     created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, intent_id) do nothing
                """)
            .param(intent.testRunId().value())
            .param(intent.intentId())
            .param(intent.accountId())
            .param(intent.amount().value())
            .param(intent.currency())
            .param(intent.captureMethod().wireValue())
            .param(intent.status().wireValue())
            .param(intent.paymentMethod())
            .param(intent.customer())
            .param(intent.receiptEmail())
            .param(intent.clientSecret())
            .param(scenarioCodeOf(intent.lastPaymentError()))
            .param(wireValueOf(intent.cancellationReason()))
            .param(intent.captureExpiresAt().atOffset(ZoneOffset.UTC))
            .param(atUtc(intent.settlesAt()))
            .param(scenarioCodeOf(intent.settlesWith()))
            .param(intent.origin().name())
            .param(intent.createdAt().atOffset(ZoneOffset.UTC))
            .param(intent.updatedAt().atOffset(ZoneOffset.UTC))
            .update();
    if (inserted != 1) {
      return false;
    }
    claimIntentId(intent.testRunId(), intent.intentId());
    for (MonetaryOperation operation : intent.ledger().operations()) {
      insert(intent, operation);
    }
    if (charge != null) {
      insert(charge);
    }
    return true;
  }

  private int insert(StripeCharge charge) {
    return jdbc.sql(
            """
            insert into stripe_charge
                (test_run_id, charge_id, intent_id, account_id, amount, currency, payment_method,
                 status, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(charge.testRunId().value())
        .param(charge.chargeId())
        .param(charge.intentId())
        .param(charge.accountId())
        .param(charge.amount().value())
        .param(charge.currency())
        .param(charge.paymentMethod())
        .param(charge.status().wireValue())
        .param(charge.createdAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  private static @Nullable OffsetDateTime atUtc(@Nullable Instant at) {
    return at == null ? null : at.atOffset(ZoneOffset.UTC);
  }

  private static @Nullable Instant instantOf(@Nullable OffsetDateTime at) {
    return at == null ? null : at.toInstant();
  }

  private static @Nullable String scenarioCodeOf(@Nullable StripeDecline decline) {
    return decline == null ? null : decline.scenarioCode();
  }

  private static @Nullable String wireValueOf(@Nullable StripeCancellationReason reason) {
    return reason == null ? null : reason.wireValue();
  }

  private int insert(StripePaymentIntent intent, MonetaryOperation operation) {
    return jdbc.sql(
            """
            insert into stripe_payment_intent_operation
                (test_run_id, intent_id, seq, kind, amount, closes_authorization,
                 created_object_id, native_status, normalized_status, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, intent_id, seq) do nothing
            """)
        .param(intent.testRunId().value())
        .param(intent.intentId())
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
  public Optional<StripePaymentIntent> find(TestRunId testRunId, String intentId) {
    return jdbc.sql("select * from stripe_payment_intent where test_run_id = ? and intent_id = ?")
        .params(testRunId.value(), intentId)
        .query(JdbcStripePaymentIntents::toIntent)
        .optional()
        .map(this::withItsOperations);
  }

  @Override
  public List<StripePaymentIntent> allById(TestRunId testRunId, List<String> intentIds) {
    List<StripePaymentIntent> held =
        jdbc.sql(
                """
                select * from stripe_payment_intent
                where test_run_id = ? and intent_id = any(?)
                order by arrival desc
                """)
            .params(testRunId.value(), intentIds.toArray(String[]::new))
            .query(JdbcStripePaymentIntents::toIntent)
            .list();
    return withTheirOperations(testRunId, held);
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String intentId) {
    return jdbc.sql(
            "select arrival from stripe_payment_intent where test_run_id = ? and intent_id = ?")
        .params(testRunId.value(), intentId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, intentId));
  }

  @Override
  public List<ArrivalOf<StripePaymentIntent>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    List<ArrivalOf<StripePaymentIntent>> found =
        jdbc.sql(
                """
                select * from stripe_payment_intent where test_run_id = ? and arrival < ?
                order by arrival desc limit ?
                """)
            .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
            .query(JdbcStripePaymentIntents::toArrivedIntent)
            .list();
    List<String> intentIds = found.stream().map(arrived -> arrived.row().intentId()).toList();
    Map<String, Ledger> ledgers = theLedgersOf(testRunId, intentIds);
    Map<String, String> latestCharges = theLatestChargesOf(testRunId, intentIds);
    List<ArrivalOf<StripePaymentIntent>> withLedgers = new ArrayList<>();
    for (ArrivalOf<StripePaymentIntent> arrived : found) {
      StripePaymentIntent intent = arrived.row();
      withLedgers.add(
          new ArrivalOf<>(
              arrived.arrival(),
              with(
                  intent,
                  Ledgers.theLedgerOf(ledgers, intent.intentId()),
                  latestCharges.get(intent.intentId()))));
    }
    return List.copyOf(withLedgers);
  }

  private static ArrivalOf<StripePaymentIntent> toArrivedIntent(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toIntent(rs, row));
  }

  @Override
  public LedgerClaim confirmed(
      StripePaymentIntent intent,
      StripeIntentStatus reached,
      String paymentMethod,
      MonetaryOperation operation,
      StripeCharge charge,
      Instant at) {
    return claim(
        intent,
        () -> {
          int moved =
              jdbc.sql(
                      """
                      update stripe_payment_intent
                      set status = ?, payment_method = ?, last_payment_error = null
                      where test_run_id = ? and intent_id = ? and status = ?
                      """)
                  .params(
                      reached.wireValue(),
                      paymentMethod,
                      intent.testRunId().value(),
                      intent.intentId(),
                      intent.status().wireValue())
                  .update();
          if (moved != 1) {
            return moved;
          }
          insert(charge);
          return insert(intent, operation);
        },
        at);
  }

  @Override
  public LedgerClaim declined(
      StripePaymentIntent intent,
      StripeDecline decline,
      @Nullable String paymentMethod,
      StripeCharge charge,
      Instant at) {
    return claim(
        intent,
        () -> {
          int moved =
              jdbc.sql(
                      """
                      update stripe_payment_intent
                      set status = ?, payment_method = ?, last_payment_error = ?
                      where test_run_id = ? and intent_id = ? and status = ?
                      """)
                  .param(StripeIntentStatus.REQUIRES_PAYMENT_METHOD.wireValue())
                  .param(paymentMethod)
                  .param(decline.scenarioCode())
                  .param(intent.testRunId().value())
                  .param(intent.intentId())
                  .param(intent.status().wireValue())
                  .update();
          return moved == 1 ? insert(charge) : moved;
        },
        at);
  }

  @Override
  public LedgerClaim pending(
      StripePaymentIntent intent,
      String paymentMethod,
      Instant settlesAt,
      @Nullable StripeDecline settlesWith,
      Instant at) {
    return claim(
        intent,
        () ->
            jdbc.sql(
                    """
                    update stripe_payment_intent
                    set status = ?, payment_method = ?, last_payment_error = null,
                        settles_at = ?, settles_with = ?
                    where test_run_id = ? and intent_id = ? and status = ?
                    """)
                .param(StripeIntentStatus.PROCESSING.wireValue())
                .param(paymentMethod)
                .param(settlesAt.atOffset(ZoneOffset.UTC))
                .param(scenarioCodeOf(settlesWith))
                .param(intent.testRunId().value())
                .param(intent.intentId())
                .param(intent.status().wireValue())
                .update(),
        at);
  }

  @Override
  public LedgerClaim captured(
      StripePaymentIntent intent,
      StripeIntentStatus reached,
      MonetaryOperation operation,
      Instant at) {
    return claim(
        intent,
        () -> {
          int moved =
              jdbc.sql(
                      """
                      update stripe_payment_intent set status = ?
                      where test_run_id = ? and intent_id = ? and status = ?
                      """)
                  .params(
                      reached.wireValue(),
                      intent.testRunId().value(),
                      intent.intentId(),
                      StripeIntentStatus.REQUIRES_CAPTURE.wireValue())
                  .update();
          return moved == 1 ? insert(intent, operation) : moved;
        },
        at);
  }

  @Override
  public LedgerClaim cancelled(
      StripePaymentIntent intent, @Nullable StripeCancellationReason reason, Instant at) {
    return claim(
        intent,
        () ->
            jdbc.sql(
                    """
                    update stripe_payment_intent set status = ?, cancellation_reason = ?
                    where test_run_id = ? and intent_id = ? and status = ?
                    """)
                .param(StripeIntentStatus.CANCELED.wireValue())
                .param(wireValueOf(reason))
                .param(intent.testRunId().value())
                .param(intent.intentId())
                .param(intent.status().wireValue())
                .update(),
        at);
  }

  private LedgerClaim claim(StripePaymentIntent intent, IntSupplier statement, Instant at) {
    return Objects.requireNonNull(
        transactions.execute(
            status ->
                LedgerClaim.of(() -> stillStores(intent), statement, () -> touch(intent, at))),
        "the unit of work answered nothing where its callback answers a LedgerClaim on every path");
  }

  private boolean stillStores(StripePaymentIntent intent) {
    return jdbc.sql("select 1 from stripe_payment_intent where test_run_id = ? and intent_id = ?")
        .params(intent.testRunId().value(), intent.intentId())
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  private void touch(StripePaymentIntent intent, Instant at) {
    jdbc.sql(
            "update stripe_payment_intent set updated_at = ? where test_run_id = ? and intent_id = ?")
        .params(at.atOffset(ZoneOffset.UTC), intent.testRunId().value(), intent.intentId())
        .update();
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from stripe_intent_id_claim where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from stripe_payment_intent where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from stripe_intent_id_claim").update();
    return jdbc.sql("delete from stripe_payment_intent").update();
  }

  private StripePaymentIntent withItsOperations(StripePaymentIntent intent) {
    List<String> named = List.of(intent.intentId());
    return with(
        intent,
        Ledgers.theLedgerOf(theLedgersOf(intent.testRunId(), named), intent.intentId()),
        theLatestChargesOf(intent.testRunId(), named).get(intent.intentId()));
  }

  private List<StripePaymentIntent> withTheirOperations(
      TestRunId testRunId, List<StripePaymentIntent> held) {
    List<String> intentIds = held.stream().map(StripePaymentIntent::intentId).toList();
    Map<String, Ledger> ledgers = theLedgersOf(testRunId, intentIds);
    Map<String, String> latestCharges = theLatestChargesOf(testRunId, intentIds);
    return held.stream()
        .map(
            intent ->
                with(
                    intent,
                    Ledgers.theLedgerOf(ledgers, intent.intentId()),
                    latestCharges.get(intent.intentId())))
        .toList();
  }

  private Map<String, Ledger> theLedgersOf(TestRunId testRunId, List<String> intentIds) {
    return Ledgers.keyedByObject(
        jdbc.sql(
                """
                select * from stripe_payment_intent_operation
                where test_run_id = ? and intent_id = any(?)
                order by intent_id, seq
                """)
            .params(testRunId.value(), intentIds.toArray(String[]::new))
            .query(JdbcStripePaymentIntents::toStoredOperation)
            .list());
  }

  private Map<String, String> theLatestChargesOf(TestRunId testRunId, List<String> intentIds) {
    return StripeLatestCharges.keyedByIntent(
        jdbc.sql(
                """
                select intent_id, charge_id from stripe_charge
                where test_run_id = ? and intent_id = any(?)
                order by arrival
                """)
            .params(testRunId.value(), intentIds.toArray(String[]::new))
            .query(JdbcStripePaymentIntents::toChargeOfAnIntent)
            .list());
  }

  private static OperationOf toStoredOperation(ResultSet rs, int row) throws SQLException {
    return new OperationOf(rs.getString("intent_id"), toOperation(rs));
  }

  private static StripeLatestCharges.ChargeOf toChargeOfAnIntent(ResultSet rs, int row)
      throws SQLException {
    return new StripeLatestCharges.ChargeOf(rs.getString("intent_id"), rs.getString("charge_id"));
  }

  private static StripePaymentIntent with(
      StripePaymentIntent intent, Ledger ledger, @Nullable String latestCharge) {
    return new StripePaymentIntent(
        intent.testRunId(),
        intent.intentId(),
        intent.accountId(),
        intent.amount(),
        intent.currency(),
        intent.captureMethod(),
        intent.status(),
        intent.paymentMethod(),
        intent.customer(),
        intent.receiptEmail(),
        intent.clientSecret(),
        latestCharge,
        intent.lastPaymentError(),
        intent.cancellationReason(),
        intent.captureExpiresAt(),
        intent.settlesAt(),
        intent.settlesWith(),
        ledger,
        intent.origin(),
        intent.createdAt(),
        intent.updatedAt());
  }

  private static StripePaymentIntent toIntent(ResultSet rs, int row) throws SQLException {
    return new StripePaymentIntent(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("intent_id"),
        rs.getString("account_id"),
        Amount.of(rs.getString("amount")),
        rs.getString("currency"),
        StripeCaptureMethod.of(rs.getString("capture_method")).orElseThrow(),
        StripeIntentStatus.of(rs.getString("status")).orElseThrow(),
        rs.getString("payment_method"),
        rs.getString("customer"),
        rs.getString("receipt_email"),
        rs.getString("client_secret"),
        null,
        declineOf(rs.getString("last_payment_error")),
        reasonOf(rs.getString("cancellation_reason")),
        rs.getObject("capture_expires_at", OffsetDateTime.class).toInstant(),
        instantOf(rs.getObject("settles_at", OffsetDateTime.class)),
        declineOf(rs.getString("settles_with")),
        Ledger.empty(),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable StripeDecline declineOf(@Nullable String scenarioCode) {
    return scenarioCode == null ? null : StripeDecline.of(scenarioCode).orElseThrow();
  }

  private static @Nullable StripeCancellationReason reasonOf(@Nullable String wireValue) {
    return wireValue == null ? null : StripeCancellationReason.of(wireValue).orElseThrow();
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
