package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.List;

public record RetryPlan(List<Duration> delays) implements CallbackRetryPlans {

  public RetryPlan {
    if (delays.isEmpty()) {
      throw new IllegalArgumentException(
          "a retry plan needs one delay per attempt, and at least one");
    }
    delays = List.copyOf(delays);
  }

  public int attemptCap() {
    return delays.size();
  }

  public Duration delayBefore(int number) {
    return delays.get(number - 1);
  }

  public boolean hasAttemptAfter(int attemptsMade) {
    return attemptsMade < delays.size();
  }

  @Override
  public RetryPlan of(ProviderId provider, TestRunId testRunId) {
    return this;
  }
}
