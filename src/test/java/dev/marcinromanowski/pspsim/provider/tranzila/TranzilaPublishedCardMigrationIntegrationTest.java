package dev.marcinromanowski.pspsim.provider.tranzila;

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

class TranzilaPublishedCardMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_tranzila_kept_the_published_card";

  private static final String RUN = "before-published-cards";

  private static final MigrationVersion BEFORE = MigrationVersion.fromVersion("90");

  private static final MigrationVersion AFTER = MigrationVersion.fromVersion("91");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverRowsMadeWhileAMaskDecidedThe3dsOutcome() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE).migrate();
    jdbc.update(anOldTransaction(10000001, "'t000000000000010022'", "490763xxxxxx0022"));
    jdbc.update(anOldTransaction(10000002, "'t000000000000029022'", "490763xxxxxx9022"));
    jdbc.update(anOldTransaction(10000003, "'t000000000000035005'", "491891xxxxxx5005"));
    jdbc.update(anOldTransaction(10000004, "'t5d55d2922ca4029022'", "xxxxxxxxxxxx9022"));
    jdbc.update(anOldTransaction(10000005, "'t000000000000054245'", "458021xxxxxx4245"));
    jdbc.update(anOldTransaction(10000006, "'t000000000000060022'", "xxxxxxxxxxxx0022"));
    jdbc.update(anOldChallenge("3ds_" + "0".repeat(39) + "1", "491891xxxxxx5005"));
    jdbc.update(anOldChallenge("3ds_" + "0".repeat(39) + "2", "458021xxxxxx4245"));
    jdbc.update(anOldPage("directng_" + "0".repeat(23) + "1", "'t5d55d2922ca4029022'"));
    jdbc.update(anOldPage("directng_" + "0".repeat(23) + "2", "'t000000000000079022'"));
    flyway(AFTER).migrate();
  }

  @Test
  void a_row_recorded_before_v91_is_read_as_the_published_card_its_mask_or_page_token_looks_like() {
    assertThat(
            jdbc.queryForList(
                "select published_as from "
                    + SCHEMA
                    + ".tranzila_transaction order by transaction_id",
                String.class))
        .containsExactly("FAILED", "AUTHENTICATED", "CHALLENGED", "AUTHENTICATED", null, null);
    assertThat(
            jdbc.queryForList(
                "select published_as from " + SCHEMA + ".tranzila_challenge order by track_id",
                String.class))
        .containsExactly("CHALLENGED", null);
    assertThat(
            jdbc.queryForList(
                "select published_as from " + SCHEMA + ".tranzila_page order by page_id",
                String.class))
        .containsExactly("AUTHENTICATED", null);
    TranzilaTransaction read =
        jdbc.queryForObject(
            "select * from " + SCHEMA + ".tranzila_transaction where transaction_id = 10000001",
            JdbcTranzilaTransactions::toTransaction);
    assertThat(read).isNotNull();
    assertThat(read.card().theThreeDSecureOutcome()).contains(TranzilaThreeDSecureOutcome.FAILED);
  }

  @Test
  void a_published_outcome_is_one_of_the_three_published_and_nothing_else() {
    for (String table : List.of("tranzila_transaction", "tranzila_challenge", "tranzila_page")) {
      for (String refused : List.of("'authenticated'", "'4907639999990022'", "''")) {
        assertThatThrownBy(
                () ->
                    jdbc.update(
                        "update " + SCHEMA + "." + table + " set published_as = " + refused))
            .as(table + " " + refused)
            .isInstanceOf(DataIntegrityViolationException.class);
      }
    }
  }

  private String anOldTransaction(long transactionId, String token, String mask) {
    return "insert into "
        + SCHEMA
        + ".tranzila_transaction (test_run_id, transaction_id, root_txn_id, account_id,"
        + " terminal_name, txn_type, processor_response_code, amount, currency_code,"
        + " auth_number, token, card_mask, card_type, expiry_month, expiry_year, response_cvv,"
        + " response_id, origin, created_at) values ('"
        + RUN
        + "', "
        + transactionId
        + ", "
        + transactionId
        + ", 'a-1', 'myterminal', 'debit', '000', '10.53', 'ILS', '1000001', "
        + token
        + ", '"
        + mask
        + "', 2, 7, 30, '0', '0', 'PROVIDER_API', timestamptz '2026-10-13T12:00:00Z')";
  }

  private String anOldChallenge(String trackId, String mask) {
    return "insert into "
        + SCHEMA
        + ".tranzila_challenge (track_id, test_run_id, account_id, terminal_name,"
        + " transaction_id, txn_type, amount, currency_code, token, card_mask, card_type,"
        + " expiry_month, expiry_year, response_cvv, response_id, force_txn_on_3ds_fail,"
        + " original_request, redirect_params, origin, created_at) values ('"
        + trackId
        + "', '"
        + RUN
        + "', 'a-1', 'myterminal', 10000009, 'debit', '10.53', 'ILS', 't000000000000095005', '"
        + mask
        + "', 2, 7, 30, '0', '0', false, '{}', '{}', 'PROVIDER_API',"
        + " timestamptz '2026-10-13T12:00:00Z')";
  }

  private String anOldPage(String pageId, String token) {
    return "insert into "
        + SCHEMA
        + ".tranzila_page (page_id, test_run_id, account_id, terminal_name, sum, currency,"
        + " tranmode, echoed, origin, created_at, outcome, response, token, card_mask, card_type,"
        + " settled_at) values ('"
        + pageId
        + "', '"
        + RUN
        + "', 'a-1', 'myterminal', '10', '1', 'K', '{}', 'PROVIDER_API',"
        + " timestamptz '2026-10-13T12:00:00Z', 'PAID', '000', "
        + token
        + ", 'xxxxxxxxxxxx9022', 2, timestamptz '2026-10-13T12:01:00Z')";
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
