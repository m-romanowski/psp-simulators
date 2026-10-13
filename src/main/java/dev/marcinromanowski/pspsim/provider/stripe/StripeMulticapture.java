package dev.marcinromanowski.pspsim.provider.stripe;

final class StripeMulticapture {

  static final int PUBLISHED_NON_FINAL_CAPTURE_CEILING = 50;

  static final String CEILING_IS_REACHED =
      "Stripe allows up to "
          + PUBLISHED_NON_FINAL_CAPTURE_CEILING
          + " non-final captures for a single PaymentIntent, and this PaymentIntent has taken all"
          + " of them. One further capture is allowed and it must be the final one, so send this"
          + " request without "
          + StripeFields.FINAL_CAPTURE
          + " or with it set to "
          + StripeFlag.TRUE;

  private StripeMulticapture() {}

  static boolean hasRoomForANonFinalCapture(StripePaymentIntent intent) {
    return intent.nonFinalCaptures() < PUBLISHED_NON_FINAL_CAPTURE_CEILING;
  }
}
