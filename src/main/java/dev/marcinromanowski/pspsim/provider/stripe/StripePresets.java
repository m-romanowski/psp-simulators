package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class StripePresets {

  private StripePresets() {}

  static final int UNREACHED_GATEWAY_STATUS = 502;

  static final String ANSWERED_WITHOUT_THE_CAPTURE = "answered-without-the-capture";

  static final String REFUNDED_IN_PART = "refunded-in-part";

  static final StripeDecline CAPTURE_DECLINE = StripeDecline.GENERIC_DECLINE;

  static final StripeDecline CARD_SETUP_DECLINE = StripeDecline.GENERIC_DECLINE;

  static final StripeDecline ASYNCHRONOUS_PAYMENT_DECLINE = StripeDecline.GENERIC_DECLINE;

  private static final String NO_CARD_STORAGE_TO_CALL_BACK_ABOUT =
      "provider stripe reports a stored card with setup_intent.succeeded and"
          + " setup_intent.setup_failed, and this build emits neither, so it delivers no callback"
          + " about a card it set up for this preset to delay";

  private static final String THE_SIGNATURE_IS_AN_ACCOUNT_SETTING =
      "no scenario action corrupts the signature of a delivery, and a stripe event is signed with"
          + " the account's own webhook secret, so the emission a merchant's signature check has"
          + " to refuse is the account setting "
          + StripeWebhookSigning.EMISSION_SETTING
          + " and not a rule a preset could write";

  private static final String NO_TIMEOUT_TO_REPEAT =
      "a timeout against provider stripe is reachable only through a DELAYED_RESPONSE held past the"
          + " client's read timeout, and this preset name carries no wall-clock duration; a preset"
          + " that guessed one would guess the read timeout of the system under test. The repeat is"
          + " not what is missing: a repeat carrying the Idempotency-Key of the timed-out capture"
          + " replays the answer the first attempt rendered, which is what this preset exists to"
          + " exercise";

  static PresetExpansion of(PresetRequest request) {
    return switch (request.preset()) {
      case PREAUTH_SUCCESS_CAPTURE_SUCCESS ->
          rules(
              rule(request, StripeOperations.CREATE, succeed()),
              rule(request, StripeOperations.CONFIRM, succeed()),
              rule(request, StripeOperations.CAPTURE, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_DECLINE ->
          rules(
              rule(request, StripeOperations.CREATE, succeed()),
              rule(request, StripeOperations.CONFIRM, succeed()),
              rule(
                  request,
                  StripeOperations.CAPTURE,
                  new ScenarioAction.TerminalFailure(CAPTURE_DECLINE.scenarioCode())));
      case CAPTURE_ACCEPTED_RESPONSE_LOST ->
          rules(
              rule(
                  request,
                  StripeOperations.CAPTURE,
                  new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection())));
      case CANCEL_HOLD_SUCCESS -> rules(rule(request, StripeOperations.CANCEL, succeed()));
      case PSP_5XX_THEN_SUCCESS ->
          rules(
              step(
                  request,
                  StripeOperations.CAPTURE,
                  new ScenarioAction.TransportFaultInsteadOfOperation(
                      new TransportFault.ServerError(UNREACHED_GATEWAY_STATUS)),
                  ScenarioStory.INITIAL_STATE,
                  ANSWERED_WITHOUT_THE_CAPTURE),
              step(
                  request,
                  StripeOperations.CAPTURE,
                  succeed(),
                  ANSWERED_WITHOUT_THE_CAPTURE,
                  null));
      case CARD_ADD_SUCCESS ->
          rules(rule(request, StripeOperations.CONFIRM_SETUP_INTENT, succeed()));
      case CARD_ADD_FAILED ->
          rules(
              rule(
                  request,
                  StripeOperations.CONFIRM_SETUP_INTENT,
                  new ScenarioAction.TerminalFailure(CARD_SETUP_DECLINE.scenarioCode())));
      case CARD_ADD_CALLBACK_DELAYED -> notExpressible(NO_CARD_STORAGE_TO_CALL_BACK_ABOUT);
      case PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS -> notExpressible(NO_TIMEOUT_TO_REPEAT);
      case REFUND_FULL_SUCCESS -> rules(rule(request, StripeOperations.REFUND, succeed()));
      case REFUND_PARTIAL_THEN_FULL ->
          rules(
              step(
                  request,
                  StripeOperations.REFUND,
                  succeed(),
                  ScenarioStory.INITIAL_STATE,
                  REFUNDED_IN_PART),
              step(request, StripeOperations.REFUND, succeed(), REFUNDED_IN_PART, null));
      case WEBHOOK_DELAYED -> rules(heldBack(request, null));
      case WEBHOOK_DUPLICATE ->
          rules(shaping(request, null, new ScenarioAction.DeliveryDuplicated()));
      case WEBHOOK_INVALID_SIGNATURE -> notExpressible(THE_SIGNATURE_IS_AN_ACCOUNT_SETTING);
      case WEBHOOK_OUT_OF_ORDER ->
          rules(heldBack(request, StripeEventType.AMOUNT_CAPTURABLE_UPDATED.wireValue()));
      case STATUS_LOOKUP_PROCESSING_THEN_SUCCESS -> inFlightUntil(request, null);
      case STATUS_LOOKUP_PROCESSING_THEN_FAILED ->
          inFlightUntil(request, ASYNCHRONOUS_PAYMENT_DECLINE);
    };
  }

  private static PresetExpansion inFlightUntil(
      PresetRequest request, @Nullable StripeDecline comingToRestWith) {
    ScenarioAction settling =
        new ScenarioAction.ProcessingThenTerminal(
            ScenarioPreset.THE_OUTCOME_SETTLES_AFTER,
            comingToRestWith == null ? null : comingToRestWith.scenarioCode());
    return rules(
        rule(request, StripeOperations.CREATE, settling),
        rule(request, StripeOperations.CONFIRM, settling));
  }

  private static NewScenarioRule heldBack(PresetRequest request, @Nullable String eventType) {
    return shaping(
        request,
        eventType,
        new ScenarioAction.DeliveryDelayed(ScenarioPreset.DELIVERY_HELD_BACK_BY));
  }

  private static NewScenarioRule shaping(
      PresetRequest request, @Nullable String eventType, ScenarioAction.OnTheDelivery action) {
    ScenarioMatch narrowing = request.narrowing();
    return new NewScenarioRule(
        StripeApi.STRIPE,
        request.testRunId(),
        request.priority(),
        eventType == null ? narrowing : narrowing.reporting(eventType),
        action,
        null);
  }

  private static ScenarioAction succeed() {
    return new ScenarioAction.FixedSuccess();
  }

  private static PresetExpansion rules(NewScenarioRule... expanded) {
    return new PresetExpansion.Rules(List.of(expanded));
  }

  private static PresetExpansion notExpressible(String reason) {
    return new PresetExpansion.NotExpressible(reason);
  }

  private static NewScenarioRule rule(
      PresetRequest request, String operation, ScenarioAction action) {
    return written(request, operation, action, null);
  }

  private static NewScenarioRule step(
      PresetRequest request,
      String operation,
      ScenarioAction action,
      String whenState,
      @Nullable String thenState) {
    return written(
        request,
        operation,
        action,
        new ScenarioStory(
            request.preset().publishedName(), StoryKeying.FLOW, whenState, thenState));
  }

  private static NewScenarioRule written(
      PresetRequest request,
      String operation,
      ScenarioAction action,
      @Nullable ScenarioStory story) {
    return new NewScenarioRule(
        StripeApi.STRIPE,
        request.testRunId(),
        request.priority(),
        request.narrowing().naming(operation),
        action,
        story);
  }
}
