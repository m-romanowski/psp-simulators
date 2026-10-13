package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptRendering;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.time.Instant;

final class AzericardCallbackRendering implements AttemptRendering {

  private final ProviderAccounts accounts;

  AzericardCallbackRendering(ProviderAccounts accounts) {
    this.accounts = accounts;
  }

  @Override
  public ProviderId provider() {
    return AzericardGateway.AZERICARD;
  }

  @Override
  public Rendering renderFor(WebhookDelivery delivery, String targetUrl, Instant attemptedAt) {
    for (WebhookTarget target :
        accounts.webhookTargetsOf(AzericardGateway.AZERICARD, delivery.testRunId())) {
      if (target.accountId().equals(delivery.merchantId()) && target.url().equals(targetUrl)) {
        return new Rendering.Sending(delivery.payload());
      }
    }
    return new Rendering.NothingThisProviderWouldSend(
        AzericardCallbacks.NOT_A_TARGET_OF_THE_ACCOUNT);
  }
}
