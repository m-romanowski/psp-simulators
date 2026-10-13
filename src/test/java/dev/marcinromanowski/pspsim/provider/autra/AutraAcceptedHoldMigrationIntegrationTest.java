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

class AutraAcceptedHoldMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_hold_answered_accepted";

  private static final String RUN = "before-accepted-holds";

  private static final String ACCOUNT = "merchant-already-here";

  private static final String DOCUMENT = "25055662000190";

  private static final String A_SALE = "11111111-1111-4111-8111-111111111111";

  private static final String A_DECLINED_SALE = "22222222-2222-4222-8222-222222222222";

  private static final String AN_OPEN_HOLD = "33333333-3333-4333-8333-333333333333";

  private static final String A_CAPTURED_HOLD = "44444444-4444-4444-8444-444444444444";

  private static final String A_RELEASED_HOLD = "55555555-5555-4555-8555-555555555555";

  private static final String A_DECLINED_HOLD = "66666666-6666-4666-8666-666666666666";

  private static final String A_FULLY_REVERSED_SALE = "77777777-7777-4777-8777-777777777777";

  private static final String A_PARTLY_REVERSED_HOLD = "88888888-8888-4888-8888-888888888888";

  private static final String A_STATUS_AUTRA_ADDED_LATER = "99999999-9999-4999-8999-999999999999";

  private static final String A_ZERO_DOLLAR_VALIDATION = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";

  private static final String A_PARTLY_REVERSED_SALE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2";

  private static final String A_FULLY_REVERSED_HOLD = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3";

  private static final String THE_OLD_WORD = "PRE_AUTHORIZED";

  private static final String THE_WORD_AUTRA_ANSWERS = "ACCEPTED";

  private static final String AN_ORDER_HELD_BEFORE_THE_CALL = "pedido-being-claimed";

  private static final String AN_ORDER_A_HOLD_SETTLED = "pedido-abc-123";

  private static final MigrationVersion BEFORE_ACCEPTED_HOLDS = MigrationVersion.fromVersion("77");

  private static final MigrationVersion WHEN_ACCEPTED_HOLDS_LANDED =
      MigrationVersion.fromVersion("78");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void leaveNoSchemaOfThisClassBehindInTheSharedContainer() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
  }

  @BeforeEach
  void migrateOverADatabaseHoldingPaymentsOfEveryShapeTheOldStoreCouldHold() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_ACCEPTED_HOLDS).migrate();
    aTestRunOfTheOldShape();
    aMerchantOfTheOldShape();
    aPaymentOfTheOldShape(A_SALE, AutraTimeline.SALE, AutraStatus.ACCEPTED, null);
    anOperationOfTheOldShape(A_SALE, 1, "CAPTURE", AutraStatus.ACCEPTED, "SUCCEEDED");
    anEventOfTheOldShape(A_SALE, 1, AutraTimeline.SALE, AutraStatus.ACCEPTED);
    aPaymentOfTheOldShape(A_DECLINED_SALE, AutraTimeline.SALE, AutraStatus.DECLINED, null);
    anEventOfTheOldShape(A_DECLINED_SALE, 1, AutraTimeline.SALE, AutraStatus.DECLINED);
    aPaymentOfTheOldShape(AN_OPEN_HOLD, AutraTimeline.PREAUTH, THE_OLD_WORD, null);
    anOperationOfTheOldShape(AN_OPEN_HOLD, 1, "AUTHORIZATION", THE_OLD_WORD, "READY_FOR_CAPTURE");
    anEventOfTheOldShape(AN_OPEN_HOLD, 1, AutraTimeline.PREAUTH, THE_OLD_WORD);
    aPaymentOfTheOldShape(A_CAPTURED_HOLD, AutraTimeline.PREAUTH, AutraStatus.CAPTURED, null);
    anOperationOfTheOldShape(
        A_CAPTURED_HOLD, 1, "AUTHORIZATION", THE_OLD_WORD, "READY_FOR_CAPTURE");
    anOperationOfTheOldShape(A_CAPTURED_HOLD, 2, "CAPTURE", AutraStatus.CAPTURED, "SUCCEEDED");
    anEventOfTheOldShape(A_CAPTURED_HOLD, 1, AutraTimeline.PREAUTH, THE_OLD_WORD);
    anEventOfTheOldShape(A_CAPTURED_HOLD, 2, AutraTimeline.CAPTURE, AutraStatus.CAPTURED);
    aPaymentOfTheOldShape(
        A_RELEASED_HOLD, AutraTimeline.PREAUTH, AutraStatus.VOIDED, "2026-09-22T13:00:00Z");
    anOperationOfTheOldShape(
        A_RELEASED_HOLD, 1, "AUTHORIZATION", THE_OLD_WORD, "READY_FOR_CAPTURE");
    anEventOfTheOldShape(A_RELEASED_HOLD, 1, AutraTimeline.PREAUTH, THE_OLD_WORD);
    anEventOfTheOldShape(A_RELEASED_HOLD, 2, AutraTimeline.VOID, AutraStatus.VOIDED);
    aPaymentOfTheOldShape(A_DECLINED_HOLD, AutraTimeline.PREAUTH, AutraStatus.DECLINED, null);
    anEventOfTheOldShape(A_DECLINED_HOLD, 1, AutraTimeline.PREAUTH, AutraStatus.DECLINED);
    aPaymentOfTheOldShape(A_FULLY_REVERSED_SALE, AutraTimeline.SALE, AutraStatus.VOIDED, null);
    anOperationOfTheOldShape(
        A_FULLY_REVERSED_SALE, 1, "CAPTURE", AutraStatus.ACCEPTED, "SUCCEEDED");
    anOperationOfTheOldShape(A_FULLY_REVERSED_SALE, 2, "REFUND", AutraStatus.VOIDED, "REFUNDED");
    aPaymentOfTheOldShape(
        A_PARTLY_REVERSED_HOLD, AutraTimeline.PREAUTH, AutraStatus.PARTIALLY_VOIDED, null);
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_HOLD, 1, "AUTHORIZATION", THE_OLD_WORD, "READY_FOR_CAPTURE");
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_HOLD, 2, "CAPTURE", AutraStatus.CAPTURED, "SUCCEEDED");
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_HOLD, 3, "REFUND", AutraStatus.PARTIALLY_VOIDED, "PARTIALLY_REFUNDED");
    aPaymentOfTheOldShape(
        A_STATUS_AUTRA_ADDED_LATER, AutraTimeline.SALE, "CHARGEBACK_OPENED", null);
    aZeroAmountPaymentOfTheOldShape(
        A_ZERO_DOLLAR_VALIDATION, AutraTimeline.SALE, AutraStatus.ACCEPTED);
    aPaymentOfTheOldShape(
        A_PARTLY_REVERSED_SALE, AutraTimeline.SALE, AutraStatus.PARTIALLY_VOIDED, null);
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_SALE, 1, "CAPTURE", AutraStatus.ACCEPTED, "SUCCEEDED");
    anOperationOfTheOldShape(
        A_PARTLY_REVERSED_SALE, 2, "REFUND", AutraStatus.PARTIALLY_VOIDED, "PARTIALLY_REFUNDED");
    aPaymentOfTheOldShape(A_FULLY_REVERSED_HOLD, AutraTimeline.PREAUTH, AutraStatus.VOIDED, null);
    anOperationOfTheOldShape(
        A_FULLY_REVERSED_HOLD, 1, "AUTHORIZATION", THE_OLD_WORD, "READY_FOR_CAPTURE");
    anOperationOfTheOldShape(
        A_FULLY_REVERSED_HOLD, 2, "CAPTURE", AutraStatus.CAPTURED, "SUCCEEDED");
    anOperationOfTheOldShape(A_FULLY_REVERSED_HOLD, 3, "REFUND", AutraStatus.VOIDED, "REFUNDED");
    anOrderClaimOfTheOldShape(
        AN_ORDER_HELD_BEFORE_THE_CALL, "HELD_BEFORE_THE_ACQUIRER_WAS_CALLED", null);
    anOrderClaimOfTheOldShape(AN_ORDER_A_HOLD_SETTLED, THE_OLD_WORD, AN_OPEN_HOLD);
    flyway(WHEN_ACCEPTED_HOLDS_LANDED).migrate();
  }

  @Test
  void a_reservation_open_before_this_slice_answers_the_word_autra_answers_after_it() {
    assertThat(theStatusOf(AN_OPEN_HOLD)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
  }

  @Test
  void a_reservation_rewritten_by_this_slice_keeps_the_type_that_tells_it_from_a_sale() {
    assertThat(theTypeOf(AN_OPEN_HOLD)).isEqualTo(AutraTimeline.PREAUTH);
    assertThat(theStatusOf(A_SALE)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theTypeOf(A_SALE)).isEqualTo(AutraTimeline.SALE);
  }

  @Test
  void every_other_status_the_old_store_held_is_left_exactly_as_it_was() {
    assertThat(theStatusOf(A_DECLINED_SALE)).isEqualTo(AutraStatus.DECLINED);
    assertThat(theStatusOf(A_CAPTURED_HOLD)).isEqualTo(AutraStatus.CAPTURED);
    assertThat(theStatusOf(A_RELEASED_HOLD)).isEqualTo(AutraStatus.VOIDED);
    assertThat(theStatusOf(A_DECLINED_HOLD)).isEqualTo(AutraStatus.DECLINED);
    assertThat(theStatusOf(A_FULLY_REVERSED_SALE)).isEqualTo(AutraStatus.VOIDED);
    assertThat(theStatusOf(A_PARTLY_REVERSED_HOLD)).isEqualTo(AutraStatus.PARTIALLY_VOIDED);
    assertThat(theStatusOf(A_PARTLY_REVERSED_SALE)).isEqualTo(AutraStatus.PARTIALLY_VOIDED);
    assertThat(theStatusOf(A_FULLY_REVERSED_HOLD)).isEqualTo(AutraStatus.VOIDED);
    assertThat(theStatusOf(A_ZERO_DOLLAR_VALIDATION)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theStatusOf(A_STATUS_AUTRA_ADDED_LATER))
        .as("autra publishes its statuses as an open set, so a value this build never wrote stays")
        .isEqualTo("CHARGEBACK_OPENED");
  }

  @Test
  void no_payment_row_anywhere_still_answers_the_word_only_the_vendors_guide_published() {
    assertThat(theRowsHolding("autra_payment", "native_status")).isZero();
  }

  @Test
  void the_reservation_on_the_ledger_of_a_hold_in_every_state_it_could_be_in_answers_accepted() {
    assertThat(theOperationStatusOf(AN_OPEN_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theOperationStatusOf(A_CAPTURED_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theOperationStatusOf(A_RELEASED_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theOperationStatusOf(A_PARTLY_REVERSED_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theOperationStatusOf(A_FULLY_REVERSED_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theRowsHolding("autra_payment_operation", "native_status")).isZero();
  }

  @Test
  void the_reservation_step_on_the_timeline_of_a_hold_answers_accepted_and_the_rest_is_untouched() {
    assertThat(theEventStatusOf(AN_OPEN_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theEventStatusOf(A_CAPTURED_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theEventStatusOf(A_CAPTURED_HOLD, 2)).isEqualTo(AutraStatus.CAPTURED);
    assertThat(theEventStatusOf(A_RELEASED_HOLD, 1)).isEqualTo(THE_WORD_AUTRA_ANSWERS);
    assertThat(theEventStatusOf(A_RELEASED_HOLD, 2)).isEqualTo(AutraStatus.VOIDED);
    assertThat(theEventStatusOf(A_DECLINED_HOLD, 1)).isEqualTo(AutraStatus.DECLINED);
    assertThat(theRowsHolding("autra_payment_event", "status")).isZero();
  }

  @Test
  void an_order_claim_a_hold_settled_before_this_slice_still_names_the_payment_that_holds_it() {
    assertThat(theClaimStateOf(AN_ORDER_A_HOLD_SETTLED))
        .isEqualTo("HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES");
    assertThat(thePaymentNamedBy(AN_ORDER_A_HOLD_SETTLED)).isEqualTo(AN_OPEN_HOLD);
    assertThat(theClaimStateOf(AN_ORDER_HELD_BEFORE_THE_CALL))
        .isEqualTo("HELD_BEFORE_THE_ACQUIRER_WAS_CALLED");
    assertThat(theRowsHolding("autra_order_claim", "claim_state")).isZero();
  }

  @Test
  void the_claim_state_the_store_no_longer_knows_is_refused_and_the_one_it_knows_is_taken() {
    assertThatThrownBy(() -> anOrderClaimOfTheOldShape("pedido-rejected", THE_OLD_WORD, A_SALE))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(
            () ->
                anOrderClaimOfTheOldShape(
                    "pedido-taken", "HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES", A_SALE))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                anOrderClaimOfTheOldShape(
                    "pedido-no-payment", "HELD_BY_THE_PRE_AUTHORIZATION_IT_NAMES", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_sale_the_store_is_told_was_released_is_refused_because_only_a_hold_can_be_released() {
    assertThatThrownBy(
            () ->
                aPaymentOfTheOldShape(
                    "aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa",
                    AutraTimeline.SALE,
                    AutraStatus.VOIDED,
                    "2026-09-22T13:00:00Z"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(
            () ->
                aPaymentOfTheOldShape(
                    "aaaaaaaa-2222-4222-8222-aaaaaaaaaaaa",
                    AutraTimeline.PREAUTH,
                    AutraStatus.VOIDED,
                    "2026-09-22T13:00:00Z"))
        .doesNotThrowAnyException();
  }

  @Test
  void every_shape_the_old_store_could_hold_was_seeded_before_the_migration_ran() {
    assertThat(
            jdbc.queryForObject("select count(*) from " + SCHEMA + ".autra_payment", Integer.class))
        .as(
            "every shape this provider can store: a sale accepted, accepted at a zero amount,"
                + " declined, reversed whole and reversed in part; a hold open, captured,"
                + " released, declined, reversed whole after its capture and reversed in part"
                + " after its capture; and a payment at a status autra published after this build")
        .isEqualTo(12);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".autra_payment_operation", Integer.class))
        .isEqualTo(15);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".autra_payment_event", Integer.class))
        .isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".autra_order_claim", Integer.class))
        .isEqualTo(2);
  }

  private int theRowsHolding(String table, String column) {
    return jdbc.queryForObject(
        "select count(*) from " + SCHEMA + "." + table + " where " + column + " = ?",
        Integer.class,
        THE_OLD_WORD);
  }

  private String theStatusOf(String paymentId) {
    return jdbc.queryForObject(
        "select native_status from "
            + SCHEMA
            + ".autra_payment where test_run_id = ? and payment_id = ?",
        String.class,
        RUN,
        paymentId);
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

  private String theOperationStatusOf(String paymentId, int seq) {
    return jdbc.queryForObject(
        "select native_status from "
            + SCHEMA
            + ".autra_payment_operation where test_run_id = ? and payment_id = ? and seq = ?",
        String.class,
        RUN,
        paymentId,
        seq);
  }

  private String theEventStatusOf(String paymentId, int seq) {
    return jdbc.queryForObject(
        "select status from "
            + SCHEMA
            + ".autra_payment_event where test_run_id = ? and payment_id = ? and seq = ?",
        String.class,
        RUN,
        paymentId,
        seq);
  }

  private String theClaimStateOf(String orderId) {
    return jdbc.queryForObject(
        "select claim_state from "
            + SCHEMA
            + ".autra_order_claim where test_run_id = ? and document_id = ? and order_id = ?",
        String.class,
        RUN,
        DOCUMENT,
        orderId);
  }

  private String thePaymentNamedBy(String orderId) {
    return jdbc.queryForObject(
        "select payment_id from "
            + SCHEMA
            + ".autra_order_claim where test_run_id = ? and document_id = ? and order_id = ?",
        String.class,
        RUN,
        DOCUMENT,
        orderId);
  }

  private void anOrderClaimOfTheOldShape(String orderId, String claimState, String paymentId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_order_claim (test_run_id, document_id, order_id, account_id, claim_state,"
            + " payment_id, at)"
            + " values (?, ?, ?, ?, ?, ?, timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        DOCUMENT,
        orderId,
        ACCOUNT,
        claimState,
        paymentId);
  }

  private void anEventOfTheOldShape(String paymentId, int seq, String type, String status) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment_event (test_run_id, payment_id, seq, event_type, status, amount, at)"
            + " values (?, ?, ?, ?, ?, '500.00', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        seq,
        type,
        status);
  }

  private void aZeroAmountPaymentOfTheOldShape(String paymentId, String type, String nativeStatus) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id, payment_type,"
            + " transaction_type, amount, currency, installments, native_status,"
            + " authorization_code, rrn, tracking_number, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, ?, 'CREDIT', '0.00', 'BRL', 1, ?, 'A1B2C3', '112676199769',"
            + " '900123456789', 'PROVIDER_API', timestamptz '2026-09-22T12:00:00Z',"
            + " timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        ACCOUNT,
        DOCUMENT,
        type,
        nativeStatus);
  }

  private void aPaymentOfTheOldShape(
      String paymentId, String type, String nativeStatus, String releasedAt) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".autra_payment (test_run_id, payment_id, account_id, document_id, payment_type,"
            + " transaction_type, amount, currency, installments, native_status,"
            + " authorization_code, rrn, tracking_number, hold_released_at, origin, created_at,"
            + " updated_at)"
            + " values (?, ?, ?, ?, ?, 'CREDIT', '500.00', 'BRL', 1, ?, 'A1B2C3', '112676199769',"
            + " '900123456789', ?::timestamptz, 'PROVIDER_API',"
            + " timestamptz '2026-09-22T12:00:00Z', timestamptz '2026-09-22T12:00:00Z')",
        RUN,
        paymentId,
        ACCOUNT,
        DOCUMENT,
        type,
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
