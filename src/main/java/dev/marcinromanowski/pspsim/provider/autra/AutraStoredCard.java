package dev.marcinromanowski.pspsim.provider.autra;

import org.jspecify.annotations.Nullable;

record AutraStoredCard(
    @Nullable String slugToken,
    @Nullable String slugStoredCard,
    @Nullable String tokenExpirationDate,
    @Nullable String brand,
    @Nullable String first4,
    @Nullable String last4,
    @Nullable Integer expirationMonth,
    @Nullable Integer expirationYear) {

  static final String A_STORED_CARD_NAMES_AT_LEAST_ONE_SLUG =
      "provider autra names a stored card by slugToken and slugStoredCard together and publishes"
          + " that one of the two is what a later charge sends: \"Use card OU tokenData, nunca os"
          + " dois\", with tokenData carrying \"slugToken\" — \"Network token — preferir quando"
          + " disponivel\" — and \"slugStoredCard\" — \"Cartao armazenado — expira junto com o"
          + " cartao fisico\". A pair with neither half names no card at all, so a tokenData built"
          + " from it would be an empty object this contract has no charge for";

  AutraStoredCard {
    if (blank(slugToken) && blank(slugStoredCard)) {
      throw new IllegalArgumentException(A_STORED_CARD_NAMES_AT_LEAST_ONE_SLUG);
    }
  }

  private static boolean blank(@Nullable String slug) {
    return slug == null || slug.isBlank();
  }
}
