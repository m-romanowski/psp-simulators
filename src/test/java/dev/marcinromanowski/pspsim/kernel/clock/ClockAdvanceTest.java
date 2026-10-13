package dev.marcinromanowski.pspsim.kernel.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClockAdvanceTest {

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");

  private final RecordedTimes times = new RecordedTimes();

  @Test
  void a_duration_no_clock_can_be_advanced_by_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(() -> ClockAdvance.of("tomorrow"))
        .hasMessage(
            "advanceBy must name a span of time: a form states one as a number and a unit, and a"
                + " call to the control API writes one as an ISO-8601 duration, as in PT30S,"
                + " PT1H or P30D");
  }

  @Test
  void an_advance_that_names_no_duration_at_all_is_rejected_for_the_same_unreadable_reason() {
    assertThatThrownBy(() -> ClockAdvance.of(null))
        .hasMessage(
            "advanceBy must name a span of time: a form states one as a number and a unit, and a"
                + " call to the control API writes one as an ISO-8601 duration, as in PT30S,"
                + " PT1H or P30D");
    assertThatThrownBy(() -> ClockAdvance.of("   "))
        .hasMessage(
            "advanceBy must name a span of time: a form states one as a number and a unit, and a"
                + " call to the control API writes one as an ISO-8601 duration, as in PT30S,"
                + " PT1H or P30D");
  }

  @Test
  void a_duration_of_zero_is_refused_because_a_jump_of_none_would_be_audited_as_a_jump() {
    assertThatThrownBy(() -> ClockAdvance.of("PT0S"))
        .hasMessage(
            "advanceBy must name a duration longer than zero: a run whose clock went backwards"
                + " would read as now an instant before the objects it already holds were created,"
                + " and a jump of no duration would be audited as a jump that did not happen");
  }

  @Test
  void a_duration_that_would_move_a_clock_backwards_is_refused_under_that_same_reason() {
    assertThatThrownBy(() -> ClockAdvance.of("PT-1H"))
        .hasMessage(
            "advanceBy must name a duration longer than zero: a run whose clock went backwards"
                + " would read as now an instant before the objects it already holds were created,"
                + " and a jump of no duration would be audited as a jump that did not happen");
  }

  @Test
  void a_duration_is_read_through_the_space_a_form_field_or_a_shell_leaves_around_it() {
    assertThat(ClockAdvance.of("  PT1H  ").by()).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void a_duration_may_name_days_because_a_deadline_a_tester_waits_out_is_rarely_seconds_away() {
    assertThat(ClockAdvance.of("P30D").by()).isEqualTo(Duration.ofDays(30));
  }

  @Test
  void an_advance_moves_a_pinned_run_to_the_instant_the_duration_past_where_it_stood() {
    times.pin(RUN, PINNED);

    Optional<ClockAdvance.Jump> jumped = ClockAdvance.of("PT1H").applyTo(times, RUN);

    assertThat(jumped)
        .get()
        .satisfies(
            jump -> {
              assertThat(jump.from()).isEqualTo(Instant.parse("2026-09-07T12:00:00Z"));
              assertThat(jump.to()).isEqualTo(Instant.parse("2026-09-07T13:00:00Z"));
              assertThat(jump.by()).isEqualTo(Duration.ofHours(1));
            });
    assertThat(times.of(RUN)).contains(Instant.parse("2026-09-07T13:00:00Z"));
  }

  @Test
  void two_advances_of_an_hour_leave_a_run_two_hours_past_where_it_was_pinned() {
    times.pin(RUN, PINNED);

    ClockAdvance.of("PT1H").applyTo(times, RUN);
    Optional<ClockAdvance.Jump> second = ClockAdvance.of("PT1H").applyTo(times, RUN);

    assertThat(second)
        .get()
        .satisfies(
            jump -> {
              assertThat(jump.from()).isEqualTo(Instant.parse("2026-09-07T13:00:00Z"));
              assertThat(jump.to()).isEqualTo(Instant.parse("2026-09-07T14:00:00Z"));
            });
    assertThat(times.of(RUN)).contains(Instant.parse("2026-09-07T14:00:00Z"));
  }

  @Test
  void an_advance_moves_the_run_it_was_asked_of_and_leaves_every_other_pinned_run_where_it_was() {
    TestRunId other = TestRunId.of("run-b");
    times.pin(RUN, PINNED);
    times.pin(other, PINNED);

    ClockAdvance.of("PT1H").applyTo(times, RUN);

    assertThat(times.of(other)).contains(Instant.parse("2026-09-07T12:00:00Z"));
  }

  @Test
  void an_advance_asked_of_a_run_that_reads_the_host_clock_names_no_jump_and_pins_nothing() {
    Optional<ClockAdvance.Jump> jumped = ClockAdvance.of("PT1H").applyTo(times, RUN);

    assertThat(jumped).isEmpty();
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
