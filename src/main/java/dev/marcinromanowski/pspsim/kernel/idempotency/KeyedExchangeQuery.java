package dev.marcinromanowski.pspsim.kernel.idempotency;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record KeyedExchangeQuery(TestRunId testRunId, long after, int limit) {

  public static final int DEFAULT_LIMIT = 50;

  public static final int LARGEST_LIMIT = 500;

  public static final long FROM_THE_NEWEST = Long.MAX_VALUE;

  public static final String AFTER_NAMES_A_ROW_ALREADY_SEEN =
      "after must be the cursor a previous page ended on, which is a whole number";

  public KeyedExchangeQuery {
    if (limit < 1 || limit > LARGEST_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + LARGEST_LIMIT);
    }
    if (after < 1) {
      throw new IllegalArgumentException(AFTER_NAMES_A_ROW_ALREADY_SEEN);
    }
  }

  public static KeyedExchangeQuery of(
      @Nullable String testRunId,
      @Nullable String after,
      @Nullable String limit,
      TestRunId whenTestRunAbsent) {
    return new KeyedExchangeQuery(
        testRunOf(testRunId, whenTestRunAbsent), afterOf(after), limitOf(limit));
  }

  private static TestRunId testRunOf(@Nullable String declared, TestRunId whenAbsent) {
    if (declared == null || declared.isBlank()) {
      return whenAbsent;
    }
    return TestRunId.storedNamespace(declared);
  }

  private static long afterOf(@Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return FROM_THE_NEWEST;
    }
    try {
      return Long.parseLong(declared.strip());
    } catch (NumberFormatException notANumber) {
      throw new IllegalArgumentException(AFTER_NAMES_A_ROW_ALREADY_SEEN);
    }
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
