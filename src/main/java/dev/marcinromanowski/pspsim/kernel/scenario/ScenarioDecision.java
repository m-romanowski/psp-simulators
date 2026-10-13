package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public sealed interface ScenarioDecision {

  record NoScenario() implements ScenarioDecision {}

  record Proceed(String scenarioId) implements ScenarioDecision {}

  record Fail(String scenarioId, String providerCode) implements ScenarioDecision {}

  record Disrupt(String scenarioId, TransportFault fault) implements ScenarioDecision {}

  record Preempt(String scenarioId, FaultedDelivery answer) implements ScenarioDecision {}

  record Pend(String scenarioId, Duration settlesAfter, @Nullable String providerCode)
      implements ScenarioDecision {

    public IllegalStateException theOperationAnswersNoProcessingState() {
      return new IllegalStateException(
          "a rule scheduled an outcome behind a provider-native processing state and the operation"
              + " it reached answers with none. A provider names the operations that do on its own"
              + " vocabulary and admission refuses a rule naming any other, so no request should"
              + " ever arrive here carrying this decision");
    }
  }
}
