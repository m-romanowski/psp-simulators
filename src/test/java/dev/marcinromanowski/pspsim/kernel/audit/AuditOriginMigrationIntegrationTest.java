package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditOriginMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_an_audit_row_named_a_surface";

  private static final String RUN = "before-origin";

  private static final MigrationVersion BEFORE_ORIGIN = MigrationVersion.fromVersion("40");

  private static final MigrationVersion WITH_ORIGIN = MigrationVersion.fromVersion("41");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverATrailWhoseRowsNamedNoSurface() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_ORIGIN).migrate();
    aRowOfTheOldShape("aud_inbound", "INBOUND_REQUEST");
    aRowOfTheOldShape("aud_outbound", "OUTBOUND_RESPONSE");
    aRowOfTheOldShape("aud_attempt", "OUTBOUND_WEBHOOK_ATTEMPT");
    aRowOfTheOldShape("aud_dropped", "OUTBOUND_WEBHOOK_DROPPED");
    aRowOfTheOldShape("aud_not_sent", "OUTBOUND_WEBHOOK_NOT_SENT");
    flyway(WITH_ORIGIN).migrate();
  }

  @Test
  void an_exchange_a_run_already_held_still_reads_as_the_provider_traffic_it_was() {
    assertThat(originOf("aud_inbound")).isEqualTo("PROVIDER_API");
    assertThat(originOf("aud_outbound")).isEqualTo("PROVIDER_API");
  }

  @Test
  void a_callback_a_run_already_held_reads_as_the_queue_that_carried_it_and_not_as_a_tester() {
    assertThat(originOf("aud_attempt")).isEqualTo("SCHEDULED_JOB");
    assertThat(originOf("aud_dropped")).isEqualTo("SCHEDULED_JOB");
    assertThat(originOf("aud_not_sent")).isEqualTo("SCHEDULED_JOB");
  }

  @Test
  void a_row_written_after_the_upgrade_cannot_leave_the_surface_that_wrote_it_unnamed() {
    assertThatThrownBy(() -> aRowOfTheOldShape("aud_later", "INBOUND_REQUEST"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String originOf(String auditId) {
    return jdbc.queryForObject(
        "select origin from " + SCHEMA + ".simulator_audit_record where audit_id = ?",
        String.class,
        auditId);
  }

  private void aRowOfTheOldShape(String auditId, String direction) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, resolution_rule,"
            + " direction, outcome, method, path, http_status, provider_object_id, scenario_id,"
            + " attempt, created_at, sanitized_headers, sanitized_body)"
            + " values (?, 'grow', ?, 'ACCOUNT_CREDENTIALS', ?, null, 'POST', '/api/light', null,"
            + " null, null, 1, timestamptz '2026-09-07T12:00:00Z', '{}', '{}')",
        auditId,
        RUN,
        direction);
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
