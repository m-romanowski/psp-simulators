package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
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

class ScenarioStoryKeySpaceMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_key_named_its_space";

  private static final String RUN = "before-a-key-named-its-space";

  private static final String STORY = "capture-retries";

  private static final MigrationVersion BEFORE_A_KEY_NAMED_ITS_SPACE =
      MigrationVersion.fromVersion("23");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseKeysNamedNoSpace() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_KEY_NAMED_ITS_SPACE).migrate();
    stateOfTheOldShape(STORY, "FLOW", "process:100001", "asked");
    stateOfTheOldShape(STORY, "FLOW", "100001", "settled");
    stateOfTheOldShape(STORY, "FLOW", "8:7777777720220628084800", "declined");
    stateOfTheOldShape(STORY, "TEST_RUN", RUN, "counted");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_key_that_already_named_its_space_is_left_as_the_provider_spelled_it() {
    assertThat(stateUnder("process:100001")).isEqualTo("asked");
  }

  @Test
  void a_key_that_named_only_the_object_it_was_about_is_gone_rather_than_guessed_into_a_space() {
    assertThat(everyKeyHeld()).doesNotContain("100001");
  }

  @Test
  void a_key_that_counted_a_field_where_a_space_is_named_is_gone_as_well() {
    assertThat(everyKeyHeld()).doesNotContain("8:7777777720220628084800");
  }

  @Test
  void a_state_of_a_whole_test_run_is_kept_and_keyed_in_the_space_the_kernel_names_runs_in() {
    assertThat(stateUnder("test-run:" + RUN)).isEqualTo("counted");
    assertThat(everyKeyHeld()).doesNotContain(RUN);
  }

  @ParameterizedTest(name = "neither the table nor a request admits the key {0}")
  @ValueSource(
      strings = {
        "100001",
        "8-7777777720220628084800",
        ":100001",
        "Process:100001",
        "process:",
        "process: "
      })
  void neither_the_table_nor_a_request_admits_a_key_that_names_no_identifier_space(String unnamed) {
    assertThatThrownBy(() -> stateOfTheMigratedTable(STORY, "FLOW", unnamed, "asked"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aRequestKeyedOn(unnamed)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "both the table and a request admit the key {0}")
  @ValueSource(strings = {"process:100002", "payment:8-7777777720220628084800", "test-run:" + RUN})
  void both_the_table_and_a_request_admit_a_key_that_names_the_space_it_was_drawn_from(
      String named) {
    stateOfTheMigratedTable("lookup-retries", "FLOW", named, "asked");

    assertThat(aRequestKeyedOn(named).storyKey()).isEqualTo(named);
    assertThat(everyKeyHeld()).contains(named);
  }

  private static ScenarioRequest aRequestKeyedOn(String storyKey) {
    return new ScenarioRequest(
        ProviderId.of("grow"),
        TestRunId.of(RUN),
        storyKey,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Map.of());
  }

  private String stateUnder(String storyKey) {
    return jdbc.queryForObject(
        "select state from " + SCHEMA + ".simulator_scenario_story_state where story_key = ?",
        String.class,
        storyKey);
  }

  private List<String> everyKeyHeld() {
    return jdbc.queryForList(
        "select story_key from " + SCHEMA + ".simulator_scenario_story_state", String.class);
  }

  private void stateOfTheOldShape(String story, String keyedOn, String storyKey, String state) {
    stateOfTheMigratedTable(story, keyedOn, storyKey, state);
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
