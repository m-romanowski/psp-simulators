package dev.marcinromanowski.pspsim.kernel.clock;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

public interface SimulatorClock {

  Instant now(TestRunId testRunId);
}
