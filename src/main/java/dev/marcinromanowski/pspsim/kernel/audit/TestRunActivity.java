package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

public record TestRunActivity(TestRunId testRunId, Instant lastTouchedAt, int auditRows) {

  public TestRunActivity {
    if (auditRows < 1) {
      throw new IllegalArgumentException(
          "a test run the trail knows carries at least one row, not " + auditRows);
    }
  }
}
