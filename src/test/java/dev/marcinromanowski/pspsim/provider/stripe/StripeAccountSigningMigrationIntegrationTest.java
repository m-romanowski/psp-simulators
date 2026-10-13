package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeAccountSigningMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stripe_account_signed_anything";

  private static final String RUN = "before-a-stripe-account-signed";

  private static final String UNSIGNING = "merchant-unsigning";

  private static final MigrationVersion BEFORE_IT_SIGNED = MigrationVersion.fromVersion("29");

  private static final MigrationVersion WHEN_IT_SIGNED = MigrationVersion.fromVersion("30");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseAccountsHadNoSigningSecret() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_IT_SIGNED).migrate();
    accountOfTheOldShape(UNSIGNING);
    flyway(WHEN_IT_SIGNED).migrate();
  }

  @Test
  void an_account_configured_before_signing_existed_signs_nothing_and_dates_nothing_back() {
    Map<String, Object> kept = theAccount(UNSIGNING);

    assertThat(kept.get("webhook_secret")).isNull();
    assertThat(kept.get("webhook_signature")).isEqualTo(StripeSignatureEmission.VALID.name());
    assertThat(kept.get("webhook_timestamp_age")).isEqualTo("PT0S");
    assertThat(kept.get("secret_key")).isEqualTo("sk_test_51Simulator");
  }

  @Test
  void the_store_refuses_a_signing_secret_of_a_shape_the_control_api_refuses() {
    assertThatCode(() -> anAccountSigningWith("merchant-signing", "whsec_theEndpointSecret"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> anAccountSigningWith("merchant-wrong", "sk_test_51Simulator"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_refuses_an_account_that_describes_a_signature_it_would_never_send() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
                        + " webhook_secret, webhook_signature, webhook_timestamp_age)"
                        + " values (?, 'merchant-unsigned-but-stale', 'sk_test_51Another', 'PT72H',"
                        + " null, 'VALID', 'PT5M')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_refuses_a_signature_emission_no_account_of_this_simulator_names() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
                        + " webhook_secret, webhook_signature, webhook_timestamp_age)"
                        + " values (?, 'merchant-unnamed', 'sk_test_51Third', 'PT72H',"
                        + " 'whsec_theEndpointSecret', 'WRONG', 'PT0S')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void anAccountSigningWith(String accountId, String webhookSecret) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
            + " webhook_secret, webhook_signature, webhook_timestamp_age)"
            + " values (?, ?, ?, 'PT72H', ?, 'VALID', 'PT0S')",
        RUN,
        accountId,
        "sk_test_" + accountId,
        webhookSecret);
  }

  private void accountOfTheOldShape(String accountId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window)"
            + " values (?, ?, 'sk_test_51Simulator', 'PT72H')",
        RUN,
        accountId);
  }

  private Map<String, Object> theAccount(String accountId) {
    return jdbc.queryForMap(
        "select * from " + SCHEMA + ".stripe_account where test_run_id = ? and account_id = ?",
        RUN,
        accountId);
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
