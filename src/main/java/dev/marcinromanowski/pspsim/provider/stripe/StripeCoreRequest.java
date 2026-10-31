package dev.marcinromanowski.pspsim.provider.stripe;

import org.jspecify.annotations.Nullable;

record StripeCoreRequest(StripeForm form, @Nullable StripeParameter writtenPaymentMethodTypes) {

  static StripeCoreRequest asSent(StripeForm form) {
    return new StripeCoreRequest(form, null);
  }
}
