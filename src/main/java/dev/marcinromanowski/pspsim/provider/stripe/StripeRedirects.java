package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum StripeRedirects {
  ALWAYS("always"),
  NEVER("never");

  private final String wireValue;

  StripeRedirects(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  static Optional<StripeRedirects> of(String wireValue) {
    for (StripeRedirects redirects : values()) {
      if (redirects.wireValue.equals(wireValue)) {
        return Optional.of(redirects);
      }
    }
    return Optional.empty();
  }

  static List<String> published() {
    List<String> published = new ArrayList<>();
    for (StripeRedirects redirects : values()) {
      published.add(redirects.wireValue);
    }
    return List.copyOf(published);
  }
}
