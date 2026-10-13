package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptRendering;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class StripeWebhookRendering implements AttemptRendering {

  static final String NO_ACCOUNT_TO_SIGN_IT =
      "provider stripe signs a delivery with the webhook signing secret of the account the event"
          + " belongs to, read afresh at each attempt, and that account is no longer configured in"
          + " this test run. Sending the event now would post unsigned what was configured signed,"
          + " which is something Stripe never does, so the simulator sends nothing. Configure the"
          + " account again and attempt this delivery to send it";

  private final StripeAccounts accounts;

  StripeWebhookRendering(StripeAccounts accounts) {
    this.accounts = accounts;
  }

  @Override
  public ProviderId provider() {
    return StripeApi.STRIPE;
  }

  @Override
  public Rendering renderFor(WebhookDelivery delivery, String targetUrl, Instant attemptedAt) {
    Optional<StripeAccount> account = accounts.find(delivery.testRunId(), delivery.merchantId());
    if (account.isEmpty()) {
      return new Rendering.NothingThisProviderWouldSend(NO_ACCOUNT_TO_SIGN_IT);
    }
    StripeWebhookSigning signing = account.get().webhookSigning();
    WebhookPayload stored = delivery.payload();
    String secret = signing.secret();
    if (secret == null) {
      return new Rendering.Sending(stored);
    }
    byte[] body = stored.body();
    return new Rendering.Sending(
        new WebhookPayload(
            stored.contentType(),
            Map.of(
                StripeSignature.HEADER,
                List.of(
                    StripeSignature.of(
                        secret, signing.emission(), signing.timestampFor(attemptedAt), body))),
            body,
            stored.sanitizedBody()));
  }
}
