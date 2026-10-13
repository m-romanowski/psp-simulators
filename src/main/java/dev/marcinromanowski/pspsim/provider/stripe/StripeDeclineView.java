package dev.marcinromanowski.pspsim.provider.stripe;

import org.jspecify.annotations.Nullable;

final class StripeDeclineView {

  private StripeDeclineView() {}

  static StripeJson of(@Nullable StripeDecline decline) {
    if (decline == null) {
      return StripeJson.nothing();
    }
    String declineCode = decline.declineCode();
    if (declineCode == null) {
      return StripeJson.object(
          "code",
          StripeJson.text(decline.errorCode()),
          "type",
          StripeJson.text(StripeErrorType.CARD_ERROR.wireValue()));
    }
    return StripeJson.object(
        "code",
        StripeJson.text(decline.errorCode()),
        "decline_code",
        StripeJson.text(declineCode),
        "type",
        StripeJson.text(StripeErrorType.CARD_ERROR.wireValue()));
  }
}
