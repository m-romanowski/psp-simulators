package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ScenarioRuleMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_the_story";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseHoldingRulesOfTheRemovedTypes() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("15")).migrate();
    ruleOf("scn_transient", "TRANSIENT_THEN_SUCCESS", "813", 2);
    ruleOf("scn_faulting", "TRANSPORT_FAULT_THEN_SUCCESS", null, 1);
    ruleOf("scn_decline", "TERMINAL_FAILURE", "701", null);
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_rule_of_a_type_this_simulator_can_no_longer_perform_is_gone() {
    assertThat(storedRules()).containsExactly("scn_decline");
  }

  @Test
  void a_rule_of_a_type_that_survived_keeps_its_action_and_names_no_story() {
    assertThat(
            jdbc.queryForList(
                "select action_type, action_provider_code, story, when_state, then_state from "
                    + SCHEMA
                    + ".simulator_scenario_rule"))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).containsEntry("action_type", "TERMINAL_FAILURE");
              assertThat(row).containsEntry("action_provider_code", "701");
              assertThat(row).containsEntry("story", null);
              assertThat(row).containsEntry("when_state", null);
              assertThat(row).containsEntry("then_state", null);
            });
  }

  @Test
  void the_columns_the_removed_types_counted_with_are_gone_and_the_story_columns_are_there() {
    assertThat(columnsOfTheRuleTable())
        .doesNotContain("failures_before_success", "failures_consumed")
        .contains("story", "when_state", "then_state");
  }

  @Test
  void the_story_state_table_arrives_empty_and_ready() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".simulator_scenario_story_state",
                Integer.class))
        .isZero();
  }

  private List<String> storedRules() {
    return jdbc.queryForList(
        "select scenario_id from " + SCHEMA + ".simulator_scenario_rule", String.class);
  }

  private List<String> columnsOfTheRuleTable() {
    return jdbc.queryForList(
        "select column_name from information_schema.columns"
            + " where table_schema = ? and table_name = 'simulator_scenario_rule'",
        String.class,
        SCHEMA);
  }

  private void ruleOf(
      String scenarioId, String actionType, String providerCode, Integer failuresBeforeSuccess) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_rule (scenario_id, provider, test_run_id, priority,"
            + " match_header, action_type, action_provider_code, action_fault,"
            + " action_http_status, failures_before_success, failures_consumed, created_at)"
            + " values (?, 'grow', 'before-the-story', 0, '{}', ?, ?, ?, ?, ?, 0,"
            + " timestamptz '2026-09-07T12:00:00Z')",
        scenarioId,
        actionType,
        providerCode,
        providerCode == null ? "SERVER_ERROR" : null,
        providerCode == null ? Integer.valueOf(503) : null,
        failuresBeforeSuccess);
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
