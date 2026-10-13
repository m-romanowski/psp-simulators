package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;

final class GrowPersonas {

  static final String NO_INSTRUMENT_A_PERSONA_COULD_SEED =
      "a persona of this simulator seeds an instrument and hands over the provider-native ids that"
          + " instrument answers to, and provider grow reads none: "
          + GrowFixtureCards.A_CARD_TOKEN_IS_MINTED_BY_A_SAVED_PROCESS
          + ". A grow persona could still write this run's scenario rules and seed a card with no"
          + " token, and the call the persona exists for, createTransactionWithToken, would answer"
          + " err.id 140 for it, so the persona would hand a tester an instrument no grow endpoint"
          + " could charge. Press the preset on the Scenarios screen and take a card through the"
          + " hosted process instead, which is the only path grow publishes to a cardToken. "
          + GrowProcessObject.THE_TOKEN_A_SAVED_PROCESS_MINTED_IS_READ_FROM_ITS_OBJECT;

  private GrowPersonas() {}

  static ProviderPersonas of() {
    return ProviderPersonas.noneOfThem(GrowLightApi.GROW, NO_INSTRUMENT_A_PERSONA_COULD_SEED);
  }
}
