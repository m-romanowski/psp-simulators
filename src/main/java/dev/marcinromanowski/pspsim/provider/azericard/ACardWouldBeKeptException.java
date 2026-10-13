package dev.marcinromanowski.pspsim.provider.azericard;

final class ACardWouldBeKeptException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  static final String A_SESSION_KEEPS_NO_CARD =
      "a payer session keeps the signed request the merchant's page posted and never a card"
          + " number, expiry or CVC, because a card this simulator stored would be a card it"
          + " could leak";

  ACardWouldBeKeptException() {
    super(A_SESSION_KEEPS_NO_CARD);
  }
}
