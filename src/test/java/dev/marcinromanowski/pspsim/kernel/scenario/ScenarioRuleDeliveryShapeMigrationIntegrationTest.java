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

class ScenarioRuleDeliveryShapeMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_rule_shaped_a_delivery";

  private static final String RUN = "before-a-rule-shaped-a-delivery";

  private static final MigrationVersion BEFORE_A_RULE_SHAPED_A_DELIVERY =
      MigrationVersion.fromVersion("35");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseRulesAllAnsweredRequests() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_RULE_SHAPED_A_DELIVERY).migrate();
    ruleOfTheOldShape("scn_success", "FIXED_SUCCESS", null, null, null, null);
    ruleOfTheOldShape("scn_decline", "TERMINAL_FAILURE", "813", null, null, null);
    ruleOfTheOldShape("scn_dropped", "TRANSPORT_FAULT", null, "DROPPED_CONNECTION", null, null);
    ruleOfTheOldShape("scn_held", "TRANSPORT_FAULT", null, "DELAYED_RESPONSE", null, 2000L);
    ruleOfTheOldShape(
        "scn_unreached", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION", null, "SERVER_ERROR", 502, null);
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void every_rule_written_before_a_delivery_could_be_shaped_survives_the_migration() {
    assertThat(everyRuleHeld())
        .containsExactlyInAnyOrder(
            "scn_success", "scn_decline", "scn_dropped", "scn_held", "scn_unreached");
  }

  @Test
  void a_rule_written_before_the_event_type_existed_narrows_on_none() {
    assertThat(eventTypesHeld()).containsOnlyNulls();
  }

  @ParameterizedTest(name = "the table admits a {0} rule that narrows on nothing a request carries")
  @ValueSource(strings = {"DELIVERY_DUPLICATED", "DELIVERY_DROPPED"})
  void the_migrated_table_admits_a_rule_that_shapes_a_delivery(String actionType) {
    shapingRule("scn_" + actionType, actionType, null, "payment_process.completed");

    assertThat(everyRuleHeld()).contains("scn_" + actionType);
  }

  @Test
  void the_migrated_table_admits_a_held_delivery_only_with_the_length_of_the_hold() {
    shapingRule("scn_held_back", "DELIVERY_DELAYED", 5000L, null);

    assertThat(everyRuleHeld()).contains("scn_held_back");
    assertThatThrownBy(() -> shapingRule("scn_lengthless", "DELIVERY_DELAYED", null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_refuses_a_hold_longer_than_a_test_run_would_wait_for() {
    assertThatThrownBy(() -> shapingRule("scn_forever", "DELIVERY_DELAYED", 3_600_001L, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_refuses_a_delivery_rule_that_narrows_on_a_fact_only_a_request_carries() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".simulator_scenario_rule (scenario_id, provider, test_run_id, priority,"
                        + " match_header, match_operation, action_type, created_at)"
                        + " values ('scn_about_a_call', 'grow', ?, 0, '{}',"
                        + " 'refundTransaction', 'DELIVERY_DROPPED',"
                        + " timestamptz '2026-09-07T12:00:00Z')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_refuses_an_event_type_on_a_rule_that_answers_a_request() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".simulator_scenario_rule (scenario_id, provider, test_run_id, priority,"
                        + " match_header, match_event_type, action_type, created_at)"
                        + " values ('scn_reporting', 'grow', ?, 0, '{}',"
                        + " 'payment_process.completed', 'FIXED_SUCCESS',"
                        + " timestamptz '2026-09-07T12:00:00Z')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void shapingRule(String scenarioId, String actionType, Long delayMs, String eventType) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_scenario_rule (scenario_id, provider, test_run_id, priority,"
            + " match_header, match_event_type, action_type, action_delay_ms, created_at)"
            + " values (?, 'grow', ?, 0, '{}', ?, ?, ?, timestamptz '2026-09-07T12:00:00Z')",
        scenarioId,
        RUN,
        eventType,
        actionType,
        delayMs);
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

  private List<String> eventTypesHeld() {
    return jdbc.queryForList(
        "select match_event_type from " + SCHEMA + ".simulator_scenario_rule", String.class);
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
