package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AzericardSeededCardMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_card_could_be_seeded";

  private static final String RUN = "before-the-fixture";

  private static final String TERMINAL_ID = "70000007";

  private static final String STORED_TOKEN = "STOREDBYAPAYMENT000000000001";

  private static final String SEEDED_TOKEN = "SEEDEDBYAFIXTURE000000000001";

  private static final MigrationVersion BEFORE_A_CARD_COULD_BE_SEEDED =
      MigrationVersion.fromVersion("44");

  private static final MigrationVersion WHEN_A_CARD_COULD_BE_SEEDED =
      MigrationVersion.fromVersion("45");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhereEveryStoredCardWasStoredByAPayment() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_A_CARD_COULD_BE_SEEDED).migrate();
    aTerminalOfTheOldShape();
    aCardStoredByAPayment();
    flyway(WHEN_A_CARD_COULD_BE_SEEDED).migrate();
  }

  @Test
  void a_card_a_payment_stored_before_fixtures_existed_still_names_the_payment_that_stored_it() {
    Map<String, Object> card =
        jdbc.queryForMap(
            "select stored_by_int_ref, order_id, fixture_id from "
                + SCHEMA
                + ".azericard_stored_card where token = ?",
            STORED_TOKEN);

    assertThat(card.get("stored_by_int_ref")).isEqualTo("INTREFBEFOREFIXTURE1");
    assertThat(card.get("order_id")).isEqualTo("20220628084800");
    assertThat(card.get("fixture_id")).isNull();
  }

  @Test
  void a_card_a_fixture_seeded_is_accepted_with_no_payment_behind_it() {
    assertThatCode(this::aCardSeededByAFixture).doesNotThrowAnyException();

    assertThat(
            jdbc.queryForObject(
                "select fixture_id from " + SCHEMA + ".azericard_stored_card where token = ?",
                String.class,
                SEEDED_TOKEN))
        .isEqualTo("fx_seeded");
  }

  @Test
  void the_store_refuses_a_card_that_claims_both_a_payment_and_a_fixture() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".azericard_stored_card (test_run_id, token, terminal_id, merchant_id,"
                        + " stored_by_int_ref, order_id, fixture_id, created_at)"
                        + " values (?, ?, ?, 'merchant-1', 'INTREFBOTH0000000001',"
                        + " '20220628084801', 'fx_both', timestamptz '2026-09-10T12:00:00Z')",
                    RUN,
                    "BOTHATONCE000000000000000001",
                    TERMINAL_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_refuses_a_card_that_claims_neither_a_payment_nor_a_fixture() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".azericard_stored_card (test_run_id, token, terminal_id, merchant_id,"
                        + " stored_by_int_ref, order_id, fixture_id, created_at)"
                        + " values (?, ?, ?, 'merchant-1', null, null, null,"
                        + " timestamptz '2026-09-10T12:00:00Z')",
                    RUN,
                    "ORPHANEDCARD0000000000000001",
                    TERMINAL_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_refuses_a_card_that_names_a_payment_by_half() {
    String token = "HALFAPAYMENT00000000000000001".substring(0, 28);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".azericard_stored_card (test_run_id, token, terminal_id, merchant_id,"
                        + " stored_by_int_ref, order_id, fixture_id, created_at)"
                        + " values (?, ?, ?, 'merchant-1', 'INTREFHALF0000000001', null, null,"
                        + " timestamptz '2026-09-10T12:00:00Z')",
                    RUN,
                    token,
                    TERMINAL_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aCardSeededByAFixture() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_stored_card (test_run_id, token, terminal_id, merchant_id,"
            + " stored_by_int_ref, order_id, fixture_id, created_at)"
            + " values (?, ?, ?, 'merchant-1', null, null, 'fx_seeded',"
            + " timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        SEEDED_TOKEN,
        TERMINAL_ID);
  }

  private void aTerminalOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_terminal (test_run_id, account_id, terminal_id, merchant_key,"
            + " gateway_key, duplicate_response_code, partial_completion, callback_signature)"
            + " values (?, 'merchant-1', ?, 'a-key', 'a-secret', '-1',"
            + " 'CLOSES_THE_AUTHORIZATION', 'VALID')",
        RUN,
        TERMINAL_ID);
  }

  private void aCardStoredByAPayment() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_stored_card (test_run_id, token, terminal_id, merchant_id,"
            + " stored_by_int_ref, order_id, created_at)"
            + " values (?, ?, ?, 'merchant-1', 'INTREFBEFOREFIXTURE1', '20220628084800',"
            + " timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        STORED_TOKEN,
        TERMINAL_ID);
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
