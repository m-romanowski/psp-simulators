package dev.marcinromanowski.pspsim.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class StoredCardOriginMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stored_card_named_its_surface";

  private static final String RUN = "before-card-origin";

  private static final MigrationVersion BEFORE_ORIGIN = MigrationVersion.fromVersion("47");

  private static final MigrationVersion WITH_ORIGIN = MigrationVersion.fromVersion("48");

  private static final String STORED_BY_A_PAYMENT = "CARDTOKENSTOREDBYAPAYMENT01";

  private static final String SEEDED_BY_A_FIXTURE = "CARDTOKENSEEDEDBYAFIXTURE01";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseCardsNamedNoSurface() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_ORIGIN).migrate();
    aCardOfTheOldShapeStoredByAPayment();
    aCardOfTheOldShapeSeededByAFixture();
    flyway(WITH_ORIGIN).migrate();
  }

  @Test
  void a_card_a_payment_had_already_stored_reports_the_provider_route_that_stored_it() {
    assertThat(originOf(STORED_BY_A_PAYMENT)).isEqualTo("PROVIDER_API");
  }

  @Test
  void a_card_a_seed_had_already_placed_keeps_the_control_api_it_could_only_have_come_from() {
    assertThat(originOf(SEEDED_BY_A_FIXTURE)).isEqualTo("CONTROL_API");
  }

  @Test
  void a_card_a_payment_stored_cannot_afterwards_claim_a_tester_placed_it() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update " + SCHEMA + ".azericard_stored_card set origin = 'UI' where token = ?",
                    STORED_BY_A_PAYMENT))
        .hasMessageContaining("azericard_stored_card_origin_is_a_surface_that_stores_one");
  }

  @Test
  void a_card_the_screen_seeds_is_admitted_beside_the_ones_the_control_api_seeded() {
    jdbc.update(
        "update " + SCHEMA + ".azericard_stored_card set origin = 'UI' where token = ?",
        SEEDED_BY_A_FIXTURE);

    assertThat(originOf(SEEDED_BY_A_FIXTURE)).isEqualTo("UI");
  }

  private String originOf(String token) {
    return jdbc.queryForObject(
        "select origin from "
            + SCHEMA
            + ".azericard_stored_card where test_run_id = ?"
            + " and token = ?",
        String.class,
        RUN,
        token);
  }

  private void aCardOfTheOldShapeStoredByAPayment() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_stored_card (test_run_id, token, terminal_id, merchant_id,"
            + " stored_by_int_ref, order_id, fixture_id, created_at)"
            + " values (?, ?, 'az-terminal-1', 'merchant-1', 'INTREF0000000001', 'ORDER001',"
            + " null, timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        STORED_BY_A_PAYMENT);
  }

  private void aCardOfTheOldShapeSeededByAFixture() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_stored_card (test_run_id, token, terminal_id, merchant_id,"
            + " stored_by_int_ref, order_id, fixture_id, created_at)"
            + " values (?, ?, 'az-terminal-1', 'merchant-1', null, null, 'pmf_1',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        SEEDED_BY_A_FIXTURE);
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
