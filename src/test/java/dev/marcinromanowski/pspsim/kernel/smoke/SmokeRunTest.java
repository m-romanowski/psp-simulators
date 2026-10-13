package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmokeRunTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");

  private static final Provenance PROVENANCE = Samples.A_PUBLISHED_EXAMPLE;

  @Test
  void a_run_counts_what_held_what_did_not_and_what_does_not_apply() {
    SmokeRun run =
        new SmokeRun(
            Instant.parse("2026-09-24T09:00:00Z"),
            Duration.ofMillis(1200),
            List.of(
                new SmokeVerdict.Passed(PROVIDER, "a", PROVENANCE, 2),
                new SmokeVerdict.Failed(
                    PROVIDER, "b", PROVENANCE, "status", FailureKind.NOT_OBSERVED),
                new SmokeVerdict.NotApplicable(PROVIDER, "c", PROVENANCE, "nothing signs it")));

    assertThat(run.passed()).isEqualTo(1);
    assertThat(run.failed()).isEqualTo(1);
    assertThat(run.notApplicable()).isEqualTo(1);
    assertThat(run.everythingApplicableHeld()).isFalse();
    assertThat(run.took()).isEqualTo(Duration.ofMillis(1200));
  }

  @Test
  void a_run_of_nothing_but_holds_and_withheld_checks_held() {
    SmokeRun run =
        new SmokeRun(
            Instant.parse("2026-09-24T09:00:00Z"),
            Duration.ZERO,
            List.of(
                new SmokeVerdict.Passed(PROVIDER, "a", PROVENANCE, 1),
                new SmokeVerdict.NotApplicable(PROVIDER, "c", PROVENANCE, "nothing signs it")));

    assertThat(run.everythingApplicableHeld()).isTrue();
  }

  @Test
  void a_check_cannot_be_recorded_as_having_held_while_asserting_nothing() {
    assertThatThrownBy(() -> new SmokeVerdict.Passed(PROVIDER, "a", PROVENANCE, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one expectation");
  }
}
