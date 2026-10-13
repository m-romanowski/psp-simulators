package dev.marcinromanowski.pspsim.kernel.testrun;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class TestRunHeader {

  public static final String NAME = "X-Simulator-Test-Run-Id";

  private TestRunHeader() {}

  public static TestRunCandidates in(
      Map<String, List<String>> headers, Predicate<TestRunId> exists) {
    List<String> sent = valuesOf(headers);
    if (sent.isEmpty()) {
      return TestRunCandidates.notEvaluated(TestRunResolutionRule.TEST_HEADER);
    }
    Set<TestRunId> matched = new LinkedHashSet<>();
    for (String value : sent) {
      TestRunId named = testRunIdOf(value);
      if (named == null || !exists.test(named)) {
        return unmatched();
      }
      matched.add(named);
    }
    return TestRunCandidates.of(TestRunResolutionRule.TEST_HEADER, matched);
  }

  private static TestRunCandidates unmatched() {
    return TestRunCandidates.of(TestRunResolutionRule.TEST_HEADER, List.of());
  }

  private static List<String> valuesOf(Map<String, List<String>> headers) {
    List<String> values = new ArrayList<>();
    headers.forEach(
        (name, sent) -> {
          if (NAME.equalsIgnoreCase(name)) {
            values.addAll(sent);
          }
        });
    return values;
  }

  private static @Nullable TestRunId testRunIdOf(String value) {
    try {
      return TestRunId.of(value);
    } catch (IllegalArgumentException notATestRunId) {
      return null;
    }
  }
}
