package dev.marcinromanowski.pspsim.kernel.clock.internal;

import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Clock;
import java.time.Instant;

public final class HostBackedSimulatorClock implements SimulatorClock {

  private final FixedTimes fixedTimes;
  private final Clock host;

  public HostBackedSimulatorClock(FixedTimes fixedTimes) {
    this(fixedTimes, Clock.systemUTC());
  }

  public HostBackedSimulatorClock(FixedTimes fixedTimes, Clock host) {
    this.fixedTimes = fixedTimes;
    this.host = host;
  }

  @Override
  public Instant now(TestRunId testRunId) {
    return fixedTimes.of(testRunId).orElseGet(host::instant);
  }
}
