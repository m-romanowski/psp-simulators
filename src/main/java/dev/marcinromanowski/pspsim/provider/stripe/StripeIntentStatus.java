package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.util.Optional;

enum StripeIntentStatus {
  REQUIRES_PAYMENT_METHOD("requires_payment_method", NormalizedStatus.NOT_INITIALIZED, true, true),
  REQUIRES_CONFIRMATION("requires_confirmation", NormalizedStatus.NOT_INITIALIZED, true, true),
  REQUIRES_ACTION("requires_action", NormalizedStatus.NOT_INITIALIZED, true, true),
  PROCESSING("processing", NormalizedStatus.PROCESSING, false, false),
  REQUIRES_CAPTURE("requires_capture", NormalizedStatus.READY_FOR_CAPTURE, false, true),
  SUCCEEDED("succeeded", NormalizedStatus.SUCCEEDED, false, false),
  CANCELED("canceled", NormalizedStatus.CANCELLED, false, false);

  private final String wireValue;
  private final NormalizedStatus normalized;
  private final boolean confirmable;
  private final boolean cancellable;

  StripeIntentStatus(
      String wireValue, NormalizedStatus normalized, boolean confirmable, boolean cancellable) {
    this.wireValue = wireValue;
    this.normalized = normalized;
    this.confirmable = confirmable;
    this.cancellable = cancellable;
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

  boolean cancellable() {
    return cancellable;
  }

  static Optional<StripeIntentStatus> of(String wireValue) {
    for (StripeIntentStatus status : values()) {
      if (status.wireValue.equals(wireValue)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }
}
