package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;

public interface AcknowledgementPolicy {

  ProviderId provider();

  Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response);
}
