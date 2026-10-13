package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionAdmission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class ReproductionScreen {

  public static final String THE_BOX_A_REMAPPING_IS_TYPED_INTO = "remap";

  public static final String A_BUNDLE_IS_WHAT_THIS_BOX_TAKES =
      "this box takes the document the box above produced; what was pasted is not a reproduction"
          + " bundle this simulator can read";

  public static final String A_LINE_NAMES_THE_ID_IT_REPLACES =
      "each line of remapping names one provider-native id this bundle carries and the id to use"
          + " here, as carried=here; this refusal names the line that did not, and never what was"
          + " typed on it";

  public static final String NOTHING_WAS_WRITTEN =
      "this was a dry run: the report below is what an import would do, and this test run holds"
          + " everything it held before";

  public static final String THE_EVIDENCE_IS_READ_AND_NEVER_WRITTEN =
      "the trail, the recorded requests and responses, the callbacks with their attempts and the"
          + " state snapshot are evidence a person reads. No import writes any of them, here or"
          + " anywhere, and the bundle says so on its face under contents.";

  public static final String A_SIGNATURE_TRAVELS_AS_EVIDENCE =
      "a recorded request, response or callback carries the signature the key of the run that made"
          + " it produced. This simulator carries no key in a bundle, so those bytes verify against"
          + " nothing under another simulator's key while still reading as correct. That is why"
          + " they are carried as evidence and not as something to send again.";

  public static final String IDS_ARE_PRESERVED_UNLESS_A_REMAPPING_IS_TYPED =
      "leave the remapping box empty and the provider-native ids this bundle carries are written"
          + " unchanged. An id the target run already gives to another instrument is then refused"
          + " as a conflict naming that instrument, and the way through is a line in this box.";

  private static final Pattern THE_END_OF_A_LINE = Pattern.compile("\\R");

  private ReproductionScreen() {}

  public sealed interface Pasted {

    record Unreadable(String message) implements Pasted {}

    record Bundle(ReproductionBundle bundle) implements Pasted {}
  }

  public sealed interface Remapping {

    record Unreadable(String message) implements Remapping {}

    record Asked(Map<String, String> byCarriedId) implements Remapping {

      public Asked {
        byCarriedId = Map.copyOf(byCarriedId);
      }
    }
  }

  public static Pasted pastedInto(
      @Nullable String document, Function<String, ReproductionBundle> reader) {
    if (document == null || document.isBlank()) {
      return new Pasted.Unreadable(A_BUNDLE_IS_WHAT_THIS_BOX_TAKES);
    }
    try {
      return new Pasted.Bundle(reader.apply(document));
    } catch (RuntimeException unreadable) {
      return new Pasted.Unreadable(A_BUNDLE_IS_WHAT_THIS_BOX_TAKES);
    }
  }

  public static Remapping theRemappingTypedInto(@Nullable String typed) {
    if (typed == null || typed.isBlank()) {
      return new Remapping.Asked(Map.of());
    }
    Map<String, String> byCarriedId = new LinkedHashMap<>();
    List<String> lines = THE_END_OF_A_LINE.splitAsStream(typed).toList();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty()) {
        continue;
      }
      int equals = line.indexOf('=');
      if (equals <= 0) {
        return theLineItCannotRead(i);
      }
      String carried = line.substring(0, equals).strip();
      String here = line.substring(equals + 1).strip();
      if (here.isEmpty()) {
        return theLineItCannotRead(i);
      }
      byCarriedId.put(carried, here);
    }
    return new Remapping.Asked(Map.copyOf(byCarriedId));
  }

  private static Remapping theLineItCannotRead(int line) {
    return new Remapping.Unreadable(
        THE_BOX_A_REMAPPING_IS_TYPED_INTO
            + " line "
            + (line + 1)
            + ": "
            + A_LINE_NAMES_THE_ID_IT_REPLACES);
  }

  public static ActionVerdict.Refused theRefusalOfABundleItDidNotTakeBack() {
    return ActionVerdict.refused(
        RefusalSubject.noObjectWasNamed(
            ReproductionAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
  }
}
