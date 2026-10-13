package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ProviderSettingMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_provider_had_settings";

  private static final String RUN = "before-a-provider-had-settings";

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final MigrationVersion BEFORE_A_PROVIDER_HAD_SETTINGS =
      MigrationVersion.fromVersion("45");

  private static final MigrationVersion WITH_PROVIDER_SETTINGS = MigrationVersion.fromVersion("46");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TestRunSettings settings;

  @BeforeEach
  void migrateOverADatabaseWhoseDeliveriesCarriedTheOneGlobalPlan() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_PROVIDER_HAD_SETTINGS).migrate();
    aQueuedDeliveryOfTheOldShape();
    flyway(WITH_PROVIDER_SETTINGS).migrate();
  }

  @Test
  void a_callback_queued_before_this_slice_keeps_the_five_delays_it_left_on() {
    assertThat(delaysOfTheQueuedDelivery())
        .isEqualTo("[\"PT0S\",\"PT1S\",\"PT5S\",\"PT30S\",\"PT2M\"]");
  }

  @Test
  void a_callback_queued_before_this_slice_keeps_the_attempts_it_had_already_made() {
    assertThat(attemptsOfTheQueuedDelivery()).isEqualTo(2);
  }

  @Test
  void the_migrated_database_holds_a_setting_per_provider_and_test_run() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_provider_setting"
            + " (provider, test_run_id, setting_key, setting_value, updated_at)"
            + " values ('grow', ?, 'webhook.retryPlan', 'PT0S,PT1S',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN);

    assertThat(
            jdbc.queryForList(
                "select setting_value from " + SCHEMA + ".simulator_provider_setting",
                String.class))
        .containsExactly("PT0S,PT1S");
  }

  @Test
  void a_setting_a_harness_wrote_for_one_run_is_read_back_for_that_run_alone() {
    TestRunId mine = TestRunId.of("settings-store-mine");
    TestRunId theirs = TestRunId.of("settings-store-theirs");

    settings.merge(GROW, mine, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S"));

    assertThat(settings.of(GROW, mine))
        .containsExactly(Map.entry(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S"));
    assertThat(settings.of(GROW, theirs)).isEmpty();
    assertThat(settings.of(ProviderId.of("azericard"), mine)).isEmpty();
  }

  @Test
  void a_setting_written_twice_for_one_run_keeps_the_value_written_last() {
    TestRunId mine = TestRunId.of("settings-store-twice");

    settings.merge(GROW, mine, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S"));
    settings.merge(GROW, mine, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT2S,PT3S"));

    assertThat(settings.of(GROW, mine))
        .containsExactly(Map.entry(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT2S,PT3S"));
  }

  @Test
  void a_stored_key_this_build_knows_no_setting_for_is_left_out_rather_than_failing_a_read() {
    jdbc.update(
        "insert into simulator_provider_setting"
            + " (provider, test_run_id, setting_key, setting_value, updated_at)"
            + " values ('grow', 'settings-store-unknown', 'webhook.retryBudget', 'PT0S',"
            + " timestamptz '2026-09-07T12:00:00Z')");

    assertThat(settings.of(GROW, TestRunId.of("settings-store-unknown"))).isEmpty();
  }

  private String delaysOfTheQueuedDelivery() {
    return jdbc.queryForObject(
        "select retry_delays from "
            + SCHEMA
            + ".simulator_webhook_delivery where delivery_id = 'whjob_queued'",
        String.class);
  }

  private Integer attemptsOfTheQueuedDelivery() {
    return jdbc.queryForObject(
        "select attempts_made from "
            + SCHEMA
            + ".simulator_webhook_delivery where delivery_id = 'whjob_queued'",
        Integer.class);
  }

  private void aQueuedDeliveryOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery (delivery_id, provider, test_run_id, merchant_id,"
            + " event_id, event_type, provider_object_id, target_url, content_type, headers,"
            + " body, sanitized_body, retry_delays, state, acknowledgement_owed, attempts_made,"
            + " next_attempt_at, created_at, updated_at)"
            + " values ('whjob_queued', 'grow', ?, 'merchant-1', 'evt_1',"
            + " 'payment_process.completed', '734754', 'http://localhost:9/callbacks',"
            + " 'application/x-www-form-urlencoded', '{}', ?, '{}',"
            + " '[\"PT0S\",\"PT1S\",\"PT5S\",\"PT30S\",\"PT2M\"]', 'PENDING', false, 2,"
            + " timestamptz '2026-09-07T12:00:06Z', timestamptz '2026-09-07T12:00:00Z',"
            + " timestamptz '2026-09-07T12:00:01Z')",
        RUN,
        "status=1".getBytes(StandardCharsets.UTF_8));
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
