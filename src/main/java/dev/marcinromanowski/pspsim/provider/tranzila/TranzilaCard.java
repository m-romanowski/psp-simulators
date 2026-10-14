package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Locale;
import java.util.Optional;

record TranzilaCard(String cardMask, int cardType, int expiryMonth, int expiryYear) {

  static final int MASTERCARD = 1;

  static final int VISA = 2;

  static final int DINERS = 3;

  static final int AMEX = 4;

  static final int ISRACARD = 5;

  static final int MAESTRO = 6;

  static final int SHOWN_FIRST = 6;

  static final int SHOWN_LAST = 4;

  static final String CARD_LOCALITY = "domestic";

  static final String UNSEEN = "x";

  static TranzilaCard ofThePan(String pan, int expiryMonth, int expiryYear) {
    return new TranzilaCard(maskOf(pan), cardTypeOf(pan), expiryMonth, expiryYear % 100);
  }

  TranzilaCard expiringIn(int month, int year) {
    return new TranzilaCard(cardMask, cardType, month, year % 100);
  }

  static String maskOf(String cardNumber) {
    if (cardNumber.length() <= SHOWN_FIRST + SHOWN_LAST) {
      return "x".repeat(cardNumber.length() - SHOWN_LAST)
          + cardNumber.substring(cardNumber.length() - SHOWN_LAST);
    }
    return cardNumber.substring(0, SHOWN_FIRST)
        + "x".repeat(cardNumber.length() - SHOWN_FIRST - SHOWN_LAST)
        + cardNumber.substring(cardNumber.length() - SHOWN_LAST);
  }

  static int cardTypeOf(String pan) {
    if (pan.startsWith("4")) {
      return VISA;
    }
    if (pan.startsWith("34") || pan.startsWith("37")) {
      return AMEX;
    }
    if (pan.startsWith("36") || pan.startsWith("38") || pan.startsWith("30")) {
      return DINERS;
    }
    if (pan.matches("5[1-5].*|2[2-7].*")) {
      return MASTERCARD;
    }
    if (pan.matches("5[06-9].*|6.*")) {
      return MAESTRO;
    }
    return ISRACARD;
  }

  static Optional<Integer> cardTypeNamed(String brand) {
    return switch (brand.toLowerCase(Locale.ROOT)) {
      case "mastercard" -> Optional.of(Integer.valueOf(MASTERCARD));
      case "visa" -> Optional.of(Integer.valueOf(VISA));
      case "diners" -> Optional.of(Integer.valueOf(DINERS));
      case "amex" -> Optional.of(Integer.valueOf(AMEX));
      case "isracard" -> Optional.of(Integer.valueOf(ISRACARD));
      case "maestro" -> Optional.of(Integer.valueOf(MAESTRO));
      default -> Optional.empty();
    };
  }

  String cardTypeName() {
    return switch (cardType) {
      case MASTERCARD -> "Mastercard";
      case VISA -> "Visa";
      case DINERS -> "Diners";
      case AMEX -> "Amex";
      case ISRACARD -> "Isracard";
      default -> "Maestro";
    };
  }

  String last4() {
    return cardMask.substring(cardMask.length() - SHOWN_LAST);
  }

  boolean isTheSameCardAs(TranzilaCard other) {
    return last4().equals(other.last4())
        && expiryMonth == other.expiryMonth
        && expiryYear == other.expiryYear
        && cardType == other.cardType
        && theBin().flatMap(bin -> other.theBin().map(bin::equals)).orElse(Boolean.TRUE);
  }

  Optional<String> theBin() {
    String shown = cardMask.substring(0, Math.min(SHOWN_FIRST, cardMask.length()));
    return shown.length() == SHOWN_FIRST && !shown.contains(UNSEEN)
        ? Optional.of(shown)
        : Optional.empty();
  }
}
