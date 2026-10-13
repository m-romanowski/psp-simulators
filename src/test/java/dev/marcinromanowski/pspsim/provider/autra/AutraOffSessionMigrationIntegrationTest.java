package dev.marcinromanowski.pspsim.provider.autra;

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

class AutraOffSessionMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_autra_charged_off_session";

  private static final String RUN = "before-autra-off-session";

  private static final String ACCOUNT = "merchant-already-here";

  private static final String DOCUMENT = "25055662000190";

  private static final String PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String PIX_ID = "cdb445694be7486e830f720658f9a1a1";

  private static final String SESSION_ID = "03294670-67f0-4dca-a121-cf0e0fb2a92f";

  private static final MigrationVersion BEFORE_OFF_SESSION = MigrationVersion.fromVersion("66");

  private static final MigrationVersion WHEN_OFF_SESSION_LANDED =
      MigrationVersion.fromVersion("67");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldAMerchantAPaymentAndAPix() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_OFF_SESSION).migrate();
    aTestRunOfTheOldShape();
    aMerchantOfTheOldShape();
    aPreAuthorizationOfTheOldShape();
    aPixOfTheOldShape();
    flyway(WHEN_OFF_SESSION_LANDED).migrate();
  }

  @Test
  void a_merchant_configured_before_this_slice_keeps_every_value_it_was_configured_with() {
    assertThat(theMerchantColumn("client_id")).isEqualTo("client-already-here");
    assertThat(theMerchantColumn("document_id")).isEqualTo(DOCUMENT);
    assertThat(theMerchantColumn("authorizer_rejected_message")).isEqualTo("05 - Do not honor");
  }

  @Test
  void a_merchant_configured_before_this_slice_reads_the_machine_readable_side_of_the_decline() {
    assertThat(theMerchantColumn("sale_decline_status")).isEqualTo("TWO_HUNDRED_WITH_ERRORS");
  }

  @Test
  void the_backfilled_column_is_left_with_no_default_so_a_write_that_omits_it_is_refused() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".autra_merchant (test_run_id, account_id, client_id, client_secret,"
                        + " document_id, oauth_failure, oauth_field_casing,"
                        + " pre_authorization_shape, void_shape, authorizer_rejected_message,"
                        + " merchant_not_found_status)"
                        + " values (?, 'merchant-2', 'client-2', 'a-secret', '12345678000190',"
                        + " 'BAD_REQUEST_WITH_ERROR_STRING', 'CAMEL_CASE', 'PUBLISHED_SCHEMA',"
                        + " 'PUBLISHED_SCHEMA', '05 - Do not honor', 'NOT_FOUND')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_merchant_store_takes_neither_reading_of_the_decline_but_the_two_the_vendor_publishes() {
    assertThatCode(() -> theDeclineReadingBecomes("UNPROCESSABLE_WITH_ERRORS"))
        .doesNotThrowAnyException();
    assertThatCode(() -> theDeclineReadingBecomes("TWO_HUNDRED_WITH_ERRORS"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> theDeclineReadingBecomes("FOUR_HUNDRED_WITH_ERRORS"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_pre_authorization_taken_before_this_slice_survives_and_names_no_stored_card_half() {
    assertThat(
            jdbc.queryForObject(
                "select native_status from "
                    + SCHEMA
                    + ".autra_payment where test_run_id = ? and payment_id = ?",
                String.class,
                RUN,
                PAYMENT_ID))
        .isEqualTo("PRE_AUTHORIZED");
    assertThat(
            jdbc.queryForObject(
                "select slug_stored_card from "
                    + SCHEMA
                    + ".autra_payment where test_run_id = ? and payment_id = ?",
                String.class,
                RUN,
                PAYMENT_ID))
        .isNull();
  }

  @Test
  void a_pix_created_before_this_slice_survives_the_migration_untouched() {
    assertThat(
            jdbc.queryForObject(
                "select native_status from "
                    + SCHEMA
                    + ".autra_pix_payment where test_run_id = ? and pix_id = ?",
                String.class,
                RUN,
                PIX_ID))
        .isEqualTo(AutraStatus.PENDING);
  }

  @Test
  void the_migration_invents_no_card_session_for_the_objects_it_found_already_stored() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".autra_card_session", Integer.class))
        .isZero();
  }

  @Test
  void the_card_session_store_refuses_a_session_that_does_not_expire_fifteen_minutes_on() {
    assertThatCode(() -> aCardSessionExpiringAt("2026-09-22T12:15:00Z")).doesNotThrowAnyException();
    assertThatThrownBy(() -> aCardSessionExpiringAt("2026-09-22T12:14:00Z"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aCardSessionExpiringAt("2026-09-22T12:16:00Z"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_card_session_store_refuses_a_tokenized_session_that_names_neither_half_of_the_pair() {
    assertThatThrownBy(() -> aCardSessionThatIs(AutraCardSession.TOKENIZED, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aCardSessionThatIs(AutraCardSession.TOKENIZED, "a-token-slug", null))
        .doesNotThrowAnyException();
  }

  @Test
  void the_card_session_store_refuses_a_session_holding_a_slug_before_a_payer_tokenized_one() {
    assertThatThrownBy(() -> aCardSessionThatIs(AutraStatus.PENDING, "a-token-slug", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aCardSessionThatIs(AutraStatus.PENDING, null, null))
        .doesNotThrowAnyException();
  }

  private String theMerchantColumn(String column) {
    return jdbc.queryForObject(
        "select "
            + column
            + " from "
            + SCHEMA
            + ".autra_merchant where test_run_id = ? and account_id = ?",
        String.class,
        RUN,
        ACCOUNT);
  }

  private void theDeclineReadingBecomes(String reading) {
    jdbc.update(
        "update " + SCHEMA + ".autra_merchant set sale_decline_status = ? where test_run_id = ?",
        reading,
        RUN);
  }

  private void aCardSessionExpiringAt(String expiresAt) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_card_session (test_run_id, card_session_id, account_id, document_id,"
            + " native_status, hosted_card_url, expires_at, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, 'PENDING', 'https://cards.autra.io/x', timestamptz '"
            + expiresAt
            + "', 'PROVIDER_API', timestamptz '2026-09-22T12:00:00Z',"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        SESSION_ID + expiresAt.substring(14, 16),
        ACCOUNT,
        DOCUMENT);
  }

  private void aCardSessionThatIs(String nativeStatus, String slugToken, String slugStoredCard) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_card_session (test_run_id, card_session_id, account_id, document_id,"
            + " native_status, hosted_card_url, slug_token, slug_stored_card, expires_at, origin,"
            + " created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, 'https://cards.autra.io/x', ?, ?,"
            + " timestamptz '2026-09-22T12:15:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        SESSION_ID + nativeStatus,
        ACCOUNT,
        DOCUMENT,
        nativeStatus,
        slugToken,
        slugStoredCard);
  }

  private void aTestRunOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now)"
            + " values (?, timestamptz '2026-09-22T12:00:00Z')",
        RUN);
  }

  private void aMerchantOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_merchant (test_run_id, account_id, client_id, client_secret, document_id,"
            + " oauth_failure, oauth_field_casing, pre_authorization_shape, void_shape,"
            + " authorizer_rejected_message, merchant_not_found_status)"
            + " values (?, ?, 'client-already-here', 'a-secret', ?,"
            + " 'BAD_REQUEST_WITH_ERROR_STRING', 'CAMEL_CASE', 'PUBLISHED_SCHEMA',"
            + " 'PUBLISHED_SCHEMA', '05 - Do not honor', 'NOT_FOUND')",
        RUN,
        ACCOUNT,
        DOCUMENT);
  }

  private void aPreAuthorizationOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id, order_id,"
            + " transaction_type, amount, currency, installments, native_status, nsu,"
            + " authorization_code, rrn, tracking_number, slug_token, origin, created_at,"
            + " updated_at)"
            + " values (?, ?, ?, ?, 'pedido-abc-123', 'CREDIT', '500.00', 'BRL', 1, ?,"
            + " '000123456', 'A1B2C3', '112676199769', '900123456789', 'slug-token-abc',"
            + " 'PROVIDER_API', timestamptz '2026-09-22T12:00:00Z',"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        PAYMENT_ID,
        ACCOUNT,
        DOCUMENT,
        "PRE_AUTHORIZED");
  }

  private void aPixOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_pix_payment (test_run_id, pix_id, account_id, document_id, amount, currency,"
            + " native_status, qr_code, slug, muid, expires_at, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, '99.90', 'BRL', ?, 'a-qr', 'A13CA0', '1A0B5369',"
            + " timestamptz '2026-09-22T12:05:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        PIX_ID,
        ACCOUNT,
        DOCUMENT,
        AutraStatus.PENDING);
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
