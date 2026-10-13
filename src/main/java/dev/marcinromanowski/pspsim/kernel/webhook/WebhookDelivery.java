package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record WebhookDelivery(
    String deliveryId,
    ProviderId provider,
    TestRunId testRunId,
    String merchantId,
    String eventId,
    @Nullable String eventType,
    @Nullable String providerObjectId,
    @Nullable String targetUrl,
    WebhookPayload payload,
    RetryPlan retryPlan,
    DeliveryState state,
    boolean acknowledgementOwed,
    int attemptsMade,
    @Nullable Instant nextAttemptAt,
    Instant createdAt,
    Instant updatedAt,
    @Nullable String shapedBy,
    @Nullable String notSentBecause) {

  public static final String HAS_NO_TARGET =
      "no target: the provider found none to post this callback to";

  public WebhookDelivery {
    if (targetUrl == null && state != DeliveryState.UNDELIVERABLE) {
      throw new TargetlessDeliveryIsTerminalException();
    }
  }

  public boolean canBeAttempted() {
    return targetUrl != null;
  }

  public boolean isRetargetedBy(@Nullable String instead) {
    return instead != null && !instead.equals(targetUrl);
  }

  public String theTargetOfAnAttempt(@Nullable String instead) {
    if (targetUrl == null) {
      throw new DeliveryHasNoTargetException(providerObjectId);
    }
    return instead == null ? targetUrl : instead;
  }

  public boolean isDueAt(Instant now) {
    return state == DeliveryState.PENDING && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
  }
}
