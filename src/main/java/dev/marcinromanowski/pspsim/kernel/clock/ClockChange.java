package dev.marcinromanowski.pspsim.kernel.clock;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;

public sealed interface ClockChange {

  void applyTo(FixedTimes fixedTimes, TestRunId testRunId);

  static ClockChange of(@Nullable DeclaredClock declared) {
    if (declared == null) {
      return new Unchanged();
    }
    String fixedNow = declared.fixedNow();
    if (fixedNow == null) {
      return new ReleasedToHostClock();
    }
    try {
      return new PinnedTo(Instant.parse(fixedNow));
    } catch (DateTimeParseException malformed) {
      throw new IllegalArgumentException("clock.fixedNow must be an ISO-8601 instant");
    }
  }

  record Unchanged() implements ClockChange {

    @Override
    public void applyTo(FixedTimes fixedTimes, TestRunId testRunId) {}
  }

  record PinnedTo(Instant fixedNow) implements ClockChange {

    @Override
    public void applyTo(FixedTimes fixedTimes, TestRunId testRunId) {
      fixedTimes.pin(testRunId, fixedNow);
    }
  }

  record ReleasedToHostClock() implements ClockChange {

    @Override
    public void applyTo(FixedTimes fixedTimes, TestRunId testRunId) {
      fixedTimes.release(testRunId);
    }
  }
}
