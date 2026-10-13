package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;

final class AzericardCallbackAcknowledgement implements AcknowledgementPolicy {

  private static final int LOWEST_SUCCESS = 200;

  private static final int BELOW_REDIRECTION = 300;

  @Override
  public ProviderId provider() {
    return AzericardGateway.AZERICARD;
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
    return httpStatus >= LOWEST_SUCCESS && httpStatus < BELOW_REDIRECTION;
  }
}
