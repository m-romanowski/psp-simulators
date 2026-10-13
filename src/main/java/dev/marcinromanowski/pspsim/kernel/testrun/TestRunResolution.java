package dev.marcinromanowski.pspsim.kernel.testrun;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule.WhenNothingMatched;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record TestRunResolution(TestRunId testRunId, TestRunResolutionRule rule) {

  public TestRunResolution {
    if (rule.namesATestRun() == TestRunId.UNKNOWN.equals(testRunId)) {
      throw new IllegalArgumentException(
          "only a verdict that named no test run carries the reserved namespace, and it always"
              + " does: "
              + rule.name());
    }
  }

  public static TestRunResolution of(List<TestRunCandidates> candidates, TestRunId fallback) {
    Map<TestRunResolutionRule, TestRunCandidates> byRule = new LinkedHashMap<>();
    for (TestRunCandidates evaluated : candidates) {
      TestRunCandidates clash = byRule.put(evaluated.rule(), evaluated);
      if (clash != null) {
        throw new IllegalArgumentException(
            "a rule may be evaluated once per request: " + evaluated.rule().name());
      }
    }
    for (TestRunResolutionRule rule : TestRunResolutionRule.byPrecedence()) {
      TestRunCandidates seen = byRule.get(rule);
      if (seen == null || !seen.evaluated()) {
        continue;
      }
      if (seen.isAmbiguous()) {
        return new TestRunResolution(TestRunId.UNKNOWN, TestRunResolutionRule.AMBIGUOUS);
      }
      if (seen.matchedNothing()) {
        switch (rule.whenNothingMatched()) {
          case WhenNothingMatched.TheNextRuleDecides _ -> {
            continue;
          }
          case WhenNothingMatched.ResolutionFails failed -> {
            return new TestRunResolution(TestRunId.UNKNOWN, failed.verdict());
          }
        }
      }
      return new TestRunResolution(seen.testRunIds().iterator().next(), rule);
    }
    return new TestRunResolution(fallback, TestRunResolutionRule.DEFAULT);
  }

  public Optional<TestRunId> resolvedTestRun() {
    return rule.namesATestRun() ? Optional.of(testRunId) : Optional.empty();
  }
}
