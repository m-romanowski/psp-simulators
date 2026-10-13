package dev.marcinromanowski.pspsim.kernel.testrun;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record TestRunCandidates(
    TestRunResolutionRule rule, boolean evaluated, Set<TestRunId> testRunIds) {

  public TestRunCandidates {
    if (!rule.producesCandidates()) {
      throw new IllegalArgumentException(
          "rule must be one of the candidate-producing rules of doc/README.md, not " + rule.name());
    }
    if (!evaluated && !testRunIds.isEmpty()) {
      throw new IllegalArgumentException(
          "a rule that was not evaluated has no candidates: " + rule.name());
    }
    testRunIds = Collections.unmodifiableSet(new LinkedHashSet<>(testRunIds));
  }

  public static TestRunCandidates of(TestRunResolutionRule rule, Collection<TestRunId> testRunIds) {
    return new TestRunCandidates(rule, true, new LinkedHashSet<>(testRunIds));
  }

  public static TestRunCandidates notEvaluated(TestRunResolutionRule rule) {
    return new TestRunCandidates(rule, false, Set.of());
  }

  public boolean matchedNothing() {
    return evaluated && testRunIds.isEmpty();
  }

  public boolean isAmbiguous() {
    return testRunIds.size() > 1;
  }
}
