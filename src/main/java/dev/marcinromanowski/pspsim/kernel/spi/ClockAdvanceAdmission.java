package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.clock.ClockAdvance;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredAdvance;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ClockAdvanceAdmission {

  public static final String THE_TIME_JUMP_IT_ASKED_THIS_TEST_RUN_TO_MAKE =
      "the time jump this request asked this test run to make";

  public static final String A_TIME_JUMP_MOVES_A_PINNED_CLOCK =
      "this test run reads the host clock, and a duration added to a clock that is already moving"
          + " would be read back as a different instant a moment later, so nothing here can say"
          + " what such a run would be advanced to; pin the run with clock.fixedNow first, and"
          + " advance it after that";

  public sealed interface Verdict {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_TIME_JUMP_IT_ASKED_THIS_TEST_RUN_TO_MAKE));
      }
    }

    record NotPinned(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.conflicting(
            RefusalSubject.noObjectWasNamed(THE_TIME_JUMP_IT_ASKED_THIS_TEST_RUN_TO_MAKE));
      }
    }

    record Advanced(TestRunId testRunId, Instant movedFrom, Instant now, Duration by)
        implements Verdict {

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  private final FixedTimes fixedTimes;
  private final TestRunId whenNoTestRunIsNamed;

  public ClockAdvanceAdmission(FixedTimes fixedTimes, TestRunId whenNoTestRunIsNamed) {
    this.fixedTimes = fixedTimes;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public Verdict advance(DeclaredAdvance declared) {
    TestRunId testRunId;
    ClockAdvance asked;
    try {
      testRunId = TestRunId.declaredOr(declared.testRunId(), whenNoTestRunIsNamed);
      asked = ClockAdvance.of(declared.advanceBy());
    } catch (IllegalArgumentException rejected) {
      return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
    }
    Optional<ClockAdvance.Jump> jumped = asked.applyTo(fixedTimes, testRunId);
    if (jumped.isEmpty()) {
      return new Verdict.NotPinned(A_TIME_JUMP_MOVES_A_PINNED_CLOCK);
    }
    ClockAdvance.Jump jump = jumped.get();
    return new Verdict.Advanced(testRunId, jump.from(), jump.to(), jump.by());
  }
}
