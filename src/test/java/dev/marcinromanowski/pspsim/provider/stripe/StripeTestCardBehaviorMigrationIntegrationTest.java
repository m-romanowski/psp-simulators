package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeTestCardBehaviorMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_stripe_cards_carried_outcomes";
  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void migration_preserves_old_cards_and_wallets_and_recovers_the_behavior_of_known_copies() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    migrate("95");
    for (String id : new String[] {"pm_raw", "pm_copy"}) {
      jdbc.update(
          "insert into "
              + SCHEMA
              + ".stripe_payment_method"
              + " (test_run_id, payment_method_id, account_id, method_type, card_brand, card_last4,"
              + " card_exp_month, card_exp_year, card_funding, origin, created_at, updated_at, copied_from)"
              + " values ('migration', ?, 'merchant-1', 'CARD', 'visa', '9995', 12, 2034, 'credit',"
              + " 'PROVIDER_API', now(), now(), ?)",
          id,
          id.equals("pm_copy") ? "pm_card_visa_chargeDeclinedInsufficientFunds" : null);
    }
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_method"
            + " (test_run_id, payment_method_id, account_id, method_type, origin, created_at, updated_at)"
            + " values ('migration', 'pm_wallet', 'merchant-1', 'PAYPAL', 'CONTROL_API', now(), now())");
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_payment_intent"
            + " (test_run_id, intent_id, account_id, amount, currency, capture_method, status,"
            + " client_secret, last_payment_error, capture_expires_at, origin, created_at, updated_at, payment_method_types)"
            + " values ('migration', 'pi_sim_old', 'merchant-1', '1050', 'usd', 'manual', 'requires_payment_method',"
            + " 'pi_sim_old_secret_x', 'expired_card', now(), 'PROVIDER_API', now(), now(), '{card}')");
    for (String charge : new String[] {"ch_sim_first", "ch_sim_latest"}) {
      jdbc.update(
          "insert into "
              + SCHEMA
              + ".stripe_charge"
              + " (test_run_id, charge_id, intent_id, account_id, amount, currency, status, created_at)"
              + " values ('migration', ?, 'pi_sim_old', 'merchant-1', '1050', 'usd', 'failed', now())",
          charge);
    }
    migrate("96");
    assertThat(
            jdbc.queryForMap(
                    "select failure_code from "
                        + SCHEMA
                        + ".stripe_charge where charge_id = 'ch_sim_first'")
                .get("failure_code"))
        .isNull();
    assertThat(
            jdbc.queryForMap(
                    "select failure_code from "
                        + SCHEMA
                        + ".stripe_charge where charge_id = 'ch_sim_latest'")
                .get("failure_code"))
        .isEqualTo("expired_card");
    jdbc.update("update " + SCHEMA + ".stripe_payment_intent set last_payment_error = 'lost_card'");
    assertThat(
            jdbc.queryForObject(
                "select last_payment_error from "
                    + SCHEMA
                    + ".stripe_payment_intent where intent_id = 'pi_sim_old'",
                String.class))
        .isEqualTo("lost_card");
    assertThat(behavior("pm_raw")).isNull();
    assertThat(behavior("pm_wallet")).isNull();
    assertThat(behavior("pm_copy")).isEqualTo("INSUFFICIENT_FUNDS");
    assertThat(
            jdbc.queryForObject(
                "select card_last4 from "
                    + SCHEMA
                    + ".stripe_payment_method where payment_method_id = 'pm_raw'",
                String.class))
        .isEqualTo("9995");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".stripe_payment_method set card_test_behavior = 'GENERIC_DECLINE' where payment_method_id = 'pm_wallet'"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".stripe_payment_method set card_test_behavior = 'UNPUBLISHED' where payment_method_id = 'pm_raw'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Object behavior(String method) {
    return jdbc.queryForMap(
            "select card_test_behavior from "
                + SCHEMA
                + ".stripe_payment_method where payment_method_id = ?",
            method)
        .get("card_test_behavior");
  }

  private void migrate(String target) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .schemas(SCHEMA)
        .target(MigrationVersion.fromVersion(target))
        .load()
        .migrate();
  }
}
