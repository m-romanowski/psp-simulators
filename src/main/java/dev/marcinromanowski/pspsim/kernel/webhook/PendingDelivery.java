package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

public record PendingDelivery(
    ProviderId provider, TestRunId testRunId, String deliveryId, Instant nextAttemptAt) {

  public boolean isDueAt(Instant now) {
    return !nextAttemptAt.isAfter(now);
  }
}
