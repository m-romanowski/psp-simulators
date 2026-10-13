package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.util.List;
import java.util.Optional;

enum StripeSignatureEmission {
  VALID,
  CORRUPTED;

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            VALID.name(), "a delivered event is signed so your own Stripe-Signature check passes"),
        new AcceptedValue(
            CORRUPTED.name(),
            "a delivered event carries a v1 element that cannot verify, so your check has"
                + " something to refuse"));
  }

  static Optional<StripeSignatureEmission> of(String name) {
    for (StripeSignatureEmission emission : values()) {
      if (emission.name().equals(name)) {
        return Optional.of(emission);
      }
    }
    return Optional.empty();
  }

  static StripeSignatureEmission stored(String name) {
    return of(name)
        .orElseThrow(
            () ->
                new ProviderConfigurationRejectedException(
                    "the stripe_account store holds a signature emission of " + name));
  }
}
