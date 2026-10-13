package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.Optional;

enum StripeChargeStatus {
  SUCCEEDED("succeeded"),
  FAILED("failed");

  private final String wireValue;

  StripeChargeStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  boolean paid() {
    return this == SUCCEEDED;
  }

  static Optional<StripeChargeStatus> of(String wireValue) {
    for (StripeChargeStatus status : values()) {
      if (status.wireValue.equals(wireValue)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }
}
