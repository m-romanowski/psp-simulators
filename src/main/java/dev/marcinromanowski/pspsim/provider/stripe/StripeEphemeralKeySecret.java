package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;

final class StripeEphemeralKeySecret {

  static final String THE_KEY_FAMILY_PREFIX_WITH_SIM_WHERE_STRIPE_WRITES_TEST = "ek_sim_";

  private static final int RANDOM_LENGTH = 32;

  private StripeEphemeralKeySecret() {}

  static String drawnApartFromTheKeysOwnId(IdGenerator ids) {
    return THE_KEY_FAMILY_PREFIX_WITH_SIM_WHERE_STRIPE_WRITES_TEST + ids.hex(RANDOM_LENGTH);
  }
}
