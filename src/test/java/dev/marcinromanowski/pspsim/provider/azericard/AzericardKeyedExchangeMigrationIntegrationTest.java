package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AzericardKeyedExchangeMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_azericard_recorded_what_it_answered_under_a_key";

  private static final String RUN = "before-the-exchange";

  private static final String TERMINAL_ID = "70000007";

  private static final String INT_REF = "INTREFBEFOREEXCHANGE01";

  private static final String ORDER = "20220628084800";

  private static final String TRACE = "084800";

  private static final MigrationVersion BEFORE_THE_EXCHANGE = MigrationVersion.fromVersion("62");

  private static final MigrationVersion WHEN_THE_EXCHANGE_LANDED =
      MigrationVersion.fromVersion("63");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatHeldADuplicateNothingHadRecorded() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_EXCHANGE).migrate();
    aTerminalOfTheOldShape();
    aPaymentOfTheOldShape();
    aTraceClaimedByThatPayment();
    flyway(WHEN_THE_EXCHANGE_LANDED).migrate();
  }

  @Test
  void a_payment_kept_before_the_gateway_recorded_an_exchange_survives_the_migration() {
    assertThat(
            jdbc.queryForObject(
                "select order_id from " + SCHEMA + ".azericard_payment where int_ref = ?",
                String.class,
                INT_REF))
        .isEqualTo(ORDER);
  }

  @Test
  void the_trace_that_payment_claimed_is_still_claimed_after_the_migration() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".azericard_order_trace where terminal_id = ? and trace = ?",
                Integer.class,
                TERMINAL_ID,
                TRACE))
        .isEqualTo(1);
  }

  @Test
  void the_migration_invents_no_exchange_for_the_payment_it_found_already_stored() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".azericard_keyed_exchange", Integer.class))
        .isZero();
  }

  @Test
  void the_store_refuses_a_verdict_no_key_this_provider_declares_could_ever_reach() {
    assertThatCode(() -> aRequestRecordedAs(ExchangeVerdict.FIRST_REQUEST.name(), 0))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> aRequestRecordedAs(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY.name(), 1))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aRequestRecordedAs(ExchangeVerdict.CONFLICTING_PAYLOAD.name(), 2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_refuses_half_an_answer_where_a_request_either_was_answered_or_was_not() {
    assertThatCode(() -> aRequestAnsweredWith(null, null, null, 3)).doesNotThrowAnyException();
    assertThatThrownBy(() -> aRequestAnsweredWith(200, null, null, 4))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aRequestRecordedAs(String verdict, int seq) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_keyed_exchange (test_run_id, account_id, key_name, key_value, seq,"
            + " operation, fingerprint, verdict, http_status, content_type, body,"
            + " provider_object_id, state_changed, at)"
            + " values (?, 'merchant-1', 'ORDER', ?, ?, 'TRTYPE=0', 'a-fingerprint', ?, 200,"
            + " 'text/html', '<response/>', ?, true, timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        TRACE,
        seq,
        verdict,
        INT_REF);
  }

  private void aRequestAnsweredWith(Integer httpStatus, String contentType, String body, int seq) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_keyed_exchange (test_run_id, account_id, key_name, key_value, seq,"
            + " operation, fingerprint, verdict, http_status, content_type, body,"
            + " provider_object_id, state_changed, at)"
            + " values (?, 'merchant-1', 'ORDER', ?, ?, 'TRTYPE=0', 'a-fingerprint',"
            + " 'FIRST_REQUEST', ?, ?, ?, null, false, timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        TRACE,
        seq,
        httpStatus,
        contentType,
        body);
  }

  private void aTerminalOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_terminal"
            + " (test_run_id, account_id, terminal_id, merchant_key, gateway_key,"
            + " duplicate_response_code, partial_completion, callback_signature)"
            + " values (?, 'merchant-1', ?, 'a-key', 'a-secret', '-1',"
            + " 'CLOSES_THE_AUTHORIZATION', 'VALID')",
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
            + " values (?, ?, ?, 'merchant-1', ?, '0', '11.48', 'AZN', '111111',"
            + " '112676199769', '0', '00', 'PROVIDER_API',"
            + " timestamptz '2026-09-10T12:00:00Z', timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        INT_REF,
        TERMINAL_ID,
        ORDER);
  }

  private void aTraceClaimedByThatPayment() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_order_trace (test_run_id, terminal_id, trace_day, trace)"
            + " values (?, ?, date '2026-09-10', ?)",
        RUN,
        TERMINAL_ID,
        TRACE);
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
