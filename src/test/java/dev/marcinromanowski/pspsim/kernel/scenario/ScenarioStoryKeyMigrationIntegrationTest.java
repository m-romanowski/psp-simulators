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

class ScenarioStoryKeyMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_the_story_key";

  private static final MigrationVersion BEFORE_A_KEY_NAMED_ITS_SPACE =
      MigrationVersion.fromVersion("23");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseHoldingStatesOfBothOldShapes() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("17")).migrate();
    stateOf("capture-retries", "pi_sim_123", "asked");
    stateOf("hold-retries", null, "asked");
    flyway(BEFORE_A_KEY_NAMED_ITS_SPACE).migrate();
  }

  @Test
  void a_state_that_belonged_to_a_whole_test_run_rather_than_to_a_flow_is_gone() {
    assertThat(storedStates()).containsExactly("capture-retries");
  }

  @Test
  void a_state_that_named_the_object_it_was_about_keeps_it_as_the_flow_it_is_keyed_on() {
    assertThat(
            jdbc.queryForList(
                "select story, story_key, state from "
                    + SCHEMA
                    + ".simulator_scenario_story_state"))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).containsEntry("story", "capture-retries");
              assertThat(row).containsEntry("story_key", "pi_sim_123");
              assertThat(row).containsEntry("state", "asked");
            });
  }

  @Test
  void the_table_no_longer_admits_a_story_that_belongs_to_no_flow() {
    assertThatThrownBy(() -> stateOfTheMigratedTable("lookup-retries", null, "asked"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest(name = "neither the table nor a request admits a key made of code point {0}")
  @ValueSource(ints = {0x0009, 0x000B, 0x001F, 0x0020, 0x2003, 0x2028, 0x3000})
  void the_table_no_longer_admits_a_flow_key_that_names_nothing(int whitespace) {
    String namingNothing = Character.toString(whitespace).repeat(2);

    assertThatThrownBy(() -> stateOfTheMigratedTable("lookup-retries", namingNothing, "asked"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aRequestKeyedOn(namingNothing))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ScenarioRequest aRequestKeyedOn(String storyKey) {
    return new ScenarioRequest(
        ProviderId.of("grow"),
        TestRunId.of("before-the-story-key"),
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

  @Test
  void the_table_still_refuses_a_second_state_of_one_story_against_one_flow() {
    assertThatThrownBy(() -> stateOfTheMigratedTable("capture-retries", "pi_sim_123", "settled"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void two_flows_of_one_test_run_each_hold_their_own_state_of_one_story() {
    stateOfTheMigratedTable("capture-retries", "pi_sim_456", "settled");

    assertThat(
            jdbc.queryForList(
                "select story_key from "
                    + SCHEMA
                    + ".simulator_scenario_story_state where story = 'capture-retries'"
                    + " order by story_key",
                String.class))
        .containsExactly("pi_sim_123", "pi_sim_456");
  }

  private List<String> storedStates() {
    return jdbc.queryForList(
        "select story from " + SCHEMA + ".simulator_scenario_story_state", String.class);
  }

  private void stateOf(String story, String objectId, String state) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_story_state"
            + " (provider, test_run_id, story, object_id, state, updated_at)"
            + " values ('grow', 'before-the-story-key', ?, ?, ?,"
            + " timestamptz '2026-09-07T12:00:00Z')",
        story,
        objectId,
        state);
  }

  private void stateOfTheMigratedTable(String story, String storyKey, String state) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_story_state"
            + " (provider, test_run_id, story, keyed_on, story_key, state, updated_at)"
            + " values ('grow', 'before-the-story-key', ?, 'FLOW', ?, ?,"
            + " timestamptz '2026-09-07T12:00:00Z')",
        story,
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
