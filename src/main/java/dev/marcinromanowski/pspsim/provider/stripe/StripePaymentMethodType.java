package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

enum StripePaymentMethodType {
  CARD("card", false),
  PAYPAL("paypal", true);

  private final String wireValue;
  private final boolean redirects;

  StripePaymentMethodType(String wireValue, boolean redirects) {
    this.wireValue = wireValue;
    this.redirects = redirects;
  }

  String wireValue() {
    return wireValue;
  }

  boolean describesACard() {
    return this == CARD;
  }

  boolean redirects() {
    return redirects;
  }

  boolean presentsIn(@Nullable String currency) {
    if (currency == null) {
      return true;
    }
    return switch (this) {
      case CARD -> true;
      case PAYPAL ->
          List.of(
                  "aud", "cad", "chf", "czk", "dkk", "eur", "gbp", "hkd", "nok", "nzd", "pln",
                  "sek", "sgd", "usd")
              .contains(currency.toLowerCase(Locale.ROOT));
    };
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
