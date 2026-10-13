package dev.marcinromanowski.pspsim.provider.autra;

import org.jspecify.annotations.Nullable;

record AutraPayerCard(
    String alias,
    String brand,
    @Nullable String first4,
    String last4,
    int expirationMonth,
    int expirationYear,
    @Nullable String slugToken,
    @Nullable String slugStoredCard) {

  static final String MASKED = "•••• ";

  String describedForAPayer() {
    return brand + " " + MASKED + last4 + ", " + theExpiryAsAutraWritesIt();
  }

  String theExpiryAsAutraWritesIt() {
    String month = expirationMonth < 10 ? "0" + expirationMonth : String.valueOf(expirationMonth);
    return month + expirationYear;
  }

  boolean namesASlugOfItsOwn() {
    return notBlank(slugToken) || notBlank(slugStoredCard);
  }

  private static boolean notBlank(@Nullable String slug) {
    return slug != null && !slug.isBlank();
  }
}
