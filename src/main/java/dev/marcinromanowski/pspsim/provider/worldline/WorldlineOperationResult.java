package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

record WorldlineOperationResult(
    ProviderOutcome outcome,
    @Nullable String providerObjectId,
    @Nullable RefusalSubject refusal,
    @Nullable String scenarioId,
    @Nullable TransportFault fault,
    @Nullable FaultedDelivery insteadOfTheOperation,
    WorldlineBody body) {

  static WorldlineOperationResult succeeded(@Nullable String providerObjectId, WorldlineBody body) {
    return new WorldlineOperationResult(
        ProviderOutcome.SUCCESS, providerObjectId, null, null, null, null, body);
  }

  static WorldlineOperationResult failed(RefusalSubject refusal, WorldlineBody body) {
    return new WorldlineOperationResult(
        ProviderOutcome.FAILURE,
        refusal.theObjectItNames().orElse(null),
        refusal,
        null,
        null,
        null,
        body);
  }

  static WorldlineOperationResult decidedBy(
      ScenarioDecision decision,
      Supplier<WorldlineOperationResult> operation,
      Function<String, WorldlineOperationResult> decline) {
    return switch (decision) {
      case ScenarioDecision.NoScenario _ -> operation.get();
      case ScenarioDecision.Proceed matched -> operation.get().proceededUnder(matched.scenarioId());
      case ScenarioDecision.Disrupt disrupted ->
          operation.get().faultedBy(disrupted.scenarioId(), disrupted.fault());
      case ScenarioDecision.Preempt preempted ->
          answeredInsteadOf(preempted.scenarioId(), preempted.answer());
      case ScenarioDecision.Fail declined ->
          decline.apply(declined.providerCode()).declinedBy(declined.scenarioId());
      case ScenarioDecision.Pend pending -> throw pending.theOperationAnswersNoProcessingState();
    };
  }

  static WorldlineOperationResult answeredInsteadOf(String scenarioId, FaultedDelivery answer) {
    return new WorldlineOperationResult(
        ProviderOutcome.UNKNOWN,
        null,
        null,
        scenarioId,
        null,
        answer,
        WorldlineBody.nothingRendered());
  }

  WorldlineOperationResult declinedBy(String scenarioId) {
    return new WorldlineOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  WorldlineOperationResult proceededUnder(String scenarioId) {
    if (outcome == ProviderOutcome.FAILURE) {
      return this;
    }
    return new WorldlineOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  WorldlineOperationResult faultedBy(String scenarioId, TransportFault fault) {
    return new WorldlineOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  WorldlineOperationResult alsoCarrying(String header, String value) {
    return new WorldlineOperationResult(
        outcome,
        providerObjectId,
        refusal,
        scenarioId,
        fault,
        insteadOfTheOperation,
        body.alsoCarrying(header, value));
  }
}
