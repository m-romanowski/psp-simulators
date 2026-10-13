package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

@FunctionalInterface
public interface CallbackRetryPlans {

  RetryPlan of(ProviderId provider, TestRunId testRunId);
}
