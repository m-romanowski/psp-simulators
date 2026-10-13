package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class TheCausesOfAFailure {

  public static final String THE_CAUSES = "causes";

  static final int AS_DEEP_AS_A_READER_GOES = 12;

  private static final Pattern A_FULLY_QUALIFIED_TYPE_NAME =
      Pattern.compile("(?:[a-z_$][A-Za-z0-9_$]*\\.){2,}([A-Z][A-Za-z0-9_$]*)");

  private static final Pattern WHAT_A_DRIVER_QUOTES_BACK = Pattern.compile("\\[[^\\[\\]]*\\]");

  private TheCausesOfAFailure() {}

  public static List<String> of(@Nullable Throwable error) {
    List<String> asItSaysItself = new ArrayList<>();
    Throwable reading = error;
    while (reading != null && asItSaysItself.size() < AS_DEEP_AS_A_READER_GOES) {
      asItSaysItself.add(asItSaysItself(reading));
      reading = reading.getCause();
    }
    return List.copyOf(asItSaysItself);
  }

  private static String asItSaysItself(Throwable reading) {
    String said = reading.getMessage();
    String named = reading.getClass().getSimpleName();
    return said == null ? named : named + ": " + withoutWhatTravelsNowhere(said);
  }

  static String withoutWhatTravelsNowhere(String said) {
    String withSimpleNames = A_FULLY_QUALIFIED_TYPE_NAME.matcher(said).replaceAll("$1");
    return WHAT_A_DRIVER_QUOTES_BACK
        .matcher(withSimpleNames)
        .replaceAll(Matcher.quoteReplacement(Redaction.REDACTED));
  }
}
