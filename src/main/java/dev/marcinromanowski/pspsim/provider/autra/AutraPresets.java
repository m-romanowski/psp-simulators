package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class AutraPresets {

  private AutraPresets() {}

  static final int UNREACHED_ACQUIRER_STATUS = 502;

  static final String ANSWERED_WITHOUT_THE_OPERATION = "answered-without-the-operation";

  static final String CAPTURE_DECLINE_CODE = AutraErrors.AUTHORIZER_REJECTED;

  static final Duration HELD_BACK_BY = ScenarioPreset.DELIVERY_HELD_BACK_BY;

  private static final String NO_TIMEOUT_TO_REPEAT =
      "a timeout against provider autra is reachable only through a DELAYED_RESPONSE held past the"
          + " client's read timeout, and this preset name carries no wall-clock duration for one;"
          + " a preset that guessed one would be guessing the read timeout of the system under"
          + " test. A DELAYED_RESPONSE also delivers bytes the capture already produced, so the"
          + " hold is captured while the client is still waiting and the repeat this preset"
          + " promises would be answered 422 INVALID_STATUS";

  private static final String NO_CALLBACK_REPORTS_A_REGISTERED_CARD =
      "provider autra publishes no webhook for tokenization at all: \"Domínios suportados\""
          + " lists nine domains and none of them covers it, and the vendor states outright that"
          + " \"card confirmation is performed by the merchant application from the SDK"
          + " result\". A card registered against this contract therefore leaves no delivery for"
          + " a rule to hold back, and a preset that delayed one would be delaying a callback"
          + " autra never sends";

  private static final String NO_ACTION_CORRUPTS_A_SIGNATURE =
      "provider autra signs every acquiring callback \"sha256=\" + hex(HMAC-SHA256(secret,"
          + " raw_body)) and publishes that a receiver answers a mismatch 401, so a mis-signed"
          + " delivery is worth testing against it. No scenario action of this simulator corrupts"
          + " the signature of a delivery, and a preset expands into scenario rules and nothing"
          + " else, so there is no rule for this one to be written as";

  private static final String NO_LOOKUP_ON_THIS_CONTRACT =
      AutraOperations.NO_PROCESSING_OUTCOME
          + ". The read route this preset polls, GET /v1/acquiring/payments/{paymentId}, is not"
          + " served by this simulator's autra module either";

  static PresetExpansion of(PresetRequest request) {
    return switch (request.preset()) {
      case PSP_5XX_THEN_SUCCESS -> answeredWithoutTheOperationThenSucceeding(request);
      case PREAUTH_SUCCESS_CAPTURE_SUCCESS ->
          rules(
              rule(request, AutraOperations.PRE_AUTHORIZATION, succeed()),
              rule(request, AutraOperations.CAPTURE, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_DECLINE ->
          rules(
              rule(request, AutraOperations.PRE_AUTHORIZATION, succeed()),
              rule(
                  request,
                  AutraOperations.CAPTURE,
                  new ScenarioAction.TerminalFailure(CAPTURE_DECLINE_CODE)));
      case CAPTURE_ACCEPTED_RESPONSE_LOST ->
          rules(
              rule(
                  request,
                  AutraOperations.CAPTURE,
                  new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection())));
      case CANCEL_HOLD_SUCCESS, REFUND_FULL_SUCCESS, REFUND_PARTIAL_THEN_FULL ->
          rules(rule(request, AutraOperations.VOID, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS -> notExpressible(NO_TIMEOUT_TO_REPEAT);
      case CARD_ADD_SUCCESS -> rules(rule(request, AutraOperations.CARD_SESSION, succeed()));
      case CARD_ADD_FAILED ->
          rules(
              rule(
                  request,
                  AutraOperations.CARD_SESSION,
                  new ScenarioAction.TerminalFailure(CAPTURE_DECLINE_CODE)));
      case CARD_ADD_CALLBACK_DELAYED -> notExpressible(NO_CALLBACK_REPORTS_A_REGISTERED_CARD);
      case WEBHOOK_DELAYED ->
          rules(shaping(request, null, new ScenarioAction.DeliveryDelayed(HELD_BACK_BY)));
      case WEBHOOK_DUPLICATE ->
          rules(shaping(request, null, new ScenarioAction.DeliveryDuplicated()));
      case WEBHOOK_OUT_OF_ORDER ->
          rules(
              shaping(
                  request,
                  AutraPixEvent.FINANCIAL_TRANSACTION.eventType(),
                  new ScenarioAction.DeliveryDelayed(HELD_BACK_BY)));
      case WEBHOOK_INVALID_SIGNATURE -> notExpressible(NO_ACTION_CORRUPTS_A_SIGNATURE);
      case STATUS_LOOKUP_PROCESSING_THEN_SUCCESS, STATUS_LOOKUP_PROCESSING_THEN_FAILED ->
          notExpressible(NO_LOOKUP_ON_THIS_CONTRACT);
    };
  }

  private static PresetExpansion answeredWithoutTheOperationThenSucceeding(PresetRequest request) {
    List<NewScenarioRule> expanded = new ArrayList<>();
    for (String operation : AutraOperations.VOCABULARY.operationNames()) {
      expanded.add(
          step(
              request,
              operation,
              new ScenarioAction.TransportFaultInsteadOfOperation(
                  new TransportFault.ServerError(UNREACHED_ACQUIRER_STATUS)),
              ScenarioStory.INITIAL_STATE,
              ANSWERED_WITHOUT_THE_OPERATION));
      expanded.add(
          step(
              request,
              operation,
              new ScenarioAction.FixedSuccess(),
              ANSWERED_WITHOUT_THE_OPERATION,
              null));
    }
    return new PresetExpansion.Rules(List.copyOf(expanded));
  }

  private static NewScenarioRule shaping(
      PresetRequest request, @Nullable String eventType, ScenarioAction.OnTheDelivery action) {
    ScenarioMatch narrowing = request.narrowing();
    return new NewScenarioRule(
        AutraApi.AUTRA,
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
            request.preset().publishedName(), StoryKeying.TEST_RUN, whenState, thenState));
  }

  private static NewScenarioRule written(
      PresetRequest request,
      String operation,
      ScenarioAction action,
      @Nullable ScenarioStory story) {
    return new NewScenarioRule(
        AutraApi.AUTRA,
        request.testRunId(),
        request.priority(),
        request.narrowing().naming(operation),
        action,
        story);
  }
}
