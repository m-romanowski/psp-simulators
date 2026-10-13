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

class AutraTimelineMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_autra_read_a_payment_back";

  private static final String RUN = "before-autra-reads";

  private static final String ACCOUNT = "merchant-already-here";

  private static final String DOCUMENT = "25055662000190";

  private static final String A_SALE = "11111111-1111-4111-8111-111111111111";

  private static final String A_DECLINED_SALE = "22222222-2222-4222-8222-222222222222";

  private static final String AN_OPEN_HOLD = "33333333-3333-4333-8333-333333333333";

  private static final String A_CAPTURED_HOLD = "44444444-4444-4444-8444-444444444444";

  private static final String A_RELEASED_HOLD = "55555555-5555-4555-8555-555555555555";

  private static final String A_DECLINED_HOLD = "66666666-6666-4666-8666-666666666666";

  private static final String A_FULLY_REVERSED_SALE = "77777777-7777-4777-8777-777777777777";

  private static final String A_PARTLY_REVERSED_SALE = "88888888-8888-4888-8888-888888888888";

  private static final String A_PARTLY_REVERSED_HOLD = "99999999-9999-4999-8999-999999999999";

  private static final MigrationVersion BEFORE_THE_READS = MigrationVersion.fromVersion("75");

  private static final MigrationVersion WHEN_THE_READS_LANDED = MigrationVersion.fromVersion("76");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void leaveNoSchemaOfThisClassBehindInTheSharedContainer() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
  }

  @BeforeEach
  void migrateOverADatabaseHoldingPaymentsOfEveryShapeTheOldStoreCouldHold() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_READS).migrate();
    aTestRunOfTheOldShape();
    aMerchantOfTheOldShape();
    aPaymentOfTheOldShape(A_SALE, AutraStatus.ACCEPTED, null);
    anOperationOfTheOldShape(A_SALE, 1, "CAPTURE", AutraStatus.ACCEPTED, "SUCCEEDED");
    aPaymentOfTheOldShape(A_DECLINED_SALE, AutraStatus.DECLINED, null);
    aPaymentOfTheOldShape(AN_OPEN_HOLD, "PRE_AUTHORIZED", null);
    anOperationOfTheOldShape(
        AN_OPEN_HOLD, 1, "AUTHORIZATION", "PRE_AUTHORIZED", "READY_FOR_CAPTURE");
    aPaymentOfTheOldShape(A_CAPTURED_HOLD, AutraStatus.CAPTURED, null);
    anOperationOfTheOldShape(
        A_CAPTURED_HOLD, 1, "AUTHORIZATION", "PRE_AUTHORIZED", "READY_FOR_CAPTURE");
    anOperationOfTheOldShape(A_CAPTURED_HOLD, 2, "CAPTURE", AutraStatus.CAPTURED, "SUCCEEDED");
    aPaymentOfTheOldShape(A_RELEASED_HOLD, AutraStatus.VOIDED, "2026-09-22T13:00:00Z");
    anOperationOfTheOldShape(
        A_RELEASED_HOLD, 1, "AUTHORIZATION", "PRE_AUTHORIZED", "READY_FOR_CAPTURE");
    aPaymentOfTheOldShape(A_DECLINED_HOLD, AutraStatus.DECLINED, null);
    aPaymentOfTheOldShape(A_FULLY_REVERSED_SALE, AutraStatus.VOIDED, null);
    anOperationOfTheOldShape(
        A_FULLY_REVERSED_SALE, 1, "CAPTURE", AutraStatus.ACCEPTED, "SUCCEEDED");
    anOperationOfTheOldShape(A_FULLY_REVERSED_SALE, 2, "REFUND", AutraStatus.VOIDED, "REFUNDED");
    aPaymentOfTheOldShape(A_PARTLY_REVERSED_SALE, AutraStatus.PARTIALLY_VOIDED, null);
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_SALE, 1, "CAPTURE", AutraStatus.ACCEPTED, "SUCCEEDED");
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_SALE, 2, "REFUND", AutraStatus.PARTIALLY_VOIDED, "PARTIALLY_REFUNDED");
    aPaymentOfTheOldShape(A_PARTLY_REVERSED_HOLD, AutraStatus.PARTIALLY_VOIDED, null);
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_HOLD, 1, "AUTHORIZATION", "PRE_AUTHORIZED", "READY_FOR_CAPTURE");
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_HOLD, 2, "CAPTURE", AutraStatus.CAPTURED, "SUCCEEDED");
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_HOLD, 3, "REFUND", AutraStatus.PARTIALLY_VOIDED, "PARTIALLY_REFUNDED");
    flyway(WHEN_THE_READS_LANDED).migrate();
  }

  @Test
  void a_sale_stored_before_this_slice_is_backfilled_as_a_sale() {
    assertThat(theTypeOf(A_SALE)).isEqualTo(AutraTimeline.SALE);
    assertThat(theTypeOf(A_DECLINED_SALE)).isEqualTo(AutraTimeline.SALE);
  }

  @Test
  void a_reversed_sale_is_backfilled_as_a_sale_whichever_status_the_reversal_left_it_at() {
    assertThat(theTypeOf(A_FULLY_REVERSED_SALE)).isEqualTo(AutraTimeline.SALE);
    assertThat(theTypeOf(A_PARTLY_REVERSED_SALE))
        .as(
            "a sale reversed in part reads PARTIALLY_VOIDED with no hold ever released and no"
                + " AUTHORIZATION on its ledger, so a backfill that reads the status instead of"
                + " the ledger types it a pre-authorization and ?type=SALE then misses it")
        .isEqualTo(AutraTimeline.SALE);
  }

  @Test
  void a_hold_reversed_in_part_after_its_capture_is_backfilled_as_a_hold() {
    assertThat(theTypeOf(A_PARTLY_REVERSED_HOLD)).isEqualTo(AutraTimeline.PREAUTH);
  }

  @Test
  void a_hold_stored_before_this_slice_is_backfilled_as_a_hold_in_each_state_it_could_be_in() {
    assertThat(theTypeOf(AN_OPEN_HOLD)).isEqualTo(AutraTimeline.PREAUTH);
    assertThat(theTypeOf(A_CAPTURED_HOLD)).isEqualTo(AutraTimeline.PREAUTH);
    assertThat(theTypeOf(A_RELEASED_HOLD)).isEqualTo(AutraTimeline.PREAUTH);
  }

  @Test
  void a_hold_the_acquirer_declined_before_this_slice_is_backfilled_as_a_sale_and_reads_as_one() {
    assertThat(theTypeOf(A_DECLINED_HOLD)).isEqualTo(AutraTimeline.SALE);
  }

  @Test
  void every_payment_stored_before_this_slice_carries_a_type_after_the_migration() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".autra_payment where payment_type is null",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("select count(*) from " + SCHEMA + ".autra_payment", Integer.class))
        .as(
            "every shape the store could hold before this slice: a sale accepted, declined,"
                + " reversed whole and reversed in part, and a hold open, captured, reversed in"
                + " part, released and declined")
        .isEqualTo(9);
  }

  @Test
  void the_backfilled_column_is_left_with_no_default_so_a_write_that_omits_it_is_refused() {
    assertThatThrownBy(() -> aPaymentOfTheNewShape("aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_store_takes_the_two_things_a_payment_is_and_nothing_else() {
    assertThatCode(() -> aPaymentOfTheNewShape("aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa", "SALE"))
        .doesNotThrowAnyException();
    assertThatCode(() -> aPaymentOfTheNewShape("aaaaaaaa-3333-4333-8333-aaaaaaaaaaaa", "PREAUTH"))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> aPaymentOfTheNewShape("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "CAPTURE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_payment_stored_before_this_slice_reads_back_with_an_empty_timeline() {
    assertThat(theEventCountOf(A_SALE)).isZero();
    assertThat(theEventCountOf(A_CAPTURED_HOLD)).isZero();
  }

  @Test
  void the_timeline_of_a_payment_stored_before_this_slice_can_be_written_from_now_on() {
    assertThatCode(() -> anEventOf(A_SALE, 0, "SALE", AutraStatus.ACCEPTED, null, null))
        .doesNotThrowAnyException();

    assertThat(theEventCountOf(A_SALE)).isEqualTo(1);
  }

  @Test
  void an_event_naming_a_payment_this_run_does_not_hold_is_refused() {
    assertThatThrownBy(
            () ->
                anEventOf(
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                    0,
                    "SALE",
                    AutraStatus.ACCEPTED,
                    null,
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void an_event_carrying_one_half_of_a_refusal_and_not_the_other_is_refused_by_the_store() {
    assertThatThrownBy(
            () ->
                anEventOf(A_DECLINED_SALE, 0, "SALE", AutraStatus.DECLINED, "CARD_DECLINED", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> anEventOf(A_DECLINED_SALE, 1, "SALE", AutraStatus.DECLINED, null, "no good"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(
            () ->
                anEventOf(
                    A_DECLINED_SALE, 2, "SALE", AutraStatus.DECLINED, "CARD_DECLINED", "no good"))
        .doesNotThrowAnyException();
  }

  @Test
  void the_timeline_of_a_payment_goes_when_the_payment_goes() {
    anEventOf(A_SALE, 0, "SALE", AutraStatus.ACCEPTED, null, null);

    jdbc.update(
        "delete from " + SCHEMA + ".autra_payment where test_run_id = ? and payment_id = ?",
        RUN,
        A_SALE);

    assertThat(theEventCountOf(A_SALE)).isZero();
  }

  @Test
  void a_merchant_configured_before_this_slice_keeps_the_values_it_was_configured_with() {
    assertThat(
            jdbc.queryForObject(
                "select client_id from "
                    + SCHEMA
                    + ".autra_merchant where test_run_id = ? and account_id = ?",
                String.class,
                RUN,
                ACCOUNT))
        .isEqualTo("client-already-here");
  }

  private String theTypeOf(String paymentId) {
    return jdbc.queryForObject(
        "select payment_type from "
            + SCHEMA
            + ".autra_payment where test_run_id = ? and payment_id = ?",
        String.class,
        RUN,
        paymentId);
  }

  private int theEventCountOf(String paymentId) {
    return jdbc.queryForObject(
        "select count(*) from "
            + SCHEMA
            + ".autra_payment_event where test_run_id = ? and payment_id = ?",
        Integer.class,
        RUN,
        paymentId);
  }

  private void anEventOf(
      String paymentId, int seq, String type, String status, String errorCode, String msg) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment_event (test_run_id, payment_id, seq, event_type, status, amount,"
            + " error_code, msg, at)"
            + " values (?, ?, ?, ?, ?, '500.00', ?, ?, timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        seq,
        type,
        status,
        errorCode,
        msg);
  }

  private void aPaymentOfTheNewShape(String paymentId, String type) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id, payment_type,"
            + " transaction_type, amount, currency, installments, native_status,"
            + " authorization_code, rrn, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, 'CREDIT', '500.00', 'BRL', 1, 'ACCEPTED', 'A1B2C3',"
            + " '112676199769', 'PROVIDER_API', timestamptz '2026-09-22T12:00:00Z',"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        ACCOUNT,
        DOCUMENT,
        type);
  }

  private void aPaymentOfTheOldShape(String paymentId, String nativeStatus, String releasedAt) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id,"
            + " transaction_type, amount, currency, installments, native_status,"
            + " authorization_code, rrn, tracking_number, hold_released_at, origin, created_at,"
            + " updated_at)"
            + " values (?, ?, ?, ?, 'CREDIT', '500.00', 'BRL', 1, ?, 'A1B2C3', '112676199769',"
            + " '900123456789', ?::timestamptz, 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        ACCOUNT,
        DOCUMENT,
        nativeStatus,
        releasedAt);
  }

  private void anOperationOfTheOldShape(
      String paymentId, int seq, String kind, String nativeStatus, String normalizedStatus) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment_operation (test_run_id, payment_id, seq, kind, amount,"
            + " closes_authorization, created_object_id, native_status, normalized_status,"
            + " created_at)"
            + " values (?, ?, ?, ?, '500.00', null, null, ?, ?,"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        seq,
        kind,
        nativeStatus,
        normalizedStatus);
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
            + " authorizer_rejected_message, merchant_not_found_status, sale_decline_status,"
            + " acquirer_transaction_identifier)"
            + " values (?, ?, 'client-already-here', 'a-secret', ?,"
            + " 'BAD_REQUEST_WITH_ERROR_STRING', 'CAMEL_CASE', 'PUBLISHED_SCHEMA',"
            + " 'PUBLISHED_SCHEMA', '05 - Do not honor', 'NOT_FOUND', 'TWO_HUNDRED_WITH_ERRORS',"
            + " 'REPORTED')",
        RUN,
        ACCOUNT,
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
