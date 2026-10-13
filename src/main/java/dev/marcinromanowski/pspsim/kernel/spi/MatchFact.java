package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import java.util.ArrayList;
import java.util.List;

public enum MatchFact {
  MERCHANT_ID("match.merchantId"),
  ORDER_ID("match.orderId"),
  PROVIDER_OBJECT_ID("match.providerObjectId"),
  CUSTOMER_ID("match.customerId"),
  PAYMENT_METHOD_ID("match.paymentMethodId"),
  AMOUNT("match.amount"),
  CURRENCY("match.currency"),
  EVENT_TYPE("match.eventType");

  private final String property;

  MatchFact(String property) {
    this.property = property;
  }

  public String property() {
    return property;
  }

  public boolean namedIn(ScenarioMatch match) {
    return switch (this) {
      case MERCHANT_ID -> match.merchantId() != null;
      case ORDER_ID -> match.orderId() != null;
      case PROVIDER_OBJECT_ID -> match.providerObjectId() != null;
      case CUSTOMER_ID -> match.customerId() != null;
      case PAYMENT_METHOD_ID -> match.paymentMethodId() != null;
      case AMOUNT -> match.amount() != null;
      case CURRENCY -> match.currency() != null;
      case EVENT_TYPE -> match.eventType() != null;
    };
  }

  public static List<MatchFact> namedBy(ScenarioMatch match) {
    List<MatchFact> named = new ArrayList<>();
    for (MatchFact fact : values()) {
      if (fact.namedIn(match)) {
        named.add(fact);
      }
    }
    return List.copyOf(named);
  }
}
