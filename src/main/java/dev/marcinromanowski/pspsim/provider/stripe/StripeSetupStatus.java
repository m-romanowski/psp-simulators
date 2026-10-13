package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;

enum StripeSetupStatus {
  REQUIRES_PAYMENT_METHOD("requires_payment_method", NormalizedStatus.NOT_INITIALIZED, true),
  REQUIRES_CONFIRMATION("requires_confirmation", NormalizedStatus.NOT_INITIALIZED, true),
  REQUIRES_ACTION("requires_action", NormalizedStatus.NOT_INITIALIZED, true),
  PROCESSING("processing", NormalizedStatus.PROCESSING, false),
  CANCELED("canceled", NormalizedStatus.CANCELLED, false),
  SUCCEEDED("succeeded", NormalizedStatus.SUCCEEDED, false);

  private final String wireValue;
  private final NormalizedStatus normalized;
  private final boolean confirmable;

  StripeSetupStatus(String wireValue, NormalizedStatus normalized, boolean confirmable) {
    this.wireValue = wireValue;
    this.normalized = normalized;
    this.confirmable = confirmable;
  }

  String wireValue() {
    return wireValue;
  }

  NormalizedStatus normalized() {
    return normalized;
  }

  boolean confirmable() {
    return confirmable;
  }
}
