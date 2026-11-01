package dev.marcinromanowski.pspsim.provider.stripe;

import org.jspecify.annotations.Nullable;

enum StripeTestCardBehavior {
  GENERIC_DECLINE(StripeDecline.GENERIC_DECLINE, true),
  INSUFFICIENT_FUNDS(StripeDecline.INSUFFICIENT_FUNDS, true),
  LOST_CARD(StripeDecline.LOST_CARD, true),
  STOLEN_CARD(StripeDecline.STOLEN_CARD, true),
  EXPIRED_CARD(StripeDecline.EXPIRED_CARD, true),
  INCORRECT_CVC(StripeDecline.INCORRECT_CVC, true),
  PROCESSING_ERROR(StripeDecline.PROCESSING_ERROR, true),
  VELOCITY_LIMIT(StripeDecline.CARD_VELOCITY_EXCEEDED, true),
  CHARGE_CUSTOMER_FAIL(StripeDecline.GENERIC_DECLINE, false);

  private final StripeDecline decline;
  private final boolean refusesSetup;

  StripeTestCardBehavior(StripeDecline decline, boolean refusesSetup) {
    this.decline = decline;
    this.refusesSetup = refusesSetup;
  }

  @Nullable StripeDecline outcomeOf(String operation) {
    return switch (operation) {
      case StripeOperations.CREATE, StripeOperations.CONFIRM -> decline;
      case StripeOperations.CONFIRM_SETUP_INTENT -> refusesSetup ? decline : null;
      default -> null;
    };
  }

  static @Nullable StripeTestCardBehavior storedAs(@Nullable String value) {
    return value == null ? null : valueOf(value);
  }

  static @Nullable StripeTestCardBehavior ofNumber(String number) {
    return switch (number) {
      case "4000000000000002" -> GENERIC_DECLINE;
      case "4000000000009995" -> INSUFFICIENT_FUNDS;
      case "4000000000009987" -> LOST_CARD;
      case "4000000000009979" -> STOLEN_CARD;
      case "4000000000000069" -> EXPIRED_CARD;
      case "4000000000000127" -> INCORRECT_CVC;
      case "4000000000000119" -> PROCESSING_ERROR;
      case "4000000000006975" -> VELOCITY_LIMIT;
      case "4000000000000341" -> CHARGE_CUSTOMER_FAIL;
      default -> null;
    };
  }
}
