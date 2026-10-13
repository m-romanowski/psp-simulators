package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;

final class GrowCallbackAcknowledgement implements AcknowledgementPolicy {

  private final GrowAccounts accounts;

  GrowCallbackAcknowledgement(GrowAccounts accounts) {
    this.accounts = accounts;
  }

  @Override
  public ProviderId provider() {
    return GrowLightApi.GROW;
  }

  @Override
  public Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response) {
    return switch (response) {
      case MerchantResponse.Answered answered ->
          isSuccessful(answered.httpStatus()) ? received(delivery) : Acknowledgement.REFUSED;
      case MerchantResponse.NotAnswered _ -> Acknowledgement.REFUSED;
    };
  }

  private Acknowledgement received(WebhookDelivery delivery) {
    return modeOf(delivery) == GrowAcknowledgementMode.APPROVE_TRANSACTION
        ? Acknowledgement.AWAITING_SEPARATE_CALL
        : Acknowledgement.ACKNOWLEDGED;
  }

  private GrowAcknowledgementMode modeOf(WebhookDelivery delivery) {
    return accounts
        .find(delivery.testRunId(), delivery.merchantId())
        .map(GrowAccount::acknowledgement)
        .orElse(GrowAcknowledgementMode.HTTP_2XX);
  }

  private static boolean isSuccessful(int httpStatus) {
    return httpStatus >= 200 && httpStatus < 300;
  }
}
