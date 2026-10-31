package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripePaymentMethodShapeMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stripe_payment_method_carried_its_creation";

  private static final String RUN = "before-a-payment-method-carried-its-creation";

  private static final MigrationVersion BEFORE_IT_CARRIED_ONE = MigrationVersion.fromVersion("94");

  private static final MigrationVersion WHEN_IT_CARRIED_ONE = MigrationVersion.fromVersion("95");

  private static final TestRunId A_RUN = TestRunId.of("a-payment-method-stored-whole");

  private static final Instant AT = Instant.parse("2026-10-31T09:00:00Z");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StripePaymentMethods methods;

  @BeforeEach
  void migrateOverMethodsSeededBeforeAnyWasCreated() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_IT_CARRIED_ONE).migrate();
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_method (test_run_id, payment_method_id, account_id, customer_id,"
            + " method_type, card_brand, card_last4, card_exp_month, card_exp_year, card_funding,"
            + " origin, created_at, updated_at) values (?, 'pm_seeded_card', 'merchant-1',"
            + " 'cus_1', 'CARD', 'unpublished', '4242', 12, 2030, 'unpublished', 'CONTROL_API',"
            + " now(), now())",
        RUN);
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_method (test_run_id, payment_method_id, account_id, method_type,"
            + " origin, created_at, updated_at) values (?, 'pm_seeded_wallet', 'merchant-1',"
            + " 'PAYPAL', 'CONTROL_API', now(), now())",
        RUN);
    flyway(WHEN_IT_CARRIED_ONE).migrate();
    methods.clear(A_RUN);
  }

  @AfterEach
  void clearTheRunStoredWhole() {
    methods.clear(A_RUN);
  }

  @Test
  void a_card_seeded_before_keeps_its_brand_as_its_only_network_and_carries_nothing_else_new()
      throws SQLException {
    Map<String, Object> card = theMethod("pm_seeded_card");

    assertThat(textsOf(card.get("card_networks"))).containsExactly("unpublished");
    assertThat(card.get("card_country")).isNull();
    assertThat(card.get("card_display_brand")).isNull();
    assertThat(card.get("card_regulated_status")).isNull();
    assertThat(card.get("card_three_d_secure_supported")).isNull();
    assertThat(card.get("billing_name")).isNull();
    assertThat(String.valueOf(card.get("metadata"))).isEqualTo("{}");
    assertThat(card.get("allow_redisplay")).isEqualTo("unspecified");
    assertThat(card.get("copied_from")).isNull();
    assertThat(card.get("card_last4")).isEqualTo("4242");
    assertThat(card.get("customer_id")).isEqualTo("cus_1");
  }

  @Test
  void a_wallet_seeded_before_carries_no_card_facts() {
    Map<String, Object> wallet = theMethod("pm_seeded_wallet");

    assertThat(wallet.get("card_networks")).isNull();
    assertThat(String.valueOf(wallet.get("metadata"))).isEqualTo("{}");
    assertThat(wallet.get("allow_redisplay")).isEqualTo("unspecified");
  }

  @Test
  void the_store_refuses_card_facts_on_a_method_that_is_no_card() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".stripe_payment_method set card_country = 'US' where payment_method_id"
                        + " = 'pm_seeded_wallet'"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("stripe_payment_method_card_facts_belong_to_a_card");
  }

  @Test
  void the_store_refuses_a_regulated_status_a_redisplay_and_a_metadata_stripe_does_not_publish() {
    assertThatThrownBy(() -> anUpdateOfTheCard("card_regulated_status = 'sometimes'"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("stripe_payment_method_regulated_status");
    assertThatThrownBy(() -> anUpdateOfTheCard("allow_redisplay = 'sometimes'"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("stripe_payment_method_allow_redisplay");
    assertThatThrownBy(() -> anUpdateOfTheCard("metadata = '[]'::json"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("stripe_payment_method_metadata_is_an_object");
  }

  @Test
  void a_created_method_is_read_back_with_everything_it_was_stored_with() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("zeta", "last");
    metadata.put("alpha", "first");
    StripePaymentMethod created =
        new StripePaymentMethod(
            A_RUN,
            "pm_sim_00000000000000000001",
            "merchant-1",
            null,
            StripePaymentMethodType.CARD,
            new StripeCard(
                "visa",
                "4242",
                10,
                2028,
                "credit",
                "US",
                "visa",
                List.of("visa"),
                "unregulated",
                true),
            new StripeBillingDetails(
                "San Francisco",
                "US",
                "150 Townsend St",
                "4th Floor",
                "94103",
                "CA",
                "email@email.com",
                "Isaac Asimov",
                "555-555-5555",
                "12-3456789"),
            metadata,
            "always",
            "pm_card_visa",
            null,
            ObjectOrigin.PROVIDER_API,
            AT,
            AT);

    assertThat(methods.insertIfAbsent(created)).isTrue();
    assertThat(methods.insertIfAbsent(created)).isFalse();

    StripePaymentMethod read = methods.find(A_RUN, created.paymentMethodId()).orElseThrow();
    assertThat(read).isEqualTo(created);
    assertThat(read.metadata().keySet()).containsExactly("zeta", "alpha");
  }

  @Test
  void a_method_of_a_number_stripe_publishes_nothing_for_is_read_back_with_no_networks() {
    StripePaymentMethod created =
        new StripePaymentMethod(
            A_RUN,
            "pm_sim_00000000000000000002",
            "merchant-1",
            null,
            StripePaymentMethodType.CARD,
            new StripeCard("unknown", "1111", 10, 2028, "unknown", null, null, null, null, null),
            StripeBillingDetails.NONE,
            Map.of(),
            "unspecified",
            null,
            null,
            ObjectOrigin.PROVIDER_API,
            AT,
            AT);

    methods.insertIfAbsent(created);

    assertThat(methods.find(A_RUN, created.paymentMethodId())).contains(created);
  }

  private void anUpdateOfTheCard(String assignment) {
    jdbc.update(
        "update "
            + SCHEMA
            + ".stripe_payment_method set "
            + assignment
            + " where payment_method_id = 'pm_seeded_card'");
  }

  private Map<String, Object> theMethod(String paymentMethodId) {
    return jdbc.queryForMap(
        "select * from "
            + SCHEMA
            + ".stripe_payment_method where test_run_id = ? and payment_method_id = ?",
        RUN,
        paymentMethodId);
  }

  private static List<String> textsOf(Object stored) throws SQLException {
    return List.of((String[]) ((Array) stored).getArray());
  }

  private Flyway flyway(MigrationVersion target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .schemas(SCHEMA)
        .target(target)
        .load();
  }
}
