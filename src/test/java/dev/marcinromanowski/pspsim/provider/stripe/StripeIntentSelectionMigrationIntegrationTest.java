package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeIntentSelectionMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stripe_intent_kept_its_selection";

  private static final String RUN = "before-an-intent-kept-its-selection";

  private static final String ACCOUNT = "merchant-1";

  private static final String A_HOLD = "pi_sim_00000000000000a1";

  private static final String A_DECLINED_PAYMENT = "pi_sim_00000000000000a2";

  private static final String A_SETUP = "seti_sim_00000000000000b1";

  private static final MigrationVersion BEFORE_AN_INTENT_KEPT_ITS_SELECTION =
      MigrationVersion.fromVersion("92");

  private static final MigrationVersion WHEN_AN_INTENT_KEPT_ITS_SELECTION =
      MigrationVersion.fromVersion("93");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverIntentsStoredBeforeAnySelectionWasKept() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_AN_INTENT_KEPT_ITS_SELECTION).migrate();
    aPaymentOfTheOldShape(A_HOLD, "requires_capture", null);
    aPaymentOfTheOldShape(A_DECLINED_PAYMENT, "requires_payment_method", "generic_decline");
    aSetupOfTheOldShape(A_SETUP);
    flyway(WHEN_AN_INTENT_KEPT_ITS_SELECTION).migrate();
  }

  @Test
  void a_payment_stored_before_keeps_the_card_it_answered_and_reads_back_as_dynamic() {
    for (String intentId : List.of(A_HOLD, A_DECLINED_PAYMENT)) {
      assertThat(theSelectionOf("stripe_payment_intent", "intent_id", intentId))
          .as(intentId)
          .isEqualTo(
              new StripeSelection(
                  true, null, null, null, null, List.of(StripePaymentMethodType.CARD)));
    }
  }

  @Test
  void a_setup_stored_before_keeps_the_card_and_paypal_it_answered_and_reads_back_as_dynamic() {
    assertThat(theSelectionOf("stripe_setup_intent", "setup_intent_id", A_SETUP))
        .isEqualTo(
            new StripeSelection(
                true,
                null,
                null,
                null,
                null,
                List.of(StripePaymentMethodType.CARD, StripePaymentMethodType.PAYPAL)));
  }

  @Test
  void the_backfill_leaves_every_other_column_of_an_old_row_as_it_was() {
    Map<String, Object> payment =
        jdbc.queryForMap(
            "select * from " + SCHEMA + ".stripe_payment_intent where intent_id = ?",
            A_DECLINED_PAYMENT);
    Map<String, Object> setup =
        jdbc.queryForMap(
            "select * from " + SCHEMA + ".stripe_setup_intent where setup_intent_id = ?", A_SETUP);

    assertThat(payment.get("status")).isEqualTo("requires_payment_method");
    assertThat(payment.get("last_payment_error")).isEqualTo("generic_decline");
    assertThat(payment.get("currency")).isEqualTo("pln");
    assertThat(payment.get("payment_method")).isEqualTo("pm_card_visa");
    assertThat(setup.get("status")).isEqualTo("REQUIRES_CONFIRMATION");
    assertThat(setup.get("payment_method_id")).isEqualTo("pm_one");
  }

  @Test
  void a_row_written_after_the_migration_must_name_the_types_it_offers() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".stripe_setup_intent (test_run_id, setup_intent_id, account_id,"
                        + " intended_usage, status, client_secret, origin, created_at,"
                        + " updated_at) values (?, 'seti_sim_untyped', ?, 'OFF_SESSION',"
                        + " 'REQUIRES_PAYMENT_METHOD', 'seti_sim_untyped_secret_x', 'PROVIDER_API',"
                        + " now(), now())",
                    RUN,
                    ACCOUNT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_keeps_each_selection_a_request_can_make() {
    assertThatCode(
            () -> {
              aSetupSelecting(
                  "seti_sim_dynamic",
                  "true",
                  "'never'",
                  "null",
                  "'{paypal}'",
                  "'pmc_1'",
                  "'{card}'");
              aSetupSelecting(
                  "seti_sim_allowed",
                  "null",
                  "null",
                  "'{card,sepa_debit}'",
                  "null",
                  "null",
                  "'{card}'");
              aSetupSelecting(
                  "seti_sim_listed", "null", "null", "null", "null", "null", "'{paypal,card}'");
              aSetupSelecting(
                  "seti_sim_switched_off", "false", "'always'", "null", "null", "null", "'{card}'");
            })
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null, null, null, null, null, '{}'",
        "null, null, null, null, null, '{sepa_debit}'",
        "true, null, '{card}', null, null, '{card}'",
        "null, null, '{card}', '{paypal}', null, '{card}'",
        "null, null, '{card}', null, 'pmc_1', '{card}'",
        "null, 'never', null, null, null, '{card}'",
        "true, 'sometimes', null, null, null, '{card}'",
        "false, null, null, '{paypal}', null, '{card}'",
        "null, null, null, null, 'pmc_1', '{card}'"
      })
  void the_store_refuses_a_selection_no_request_can_make(String selection) {
    String[] columns = selection.split(", ", -1);
    assertThatThrownBy(
            () ->
                aSetupSelecting(
                    "seti_sim_refused",
                    columns[0],
                    columns[1],
                    columns[2],
                    columns[3],
                    columns[4],
                    columns[5]))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                aPaymentSelecting(
                    "pi_sim_refused",
                    columns[0],
                    columns[1],
                    columns[2],
                    columns[3],
                    columns[4],
                    columns[5]))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private StripeSelection theSelectionOf(String table, String idColumn, String id) {
    return jdbc.queryForObject(
        "select * from " + SCHEMA + "." + table + " where " + idColumn + " = ?",
        (rs, row) -> StripeSelectionColumns.read(rs),
        id);
  }

  private void aSetupSelecting(
      String setupIntentId,
      String enabled,
      String redirects,
      String allowed,
      String excluded,
      String configuration,
      String offered) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_setup_intent (test_run_id, setup_intent_id, account_id, intended_usage,"
            + " status, client_secret, origin, created_at, updated_at,"
            + " automatic_payment_methods_enabled, automatic_payment_methods_allow_redirects,"
            + " allowed_payment_method_types, excluded_payment_method_types,"
            + " payment_method_configuration, payment_method_types) values (?, ?, ?,"
            + " 'OFF_SESSION', 'REQUIRES_PAYMENT_METHOD', ?, 'PROVIDER_API', now(), now(), "
            + String.join(", ", enabled, redirects, allowed, excluded, configuration, offered)
            + ")",
        RUN,
        setupIntentId,
        ACCOUNT,
        setupIntentId + "_secret_x");
  }

  private void aPaymentSelecting(
      String intentId,
      String enabled,
      String redirects,
      String allowed,
      String excluded,
      String configuration,
      String offered) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_intent (test_run_id, intent_id, account_id, amount, currency,"
            + " capture_method, status, client_secret, capture_expires_at, origin, created_at,"
            + " updated_at, automatic_payment_methods_enabled,"
            + " automatic_payment_methods_allow_redirects, allowed_payment_method_types,"
            + " excluded_payment_method_types, payment_method_configuration,"
            + " payment_method_types) values (?, ?, ?, '1000', 'pln', 'manual',"
            + " 'requires_payment_method', ?, now(), 'PROVIDER_API', now(), now(), "
            + String.join(", ", enabled, redirects, allowed, excluded, configuration, offered)
            + ")",
        RUN,
        intentId,
        ACCOUNT,
        intentId + "_secret_x");
  }

  private void aPaymentOfTheOldShape(
      String intentId, String status, @Nullable String lastPaymentError) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_intent (test_run_id, intent_id, account_id, amount, currency,"
            + " capture_method, status, payment_method, customer, receipt_email, client_secret,"
            + " last_payment_error, cancellation_reason, capture_expires_at, origin, created_at,"
            + " updated_at)"
            + " values (?, ?, ?, '1000', 'pln', 'manual', ?, 'pm_card_visa', null, null,"
            + " ?, ?, null, now(), 'PROVIDER_API', now(), now())",
        RUN,
        intentId,
        ACCOUNT,
        status,
        intentId + "_secret_x",
        lastPaymentError);
  }

  private void aSetupOfTheOldShape(String setupIntentId) {
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

  private Flyway flyway(MigrationVersion target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .schemas(SCHEMA)
        .target(target)
        .load();
  }
}
