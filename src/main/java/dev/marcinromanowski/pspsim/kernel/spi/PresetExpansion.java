package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import java.util.List;

public sealed interface PresetExpansion {

  record Rules(List<NewScenarioRule> rules) implements PresetExpansion {

    public Rules {
      if (rules.isEmpty()) {
        throw new IllegalArgumentException(
            "a preset that expands into no rule did nothing, and must be reported as one this"
                + " provider cannot express");
      }
      rules = List.copyOf(rules);
    }
  }

  record NotExpressible(String reason) implements PresetExpansion {

    public NotExpressible {
      if (reason.isBlank()) {
        throw new IllegalArgumentException(
            "a preset this provider cannot express must name what is missing");
      }
    }
  }
}
