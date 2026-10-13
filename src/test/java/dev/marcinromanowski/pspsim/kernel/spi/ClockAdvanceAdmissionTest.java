package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredAdvance;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClockAdvanceAdmissionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId RUN = TestRunId.of("named-run");

  private static final Instant PINNED = Instant.parse("2026-09-12T10:00:00Z");

  private final Pins pins = new Pins();

  @Test
  void an_advance_names_the_run_it_moved_the_instant_it_left_and_the_instant_it_reads_now() {
    pins.pin(RUN, PINNED);

    ClockAdvanceAdmission.Verdict verdict =
        admission().advance(new DeclaredAdvance(RUN.value(), "PT2H"));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ClockAdvanceAdmission.Verdict.Advanced.class,
            advanced -> {
              assertThat(advanced.testRunId()).isEqualTo(RUN);
              assertThat(advanced.movedFrom()).isEqualTo(Instant.parse("2026-09-12T10:00:00Z"));
              assertThat(advanced.now()).isEqualTo(Instant.parse("2026-09-12T12:00:00Z"));
              assertThat(advanced.by()).isEqualTo(Duration.ofHours(2));
            });
    assertThat(pins.of(RUN)).contains(Instant.parse("2026-09-12T12:00:00Z"));
  }

  @Test
  void an_advance_that_names_no_test_run_moves_the_one_the_simulator_defaults_to() {
    pins.pin(DEFAULT_RUN, PINNED);

    ClockAdvanceAdmission.Verdict verdict = admission().advance(new DeclaredAdvance(null, "PT1H"));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ClockAdvanceAdmission.Verdict.Advanced.class,
            advanced -> assertThat(advanced.testRunId()).isEqualTo(DEFAULT_RUN));
    assertThat(pins.of(DEFAULT_RUN)).contains(Instant.parse("2026-09-12T11:00:00Z"));
  }

  @Test
  void an_advance_of_a_run_this_simulator_holds_no_pinned_time_for_is_refused_and_pins_nothing() {
    ClockAdvanceAdmission.Verdict verdict =
        admission().advance(new DeclaredAdvance(RUN.value(), "PT1H"));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ClockAdvanceAdmission.Verdict.NotPinned.class,
            following ->
                assertThat(following.message())
                    .isEqualTo(
                        "this test run reads the host clock, and a duration added to a clock that"
                            + " is already moving would be read back as a different instant a"
                            + " moment later, so nothing here can say what such a run would be"
                            + " advanced to; pin the run with clock.fixedNow first, and advance it"
                            + " after that"));
    assertThat(pins.of(RUN)).isEmpty();
  }

  @Test
  void a_duration_no_clock_can_be_advanced_by_is_rejected_and_leaves_a_pinned_run_where_it_was() {
    pins.pin(RUN, PINNED);

    ClockAdvanceAdmission.Verdict verdict =
        admission().advance(new DeclaredAdvance(RUN.value(), "one hour"));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ClockAdvanceAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(
                        "advanceBy must name a span of time: a form states one as a number and a unit, and a"
                            + " call to the control API writes one as an ISO-8601 duration, as in PT30S,"
                            + " PT1H or P30D"));
    assertThat(pins.of(RUN)).contains(Instant.parse("2026-09-12T10:00:00Z"));
  }

  @Test
  void an_advance_that_would_move_a_clock_backwards_is_rejected_and_moves_nothing() {
    pins.pin(RUN, PINNED);

    ClockAdvanceAdmission.Verdict verdict =
        admission().advance(new DeclaredAdvance(RUN.value(), "PT-1H"));

    assertThat(verdict).isInstanceOf(ClockAdvanceAdmission.Verdict.Rejected.class);
    assertThat(pins.of(RUN)).contains(Instant.parse("2026-09-12T10:00:00Z"));
  }

  @Test
  void an_advance_naming_a_test_run_no_clock_can_belong_to_is_rejected_before_any_clock_moves() {
    pins.pin(RUN, PINNED);

    ClockAdvanceAdmission.Verdict verdict =
        admission().advance(new DeclaredAdvance("run one", "PT1H"));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ClockAdvanceAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .as("the property is named once, and never the value a caller sent")
                    .startsWith("testRunId must match")
                    .doesNotContain("run one"));
    assertThat(pins.of(RUN)).contains(Instant.parse("2026-09-12T10:00:00Z"));
  }

  @Test
  void every_arm_of_an_advance_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            ClockAdvanceAdmission.THE_TIME_JUMP_IT_ASKED_THIS_TEST_RUN_TO_MAKE);

    assertThat(new ClockAdvanceAdmission.Verdict.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(new ClockAdvanceAdmission.Verdict.NotPinned("host clock").theRefusalItReports())
        .isEqualTo(ActionVerdict.conflicting(subject));
    assertThat(
            new ClockAdvanceAdmission.Verdict.Advanced(RUN, PINNED, PINNED, Duration.ofHours(1))
                .theRefusalItReports())
        .isNull();
  }

  @Test
  void an_unreadable_advance_answers_400_and_one_asked_of_an_unpinned_run_answers_409() {
    assertThat(
            new ClockAdvanceAdmission.Verdict.Rejected("unreadable")
                .theRefusalItReports()
                .httpStatus())
        .isEqualTo(400);
    assertThat(
            new ClockAdvanceAdmission.Verdict.NotPinned("host clock")
                .theRefusalItReports()
                .httpStatus())
        .isEqualTo(409);
  }

  private ClockAdvanceAdmission admission() {
    return new ClockAdvanceAdmission(pins, DEFAULT_RUN);
  }

  private static final class Pins implements FixedTimes {

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
