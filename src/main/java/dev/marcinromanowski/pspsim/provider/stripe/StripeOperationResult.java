package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

record StripeOperationResult(
    ProviderOutcome outcome,
    @Nullable String providerObjectId,
    @Nullable RefusalSubject refusal,
    @Nullable String scenarioId,
    @Nullable TransportFault fault,
    @Nullable FaultedDelivery insteadOfTheOperation,
    int httpStatus,
    StripeJson body,
    boolean theEndpointBeganExecuting,
    boolean stateChanged) {

  static final int OK = 200;

  static StripeOperationResult succeeded(String providerObjectId, StripeJson body) {
    return new StripeOperationResult(
        ProviderOutcome.SUCCESS, providerObjectId, null, null, null, null, OK, body, true, true);
  }

  static StripeOperationResult answered(StripeJson body) {
    return new StripeOperationResult(
        ProviderOutcome.SUCCESS, null, null, null, null, null, OK, body, true, false);
  }

  static StripeOperationResult answered(String providerObjectId, StripeJson body) {
    return new StripeOperationResult(
        ProviderOutcome.SUCCESS, providerObjectId, null, null, null, null, OK, body, true, false);
  }

  static StripeOperationResult refusedBeforeExecuting(RefusalSubject refusal, StripeError error) {
    return refused(refusal, error, false, false);
  }

  static StripeOperationResult refused(RefusalSubject refusal, StripeError error) {
    return refused(refusal, error, true, false);
  }

  static StripeOperationResult refusedAfterChangingState(
      RefusalSubject refusal, StripeError error) {
    return refused(refusal, error, true, true);
  }

  private static StripeOperationResult refused(
      RefusalSubject refusal,
      StripeError error,
      boolean theEndpointBeganExecuting,
      boolean stateChanged) {
    return new StripeOperationResult(
        ProviderOutcome.FAILURE,
        refusal.theObjectItNames().orElse(null),
        refusal,
        null,
        null,
        null,
        error.httpStatus(),
        error.body(),
        theEndpointBeganExecuting,
        stateChanged);
  }

  static StripeOperationResult decidedBy(
      ScenarioDecision decision,
      Supplier<StripeOperationResult> operation,
      Function<StripeDecline, StripeOperationResult> decline) {
    return decidedBy(
        decision,
        operation,
        decline,
        pending -> {
          throw pending.theOperationAnswersNoProcessingState();
        });
  }

  static StripeOperationResult decidedBy(
      ScenarioDecision decision,
      Supplier<StripeOperationResult> operation,
      Function<StripeDecline, StripeOperationResult> decline,
      Function<ScenarioDecision.Pend, StripeOperationResult> pend) {
    return switch (decision) {
      case ScenarioDecision.NoScenario _ -> operation.get();
      case ScenarioDecision.Proceed matched -> operation.get().proceededUnder(matched.scenarioId());
      case ScenarioDecision.Disrupt disrupted ->
          operation.get().faultedBy(disrupted.scenarioId(), disrupted.fault());
      case ScenarioDecision.Preempt preempted ->
          answeredInsteadOf(preempted.scenarioId(), preempted.answer());
      case ScenarioDecision.Fail declined ->
          decline
              .apply(StripeDecline.of(declined.providerCode()).orElseThrow())
              .declinedBy(declined.scenarioId());
      case ScenarioDecision.Pend pending ->
          pend.apply(pending).proceededUnder(pending.scenarioId());
    };
  }

  private static StripeOperationResult answeredInsteadOf(
      String scenarioId, FaultedDelivery answer) {
    return new StripeOperationResult(
        ProviderOutcome.UNKNOWN,
        null,
        null,
        scenarioId,
        null,
        answer,
        answer.httpStatus(),
        StripeJson.object(),
        false,
        false);
  }

  StripeOperationResult declinedBy(String scenarioId) {
    return new StripeOperationResult(
        outcome,
        providerObjectId,
        refusal,
        scenarioId,
        fault,
        insteadOfTheOperation,
        httpStatus,
        body,
        theEndpointBeganExecuting,
        stateChanged);
  }

  StripeOperationResult proceededUnder(String scenarioId) {
    if (outcome == ProviderOutcome.FAILURE) {
      return this;
    }
    return new StripeOperationResult(
        outcome,
        providerObjectId,
        refusal,
        scenarioId,
        fault,
        insteadOfTheOperation,
        httpStatus,
        body,
        theEndpointBeganExecuting,
        stateChanged);
  }

  StripeOperationResult faultedBy(String scenarioId, TransportFault fault) {
    return new StripeOperationResult(
        outcome,
        providerObjectId,
        refusal,
        scenarioId,
        fault,
        insteadOfTheOperation,
        httpStatus,
        body,
        theEndpointBeganExecuting,
        stateChanged);
  }
}
