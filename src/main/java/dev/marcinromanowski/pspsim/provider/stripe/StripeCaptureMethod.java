package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum StripeCaptureMethod {
  AUTOMATIC("automatic"),
  AUTOMATIC_ASYNC("automatic_async"),
  MANUAL("manual");

  static final StripeCaptureMethod PUBLISHED_DEFAULT = AUTOMATIC_ASYNC;

  private final String wireValue;

  StripeCaptureMethod(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  boolean holdsTheFundsForALaterCapture() {
    return this == MANUAL;
  }

  StripeIntentStatus afterASuccessfulConfirmation() {
    return holdsTheFundsForALaterCapture()
        ? StripeIntentStatus.REQUIRES_CAPTURE
        : StripeIntentStatus.SUCCEEDED;
  }

  static String theMethodsTheVendorPublishes() {
    List<String> names = new ArrayList<>();
    for (StripeCaptureMethod method : values()) {
      names.add(method.wireValue);
    }
    return String.join(", ", names);
  }

  static Optional<StripeCaptureMethod> of(String wireValue) {
    for (StripeCaptureMethod method : values()) {
      if (method.wireValue.equals(wireValue)) {
        return Optional.of(method);
      }
    }
    return Optional.empty();
  }
}
