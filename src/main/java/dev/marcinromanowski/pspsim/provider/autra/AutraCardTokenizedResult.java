package dev.marcinromanowski.pspsim.provider.autra;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AutraCardTokenizedResult {

  private AutraCardTokenizedResult() {}

  static AutraJson.JsonObject of(AutraCardSession session, AutraStoredCard card) {
    Map<String, AutraJson> result = new LinkedHashMap<>();
    textIfNamed(result, AutraFields.SLUG_STORED_CARD, card.slugStoredCard());
    textIfNamed(result, AutraFields.SLUG_TOKEN, card.slugToken());
    textIfNamed(result, AutraFields.TOKEN_EXPIRATION_DATE, card.tokenExpirationDate());
    textIfNamed(result, AutraFields.BRAND, card.brand());
    textIfNamed(result, AutraFields.FIRST4, card.first4());
    textIfNamed(result, AutraFields.LAST4, card.last4());
    numberIfNamed(result, AutraFields.EXPIRATION_MONTH, card.expirationMonth());
    numberIfNamed(result, AutraFields.EXPIRATION_YEAR, card.expirationYear());
    textIfNamed(result, AutraFields.NICKNAME, session.nickname());
    return AutraJson.object(result);
  }

  private static void textIfNamed(
      Map<String, AutraJson> result, String field, @Nullable String value) {
    if (value != null) {
      result.put(field, AutraJson.text(value));
    }
  }

  private static void numberIfNamed(
      Map<String, AutraJson> result, String field, @Nullable Integer value) {
    if (value != null) {
      result.put(field, AutraJson.number(value.toString()));
    }
  }
}
