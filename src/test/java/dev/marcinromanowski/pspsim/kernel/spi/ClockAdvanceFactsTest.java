package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClockAdvanceFactsTest {

  private static final TestRunId RUN = TestRunId.of("named-run");

  @Test
  void a_recorded_advance_says_by_how_much_it_moved_where_it_left_and_what_it_now_reads() {
    assertThat(
            ClockAdvanceFacts.of(
                new ClockAdvanceAdmission.Verdict.Advanced(
                    RUN,
                    Instant.parse("2026-09-12T10:00:00Z"),
                    Instant.parse("2026-09-12T12:00:00Z"),
                    Duration.ofHours(2))))
        .containsExactly(
            entry("clock.advanceBy", List.of("PT2H")),
            entry("clock.movedFrom", List.of("2026-09-12T10:00:00Z")),
            entry("clock.now", List.of("2026-09-12T12:00:00Z")));
  }

  @Test
  void a_refused_advance_is_recorded_under_the_duration_it_asked_for_and_not_under_a_landing() {
    assertThat(ClockAdvanceFacts.asked("PT1H"))
        .containsExactly(entry("clock.advanceBy", List.of("PT1H")));
  }

  @Test
  void an_advance_that_named_no_duration_is_recorded_as_an_action_that_named_none() {
    assertThat(ClockAdvanceFacts.asked(null)).isEmpty();
  }
}
