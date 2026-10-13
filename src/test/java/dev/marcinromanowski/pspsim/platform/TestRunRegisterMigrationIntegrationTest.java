package dev.marcinromanowski.pspsim.platform;

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

class TestRunRegisterMigrationIntegrationTest extends PostgresBackedTest {

  private static final String RESERVED = "UNKNOWN";

  private static final String SCHEMA = "before_the_register";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverALegacyDatabase() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("3")).migrate();
    configureAsTheSimulatorOfTheTimeWouldHave();
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_run_configured_with_accounts_alone_is_in_the_register_afterwards() {
    assertThat(register()).contains("legacy-accounts-only");
  }

  @Test
  void a_run_configured_with_only_a_webhook_target_is_in_the_register_afterwards() {
    assertThat(register()).contains("legacy-webhooks-only");
  }

  @Test
  void a_run_left_holding_only_provider_objects_is_in_the_register_afterwards() {
    assertThat(register()).contains("legacy-objects-only");
  }

  @Test
  void a_run_that_had_pinned_its_clock_keeps_its_row_and_its_pinned_time() {
    assertThat(register()).contains("legacy-pinned");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".simulator_test_run"
                    + " where test_run_id = 'legacy-pinned' and fixed_now is not null",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void a_backfilled_run_follows_the_host_clock() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".simulator_test_run"
                    + " where test_run_id = 'legacy-accounts-only' and fixed_now is null",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void nothing_a_harness_configured_under_the_reserved_namespace_survives() {
    assertThat(register()).doesNotContain(RESERVED);
    assertThat(rowsUnderTheReservedNamespace("simulator_provider_account")).isZero();
    assertThat(rowsUnderTheReservedNamespace("simulator_webhook_target")).isZero();
    assertThat(rowsUnderTheReservedNamespace("grow_payment_process")).isZero();
  }

  @Test
  void the_audit_records_of_requests_that_resolved_to_nothing_are_left_alone() {
    assertThat(rowsUnderTheReservedNamespace("simulator_audit_record")).isEqualTo(1);
  }

  private List<String> register() {
    return jdbc.queryForList(
        "select test_run_id from " + SCHEMA + ".simulator_test_run", String.class);
  }

  private int rowsUnderTheReservedNamespace(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + SCHEMA + "." + table + " where test_run_id = ?",
        Integer.class,
        RESERVED);
  }

  private Flyway flyway(MigrationVersion target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .schemas(SCHEMA)
        .target(target)
        .load();
  }

  private void configureAsTheSimulatorOfTheTimeWouldHave() {
    account("legacy-accounts-only", "merchant-1");
    webhookTarget("legacy-webhooks-only", "merchant-2");
    hostedProcess("legacy-objects-only", "100001");
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now)"
            + " values ('legacy-pinned', timestamptz '2026-09-07T12:00:00Z')");

    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now)"
            + " values (?, timestamptz '2026-09-07T12:00:00Z')",
        RESERVED);
    account(RESERVED, "merchant-3");
    webhookTarget(RESERVED, "merchant-3");
    hostedProcess(RESERVED, "100002");
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_audit_record (audit_id, provider, test_run_id, resolution_rule,"
            + " direction, method, path, attempt, created_at, sanitized_headers, sanitized_body)"
            + " values ('audit-1', 'grow', ?, 'AMBIGUOUS', 'INBOUND', 'POST', '/api', 1,"
            + " timestamptz '2026-09-07T12:00:00Z', '{}', '{}')",
        RESERVED);
  }

  private void account(String testRunId, String merchantId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_provider_account (provider, test_run_id, merchant_id, user_id,"
            + " page_code, updated_at)"
            + " values ('grow', ?, ?, 'grow-user', 'grow-page',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        testRunId,
        merchantId);
  }

  private void webhookTarget(String testRunId, String merchantId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_target (provider, test_run_id, merchant_id, url, updated_at)"
            + " values ('grow', ?, ?, 'https://merchant.example/hook',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        testRunId,
        merchantId);
  }

  private void hostedProcess(String testRunId, String processId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_payment_process (test_run_id, process_id, process_token, merchant_id,"
            + " charge_type, charge_sum, description, save_card_token, success_url, cancel_url,"
            + " payer_full_name, payer_phone, custom_fields, hosted_page_url, native_status_code,"
            + " native_status, normalized_status, origin, created_at, updated_at)"
            + " values (?, ?, 'token', 'merchant-1', '2', '120.50', 'A hold', false,"
            + " 'https://merchant.example/thanks', 'https://merchant.example/cancel',"
            + " 'Israel Israeli', '0505503144', '{}', 'https://simulator.internal/far', '0',"
            + " 'not initialized', 'NOT_INITIALIZED', 'PROVIDER_API',"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        testRunId,
        processId);
  }
}
