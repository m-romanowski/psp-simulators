package dev.marcinromanowski.pspsim.provider.tranzila;

import org.jspecify.annotations.Nullable;

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
    return TranzilaCard.cardTypeOf(number);
  }

  TranzilaCard withoutItsNumber(int expiryMonth, int expiryYear) {
    return new TranzilaCard(
        TranzilaCard.UNSEEN.repeat(number.length() - TranzilaCard.SHOWN_LAST) + lastFour(),
        cardType(),
        expiryMonth,
        expiryYear % 100);
  }

  boolean cvvEntered() {
    return cvvEntered;
  }

  boolean idEntered() {
    return idEntered;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof TranzilaHostedCard card
        && number.equals(card.number)
        && cvvEntered == card.cvvEntered
        && idEntered == card.idEntered;
  }

  @Override
  public int hashCode() {
    return lastFour().hashCode();
  }

  @Override
  public String toString() {
    return "a card the payer typed, ending " + lastFour();
  }
}
