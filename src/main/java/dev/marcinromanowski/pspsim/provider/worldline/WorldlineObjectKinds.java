package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;

final class WorldlineObjectKinds {

  static final ProviderObjectKind A_HOLD =
      new ProviderObjectKind(
          "a hold on a card",
          "Worldline \"Only blocks the order.amountOfMoney.amount up to 30 days\" and charges"
              + " nothing yet, and under this authorisation mode it takes a capture or a cancel for"
              + " part of the amount as readily as for all of it.");

  static final ProviderObjectKind A_HOLD_TAKEN_ONLY_IN_FULL =
      new ProviderObjectKind(
          "a hold captured only in full",
          "Worldline \"Only blocks the order.amountOfMoney.amount for 7 days\" and charges nothing"
              + " yet, and under this authorisation mode it \"Supports only full captures\" and"
              + " cancels only the whole of it.");

  static final ProviderObjectKind A_CHARGE =
      new ProviderObjectKind(
          "a card charged in one call",
          "Worldline \"Blocks and captures the order.amountOfMoney.amount in one go\", so the money"
              + " is taken already and there is no hold left to capture or cancel.");

  static final ProviderObjectKind A_TOKENIZATION =
      new ProviderObjectKind(
          "a hosted card page",
          "A card page worldline hosts for a payer to type a card on, with a life span of three"
              + " hours. No money moves on it: what finishing it leaves behind is a token a"
              + " payment charges.");

  private WorldlineObjectKinds() {}

  static ProviderObjectKind of(WorldlinePayment payment) {
    return switch (payment.authorizationMode()) {
      case PRE_AUTHORIZATION -> A_HOLD;
      case FINAL_AUTHORIZATION -> A_HOLD_TAKEN_ONLY_IN_FULL;
      case SALE -> A_CHARGE;
    };
  }

  static ProviderObjectKind ofATokenization() {
    return A_TOKENIZATION;
  }
}
