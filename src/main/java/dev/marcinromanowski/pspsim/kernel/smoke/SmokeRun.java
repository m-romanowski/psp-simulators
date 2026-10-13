package dev.marcinromanowski.pspsim.kernel.smoke;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record SmokeRun(Instant startedAt, Duration took, List<SmokeVerdict> verdicts) {

  public SmokeRun {
    verdicts = List.copyOf(verdicts);
  }

  public long passed() {
    return verdicts.stream().filter(SmokeVerdict.Passed.class::isInstance).count();
  }

  public long failed() {
    return verdicts.stream().filter(SmokeVerdict.Failed.class::isInstance).count();
  }

  public long notApplicable() {
    return verdicts.stream().filter(SmokeVerdict.NotApplicable.class::isInstance).count();
  }

  public boolean everythingApplicableHeld() {
    return failed() == 0;
  }
}
