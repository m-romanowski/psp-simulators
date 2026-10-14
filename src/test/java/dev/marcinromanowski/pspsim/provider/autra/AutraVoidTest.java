package dev.marcinromanowski.pspsim.provider.autra;

import static dev.marcinromanowski.pspsim.provider.autra.AutraPreAuthorizationTest.theCodeIn;
import static dev.marcinromanowski.pspsim.provider.autra.AutraPreAuthorizationTest.theMessageIn;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraVoidTest {

  private static final TestRunId RUN = TestRunId.of("autra-void");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);

  private AutraApi apiFor(Map<String, String> configuration) {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, configuration));
    return AutraWiring.api(merchants, sessions, payments, audit, RUN, NOW, scenarios);
  }

  private String aHoldOf(AutraApi api, String amount) {
    return AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null)
        .providerObjectId();
  }

  private String aCapturedPaymentOf(AutraApi api, String amount) {
    String paymentId = aHoldOf(api, amount);
    api.capture(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/capture",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            "{\"transactionalToken\":\""
                + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"amount\":"
                + amount
                + "}"),
        paymentId);
    return paymentId;
  }

  private AutraApiResponse reverse(AutraApi api, String paymentId, String body) {
    return api.voidPayment(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/void",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            body),
        paymentId);
  }

  private String voidBody(String amount) {
    return "{\"transactionalToken\":\""
        + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"amount\":"
        + amount
        + ",\"currency\":\"BRL\"}";
  }

  @Test
  void releasing_a_hold_is_answered_the_published_shape_with_the_identifier_spelled_paymentId() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.00"));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.text(AutraFields.PAYMENT_ID_LOWERCASE_D)).contains(paymentId);
    assertThat(body.text(AutraFields.PAYMENT_ID_UPPERCASE_D)).isEmpty();
    assertThat(body.text(AutraFields.STATUS)).contains(AutraStatus.VOIDED);
    assertThat(body.number(AutraFields.AMOUNT)).contains("500.00");
    assertThat(body.text(AutraFields.CURRENCY)).contains("BRL");
    assertThat(body.text(AutraFields.RRN))
        .contains(payments.findById(RUN, paymentId).orElseThrow().rrn());
    assertThat(body.text("trackingNumber"))
        .contains(payments.findById(RUN, paymentId).orElseThrow().trackingNumber());
  }

  @Test
  void a_released_hold_is_cancelled_and_holds_nothing_capturable() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    reverse(api, paymentId, voidBody("500.00"));

    AutraPayment stored = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(stored.nativeStatus()).isEqualTo(AutraStatus.VOIDED);
    assertThat(stored.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(stored.capturableRemainder()).isEqualByComparingTo("0");
  }

  @Test
  void a_reversal_below_the_reserved_value_of_an_uncaptured_hold_is_answered_422_invalid_amount() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, voidBody("100.00"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_AMOUNT);
    assertThat(theMessageIn(response)).contains("released whole");
  }

  @Test
  void a_reversal_above_the_remaining_balance_is_answered_the_published_eligibility_code() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.01"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response))
        .isEqualTo(AutraErrors.AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE);
  }

  @Test
  void reversing_part_of_a_captured_payment_is_answered_partially_voided() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, voidBody("200.00"));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(
            AutraJsonReader.objectIn(AutraWire.bodyOf(response))
                .orElseThrow()
                .text(AutraFields.STATUS))
        .contains(AutraStatus.PARTIALLY_VOIDED);
    AutraPayment stored = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(stored.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
    assertThat(stored.reversibleRemainder()).isEqualByComparingTo("300.00");
  }

  @Test
  void
      reversing_the_remainder_after_a_partial_reversal_is_answered_voided_and_refunds_the_payment() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");
    reverse(api, paymentId, voidBody("200.00"));

    AutraApiResponse response = reverse(api, paymentId, voidBody("300.00"));

    assertThat(
            AutraJsonReader.objectIn(AutraWire.bodyOf(response))
                .orElseThrow()
                .text(AutraFields.STATUS))
        .contains(AutraStatus.VOIDED);
    AutraPayment stored = payments.findById(RUN, paymentId).orElseThrow();
    assertThat(stored.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(stored.reversalsAccepted()).isFalse();
  }

  @Test
  void a_repeated_full_reversal_is_refused_with_the_code_this_simulator_chose_for_it() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");
    reverse(api, paymentId, voidBody("500.00"));

    AutraApiResponse repeated = reverse(api, paymentId, voidBody("500.00"));

    assertThat(repeated.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(repeated)).isEqualTo(AutraErrors.INVALID_STATUS);
    assertThat(theMessageIn(repeated)).contains("no remaining balance");
  }

  @Test
  void a_second_release_of_a_hold_already_released_is_refused_saying_it_was_already_cancelled() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");
    reverse(api, paymentId, voidBody("500.00"));

    AutraApiResponse repeated = reverse(api, paymentId, voidBody("500.00"));

    assertThat(repeated.httpStatus()).isEqualTo(422);
    assertThat(theMessageIn(repeated)).contains("already been cancelled");
  }

  @Test
  void a_reversal_of_a_declined_attempt_is_refused_saying_it_reserved_nothing() {
    AutraApi api = apiFor(Map.of());
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

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.00"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theMessageIn(response)).contains("reserved nothing");
  }

  @Test
  void
      the_guides_bodyless_call_releases_the_whole_hold_when_the_account_asks_for_that_publication() {
    AutraApi api =
        apiFor(
            Map.of(
                AutraRequestShape.VOID_SETTING, AutraRequestShape.PUBLISHED_GUIDE_EXAMPLE.name()));
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, "");

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(
            AutraJsonReader.objectIn(AutraWire.bodyOf(response))
                .orElseThrow()
                .number(AutraFields.AMOUNT))
        .contains("500.00");
  }

  @Test
  void the_guides_bodyless_call_is_refused_under_the_schema_this_simulator_defaults_to() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, "");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_void_body_that_is_not_a_json_object_is_refused_even_under_the_guides_shape() {
    AutraApi api =
        apiFor(
            Map.of(
                AutraRequestShape.VOID_SETTING, AutraRequestShape.PUBLISHED_GUIDE_EXAMPLE.name()));
    String paymentId = aHoldOf(api, "500.00");

    assertThat(theCodeIn(reverse(api, paymentId, "not json"))).isEqualTo(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_void_without_documentId_or_transactionalToken_is_answered_the_published_400_codes() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    assertThat(theCodeIn(reverse(api, paymentId, "{\"transactionalToken\":\"t\",\"amount\":1}")))
        .isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
    assertThat(
            theCodeIn(
                reverse(api, paymentId, "{\"documentId\":\"" + DOCUMENT + "\",\"amount\":1}")))
        .isEqualTo(AutraErrors.MISSING_TRANSACTIONAL_TOKEN);
  }

  @Test
  void a_void_without_an_amount_is_refused_under_the_schema_that_requires_one() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    assertThat(
            theCodeIn(
                reverse(
                    api,
                    paymentId,
                    "{\"transactionalToken\":\"t\",\"documentId\":\"" + DOCUMENT + "\"}")))
        .isEqualTo(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void a_void_amount_that_is_not_above_zero_is_answered_400_invalid_amount() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, voidBody("0"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void a_void_naming_a_document_that_is_not_the_merchants_is_answered_merchant_not_found() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response =
        reverse(api, paymentId, voidBody("500.00").replace(DOCUMENT, "11111111111"));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void a_void_of_a_payment_this_run_does_not_hold_is_answered_404_payment_not_found() {
    AutraApi api = apiFor(Map.of());
    aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, "no-such-payment", voidBody("500.00"));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.PAYMENT_NOT_FOUND);
  }

  @Test
  void a_declined_void_is_422_with_the_eligibility_code_the_rule_named_and_moves_no_money() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.VOID),
            new ScenarioAction.TerminalFailure(AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY),
            null));

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.00"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY);
    assertThat(payments.findById(RUN, paymentId).orElseThrow().reversibleRemainder())
        .isEqualByComparingTo("500.00");
  }

  @Test
  void a_fault_answered_instead_of_a_void_leaves_the_payment_with_everything_still_reversible() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.VOID),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.00"));

    assertThat(response.httpStatus()).isEqualTo(502);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(payments.findById(RUN, paymentId).orElseThrow().reversibleRemainder())
        .isEqualByComparingTo("500.00");
  }

  @Test
  void a_full_reversal_writes_a_refund_operation_that_carries_the_status_the_void_answered() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");

    reverse(api, paymentId, voidBody("500.00"));

    assertThat(payments.findById(RUN, paymentId).orElseThrow().ledger().operations())
        .last()
        .satisfies(
            operation -> {
              assertThat(operation.kind()).isEqualTo(OperationKind.REFUND);
              assertThat(operation.amount().value()).isEqualTo("500.00");
              assertThat(operation.nativeStatus()).isEqualTo(AutraStatus.VOIDED);
              assertThat(operation.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
              assertThat(operation.closesAuthorization()).isNull();
              assertThat(operation.at()).isEqualTo(NOW);
            });
  }

  @Test
  void a_partial_reversal_writes_a_refund_operation_that_carries_the_partial_status_and_amount() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");

    reverse(api, paymentId, voidBody("200.00"));

    assertThat(payments.findById(RUN, paymentId).orElseThrow().ledger().operations())
        .last()
        .satisfies(
            operation -> {
              assertThat(operation.amount().value()).isEqualTo("200.00");
              assertThat(operation.nativeStatus()).isEqualTo(AutraStatus.PARTIALLY_VOIDED);
              assertThat(operation.normalizedStatus())
                  .isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
            });
  }

  @Test
  void
      the_first_call_under_a_payment_is_recorded_as_the_first_and_the_next_as_a_correlated_retry() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");

    reverse(api, paymentId, voidBody("500.00"));

    assertThat(payments.observedRequests())
        .extracting(RecordedKeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.CORRELATED_RETRY);
    assertThat(payments.observedRequests())
        .extracting(RecordedKeyedRequest::stateChanged)
        .containsExactly(true, true);
  }

  @Test
  void a_refused_void_is_recorded_as_a_request_that_changed_nothing_about_the_payment() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.VOID),
            new ScenarioAction.TerminalFailure(AutraErrors.ORIGINAL_CYCLE_NOT_ACCEPTED),
            null));

    reverse(api, paymentId, voidBody("500.00"));

    assertThat(payments.observedRequests())
        .last()
        .satisfies(
            observed -> {
              assertThat(observed.operation()).isEqualTo(AutraOperations.VOID);
              assertThat(observed.stateChanged()).isFalse();
              assertThat(observed.httpStatus()).isEqualTo(422);
              assertThat(observed.contentType()).isEqualTo(AutraFields.JSON);
              assertThat(observed.body()).contains(AutraErrors.ORIGINAL_CYCLE_NOT_ACCEPTED);
            });
  }

  @Test
  void a_release_whose_claim_another_request_took_first_is_answered_422_invalid_status() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");
    payments.losingTheNextLedgerClaim();

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.00"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_STATUS);
  }

  @Test
  void a_capture_and_a_void_of_one_payment_are_recorded_under_the_key_that_names_that_payment() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aCapturedPaymentOf(api, "500.00");

    reverse(api, paymentId, voidBody("500.00"));

    assertThat(payments.observedRequests())
        .allSatisfy(
            observed -> {
              assertThat(observed.keyName()).isEqualTo(AutraKeyMechanisms.PAYMENT_ID);
              assertThat(observed.keyValue()).isEqualTo(paymentId);
            })
        .extracting(RecordedKeyedRequest::operation)
        .containsExactly(AutraOperations.CAPTURE, AutraOperations.VOID);
  }

  @Test
  void releasing_a_hold_publishes_the_primary_name_and_never_the_compatibility_one() {
    AutraApi api = apiFor(Map.of());
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.00"));

    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.members().keySet())
        .containsExactly(
            AutraFields.PAYMENT_ID_LOWERCASE_D,
            AutraFields.STATUS,
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            AutraFields.RRN,
            "trackingNumber");
    assertThat(body.has("nsu")).isFalse();
  }

  @Test
  void releasing_a_hold_whose_acquirer_named_no_transaction_publishes_no_tracking_number() {
    AutraApi api =
        apiFor(Map.of(AutraAcquirerReport.SETTING, AutraAcquirerReport.NOT_REPORTED.name()));
    String paymentId = aHoldOf(api, "500.00");

    AutraApiResponse response = reverse(api, paymentId, voidBody("500.00"));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.has("trackingNumber")).isFalse();
    assertThat(body.has("nsu")).isFalse();
    assertThat(body.text(AutraFields.RRN)).isPresent();
  }
}
