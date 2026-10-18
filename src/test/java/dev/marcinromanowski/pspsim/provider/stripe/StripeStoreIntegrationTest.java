package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeStoreIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "stripe-store-it";

  private static final String MERCHANT = "merchant-1";

  private static final String INTENT = "pi_sim_store_it";

  private static final OffsetDateTime AT =
      OffsetDateTime.of(2026, 9, 11, 12, 0, 0, 0, ZoneOffset.UTC);

  private static final String A_KEY = "a-key-of-one-request";

  private static final String A_FINGERPRINT = "a-fingerprint";

  private static final StripeStoredAnswer AN_ANSWER =
      new StripeStoredAnswer(
          200, "application/json", "{\"id\":\"pi_sim_store_it\"}", ProviderOutcome.SUCCESS, INTENT);

  private static final StripeStoredAnswer ANOTHER_ANSWER =
      new StripeStoredAnswer(402, "application/json", "{}", ProviderOutcome.FAILURE, null);

  private static final TestRunId RUN_ID = TestRunId.of(RUN);

  @Autowired private JdbcTemplate jdbc;
  @Autowired private StripeIdempotencyKeys keys;
  @Autowired private StripePaymentIntents intents;
  @Autowired private StripeRefunds refunds;

  @BeforeEach
  void clearWhatThisTestWrites() {
    jdbc.update("delete from stripe_payment_intent where test_run_id = ?", RUN);
    jdbc.update("delete from stripe_idempotency_key where test_run_id = ?", RUN);
    jdbc.update("delete from stripe_account where test_run_id = ?", RUN);
    jdbc.update("delete from stripe_intent_id_claim where test_run_id = ?", RUN);
    jdbc.update("delete from stripe_refund where test_run_id = ?", RUN);
    jdbc.update("delete from stripe_idempotency_exchange where test_run_id = ?", RUN);
  }

  @Test
  void refuses_an_account_whose_secret_key_is_not_a_test_mode_key() {
    assertThatThrownBy(() -> anAccountWith("sk_live_51Simulator", "PT72H"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refuses_an_account_whose_capture_window_is_not_a_stored_duration() {
    assertThatThrownBy(() -> anAccountWith("sk_test_51Simulator", "seven days"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> anAccountWith("sk_test_51Another", "PT9X"))
        .as(
            "the column's own check was ~ '^PT[0-9]', which this text satisfies and"
                + " Duration.parse throws on, so the store took a window StripeCaptureWindow"
                + " could not read back")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void keeps_an_account_the_provider_would_have_parsed() {
    assertThatCode(() -> anAccountWith("sk_test_51Simulator", "PT72H")).doesNotThrowAnyException();
  }

  @Test
  void refuses_a_payment_intent_whose_status_the_vendor_does_not_publish() {
    assertThatThrownBy(() -> anIntentWith("requires_source", "1099"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refuses_a_payment_intent_whose_amount_is_not_a_positive_integer_in_minor_units() {
    assertThatThrownBy(() -> anIntentWith("requires_capture", "10.99"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refuses_an_operation_that_names_the_payment_intent_whose_ledger_it_is_on() {
    anIntentWith("requires_capture", "1099");

    assertThatThrownBy(() -> anOperationNaming(INTENT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refuses_an_idempotency_row_that_is_still_in_progress_and_already_holds_an_answer() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    insert into stripe_idempotency_key
                        (test_run_id, account_id, idempotency_key, fingerprint, in_progress,
                         http_status, content_type, body, outcome, created_at)
                    values (?, ?, ?, ?, true, 200, 'application/json', '{}', 'SUCCESS', ?)
                    """,
                    RUN,
                    MERCHANT,
                    "a-key",
                    "a-fingerprint",
                    AT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refuses_a_completed_idempotency_row_that_holds_no_answer() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    insert into stripe_idempotency_key
                        (test_run_id, account_id, idempotency_key, fingerprint, in_progress,
                         created_at)
                    values (?, ?, ?, ?, false, ?)
                    """,
                    RUN,
                    MERCHANT,
                    "a-key",
                    "a-fingerprint",
                    AT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refuses_a_second_claim_of_a_key_whose_first_request_has_not_answered_yet() {
    StripeIdempotencyClaim first = claim(A_KEY, A_FINGERPRINT, AT.toInstant());

    StripeIdempotencyClaim second = claim(A_KEY, A_FINGERPRINT, AT.toInstant());

    assertThat(first).isInstanceOf(StripeIdempotencyClaim.Fresh.class);
    assertThat(second).isInstanceOf(StripeIdempotencyClaim.InProgressElsewhere.class);
  }

  @Test
  void replays_the_answer_the_first_request_stored_under_a_key() {
    claim(A_KEY, A_FINGERPRINT, AT.toInstant());
    keys.completed(RUN_ID, MERCHANT, A_KEY, AN_ANSWER);

    StripeIdempotencyClaim second = claim(A_KEY, A_FINGERPRINT, AT.toInstant());

    assertThat(second)
        .isInstanceOfSatisfying(
            StripeIdempotencyClaim.Replayed.class,
            replayed -> assertThat(replayed.answer()).isEqualTo(AN_ANSWER));
  }

  @Test
  void refuses_a_key_a_later_request_reused_with_other_parameters() {
    claim(A_KEY, A_FINGERPRINT, AT.toInstant());
    keys.completed(RUN_ID, MERCHANT, A_KEY, AN_ANSWER);

    StripeIdempotencyClaim second = claim(A_KEY, "another-fingerprint", AT.toInstant());

    assertThat(second).isInstanceOf(StripeIdempotencyClaim.ReusedWithOtherParameters.class);
  }

  @Test
  void forgets_a_key_older_than_the_published_window() {
    claim(A_KEY, A_FINGERPRINT, AT.toInstant());
    keys.completed(RUN_ID, MERCHANT, A_KEY, AN_ANSWER);

    StripeIdempotencyClaim afterTheWindow =
        claim(A_KEY, A_FINGERPRINT, AT.toInstant().plus(StripeIdempotency.WINDOW).plusSeconds(1));

    assertThat(afterTheWindow).isInstanceOf(StripeIdempotencyClaim.Fresh.class);
  }

  @Test
  void frees_a_key_whose_request_never_began_executing() {
    claim(A_KEY, A_FINGERPRINT, AT.toInstant());

    keys.released(RUN_ID, MERCHANT, A_KEY);

    assertThat(claim(A_KEY, A_FINGERPRINT, AT.toInstant()))
        .isInstanceOf(StripeIdempotencyClaim.Fresh.class);
  }

  @Test
  void keeps_an_answer_a_second_request_cannot_overwrite() {
    claim(A_KEY, A_FINGERPRINT, AT.toInstant());
    keys.completed(RUN_ID, MERCHANT, A_KEY, AN_ANSWER);

    keys.completed(RUN_ID, MERCHANT, A_KEY, ANOTHER_ANSWER);
    keys.released(RUN_ID, MERCHANT, A_KEY);

    assertThat(claim(A_KEY, A_FINGERPRINT, AT.toInstant()))
        .isInstanceOfSatisfying(
            StripeIdempotencyClaim.Replayed.class,
            replayed -> assertThat(replayed.answer()).isEqualTo(AN_ANSWER));
  }

  @Test
  void lets_one_capture_of_two_concurrent_ones_write_the_ledger() {
    anIntentWith("requires_capture", "1099");
    StripePaymentIntent held = intents.find(RUN_ID, INTENT).orElseThrow();

    LedgerClaim first =
        intents.captured(held, StripeIntentStatus.SUCCEEDED, aCapture(held), AT.toInstant());
    LedgerClaim second =
        intents.captured(held, StripeIntentStatus.SUCCEEDED, aCapture(held), AT.toInstant());

    assertThat(first).isEqualTo(LedgerClaim.APPLIED);
    assertThat(second).isEqualTo(LedgerClaim.CLAIM_TAKEN);
    assertThat(intents.find(RUN_ID, INTENT).orElseThrow().ledger().operations()).hasSize(1);
  }

  @Test
  void keeps_a_refund_beside_the_payment_it_returned_and_reads_it_back_by_its_own_id() {
    StripePaymentIntent captured = aCapturedIntent();

    StripeRefunds.Refunded applied =
        refunds.refund(
            captured,
            aRefund(captured, "re_sim_store_it"),
            refundRow("re_sim_store_it"),
            AT.toInstant());

    assertThat(applied).isEqualTo(StripeRefunds.Refunded.APPLIED);
    assertThat(refunds.find(RUN_ID, "re_sim_store_it"))
        .hasValueSatisfying(
            refund -> {
              assertThat(refund.intentId()).isEqualTo(INTENT);
              assertThat(refund.amount().value()).isEqualTo("400");
              assertThat(refund.reason()).isEqualTo(StripeRefundReason.REQUESTED_BY_CUSTOMER);
              assertThat(refund.status()).isEqualTo(StripeRefundStatus.SUCCEEDED);
            });
    assertThat(intents.find(RUN_ID, INTENT).orElseThrow().ledger().count(OperationKind.REFUND))
        .isEqualTo(1);
  }

  @Test
  void refuses_a_second_refund_that_drew_an_id_the_first_one_already_took() {
    StripePaymentIntent captured = aCapturedIntent();
    refunds.refund(
        captured, aRefund(captured, "re_sim_twice"), refundRow("re_sim_twice"), AT.toInstant());
    StripePaymentIntent afterTheFirst = intents.find(RUN_ID, INTENT).orElseThrow();

    StripeRefunds.Refunded again =
        refunds.refund(
            afterTheFirst,
            aRefund(afterTheFirst, "re_sim_twice"),
            refundRow("re_sim_twice"),
            AT.toInstant());

    assertThat(again).isEqualTo(StripeRefunds.Refunded.ID_TAKEN);
    assertThat(intents.find(RUN_ID, INTENT).orElseThrow().ledger().count(OperationKind.REFUND))
        .isEqualTo(1);
  }

  @Test
  void lets_one_refund_of_two_that_drew_the_same_ledger_place_write_it() {
    StripePaymentIntent captured = aCapturedIntent();

    StripeRefunds.Refunded first =
        refunds.refund(
            captured, aRefund(captured, "re_sim_first"), refundRow("re_sim_first"), AT.toInstant());
    StripeRefunds.Refunded second =
        refunds.refund(
            captured,
            aRefund(captured, "re_sim_second"),
            refundRow("re_sim_second"),
            AT.toInstant());

    assertThat(first).isEqualTo(StripeRefunds.Refunded.APPLIED);
    assertThat(second).isEqualTo(StripeRefunds.Refunded.CLAIM_TAKEN);
    assertThat(refunds.find(RUN_ID, "re_sim_second")).isEmpty();
  }

  @Test
  void reports_a_refund_of_a_payment_no_test_run_of_this_simulator_holds_as_gone() {
    StripePaymentIntent captured = aCapturedIntent();
    jdbc.update("delete from stripe_payment_intent where test_run_id = ?", RUN);

    StripeRefunds.Refunded applied =
        refunds.refund(
            captured, aRefund(captured, "re_sim_gone"), refundRow("re_sim_gone"), AT.toInstant());

    assertThat(applied).isEqualTo(StripeRefunds.Refunded.OBJECT_GONE);
  }

  @Test
  void refuses_a_stored_refund_whose_status_the_vendor_does_not_publish() {
    aCapturedIntent();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    insert into stripe_refund
                        (test_run_id, refund_id, intent_id, account_id, amount, currency, reason,
                         status, origin, created_at, updated_at)
                    values (?, 're_sim_invented', ?, ?, '400', 'pln', null, 'disputed',
                            'PROVIDER_API', ?, ?)
                    """,
                    RUN,
                    INTENT,
                    MERCHANT,
                    AT,
                    AT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void clears_the_refunds_of_a_test_run_along_with_its_payments() {
    StripePaymentIntent captured = aCapturedIntent();
    refunds.refund(
        captured, aRefund(captured, "re_sim_cleared"), refundRow("re_sim_cleared"), AT.toInstant());

    int cleared = refunds.clear(RUN_ID);

    assertThat(cleared).isEqualTo(1);
    assertThat(refunds.find(RUN_ID, "re_sim_cleared")).isEmpty();
  }

  private StripePaymentIntent aCapturedIntent() {
    anIntentWith("succeeded", "1000");
    jdbc.update(
        """
        insert into stripe_payment_intent_operation
            (test_run_id, intent_id, seq, kind, amount, closes_authorization, native_status,
             normalized_status, created_at)
        values (?, ?, 1, 'CAPTURE', '1000', true, 'succeeded', 'SUCCEEDED', ?)
        """,
        RUN,
        INTENT,
        AT);
    return intents.find(RUN_ID, INTENT).orElseThrow();
  }

  private static MonetaryOperation aRefund(StripePaymentIntent intent, String refundId) {
    return new MonetaryOperation(
        intent.ledger().nextSequence(),
        OperationKind.REFUND,
        Amount.of("400"),
        null,
        refundId,
        "succeeded",
        NormalizedStatus.SUCCEEDED,
        AT.toInstant());
  }

  private static StripeRefund refundRow(String refundId) {
    return new StripeRefund(
        RUN_ID,
        refundId,
        INTENT,
        null,
        MERCHANT,
        Amount.of("400"),
        "pln",
        StripeRefundReason.REQUESTED_BY_CUSTOMER,
        StripeRefundStatus.SUCCEEDED,
        ObjectOrigin.PROVIDER_API,
        AT.toInstant(),
        AT.toInstant());
  }

  private static MonetaryOperation aCapture(StripePaymentIntent intent) {
    return new MonetaryOperation(
        intent.ledger().nextSequence(),
        OperationKind.CAPTURE,
        Amount.of("1099"),
        Boolean.TRUE,
        null,
        "succeeded",
        NormalizedStatus.SUCCEEDED,
        AT.toInstant());
  }

  @Test
  void reads_back_a_refusal_recorded_before_the_request_that_was_holding_the_key_answered() {
    keys.observed(observed(KeyedOutcome.REFUSED_WHILE_THE_KEY_WAS_HELD, 409, held()));
    keys.observed(observed(KeyedOutcome.RAN_THE_OPERATION, 200, Map.of("id", List.of("pi_1"))));

    KeyedExchange exchange =
        keys.exchanges(new KeyedExchangeQuery(RUN_ID, KeyedExchangeQuery.FROM_THE_NEWEST, 50))
            .exchanges()
            .get(0);

    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(
            ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD, ExchangeVerdict.FIRST_REQUEST);
    assertThat(exchange.firstRequest().httpStatus()).isEqualTo(200);
  }

  @Test
  void keeps_the_answer_of_a_request_under_a_key_with_the_secrets_the_trail_withholds_redacted() {
    keys.observed(
        observed(
            KeyedOutcome.RAN_THE_OPERATION,
            200,
            Map.of("id", List.of("pi_1"), "client_secret", List.of("pi_1_secret_kept"))));

    assertThat(
            jdbc.queryForObject(
                "select body from stripe_idempotency_exchange where test_run_id = ?"
                    + " and idempotency_key = ?",
                String.class,
                RUN,
                A_KEY))
        .contains("pi_1")
        .contains(Redaction.REDACTED)
        .doesNotContain("pi_1_secret_kept");
  }

  @Test
  void numbers_every_one_of_several_requests_recorded_at_once_under_one_key_differently()
      throws Exception {
    int atOnce = 8;
    CountDownLatch together = new CountDownLatch(1);
    ExecutorService threads = Executors.newFixedThreadPool(atOnce);
    List<Future<?>> writing = new ArrayList<>();
    try {
      for (int each = 0; each < atOnce; each++) {
        writing.add(
            threads.submit(
                () -> {
                  together.await(10, TimeUnit.SECONDS);
                  keys.observed(observed(KeyedOutcome.REFUSED_WHILE_THE_KEY_WAS_HELD, 409, held()));
                  return null;
                }));
      }
      together.countDown();
      for (Future<?> each : writing) {
        each.get(30, TimeUnit.SECONDS);
      }
    } finally {
      threads.shutdownNow();
    }

    assertThat(
            jdbc.queryForList(
                "select seq from stripe_idempotency_exchange where test_run_id = ?"
                    + " and idempotency_key = ? order by seq",
                Integer.class,
                RUN,
                A_KEY))
        .containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
  }

  private static Map<String, List<String>> held() {
    return Map.of("held", List.of("1"));
  }

  private static KeyedObservation observed(
      KeyedOutcome outcome, int httpStatus, Map<String, List<String>> answered) {
    return new KeyedObservation(
        RUN_ID,
        MERCHANT,
        StripeKeyMechanisms.IDEMPOTENCY_KEY,
        A_KEY,
        "createPaymentIntent",
        outcome,
        new FingerprintedRequest("POST", "/v1/payment_intents", Map.of()),
        new KeyedAnswer(httpStatus, answered),
        null,
        false,
        null,
        AT.toInstant());
  }

  private StripeIdempotencyClaim claim(String key, String fingerprint, Instant now) {
    return keys.claim(RUN_ID, MERCHANT, key, fingerprint, now, now.minus(StripeIdempotency.WINDOW));
  }

  private void anAccountWith(String secretKey, String captureWindow) {
    jdbc.update(
        """
        insert into stripe_account
            (test_run_id, account_id, secret_key, capture_window, webhook_secret,
             webhook_signature, webhook_timestamp_age, refund_status)
        values (?, ?, ?, ?, null, 'VALID', 'PT0S', 'SUCCEEDED')
        """,
        RUN,
        MERCHANT,
        secretKey,
        captureWindow);
  }

  private void anIntentWith(String status, String amount) {
    jdbc.update(
        """
        insert into stripe_payment_intent
            (test_run_id, intent_id, account_id, amount, currency, capture_method, status,
             client_secret, capture_expires_at, origin, created_at, updated_at)
        values (?, ?, ?, ?, 'pln', 'manual', ?, 'secret', ?, 'PROVIDER_API', ?, ?)
        """,
        RUN,
        INTENT,
        MERCHANT,
        amount,
        status,
        AT,
        AT,
        AT);
  }

  private void anOperationNaming(String createdObjectId) {
    jdbc.update(
        """
        insert into stripe_payment_intent_operation
            (test_run_id, intent_id, seq, kind, amount, created_object_id, native_status,
             normalized_status, created_at)
        values (?, ?, 1, 'AUTHORIZATION', '1099', ?, 'requires_capture', 'READY_FOR_CAPTURE', ?)
        """,
        RUN,
        INTENT,
        createdObjectId,
        AT);
  }
}
