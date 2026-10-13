package dev.marcinromanowski.pspsim.provider.azericard;

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

class AzericardStoredCardSourceMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_terminal_lent_its_saved_cards";

  private static final String RUN = "before-a-terminal-lent-its-cards";

  private static final String TERMINAL = "77777777";

  private static final MigrationVersion BEFORE_A_TERMINAL_LENT_ITS_SAVED_CARDS =
      MigrationVersion.fromVersion("82");

  private static final MigrationVersion WHEN_A_TERMINAL_COULD_LEND_THEM =
      MigrationVersion.fromVersion("84");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseTerminalsLentNothing() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_TERMINAL_LENT_ITS_SAVED_CARDS).migrate();
    aTerminalOfTheOldShape("merchant-1");
    flyway(WHEN_A_TERMINAL_COULD_LEND_THEM).migrate();
  }

  @Test
  void a_terminal_configured_before_any_could_lend_its_saved_cards_lends_none_and_reads_back() {
    assertThat(theSourcesConfiguredFor("merchant-1"))
        .as(
            "the column this migration adds is read into a list on every request of the account,"
                + " so a row written before it existed must come out empty rather than null, or"
                + " every request of a run configured before this version raises on the read")
        .isEmpty();
  }

  @Test
  void a_terminal_configured_before_a_settlement_order_could_be_named_signs_as_the_wire_does() {
    assertThat(theSettlementOrderOf("merchant-1"))
        .as(
            "the order a completion is signed in is read on every request of the account, and a"
                + " run configured before that column existed signs the way the gateway a real"
                + " merchant reaches does, which is what it signed before there was a choice")
        .isEqualTo(AzericardSettlementMac.LIVE_WIRE.name());
  }

  @Test
  void a_terminal_written_after_this_version_must_say_what_it_lends_even_if_it_lends_nothing() {
    assertThatThrownBy(() -> aTerminalWithNoSourcesNamedAtAll("merchant-2"))
        .as(
            "the migration drops the default it backfilled with, so the store is the one place"
                + " that decides what a terminal lends and a writer that forgets the column is"
                + " refused rather than quietly filed as lending nothing")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_source_terminal_the_configuration_would_refuse_is_refused_by_this_column_as_well() {
    assertThatThrownBy(
            () ->
                aTerminalLending(
                    "merchant-3",
                    "7".repeat(AzericardTerminal.LONGEST_TERMINAL_THE_STORE_HOLDS + 1)))
        .as(
            "the bound the configuration refuses a source terminal by name for is the width of"
                + " this column, so the two refuse the same value and the sentence the"
                + " configuration gives for it is true")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aTerminalOfTheOldShape(String accountId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_terminal (test_run_id, account_id, terminal_id, merchant_key,"
            + " gateway_key, duplicate_response_code, partial_completion, callback_signature)"
            + " values (?, ?, ?, 'a-merchant-key', 'a-gateway-key', '-1',"
            + " 'CLOSES_THE_AUTHORIZATION', 'VALID')",
        RUN,
        accountId,
        TERMINAL);
  }

  private void aTerminalWithNoSourcesNamedAtAll(String accountId) {
    aTerminalOfTheOldShape(accountId);
  }

  private void aTerminalLending(String accountId, String sourceTerminalId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_terminal (test_run_id, account_id, terminal_id, merchant_key,"
            + " gateway_key, duplicate_response_code, partial_completion, callback_signature,"
            + " stored_card_source_terminal_ids)"
            + " values (?, ?, ?, 'a-merchant-key', 'a-gateway-key', '-1',"
            + " 'CLOSES_THE_AUTHORIZATION', 'VALID', ?)",
        RUN,
        accountId,
        TERMINAL,
        new String[] {sourceTerminalId});
  }

  private String theSettlementOrderOf(String accountId) {
    return jdbc.queryForObject(
        "select settlement_mac from " + SCHEMA + ".azericard_terminal where account_id = ?",
        String.class,
        accountId);
  }

  private List<String> theSourcesConfiguredFor(String accountId) {
    return jdbc.queryForObject(
        "select stored_card_source_terminal_ids from "
            + SCHEMA
            + ".azericard_terminal where account_id = ?",
        (rs, row) -> List.of((String[]) rs.getArray(1).getArray()),
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
