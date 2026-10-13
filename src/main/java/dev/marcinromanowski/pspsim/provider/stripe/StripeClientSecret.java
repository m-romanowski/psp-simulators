package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;

final class StripeClientSecret {

  static final String INFIX = "_secret_";

  private static final int RANDOM_LENGTH = 32;

  private StripeClientSecret() {}

  static String of(String intentId, IdGenerator ids) {
    return intentId + INFIX + ids.hex(RANDOM_LENGTH);
  }
}
