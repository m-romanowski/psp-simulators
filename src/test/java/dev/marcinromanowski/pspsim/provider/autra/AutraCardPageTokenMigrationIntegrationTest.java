package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AutraCardPageTokenMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_token_found_a_card_page";

  private static final String RUN = "before-a-token-found-a-card-page";

  private static final String ANOTHER_RUN = "another-run-before-a-token-found-a-card-page";

  private static final String MINTED = "0123456789abcdef01234567";

  private static final String SESSION = "03294670-67f0-4dca-a121-cf0e0fb2a92f";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverSessionsWrittenWhileTheirPageWasNotServed() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("71")).migrate();
    aSessionOfTheOldShape(
        RUN, SESSION, "https://cards.autra.io/tokenize/sessions/" + SESSION + "#token=" + MINTED);
    aSessionOfTheOldShape(ANOTHER_RUN, SESSION, "https://cards.autra.io/tokenize/sessions/x");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_session_keeps_the_token_its_published_url_already_carried() {
    assertThat(pageTokenOf(RUN)).isEqualTo(MINTED);
  }

  @Test
  void a_session_whose_url_carried_no_token_is_given_a_random_one_its_ids_do_not_spell() {
    assertThat(pageTokenOf(ANOTHER_RUN))
        .matches("[0-9a-f]{32}")
        .doesNotContain(ANOTHER_RUN)
        .doesNotContain(SESSION)
        .isNotEqualTo(pageTokenOf(RUN));
  }

  @Test
  void two_sessions_cannot_share_one_page_token_once_the_migration_has_run() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".autra_card_session set page_token = ? where test_run_id = ?",
                    MINTED,
                    ANOTHER_RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_session_written_after_the_migration_must_carry_a_page_token() {
    assertThatThrownBy(() -> aSessionCarryingNoPageToken("a-third-run", SESSION))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_url_the_token_was_taken_out_of_is_no_column_of_a_migrated_session_any_more() {
    assertThat(theColumnsOfAMigratedSession())
        .as(
            "the address of the card page is built from the token beside it and the base url this"
                + " simulator publishes itself under, so once this backfill had taken the token"
                + " out of it the column held a value nothing read back. It is dropped, and a row"
                + " written while it was the only place the token lived still carries that token")
        .contains("page_token")
        .doesNotContain("hosted_card_url");
    assertThat(pageTokenOf(RUN)).isEqualTo(MINTED);
  }

  private List<String> theColumnsOfAMigratedSession() {
    return jdbc.queryForList(
        "select column_name from information_schema.columns"
            + " where table_schema = ? and table_name = 'autra_card_session'",
        String.class,
        SCHEMA);
  }

  private String pageTokenOf(String run) {
    return jdbc.queryForObject(
        "select page_token from " + SCHEMA + ".autra_card_session where test_run_id = ?",
        String.class,
        run);
  }

  private void aSessionCarryingNoPageToken(String run, String sessionId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_card_session (test_run_id, card_session_id, account_id, document_id,"
            + " native_status, expires_at, origin, created_at, updated_at)"
            + " values (?, ?, 'merchant-1', '25055662000190', 'PENDING',"
            + " timestamptz '2026-09-22T12:15:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        run,
        sessionId);
  }

  private void aSessionOfTheOldShape(String run, String sessionId, String hostedCardUrl) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_card_session (test_run_id, card_session_id, account_id, document_id,"
            + " native_status, hosted_card_url, expires_at, origin, created_at, updated_at)"
            + " values (?, ?, 'merchant-1', '25055662000190', 'PENDING', ?,"
            + " timestamptz '2026-09-22T12:15:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        run,
        sessionId,
        hostedCardUrl);
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
