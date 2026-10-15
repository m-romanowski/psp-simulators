package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.util.function.Supplier;

public final class RunResets {

  private final UnitOfWork units;

  public RunResets(UnitOfWork units) {
    this.units = units;
  }

  public ResetOutcome ofOne(SimulatorProvider module, TestRunId testRunId, ResetScope scope) {
    return together(() -> module.reset(testRunId, scope));
  }

  public <T> T together(Supplier<T> resets) {
    return units.keepingOnly(done -> true, resets);
  }
}
