package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

class AzericardTerminalMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_terminal_had_to_fit_the_payment_store";

  private static final String RUN = "before-a-terminal-fit-the-store";

  private static final String FITS = "77777777";

  private static final String WIDEST =
      "7".repeat(AzericardTerminal.LONGEST_TERMINAL_THE_STORE_HOLDS);

  private static final String TOO_WIDE =
      "7".repeat(AzericardTerminal.LONGEST_TERMINAL_THE_STORE_HOLDS + 1);

  private static final MigrationVersion BEFORE_A_TERMINAL_HAD_TO_FIT =
      MigrationVersion.fromVersion("25");

  private static final MigrationVersion WHEN_A_TERMINAL_HAD_TO_FIT =
      MigrationVersion.fromVersion("26");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatTookATerminalNoPaymentCouldBeKeptUnder() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_TERMINAL_HAD_TO_FIT).migrate();
    accountOfTheOldShape("azericard", "merchant-1", FITS);
    accountOfTheOldShape("azericard", "merchant-2", TOO_WIDE);
    accountOfTheOldShape("grow", "merchant-3", TOO_WIDE);
    flyway(WHEN_A_TERMINAL_HAD_TO_FIT).migrate();
  }

  @Test
  void an_azericard_account_no_request_of_it_could_ever_be_answered_on_is_gone() {
    assertThat(terminalsConfiguredFor("azericard")).containsExactly(FITS);
  }

  @Test
  void an_account_of_a_provider_that_keeps_nothing_under_a_terminal_is_left_as_it_was_configured() {
    assertThat(terminalsConfiguredFor("grow")).containsExactly(TOO_WIDE);
  }

  @Test
  void the_store_a_terminal_is_measured_against_refuses_exactly_what_the_control_api_refuses() {
    assertThatCode(() -> anOrderTraceClaimedBy(WIDEST)).doesNotThrowAnyException();
    assertThatThrownBy(() -> anOrderTraceClaimedBy(TOO_WIDE))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_a_payment_is_kept_in_refuses_exactly_what_the_control_api_refuses() {
    assertThatCode(() -> aPaymentKeptUnder(WIDEST, "INTREF0000000001")).doesNotThrowAnyException();
    assertThatThrownBy(() -> aPaymentKeptUnder(TOO_WIDE, "INTREF0000000002"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void anOrderTraceClaimedBy(String terminalId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_order_trace (test_run_id, terminal_id, trace_day, trace)"
            + " values (?, ?, date '2026-09-10', '084800')",
        RUN,
        terminalId);
  }

  private void aPaymentKeptUnder(String terminalId, String intRef) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_payment (test_run_id, int_ref, terminal_id, merchant_id, order_id,"
            + " trtype, amount, currency, rrn, action_code, response_code, origin, created_at,"
            + " updated_at)"
            + " values (?, ?, ?, 'merchant-1', '20220628084800', '0', '11.48', 'AZN',"
            + " '112676199769', '0', '00', 'PROVIDER_API',"
            + " timestamptz '2026-09-10T12:00:00Z', timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        intRef,
        terminalId);
  }

  private void accountOfTheOldShape(String provider, String merchantId, String terminalId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_provider_account"
            + " (provider, test_run_id, merchant_id, terminal_id, updated_at)"
            + " values (?, ?, ?, ?, timestamptz '2026-09-10T12:00:00Z')",
        provider,
        RUN,
        merchantId,
        terminalId);
  }

  private List<String> terminalsConfiguredFor(String provider) {
    return jdbc.queryForList(
        "select terminal_id from "
            + SCHEMA
            + ".simulator_provider_account where provider = ? order by merchant_id",
        String.class,
        provider);
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
