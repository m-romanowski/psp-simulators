package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

class WorldlineMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_worldline_served_a_call";

  private static final String RUN = "before-worldline";

  private static final String ANOTHER_PROVIDERS_ACCOUNT = "merchant-already-here";

  private static final String DOCUMENT = "25055662000190";

  private static final String PAYMENT_ID = "3066019730_0";

  private static final MigrationVersion BEFORE_WORLDLINE = MigrationVersion.fromVersion("67");

  private static final MigrationVersion WHEN_WORLDLINE_LANDED = MigrationVersion.fromVersion("68");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldARunAndAnAccountOfAnotherProvider() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_WORLDLINE).migrate();
    aTestRunOfTheOldShape();
    anAccountOfAnotherProviderInThatRun();
    flyway(WHEN_WORLDLINE_LANDED).migrate();
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
  void an_account_of_another_provider_in_that_run_is_left_exactly_as_it_was() {
    assertThat(
            jdbc.queryForObject(
                "select document_id from "
                    + SCHEMA
                    + ".autra_merchant where test_run_id = ? and account_id = ?",
                String.class,
                RUN,
                ANOTHER_PROVIDERS_ACCOUNT))
        .isEqualTo(DOCUMENT);
  }

  @Test
  void the_migration_invents_no_worldline_merchant_for_a_run_that_had_none() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".worldline_merchant", Integer.class))
        .isZero();
  }

  @Test
  void the_merchant_store_takes_the_two_path_versions_the_vendor_publishes_and_no_third() {
    assertThatCode(
            () ->
                aMerchantSigning(
                    "m-1",
                    "k-1",
                    WorldlineSignedPathVersion.V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT
                        .name()))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                aMerchantSigning(
                    "m-2",
                    "k-2",
                    WorldlineSignedPathVersion.V1_AS_THE_WORKED_EXAMPLES_SIGN_IT.name()))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> aMerchantSigning("m-3", "k-3", "V3_NOBODY_PUBLISHES"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_merchant_store_refuses_a_merchant_id_that_would_address_a_different_route() {
    assertThatThrownBy(
            () ->
                aMerchantSigning(
                    "one/two",
                    "k-4",
                    WorldlineSignedPathVersion.V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT
                        .name()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_payment_store_refuses_an_identifier_that_is_not_the_published_payid_payidsub_grammar() {
    assertThatCode(() -> aPaymentIdentifiedAs(PAYMENT_ID)).doesNotThrowAnyException();
    assertThatThrownBy(() -> aPaymentIdentifiedAs("not-a-payment-id"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_payment_store_refuses_an_amount_outside_the_bounds_the_vendor_publishes() {
    assertThatThrownBy(() -> aPaymentOf(WorldlineAmounts.LARGEST + 1))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aPaymentOf(-1L)).isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aPaymentOf(WorldlineAmounts.SMALLEST)).doesNotThrowAnyException();
  }

  @Test
  void the_payment_store_takes_the_three_authorization_modes_the_vendor_publishes_and_no_fourth() {
    assertThatCode(
            () -> aPaymentIn("3066019730_1", WorldlineAuthorizationMode.PRE_AUTHORIZATION.name()))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> aPaymentIn("3066019730_2", "UNSPECIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void one_identifier_reaches_one_payment_because_the_identifier_store_is_keyed_on_it() {
    aPaymentIdentifiedAs(PAYMENT_ID);
    anIdentifierOf(PAYMENT_ID, PAYMENT_ID, 0);
    assertThatThrownBy(() -> anIdentifierOf(PAYMENT_ID, "3066019730_9", 0))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void one_payment_mints_one_identifier_per_maintenance_operation_and_never_two_under_one_number() {
    aPaymentIdentifiedAs(PAYMENT_ID);
    anIdentifierOf(PAYMENT_ID, PAYMENT_ID, 0);
    anIdentifierOf("3066019730_1", PAYMENT_ID, 1);
    assertThatThrownBy(() -> anIdentifierOf("3066019730_2", PAYMENT_ID, 1))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_tokenization_store_refuses_a_session_holding_a_token_before_a_payer_minted_one() {
    assertThatCode(
            () -> aTokenizationThatIs("a".repeat(32), WorldlineStatus.AWAITING_THE_PAYER, null))
        .doesNotThrowAnyException();
    String hex = "b".repeat(32);

    assertThatThrownBy(() -> aTokenizationThatIs(hex, WorldlineStatus.AWAITING_THE_PAYER, "token"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_tokenization_store_refuses_an_identifier_that_is_not_thirty_two_lowercase_hex() {
    assertThatThrownBy(
            () -> aTokenizationThatIs("NOT-HEX", WorldlineStatus.AWAITING_THE_PAYER, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_keyed_exchange_store_takes_the_four_verdicts_this_provider_records_and_no_other() {
    assertThatCode(() -> anExchangeWithVerdict("FIRST_REQUEST", 0)).doesNotThrowAnyException();
    assertThatCode(() -> anExchangeWithVerdict("TRUE_IDEMPOTENT_REPLAY", 1))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> anExchangeWithVerdict("CONFLICTING_PAYLOAD", 2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_idempotence_claim_store_refuses_a_completed_claim_that_kept_no_answer() {
    assertThatCode(() -> aClaimThatIs("k-running", "RUNNING", null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> aClaimThatIs("k-completed", "COMPLETED", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aMerchantSigning(String merchantId, String apiKey, String signedPathVersion) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_merchant (test_run_id, account_id, merchant_id, api_key, api_secret,"
            + " signed_path_version, capture_landing, authentication)"
            + " values (?, ?, ?, ?, 'a-secret', ?, 'CAPTURED_9', 'THE_SIGNATURE_IS_VERIFIED')",
        RUN,
        "account-" + apiKey,
        merchantId,
        apiKey,
        signedPathVersion);
  }

  private void aPaymentIdentifiedAs(String paymentId) {
    aPayment(paymentId, 5000L, WorldlineAuthorizationMode.PRE_AUTHORIZATION.name());
  }

  private void aPaymentOf(long amount) {
    aPayment(
        "3066019730_" + Math.abs(amount % 7),
        amount,
        WorldlineAuthorizationMode.PRE_AUTHORIZATION.name());
  }

  private void aPaymentIn(String paymentId, String authorizationMode) {
    aPayment(paymentId, 5000L, authorizationMode);
  }

  private void aPayment(String paymentId, long amount, String authorizationMode) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_payment (test_run_id, payment_id, account_id, merchant_id, amount,"
            + " currency_code, authorization_mode, native_status, status_category, status_code,"
            + " authorization_closed, origin, created_at, updated_at)"
            + " values (?, ?, 'account-1', 'pspid-1', ?, 'PLN', ?, 'PENDING_CAPTURE',"
            + " 'PENDING_MERCHANT', 5, false, 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        Long.valueOf(amount),
        authorizationMode);
  }

  private void anIdentifierOf(String identifier, String paymentId, int payIdSub) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_payment_identifier (test_run_id, identifier, payment_id, payid_sub,"
            + " minted_at)"
            + " values (?, ?, ?, ?, timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        identifier,
        paymentId,
        Integer.valueOf(payIdSub));
  }

  private void aTokenizationThatIs(String id, String nativeStatus, String token) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_hosted_tokenization (test_run_id, hosted_tokenization_id, account_id,"
            + " merchant_id, native_status, hosted_tokenization_url, partial_redirect_url, token,"
            + " card_number, expiry_date, expires_at, origin, created_at, updated_at)"
            + " values (?, ?, 'account-1', 'pspid-1', ?, 'https://x', 'x', ?,"
            + (token == null ? " null, null," : " '************1111', '1230',")
            + " timestamptz '2026-09-22T15:00:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        id,
        nativeStatus,
        token);
  }

  private void anExchangeWithVerdict(String verdict, int seq) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_keyed_exchange (test_run_id, account_id, key_name, key_value, seq,"
            + " operation, fingerprint, verdict, state_changed, at)"
            + " values (?, 'account-1', 'X-GCS-Idempotence-Key', 'a-key', ?, 'op', 'fp', ?, true,"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        Integer.valueOf(seq),
        verdict);
  }

  private void aClaimThatIs(String key, String claimState, String body) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_idempotence_key (test_run_id, account_id, key_value, header_name,"
            + " operation, fingerprint, claim_state, http_status, content_type, body, claimed_at)"
            + " values (?, 'account-1', ?, 'X-GCS-Idempotence-Key', 'op', 'fp', ?, null, null, ?,"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        key,
        claimState,
        body);
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
            + ".autra_merchant (test_run_id, account_id, client_id, client_secret, document_id,"
            + " oauth_failure, oauth_field_casing, pre_authorization_shape, void_shape,"
            + " authorizer_rejected_message, merchant_not_found_status, sale_decline_status)"
            + " values (?, ?, 'client-already-here', 'a-secret', ?,"
            + " 'BAD_REQUEST_WITH_ERROR_STRING', 'CAMEL_CASE', 'PUBLISHED_SCHEMA',"
            + " 'PUBLISHED_SCHEMA', '05 - Do not honor', 'NOT_FOUND', 'TWO_HUNDRED_WITH_ERRORS')",
        RUN,
        ANOTHER_PROVIDERS_ACCOUNT,
        DOCUMENT);
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
