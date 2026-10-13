package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.smoke.FailureKind;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class SmokeScreen {

  public static final String NOTHING_HAS_BEEN_CHECKED_YET =
      "Nothing has been checked against a real PSP in this simulator since it started. Press "
          + "Run the checks to drive every covered provider's own HTTP surface and read the "
          + "answers against the vendor samples each one reads its expectations from.";

  public static final String EVERY_APPLICABLE_CHECK_HELD =
      "Every applicable check held against the vendor sample it reads its expectations from.";

  public static final String A_CHECK_DID_NOT_HOLD =
      "A check did not hold. Each failing row names the provider, the operation, the field and "
          + "the vendor sample the expectation is read from; it never renders what was observed.";

  private SmokeScreen() {}

  public static @Nullable String theRefusalThisScreenPrints(
      @Nullable String whatRunningTheChecksRefused, Selection whatTheBarCarries) {
    if (whatRunningTheChecksRefused != null) {
      return whatRunningTheChecksRefused;
    }
    return whatTheBarCarries instanceof Selection.Refused refused ? refused.message() : null;
  }

  public record Row(
      String provider, String operation, String verdict, String field, String evidence) {}

  public record View(
      boolean ran,
      String summary,
      long passed,
      long failed,
      long notApplicable,
      long tookMillis,
      List<String> uncovered,
      List<Row> rows) {}

  public static View of(Optional<SmokeRun> last, List<String> uncovered) {
    if (last.isEmpty()) {
      return new View(
          false, NOTHING_HAS_BEEN_CHECKED_YET, 0, 0, 0, 0, List.copyOf(uncovered), List.of());
    }
    SmokeRun run = last.get();
    List<Row> rows = new ArrayList<>();
    for (SmokeVerdict verdict : run.verdicts()) {
      rows.add(rowOf(verdict));
    }
    return new View(
        true,
        run.everythingApplicableHeld() ? EVERY_APPLICABLE_CHECK_HELD : A_CHECK_DID_NOT_HOLD,
        run.passed(),
        run.failed(),
        run.notApplicable(),
        run.took().toMillis(),
        List.copyOf(uncovered),
        List.copyOf(rows));
  }

  private static Row rowOf(SmokeVerdict verdict) {
    return switch (verdict) {
      case SmokeVerdict.Passed held ->
          new Row(
              held.provider().value(), held.operation(), "held", "", held.provenance().rendered());
      case SmokeVerdict.NotApplicable withheld ->
          new Row(
              withheld.provider().value(),
              withheld.operation(),
              "does not apply",
              "",
              withheld.reason() + " (" + withheld.provenance().rendered() + ")");
      case SmokeVerdict.Failed broke ->
          new Row(
              broke.provider().value(),
              broke.operation(),
              verdictOf(broke.kind()),
              broke.field(),
              broke.provenance().rendered());
    };
  }

  static String verdictOf(FailureKind kind) {
    return switch (kind) {
      case NOT_AS_PUBLISHED -> "not what the vendor sample shows";
      case NOT_OBSERVED -> "the answer carried no such field";
      case ASSERTS_NOTHING -> "the check asserts nothing";
      case UNREACHABLE -> "the route did not answer";
    };
  }
}
