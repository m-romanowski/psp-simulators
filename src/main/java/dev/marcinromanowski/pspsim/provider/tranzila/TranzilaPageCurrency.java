package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Optional;

enum TranzilaPageCurrency {
  NIS("1", "ILS"),

  US_DOLLAR("2", "USD"),

  EURO("978", "EUR"),

  POUND_STERLING("826", "GBP");

  private final String numeric;
  private final String alpha;

  TranzilaPageCurrency(String numeric, String alpha) {
    this.numeric = numeric;
    this.alpha = alpha;
  }

  String numeric() {
    return numeric;
  }

  String alpha() {
    return alpha;
  }

  static Optional<TranzilaPageCurrency> numbered(String sent) {
    for (TranzilaPageCurrency published : values()) {
      if (published.numeric.equals(sent)) {
        return Optional.of(published);
      }
    }
    return Optional.empty();
  }

  static Optional<TranzilaPageCurrency> alphabetic(String sent) {
    for (TranzilaPageCurrency published : values()) {
      if (published.alpha.equals(sent)) {
        return Optional.of(published);
      }
    }
    return Optional.empty();
  }
}
