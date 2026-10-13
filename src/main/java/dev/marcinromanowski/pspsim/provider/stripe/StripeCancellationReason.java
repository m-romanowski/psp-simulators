package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum StripeCancellationReason {
  DUPLICATE("duplicate", true),
  FRAUDULENT("fraudulent", true),
  REQUESTED_BY_CUSTOMER("requested_by_customer", true),
  ABANDONED("abandoned", true),
  EXPIRED("expired", false);

  private final String wireValue;
  private final boolean aCallerMaySendIt;

  StripeCancellationReason(String wireValue, boolean aCallerMaySendIt) {
    this.wireValue = wireValue;
    this.aCallerMaySendIt = aCallerMaySendIt;
  }

  String wireValue() {
    return wireValue;
  }

  boolean aCallerMaySendIt() {
    return aCallerMaySendIt;
  }

  static Optional<StripeCancellationReason> sentByACaller(String wireValue) {
    return of(wireValue).filter(StripeCancellationReason::aCallerMaySendIt);
  }

  static Optional<StripeCancellationReason> of(String wireValue) {
    for (StripeCancellationReason reason : values()) {
      if (reason.wireValue.equals(wireValue)) {
        return Optional.of(reason);
      }
    }
    return Optional.empty();
  }

  static String theReasonsACallerMaySend() {
    List<String> names = new ArrayList<>();
    for (StripeCancellationReason reason : values()) {
      if (reason.aCallerMaySendIt) {
        names.add(reason.wireValue);
      }
    }
    return String.join(", ", names);
  }
}
