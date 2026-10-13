package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class AutraOneTransactionMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_autra_named_one_transaction";

  private static final String RUN = "before-autra-one-transaction";

  private static final String ACCOUNT = "merchant-already-here";

  private static final String DOCUMENT = "25055662000190";

  private static final String PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String THE_TRACKING_NUMBER_IT_WAS_STORED_WITH = "900123456789";

  private static final String THE_NSU_THAT_DISAGREED_WITH_IT = "100123456";

  private static final MigrationVersion BEFORE_ONE_TRANSACTION = MigrationVersion.fromVersion("74");

  private static final MigrationVersion WHEN_ONE_TRANSACTION_LANDED =
      MigrationVersion.fromVersion("75");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void leaveNoSchemaOfThisClassBehindInTheSharedContainer() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
  }

  @BeforeEach
  void migrateOverADatabaseHoldingAPaymentWhoseTwoNamesDisagreed() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_ONE_TRANSACTION).migrate();
    aTestRunOfTheOldShape();
    aMerchantOfTheOldShape();
    aPaymentOfTheOldShapeWhoseTwoNamesDisagreed();
    flyway(WHEN_ONE_TRANSACTION_LANDED).migrate();
  }

  @Test
  void the_payment_stored_before_this_slice_keeps_the_tracking_number_it_was_stored_with() {
    assertThat(thePaymentColumn("tracking_number"))
        .isEqualTo(THE_TRACKING_NUMBER_IT_WAS_STORED_WITH);
  }

  @Test
  void the_second_name_that_disagreed_with_it_is_no_longer_a_column_the_store_has() {
    assertThatThrownBy(() -> thePaymentColumn("nsu")).hasMessageContaining("nsu");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.columns where table_schema = ?"
                    + " and table_name = 'autra_payment' and column_name = 'nsu'",
                Integer.class,
                SCHEMA))
        .isZero();
  }

  @Test
  void a_payment_whose_acquirer_named_no_transaction_is_now_storable_where_it_was_refused_before() {
    assertThatCode(() -> aPaymentTracked(null)).doesNotThrowAnyException();
    assertThat(
            jdbc.queryForObject(
                "select tracking_number from "
                    + SCHEMA
                    + ".autra_payment where test_run_id = ? and payment_id = ?",
                String.class,
                RUN,
                PAYMENT_ID + "-null"))
        .isNull();
  }

  @Test
  void after_the_migration_the_store_takes_twelve_digits_and_refuses_every_other_shape() {
    assertThatCode(() -> aPaymentTracked("400111222333")).doesNotThrowAnyException();
    assertThatThrownBy(() -> aPaymentTracked("TRK-000123"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aPaymentTracked("400111222"))
        .as("nine digits, which a digits-only check would have taken")
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aPaymentTracked("4001112223334"))
        .as("thirteen digits, inside varchar(32) and outside the published shape")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_merchant_configured_before_this_slice_keeps_its_values_and_names_a_reporting_acquirer() {
    assertThat(theMerchantColumn("client_id")).isEqualTo("client-already-here");
    assertThat(theMerchantColumn("authorizer_rejected_message")).isEqualTo("05 - Do not honor");
    assertThat(theMerchantColumn("acquirer_transaction_identifier")).isEqualTo("REPORTED");
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
                        + " merchant_not_found_status, sale_decline_status)"
                        + " values (?, 'merchant-2', 'client-2', 'a-secret', '12345678000190',"
                        + " 'BAD_REQUEST_WITH_ERROR_STRING', 'CAMEL_CASE', 'PUBLISHED_SCHEMA',"
                        + " 'PUBLISHED_SCHEMA', '05 - Do not honor', 'NOT_FOUND',"
                        + " 'TWO_HUNDRED_WITH_ERRORS')",
                    RUN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_merchant_store_takes_the_two_things_an_acquirer_does_and_nothing_else() {
    assertThatCode(() -> theAcquirerBecomes("NOT_REPORTED")).doesNotThrowAnyException();
    assertThatCode(() -> theAcquirerBecomes("REPORTED")).doesNotThrowAnyException();
    assertThatThrownBy(() -> theAcquirerBecomes("SOMETIMES"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String thePaymentColumn(String column) {
    return jdbc.queryForObject(
        "select "
            + column
            + " from "
            + SCHEMA
            + ".autra_payment where test_run_id = ? and payment_id = ?",
        String.class,
        RUN,
        PAYMENT_ID);
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

  private void theAcquirerBecomes(String reporting) {
    jdbc.update(
        "update "
            + SCHEMA
            + ".autra_merchant set acquirer_transaction_identifier = ? where test_run_id = ?",
        reporting,
        RUN);
  }

  private void aPaymentTracked(String trackingNumber) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id,"
            + " transaction_type, amount, currency, installments, native_status,"
            + " authorization_code, rrn, tracking_number, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, 'CREDIT', '500.00', 'BRL', 1, ?, 'A1B2C3', '112676199769',"
            + " ?, 'PROVIDER_API', timestamptz '2026-09-22T12:00:00Z',"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        PAYMENT_ID + "-" + trackingNumber,
        ACCOUNT,
        DOCUMENT,
        AutraStatus.ACCEPTED,
        trackingNumber);
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
            + " authorizer_rejected_message, merchant_not_found_status, sale_decline_status)"
            + " values (?, ?, 'client-already-here', 'a-secret', ?,"
            + " 'BAD_REQUEST_WITH_ERROR_STRING', 'CAMEL_CASE', 'PUBLISHED_SCHEMA',"
            + " 'PUBLISHED_SCHEMA', '05 - Do not honor', 'NOT_FOUND', 'TWO_HUNDRED_WITH_ERRORS')",
        RUN,
        ACCOUNT,
        DOCUMENT);
  }

  private void aPaymentOfTheOldShapeWhoseTwoNamesDisagreed() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id, order_id,"
            + " transaction_type, amount, currency, installments, native_status, nsu,"
            + " authorization_code, rrn, tracking_number, slug_token, origin, created_at,"
            + " updated_at)"
            + " values (?, ?, ?, ?, 'pedido-abc-123', 'CREDIT', '500.00', 'BRL', 1, ?, ?,"
            + " 'A1B2C3', '112676199769', ?, 'slug-token-abc',"
            + " 'PROVIDER_API', timestamptz '2026-09-22T12:00:00Z',"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        PAYMENT_ID,
        ACCOUNT,
        DOCUMENT,
        AutraStatus.ACCEPTED,
        THE_NSU_THAT_DISAGREED_WITH_IT,
        THE_TRACKING_NUMBER_IT_WAS_STORED_WITH);
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
