package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
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
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcStripePaymentMethods implements StripePaymentMethods {

  private final JdbcClient jdbc;

  JdbcStripePaymentMethods(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(StripePaymentMethod method) {
    FixtureCard card = method.card();
    return jdbc.sql(
                """
            insert into stripe_payment_method
                (test_run_id, payment_method_id, account_id, customer_id, method_type,
                 card_brand, card_last4, card_exp_month, card_exp_year, card_funding,
                 detached_at, origin, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, payment_method_id) do nothing
            """)
            .param(method.testRunId().value())
            .param(method.paymentMethodId())
            .param(method.accountId())
            .param(method.customerId())
            .param(method.type().name())
            .param(card == null ? null : card.brand())
            .param(card == null ? null : card.last4())
            .param(card == null ? null : card.expMonth())
            .param(card == null ? null : card.expYear())
            .param(card == null ? null : card.funding())
            .param(at(method.detachedAt()))
            .param(method.origin().name())
            .param(method.createdAt().atOffset(ZoneOffset.UTC))
            .param(method.updatedAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<StripePaymentMethod> find(TestRunId testRunId, String paymentMethodId) {
    return jdbc.sql(
            "select * from stripe_payment_method where test_run_id = ? and payment_method_id = ?")
        .params(testRunId.value(), paymentMethodId)
        .query(JdbcStripePaymentMethods::toPaymentMethod)
        .optional();
  }

  @Override
  public List<StripePaymentMethod> allById(TestRunId testRunId, List<String> paymentMethodIds) {
    return jdbc.sql(
            """
            select * from stripe_payment_method
            where test_run_id = ? and payment_method_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), paymentMethodIds.toArray(String[]::new))
        .query(JdbcStripePaymentMethods::toPaymentMethod)
        .list();
  }

  @Override
  public List<StripePaymentMethod> of(
      TestRunId testRunId,
      String accountId,
      @Nullable String customerId,
      @Nullable StripePaymentMethodType type) {
    StringBuilder sql =
        new StringBuilder(
            "select * from stripe_payment_method where test_run_id = ? and account_id = ?");
    List<Object> arguments = new ArrayList<>();
    arguments.add(testRunId.value());
    arguments.add(accountId);
    if (customerId != null) {
      sql.append(" and customer_id = ?");
      arguments.add(customerId);
    }
    if (type != null) {
      sql.append(" and method_type = ?");
      arguments.add(type.name());
    }
    sql.append(" order by arrival desc");
    return jdbc.sql(sql.toString())
        .params(arguments)
        .query(JdbcStripePaymentMethods::toPaymentMethod)
        .list();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentMethodId) {
    return jdbc.sql(
            "select arrival from stripe_payment_method where test_run_id = ?"
                + " and payment_method_id = ?")
        .params(testRunId.value(), paymentMethodId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, paymentMethodId));
  }

  @Override
  public List<ArrivalOf<StripePaymentMethod>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from stripe_payment_method where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcStripePaymentMethods::toArrivedPaymentMethod)
        .list();
  }

  @Override
  public Detachment detached(StripePaymentMethod method, Instant at) {
    int changed =
        jdbc.sql(
                """
                update stripe_payment_method
                set customer_id = null, detached_at = ?, updated_at = ?
                where test_run_id = ? and payment_method_id = ? and detached_at is null
                """)
            .param(at.atOffset(ZoneOffset.UTC))
            .param(at.atOffset(ZoneOffset.UTC))
            .param(method.testRunId().value())
            .param(method.paymentMethodId())
            .update();
    if (changed == 1) {
      return Detachment.APPLIED;
    }
    return find(method.testRunId(), method.paymentMethodId())
        .map(held -> Detachment.ALREADY_DETACHED)
        .orElse(Detachment.OBJECT_GONE);
  }

  @Override
  public int detachedEveryMethodOf(
      TestRunId testRunId, String accountId, String customerId, Instant at) {
    return jdbc.sql(
            """
            update stripe_payment_method
            set customer_id = null, detached_at = ?, updated_at = ?
            where test_run_id = ? and account_id = ? and customer_id = ?
            """)
        .param(at.atOffset(ZoneOffset.UTC))
        .param(at.atOffset(ZoneOffset.UTC))
        .param(testRunId.value())
        .param(accountId)
        .param(customerId)
        .update();
  }

  @Override
  public void forget(TestRunId testRunId, String paymentMethodId) {
    jdbc.sql("delete from stripe_payment_method where test_run_id = ? and payment_method_id = ?")
        .params(testRunId.value(), paymentMethodId)
        .update();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from stripe_payment_method where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from stripe_payment_method").update();
  }

  private static ArrivalOf<StripePaymentMethod> toArrivedPaymentMethod(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toPaymentMethod(rs, row));
  }

  private static StripePaymentMethod toPaymentMethod(ResultSet rs, int row) throws SQLException {
    StripePaymentMethodType type = StripePaymentMethodType.valueOf(rs.getString("method_type"));
    return new StripePaymentMethod(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("payment_method_id"),
        rs.getString("account_id"),
        rs.getString("customer_id"),
        type,
        cardOf(rs, type),
        instantOf(rs.getObject("detached_at", OffsetDateTime.class)),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable FixtureCard cardOf(ResultSet rs, StripePaymentMethodType type)
      throws SQLException {
    if (!type.describesACard()) {
      return null;
    }
    return new FixtureCard(
        rs.getString("card_brand"),
        rs.getString("card_last4"),
        rs.getInt("card_exp_month"),
        rs.getInt("card_exp_year"),
        rs.getString("card_funding"));
  }

  private static @Nullable Instant instantOf(@Nullable OffsetDateTime stored) {
    return stored == null ? null : stored.toInstant();
  }

  private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
