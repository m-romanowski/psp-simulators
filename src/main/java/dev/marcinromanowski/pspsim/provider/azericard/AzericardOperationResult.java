package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

record AzericardOperationResult(
    ProviderOutcome outcome,
    @Nullable String providerObjectId,
    @Nullable RefusalSubject refusal,
    @Nullable String scenarioId,
    @Nullable TransportFault fault,
    @Nullable FaultedDelivery insteadOfTheOperation,
    AzericardBody body) {

  static AzericardOperationResult succeeded(String providerObjectId, AzericardBody body) {
    return new AzericardOperationResult(
        ProviderOutcome.SUCCESS, providerObjectId, null, null, null, null, body);
  }

  static AzericardOperationResult failed(RefusalSubject refusal, AzericardBody body) {
    return new AzericardOperationResult(
        ProviderOutcome.FAILURE,
        refusal.theObjectItNames().orElse(null),
        refusal,
        null,
        null,
        null,
        body);
  }

  static AzericardOperationResult decidedBy(
      ScenarioDecision decision,
      Supplier<AzericardOperationResult> operation,
      Function<String, AzericardOperationResult> decline) {
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

  private static AzericardOperationResult answeredInsteadOf(
      String scenarioId, FaultedDelivery answer) {
    return new AzericardOperationResult(
        ProviderOutcome.UNKNOWN,
        null,
        null,
        scenarioId,
        null,
        answer,
        AzericardBody.nothingRendered());
  }

  AzericardOperationResult declinedBy(String scenarioId) {
    return new AzericardOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  AzericardOperationResult proceededUnder(String scenarioId) {
    if (outcome == ProviderOutcome.FAILURE) {
      return this;
    }
    return new AzericardOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  AzericardOperationResult faultedBy(String scenarioId, TransportFault fault) {
    return new AzericardOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }
}
