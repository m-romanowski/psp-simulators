package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WebhookAttemptWireMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_an_attempt_recorded_the_wire";

  private static final String RUN = "before-an-attempt-recorded-the-wire";

  private static final String TARGET = "http://localhost:9/callbacks";

  private static final String THE_HEADERS_GROW_ADDED_OF_ITS_OWN = "{}";

  private static final String THE_HEADERS_STRIPE_ADDED_OF_ITS_OWN =
      "{\"Stripe-Signature\":[\"t=1,v1=a\"]}";

  private static final String THE_WIRE_AN_ATTEMPT_RECORDS_NOW =
      "{\"Content-Type\":[\"application/x-www-form-urlencoded\"]}";

  private static final MigrationVersion BEFORE_AN_ATTEMPT_RECORDED_THE_WIRE =
      MigrationVersion.fromVersion("69");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseAttemptsHeldOnlyWhatTheProviderAdded() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_AN_ATTEMPT_RECORDED_THE_WIRE).migrate();
    deliveryOfTheOldShape("whjob_grow", "grow", "application/x-www-form-urlencoded");
    deliveryOfTheOldShape("whjob_stripe", "stripe", "application/json");
    attemptOfTheOldShape("whjob_grow", 1, THE_HEADERS_GROW_ADDED_OF_ITS_OWN);
    attemptOfTheOldShape("whjob_grow", 2, THE_HEADERS_GROW_ADDED_OF_ITS_OWN);
    attemptOfTheOldShape("whjob_stripe", 1, THE_HEADERS_STRIPE_ADDED_OF_ITS_OWN);
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void an_attempt_that_recorded_only_what_its_provider_added_now_records_no_wire_at_all() {
    assertThat(everyAttemptHeld())
        .hasSize(3)
        .allSatisfy(row -> assertThat(row.get("sent_headers")).isNull());
  }

  @Test
  void no_attempt_of_the_old_shape_survives_looking_like_one_that_put_no_header_on_the_wire() {
    assertThat(everyAttemptHeld())
        .extracting(row -> row.get("sent_headers"))
        .doesNotContain(THE_HEADERS_GROW_ADDED_OF_ITS_OWN, THE_HEADERS_STRIPE_ADDED_OF_ITS_OWN);
  }

  @Test
  void an_attempt_of_the_old_shape_still_reads_with_everything_else_it_was_stored_with() {
    assertThat(everyAttemptHeld())
        .extracting(
            row -> row.get("delivery_id"),
            row -> row.get("attempt_number"),
            row -> row.get("target_url"),
            row -> row.get("verdict"),
            row -> row.get("http_status"),
            row -> row.get("response_preview"))
        .containsExactlyInAnyOrder(
            tuple("whjob_grow", 1, TARGET, "REFUSED", 500, "no thanks"),
            tuple("whjob_grow", 2, TARGET, "REFUSED", 500, "no thanks"),
            tuple("whjob_stripe", 1, TARGET, "REFUSED", 500, "no thanks"));
  }

  @Test
  void an_attempt_written_after_the_migration_carries_the_content_type_it_posted_under() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery_attempt (delivery_id, attempt_number, attempted_at,"
            + " target_url, sent_headers, verdict, http_status, transport_failure,"
            + " response_preview)"
            + " values ('whjob_grow', 3, timestamptz '2026-09-25T13:00:00Z', ?, ?,"
            + " 'ACKNOWLEDGED', 204, null, '')",
        TARGET,
        THE_WIRE_AN_ATTEMPT_RECORDS_NOW);

    assertThat(
            jdbc.queryForObject(
                "select sent_headers from "
                    + SCHEMA
                    + ".simulator_webhook_delivery_attempt"
                    + " where delivery_id = 'whjob_grow' and attempt_number = 3",
                String.class))
        .isEqualTo(THE_WIRE_AN_ATTEMPT_RECORDS_NOW);
  }

  @Test
  void a_reader_tells_the_attempts_that_recorded_the_wire_from_the_ones_written_before_it_was() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery_attempt (delivery_id, attempt_number, attempted_at,"
            + " target_url, sent_headers, verdict, http_status, transport_failure,"
            + " response_preview)"
            + " values ('whjob_grow', 3, timestamptz '2026-09-25T13:00:00Z', ?, ?,"
            + " 'ACKNOWLEDGED', 204, null, '')",
        TARGET,
        THE_WIRE_AN_ATTEMPT_RECORDS_NOW);

    assertThat(
            jdbc.queryForList(
                "select attempt_number from "
                    + SCHEMA
                    + ".simulator_webhook_delivery_attempt"
                    + " where delivery_id = 'whjob_grow' and sent_headers is null",
                Integer.class))
        .containsExactlyInAnyOrder(1, 2);
  }

  private void deliveryOfTheOldShape(String deliveryId, String provider, String contentType) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery (delivery_id, provider, test_run_id, merchant_id,"
            + " event_id, event_type, provider_object_id, target_url, content_type, headers,"
            + " body, sanitized_body, retry_delays, state, acknowledgement_owed, attempts_made,"
            + " next_attempt_at, created_at, updated_at)"
            + " values (?, ?, ?, 'merchant-1', 'evt_1', 'payment.completed', '734754', ?, ?,"
            + " '{}', ?, '{}', '[\"PT0S\"]', 'EXHAUSTED', false, 2, null,"
            + " timestamptz '2026-09-25T12:00:00Z', timestamptz '2026-09-25T12:00:00Z')",
        deliveryId,
        provider,
        RUN,
        TARGET,
        contentType,
        "status=1".getBytes(StandardCharsets.UTF_8));
  }

  private void attemptOfTheOldShape(String deliveryId, int number, String sentHeaders) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_delivery_attempt (delivery_id, attempt_number, attempted_at,"
            + " target_url, sent_headers, verdict, http_status, transport_failure,"
            + " response_preview)"
            + " values (?, ?, timestamptz '2026-09-25T12:00:00Z', ?, ?, 'REFUSED', 500, null,"
            + " 'no thanks')",
        deliveryId,
        number,
        TARGET,
        sentHeaders);
  }

  private List<Map<String, Object>> everyAttemptHeld() {
    return jdbc.queryForList(
        "select delivery_id, attempt_number, target_url, sent_headers, verdict, http_status,"
            + " response_preview from "
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
