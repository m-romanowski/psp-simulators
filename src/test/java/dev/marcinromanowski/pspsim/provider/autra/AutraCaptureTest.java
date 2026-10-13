package dev.marcinromanowski.pspsim.provider.autra;

import static dev.marcinromanowski.pspsim.provider.autra.AutraPreAuthorizationTest.theCodeIn;
import static dev.marcinromanowski.pspsim.provider.autra.AutraPreAuthorizationTest.theMessageIn;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraCaptureTest {

  private static final TestRunId RUN = TestRunId.of("autra-capture");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final AutraApi api = apiFor(Map.of());

  private AutraApi apiFor(Map<String, String> configuration) {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, configuration));
    return AutraWiring.api(merchants, sessions, payments, audit, RUN, NOW, scenarios);
  }

  private String aHoldOf(String amount) {
    return AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null)
        .providerObjectId();
  }

  private AutraApiResponse capture(String paymentId, String body) {
    return api.capture(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/capture",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            body),
        paymentId);
  }

  private String captureBody(String amount) {
    return "{\"transactionalToken\":\""
        + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\""
        + (amount == null ? "" : ",\"amount\":" + amount)
        + "}";
  }

  @Test
  void a_capture_is_answered_the_inferred_shape_with_the_identifier_spelled_paymentID() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response = capture(paymentId, captureBody("480.00"));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.text(AutraFields.PAYMENT_ID_UPPERCASE_D)).contains(paymentId);
    assertThat(body.text(AutraFields.PAYMENT_ID_LOWERCASE_D)).isEmpty();
    assertThat(body.text(AutraFields.STATUS)).contains(AutraStatus.CAPTURED);
    assertThat(body.number(AutraFields.AMOUNT)).contains("480.00");
    assertThat(body.text(AutraFields.CURRENCY)).contains("BRL");
  }

  @Test
  void the_capture_it_answered_leaves_the_payment_captured_with_a_capture_on_its_ledger() {
    String paymentId = aHoldOf("500.00");

    capture(paymentId, captureBody("480.00"));

    AutraPayment stored = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(stored.nativeStatus()).isEqualTo(AutraStatus.CAPTURED);
    assertThat(stored.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(stored.ledger().count(OperationKind.CAPTURE)).isEqualTo(1);
    assertThat(stored.capturableRemainder()).isEqualByComparingTo("0");
  }

  @Test
  void
      the_capture_operation_closes_the_authorization_because_a_captured_hold_takes_no_second_one() {
    String paymentId = aHoldOf("500.00");

    capture(paymentId, captureBody("480.00"));

    assertThat(payments.findById(RUN, paymentId).orElseThrow().ledger().operations())
        .last()
        .satisfies(
            operation -> {
              assertThat(operation.kind()).isEqualTo(OperationKind.CAPTURE);
              assertThat(operation.amount().value()).isEqualTo("480.00");
              assertThat(operation.closesAuthorization()).isTrue();
              assertThat(operation.nativeStatus()).isEqualTo(AutraStatus.CAPTURED);
              assertThat(operation.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
            });
  }

  @Test
  void
      two_captures_of_one_payment_that_sent_different_bodies_are_recorded_under_different_prints() {
    String first = aHoldOf("500.00");
    String second = aHoldOf("500.00");

    capture(first, captureBody("480.00"));
    capture(second, captureBody("120.00"));

    assertThat(payments.observedRequests())
        .extracting(AutraObservedRequest::fingerprint)
        .doesNotHaveDuplicates();
  }

  @Test
  void two_captures_that_sent_the_same_body_are_recorded_under_the_same_print() {
    String first = aHoldOf("500.00");
    String second = aHoldOf("500.00");
    String body =
        "{\"transactionalToken\":\"one-session\",\"documentId\":\""
            + DOCUMENT
            + "\",\"amount\":480.00}";
    sessions.open(new AutraSession(RUN, "one-session", ACCOUNT, DOCUMENT, NOW));

    capture(first, body);
    capture(second, body);

    assertThat(payments.observedRequests()).hasSize(2);
    assertThat(payments.observedRequests().get(0).fingerprint())
        .isEqualTo(payments.observedRequests().get(1).fingerprint());
  }

  @Test
  void a_capture_that_names_no_amount_takes_the_whole_value_the_hold_reserved() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response = capture(paymentId, captureBody(null));

    assertThat(
            AutraJsonReader.objectIn(AutraWire.bodyOf(response))
                .orElseThrow()
                .number(AutraFields.AMOUNT))
        .contains("500.00");
  }

  @Test
  void a_capture_above_the_reserved_value_is_answered_422_invalid_amount() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response = capture(paymentId, captureBody("500.01"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void a_capture_amount_that_is_not_above_zero_is_answered_400_invalid_amount() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response = capture(paymentId, captureBody("0"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void a_capture_without_documentId_is_answered_the_400_code_the_error_table_publishes_for_it() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response =
        capture(paymentId, "{\"transactionalToken\":\"t\",\"amount\":480.00}");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_capture_without_transactionalToken_is_answered_the_400_code_published_for_it() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response =
        capture(paymentId, "{\"documentId\":\"" + DOCUMENT + "\",\"amount\":480.00}");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_TRANSACTIONAL_TOKEN);
  }

  @Test
  void a_capture_carrying_a_card_or_a_token_is_refused_as_the_operations_own_table_instructs() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response =
        capture(
            paymentId,
            captureBody("480.00")
                .replace("{\"transactionalToken\"", "{\"card\":{},\"transactionalToken\""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_capture_whose_body_is_not_a_json_object_is_answered_400_invalid_json() {
    assertThat(theCodeIn(capture(aHoldOf("500.00"), "not json")))
        .isEqualTo(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_capture_naming_a_document_that_is_not_the_merchants_is_answered_merchant_not_found() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response =
        capture(paymentId, captureBody("480.00").replace(DOCUMENT, "11111111111"));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void a_capture_of_a_payment_this_run_does_not_hold_is_answered_404_payment_not_found() {
    aHoldOf("500.00");

    AutraApiResponse response = capture("no-such-payment", captureBody("480.00"));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.PAYMENT_NOT_FOUND);
  }

  @Test
  void a_refusal_over_a_payment_names_that_payment_as_the_object_it_was_refused_on() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response = capture(paymentId, captureBody("500.01"));

    assertThat(response.providerObjectId()).isEqualTo(paymentId);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_refusal_over_a_request_rather_than_a_payment_names_no_object_of_this_run() {
    AutraApiResponse response = capture(aHoldOf("500.00"), "not json");

    assertThat(response.providerObjectId()).isNull();
  }

  @Test
  void a_second_capture_is_answered_422_invalid_status_saying_it_was_already_captured() {
    String paymentId = aHoldOf("500.00");
    capture(paymentId, captureBody("480.00"));

    AutraApiResponse repeated = capture(paymentId, captureBody("10.00"));

    assertThat(repeated.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(repeated)).isEqualTo(AutraErrors.INVALID_STATUS);
    assertThat(theMessageIn(repeated)).contains("already been captured");
  }

  @Test
  void a_capture_of_a_declined_attempt_is_answered_422_invalid_status_saying_it_reserved_nothing() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.PRE_AUTHORIZATION),
            new ScenarioAction.TerminalFailure(AutraErrors.AUTHORIZER_REJECTED),
            null));
    AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", null);
    rules.clearAll(AutraApi.AUTRA);
    String paymentId = payments.all().get(0).paymentId();

    AutraApiResponse response = capture(paymentId, captureBody("480.00"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theMessageIn(response)).contains("failed and reserved nothing");
  }

  @Test
  void a_declined_capture_is_422_with_the_code_the_rule_named_and_leaves_the_hold_capturable() {
    String paymentId = aHoldOf("500.00");
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.CAPTURE),
            new ScenarioAction.TerminalFailure(AutraErrors.MSG_GENERAL_ERROR),
            null));

    AutraApiResponse response = capture(paymentId, captureBody("480.00"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MSG_GENERAL_ERROR);
    assertThat(payments.findById(RUN, paymentId).orElseThrow().isAHoldNothingHasClosed()).isTrue();
  }

  @Test
  void a_declined_capture_is_recorded_under_the_payment_as_a_request_that_changed_nothing() {
    String paymentId = aHoldOf("500.00");
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.CAPTURE),
            new ScenarioAction.TerminalFailure(AutraErrors.MSG_GENERAL_ERROR),
            null));

    capture(paymentId, captureBody("480.00"));

    assertThat(payments.observedRequests())
        .singleElement()
        .satisfies(
            observed -> {
              assertThat(observed.keyName()).isEqualTo(AutraKeyMechanisms.PAYMENT_ID);
              assertThat(observed.keyValue()).isEqualTo(paymentId);
              assertThat(observed.operation()).isEqualTo(AutraOperations.CAPTURE);
              assertThat(observed.stateChanged()).isFalse();
              assertThat(observed.httpStatus()).isEqualTo(422);
              assertThat(observed.body()).contains(AutraErrors.MSG_GENERAL_ERROR);
            });
  }

  @Test
  void a_capture_whose_ledger_claim_another_request_took_first_is_answered_422_invalid_status() {
    String paymentId = aHoldOf("500.00");
    payments.losingTheNextLedgerClaim();

    AutraApiResponse response = capture(paymentId, captureBody("480.00"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_STATUS);
  }

  @Test
  void a_capture_names_the_acquirer_transaction_once_under_each_of_the_two_published_names() {
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response = capture(paymentId, captureBody("480.00"));

    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.members().keySet())
        .containsExactly(
            AutraFields.PAYMENT_ID_UPPERCASE_D,
            AutraFields.STATUS,
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            "trackingNumber",
            "nsu",
            AutraFields.RRN,
            AutraFields.AUTHORIZATION_CODE);
    assertThat(body.text("nsu")).isEqualTo(body.text("trackingNumber"));
    assertThat(body.text("trackingNumber"))
        .contains(payments.findById(RUN, paymentId).orElseThrow().trackingNumber());
  }

  @Test
  void a_capture_whose_acquirer_named_no_transaction_publishes_neither_of_the_two_names() {
    merchants.merge(
        RUN,
        List.of(
            AutraDoubles.merchant(
                RUN,
                ACCOUNT,
                DOCUMENT,
                Map.of(AutraAcquirerReport.SETTING, AutraAcquirerReport.NOT_REPORTED.name()))));
    String paymentId = aHoldOf("500.00");

    AutraApiResponse response = capture(paymentId, captureBody("480.00"));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.has("trackingNumber")).isFalse();
    assertThat(body.has("nsu")).isFalse();
    assertThat(body.text(AutraFields.RRN)).isPresent();
  }
}
