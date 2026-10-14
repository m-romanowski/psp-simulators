package dev.marcinromanowski.pspsim.provider.azericard;

import java.util.function.ToIntFunction;

final class AzericardObservation {

  static final int ATTEMPTS = 100;

  private AzericardObservation() {}

  static void recorded(
      ToIntFunction<AzericardObservedRequest> observe, AzericardObservedRequest request) {
    for (int attempt = 1; observe.applyAsInt(request) != 1; attempt++) {
      if (attempt >= ATTEMPTS) {
        throw new IllegalStateException(
            "the exchange under "
                + request.keyName()
                + " could not be recorded in "
                + ATTEMPTS
                + " attempts, each of which lost its sequence number to another request");
      }
    }
  }
}
