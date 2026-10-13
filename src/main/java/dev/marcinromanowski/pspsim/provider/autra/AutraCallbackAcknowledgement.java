package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;

final class AutraCallbackAcknowledgement implements AcknowledgementPolicy {

  static final int LOWEST_DELIVERED = 200;

  static final int BELOW_REDIRECTION = 300;

  static final String ONLY_A_2XX_COUNTS =
      "provider autra counts a delivery as made on a 2xx and re-queues everything else, a timeout"
          + " included: \"Somente 2xx conta como entregue\"";

  @Override
  public ProviderId provider() {
    return AutraApi.AUTRA;
  }

  @Override
  public Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response) {
    return switch (response) {
      case MerchantResponse.Answered answered ->
          wasDelivered(answered.httpStatus())
              ? Acknowledgement.ACKNOWLEDGED
              : Acknowledgement.REFUSED;
      case MerchantResponse.NotAnswered _ -> Acknowledgement.REFUSED;
    };
  }

  static boolean wasDelivered(int httpStatus) {
    return httpStatus >= LOWEST_DELIVERED && httpStatus < BELOW_REDIRECTION;
  }
}
