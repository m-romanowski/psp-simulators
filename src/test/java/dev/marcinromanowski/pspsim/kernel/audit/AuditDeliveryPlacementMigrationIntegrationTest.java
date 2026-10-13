package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditDeliveryPlacementMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_the_placement";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseHoldingDeliveriesWrittenBeforeThePlacementExisted() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("16")).migrate();
    damagedDeliveryOf("aud_truncated", "TRUNCATED_BODY");
    damagedDeliveryOf("aud_dropped", "DROPPED_CONNECTION");
    wholeDeliveryOf("aud_whole");
    inboundRequestOf("aud_request");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_delivery_written_before_the_placement_existed_is_one_the_operation_had_already_run() {
    assertThat(placementOf("aud_truncated")).isEqualTo("AFTER_THE_OPERATION");
    assertThat(placementOf("aud_dropped")).isEqualTo("AFTER_THE_OPERATION");
  }

  @Test
  void a_row_that_carried_no_fault_is_left_with_no_placement_to_name() {
    assertThat(placementOf("aud_whole")).isNull();
    assertThat(placementOf("aud_request")).isNull();
  }

  @ParameterizedTest(name = "the table refuses {0}")
  @MethodSource("theDeliveriesNoReaderCouldReadBack")
  void a_delivery_a_reader_could_not_read_back_is_refused_by_the_table(
      String name, @Nullable String fault, @Nullable String placement, @Nullable String outcome) {
    assertThatThrownBy(() -> deliveryOf("aud_refused", fault, placement, outcome))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static List<Arguments> theDeliveriesNoReaderCouldReadBack() {
    return List.of(
        Arguments.of(
            "a fault kind no TransportFaultKind names",
            "NOT_A_FAULT",
            "AFTER_THE_OPERATION",
            "SUCCESS"),
        Arguments.of("a placement no FaultPlacement names", "SERVER_ERROR", "NOWHERE", "UNKNOWN"),
        Arguments.of("a fault with no placement beside it", "SERVER_ERROR", null, "UNKNOWN"),
        Arguments.of("a placement with no fault beside it", null, "AFTER_THE_OPERATION", "SUCCESS"),
        Arguments.of(
            "a delayed response answered instead of the operation that renders its bytes",
            "DELAYED_RESPONSE",
            "INSTEAD_OF_THE_OPERATION",
            "UNKNOWN"),
        Arguments.of(
            "a truncation answered instead of the operation that renders its bytes",
            "TRUNCATED_BODY",
            "INSTEAD_OF_THE_OPERATION",
            "UNKNOWN"),
        Arguments.of(
            "an operation that never ran on which the provider still reached a verdict",
            "SERVER_ERROR",
            "INSTEAD_OF_THE_OPERATION",
            "SUCCESS"));
  }

  @Test
  void the_one_pairing_a_fault_answered_instead_of_the_operation_may_take_is_stored() {
    assertThatCode(
            () ->
                deliveryOf("aud_unreached", "SERVER_ERROR", "INSTEAD_OF_THE_OPERATION", "UNKNOWN"))
        .doesNotThrowAnyException();
    assertThat(placementOf("aud_unreached")).isEqualTo("INSTEAD_OF_THE_OPERATION");
  }

  private @Nullable String placementOf(String auditId) {
    return jdbc.queryForObject(
        "select delivered_fault_placement from "
            + SCHEMA
            + ".simulator_audit_record where audit_id = ?",
        String.class,
        auditId);
  }

  private void damagedDeliveryOf(String auditId, String fault) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, direction, outcome,"
            + " method, path, attempt, created_at, sanitized_headers, sanitized_body,"
            + " http_status, delivered_fault, delivered_declared_length, delivered_body_bytes,"
            + " delivered_held_for_ms)"
            + " values (?, 'grow', 'before-the-placement', 'OUTBOUND_RESPONSE', 'SUCCESS', 'POST',"
            + " '/api/light/server/1.0/settleSuspendedTransaction', 1,"
            + " timestamptz '2026-09-07T12:00:00Z', '{}', '{}', 200, ?, 120, 60, 0)",
        auditId,
        fault);
  }

  private void wholeDeliveryOf(String auditId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, direction, outcome,"
            + " method, path, attempt, created_at, sanitized_headers, sanitized_body,"
            + " http_status, delivered_declared_length, delivered_body_bytes,"
            + " delivered_held_for_ms)"
            + " values (?, 'grow', 'before-the-placement', 'OUTBOUND_RESPONSE', 'SUCCESS', 'POST',"
            + " '/api/light/server/1.0/settleSuspendedTransaction', 1,"
            + " timestamptz '2026-09-07T12:00:00Z', '{}', '{}', 200, 120, 120, 0)",
        auditId);
  }

  private void inboundRequestOf(String auditId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, direction, method, path,"
            + " attempt, created_at, sanitized_headers, sanitized_body)"
            + " values (?, 'grow', 'before-the-placement', 'INBOUND_REQUEST', 'POST',"
            + " '/api/light/server/1.0/settleSuspendedTransaction', 1,"
            + " timestamptz '2026-09-07T12:00:00Z', '{}', '{}')",
        auditId);
  }

  private void deliveryOf(
      String auditId,
      @Nullable String fault,
      @Nullable String placement,
      @Nullable String outcome) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, origin, direction,"
            + " outcome, method, path, attempt, created_at, sanitized_headers, sanitized_body,"
            + " http_status, delivered_fault, delivered_fault_placement,"
            + " delivered_declared_length, delivered_body_bytes, delivered_held_for_ms)"
            + " values (?, 'grow', 'before-the-placement', 'PROVIDER_API', 'OUTBOUND_RESPONSE',"
            + " ?, 'POST',"
            + " '/api/light/server/1.0/settleSuspendedTransaction', 1,"
            + " timestamptz '2026-09-07T12:00:00Z', '{}', '{}', 502, ?, ?, 0, 0, 0)",
        auditId,
        outcome,
        fault,
        placement);
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
