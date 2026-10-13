package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class AzericardPresets {

  private AzericardPresets() {}

  static final int UNREACHED_GATEWAY_STATUS = 502;

  static final String ANSWERED_WITHOUT_THE_OPERATION = "answered-without-the-operation";

  static final String CAPTURE_DECLINE_CODE = "ZZ";

  static final String CARD_STORAGE_DECLINE_CODE = "ZZ";

  private static final String NO_TIMEOUT_TO_REPEAT =
      "a timeout against provider azericard is reachable only through a DELAYED_RESPONSE held past"
          + " the client's read timeout, and two things are missing. The first is a wall-clock"
          + " duration this preset name does not carry, and a preset that guessed one would guess"
          + " the read timeout of the system under test. The second is that a DELAYED_RESPONSE"
          + " delivers the bytes TRTYPE=21 already rendered, so the hold is already completed while"
          + " the client is still waiting and the repeat this preset promises would be answered"
          + " ACTION=3; the placement that would leave the hold uncompleted is refused where such a"
          + " rule is written, because a fault answered instead of the operation renders no bytes"
          + " to deliver late";

  private static final String ONE_CALLBACK_PER_AUTHORIZATION =
      "an azericard authorization reaches its outcome in the answer to the request that made it"
          + " and BACKREF carries that one result, so one payment has no second callback for a"
          + " held-back first one to be overtaken by";

  private static final String THE_SIGNATURE_IS_AN_ACCOUNT_SETTING =
      "no scenario action corrupts the signature of a callback, and a callback of provider"
          + " azericard is signed with the terminal's own gateway key, so the emission a"
          + " merchant's P_SIGN check has to refuse is the account setting "
          + AzericardCallbackSignature.SETTING
          + " and not a rule a preset could write";

  private static final String NO_PARTIAL_REVERSAL =
      "provider azericard publishes nothing that permits a partial reversal, and azericard.md"
          + " counts PARTIALLY_REFUNDED among the states this contract cannot produce, so a"
          + " TRTYPE=22 or TRTYPE=24 of this simulator reverses the whole reversible remainder and"
          + " there is no partial first reversal for this preset to write";

  static PresetExpansion of(PresetRequest request) {
    return switch (request.preset()) {
      case PSP_5XX_THEN_SUCCESS -> answeredWithoutTheOperationThenSucceeding(request);
      case PREAUTH_SUCCESS_CAPTURE_SUCCESS ->
          rules(
              rule(request, AzericardTransactionType.PRE_AUTHORIZATION, succeed()),
              rule(request, AzericardTransactionType.SALES_COMPLETION, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_DECLINE ->
          rules(
              rule(request, AzericardTransactionType.PRE_AUTHORIZATION, succeed()),
              rule(
                  request,
                  AzericardTransactionType.SALES_COMPLETION,
                  new ScenarioAction.TerminalFailure(CAPTURE_DECLINE_CODE)));
      case CAPTURE_ACCEPTED_RESPONSE_LOST ->
          rules(
              rule(
                  request,
                  AzericardTransactionType.SALES_COMPLETION,
                  new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection())));
      case CANCEL_HOLD_SUCCESS -> rules(onEveryReversal(request));
      case REFUND_FULL_SUCCESS -> rules(onEveryReversal(request));
      case REFUND_PARTIAL_THEN_FULL -> notExpressible(NO_PARTIAL_REVERSAL);
      case CARD_ADD_SUCCESS -> rules(onEveryCardStorage(request, succeed()));
      case CARD_ADD_FAILED ->
          rules(
              onEveryCardStorage(
                  request, new ScenarioAction.TerminalFailure(CARD_STORAGE_DECLINE_CODE)));
      case CARD_ADD_CALLBACK_DELAYED -> rules(theCardStorageWhoseCallbackIsHeldBack(request));
      case PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS -> notExpressible(NO_TIMEOUT_TO_REPEAT);
      case WEBHOOK_DELAYED -> rules(List.of(heldBack(request)));
      case WEBHOOK_DUPLICATE ->
          rules(List.of(shaping(request, new ScenarioAction.DeliveryDuplicated())));
      case WEBHOOK_OUT_OF_ORDER -> notExpressible(ONE_CALLBACK_PER_AUTHORIZATION);
      case WEBHOOK_INVALID_SIGNATURE -> notExpressible(THE_SIGNATURE_IS_AN_ACCOUNT_SETTING);
      case STATUS_LOOKUP_PROCESSING_THEN_SUCCESS, STATUS_LOOKUP_PROCESSING_THEN_FAILED ->
          notExpressible(AzericardOperations.NO_PROCESSING_OUTCOME);
    };
  }

  private static List<NewScenarioRule> theCardStorageWhoseCallbackIsHeldBack(
      PresetRequest request) {
    List<NewScenarioRule> expanded = new ArrayList<>(onEveryCardStorage(request, succeed()));
    expanded.add(heldBack(request));
    return List.copyOf(expanded);
  }

  private static NewScenarioRule heldBack(PresetRequest request) {
    return shaping(
        request, new ScenarioAction.DeliveryDelayed(ScenarioPreset.DELIVERY_HELD_BACK_BY));
  }

  private static NewScenarioRule shaping(
      PresetRequest request, ScenarioAction.OnTheDelivery action) {
    return new NewScenarioRule(
        AzericardGateway.AZERICARD,
        request.testRunId(),
        request.priority(),
        request.narrowing(),
        action,
        null);
  }

  private static List<NewScenarioRule> onEveryReversal(PresetRequest request) {
    List<NewScenarioRule> expanded = new ArrayList<>();
    for (AzericardTransactionType type : AzericardTransactionType.values()) {
      if (type.reversesThePayment()) {
        expanded.add(rule(request, type, succeed()));
      }
    }
    return List.copyOf(expanded);
  }

  private static List<NewScenarioRule> onEveryCardStorage(
      PresetRequest request, ScenarioAction action) {
    List<NewScenarioRule> expanded = new ArrayList<>();
    for (AzericardTransactionType type : AzericardTransactionType.values()) {
      if (type.createsThePayment()) {
        expanded.add(
            written(
                request,
                type.operationName() + " " + AzericardTokenFlow.CARD_STORAGE.namedInAnOperation(),
                action,
                null));
      }
    }
    return List.copyOf(expanded);
  }

  private static PresetExpansion answeredWithoutTheOperationThenSucceeding(PresetRequest request) {
    List<NewScenarioRule> expanded = new ArrayList<>();
    for (String operation : AzericardOperations.VOCABULARY.operationNames()) {
      expanded.add(
          step(
              request,
              operation,
              new ScenarioAction.TransportFaultInsteadOfOperation(
                  new TransportFault.ServerError(UNREACHED_GATEWAY_STATUS)),
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

  private static ScenarioAction succeed() {
    return new ScenarioAction.FixedSuccess();
  }

  private static PresetExpansion rules(NewScenarioRule... expanded) {
    return new PresetExpansion.Rules(List.of(expanded));
  }

  private static PresetExpansion rules(List<NewScenarioRule> expanded) {
    return new PresetExpansion.Rules(expanded);
  }

  private static PresetExpansion notExpressible(String reason) {
    return new PresetExpansion.NotExpressible(reason);
  }

  private static NewScenarioRule rule(
      PresetRequest request, AzericardTransactionType type, ScenarioAction action) {
    return written(request, type.operationName(), action, null);
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
        AzericardGateway.AZERICARD,
        request.testRunId(),
        request.priority(),
        request.narrowing().naming(operation),
        action,
        story);
  }
}
