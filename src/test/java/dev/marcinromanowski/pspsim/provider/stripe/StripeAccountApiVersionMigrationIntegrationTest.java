package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
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

class StripeAccountApiVersionMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stripe_account_named_an_api_version";

  private static final String RUN = "before-a-stripe-account-named-a-version";

  private static final String UNSIGNED = "merchant-unsigned";

  private static final String SIGNED_AND_REFUNDING_PENDING = "merchant-signed";

  private static final MigrationVersion BEFORE_IT_NAMED_A_VERSION =
      MigrationVersion.fromVersion("91");

  private static final MigrationVersion WHEN_IT_NAMED_A_VERSION =
      MigrationVersion.fromVersion("92");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverAccountsConfiguredBeforeAnyVersionWasRead() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_IT_NAMED_A_VERSION).migrate();
    accountOfTheOldShape(UNSIGNED, null, "VALID", "PT0S", "SUCCEEDED");
    accountOfTheOldShape(
        SIGNED_AND_REFUNDING_PENDING, "whsec_theEndpointSecret", "CORRUPTED", "PT6M", "PENDING");
    flyway(WHEN_IT_NAMED_A_VERSION).migrate();
  }

  @Test
  void every_account_configured_before_the_column_is_given_what_an_empty_default_gives_now() {
    for (String accountId : new String[] {UNSIGNED, SIGNED_AND_REFUNDING_PENDING}) {
      assertThat(theAccount(accountId).get("api_version"))
          .as(accountId)
          .isEqualTo("2026-10-28.endive");
    }
  }

  @Test
  void the_backfill_leaves_every_other_column_of_an_old_row_as_it_was() {
    Map<String, Object> kept = theAccount(SIGNED_AND_REFUNDING_PENDING);

    assertThat(kept.get("secret_key")).isEqualTo("sk_test_" + SIGNED_AND_REFUNDING_PENDING);
    assertThat(kept.get("capture_window")).isEqualTo("PT72H");
    assertThat(kept.get("webhook_secret")).isEqualTo("whsec_theEndpointSecret");
    assertThat(kept.get("webhook_signature")).isEqualTo("CORRUPTED");
    assertThat(kept.get("webhook_timestamp_age")).isEqualTo("PT6M");
    assertThat(kept.get("refund_status")).isEqualTo("PENDING");
  }

  @Test
  void the_backfilled_version_is_one_the_provider_reads_back_as_the_newest_it_answers() {
    assertThat(StripeApiVersion.stored((String) theAccount(UNSIGNED).get("api_version")))
        .isEqualTo(StripeApiVersions.newest());
  }

  @Test
  void a_row_written_after_the_migration_must_name_a_version() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
                        + " webhook_secret, webhook_signature, webhook_timestamp_age,"
                        + " refund_status) values (?, 'merchant-unversioned',"
                        + " 'sk_test_unversioned', 'PT72H', null, 'VALID', 'PT0S', 'SUCCEEDED')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2025-03-31.basil", "2026-08-26.dahlia", "2026-10-28.endive"})
  void the_store_keeps_a_release_of_a_major(String apiVersion) {
    assertThatCode(() -> anAccountAt("merchant-" + apiVersion, apiVersion))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2024-06-20", "2026-10-28.preview", "2026-10-28.", "latest", ""})
  void the_store_refuses_a_version_no_account_default_can_be(String apiVersion) {
    assertThatThrownBy(() -> anAccountAt("merchant-refused", apiVersion))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void anAccountAt(String accountId, String apiVersion) {
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

  private void accountOfTheOldShape(
      String accountId,
      @Nullable String webhookSecret,
      String signature,
      String age,
      String refundStatus) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
            + " webhook_secret, webhook_signature, webhook_timestamp_age, refund_status)"
            + " values (?, ?, ?, 'PT72H', ?, ?, ?, ?)",
        RUN,
        accountId,
        "sk_test_" + accountId,
        webhookSecret,
        signature,
        age,
        refundStatus);
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
