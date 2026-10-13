package dev.marcinromanowski.pspsim.provider.stripe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum StripeEventType {
  AMOUNT_CAPTURABLE_UPDATED(
      "payment_intent.amount_capturable_updated", StripeEventSubject.PAYMENT_INTENT),
  SUCCEEDED("payment_intent.succeeded", StripeEventSubject.PAYMENT_INTENT),
  PAYMENT_FAILED("payment_intent.payment_failed", StripeEventSubject.PAYMENT_INTENT),
  CANCELED("payment_intent.canceled", StripeEventSubject.PAYMENT_INTENT),
  REFUND_CREATED("refund.created", StripeEventSubject.REFUND);

  private final String wireValue;
  private final StripeEventSubject subject;

  StripeEventType(String wireValue, StripeEventSubject subject) {
    this.wireValue = wireValue;
    this.subject = subject;
  }

  String wireValue() {
    return wireValue;
  }

  boolean reports(StripePaymentIntent intent, Instant now) {
    return switch (this) {
      case AMOUNT_CAPTURABLE_UPDATED -> intent.statusAt(now) == StripeIntentStatus.REQUIRES_CAPTURE;
      case SUCCEEDED -> intent.statusAt(now) == StripeIntentStatus.SUCCEEDED;
      case CANCELED -> intent.statusAt(now) == StripeIntentStatus.CANCELED;
      case PAYMENT_FAILED ->
          intent.statusAt(now) == StripeIntentStatus.REQUIRES_PAYMENT_METHOD
              && intent.lastPaymentErrorAt(now) != null;
      case REFUND_CREATED -> false;
    };
  }

  boolean reportsACreatedRefund() {
    return this == REFUND_CREATED;
  }

  static Optional<StripeEventType> of(String wireValue) {
    for (StripeEventType type : values()) {
      if (type.wireValue.equals(wireValue)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  static Optional<StripeEventType> theOneReporting(StripePaymentIntent intent, Instant now) {
    for (StripeEventType type : values()) {
      if (type.reports(intent, now)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  static String thePublishedNames() {
    List<String> names = new ArrayList<>();
    for (StripeEventType type : values()) {
      names.add(type.wireValue);
    }
    return String.join(", ", names);
  }

  static String thePublishedNamesOf(StripeEventSubject subject) {
    List<String> names = new ArrayList<>();
    for (StripeEventType type : values()) {
      if (type.subject == subject) {
        names.add(type.wireValue);
      }
    }
    return String.join(", ", names);
  }
}
