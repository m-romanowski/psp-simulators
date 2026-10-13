package dev.marcinromanowski.pspsim.kernel.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClockChangeTest {

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");

  private static final Instant REPINNED = Instant.parse("2027-03-01T08:30:00Z");

  private final RecordedTimes times = new RecordedTimes();

  @Test
  void a_configuration_that_says_nothing_about_the_clock_leaves_an_already_pinned_clock_alone() {
    times.pin(RUN, PINNED);

    ClockChange.of(null).applyTo(times, RUN);

    assertThat(times.of(RUN)).contains(PINNED);
  }

  @Test
  void a_clock_block_naming_an_instant_pins_the_test_run_to_it() {
    ClockChange.of(new DeclaredClock(PINNED.toString(), null, null)).applyTo(times, RUN);

    assertThat(times.of(RUN)).contains(PINNED);
  }

  @Test
  void a_clock_block_with_no_instant_returns_the_test_run_to_the_host_clock() {
    times.pin(RUN, PINNED);

    ClockChange.of(new DeclaredClock(null, "UTC", 300)).applyTo(times, RUN);

    assertThat(times.of(RUN)).isEmpty();
  }

  @Test
  void an_instant_no_clock_can_be_pinned_to_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(() -> ClockChange.of(new DeclaredClock("yesterday", null, null)))
        .hasMessage("clock.fixedNow must be an ISO-8601 instant");
  }

  @Test
  void the_three_changes_a_configuration_can_ask_of_a_clock_do_three_things_to_one_pinned_run() {
    times.pin(RUN, PINNED);

    ClockChange.of(null).applyTo(times, RUN);
    assertThat(times.of(RUN)).contains(PINNED);

    ClockChange.of(new DeclaredClock(REPINNED.toString(), null, null)).applyTo(times, RUN);
    assertThat(times.of(RUN)).contains(REPINNED);

    ClockChange.of(new DeclaredClock(null, null, null)).applyTo(times, RUN);
    assertThat(times.of(RUN)).isEmpty();
  }

  private static final class RecordedTimes implements FixedTimes {

    private final Map<TestRunId, Instant> pinned = new LinkedHashMap<>();

    @Override
    public Optional<Instant> of(TestRunId testRunId) {
      return Optional.ofNullable(pinned.get(testRunId));
    }

    @Override
    public void pin(TestRunId testRunId, Instant fixedNow) {
      pinned.put(testRunId, fixedNow);
    }

    @Override
    public void release(TestRunId testRunId) {
      pinned.remove(testRunId);
    }
  }
}
