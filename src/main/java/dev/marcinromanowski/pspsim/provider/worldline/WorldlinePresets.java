package dev.marcinromanowski.pspsim.provider.worldline;

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

final class WorldlinePresets {

  static final int UNREACHED_ACQUIRER_STATUS = 502;

  static final String ANSWERED_WITHOUT_THE_OPERATION = "answered-without-the-operation";

  private static final String NO_REFUND_ON_THIS_SLICE =
      WorldlineObjectActions.THE_REFUND_ROUTE_IS_OUT_OF_THIS_SLICE
          + ", so there is no operation here for a refund preset to write a rule against";

  private static final String NO_TIMEOUT_TO_REPEAT =
      "a timeout against provider worldline is reachable only through a DELAYED_RESPONSE held"
          + " past the client's read timeout, and this preset name carries no wall-clock duration"
          + " for one; a preset that guessed one would be guessing the read timeout of the system"
          + " under test. A DELAYED_RESPONSE also delivers bytes the capture already produced, so"
          + " the authorization is captured while the client is still waiting, and the repeat"
          + " this preset promises would be answered 400 ACTION_NOT_ALLOWED_ON_TRANSACTION";

  private static final String NO_WEBHOOK_ON_THIS_SLICE =
      "provider worldline publishes thirteen payment webhook events, five delivery attempts over"
          + " 24 hours and a retry-count header, and this slice serves none of it: the operator"
          + " scoped this module to hosted tokenization, a pre-authorization with its capture and"
          + " its cancel, and an off-session charge, and put webhooks outside it. There is no"
          + " delivery here for a rule to hold back, duplicate or reorder";

  private static final String NO_SIGNATURE_A_RULE_CAN_CORRUPT =
      "provider worldline withholds the header names its webhook signature travels under: the"
          + " only instruction Direct gives is \"Validate the signature on the message\", and its"
          + " Java SDK page says the request \"should contain the two headers for the keyId and"
          + " the signature\" without naming either. X-GCS-Signature and X-GCS-KeyId are"
          + " Worldline Connect and occur nowhere in Direct. "
          + NO_WEBHOOK_ON_THIS_SLICE;

  private static final String NO_LOOKUP_THAT_REPORTS_A_PROCESSING_STATE =
      WorldlineOperations.NO_PROCESSING_OUTCOME_A_RULE_CAN_SETTLE
          + ". The read route this preset polls, GET /v2/{merchantId}/payments/{paymentId}, is"
          + " not served by this module either; the one read it does serve is"
          + " GET /v2/{merchantId}/hostedtokenizations/{hostedTokenizationId}, which reports a"
          + " token and never a payment";

  private WorldlinePresets() {}

  static PresetExpansion of(PresetRequest request) {
    return switch (request.preset()) {
      case PSP_5XX_THEN_SUCCESS -> answeredWithoutTheOperationThenSucceeding(request);
      case PREAUTH_SUCCESS_CAPTURE_SUCCESS ->
          rules(
              rule(request, WorldlineOperations.CREATE_PAYMENT, succeed()),
              rule(request, WorldlineOperations.CAPTURE_PAYMENT, succeed()));
      case PREAUTH_SUCCESS_CAPTURE_DECLINE ->
          rules(
              rule(request, WorldlineOperations.CREATE_PAYMENT, succeed()),
              rule(
                  request,
                  WorldlineOperations.CAPTURE_PAYMENT,
                  new ScenarioAction.TerminalFailure(
                      Integer.toString(WorldlineStatus.CODE_REJECTED_CAPTURE))));
      case CAPTURE_ACCEPTED_RESPONSE_LOST ->
          rules(
              rule(
                  request,
                  WorldlineOperations.CAPTURE_PAYMENT,
                  new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection())));
      case CANCEL_HOLD_SUCCESS ->
          rules(rule(request, WorldlineOperations.CANCEL_PAYMENT, succeed()));
      case REFUND_FULL_SUCCESS, REFUND_PARTIAL_THEN_FULL -> notExpressible(NO_REFUND_ON_THIS_SLICE);
      case PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS -> notExpressible(NO_TIMEOUT_TO_REPEAT);
      case CARD_ADD_SUCCESS ->
          rules(rule(request, WorldlineOperations.CREATE_HOSTED_TOKENIZATION, succeed()));
      case CARD_ADD_FAILED ->
          rules(
              rule(
                  request,
                  WorldlineOperations.CREATE_HOSTED_TOKENIZATION,
                  new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED)));
      case CARD_ADD_CALLBACK_DELAYED, WEBHOOK_DELAYED, WEBHOOK_DUPLICATE, WEBHOOK_OUT_OF_ORDER ->
          notExpressible(NO_WEBHOOK_ON_THIS_SLICE);
      case WEBHOOK_INVALID_SIGNATURE -> notExpressible(NO_SIGNATURE_A_RULE_CAN_CORRUPT);
      case STATUS_LOOKUP_PROCESSING_THEN_SUCCESS, STATUS_LOOKUP_PROCESSING_THEN_FAILED ->
          notExpressible(NO_LOOKUP_THAT_REPORTS_A_PROCESSING_STATE);
    };
  }

  private static PresetExpansion answeredWithoutTheOperationThenSucceeding(PresetRequest request) {
    List<NewScenarioRule> expanded = new ArrayList<>();
    for (String operation : WorldlineOperations.VOCABULARY.operationNames()) {
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
        WorldlineApi.WORLDLINE,
        request.testRunId(),
        request.priority(),
        request.narrowing().naming(operation),
        action,
        story);
  }
}
