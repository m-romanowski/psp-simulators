package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    StripeCard card = method.card();
    StripeBillingDetails billing = method.billingDetails();
    List<String> networks = card == null ? null : card.networks();
    return jdbc.sql(
                """
            insert into stripe_payment_method
                (test_run_id, payment_method_id, account_id, customer_id, method_type,
                 card_brand, card_last4, card_exp_month, card_exp_year, card_funding,
                 card_country, card_display_brand, card_networks, card_regulated_status,
                 card_three_d_secure_supported,
                 billing_address_city, billing_address_country, billing_address_line1,
                 billing_address_line2, billing_address_postal_code, billing_address_state,
                 billing_email, billing_name, billing_phone, billing_tax_id,
                 metadata, allow_redisplay, copied_from,
                 detached_at, origin, created_at, updated_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?::json, ?, ?, ?, ?, ?, ?)
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
            .param(card == null ? null : card.country())
            .param(card == null ? null : card.displayBrand())
            .param(networks == null ? null : networks.toArray(String[]::new))
            .param(card == null ? null : card.regulatedStatus())
            .param(card == null ? null : card.threeDSecureSupported())
            .param(billing.city())
            .param(billing.country())
            .param(billing.line1())
            .param(billing.line2())
            .param(billing.postalCode())
            .param(billing.state())
            .param(billing.email())
            .param(billing.name())
            .param(billing.phone())
            .param(billing.taxId())
            .param(metadataAsWritten(method.metadata()))
            .param(method.allowRedisplay())
            .param(method.copiedFrom())
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
        new StripeBillingDetails(
            rs.getString("billing_address_city"),
            rs.getString("billing_address_country"),
            rs.getString("billing_address_line1"),
            rs.getString("billing_address_line2"),
            rs.getString("billing_address_postal_code"),
            rs.getString("billing_address_state"),
            rs.getString("billing_email"),
            rs.getString("billing_name"),
            rs.getString("billing_phone"),
            rs.getString("billing_tax_id")),
        metadataOf(rs.getString("metadata")),
        rs.getString("allow_redisplay"),
        rs.getString("copied_from"),
        instantOf(rs.getObject("detached_at", OffsetDateTime.class)),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable StripeCard cardOf(ResultSet rs, StripePaymentMethodType type)
      throws SQLException {
    if (!type.describesACard()) {
      return null;
    }
    Array networks = rs.getArray("card_networks");
    Object supported = rs.getObject("card_three_d_secure_supported");
    return new StripeCard(
        rs.getString("card_brand"),
        rs.getString("card_last4"),
        rs.getInt("card_exp_month"),
        rs.getInt("card_exp_year"),
        rs.getString("card_funding"),
        rs.getString("card_country"),
        rs.getString("card_display_brand"),
        networks == null ? null : List.of((String[]) networks.getArray()),
        rs.getString("card_regulated_status"),
        supported == null ? null : (Boolean) supported);
  }

  private static String metadataAsWritten(Map<String, String> metadata) {
    Map<String, StripeJson> members = new LinkedHashMap<>();
    metadata.forEach((key, value) -> members.put(key, StripeJson.text(value)));
    return StripeJsonWriter.toText(new StripeJson.JsonObject(members));
  }

  private static Map<String, String> metadataOf(String stored) {
    Map<String, String> metadata = new LinkedHashMap<>();
    if (StripeJsonReader.read(stored) instanceof StripeJson.JsonObject object) {
      object
          .members()
          .forEach(
              (key, value) -> {
                if (value instanceof StripeJson.JsonText text) {
                  metadata.put(key, text.value());
                }
              });
    }
    return metadata;
  }

  private static @Nullable Instant instantOf(@Nullable OffsetDateTime stored) {
    return stored == null ? null : stored.toInstant();
  }

  private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
