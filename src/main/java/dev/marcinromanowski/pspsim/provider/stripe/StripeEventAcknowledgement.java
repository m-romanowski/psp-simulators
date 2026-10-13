package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;

final class StripeEventAcknowledgement implements AcknowledgementPolicy {

  @Override
  public ProviderId provider() {
    return StripeApi.STRIPE;
  }

  @Override
  public Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response) {
    return switch (response) {
      case MerchantResponse.Answered answered ->
          isSuccessful(answered.httpStatus())
              ? Acknowledgement.ACKNOWLEDGED
              : Acknowledgement.REFUSED;
      case MerchantResponse.NotAnswered _ -> Acknowledgement.REFUSED;
    };
  }

  private static boolean isSuccessful(int httpStatus) {
    return httpStatus >= 200 && httpStatus < 300;
  }
}
