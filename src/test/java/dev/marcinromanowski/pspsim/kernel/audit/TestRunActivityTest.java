package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TestRunActivityTest {

  private static final TestRunId RUN = TestRunId.of("wizard-check");
  private static final Instant WHEN = Instant.parse("2026-09-30T09:15:00Z");

  @Test
  void a_run_the_trail_reports_carries_at_least_one_row() {
    assertThat(new TestRunActivity(RUN, WHEN, 1).auditRows()).isEqualTo(1);
  }

  @Test
  void a_run_the_trail_holds_no_row_of_is_not_a_run_the_trail_knows() {
    assertThatThrownBy(() -> new TestRunActivity(RUN, WHEN, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one row, not 0");
    assertThatThrownBy(() -> new TestRunActivity(RUN, WHEN, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one row, not -1");
  }
}
