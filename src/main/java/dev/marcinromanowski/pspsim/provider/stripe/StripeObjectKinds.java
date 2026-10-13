package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;

final class StripeObjectKinds {

  static final ProviderObjectKind A_HOLD =
      new ProviderObjectKind(
          "a hold on a card",
          "A PaymentIntent whose capture_method is manual: confirmed, it stands at requires_capture"
              + " and holds the funds until a capture or a cancel moves them, and unconfirmed it"
              + " holds nothing yet.");

  static final ProviderObjectKind A_CHARGE =
      new ProviderObjectKind(
          "a card charged in one call",
          "A PaymentIntent whose capture_method takes the funds as soon as it is confirmed, so it"
              + " goes straight to succeeded with no hold left to capture, and what is offered on"
              + " it afterwards is a refund.");

  static final ProviderObjectKind A_REFUND =
      new ProviderObjectKind(
          "a refund",
          "A refund stripe created against a PaymentIntent. Stripe lists it as an object of its"
              + " own, it moves no further money, and the intent it returned the money from is"
              + " named beside it.");

  static final ProviderObjectKind A_CUSTOMER =
      new ProviderObjectKind(
          "a customer",
          "A customer stripe holds. It carries no amount and no operation list: it is who a saved"
              + " card belongs to and who a later payment is charged for.");

  static final ProviderObjectKind A_SAVED_CARD =
      new ProviderObjectKind(
          "a saved card",
          "A card stripe holds as a PaymentMethod. It moves no money itself; a payment that charges"
              + " it begins at a PaymentIntent naming it.");

  static final ProviderObjectKind A_CARD_BEING_SET_UP =
      new ProviderObjectKind(
          "a card being set up for later",
          "A SetupIntent. It moves no money ever: stripe publishes it as the object that sets a"
              + " card up for future payments, and confirming it is what attaches that card to a"
              + " customer.");

  private StripeObjectKinds() {}

  static ProviderObjectKind of(StripePaymentIntent intent) {
    return intent.captureMethod().holdsTheFundsForALaterCapture() ? A_HOLD : A_CHARGE;
  }

  static ProviderObjectKind ofARefund() {
    return A_REFUND;
  }

  static ProviderObjectKind ofACustomer() {
    return A_CUSTOMER;
  }

  static ProviderObjectKind ofASavedCard() {
    return A_SAVED_CARD;
  }

  static ProviderObjectKind ofACardBeingSetUp() {
    return A_CARD_BEING_SET_UP;
  }
}
