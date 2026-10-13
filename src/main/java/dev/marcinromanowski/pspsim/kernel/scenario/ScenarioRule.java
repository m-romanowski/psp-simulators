package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;

public record ScenarioRule(
    String scenarioId,
    ProviderId provider,
    TestRunId testRunId,
    int priority,
    long arrival,
    ScenarioMatch match,
    ScenarioAction action,
    @Nullable ScenarioStory story,
    Instant createdAt) {

  public static final Comparator<ScenarioRule> THE_ORDER_THE_ENGINE_CONSULTS_THEM =
      Comparator.comparingInt(ScenarioRule::priority)
          .thenComparingInt(ScenarioRule::specificity)
          .thenComparingLong(ScenarioRule::arrival);

  public NewScenarioRule asWrittenAgain() {
    return new NewScenarioRule(provider, testRunId, priority, match, action, story);
  }

  public int specificity() {
    ScenarioStory step = story;
    return match.specificity() + (step != null && step.narrowsByState() ? 1 : 0);
  }
}
