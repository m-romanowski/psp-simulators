package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;

final class TranzilaNotifyAcknowledgement implements AcknowledgementPolicy {

  static final int THE_STATUS_THAT_ACKNOWLEDGES = 200;

  static final String THE_BODY_THAT_ACKNOWLEDGES = "OK";

  @Override
  public ProviderId provider() {
    return TranzilaApi.TRANZILA;
  }

  @Override
  public Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response) {
    return switch (response) {
      case MerchantResponse.Answered answered ->
          answered.httpStatus() == THE_STATUS_THAT_ACKNOWLEDGES
                  && THE_BODY_THAT_ACKNOWLEDGES.equals(answered.bodyPreview().strip())
              ? Acknowledgement.ACKNOWLEDGED
              : Acknowledgement.REFUSED;
      case MerchantResponse.NotAnswered _ -> Acknowledgement.REFUSED;
    };
  }
}
