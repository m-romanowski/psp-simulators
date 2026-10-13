package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class ScenarioRuleScheduledOutcomeMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_rule_scheduled_an_outcome";

  private static final String RUN = "before-a-rule-scheduled-an-outcome";

  private static final MigrationVersion BEFORE_A_RULE_SCHEDULED_AN_OUTCOME =
      MigrationVersion.fromVersion("57");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseRulesAllCameToRestAtOnce() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_RULE_SCHEDULED_AN_OUTCOME).migrate();
    ruleOfTheOldShape("scn_success", "FIXED_SUCCESS", null, null, null, null);
    ruleOfTheOldShape("scn_decline", "TERMINAL_FAILURE", "813", null, null, null);
    ruleOfTheOldShape("scn_dropped", "TRANSPORT_FAULT", null, "DROPPED_CONNECTION", null, null);
    ruleOfTheOldShape("scn_held", "TRANSPORT_FAULT", null, "DELAYED_RESPONSE", null, 2000L);
    ruleOfTheOldShape(
        "scn_unreached", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION", null, "SERVER_ERROR", 502, null);
    ruleOfTheOldShape("scn_duplicated", "DELIVERY_DUPLICATED", null, null, null, null);
    ruleOfTheOldShape("scn_delayed", "DELIVERY_DELAYED", null, null, null, 5000L);
    ruleOfTheOldShape("scn_never_sent", "DELIVERY_DROPPED", null, null, null, null);
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void every_rule_written_before_an_outcome_could_be_scheduled_survives_the_migration() {
    assertThat(everyRuleHeld())
        .containsExactlyInAnyOrder(
            "scn_success",
            "scn_decline",
            "scn_dropped",
            "scn_held",
            "scn_unreached",
            "scn_duplicated",
            "scn_delayed",
            "scn_never_sent");
  }

  @Test
  void a_rule_written_before_an_outcome_could_be_scheduled_schedules_none() {
    assertThat(momentsOwed()).containsOnlyNulls();
  }

  @Test
  void the_migrated_table_admits_a_processing_state_that_names_the_moment_it_comes_to_rest_in() {
    pendingRule("scn_in_flight", "POST /v1/payment_intents", 5000L, null);
    pendingRule("scn_in_flight_to_fail", "POST /v1/payment_intents", 5000L, "generic_decline");

    assertThat(everyRuleHeld()).contains("scn_in_flight", "scn_in_flight_to_fail");
  }

  @Test
  void the_migrated_table_refuses_a_processing_state_with_no_moment_to_come_to_rest_in() {
    assertThatThrownBy(() -> pendingRule("scn_never_lands", "POST /v1/payment_intents", null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_refuses_a_processing_state_that_names_no_operation_to_answer_it() {
    assertThatThrownBy(() -> pendingRule("scn_every_call", null, 5000L, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest(name = "an outcome scheduled {0} ms away is refused by the table")
  @ValueSource(longs = {0L, 3_600_001L})
  void the_migrated_table_refuses_an_outcome_no_test_run_could_wait_for(long millis) {
    assertThatThrownBy(
            () -> pendingRule("scn_beyond_" + millis, "POST /v1/payment_intents", millis, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_refuses_a_moment_to_come_to_rest_in_on_an_action_that_rests_at_once() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".simulator_scenario_rule (scenario_id, provider, test_run_id, priority,"
                        + " match_header, action_type, action_settles_after_ms, created_at)"
                        + " values ('scn_settling_success', 'stripe', ?, 0, '{}', 'FIXED_SUCCESS',"
                        + " 5000, timestamptz '2026-09-07T12:00:00Z')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void pendingRule(
      String scenarioId, String operation, Long settlesAfterMs, String providerCode) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_rule (scenario_id, provider, test_run_id, priority,"
            + " match_header, match_operation, action_type, action_provider_code,"
            + " action_settles_after_ms, created_at)"
            + " values (?, 'stripe', ?, 0, '{}', ?, 'PROCESSING_THEN_TERMINAL', ?, ?,"
            + " timestamptz '2026-09-07T12:00:00Z')",
        scenarioId,
        RUN,
        operation,
        providerCode,
        settlesAfterMs);
  }

  private void ruleOfTheOldShape(
      String scenarioId,
      String actionType,
      String providerCode,
      String fault,
      Integer httpStatus,
      Long delayMs) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_rule (scenario_id, provider, test_run_id, priority,"
            + " match_header, action_type, action_provider_code, action_fault,"
            + " action_http_status, action_delay_ms, created_at)"
            + " values (?, 'grow', ?, 0, '{}', ?, ?, ?, ?, ?,"
            + " timestamptz '2026-09-07T12:00:00Z')",
        scenarioId,
        RUN,
        actionType,
        providerCode,
        fault,
        httpStatus,
        delayMs);
  }

  private List<String> everyRuleHeld() {
    return jdbc.queryForList(
        "select scenario_id from " + SCHEMA + ".simulator_scenario_rule", String.class);
  }

  private List<Long> momentsOwed() {
    return jdbc.queryForList(
        "select action_settles_after_ms from " + SCHEMA + ".simulator_scenario_rule", Long.class);
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
