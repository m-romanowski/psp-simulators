package dev.marcinromanowski.pspsim.kernel.clock;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;

public interface FixedTimes {

  Optional<Instant> of(TestRunId testRunId);

  void pin(TestRunId testRunId, Instant fixedNow);

  void release(TestRunId testRunId);
}
