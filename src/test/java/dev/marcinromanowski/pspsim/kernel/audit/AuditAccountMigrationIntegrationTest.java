package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditAccountMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_an_audit_row_named_an_account";

  private static final String RUN = "before-account";

  private static final String MERCHANT = "merchant-1";

  private static final MigrationVersion BEFORE_ACCOUNT = MigrationVersion.fromVersion("68");

  private static final MigrationVersion WITH_ACCOUNT = MigrationVersion.fromVersion("69");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverATrailWhoseRowsNamedNoAccount() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_ACCOUNT).migrate();
    aRowOfTheOldShape("aud_before_one");
    aRowOfTheOldShape("aud_before_two");
    flyway(WITH_ACCOUNT).migrate();
  }

  @Test
  void an_exchange_a_run_already_held_survives_the_upgrade_and_names_no_account() {
    assertThat(theTrail()).containsExactly("aud_before_one", "aud_before_two");
    assertThat(accountOf("aud_before_one")).isNull();
    assertThat(accountOf("aud_before_two")).isNull();
  }

  @Test
  void
      a_narrowing_to_an_account_reaches_no_row_written_before_the_column_and_drops_none_otherwise() {
    assertThat(theTrailNarrowedTo(MERCHANT)).isEmpty();
    assertThat(theTrail()).hasSize(2);
  }

  @Test
  void a_row_written_after_the_upgrade_carries_an_account_the_trail_can_be_narrowed_by() {
    aRowNaming("aud_after", MERCHANT);

    assertThat(accountOf("aud_after")).isEqualTo(MERCHANT);
    assertThat(theTrailNarrowedTo(MERCHANT)).containsExactly("aud_after");
  }

  @Test
  void a_row_written_after_the_upgrade_cannot_carry_a_blank_account_no_narrowing_could_match() {
    assertThatThrownBy(() -> aRowNaming("aud_blank", ""))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private @Nullable String accountOf(String auditId) {
    return jdbc.queryForObject(
        "select account_id from " + SCHEMA + ".simulator_audit_record where audit_id = ?",
        String.class,
        auditId);
  }

  private List<String> theTrail() {
    return jdbc.queryForList(
        "select audit_id from " + SCHEMA + ".simulator_audit_record order by seq", String.class);
  }

  private List<String> theTrailNarrowedTo(String accountId) {
    return jdbc.queryForList(
        "select audit_id from "
            + SCHEMA
            + ".simulator_audit_record where account_id = ? order by seq",
        String.class,
        accountId);
  }

  private void aRowOfTheOldShape(String auditId) {
    write(auditId, null);
  }

  private void aRowNaming(String auditId, String accountId) {
    write(auditId, accountId);
  }

  private void write(String auditId, @Nullable String accountId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, resolution_rule, origin,"
            + " direction, method, path, attempt, created_at, sanitized_headers, sanitized_body"
            + theAccountColumnOf(accountId)
            + ") values (?, 'grow', ?, 'ACCOUNT_CREDENTIALS', 'PROVIDER_API', 'INBOUND_REQUEST',"
            + " 'POST', '/api/light', 1, timestamptz '2026-09-07T12:00:00Z', '{}', '{}'"
            + theAccountValueOf(accountId)
            + ")",
        theParametersOf(auditId, accountId));
  }

  private static String theAccountColumnOf(@Nullable String accountId) {
    return accountId == null ? "" : ", account_id";
  }

  private static String theAccountValueOf(@Nullable String accountId) {
    return accountId == null ? "" : ", ?";
  }

  private static Object[] theParametersOf(String auditId, @Nullable String accountId) {
    return accountId == null ? new Object[] {auditId, RUN} : new Object[] {auditId, RUN, accountId};
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
