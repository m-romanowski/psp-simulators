package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;

final class AzericardObjectKinds {

  static final ProviderObjectKind A_HOLD =
      new ProviderObjectKind(
          "a hold on a card",
          "Azericard held the funds on the card and moved none of them: TRTYPE=0 holds them, and"
              + " completing it later for the whole amount or part of it is what takes the money.");

  static final ProviderObjectKind A_CHARGE =
      new ProviderObjectKind(
          "a card charged in one call",
          "Azericard took the funds in the same call it authorized them: TRTYPE=1 leaves no hold to"
              + " complete, and what the screen offers on it afterwards is a reversal that returns"
              + " the funds.");

  static final ProviderObjectKind A_SAVED_CARD =
      new ProviderObjectKind(
          "a saved card",
          "A card azericard kept after a payer typed it on a page azericard hosts, held as a token"
              + " a later authorization charges without the payer being there.");

  static final ProviderObjectKind A_PAYER_PAGE =
      new ProviderObjectKind(
          "a hosted payer page",
          "A payment page azericard hosts for a payer to type a card on. The payment itself does"
              + " not exist until that page is finished, so this object carries the amount the"
              + " page was opened for and no money movement of its own.");

  private AzericardObjectKinds() {}

  static ProviderObjectKind of(AzericardPayment payment) {
    return payment.transactionType() == AzericardTransactionType.PRE_AUTHORIZATION
        ? A_HOLD
        : A_CHARGE;
  }

  static ProviderObjectKind ofASavedCard() {
    return A_SAVED_CARD;
  }

  static ProviderObjectKind ofAPayerPage() {
    return A_PAYER_PAGE;
  }
}
