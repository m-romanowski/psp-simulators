package dev.marcinromanowski.pspsim.provider.worldline;

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

class WorldlineHostedTokenizationIdMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_session_id_named_one_run";

  private static final String ONE_RUN = "before-the-page-one";

  private static final String ANOTHER_RUN = "before-the-page-another";

  private static final String ONE_ID = "a".repeat(32);

  private static final String ANOTHER_ID = "b".repeat(32);

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverSessionsWrittenWhileAnIdWasUniqueOnlyWithinItsRun() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("70")).migrate();
    aSessionInTheOldShape(ONE_RUN, ONE_ID);
    aSessionInTheOldShape(ANOTHER_RUN, ANOTHER_ID);
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void the_sessions_of_two_runs_written_before_the_page_existed_survive_the_migration() {
    assertThat(runHolding(ONE_ID)).isEqualTo(ONE_RUN);
    assertThat(runHolding(ANOTHER_ID)).isEqualTo(ANOTHER_RUN);
  }

  @Test
  void a_second_run_can_no_longer_hold_a_session_under_an_id_another_run_holds() {
    assertThatThrownBy(() -> aSession(ANOTHER_RUN, ONE_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_new_id_is_still_taken_in_a_run_that_already_holds_other_sessions() {
    aSession(ONE_RUN, "c".repeat(32));

    assertThat(runHolding("c".repeat(32))).isEqualTo(ONE_RUN);
  }

  @Test
  void a_session_written_in_the_old_shape_no_longer_stores_the_url_it_was_written_with() {
    assertThat(columnExists("hosted_tokenization_url")).isFalse();
    assertThat(columnExists("partial_redirect_url")).isFalse();
    assertThat(runHolding(ONE_ID)).isEqualTo(ONE_RUN);
    assertThat(
            CreateHostedTokenization.theHostedTokenizationUrlOf(
                WorldlineWire.PUBLIC_BASE_URL, ONE_ID))
        .isEqualTo(WorldlineWire.PUBLIC_BASE_URL + WorldlineHostedTokenizationPage.pathOf(ONE_ID))
        .isNotEqualTo("https://x");
  }

  private boolean columnExists(String column) {
    Integer found =
        jdbc.queryForObject(
            "select count(*) from information_schema.columns where table_schema = ? and"
                + " table_name = 'worldline_hosted_tokenization' and column_name = ?",
            Integer.class,
            SCHEMA,
            column);
    return found != null && found > 0;
  }

  private String runHolding(String hostedTokenizationId) {
    return jdbc.queryForObject(
        "select test_run_id from "
            + SCHEMA
            + ".worldline_hosted_tokenization where hosted_tokenization_id = ?",
        String.class,
        hostedTokenizationId);
  }

  private void aSessionInTheOldShape(String run, String id) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_hosted_tokenization (test_run_id, hosted_tokenization_id, account_id,"
            + " merchant_id, native_status, hosted_tokenization_url, partial_redirect_url,"
            + " expires_at, origin, created_at, updated_at)"
            + " values (?, ?, 'account-1', 'pspid-1', 'AWAITING_THE_PAYER', 'https://x', 'x',"
            + " timestamptz '2026-09-22T15:00:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        run,
        id);
  }

  private void aSession(String run, String id) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_hosted_tokenization (test_run_id, hosted_tokenization_id, account_id,"
            + " merchant_id, native_status, expires_at, origin, created_at, updated_at)"
            + " values (?, ?, 'account-1', 'pspid-1', 'AWAITING_THE_PAYER',"
            + " timestamptz '2026-09-22T15:00:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        run,
        id);
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
