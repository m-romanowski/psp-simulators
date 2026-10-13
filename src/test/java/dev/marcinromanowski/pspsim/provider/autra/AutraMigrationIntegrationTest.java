package dev.marcinromanowski.pspsim.provider.autra;

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

class AutraMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_autra_served_an_acquiring_call";

  private static final String RUN = "before-autra";

  private static final String ACCOUNT = "merchant-already-here";

  private static final String DOCUMENT = "25055662000190";

  private static final String PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";

  private static final MigrationVersion BEFORE_AUTRA = MigrationVersion.fromVersion("64");

  private static final MigrationVersion WHEN_AUTRA_LANDED = MigrationVersion.fromVersion("65");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldARunAndAnAccountOfAnotherProvider() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_AUTRA).migrate();
    aTestRunOfTheOldShape();
    anAccountOfAnotherProviderInThatRun();
    flyway(WHEN_AUTRA_LANDED).migrate();
  }

  @Test
  void a_test_run_registered_before_this_provider_existed_survives_the_migration() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".simulator_test_run where test_run_id = ?",
                Integer.class,
                RUN))
        .isEqualTo(1);
  }

  @Test
  void an_account_of_another_provider_in_that_run_survives_the_migration_untouched() {
    assertThat(
            jdbc.queryForObject(
                "select provider from "
                    + SCHEMA
                    + ".simulator_provider_account where test_run_id = ? and account_id = ?",
                String.class,
                RUN,
                ACCOUNT))
        .isEqualTo("grow");
  }

  @Test
  void the_migration_invents_no_autra_merchant_for_the_account_it_found_already_stored() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".autra_merchant", Integer.class))
        .isZero();
  }

  @Test
  void the_merchant_store_refuses_a_masked_document_and_takes_one_written_as_digits_alone() {
    assertThatCode(() -> aMerchantWith(DOCUMENT, "05 - Do not honor")).doesNotThrowAnyException();
    assertThatThrownBy(() -> aMerchantWith("25.055.662/0001-90", "05 - Do not honor"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_merchant_store_refuses_a_decline_message_outside_the_grammar_autra_publishes() {
    assertThatThrownBy(() -> aMerchantWith("11111111111", "declined"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_payment_store_takes_a_status_autra_has_never_published_because_that_list_may_grow() {
    aMerchantWith(DOCUMENT, "05 - Do not honor");

    assertThatCode(() -> aPaymentWith("CHARGEBACK_OPENED", 1)).doesNotThrowAnyException();

    assertThat(
            jdbc.queryForObject(
                "select native_status from " + SCHEMA + ".autra_payment where payment_id = ?",
                String.class,
                PAYMENT_ID))
        .isEqualTo("CHARGEBACK_OPENED");
  }

  @Test
  void the_payment_store_refuses_an_instalment_count_outside_the_range_autra_publishes() {
    aMerchantWith(DOCUMENT, "05 - Do not honor");

    assertThatThrownBy(() -> aPaymentWith("PRE_AUTHORIZED", 13))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_order_claim_store_refuses_a_settled_claim_that_names_no_payment() {
    assertThatCode(() -> anOrderClaim("HELD_BEFORE_THE_ACQUIRER_WAS_CALLED", null))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> anOrderClaim("PRE_AUTHORIZED", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_order_claim_store_refuses_a_held_claim_that_already_names_a_payment() {
    assertThatThrownBy(() -> anOrderClaim("HELD_BEFORE_THE_ACQUIRER_WAS_CALLED", PAYMENT_ID))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_exchange_store_takes_every_verdict_a_key_of_this_provider_can_reach_and_no_other() {
    assertThatCode(() -> anExchangeRecordedAs(ExchangeVerdict.FIRST_REQUEST.name(), 0))
        .doesNotThrowAnyException();
    assertThatCode(() -> anExchangeRecordedAs(ExchangeVerdict.DUPLICATE_KEY_REFUSAL.name(), 1))
        .doesNotThrowAnyException();
    assertThatCode(() -> anExchangeRecordedAs(ExchangeVerdict.REPEAT_ADMITTED.name(), 2))
        .doesNotThrowAnyException();
    assertThatCode(
            () -> anExchangeRecordedAs(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.name(), 3))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> anExchangeRecordedAs(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY.name(), 4))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aTestRunOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now)"
            + " values (?, timestamptz '2026-09-22T12:00:00Z')",
        RUN);
  }

  private void anAccountOfAnotherProviderInThatRun() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_provider_account (provider, test_run_id, account_id, updated_at)"
            + " values ('grow', ?, ?, timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        ACCOUNT);
  }

  private void aMerchantWith(String documentId, String authorizerMessage) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_merchant (test_run_id, account_id, client_id, client_secret, document_id,"
            + " oauth_failure, oauth_field_casing, pre_authorization_shape, void_shape,"
            + " authorizer_rejected_message, merchant_not_found_status)"
            + " values (?, ?, ?, 'a-secret', ?, 'BAD_REQUEST_WITH_ERROR_STRING', 'CAMEL_CASE',"
            + " 'PUBLISHED_SCHEMA', 'PUBLISHED_SCHEMA', ?, 'NOT_FOUND')",
        RUN,
        "account-" + documentId,
        "client-" + documentId,
        documentId,
        authorizerMessage);
  }

  private void aPaymentWith(String nativeStatus, int installments) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id, order_id,"
            + " transaction_type, amount, currency, installments, native_status, nsu,"
            + " authorization_code, rrn, tracking_number, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, null, 'CREDIT', '500.00', 'BRL', ?, ?, '000123456', 'A1B2C3',"
            + " '112676199769', '900123456789', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        PAYMENT_ID,
        "account-" + DOCUMENT,
        DOCUMENT,
        installments,
        nativeStatus);
  }

  private void anOrderClaim(String claimState, String paymentId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_order_claim (test_run_id, document_id, order_id, account_id, claim_state,"
            + " payment_id, at)"
            + " values (?, ?, ?, 'merchant-1', ?, ?, timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        DOCUMENT,
        "pedido-" + claimState + "-" + paymentId,
        claimState,
        paymentId);
  }

  private void anExchangeRecordedAs(String verdict, int seq) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_keyed_exchange (test_run_id, account_id, key_name, key_value, seq,"
            + " operation, fingerprint, verdict, http_status, content_type, body,"
            + " provider_object_id, state_changed, at)"
            + " values (?, 'merchant-1', 'orderId', 'pedido-abc-123', ?,"
            + " 'POST /v1/acquiring/payments/pre-authorization', 'a-fingerprint', ?, 200,"
            + " 'application/json', '{}', null, true, timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        seq,
        verdict);
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
