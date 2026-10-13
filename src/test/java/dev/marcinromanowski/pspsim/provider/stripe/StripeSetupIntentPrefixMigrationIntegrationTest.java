package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeSetupIntentPrefixMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_setup_intent_id_was_minted";

  private static final String RUN = "before-a-setup-intent-existed";

  private static final String ACCOUNT = "merchant-1";

  private static final String A_COLLIDING_CUSTOMER = "seti_sim_seeded_as_a_customer";

  private static final String A_COLLIDING_METHOD = "seti_sim_seeded_as_a_method";

  private static final String A_CUSTOMER_OF_ITS_OWN = "cus_seeded_before_the_migration";

  private static final String A_METHOD_OF_ITS_OWN = "pm_seeded_before_the_migration";

  private static final MigrationVersion BEFORE_SETUP_INTENTS = MigrationVersion.fromVersion("54");

  private static final MigrationVersion WHEN_SETUP_INTENTS_ARRIVED =
      MigrationVersion.fromVersion("55");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseSeededUnderThePrefixASetupIntentNowMints() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_SETUP_INTENTS).migrate();
    aCustomerOfTheOldShape(A_COLLIDING_CUSTOMER);
    aCustomerOfTheOldShape(A_CUSTOMER_OF_ITS_OWN);
    aPaymentMethodOfTheOldShape(A_COLLIDING_METHOD, null);
    aPaymentMethodOfTheOldShape(A_METHOD_OF_ITS_OWN, A_CUSTOMER_OF_ITS_OWN);
    flyway(WHEN_SETUP_INTENTS_ARRIVED).migrate();
  }

  @Test
  void
      a_customer_seeded_under_the_prefix_a_setup_intent_now_mints_does_not_survive_the_migration() {
    assertThat(customerIds()).containsExactly(A_CUSTOMER_OF_ITS_OWN);
  }

  @Test
  void a_payment_method_seeded_under_that_prefix_goes_and_the_one_beside_it_keeps_its_owner() {
    assertThat(paymentMethodIds()).containsExactly(A_METHOD_OF_ITS_OWN);
    assertThat(
            jdbc.queryForObject(
                "select customer_id from "
                    + SCHEMA
                    + ".stripe_payment_method where payment_method_id = ?",
                String.class,
                A_METHOD_OF_ITS_OWN))
        .isEqualTo(A_CUSTOMER_OF_ITS_OWN);
  }

  @Test
  void the_store_refuses_a_customer_or_a_method_claiming_a_minted_setup_intent_id_from_now_on() {
    assertThatThrownBy(() -> aCustomerOfTheOldShape("seti_sim_after_the_migration"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aPaymentMethodOfTheOldShape("seti_sim_after_the_migration", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aCustomerOfTheOldShape("cus_still_fine")).doesNotThrowAnyException();
  }

  @Test
  void the_new_table_refuses_a_setup_intent_that_carries_a_setup_error_in_any_other_status() {
    assertThatCode(() -> aSetupIntent("seti_sim_failed", "REQUIRES_PAYMENT_METHOD", "expired_card"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> aSetupIntent("seti_sim_odd", "REQUIRES_CONFIRMATION", "expired_card"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_new_table_refuses_a_succeeded_setup_that_names_no_payment_method() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".stripe_setup_intent (test_run_id, setup_intent_id, account_id,"
                        + " customer_id, payment_method_id, intended_usage, status, client_secret,"
                        + " last_setup_error, origin, created_at, updated_at)"
                        + " values (?, 'seti_sim_empty', ?, null, null, 'OFF_SESSION', 'SUCCEEDED',"
                        + " 'seti_sim_empty_secret_x', null, 'PROVIDER_API', now(), now())",
                    RUN,
                    ACCOUNT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_new_table_refuses_a_setup_intent_id_this_simulator_mints_another_object_under() {
    assertThatThrownBy(() -> aSetupIntent("cus_sim_stolen", "REQUIRES_PAYMENT_METHOD", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aSetupIntent("pi_sim_stolen", "REQUIRES_PAYMENT_METHOD", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aSetupIntent("re_sim_stolen", "REQUIRES_PAYMENT_METHOD", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_new_table_refuses_a_status_provider_stripe_does_not_publish_for_a_setup_intent() {
    assertThatThrownBy(() -> aSetupIntent("seti_sim_failed_status", "FAILED", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aSetupIntent(String setupIntentId, String status, String lastSetupError) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_setup_intent (test_run_id, setup_intent_id, account_id, customer_id,"
            + " payment_method_id, intended_usage, status, client_secret, last_setup_error,"
            + " origin, created_at, updated_at)"
            + " values (?, ?, ?, null, 'pm_one', 'OFF_SESSION', ?, ?, ?, 'PROVIDER_API', now(),"
            + " now())",
        RUN,
        setupIntentId,
        ACCOUNT,
        status,
        setupIntentId + "_secret_x",
        lastSetupError);
  }

  private void aCustomerOfTheOldShape(String customerId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_customer (test_run_id, customer_id, account_id, email, deleted_at, origin,"
            + " created_at, updated_at) values (?, ?, ?, null, null, 'CONTROL_API', now(), now())",
        RUN,
        customerId,
        ACCOUNT);
  }

  private void aPaymentMethodOfTheOldShape(String paymentMethodId, String customerId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_method (test_run_id, payment_method_id, account_id, customer_id,"
            + " method_type, card_brand, card_last4, card_exp_month, card_exp_year, card_funding,"
            + " detached_at, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, 'CARD', 'visa', '4242', 12, 2030, 'credit', null,"
            + " 'CONTROL_API', now(), now())",
        RUN,
        paymentMethodId,
        ACCOUNT,
        customerId);
  }

  private List<String> customerIds() {
    return jdbc.queryForList(
        "select customer_id from " + SCHEMA + ".stripe_customer order by customer_id",
        String.class);
  }

  private List<String> paymentMethodIds() {
    return jdbc.queryForList(
        "select payment_method_id from "
            + SCHEMA
            + ".stripe_payment_method"
            + " order by payment_method_id",
        String.class);
  }

  private Flyway flyway(MigrationVersion target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .schemas(SCHEMA)
        .defaultSchema(SCHEMA)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .target(target)
        .cleanDisabled(false)
        .load();
  }
}
