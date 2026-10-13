package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum StripeSetupUsage {
  ON_SESSION("on_session"),
  OFF_SESSION("off_session");

  static final StripeSetupUsage PUBLISHED_DEFAULT = OFF_SESSION;

  private final String wireValue;

  StripeSetupUsage(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  static Optional<StripeSetupUsage> of(String wireValue) {
    for (StripeSetupUsage usage : values()) {
      if (usage.wireValue.equals(wireValue)) {
        return Optional.of(usage);
      }
    }
    return Optional.empty();
  }

  static String theUsagesTheVendorPublishes() {
    List<String> published = new ArrayList<>();
    for (StripeSetupUsage usage : values()) {
      published.add(usage.wireValue);
    }
    return String.join("|", published);
  }
}
