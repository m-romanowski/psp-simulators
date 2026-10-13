package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import java.util.Optional;

enum StripePaymentMethodType {
  CARD("card"),
  PAYPAL("paypal");

  private final String wireValue;

  StripePaymentMethodType(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  boolean describesACard() {
    return this == CARD;
  }

  static Optional<StripePaymentMethodType> of(String wireValue) {
    for (StripePaymentMethodType type : values()) {
      if (type.wireValue.equals(wireValue)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  static Optional<StripePaymentMethodType> seededAs(PaymentMethodKind kind) {
    return switch (kind) {
      case CARD -> Optional.of(CARD);
      case WALLET -> Optional.of(PAYPAL);
      case PSP_TOKEN -> Optional.empty();
    };
  }
}
