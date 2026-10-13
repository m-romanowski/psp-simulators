package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditRefusalSubjectMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_refusal_named_its_subject";

  private static final String RUN = "before-refusal-subject";

  private static final MigrationVersion BEFORE_THE_SUBJECT = MigrationVersion.fromVersion("59");

  private static final MigrationVersion WITH_THE_SUBJECT = MigrationVersion.fromVersion("60");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverATrailWhoseRefusalsNamedNoSubject() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_SUBJECT).migrate();
    aRefusalOfTheOldShape("aud_refused");
    flyway(WITH_THE_SUBJECT).migrate();
  }

  @Test
  void
      a_refusal_a_run_already_held_survives_the_upgrade_and_reads_as_one_with_no_subject_recorded() {
    assertThat(refusalSubjectOf("aud_refused")).isNull();
    assertThat(outcomeOf("aud_refused")).isEqualTo("FAILURE");
  }

  @Test
  void a_refusal_written_after_the_upgrade_may_still_be_stored_beside_the_rows_that_predate_it() {
    aRefusalOfTheOldShape("aud_later");

    assertThat(refusalSubjectOf("aud_later")).isNull();
  }

  private @Nullable String refusalSubjectOf(String auditId) {
    return jdbc.queryForObject(
        "select refusal_subject from " + SCHEMA + ".simulator_audit_record where audit_id = ?",
        String.class,
        auditId);
  }

  private String outcomeOf(String auditId) {
    return jdbc.queryForObject(
        "select outcome from " + SCHEMA + ".simulator_audit_record where audit_id = ?",
        String.class,
        auditId);
  }

  private void aRefusalOfTheOldShape(String auditId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, resolution_rule, origin,"
            + " direction, outcome, method, path, http_status, provider_object_id, scenario_id,"
            + " attempt, created_at, sanitized_headers, sanitized_body)"
            + " values (?, 'grow', ?, 'ACCOUNT_CREDENTIALS', 'PROVIDER_API', 'OUTBOUND_RESPONSE',"
            + " 'FAILURE', 'POST', '/api/light', 200, null, null, 1,"
            + " timestamptz '2026-09-07T12:00:00Z', '{}', '{}')",
        auditId,
        RUN);
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
