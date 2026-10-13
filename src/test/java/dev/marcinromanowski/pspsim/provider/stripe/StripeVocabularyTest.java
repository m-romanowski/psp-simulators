package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StripeVocabularyTest {

  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  @ParameterizedTest
  @CsvSource({
    "requires_payment_method,NOT_INITIALIZED,true,true",
    "requires_confirmation,NOT_INITIALIZED,true,true",
    "requires_action,NOT_INITIALIZED,true,true",
    "processing,PROCESSING,false,false",
    "requires_capture,READY_FOR_CAPTURE,false,true",
    "succeeded,SUCCEEDED,false,false",
    "canceled,CANCELLED,false,false"
  })
  void maps_each_published_payment_intent_status_as_the_contract_does(
      String wireValue, String normalized, boolean confirmable, boolean cancellable) {
    StripeIntentStatus status = StripeIntentStatus.of(wireValue).orElseThrow();

    assertThat(status.normalized()).isEqualTo(NormalizedStatus.valueOf(normalized));
    assertThat(status.confirmable()).isEqualTo(confirmable);
    assertThat(status.cancellable()).isEqualTo(cancellable);
  }

  @Test
  void reads_no_status_the_vendor_does_not_publish() {
    assertThat(StripeIntentStatus.of("requires_source")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"automatic,false", "automatic_async,false", "manual,true"})
  void holds_the_funds_only_for_the_published_manual_capture_method(
      String wireValue, boolean holdsTheFunds) {
    StripeCaptureMethod method = StripeCaptureMethod.of(wireValue).orElseThrow();

    assertThat(method.holdsTheFundsForALaterCapture()).isEqualTo(holdsTheFunds);
    assertThat(method.afterASuccessfulConfirmation())
        .isEqualTo(
            holdsTheFunds ? StripeIntentStatus.REQUIRES_CAPTURE : StripeIntentStatus.SUCCEEDED);
  }

  @Test
  void defaults_a_capture_method_to_the_one_the_vendor_publishes_as_its_default() {
    assertThat(StripeCaptureMethod.PUBLISHED_DEFAULT)
        .isEqualTo(StripeCaptureMethod.AUTOMATIC_ASYNC);
  }

  @ParameterizedTest
  @CsvSource({
    "generic_decline,card_declined,generic_decline",
    "insufficient_funds,card_declined,insufficient_funds",
    "authentication_required,authentication_required,authentication_not_handled"
  })
  void pairs_a_decline_with_the_decline_code_the_published_test_cards_produce(
      String scenarioCode, String errorCode, String declineCode) {
    StripeDecline decline = StripeDecline.of(scenarioCode).orElseThrow();

    assertThat(decline.errorCode()).isEqualTo(errorCode);
    assertThat(decline.declineCode()).isEqualTo(declineCode);
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired_card", "incorrect_cvc", "processing_error"})
  void answers_a_failure_the_issuer_did_not_decline_without_a_decline_code(String scenarioCode) {
    StripeDecline decline = StripeDecline.of(scenarioCode).orElseThrow();

    assertThat(decline.errorCode()).isEqualTo(scenarioCode);
    assertThat(decline.declineCode()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "generic_decline",
        "insufficient_funds",
        "expired_card",
        "incorrect_cvc",
        "processing_error",
        "authentication_required"
      })
  void admits_every_decline_a_published_test_card_produces(String scenarioCode) {
    assertThat(refusalsOf(declining(scenarioCode))).isEmpty();
  }

  @Test
  void refuses_a_bare_card_declined_because_the_vendor_always_says_why_a_card_was_declined() {
    assertThat(refusalsOf(declining("card_declined")))
        .singleElement()
        .satisfies(refusal -> assertThat(refusal.property()).isEqualTo("action.providerCode"));
  }

  @Test
  void refuses_a_rule_that_narrows_by_an_order_id_this_provider_has_nowhere_to_read() {
    ScenarioMatch match =
        new ScenarioMatch(
            null, "order-1", null, null, null, null, null, StripeOperations.CREATE, null, Map.of());

    assertThat(refusalsOf(succeeding(match)))
        .anySatisfy(refusal -> assertThat(refusal.property()).isEqualTo("match.orderId"));
  }

  @Test
  void refuses_a_rule_that_narrows_a_cancellation_by_an_amount_the_request_does_not_carry() {
    ScenarioMatch match =
        new ScenarioMatch(
            null,
            null,
            "pi_sim_1",
            null,
            null,
            Amount.of("1099"),
            null,
            StripeOperations.CANCEL,
            null,
            Map.of());

    assertThat(refusalsOf(succeeding(match)))
        .anySatisfy(refusal -> assertThat(refusal.property()).isEqualTo("match.amount"));
  }

  @Test
  void refuses_a_rule_that_names_an_operation_this_contract_does_not_expose() {
    ScenarioMatch match = ScenarioMatch.anyRequest().naming("POST /v1/setup_intents");

    assertThat(refusalsOf(succeeding(match)))
        .anySatisfy(refusal -> assertThat(refusal.property()).isEqualTo("match.operation"));
  }

  @Test
  void names_no_decline_for_a_code_this_provider_never_answers() {
    assertThat(StripeDecline.of("card_declined")).isEmpty();
    assertThat(StripeCancellationReason.of("withdrawn")).isEmpty();
    assertThat(StripeCancellationReason.theReasonsACallerMaySend())
        .isEqualTo("duplicate, fraudulent, requested_by_customer, abandoned");
    assertThat(StripeCaptureMethod.theMethodsTheVendorPublishes())
        .isEqualTo("automatic, automatic_async, manual");
    assertThat(StripeRefundReason.of("expired_uncaptured_charge"))
        .contains(StripeRefundReason.EXPIRED_UNCAPTURED_CHARGE);
    assertThat(StripeRefundReason.sentByACaller("expired_uncaptured_charge")).isEmpty();
    assertThat(StripeRefundReason.theReasonsACallerMaySend())
        .isEqualTo("duplicate, fraudulent, requested_by_customer");
  }

  @Test
  void names_the_events_of_a_payment_intent_apart_from_the_events_of_a_refund() {
    assertThat(StripeEventType.thePublishedNamesOf(StripeEventSubject.REFUND))
        .isEqualTo("refund.created");
    assertThat(StripeEventType.thePublishedNamesOf(StripeEventSubject.PAYMENT_INTENT))
        .isEqualTo(
            "payment_intent.amount_capturable_updated, payment_intent.succeeded,"
                + " payment_intent.payment_failed, payment_intent.canceled");
    assertThat(StripeEventType.thePublishedNames())
        .isEqualTo(
            "payment_intent.amount_capturable_updated, payment_intent.succeeded,"
                + " payment_intent.payment_failed, payment_intent.canceled, refund.created");
  }

  @Test
  void spells_the_decline_codes_a_scenario_may_name_as_the_shape_the_core_refuses_against() {
    assertThat(StripeDecline.theCodesAScenarioMayName())
        .isEqualTo(
            "generic_decline|insufficient_funds|expired_card|incorrect_cvc|processing_error"
                + "|authentication_required");
    assertThat(StripeOperations.VOCABULARY.failureCodes().shape())
        .isEqualTo(StripeDecline.theCodesAScenarioMayName());
  }

  @Test
  void carries_the_message_the_vendor_publishes_for_the_decline_a_scenario_named() {
    assertThat(StripeDecline.INSUFFICIENT_FUNDS.publishedMessage())
        .isEqualTo("The card has insufficient funds to complete the purchase.");
    assertThat(StripeDecline.INCORRECT_CVC.publishedMessage()).isNull();
  }

  @Test
  void exposes_the_calls_that_change_a_payment_intent_or_set_a_card_up_and_no_retrieval() {
    assertThat(StripeOperations.VOCABULARY.operationNames())
        .containsExactly(
            StripeOperations.CREATE,
            StripeOperations.CONFIRM,
            StripeOperations.CAPTURE,
            StripeOperations.CANCEL,
            StripeOperations.REFUND,
            StripeOperations.CONFIRM_SETUP_INTENT);
  }

  @Test
  void the_setup_confirmation_is_the_one_call_of_the_setup_family_a_scenario_may_decide() {
    assertThat(StripeOperations.VOCABULARY.operationNames())
        .contains(StripeOperations.CONFIRM_SETUP_INTENT)
        .doesNotContain(StripeOperations.CREATE_SETUP_INTENT);
  }

  private static List<ScenarioRefusal> refusalsOf(NewScenarioRule rule) {
    return StripeOperations.VOCABULARY.refusals(rule);
  }

  private static NewScenarioRule declining(String providerCode) {
    return new NewScenarioRule(
        StripeApi.STRIPE,
        E2E_RUN,
        0,
        ScenarioMatch.anyRequest().naming(StripeOperations.CONFIRM),
        new ScenarioAction.TerminalFailure(providerCode),
        null);
  }

  private static NewScenarioRule succeeding(ScenarioMatch match) {
    return new NewScenarioRule(
        StripeApi.STRIPE, E2E_RUN, 0, match, new ScenarioAction.FixedSuccess(), null);
  }

  @Test
  void names_the_two_calls_a_rule_may_ask_provider_stripe_to_answer_with_a_processing_state() {
    assertThat(StripeOperations.VOCABULARY.theOperationsThatReportAProcessingState())
        .containsExactly(StripeOperations.CREATE, StripeOperations.CONFIRM);
  }

  @ParameterizedTest(name = "a pending outcome on {0} is refused by provider stripe")
  @ValueSource(
      strings = {
        "POST /v1/payment_intents/{id}/capture",
        "POST /v1/payment_intents/{id}/cancel",
        "POST /v1/refunds",
        "POST /v1/setup_intents/{id}/confirm"
      })
  void refuses_a_pending_outcome_on_a_call_provider_stripe_never_answers_with_one(String call) {
    assertThat(
            StripeOperations.VOCABULARY.refusals(
                new NewScenarioRule(
                    StripeApi.STRIPE,
                    E2E_RUN,
                    0,
                    ScenarioMatch.anyRequest().naming(call),
                    new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
                    null)))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("match.operation");
              assertThat(refusal.reason())
                  .contains(StripeOperations.CREATE, StripeOperations.CONFIRM);
            });
  }

  @Test
  void a_decision_no_endpoint_of_this_provider_can_answer_is_refused_rather_than_answered() {
    ScenarioDecision.Pend pending = new ScenarioDecision.Pend("scn_1", Duration.ofSeconds(5), null);

    Assertions.assertThatThrownBy(
            () ->
                StripeOperationResult.decidedBy(
                    pending,
                    () -> StripeOperationResult.answered(StripeJson.object()),
                    decline -> StripeOperationResult.answered(StripeJson.object())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("answers with none");
  }
}
