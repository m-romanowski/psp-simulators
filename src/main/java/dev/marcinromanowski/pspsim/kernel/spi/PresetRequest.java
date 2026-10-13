package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

public record PresetRequest(
    ScenarioPreset preset, TestRunId testRunId, int priority, ScenarioMatch narrowing) {

  public PresetRequest {
    if (narrowing.operation() != null) {
      throw new IllegalArgumentException(
          "match.operation is not a narrowing a preset accepts, because a preset names the"
              + " operations of the flow it expands into");
    }
  }
}
