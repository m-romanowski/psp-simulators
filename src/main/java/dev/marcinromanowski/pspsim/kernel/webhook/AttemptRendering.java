package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.time.Instant;

public interface AttemptRendering {

  sealed interface Rendering {

    record Sending(WebhookPayload payload) implements Rendering {}

    record NothingThisProviderWouldSend(String reason) implements Rendering {}
  }

  ProviderId provider();

  Rendering renderFor(WebhookDelivery delivery, String targetUrl, Instant attemptedAt);
}
