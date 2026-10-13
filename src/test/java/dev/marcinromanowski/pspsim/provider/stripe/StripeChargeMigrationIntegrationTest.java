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

class StripeChargeMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_charge_was_minted";

  private static final String RUN = "before-a-charge-existed";

  private static final String ACCOUNT = "merchant-1";

  private static final String A_PAYMENT_OF_THE_OLD_SHAPE = "pi_sim_before_the_charges";

  private static final String A_REFUND_TAKEN_BEFORE_CHARGES = "re_sim_before_the_charges";

  private static final String A_COLLIDING_CUSTOMER = "ch_sim_seeded_as_a_customer";

  private static final String A_COLLIDING_METHOD = "ch_sim_seeded_as_a_method";

  private static final String A_COLLIDING_SETUP = "ch_sim_seeded_as_a_setup";

  private static final String A_CUSTOMER_OF_ITS_OWN = "cus_seeded_before_the_migration";

  private static final String A_METHOD_OF_ITS_OWN = "pm_seeded_before_the_migration";

  private static final String A_SETUP_OF_ITS_OWN = "seti_sim_seeded_before_the_migration";

  private static final MigrationVersion BEFORE_CHARGES = MigrationVersion.fromVersion("55");

  private static final MigrationVersion WHEN_CHARGES_ARRIVED = MigrationVersion.fromVersion("56");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldPaymentsAndRefunds() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_CHARGES).migrate();
    aPaymentOfTheOldShape(A_PAYMENT_OF_THE_OLD_SHAPE);
    aRefundOfTheOldShape(A_REFUND_TAKEN_BEFORE_CHARGES, A_PAYMENT_OF_THE_OLD_SHAPE);
    aCustomerOfTheOldShape(A_COLLIDING_CUSTOMER);
    aCustomerOfTheOldShape(A_CUSTOMER_OF_ITS_OWN);
    aPaymentMethodOfTheOldShape(A_COLLIDING_METHOD);
    aPaymentMethodOfTheOldShape(A_METHOD_OF_ITS_OWN);
    aSetupIntentOfTheOldShape(A_COLLIDING_SETUP);
    aSetupIntentOfTheOldShape(A_SETUP_OF_ITS_OWN);
    flyway(WHEN_CHARGES_ARRIVED).migrate();
  }

  @Test
  void a_payment_taken_before_this_build_minted_charges_has_none_and_keeps_its_refund() {
    assertThat(
            jdbc.queryForList(
                "select charge_id from " + SCHEMA + ".stripe_charge where test_run_id = ?",
                String.class,
                RUN))
        .isEmpty();
    assertThat(
            jdbc.queryForList(
                "select refund_id from "
                    + SCHEMA
                    + ".stripe_refund where test_run_id = ? and charge_id is null",
                String.class,
                RUN))
        .containsExactly(A_REFUND_TAKEN_BEFORE_CHARGES);
  }

  @Test
  void every_object_seeded_under_the_prefix_a_charge_now_mints_goes_and_the_others_stay() {
    assertThat(customerIds()).containsExactly(A_CUSTOMER_OF_ITS_OWN);
    assertThat(paymentMethodIds()).containsExactly(A_METHOD_OF_ITS_OWN);
    assertThat(setupIntentIds()).containsExactly(A_SETUP_OF_ITS_OWN);
  }

  @Test
  void the_stores_refuse_an_object_claiming_a_minted_charge_id_from_now_on() {
    assertThatThrownBy(() -> aCustomerOfTheOldShape("ch_sim_after_the_migration"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aPaymentMethodOfTheOldShape("ch_sim_after_the_migration"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aSetupIntentOfTheOldShape("ch_sim_after_the_migration"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aCustomerOfTheOldShape("cus_still_fine")).doesNotThrowAnyException();
  }

  @Test
  void the_new_table_refuses_a_charge_id_this_simulator_mints_another_object_under() {
    assertThatThrownBy(() -> aCharge("pi_sim_stolen", A_PAYMENT_OF_THE_OLD_SHAPE, "succeeded"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aCharge("re_sim_stolen", A_PAYMENT_OF_THE_OLD_SHAPE, "succeeded"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aCharge("cus_sim_stolen", A_PAYMENT_OF_THE_OLD_SHAPE, "succeeded"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aCharge("seti_sim_stolen", A_PAYMENT_OF_THE_OLD_SHAPE, "succeeded"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_new_table_refuses_a_charge_status_provider_stripe_does_not_publish_for_a_charge() {
    assertThatCode(() -> aCharge("ch_sim_failed", A_PAYMENT_OF_THE_OLD_SHAPE, "failed"))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> aCharge("ch_sim_capturable", A_PAYMENT_OF_THE_OLD_SHAPE, "requires_capture"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_refund_cannot_name_a_charge_of_a_payment_other_than_its_own() {
    aPaymentOfTheOldShape("pi_sim_another_payment");
    aCharge("ch_sim_of_another_payment", "pi_sim_another_payment", "succeeded");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update " + SCHEMA + ".stripe_refund set charge_id = ? where refund_id = ?",
                    "ch_sim_of_another_payment",
                    A_REFUND_TAKEN_BEFORE_CHARGES))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_charge_goes_with_the_payment_it_was_minted_on() {
    aCharge("ch_sim_of_the_old_payment", A_PAYMENT_OF_THE_OLD_SHAPE, "succeeded");

    jdbc.update(
        "delete from " + SCHEMA + ".stripe_payment_intent where intent_id = ?",
        A_PAYMENT_OF_THE_OLD_SHAPE);

    assertThat(
            jdbc.queryForList("select charge_id from " + SCHEMA + ".stripe_charge", String.class))
        .isEmpty();
  }

  private void aCharge(String chargeId, String intentId, String status) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_charge (test_run_id, charge_id, intent_id, account_id, amount, currency,"
            + " payment_method, status, created_at)"
            + " values (?, ?, ?, ?, '1000', 'pln', 'pm_card_visa', ?, now())",
        RUN,
        chargeId,
        intentId,
        ACCOUNT,
        status);
  }

  private void aPaymentOfTheOldShape(String intentId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_intent (test_run_id, intent_id, account_id, amount, currency,"
            + " capture_method, status, payment_method, customer, receipt_email, client_secret,"
            + " last_payment_error, cancellation_reason, capture_expires_at, origin, created_at,"
            + " updated_at)"
            + " values (?, ?, ?, '1000', 'pln', 'manual', 'succeeded', 'pm_card_visa', null, null,"
            + " ?, null, null, now(), 'PROVIDER_API', now(), now())",
        RUN,
        intentId,
        ACCOUNT,
        intentId + "_secret_x");
  }

  private void aRefundOfTheOldShape(String refundId, String intentId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_refund (test_run_id, refund_id, intent_id, account_id, amount, currency,"
            + " reason, status, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, '400', 'pln', 'requested_by_customer', 'succeeded',"
            + " 'PROVIDER_API', now(), now())",
        RUN,
        refundId,
        intentId,
        ACCOUNT);
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

  private void aPaymentMethodOfTheOldShape(String paymentMethodId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_method (test_run_id, payment_method_id, account_id, customer_id,"
            + " method_type, card_brand, card_last4, card_exp_month, card_exp_year, card_funding,"
            + " detached_at, origin, created_at, updated_at)"
            + " values (?, ?, ?, null, 'CARD', 'visa', '4242', 12, 2030, 'credit', null,"
            + " 'CONTROL_API', now(), now())",
        RUN,
        paymentMethodId,
        ACCOUNT);
  }

  private void aSetupIntentOfTheOldShape(String setupIntentId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_setup_intent (test_run_id, setup_intent_id, account_id, customer_id,"
            + " payment_method_id, intended_usage, status, client_secret, last_setup_error,"
            + " origin, created_at, updated_at)"
            + " values (?, ?, ?, null, 'pm_one', 'OFF_SESSION', 'REQUIRES_CONFIRMATION', ?, null,"
            + " 'PROVIDER_API', now(), now())",
        RUN,
        setupIntentId,
        ACCOUNT,
        setupIntentId + "_secret_x");
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
            + ".stripe_payment_method order by payment_method_id",
        String.class);
  }

  private List<String> setupIntentIds() {
    return jdbc.queryForList(
        "select setup_intent_id from " + SCHEMA + ".stripe_setup_intent order by setup_intent_id",
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
