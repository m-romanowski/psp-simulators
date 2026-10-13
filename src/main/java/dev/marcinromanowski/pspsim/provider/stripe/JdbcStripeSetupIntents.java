package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
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

final class JdbcStripeSetupIntents implements StripeSetupIntents {

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  JdbcStripeSetupIntents(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public boolean insertIfAbsent(StripeSetupIntent intent) {
    return jdbc.sql(
                """
            insert into stripe_setup_intent
                (test_run_id, setup_intent_id, account_id, customer_id, payment_method_id,
                 intended_usage, status, client_secret, last_setup_error, origin, created_at,
                 updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, setup_intent_id) do nothing
            """)
            .param(intent.testRunId().value())
            .param(intent.setupIntentId())
            .param(intent.accountId())
            .param(intent.customerId())
            .param(intent.paymentMethodId())
            .param(intent.usage().name())
            .param(intent.status().name())
            .param(intent.clientSecret())
            .param(scenarioCodeOf(intent.lastSetupError()))
            .param(intent.origin().name())
            .param(intent.createdAt().atOffset(ZoneOffset.UTC))
            .param(intent.updatedAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<StripeSetupIntent> find(TestRunId testRunId, String setupIntentId) {
    return jdbc.sql(
            "select * from stripe_setup_intent where test_run_id = ? and setup_intent_id = ?")
        .params(testRunId.value(), setupIntentId)
        .query(JdbcStripeSetupIntents::toSetupIntent)
        .optional();
  }

  @Override
  public List<StripeSetupIntent> allById(TestRunId testRunId, List<String> setupIntentIds) {
    return jdbc.sql(
            """
            select * from stripe_setup_intent
            where test_run_id = ? and setup_intent_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), setupIntentIds.toArray(String[]::new))
        .query(JdbcStripeSetupIntents::toSetupIntent)
        .list();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String setupIntentId) {
    return jdbc.sql(
            "select arrival from stripe_setup_intent where test_run_id = ?"
                + " and setup_intent_id = ?")
        .params(testRunId.value(), setupIntentId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, setupIntentId));
  }

  @Override
  public List<ArrivalOf<StripeSetupIntent>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from stripe_setup_intent where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcStripeSetupIntents::toArrivedSetupIntent)
        .list();
  }

  @Override
  public Setup succeeded(
      StripeSetupIntent intent, String paymentMethodId, @Nullable String customerId, Instant at) {
    return Objects.requireNonNull(
        transactions.execute(
            status -> inOneTransaction(status, intent, paymentMethodId, customerId, at)),
        "the unit of work answered nothing where its callback answers a Setup on every path");
  }

  private Setup inOneTransaction(
      TransactionStatus status,
      StripeSetupIntent intent,
      String paymentMethodId,
      @Nullable String customerId,
      Instant at) {
    if (customerId != null && attach(intent, paymentMethodId, customerId, at) != 1) {
      return Setup.THE_METHOD_IS_NO_LONGER_ATTACHABLE;
    }
    int moved = moveTo(intent, StripeSetupStatus.SUCCEEDED, paymentMethodId, null, at);
    if (moved == 1) {
      return Setup.APPLIED;
    }
    status.setRollbackOnly();
    return whyNothingMoved(intent);
  }

  private int attach(
      StripeSetupIntent intent, String paymentMethodId, String customerId, Instant at) {
    return jdbc.sql(
            """
            update stripe_payment_method set customer_id = ?, updated_at = ?
            where test_run_id = ? and payment_method_id = ? and account_id = ?
              and detached_at is null and (customer_id is null or customer_id = ?)
            """)
        .param(customerId)
        .param(at.atOffset(ZoneOffset.UTC))
        .param(intent.testRunId().value())
        .param(paymentMethodId)
        .param(intent.accountId())
        .param(customerId)
        .update();
  }

  @Override
  public Setup failed(
      StripeSetupIntent intent, StripeDecline decline, String paymentMethodId, Instant at) {
    int moved =
        moveTo(
            intent,
            StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
            paymentMethodId,
            decline.scenarioCode(),
            at);
    return moved == 1 ? Setup.APPLIED : whyNothingMoved(intent);
  }

  private int moveTo(
      StripeSetupIntent intent,
      StripeSetupStatus reached,
      String paymentMethodId,
      @Nullable String lastSetupError,
      Instant at) {
    return jdbc.sql(
            """
            update stripe_setup_intent
            set status = ?, payment_method_id = ?, last_setup_error = ?, updated_at = ?
            where test_run_id = ? and setup_intent_id = ? and status = ?
            """)
        .param(reached.name())
        .param(paymentMethodId)
        .param(lastSetupError)
        .param(at.atOffset(ZoneOffset.UTC))
        .param(intent.testRunId().value())
        .param(intent.setupIntentId())
        .param(intent.status().name())
        .update();
  }

  private Setup whyNothingMoved(StripeSetupIntent intent) {
    return find(intent.testRunId(), intent.setupIntentId())
        .map(held -> Setup.THE_SETUP_MOVED_ON)
        .orElse(Setup.OBJECT_GONE);
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from stripe_setup_intent where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from stripe_setup_intent").update();
  }

  private static @Nullable String scenarioCodeOf(@Nullable StripeDecline decline) {
    return decline == null ? null : decline.scenarioCode();
  }

  private static ArrivalOf<StripeSetupIntent> toArrivedSetupIntent(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toSetupIntent(rs, row));
  }

  private static StripeSetupIntent toSetupIntent(ResultSet rs, int row) throws SQLException {
    return new StripeSetupIntent(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("setup_intent_id"),
        rs.getString("account_id"),
        rs.getString("customer_id"),
        rs.getString("payment_method_id"),
        StripeSetupUsage.valueOf(rs.getString("intended_usage")),
        StripeSetupStatus.valueOf(rs.getString("status")),
        rs.getString("client_secret"),
        declineOf(rs.getString("last_setup_error")),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable StripeDecline declineOf(@Nullable String scenarioCode) {
    return scenarioCode == null ? null : StripeDecline.of(scenarioCode).orElseThrow();
  }
}
