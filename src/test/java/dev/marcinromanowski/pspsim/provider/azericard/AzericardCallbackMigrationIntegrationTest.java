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

class AzericardCallbackMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_payment_knew_where_to_report_itself";

  private static final String RUN = "before-the-callback";

  private static final String TERMINAL_ID = "70000006";

  private static final String INT_REF = "INTREFBEFORECALLBACK01";

  private static final MigrationVersion BEFORE_THE_CALLBACK = MigrationVersion.fromVersion("32");

  private static final MigrationVersion WHEN_THE_CALLBACK_LANDED =
      MigrationVersion.fromVersion("35");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatHeldPaymentsNoCallbackCouldReport() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_CALLBACK).migrate();
    aTerminalOfTheOldShape();
    aPaymentOfTheOldShape();
    flyway(WHEN_THE_CALLBACK_LANDED).migrate();
  }

  @Test
  void a_payment_kept_before_the_gateway_recorded_a_backref_survives_carrying_none() {
    Map<String, Object> payment =
        jdbc.queryForMap(
            "select backref, email, token, amount from "
                + SCHEMA
                + ".azericard_payment where int_ref = ?",
            INT_REF);

    assertThat(payment.get("backref")).isNull();
    assertThat(payment.get("email")).isNull();
    assertThat(payment.get("token")).isNull();
    assertThat(payment.get("amount")).isEqualTo("11.48");
  }

  @Test
  void a_terminal_configured_before_the_callback_existed_signs_the_callbacks_it_now_sends() {
    assertThat(
            jdbc.queryForObject(
                "select callback_signature from "
                    + SCHEMA
                    + ".azericard_terminal"
                    + " where terminal_id = ?",
                String.class,
                TERMINAL_ID))
        .isEqualTo(AzericardCallbackSignature.VALID.name());
  }

  @Test
  void the_store_refuses_a_callback_emission_no_request_of_the_account_could_be_answered_under() {
    assertThatCode(() -> theTerminalEmits(AzericardCallbackSignature.ABSENT.name()))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> theTerminalEmits("SOMETIMES"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_token_wider_than_the_published_twenty_eight_characters_is_refused_by_the_card_store() {
    assertThatCode(() -> aCardStoredUnder("T".repeat(AzericardStoredCard.TOKEN_LENGTH)))
        .doesNotThrowAnyException();
    String widerThanTheColumn = "T".repeat(AzericardStoredCard.TOKEN_LENGTH + 1);

    assertThatThrownBy(() -> aCardStoredUnder(widerThanTheColumn))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void theTerminalEmits(String emission) {
    jdbc.update(
        "update " + SCHEMA + ".azericard_terminal set callback_signature = ? where terminal_id = ?",
        emission,
        TERMINAL_ID);
  }

  private void aCardStoredUnder(String token) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_stored_card"
            + " (test_run_id, token, terminal_id, merchant_id, stored_by_int_ref, order_id,"
            + " created_at)"
            + " values (?, ?, ?, 'merchant-1', ?, '20220628084800',"
            + " timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        token,
        TERMINAL_ID,
        INT_REF);
  }

  private void aTerminalOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_terminal"
            + " (test_run_id, account_id, terminal_id, merchant_key, gateway_key,"
            + " duplicate_response_code, partial_completion)"
            + " values (?, 'merchant-1', ?, 'a-key', 'a-secret', '-1',"
            + " 'CLOSES_THE_AUTHORIZATION')",
        RUN,
        TERMINAL_ID);
  }

  private void aPaymentOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_payment (test_run_id, int_ref, terminal_id, merchant_id, order_id,"
            + " trtype, amount, currency, approval, rrn, action_code, response_code, origin,"
            + " created_at, updated_at)"
            + " values (?, ?, ?, 'merchant-1', '20220628084800', '0', '11.48', 'AZN', '111111',"
            + " '112676199769', '0', '00', 'PROVIDER_API',"
            + " timestamptz '2026-09-10T12:00:00Z', timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        INT_REF,
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
