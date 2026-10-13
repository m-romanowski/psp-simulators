package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.LinkedHashMap;
import java.util.Map;

final class TranzilaOriginalRequest {

  private TranzilaOriginalRequest() {}

  static TranzilaJson.JsonObject of(TranzilaJson.JsonObject body) {
    Map<String, TranzilaJson> echoed = new LinkedHashMap<>(body.members());
    body.text(TranzilaFields.CARD_NUMBER)
        .ifPresent(
            sent ->
                echoed.put(
                    TranzilaFields.CARD_NUMBER, TranzilaJson.text(TranzilaCard.maskOf(sent))));
    body.text(TranzilaFields.CVV)
        .ifPresent(
            sent -> echoed.put(TranzilaFields.CVV, TranzilaJson.text(TranzilaFields.MASKED_CVV)));
    return TranzilaJson.object(echoed);
  }
}
