package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

record WorldlinePayerCard(
    String alias,
    @Nullable String brand,
    String last4,
    int expirationMonth,
    int expirationYear,
    @Nullable String seededToken) {

  static final int MASKED_DIGITS = 12;

  static final String MASK = "*";

  String theCardNumberAsWorldlineWritesIt() {
    return MASK.repeat(MASKED_DIGITS) + last4;
  }

  String theExpiryDateAsWorldlineWritesIt() {
    return String.format(Locale.ROOT, "%02d%02d", expirationMonth, expirationYear % 100);
  }

  boolean namesATokenOfItsOwn() {
    return seededToken != null;
  }

  String describedForAPayer() {
    String named = brand;
    return (named == null ? "card" : named)
        + " ending "
        + last4
        + ", expiring "
        + theExpiryDateAsWorldlineWritesIt();
  }
}
