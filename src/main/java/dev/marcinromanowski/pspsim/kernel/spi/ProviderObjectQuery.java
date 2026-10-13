package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record ProviderObjectQuery(TestRunId testRunId, @Nullable String after, int limit) {

  public static final int DEFAULT_LIMIT = 100;

  public static final int LARGEST_LIMIT = 500;

  public ProviderObjectQuery {
    if (limit < 1 || limit > LARGEST_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + LARGEST_LIMIT);
    }
  }

  public static ProviderObjectQuery of(DeclaredObjectQuery declared, TestRunId whenTestRunAbsent) {
    return new ProviderObjectQuery(
        testRunOf(declared.testRunId(), whenTestRunAbsent),
        declared.after(),
        limitOf(declared.limit()));
  }

  private static TestRunId testRunOf(@Nullable String declared, TestRunId whenAbsent) {
    if (declared == null || declared.isBlank()) {
      return whenAbsent;
    }
    return TestRunId.storedNamespace(declared);
  }

  private static int limitOf(@Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return DEFAULT_LIMIT;
    }
    try {
      return Integer.parseInt(declared.strip());
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException("limit must be a whole number");
    }
  }
}
