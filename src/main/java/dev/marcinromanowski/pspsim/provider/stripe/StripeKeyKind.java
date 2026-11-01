package dev.marcinromanowski.pspsim.provider.stripe;

enum StripeKeyKind {
  SECRET_KEY,
  SHORT_LIVED_KEY,
  PUBLISHABLE_KEY;

  StripeJson asShownTo(StripeJson answered) {
    return this == PUBLISHABLE_KEY ? StripePublishableView.of(answered) : answered;
  }
}
