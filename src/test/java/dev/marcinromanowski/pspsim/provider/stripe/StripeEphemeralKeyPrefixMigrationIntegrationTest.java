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

class StripeEphemeralKeyPrefixMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_an_ephemeral_key_was_minted";

  private static final String RUN = "before-an-ephemeral-key-existed";

  private static final String ACCOUNT = "merchant-1";

  private static final String A_CUSTOMER_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES =
      "ek_sim_seeded_as_a_customer";

  private static final String A_METHOD_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES =
      "ek_sim_seeded_as_a_method";

  private static final String A_SETUP_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES =
      "ek_sim_seeded_as_a_setup_intent";

  private static final String A_CHARGE_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES =
      "ek_sim_seeded_as_a_charge";

  private static final String A_CUSTOMER_UNDER_THE_STEM_A_KEYS_ID_CARRIES =
      "ephkey_sim_seeded_as_a_customer";

  private static final String A_METHOD_UNDER_THE_STEM_A_KEYS_ID_CARRIES =
      "ephkey_sim_seeded_as_a_method";

  private static final String A_SETUP_UNDER_THE_STEM_A_KEYS_ID_CARRIES =
      "ephkey_sim_seeded_as_a_setup_intent";

  private static final String A_CHARGE_UNDER_THE_STEM_A_KEYS_ID_CARRIES =
      "ephkey_sim_seeded_as_a_charge";

  private static final String A_KEY_UNDER_THE_STEM_ITS_OWN_SECRET_CARRIES =
      "ek_sim_minted_before_the_stem_moved";

  private static final String A_KEY_UNDER_THE_STEM_STRIPE_PUBLISHES =
      "ephkey_sim_minted_before_the_stem_moved";

  private static final String AN_ID_CLAIMED_AFTER_THE_STEM_MOVED = "ephkey_sim_after_the_migration";

  private static final String A_SECRET_SHAPED_ID_CLAIMED_AFTER_THE_STEM_MOVED =
      "ek_sim_after_the_migration";

  private static final String A_CUSTOMER_OF_ITS_OWN = "cus_seeded_before_the_migration";

  private static final String A_METHOD_OF_ITS_OWN = "pm_seeded_before_the_migration";

  private static final String A_SETUP_OF_ITS_OWN = "seti_seeded_before_the_migration";

  private static final String A_PAYMENT_THE_CHARGES_ARE_TAKEN_ON = "pi_seeded_before_the_migration";

  private static final String A_CHARGE_OF_ITS_OWN = "ch_seeded_before_the_migration";

  private static final MigrationVersion BEFORE_EPHEMERAL_KEYS = MigrationVersion.fromVersion("56");

  private static final MigrationVersion WHEN_EPHEMERAL_KEYS_ARRIVED =
      MigrationVersion.fromVersion("57");

  private static final MigrationVersion WHEN_THE_ID_TOOK_THE_STEM_STRIPE_PUBLISHES =
      MigrationVersion.fromVersion("82");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void seedBothStemsUnderTheSchemaAsItStoodBeforeEitherOfThemWasForbidden() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_EPHEMERAL_KEYS).migrate();
    aSeededCustomer(A_CUSTOMER_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES);
    aSeededCustomer(A_CUSTOMER_UNDER_THE_STEM_A_KEYS_ID_CARRIES);
    aSeededCustomer(A_CUSTOMER_OF_ITS_OWN);
    aSeededPaymentMethod(A_METHOD_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES);
    aSeededPaymentMethod(A_METHOD_UNDER_THE_STEM_A_KEYS_ID_CARRIES);
    aSeededPaymentMethod(A_METHOD_OF_ITS_OWN);
    aSeededSetupIntent(A_SETUP_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES);
    aSeededSetupIntent(A_SETUP_UNDER_THE_STEM_A_KEYS_ID_CARRIES);
    aSeededSetupIntent(A_SETUP_OF_ITS_OWN);
    aSeededPayment(A_PAYMENT_THE_CHARGES_ARE_TAKEN_ON);
    aSeededCharge(A_CHARGE_UNDER_THE_STEM_A_KEYS_SECRET_CARRIES);
    aSeededCharge(A_CHARGE_UNDER_THE_STEM_A_KEYS_ID_CARRIES);
    aSeededCharge(A_CHARGE_OF_ITS_OWN);
    flyway(WHEN_EPHEMERAL_KEYS_ARRIVED).migrate();
    anEphemeralKey(A_KEY_UNDER_THE_STEM_ITS_OWN_SECRET_CARRIES, A_CUSTOMER_OF_ITS_OWN, "PT1H");
    anEphemeralKey(A_KEY_UNDER_THE_STEM_STRIPE_PUBLISHES, A_CUSTOMER_OF_ITS_OWN, "PT1H");
  }

  @Test
  void an_object_seeded_under_the_prefix_an_ephemeral_key_now_mints_does_not_survive_it() {
    assertThat(idsOf("stripe_customer", "customer_id"))
        .as(
            "the migration that first forbade an ephemeral key's prefix took the stem a key's"
                + " secret carries, which is the stem the id was minted under then")
        .containsExactlyInAnyOrder(
            A_CUSTOMER_UNDER_THE_STEM_A_KEYS_ID_CARRIES, A_CUSTOMER_OF_ITS_OWN);

    migrateTheIdOntoTheStemStripePublishes();

    assertThat(idsOf("stripe_customer", "customer_id")).containsExactly(A_CUSTOMER_OF_ITS_OWN);
    assertThat(idsOf("stripe_payment_method", "payment_method_id"))
        .containsExactly(A_METHOD_OF_ITS_OWN);
    assertThat(idsOf("stripe_setup_intent", "setup_intent_id")).containsExactly(A_SETUP_OF_ITS_OWN);
    assertThat(idsOf("stripe_charge", "charge_id")).containsExactly(A_CHARGE_OF_ITS_OWN);
  }

  @Test
  void a_key_minted_under_the_stem_its_own_secret_carries_does_not_survive_it_either() {
    assertThat(idsOf("stripe_ephemeral_key", "ephemeral_key_id"))
        .containsExactlyInAnyOrder(
            A_KEY_UNDER_THE_STEM_ITS_OWN_SECRET_CARRIES, A_KEY_UNDER_THE_STEM_STRIPE_PUBLISHES);

    migrateTheIdOntoTheStemStripePublishes();

    assertThat(idsOf("stripe_ephemeral_key", "ephemeral_key_id"))
        .as(
            "a key of this contract outlives the moment it was minted by an hour, so a row no"
                + " client can still read is deleted rather than renamed to an id no client was"
                + " ever handed")
        .containsExactly(A_KEY_UNDER_THE_STEM_STRIPE_PUBLISHES);
  }

  @Test
  void the_table_refuses_a_key_whose_id_is_spelled_the_way_its_own_secret_is_from_now_on() {
    migrateTheIdOntoTheStemStripePublishes();

    assertThatThrownBy(
            () ->
                anEphemeralKey(
                    A_SECRET_SHAPED_ID_CLAIMED_AFTER_THE_STEM_MOVED, A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(
            () -> anEphemeralKey(AN_ID_CLAIMED_AFTER_THE_STEM_MOVED, A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .doesNotThrowAnyException();
  }

  @Test
  void the_stores_refuse_any_object_claiming_a_minted_ephemeral_key_id_from_now_on() {
    migrateTheIdOntoTheStemStripePublishes();

    assertThatThrownBy(() -> aSeededCustomer(AN_ID_CLAIMED_AFTER_THE_STEM_MOVED))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aSeededPaymentMethod(AN_ID_CLAIMED_AFTER_THE_STEM_MOVED))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aSeededSetupIntent(AN_ID_CLAIMED_AFTER_THE_STEM_MOVED))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aSeededCharge(AN_ID_CLAIMED_AFTER_THE_STEM_MOVED))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aSeededCustomer("cus_still_fine")).doesNotThrowAnyException();
    assertThatCode(() -> aSeededCharge("ch_still_fine")).doesNotThrowAnyException();
  }

  @Test
  void the_stores_forbid_the_id_a_key_is_minted_under_and_not_the_stem_a_keys_secret_carries() {
    migrateTheIdOntoTheStemStripePublishes();

    assertThatCode(() -> aSeededCustomer(A_SECRET_SHAPED_ID_CLAIMED_AFTER_THE_STEM_MOVED))
        .as(
            "an object id is what these four constraints are about, and after the stem moved no"
                + " ephemeral key id is spelled this way: the stem belongs to a key's secret,"
                + " which lives in a column of its own that nothing resolves an object id through")
        .doesNotThrowAnyException();
    assertThatCode(() -> aSeededCharge(A_SECRET_SHAPED_ID_CLAIMED_AFTER_THE_STEM_MOVED))
        .doesNotThrowAnyException();
  }

  @Test
  void the_new_table_refuses_a_key_for_a_customer_this_test_run_holds_none_of() {
    migrateTheIdOntoTheStemStripePublishes();

    assertThatThrownBy(() -> anEphemeralKey("ephkey_sim_orphan", "cus_nothing_created", "PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> anEphemeralKey("ephkey_sim_held", A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .doesNotThrowAnyException();
  }

  @Test
  void the_new_table_refuses_a_key_that_expires_at_or_before_the_moment_it_was_minted() {
    migrateTheIdOntoTheStemStripePublishes();

    assertThatThrownBy(() -> anEphemeralKey("ephkey_sim_instant", A_CUSTOMER_OF_ITS_OWN, "PT0S"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anEphemeralKey("ephkey_sim_backwards", A_CUSTOMER_OF_ITS_OWN, "-PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_new_table_refuses_an_ephemeral_key_id_this_simulator_mints_another_object_under() {
    migrateTheIdOntoTheStemStripePublishes();

    assertThatThrownBy(() -> anEphemeralKey("cus_sim_stolen", A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anEphemeralKey("pi_sim_stolen", A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anEphemeralKey("re_sim_stolen", A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anEphemeralKey("seti_sim_stolen", A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anEphemeralKey("ch_sim_stolen", A_CUSTOMER_OF_ITS_OWN, "PT1H"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_new_table_refuses_a_second_key_minted_under_a_secret_it_already_holds() {
    migrateTheIdOntoTheStemStripePublishes();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".stripe_ephemeral_key (test_run_id, ephemeral_key_id, account_id,"
                        + " customer_id, secret, created_at, expires_at)"
                        + " values (?, 'ephkey_sim_the_second', ?, ?, ?,"
                        + " now(), now() + interval '1 hour')",
                    RUN,
                    ACCOUNT,
                    A_CUSTOMER_OF_ITS_OWN,
                    theSecretOf(A_KEY_UNDER_THE_STEM_STRIPE_PUBLISHES)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void deleting_the_customer_row_takes_every_key_minted_for_it_with_it() {
    migrateTheIdOntoTheStemStripePublishes();

    jdbc.update(
        "delete from " + SCHEMA + ".stripe_customer where customer_id = ?", A_CUSTOMER_OF_ITS_OWN);

    assertThat(idsOf("stripe_ephemeral_key", "ephemeral_key_id")).isEmpty();
  }

  private void migrateTheIdOntoTheStemStripePublishes() {
    flyway(WHEN_THE_ID_TOOK_THE_STEM_STRIPE_PUBLISHES).migrate();
  }

  private void anEphemeralKey(String ephemeralKeyId, String customerId, String livesFor) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_ephemeral_key (test_run_id, ephemeral_key_id, account_id, customer_id,"
            + " secret, created_at, expires_at)"
            + " values (?, ?, ?, ?, ?, now(), now() + ?::interval)",
        RUN,
        ephemeralKeyId,
        ACCOUNT,
        customerId,
        theSecretOf(ephemeralKeyId),
        livesFor);
  }

  private String theSecretOf(String ephemeralKeyId) {
    return ephemeralKeyId + "_secret_x";
  }

  private void aSeededCustomer(String customerId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_customer (test_run_id, customer_id, account_id, email, deleted_at, origin,"
            + " created_at, updated_at) values (?, ?, ?, null, null, 'CONTROL_API', now(), now())",
        RUN,
        customerId,
        ACCOUNT);
  }

  private void aSeededPaymentMethod(String paymentMethodId) {
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

  private void aSeededSetupIntent(String setupIntentId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_setup_intent (test_run_id, setup_intent_id, account_id, customer_id,"
            + " payment_method_id, intended_usage, status, client_secret, last_setup_error,"
            + " origin, created_at, updated_at)"
            + " values (?, ?, ?, null, null, 'OFF_SESSION', 'REQUIRES_PAYMENT_METHOD', ?, null,"
            + " 'PROVIDER_API', now(), now())",
        RUN,
        setupIntentId,
        ACCOUNT,
        setupIntentId + "_secret_x");
  }

  private void aSeededPayment(String intentId) {
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

  private void aSeededCharge(String chargeId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_charge (test_run_id, charge_id, intent_id, account_id, amount, currency,"
            + " payment_method, status, created_at)"
            + " values (?, ?, ?, ?, '1000', 'pln', 'pm_card_visa', 'succeeded', now())",
        RUN,
        chargeId,
        A_PAYMENT_THE_CHARGES_ARE_TAKEN_ON,
        ACCOUNT);
  }

  private List<String> idsOf(String table, String column) {
    return jdbc.queryForList(
        "select " + column + " from " + SCHEMA + "." + table + " order by " + column, String.class);
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
