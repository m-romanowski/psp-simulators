package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class TranzilaPresets {

  static final int UNREACHED_ACQUIRER_STATUS = 502;

  static final String ANSWERED_WITHOUT_THE_OPERATION = "answered-without-the-operation";

  static final String A_REFUSAL = "004";

  private static final String NO_TIMEOUT_TO_REPEAT =
      "a timeout against provider tranzila is reachable only through a DELAYED_RESPONSE held past"
          + " the client's read timeout, and this preset name carries no wall-clock duration for"
          + " one; a preset that guessed one would be guessing the read timeout of the system"
          + " under test. A DELAYED_RESPONSE also delivers bytes the force already produced, so"
          + " the J5 is forced while the client is still waiting and the repeat this preset"
          + " promises would be answered 20112, one force per J5";

  private static final String NO_DELIVERY_ON_THIS_ROUTE =
      "provider tranzila publishes one notification, the payment page's notify, and the card API"
          + " this module serves sends none: every transaction is answered synchronously, so there"
          + " is no delivery for a rule to hold back, duplicate, reorder or sign wrongly";

  private static final String NO_LOOKUP_THAT_REPORTS_A_PROCESSING_STATE =
      TranzilaOperations.NO_PROCESSING_STATE
          + ", so there is no processing state for a lookup to report first";

  private TranzilaPresets() {}

  static PresetExpansion of(PresetRequest request) {
    return switch (request.preset()) {
      case PSP_5XX_THEN_SUCCESS -> answeredWithoutTheOperationThenSucceeding(request);
      case PREAUTH_SUCCESS_CAPTURE_SUCCESS ->
          rules(
              rule(request, TranzilaOperations.VERIFY, succeed()),
              rule(request, TranzilaOperations.FORCE, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_DECLINE ->
          rules(
              rule(request, TranzilaOperations.VERIFY, succeed()),
              rule(
                  request,
                  TranzilaOperations.FORCE,
                  new ScenarioAction.TerminalFailure(A_REFUSAL)));
      case CAPTURE_ACCEPTED_RESPONSE_LOST ->
          rules(
              rule(
                  request,
                  TranzilaOperations.FORCE,
                  new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection())));
      case CANCEL_HOLD_SUCCESS -> rules(rule(request, TranzilaOperations.REVERSAL, succeed()));
      case REFUND_FULL_SUCCESS, REFUND_PARTIAL_THEN_FULL ->
          rules(rule(request, TranzilaOperations.CREDIT, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS -> notExpressible(NO_TIMEOUT_TO_REPEAT);
      case CARD_ADD_SUCCESS -> rules(rule(request, TranzilaOperations.VERIFY, succeed()));
      case CARD_ADD_FAILED ->
          rules(
              rule(
                  request,
                  TranzilaOperations.VERIFY,
                  new ScenarioAction.TerminalFailure(A_REFUSAL)));
      case CARD_ADD_CALLBACK_DELAYED,
          WEBHOOK_DELAYED,
          WEBHOOK_DUPLICATE,
          WEBHOOK_OUT_OF_ORDER,
          WEBHOOK_INVALID_SIGNATURE ->
          notExpressible(NO_DELIVERY_ON_THIS_ROUTE);
      case STATUS_LOOKUP_PROCESSING_THEN_SUCCESS, STATUS_LOOKUP_PROCESSING_THEN_FAILED ->
          notExpressible(NO_LOOKUP_THAT_REPORTS_A_PROCESSING_STATE);
    };
  }

  private static PresetExpansion answeredWithoutTheOperationThenSucceeding(PresetRequest request) {
    List<NewScenarioRule> expanded = new ArrayList<>();
    for (String operation : TranzilaOperations.VOCABULARY.operationNames()) {
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
        TranzilaApi.TRANZILA,
        request.testRunId(),
        request.priority(),
        request.narrowing().naming(operation),
        action,
        story);
  }
}
