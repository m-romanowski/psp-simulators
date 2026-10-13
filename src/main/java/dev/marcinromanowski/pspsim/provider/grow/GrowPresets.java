package dev.marcinromanowski.pspsim.provider.grow;

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

final class GrowPresets {

  private GrowPresets() {}

  static final String CAPTURE_DECLINE_CODE = "809";

  static final int UNREACHED_GATEWAY_STATUS = 502;

  static final String ANSWERED_WITHOUT_THE_CAPTURE = "answered-without-the-capture";

  static final String CARD_ADD_DECLINE_CODE = "809";

  private static final String NO_TIMEOUT_TO_REPEAT =
      "a timeout against provider grow is reachable only through a DELAYED_RESPONSE held past the"
          + " client's read timeout, and two things are missing. The first is a wall-clock duration"
          + " this preset name does not carry, and a preset that guessed one would guess the read"
          + " timeout of the system under test. The second is that a DELAYED_RESPONSE delivers the"
          + " bytes the settlement already rendered, so the hold is released while the client is"
          + " still waiting and the repeat this preset promises would be answered 808; the"
          + " placement that would leave the hold held is refused where such a rule is written,"
          + " because a fault answered instead of the operation renders no bytes to deliver late."
          + " The sequence is not what is missing: a preset does expand into a story, which is what"
          + " psp-5xx-then-success is expanded as";

  private static final String NO_CALLBACK_SIGNATURE =
      "no scenario action corrupts the signature of a callback, and provider grow requires no"
          + " signature on a callback in provider-compatible mode (grow.md, \"Merchant Callback"
          + " Contract\"), so the only header this preset could invalidate is a callback signature"
          + " header, and this simulator defines no such header for grow";

  private static final String ONE_CALLBACK_PER_PROCESS =
      "a grow payment process reaches one outcome and carries one callback, and this simulator"
          + " refuses an eventType naming an outcome the process did not reach, so one process has"
          + " no second callback for a held-back first one to be overtaken by";

  private static final String NOT_EXPRESSIBLE_AS_A_RULE =
      "a partial refund of provider grow is refused on the day of the charge and a second refund is"
          + " refused by the default refund allowance, so this preset needs the simulator clock"
          + " moved past the charge date and the account reconfigured; a preset expands into"
          + " scenario rules and changes neither the clock nor the account. A story of two steps"
          + " changes neither either: a step that stands aside leaves both of those refusals in"
          + " place, and a step that declines answers a refusal of its own instead of a refund";

  static PresetExpansion of(PresetRequest request) {
    return switch (request.preset()) {
      case PREAUTH_SUCCESS_CAPTURE_SUCCESS ->
          rules(
              rule(request, GrowOperations.CREATE_PAYMENT_PROCESS, succeed()),
              rule(request, GrowOperations.SETTLE_SUSPENDED_TRANSACTION, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_DECLINE ->
          rules(
              rule(request, GrowOperations.CREATE_PAYMENT_PROCESS, succeed()),
              rule(
                  request,
                  GrowOperations.SETTLE_SUSPENDED_TRANSACTION,
                  new ScenarioAction.TerminalFailure(CAPTURE_DECLINE_CODE)));
      case CAPTURE_ACCEPTED_RESPONSE_LOST ->
          rules(
              rule(
                  request,
                  GrowOperations.SETTLE_SUSPENDED_TRANSACTION,
                  new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection())));
      case CANCEL_HOLD_SUCCESS ->
          rules(
              rule(request, GrowOperations.SETTLE_SUSPENDED_TRANSACTION, succeed()),
              rule(request, GrowOperations.REFUND_TRANSACTION, succeed()));
      case REFUND_FULL_SUCCESS ->
          rules(rule(request, GrowOperations.REFUND_TRANSACTION, succeed()));
      case PSP_5XX_THEN_SUCCESS ->
          rules(
              step(
                  request,
                  GrowOperations.SETTLE_SUSPENDED_TRANSACTION,
                  new ScenarioAction.TransportFaultInsteadOfOperation(
                      new TransportFault.ServerError(UNREACHED_GATEWAY_STATUS)),
                  ScenarioStory.INITIAL_STATE,
                  ANSWERED_WITHOUT_THE_CAPTURE),
              step(
                  request,
                  GrowOperations.SETTLE_SUSPENDED_TRANSACTION,
                  succeed(),
                  ANSWERED_WITHOUT_THE_CAPTURE,
                  null));
      case CARD_ADD_SUCCESS ->
          rules(rule(request, GrowOperations.CREATE_PAYMENT_PROCESS, succeed()));
      case CARD_ADD_FAILED ->
          rules(
              rule(
                  request,
                  GrowOperations.CREATE_PAYMENT_PROCESS,
                  new ScenarioAction.TerminalFailure(CARD_ADD_DECLINE_CODE)));
      case CARD_ADD_CALLBACK_DELAYED ->
          rules(
              rule(request, GrowOperations.CREATE_PAYMENT_PROCESS, succeed()),
              heldBack(request, GrowCallbacks.EVENT_COMPLETED));
      case PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS -> notExpressible(NO_TIMEOUT_TO_REPEAT);
      case REFUND_PARTIAL_THEN_FULL -> notExpressible(NOT_EXPRESSIBLE_AS_A_RULE);
      case WEBHOOK_DELAYED -> rules(heldBack(request, null));
      case WEBHOOK_DUPLICATE ->
          rules(shaping(request, null, new ScenarioAction.DeliveryDuplicated()));
      case WEBHOOK_OUT_OF_ORDER -> notExpressible(ONE_CALLBACK_PER_PROCESS);
      case WEBHOOK_INVALID_SIGNATURE -> notExpressible(NO_CALLBACK_SIGNATURE);
      case STATUS_LOOKUP_PROCESSING_THEN_SUCCESS, STATUS_LOOKUP_PROCESSING_THEN_FAILED ->
          notExpressible(GrowOperations.NO_PROCESSING_OUTCOME);
    };
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
        GrowLightApi.GROW,
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
        GrowLightApi.GROW,
        request.testRunId(),
        request.priority(),
        request.narrowing().naming(operation),
        action,
        story);
  }
}
