package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

record GrowOperationResult(
    ProviderOutcome outcome,
    @Nullable String providerObjectId,
    @Nullable RefusalSubject refusal,
    @Nullable String scenarioId,
    @Nullable TransportFault fault,
    @Nullable FaultedDelivery insteadOfTheOperation,
    GrowJson body) {

  static GrowOperationResult succeeded(String providerObjectId, GrowJson body) {
    return succeededAbout(providerObjectId, body);
  }

  static GrowOperationResult succeededAbout(@Nullable String providerObjectId, GrowJson body) {
    return new GrowOperationResult(
        ProviderOutcome.SUCCESS, providerObjectId, null, null, null, null, body);
  }

  static GrowOperationResult failed(RefusalSubject refusal, GrowJson body) {
    return new GrowOperationResult(
        ProviderOutcome.FAILURE,
        refusal.theObjectItNames().orElse(null),
        refusal,
        null,
        null,
        null,
        body);
  }

  static GrowOperationResult decidedBy(
      ScenarioDecision decision,
      Supplier<GrowOperationResult> operation,
      Function<String, GrowOperationResult> decline) {
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

  private static GrowOperationResult answeredInsteadOf(String scenarioId, FaultedDelivery answer) {
    return new GrowOperationResult(
        ProviderOutcome.UNKNOWN, null, null, scenarioId, null, answer, GrowJson.object());
  }

  GrowOperationResult declinedBy(String scenarioId) {
    return new GrowOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  GrowOperationResult proceededUnder(String scenarioId) {
    if (outcome == ProviderOutcome.FAILURE) {
      return this;
    }
    return new GrowOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }

  GrowOperationResult faultedBy(String scenarioId, TransportFault fault) {
    return new GrowOperationResult(
        outcome, providerObjectId, refusal, scenarioId, fault, insteadOfTheOperation, body);
  }
}
