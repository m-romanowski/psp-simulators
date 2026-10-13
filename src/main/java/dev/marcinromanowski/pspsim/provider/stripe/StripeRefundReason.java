package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum StripeRefundReason {
  DUPLICATE("duplicate", true),
  FRAUDULENT("fraudulent", true),
  REQUESTED_BY_CUSTOMER("requested_by_customer", true),
  EXPIRED_UNCAPTURED_CHARGE("expired_uncaptured_charge", false);

  private final String wireValue;
  private final boolean aCallerMaySendIt;

  StripeRefundReason(String wireValue, boolean aCallerMaySendIt) {
    this.wireValue = wireValue;
    this.aCallerMaySendIt = aCallerMaySendIt;
  }

  String wireValue() {
    return wireValue;
  }

  boolean aCallerMaySendIt() {
    return aCallerMaySendIt;
  }

  static Optional<StripeRefundReason> sentByACaller(String wireValue) {
    return of(wireValue).filter(StripeRefundReason::aCallerMaySendIt);
  }

  static Optional<StripeRefundReason> of(String wireValue) {
    for (StripeRefundReason reason : values()) {
      if (reason.wireValue.equals(wireValue)) {
        return Optional.of(reason);
      }
    }
    return Optional.empty();
  }

  static String theReasonsACallerMaySend() {
    List<String> names = new ArrayList<>();
    for (StripeRefundReason reason : values()) {
      if (reason.aCallerMaySendIt) {
        names.add(reason.wireValue);
      }
    }
    return String.join(", ", names);
  }
}
