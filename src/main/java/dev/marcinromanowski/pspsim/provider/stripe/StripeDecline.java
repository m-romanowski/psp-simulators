package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

enum StripeDecline {
  GENERIC_DECLINE(
      "generic_decline", "card_declined", "generic_decline", "The card has been declined."),
  INSUFFICIENT_FUNDS(
      "insufficient_funds",
      "card_declined",
      "insufficient_funds",
      "The card has insufficient funds to complete the purchase."),
  EXPIRED_CARD(
      "expired_card",
      "expired_card",
      null,
      "The card has expired. Check the expiration date or use a different card."),
  INCORRECT_CVC("incorrect_cvc", "incorrect_cvc", null, null),
  PROCESSING_ERROR("processing_error", "processing_error", null, null),
  AUTHENTICATION_REQUIRED(
      "authentication_required",
      "authentication_required",
      "authentication_not_handled",
      "The payment requires authentication to proceed. If your customer is off session, notify"
          + " your customer to return to your application and complete the payment.");

  private final String scenarioCode;
  private final String errorCode;
  private final @Nullable String declineCode;
  private final @Nullable String publishedMessage;

  StripeDecline(
      String scenarioCode,
      String errorCode,
      @Nullable String declineCode,
      @Nullable String publishedMessage) {
    this.scenarioCode = scenarioCode;
    this.errorCode = errorCode;
    this.declineCode = declineCode;
    this.publishedMessage = publishedMessage;
  }

  String scenarioCode() {
    return scenarioCode;
  }

  String errorCode() {
    return errorCode;
  }

  @Nullable String declineCode() {
    return declineCode;
  }

  @Nullable String publishedMessage() {
    return publishedMessage;
  }

  static Optional<StripeDecline> of(String scenarioCode) {
    for (StripeDecline decline : values()) {
      if (decline.scenarioCode.equals(scenarioCode)) {
        return Optional.of(decline);
      }
    }
    return Optional.empty();
  }

  static String theCodesAScenarioMayName() {
    List<String> codes = new ArrayList<>();
    for (StripeDecline decline : values()) {
      codes.add(decline.scenarioCode);
    }
    return String.join("|", codes);
  }
}
