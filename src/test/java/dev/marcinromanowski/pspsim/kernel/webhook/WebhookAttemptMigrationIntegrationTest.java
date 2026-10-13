package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookAttemptMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_an_attempt_named_its_target";

  private static final String RUN = "before-an-attempt-named-its-target";

  private static final String THE_TARGET_IT_WAS_SCHEDULED_FOR = "http://localhost:9/callbacks";

  private static final String ANOTHER_CONFIGURED_TARGET = "http://localhost:9/second";

  private static final String THE_HEADERS_STORED_WITH_THE_DELIVERY =
      "{\"X-Grow-Signature\":[\"sig\"]}";

  private static final MigrationVersion BEFORE_AN_ATTEMPT_NAMED_ITS_TARGET =
      MigrationVersion.fromVersion("60");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseAttemptsNamedNoTarget() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_AN_ATTEMPT_NAMED_ITS_TARGET).migrate();
    deliveryOfTheOldShape("whjob_refused", "EXHAUSTED", 2);
    deliveryOfTheOldShape("whjob_delivered", "DELIVERED", 1);
    attemptOfTheOldShape("whjob_refused", 1, "REFUSED");
    attemptOfTheOldShape("whjob_refused", 2, "REFUSED");
    attemptOfTheOldShape("whjob_delivered", 1, "ACKNOWLEDGED");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void an_attempt_made_before_one_could_be_retargeted_names_the_target_its_delivery_was_sent_to() {
    assertThat(everyAttemptHeld())
        .extracting(row -> row.get("delivery_id"), row -> row.get("target_url"))
        .containsExactlyInAnyOrder(
            tuple("whjob_refused", THE_TARGET_IT_WAS_SCHEDULED_FOR),
            tuple("whjob_refused", THE_TARGET_IT_WAS_SCHEDULED_FOR),
            tuple("whjob_delivered", THE_TARGET_IT_WAS_SCHEDULED_FOR));
  }

  @Test
  void no_attempt_survives_the_migration_without_the_target_it_went_to() {
    assertThat(everyAttemptHeld())
        .hasSize(3)
        .allSatisfy(row -> assertThat(row.get("target_url")).isNotNull());
  }

  @Test
  void an_attempt_made_before_the_wire_was_recorded_says_so_rather_than_borrowing_its_delivery_s() {
    assertThat(everyAttemptHeld())
        .hasSize(3)
        .allSatisfy(row -> assertThat(row.get("sent_headers")).isNull());
  }

  @Test
  void an_attempt_written_after_the_migration_may_name_a_target_of_its_own() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery_attempt (delivery_id, attempt_number, attempted_at,"
            + " target_url, sent_headers, verdict, http_status, transport_failure,"
            + " response_preview)"
            + " values ('whjob_refused', 3, timestamptz '2026-09-07T13:00:00Z', ?,"
            + " '{}', 'ACKNOWLEDGED', 204, null, '')",
        ANOTHER_CONFIGURED_TARGET);

    assertThat(
            jdbc.queryForObject(
                "select target_url from "
                    + SCHEMA
                    + ".simulator_webhook_delivery_attempt"
                    + " where delivery_id = 'whjob_refused' and attempt_number = 3",
                String.class))
        .isEqualTo(ANOTHER_CONFIGURED_TARGET);
  }

  @Test
  void an_attempt_that_names_no_target_is_refused_by_the_table_rather_than_stored() {
    Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".simulator_webhook_delivery_attempt (delivery_id, attempt_number,"
                        + " attempted_at, sent_headers, verdict) values ('whjob_delivered', 9,"
                        + " timestamptz '2026-09-07T13:00:00Z', '{}', 'REFUSED')"))
        .isInstanceOf(DataIntegrityViolationException.class);
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
            + " '734754', ?, 'application/x-www-form-urlencoded',"
            + " ?, ?, '{}', '[\"PT0S\"]', ?, false, ?, null,"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        deliveryId,
        RUN,
        THE_TARGET_IT_WAS_SCHEDULED_FOR,
        THE_HEADERS_STORED_WITH_THE_DELIVERY,
        "status=1".getBytes(StandardCharsets.UTF_8),
        state,
        attemptsMade);
  }

  private void attemptOfTheOldShape(String deliveryId, int number, String verdict) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery_attempt (delivery_id, attempt_number, attempted_at,"
            + " verdict, http_status, transport_failure, response_preview)"
            + " values (?, ?, timestamptz '2026-09-07T12:00:00Z', ?, 500, null, 'no thanks')",
        deliveryId,
        number,
        verdict);
  }

  private List<Map<String, Object>> everyAttemptHeld() {
    return jdbc.queryForList(
        "select delivery_id, attempt_number, target_url, sent_headers from "
            + SCHEMA
            + ".simulator_webhook_delivery_attempt");
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
