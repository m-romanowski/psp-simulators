package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

final class JdbcStripeRefunds implements StripeRefunds {

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  JdbcStripeRefunds(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public Refunded refund(
      StripePaymentIntent intent,
      @Nullable MonetaryOperation operation,
      StripeRefund refund,
      Instant at) {
    return Objects.requireNonNull(
        transactions.execute(status -> inOneTransaction(status, intent, operation, refund, at)),
        "the unit of work answered nothing where its callback answers a Refunded on every path");
  }

  private Refunded inOneTransaction(
      TransactionStatus status,
      StripePaymentIntent intent,
      @Nullable MonetaryOperation operation,
      StripeRefund refund,
      Instant at) {
    if (!stillStores(intent)) {
      return Refunded.OBJECT_GONE;
    }
    if (operation != null && insert(intent, operation) != 1) {
      return Refunded.CLAIM_TAKEN;
    }
    if (insert(refund) != 1) {
      status.setRollbackOnly();
      return Refunded.ID_TAKEN;
    }
    touch(intent, at);
    return Refunded.APPLIED;
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

  private int insert(StripeRefund refund) {
    return jdbc.sql(
            """
            insert into stripe_refund
                (test_run_id, refund_id, intent_id, charge_id, account_id, amount, currency,
                 reason, status, origin, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, refund_id) do nothing
            """)
        .param(refund.testRunId().value())
        .param(refund.refundId())
        .param(refund.intentId())
        .param(refund.chargeId())
        .param(refund.accountId())
        .param(refund.amount().value())
        .param(refund.currency())
        .param(wireValueOf(refund.reason()))
        .param(refund.status().wireValue())
        .param(refund.origin().name())
        .param(refund.createdAt().atOffset(ZoneOffset.UTC))
        .param(refund.updatedAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  private static @Nullable String wireValueOf(@Nullable StripeRefundReason reason) {
    return reason == null ? null : reason.wireValue();
  }

  @Override
  public Optional<StripeRefund> find(TestRunId testRunId, String refundId) {
    return jdbc.sql("select * from stripe_refund where test_run_id = ? and refund_id = ?")
        .params(testRunId.value(), refundId)
        .query(JdbcStripeRefunds::toRefund)
        .optional();
  }

  @Override
  public List<StripeRefund> allById(TestRunId testRunId, List<String> refundIds) {
    return jdbc.sql(
            """
            select * from stripe_refund
            where test_run_id = ? and refund_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), refundIds.toArray(String[]::new))
        .query(JdbcStripeRefunds::toRefund)
        .list();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String refundId) {
    return jdbc.sql("select arrival from stripe_refund where test_run_id = ? and refund_id = ?")
        .params(testRunId.value(), refundId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, refundId));
  }

  @Override
  public List<ArrivalOf<StripeRefund>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from stripe_refund where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcStripeRefunds::toArrivedRefund)
        .list();
  }

  private static ArrivalOf<StripeRefund> toArrivedRefund(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toRefund(rs, row));
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from stripe_refund where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from stripe_refund").update();
  }

  private static StripeRefund toRefund(ResultSet rs, int row) throws SQLException {
    return new StripeRefund(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("refund_id"),
        rs.getString("intent_id"),
        rs.getString("charge_id"),
        rs.getString("account_id"),
        Amount.of(rs.getString("amount")),
        rs.getString("currency"),
        reasonOf(rs.getString("reason")),
        StripeRefundStatus.of(rs.getString("status")).orElseThrow(),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable StripeRefundReason reasonOf(@Nullable String wireValue) {
    return wireValue == null ? null : StripeRefundReason.of(wireValue).orElseThrow();
  }
}
