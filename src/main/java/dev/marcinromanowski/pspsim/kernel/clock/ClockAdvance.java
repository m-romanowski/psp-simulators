package dev.marcinromanowski.pspsim.kernel.clock;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ClockAdvance {

  private static final String AN_ADVANCE_IS_READ_AS_A_DURATION =
      "advanceBy must name a span of time: a form states one as a number and a unit, and a call"
          + " to the control API writes one as an ISO-8601 duration, as in PT30S, PT1H or P30D";

  private static final String AN_ADVANCE_MOVES_A_CLOCK_FORWARD =
      "advanceBy must name a duration longer than zero: a run whose clock went backwards"
          + " would read as now an instant before the objects it already holds were created, and a"
          + " jump of no duration would be audited as a jump that did not happen";

  private final Duration by;

  private ClockAdvance(Duration by) {
    this.by = by;
  }

  public static ClockAdvance of(@Nullable String advanceBy) {
    if (advanceBy == null || advanceBy.isBlank()) {
      throw new IllegalArgumentException(AN_ADVANCE_IS_READ_AS_A_DURATION);
    }
    Duration by;
    try {
      by = Duration.parse(advanceBy.strip());
    } catch (DateTimeParseException unreadable) {
      throw new IllegalArgumentException(AN_ADVANCE_IS_READ_AS_A_DURATION);
    }
    if (by.isZero() || by.isNegative()) {
      throw new IllegalArgumentException(AN_ADVANCE_MOVES_A_CLOCK_FORWARD);
    }
    return new ClockAdvance(by);
  }

  public Duration by() {
    return by;
  }

  public Optional<Jump> applyTo(FixedTimes fixedTimes, TestRunId testRunId) {
    Instant from = fixedTimes.of(testRunId).orElse(null);
    if (from == null) {
      return Optional.empty();
    }
    Instant to = from.plus(by);
    fixedTimes.pin(testRunId, to);
    return Optional.of(new Jump(from, to, by));
  }

  public record Jump(Instant from, Instant to, Duration by) {}
}
