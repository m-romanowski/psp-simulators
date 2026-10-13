package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

record AutraOperationResult(
    ProviderOutcome outcome,
    @Nullable String providerObjectId,
    @Nullable RefusalSubject refusal,
    @Nullable String scenarioId,
    @Nullable TransportFault fault,
    @Nullable FaultedDelivery insteadOfTheOperation,
    AutraBody body) {

  static AutraOperationResult succeeded(@Nullable String providerObjectId, AutraBody body) {
    return new AutraOperationResult(
        ProviderOutcome.SUCCESS, providerObjectId, null, null, null, null, body);
  }

  static AutraOperationResult failed(RefusalSubject refusal, AutraBody body) {
    return new AutraOperationResult(
        ProviderOutcome.FAILURE,
        refusal.theObjectItNames().orElse(null),
        refusal,
        null,
        null,
        null,
        body);
  }

  static AutraOperationResult decidedBy(
      ScenarioDecision decision,
      Supplier<AutraOperationResult> operation,
      Function<String, AutraOperationResult> decline) {
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

  static AutraOperationResult answeredInsteadOf(String scenarioId, FaultedDelivery answer) {
    return new AutraOperationResult(
        ProviderOutcome.UNKNOWN, null, null, scenarioId, null, answer, AutraBody.nothingRendered());
  }

  AutraOperationResult declinedBy(String scenarioId) {
    return new AutraOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  AutraOperationResult proceededUnder(String scenarioId) {
    if (outcome == ProviderOutcome.FAILURE) {
      return this;
    }
    return new AutraOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  AutraOperationResult faultedBy(String scenarioId, TransportFault fault) {
    return new AutraOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }
}
