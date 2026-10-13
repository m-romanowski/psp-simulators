package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class GrowWireFormMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_an_account_named_its_wire_form";

  private static final String RUN = "before-an-account-named-its-wire-form";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverAnAccountWrittenBeforeTheWireFormWasASetting() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("40")).migrate();
    anAccountOfTheOldShape("merchant-1");
    anAccountOfTheOldShape("merchant-2");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void an_account_configured_before_the_setting_existed_answers_in_the_form_the_live_wire_uses() {
    assertThat(
            jdbc.queryForList(
                "select wire_form from " + SCHEMA + ".grow_account order by account_id",
                String.class))
        .containsExactly("LIVE_WIRE", "LIVE_WIRE");
  }

  @Test
  void the_migrated_table_refuses_a_wire_form_no_grow_account_could_read() {
    assertThatThrownBy(() -> anAccountOfTheNewShape("merchant-3", "AS_RAW_AS_YOU_LIKE"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void the_migrated_table_admits_the_form_a_client_built_against_the_published_examples_reads() {
    anAccountOfTheNewShape("merchant-4", "PUBLISHED_EXAMPLE");

    assertThat(
            jdbc.queryForObject(
                "select wire_form from " + SCHEMA + ".grow_account where account_id = ?",
                String.class,
                "merchant-4"))
        .isEqualTo("PUBLISHED_EXAMPLE");
  }

  @Test
  void the_migrated_table_refuses_an_account_that_names_no_wire_form_at_all() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".grow_account (test_run_id, account_id, user_id, page_code,"
                        + " callback_acknowledgement, settlement_ceiling, refund_allowance,"
                        + " refund_excess_rejection, merchant_balance, process_lookup_rejection,"
                        + " capture_window, automatic_void_after)"
                        + " values (?, 'merchant-5', 'user-5', 'page-5', 'HTTP_2XX',"
                        + " 'FRAME_CAPTURE', 'ONE_REFUND', 'ORIGINAL_AMOUNT', 'AVAILABLE',"
                        + " 'PUBLISHED_EXAMPLE', 'PT168H', 'PT240H')",
                    RUN))
        .isInstanceOf(DataAccessException.class);
  }

  private void anAccountOfTheOldShape(String accountId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_account (test_run_id, account_id, user_id, page_code,"
            + " callback_acknowledgement, settlement_ceiling, refund_allowance,"
            + " refund_excess_rejection, merchant_balance, process_lookup_rejection,"
            + " capture_window, automatic_void_after)"
            + " values (?, ?, ?, ?, 'HTTP_2XX', 'FRAME_CAPTURE', 'ONE_REFUND', 'ORIGINAL_AMOUNT',"
            + " 'AVAILABLE', 'PUBLISHED_EXAMPLE', 'PT168H', 'PT240H')",
        RUN,
        accountId,
        "user-" + accountId,
        "page-" + accountId);
  }

  private void anAccountOfTheNewShape(String accountId, String wireForm) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_account (test_run_id, account_id, user_id, page_code,"
            + " callback_acknowledgement, settlement_ceiling, refund_allowance,"
            + " refund_excess_rejection, merchant_balance, process_lookup_rejection, wire_form,"
            + " capture_window, automatic_void_after)"
            + " values (?, ?, ?, ?, 'HTTP_2XX', 'FRAME_CAPTURE', 'ONE_REFUND', 'ORIGINAL_AMOUNT',"
            + " 'AVAILABLE', 'PUBLISHED_EXAMPLE', ?, 'PT168H', 'PT240H')",
        RUN,
        accountId,
        "user-" + accountId,
        "page-" + accountId,
        wireForm);
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
