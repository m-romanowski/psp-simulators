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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeAccountPublishableKeyMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stripe_account_carried_a_publishable_key";

  private static final String RUN = "before-a-stripe-account-carried-a-publishable-key";

  private static final String ANOTHER_RUN = "beside-it-another-run";

  private static final String FIRST = "merchant-first";

  private static final String SECOND = "merchant-second";

  private static final MigrationVersion BEFORE_IT_CARRIED_ONE = MigrationVersion.fromVersion("93");

  private static final MigrationVersion WHEN_IT_CARRIED_ONE = MigrationVersion.fromVersion("94");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverAccountsConfiguredBeforeAnyPublishableKeyWasRead() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_IT_CARRIED_ONE).migrate();
    accountOfTheOldShape(FIRST, "2026-08-26.dahlia");
    accountOfTheOldShape(SECOND, "2026-10-28.endive");
    flyway(WHEN_IT_CARRIED_ONE).migrate();
  }

  @Test
  void every_account_configured_before_the_column_carries_no_publishable_key() {
    for (String accountId : new String[] {FIRST, SECOND}) {
      assertThat(theAccount(RUN, accountId)).containsEntry("publishable_key", null);
    }
  }

  @Test
  void the_column_leaves_every_other_column_of_an_old_row_as_it_was() {
    Map<String, Object> kept = theAccount(RUN, FIRST);

    assertThat(kept.get("secret_key")).isEqualTo("sk_test_" + FIRST);
    assertThat(kept.get("capture_window")).isEqualTo("PT72H");
    assertThat(kept.get("webhook_signature")).isEqualTo("VALID");
    assertThat(kept.get("refund_status")).isEqualTo("SUCCEEDED");
    assertThat(kept.get("api_version")).isEqualTo("2026-08-26.dahlia");
  }

  @Test
  void an_old_row_takes_a_publishable_key_and_two_old_rows_of_one_run_cannot_share_one() {
    jdbc.update(
        "update " + SCHEMA + ".stripe_account set publishable_key = ? where account_id = ?",
        "pk_test_shared",
        FIRST);

    assertThat(theAccount(RUN, FIRST)).containsEntry("publishable_key", "pk_test_shared");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".stripe_account set publishable_key = ? where account_id = ?",
                    "pk_test_shared",
                    SECOND))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("stripe_account_by_publishable_key");
  }

  @Test
  void accounts_of_two_runs_may_share_a_publishable_key() {
    jdbc.update(
        "update " + SCHEMA + ".stripe_account set publishable_key = ? where account_id = ?",
        "pk_test_shared",
        FIRST);

    assertThatCode(() -> anAccountOf(ANOTHER_RUN, FIRST, "pk_test_shared"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"pk_test_", "pk_test_51Simulator"})
  void the_store_keeps_a_test_mode_publishable_key(String publishableKey) {
    assertThatCode(() -> anAccountOf(ANOTHER_RUN, "merchant-kept", publishableKey))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"pk_live_51Simulator", "sk_test_51Simulator", "pkxtestx51", "pk_test"})
  void the_store_refuses_a_key_that_is_no_test_mode_publishable_key(String publishableKey) {
    assertThatThrownBy(() -> anAccountOf(ANOTHER_RUN, "merchant-refused", publishableKey))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("stripe_account_publishable_key_is_a_test_mode_key");
  }

  private void anAccountOf(String testRunId, String accountId, String publishableKey) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
            + " webhook_secret, webhook_signature, webhook_timestamp_age, refund_status,"
            + " api_version, publishable_key) values (?, ?, ?, 'PT72H', null, 'VALID', 'PT0S',"
            + " 'SUCCEEDED', '2026-10-28.endive', ?)",
        testRunId,
        accountId,
        "sk_test_" + testRunId + accountId,
        publishableKey);
  }

  private void accountOfTheOldShape(String accountId, String apiVersion) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
            + " webhook_secret, webhook_signature, webhook_timestamp_age, refund_status,"
            + " api_version) values (?, ?, ?, 'PT72H', null, 'VALID', 'PT0S', 'SUCCEEDED', ?)",
        RUN,
        accountId,
        "sk_test_" + accountId,
        apiVersion);
  }

  private Map<String, Object> theAccount(String testRunId, String accountId) {
    return jdbc.queryForMap(
        "select * from " + SCHEMA + ".stripe_account where test_run_id = ? and account_id = ?",
        testRunId,
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
