package dev.marcinromanowski.pspsim.kernel.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class RouteAliasMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_route_aliases";

  private static final String PAYMENTS = "api.provider.example/v2/merchant-1/payments";

  @Autowired private DataSource dataSource;

  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldAConfiguredTestRun() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("37")).migrate();
    configureAsTheSimulatorOfTheTimeWouldHave();
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void the_accounts_a_harness_configured_before_this_migration_are_still_configured_after_it() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".simulator_provider_account"
                    + " where provider = 'grow' and test_run_id = 'legacy-run'"
                    + " and account_id = 'merchant-1'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".simulator_webhook_target", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void a_database_that_answered_no_url_before_the_migration_answers_none_after_it() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".simulator_route_alias", Integer.class))
        .isZero();
  }

  @Test
  void a_run_that_existed_before_the_migration_can_bind_a_url_after_it() {
    bind("legacy-run", PAYMENTS, "/api/light/server/1.0/createPaymentProcess");

    assertThat(
            jdbc.queryForObject(
                "select test_run_id from " + SCHEMA + ".simulator_route_alias where alias_key = ?",
                String.class,
                PAYMENTS))
        .isEqualTo("legacy-run");
  }

  @Test
  void the_database_and_not_only_the_control_api_refuses_one_url_bound_to_two_test_runs() {
    bind("legacy-run", PAYMENTS, "/api/light/server/1.0/createPaymentProcess");

    assertThatThrownBy(
            () -> bind("another-run", PAYMENTS, "/api/light/server/1.0/refundTransaction"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void bind(String testRunId, String aliasKey, String to) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_route_alias"
            + " (alias_key, provider, test_run_id, from_url, to_route, updated_at)"
            + " values (?, 'grow', ?, ?, ?, timestamptz '2026-09-07T12:00:00Z')",
        aliasKey,
        testRunId,
        "https://" + aliasKey,
        to);
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
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now)"
            + " values ('legacy-run', timestamptz '2026-09-07T12:00:00Z')");
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_provider_account (provider, test_run_id, account_id, updated_at)"
            + " values ('grow', 'legacy-run', 'merchant-1',"
            + " timestamptz '2026-09-07T12:00:00Z')");
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_target (provider, test_run_id, account_id, url, updated_at)"
            + " values ('grow', 'legacy-run', 'merchant-1', 'https://merchant.example/hook',"
            + " timestamptz '2026-09-07T12:00:00Z')");
  }
}
