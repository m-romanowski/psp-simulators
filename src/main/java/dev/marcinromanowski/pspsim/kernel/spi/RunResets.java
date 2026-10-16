package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.util.List;
import java.util.function.Supplier;

public final class RunResets {

  private final UnitOfWork units;
  private final ScenarioRules scenarios;

  public RunResets(UnitOfWork units, ScenarioRules scenarios) {
    this.units = units;
    this.scenarios = scenarios;
  }

  public ResetOutcome ofOne(SimulatorProvider module, TestRunId testRunId, ResetScope scope) {
    return units.keepingOnly(
        done -> true,
        () -> {
          holdTheStoriesOf(module, testRunId, scope);
          return module.reset(testRunId, scope);
        });
  }

  public <T> T ofEvery(List<SimulatorProvider> modules, TestRunId testRunId, Supplier<T> resets) {
    return units.keepingOnly(
        done -> true,
        () -> {
          for (SimulatorProvider module : modules) {
            holdTheStoriesOf(module, testRunId, ResetScope.TEST_RUN);
          }
          return resets.get();
        });
  }

  private void holdTheStoriesOf(SimulatorProvider module, TestRunId testRunId, ResetScope scope) {
    if (scope == ResetScope.ALL) {
      scenarios.holdEveryRunForAReset(module.id());
    } else {
      scenarios.holdForAReset(module.id(), testRunId);
    }
  }
}
