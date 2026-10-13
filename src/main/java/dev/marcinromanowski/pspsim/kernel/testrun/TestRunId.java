package dev.marcinromanowski.pspsim.kernel.testrun;

import dev.marcinromanowski.pspsim.kernel.AShapeSaidInWords;
import org.jspecify.annotations.Nullable;

public record TestRunId(String value) {

  public static final AShapeSaidInWords SHAPE =
      AShapeSaidInWords.of("[A-Za-z0-9][A-Za-z0-9._-]{0,63}", "checkout-smoke");

  private static final String RESERVED = "UNKNOWN";

  public static final TestRunId UNKNOWN = new TestRunId(RESERVED);

  public TestRunId {
    if (value == null || !SHAPE.admits(value)) {
      throw new IllegalArgumentException(SHAPE.theRefusalOf("testRunId"));
    }
  }

  public static TestRunId of(String value) {
    if (RESERVED.equals(value)) {
      throw reserved();
    }
    return new TestRunId(value);
  }

  public static TestRunId declaredOr(@Nullable String declared, TestRunId whenAbsent) {
    if (declared == null || declared.isBlank()) {
      return whenAbsent;
    }
    return of(declared);
  }

  public static TestRunId theRunARowAboutThisRequestBelongsTo(
      @Nullable String declared, TestRunId whenItNamesNone) {
    try {
      return declaredOr(declared, whenItNamesNone);
    } catch (IllegalArgumentException theRequestNamedOneNoNamespaceCanHold) {
      return UNKNOWN;
    }
  }

  public static TestRunId storableNamespace(TestRunId testRunId) {
    if (UNKNOWN.equals(testRunId)) {
      throw reserved();
    }
    return testRunId;
  }

  public static String theRefusalOfTheReservedId() {
    return "testRunId must not be " + RESERVED + "; that id is reserved for a failed resolution";
  }

  private static IllegalArgumentException reserved() {
    return new IllegalArgumentException(theRefusalOfTheReservedId());
  }

  public static TestRunId storedNamespace(String value) {
    return RESERVED.equals(value) ? UNKNOWN : of(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
