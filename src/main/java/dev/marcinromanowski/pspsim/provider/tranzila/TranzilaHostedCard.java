package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Optional;

final class TranzilaHostedCard {

  private final String number;
  private final boolean cvvEntered;
  private final boolean idEntered;

  TranzilaHostedCard(String number, boolean cvvEntered, boolean idEntered) {
    this.number = number;
    this.cvvEntered = cvvEntered;
    this.idEntered = idEntered;
  }

  String lastFour() {
    return number.substring(number.length() - TranzilaCard.SHOWN_LAST);
  }

  int cardType() {
    return TranzilaHostedFieldsRequest.passesLuhn(number)
            || !TranzilaHostedFieldsRequest.isAnIsracardNumber(number)
        ? TranzilaCard.cardTypeOf(number)
        : TranzilaCard.ISRACARD;
  }

  TranzilaCard withoutItsNumber(int expiryMonth, int expiryYear) {
    return new TranzilaCard(
        TranzilaCard.UNSEEN.repeat(number.length() - TranzilaCard.SHOWN_LAST) + lastFour(),
        cardType(),
        expiryMonth,
        expiryYear % 100);
  }

  Optional<TranzilaThreeDSecureOutcome> theThreeDSecureOutcome() {
    return TranzilaThreeDSecureCards.ofThePan(number);
  }

  boolean cvvEntered() {
    return cvvEntered;
  }

  boolean idEntered() {
    return idEntered;
  }

  @Override
  public String toString() {
    return "a card the payer typed, ending " + lastFour();
  }
}
