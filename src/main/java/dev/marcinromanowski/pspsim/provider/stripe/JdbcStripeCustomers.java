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
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcStripeCustomers implements StripeCustomers {

  private final JdbcClient jdbc;

  JdbcStripeCustomers(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(StripeCustomer customer) {
    return jdbc.sql(
                """
            insert into stripe_customer
                (test_run_id, customer_id, account_id, email, deleted_at, origin, created_at,
                 updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, customer_id) do nothing
            """)
            .param(customer.testRunId().value())
            .param(customer.customerId())
            .param(customer.accountId())
            .param(customer.email())
            .param(at(customer.deletedAt()))
            .param(customer.origin().name())
            .param(customer.createdAt().atOffset(ZoneOffset.UTC))
            .param(customer.updatedAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<StripeCustomer> find(TestRunId testRunId, String customerId) {
    return jdbc.sql("select * from stripe_customer where test_run_id = ? and customer_id = ?")
        .params(testRunId.value(), customerId)
        .query(JdbcStripeCustomers::toCustomer)
        .optional();
  }

  @Override
  public List<StripeCustomer> allById(TestRunId testRunId, List<String> customerIds) {
    return jdbc.sql(
            """
            select * from stripe_customer
            where test_run_id = ? and customer_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), customerIds.toArray(String[]::new))
        .query(JdbcStripeCustomers::toCustomer)
        .list();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String customerId) {
    return jdbc.sql("select arrival from stripe_customer where test_run_id = ? and customer_id = ?")
        .params(testRunId.value(), customerId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, customerId));
  }

  @Override
  public List<ArrivalOf<StripeCustomer>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from stripe_customer where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcStripeCustomers::toArrivedCustomer)
        .list();
  }

  @Override
  public Changed updated(StripeCustomer customer, @Nullable String email, Instant at) {
    int changed =
        jdbc.sql(
                """
                update stripe_customer set email = ?, updated_at = ?
                where test_run_id = ? and customer_id = ? and deleted_at is null
                """)
            .param(email)
            .param(at.atOffset(ZoneOffset.UTC))
            .param(customer.testRunId().value())
            .param(customer.customerId())
            .update();
    return changed == 1 ? Changed.APPLIED : whyNothingChanged(customer);
  }

  @Override
  public Changed deleted(StripeCustomer customer, Instant at) {
    int changed =
        jdbc.sql(
                """
                update stripe_customer set deleted_at = ?, updated_at = ?
                where test_run_id = ? and customer_id = ? and deleted_at is null
                """)
            .param(at.atOffset(ZoneOffset.UTC))
            .param(at.atOffset(ZoneOffset.UTC))
            .param(customer.testRunId().value())
            .param(customer.customerId())
            .update();
    return changed == 1 ? Changed.APPLIED : whyNothingChanged(customer);
  }

  private Changed whyNothingChanged(StripeCustomer customer) {
    return find(customer.testRunId(), customer.customerId())
        .map(held -> Changed.ALREADY_DELETED)
        .orElse(Changed.OBJECT_GONE);
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from stripe_customer where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from stripe_customer").update();
  }

  private static ArrivalOf<StripeCustomer> toArrivedCustomer(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toCustomer(rs, row));
  }

  private static StripeCustomer toCustomer(ResultSet rs, int row) throws SQLException {
    return new StripeCustomer(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("customer_id"),
        rs.getString("account_id"),
        rs.getString("email"),
        instantOf(rs.getObject("deleted_at", OffsetDateTime.class)),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable Instant instantOf(@Nullable OffsetDateTime stored) {
    return stored == null ? null : stored.toInstant();
  }

  private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
