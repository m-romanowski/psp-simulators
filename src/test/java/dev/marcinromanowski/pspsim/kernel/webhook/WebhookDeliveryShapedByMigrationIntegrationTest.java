package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookDeliveryShapedByMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_delivery_named_its_rule";

  private static final String RUN = "before-a-delivery-named-its-rule";

  private static final MigrationVersion BEFORE_A_DELIVERY_NAMED_ITS_RULE =
      MigrationVersion.fromVersion("36");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseDeliveriesNamedNoRule() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_DELIVERY_NAMED_ITS_RULE).migrate();
    deliveryOfTheOldShape("whjob_delivered", "DELIVERED", 1);
    deliveryOfTheOldShape("whjob_pending", "PENDING", 0);
    deliveryOfTheOldShape("whjob_exhausted", "EXHAUSTED", 5);
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void every_delivery_scheduled_before_a_rule_could_shape_one_survives_the_migration() {
    assertThat(everyDeliveryHeld())
        .containsExactlyInAnyOrder("whjob_delivered", "whjob_pending", "whjob_exhausted");
  }

  @Test
  void
      a_delivery_scheduled_before_a_rule_could_shape_one_names_no_rule_rather_than_a_guessed_one() {
    assertThat(everyRuleNamed()).containsOnlyNulls();
  }

  @Test
  void the_migrated_table_holds_the_rule_a_delivery_was_shaped_by() {
    jdbc.update(
        "update " + SCHEMA + ".simulator_webhook_delivery set shaped_by = ? where delivery_id = ?",
        "scn_held",
        "whjob_pending");

    assertThat(
            jdbc.queryForObject(
                "select shaped_by from "
                    + SCHEMA
                    + ".simulator_webhook_delivery where delivery_id = 'whjob_pending'",
                String.class))
        .isEqualTo("scn_held");
  }

  private void deliveryOfTheOldShape(String deliveryId, String state, int attemptsMade) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery (delivery_id, provider, test_run_id, merchant_id,"
            + " event_id, event_type, provider_object_id, target_url, content_type, headers,"
            + " body, sanitized_body, retry_delays, state, acknowledgement_owed, attempts_made,"
            + " next_attempt_at, created_at, updated_at)"
            + " values (?, 'grow', ?, 'merchant-1', 'evt_1', 'payment_process.completed',"
            + " '734754', 'http://localhost:9/callbacks', 'application/x-www-form-urlencoded',"
            + " '{}', ?, '{}', '[\"PT0S\"]', ?, false, ?, null,"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        deliveryId,
        RUN,
        "status=1".getBytes(StandardCharsets.UTF_8),
        state,
        attemptsMade);
  }

  private List<String> everyDeliveryHeld() {
    return jdbc.queryForList(
        "select delivery_id from " + SCHEMA + ".simulator_webhook_delivery", String.class);
  }

  private List<String> everyRuleNamed() {
    return jdbc.queryForList(
        "select shaped_by from " + SCHEMA + ".simulator_webhook_delivery", String.class);
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
