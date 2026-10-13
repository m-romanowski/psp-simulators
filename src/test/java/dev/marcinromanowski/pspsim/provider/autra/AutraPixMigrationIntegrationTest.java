package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.Locale;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AutraPixMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_autra_served_a_pix";

  private static final String RUN = "before-autra-pix";

  private static final String ACCOUNT = "merchant-already-here";

  private static final String DOCUMENT = "25055662000190";

  private static final String PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String PIX_ID = "cdb445694be7486e830f720658f9a1a1";

  private static final MigrationVersion BEFORE_PIX = MigrationVersion.fromVersion("65");

  private static final MigrationVersion WHEN_PIX_LANDED = MigrationVersion.fromVersion("66");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldAMerchantAndAPaymentOfTheOldShape() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_PIX).migrate();
    aTestRunOfTheOldShape();
    aMerchantOfTheOldShape();
    aPreAuthorizationOfTheOldShape();
    flyway(WHEN_PIX_LANDED).migrate();
  }

  @Test
  void a_merchant_configured_before_this_slice_keeps_every_value_it_was_configured_with() {
    assertThat(
            jdbc.queryForObject(
                "select client_id from "
                    + SCHEMA
                    + ".autra_merchant where test_run_id = ? and account_id = ?",
                String.class,
                RUN,
                ACCOUNT))
        .isEqualTo("client-already-here");
    assertThat(
            jdbc.queryForObject(
                "select document_id from "
                    + SCHEMA
                    + ".autra_merchant where test_run_id = ? and account_id = ?",
                String.class,
                RUN,
                ACCOUNT))
        .isEqualTo(DOCUMENT);
  }

  @Test
  void a_merchant_configured_before_this_slice_signs_nothing_rather_than_signing_with_a_default() {
    assertThat(
            jdbc.queryForObject(
                "select webhook_secret from "
                    + SCHEMA
                    + ".autra_merchant where test_run_id = ? and account_id = ?",
                String.class,
                RUN,
                ACCOUNT))
        .isNull();
  }

  @Test
  void a_pre_authorization_taken_before_this_slice_survives_the_migration_untouched() {
    assertThat(
            jdbc.queryForObject(
                "select native_status from "
                    + SCHEMA
                    + ".autra_payment where test_run_id = ? and payment_id = ?",
                String.class,
                RUN,
                PAYMENT_ID))
        .isEqualTo("PRE_AUTHORIZED");
  }

  @Test
  void the_migration_invents_no_pix_for_the_payment_it_found_already_stored() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".autra_pix_payment", Integer.class))
        .isZero();
  }

  @Test
  void a_merchant_of_the_old_shape_takes_a_callback_secret_of_the_length_autra_publishes() {
    assertThatCode(() -> theSecretOfTheMerchantBecomes("a".repeat(32))).doesNotThrowAnyException();
    String shorterThanTheColumnHolds = "a".repeat(31);

    assertThatThrownBy(() -> theSecretOfTheMerchantBecomes(shorterThanTheColumnHolds))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_pix_store_refuses_an_identifier_that_is_not_thirty_two_lowercase_hex_digits() {
    assertThatCode(() -> aPixWith(PIX_ID, AutraStatus.PENDING)).doesNotThrowAnyException();
    String upperCased = PIX_ID.toUpperCase(Locale.ROOT);

    assertThatThrownBy(() -> aPixWith(upperCased, "PENDING"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aPixWith("cdb445694be7486e830f720658f9a1", "PENDING"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_pix_store_takes_a_status_autra_has_never_published_because_that_list_may_grow() {
    assertThatCode(() -> aPixWith(PIX_ID, "CHARGEBACK_OPENED")).doesNotThrowAnyException();

    assertThat(
            jdbc.queryForObject(
                "select native_status from " + SCHEMA + ".autra_pix_payment where pix_id = ?",
                String.class,
                PIX_ID))
        .isEqualTo("CHARGEBACK_OPENED");
  }

  @Test
  void the_pix_store_refuses_a_qr_that_expired_before_it_was_created() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into "
                        + SCHEMA
                        + ".autra_pix_payment (test_run_id, pix_id, account_id, document_id,"
                        + " amount, currency, native_status, qr_code, slug, muid, expires_at,"
                        + " origin, created_at, updated_at)"
                        + " values (?, ?, ?, ?, '99.90', 'BRL', 'PENDING', 'a-qr', 'A1', 'B2',"
                        + " timestamptz '2026-09-22T11:00:00Z', 'PROVIDER_API',"
                        + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
                    RUN,
                    PIX_ID,
                    ACCOUNT,
                    DOCUMENT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void theSecretOfTheMerchantBecomes(String secret) {
    jdbc.update(
        "update " + SCHEMA + ".autra_merchant set webhook_secret = ? where test_run_id = ?",
        secret,
        RUN);
  }

  private void aPixWith(String pixId, String nativeStatus) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_pix_payment (test_run_id, pix_id, account_id, document_id, order_id,"
            + " description, amount, currency, native_status, qr_code, slug, muid, expires_at,"
            + " origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, 'pedido-abc-123', 'Pedido #1234', '99.90', 'BRL', ?, 'a-qr',"
            + " 'A13CA0', '1A0B5369', timestamptz '2026-09-22T12:05:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        pixId,
        ACCOUNT,
        DOCUMENT,
        nativeStatus);
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
            + " authorization_code, rrn, tracking_number, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, 'pedido-abc-123', 'CREDIT', '500.00', 'BRL', 1, ?,"
            + " '000123456', 'A1B2C3', '112676199769', '900123456789', 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        PAYMENT_ID,
        ACCOUNT,
        DOCUMENT,
        "PRE_AUTHORIZED");
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
