package dev.marcinromanowski.pspsim.provider.stripe;

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

class StripeHeldKeyExchangeMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_stripe_recorded_a_key_another_request_held";

  private static final String RUN = "before-the-held-key";

  private static final String MERCHANT = "merchant-1";

  private static final String A_KEY = "a-key-of-one-request";

  private static final String THE_ANSWER = "{\"id\":\"pi_sim_before_the_migration\"}";

  private static final MigrationVersion BEFORE_THE_HELD_KEY = MigrationVersion.fromVersion("63");

  private static final MigrationVersion WHEN_THE_HELD_KEY_LANDED =
      MigrationVersion.fromVersion("64");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldAnExchange() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_HELD_KEY).migrate();
    aFirstRequestOfTheOldShape();
    aReplayOfTheOldShape();
    flyway(WHEN_THE_HELD_KEY_LANDED).migrate();
  }

  @Test
  void an_exchange_recorded_before_the_migration_keeps_both_of_its_requests_and_their_answers() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".stripe_idempotency_exchange where test_run_id = ? and idempotency_key = ?",
                Integer.class,
                RUN,
                A_KEY))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select body from "
                    + SCHEMA
                    + ".stripe_idempotency_exchange where test_run_id = ? and seq = 0",
                String.class,
                RUN))
        .isEqualTo(THE_ANSWER);
  }

  @Test
  void the_store_admits_a_request_refused_because_the_key_was_still_held_after_the_migration() {
    assertThatCode(
            () -> aRequestRecordedAs(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.name(), 2))
        .doesNotThrowAnyException();
  }

  @Test
  void the_store_still_refuses_a_verdict_no_key_provider_stripe_declares_could_ever_reach() {
    assertThatThrownBy(() -> aRequestRecordedAs(ExchangeVerdict.DUPLICATE_KEY_REFUSAL.name(), 3))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aRequestRecordedAs(ExchangeVerdict.REPEAT_ADMITTED.name(), 4))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aRequestRecordedAs(ExchangeVerdict.CORRELATED_RETRY.name(), 5))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aRequestRecordedAs(String verdict, int seq) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_idempotency_exchange (test_run_id, account_id, idempotency_key, seq,"
            + " operation, fingerprint, verdict, http_status, content_type, body,"
            + " provider_object_id, state_changed, at)"
            + " values (?, ?, ?, ?, 'createPaymentIntent', 'a-fingerprint', ?, 409,"
            + " 'application/json', '{\"error\":{}}', null, false,"
            + " timestamptz '2026-09-11T12:00:00Z')",
        RUN,
        MERCHANT,
        A_KEY,
        seq,
        verdict);
  }

  private void aFirstRequestOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_idempotency_exchange (test_run_id, account_id, idempotency_key, seq,"
            + " operation, fingerprint, verdict, http_status, content_type, body,"
            + " provider_object_id, state_changed, at)"
            + " values (?, ?, ?, 0, 'createPaymentIntent', 'a-fingerprint', 'FIRST_REQUEST', 200,"
            + " 'application/json', ?, 'pi_sim_before_the_migration', true,"
            + " timestamptz '2026-09-11T12:00:00Z')",
        RUN,
        MERCHANT,
        A_KEY,
        THE_ANSWER);
  }

  private void aReplayOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_idempotency_exchange (test_run_id, account_id, idempotency_key, seq,"
            + " operation, fingerprint, verdict, http_status, content_type, body,"
            + " provider_object_id, state_changed, at)"
            + " values (?, ?, ?, 1, 'createPaymentIntent', 'a-fingerprint',"
            + " 'TRUE_IDEMPOTENT_REPLAY', 200, 'application/json', ?,"
            + " 'pi_sim_before_the_migration', false, timestamptz '2026-09-11T12:01:00Z')",
        RUN,
        MERCHANT,
        A_KEY,
        THE_ANSWER);
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
