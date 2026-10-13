package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StripeLatestCharges {

  private StripeLatestCharges() {}

  record ChargeOf(String intentId, String chargeId) {}

  static Map<String, String> keyedByIntent(List<ChargeOf> oldestArrivalFirst) {
    Map<String, String> latest = new LinkedHashMap<>();
    for (ChargeOf charge : oldestArrivalFirst) {
      latest.put(charge.intentId(), charge.chargeId());
    }
    return Collections.unmodifiableMap(latest);
  }
}
