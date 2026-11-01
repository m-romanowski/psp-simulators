package dev.marcinromanowski.pspsim.provider.stripe;

import org.jspecify.annotations.Nullable;

final class StripeRefundView {

  static final String OBJECT = "refund";

  static final String A_REFUND_NAMES_THE_CHARGE_THE_MONEY_IS_ON =
      "a refund of this simulator names the charge its payment last minted, which is the one a"
          + " confirmation put the funds on; the published nullable charge is answered as null"
          + " only for a refund taken before this build minted charges at all";

  private StripeRefundView() {}

  static StripeJson of(StripeRefund refund) {
    return StripeJson.object(
        "id",
        StripeJson.text(refund.refundId()),
        "object",
        StripeJson.text(OBJECT),
        "amount",
        StripeJson.number(refund.amount().value()),
        "charge",
        StripeJson.textOrNull(refund.chargeId()),
        "created",
        StripeJson.number(Long.toString(refund.createdAt().getEpochSecond())),
        "currency",
        StripeJson.text(refund.currency()),
        "payment_intent",
        StripeJson.text(refund.intentId()),
        "reason",
        reasonOf(refund.reason()),
        "status",
        StripeJson.text(refund.status().wireValue()));
  }

  private static StripeJson reasonOf(@Nullable StripeRefundReason reason) {
    return reason == null ? StripeJson.nothing() : StripeJson.text(reason.wireValue());
  }
}
