package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
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

class ScenarioRuleKeyingMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_the_rule_declared_its_key";

  private static final String RUN = "before-the-rule-declared-its-key";

  private static final String STORY = "capture-retries";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWrittenBeforeARuleDeclaredItsKey() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("19")).migrate();
    ruleOfTheOldShape("scn_step", STORY, "Started", "asked");
    ruleOfTheOldShape("scn_plain", null, null, null);
    stateOfTheOldShape(STORY, "process:100001", "asked");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_step_written_before_a_rule_could_declare_a_key_is_keyed_on_the_flow() {
    assertThat(keyedOnOf("scn_step")).isEqualTo("FLOW");
  }

  @Test
  void a_rule_that_is_no_step_of_a_story_is_keyed_on_nothing() {
    assertThat(keyedOnOf("scn_plain")).isNull();
  }

  @Test
  void a_state_written_before_a_rule_could_declare_a_key_is_held_against_the_flow() {
    assertThat(
            jdbc.queryForList(
                "select keyed_on, story_key from " + SCHEMA + ".simulator_scenario_story_state"))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).containsEntry("keyed_on", "FLOW");
              assertThat(row).containsEntry("story_key", "process:100001");
            });
  }

  @Test
  void the_migrated_table_refuses_a_step_that_declares_no_key_at_all() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".simulator_scenario_rule set keyed_on = null"
                        + " where scenario_id = 'scn_step'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_refuses_a_key_on_a_rule_that_is_no_step_of_a_story() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".simulator_scenario_rule set keyed_on = 'FLOW'"
                        + " where scenario_id = 'scn_plain'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest(name = "neither table admits a story keyed on {0}")
  @ValueSource(strings = {"OBJECT", "flow", "PROCESS", ""})
  void neither_table_admits_a_key_this_simulator_never_keys_a_story_on(String named) {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".simulator_scenario_rule set keyed_on = ?"
                        + " where scenario_id = 'scn_step'",
                    named))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> stateOfTheMigratedTable(STORY, named, "process:100002", "asked"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_flow_and_a_test_run_that_spell_one_key_are_two_instances_of_one_story() {
    stateOfTheMigratedTable(STORY, "FLOW", "test-run:" + RUN, "asked");
    stateOfTheMigratedTable(STORY, "TEST_RUN", "test-run:" + RUN, "settled");

    assertThat(
            jdbc.queryForList(
                "select keyed_on from "
                    + SCHEMA
                    + ".simulator_scenario_story_state where story_key = ? order by keyed_on",
                String.class,
                "test-run:" + RUN))
        .containsExactly("FLOW", "TEST_RUN");
  }

  @Test
  void the_migrated_table_still_refuses_a_second_state_of_one_story_against_one_key() {
    assertThatThrownBy(() -> stateOfTheMigratedTable(STORY, "FLOW", "process:100001", "settled"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String keyedOnOf(String scenarioId) {
    return jdbc.queryForObject(
        "select keyed_on from " + SCHEMA + ".simulator_scenario_rule where scenario_id = ?",
        String.class,
        scenarioId);
  }

  private void ruleOfTheOldShape(
      String scenarioId, String story, String whenState, String thenState) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_rule"
            + " (scenario_id, provider, test_run_id, priority, match_header, action_type,"
            + "  action_provider_code, story, when_state, then_state, created_at)"
            + " values (?, 'grow', ?, 0, '{}', 'TERMINAL_FAILURE', '813', ?, ?, ?,"
            + " timestamptz '2026-09-07T12:00:00Z')",
        scenarioId,
        RUN,
        story,
        whenState,
        thenState);
  }

  private void stateOfTheOldShape(String story, String storyKey, String state) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_story_state"
            + " (provider, test_run_id, story, story_key, state, updated_at)"
            + " values ('grow', ?, ?, ?, ?, timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        story,
        storyKey,
        state);
  }

  private void stateOfTheMigratedTable(
      String story, String keyedOn, String storyKey, String state) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_story_state"
            + " (provider, test_run_id, story, keyed_on, story_key, state, updated_at)"
            + " values ('grow', ?, ?, ?, ?, ?, timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        story,
        keyedOn,
        storyKey,
        state);
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
