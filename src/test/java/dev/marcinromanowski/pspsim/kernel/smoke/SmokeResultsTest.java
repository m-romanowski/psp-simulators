package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmokeResultsTest {

  private final SmokeResults results = new SmokeResults();

  @Test
  void a_simulator_that_has_run_no_check_holds_no_result() {
    assertThat(results.last()).isEmpty();
  }

  @Test
  void the_result_a_press_produced_is_the_one_held_and_a_later_press_replaces_it() {
    SmokeRun first = aRunAt("2026-09-24T09:00:00Z");
    SmokeRun second = aRunAt("2026-09-24T10:00:00Z");

    results.hold(first);
    assertThat(results.last()).contains(first);

    results.hold(second);
    assertThat(results.last()).contains(second);
  }

  private static SmokeRun aRunAt(String moment) {
    return new SmokeRun(Instant.parse(moment), Duration.ZERO, List.of());
  }
}
