package dev.marcinromanowski.pspsim.kernel.webhook;

public interface MerchantEndpoint {

  MerchantResponse post(String url, WebhookPayload payload);
}
