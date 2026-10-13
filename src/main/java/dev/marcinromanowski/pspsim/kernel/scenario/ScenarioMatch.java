package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record ScenarioMatch(
    @Nullable String merchantId,
    @Nullable String orderId,
    @Nullable String providerObjectId,
    @Nullable String customerId,
    @Nullable String paymentMethodId,
    @Nullable Amount amount,
    @Nullable String currency,
    @Nullable String operation,
    @Nullable String eventType,
    Map<String, String> header) {

  public ScenarioMatch {
    header = copied(header);
  }

  private static SequencedMap<String, String> copied(Map<String, String> header) {
    LinkedHashMap<String, String> inTheOrderTheRuleNamedThem = new LinkedHashMap<>();
    inTheOrderTheRuleNamedThem.putAll(header);
    return Collections.unmodifiableSequencedMap(inTheOrderTheRuleNamedThem);
  }

  public static ScenarioMatch anyRequest() {
    return new ScenarioMatch(null, null, null, null, null, null, null, null, null, Map.of());
  }

  public ScenarioMatch naming(String operation) {
    return new ScenarioMatch(
        merchantId,
        orderId,
        providerObjectId,
        customerId,
        paymentMethodId,
        amount,
        currency,
        operation,
        eventType,
        header);
  }

  public ScenarioMatch reporting(String eventType) {
    return new ScenarioMatch(
        merchantId,
        orderId,
        providerObjectId,
        customerId,
        paymentMethodId,
        amount,
        currency,
        operation,
        eventType,
        header);
  }

  public int specificity() {
    int named = header.isEmpty() ? 0 : 1;
    named += merchantId == null ? 0 : 1;
    named += orderId == null ? 0 : 1;
    named += providerObjectId == null ? 0 : 1;
    named += customerId == null ? 0 : 1;
    named += paymentMethodId == null ? 0 : 1;
    named += amount == null ? 0 : 1;
    named += currency == null ? 0 : 1;
    named += operation == null ? 0 : 1;
    named += eventType == null ? 0 : 1;
    return named;
  }

  public boolean matches(ScenarioRequest request) {
    return sameText(merchantId, request.merchantId())
        && sameText(orderId, request.orderId())
        && sameText(providerObjectId, request.providerObjectId())
        && sameText(customerId, request.customerId())
        && sameText(paymentMethodId, request.paymentMethodId())
        && sameAmount(request.amount())
        && sameText(currency, request.currency())
        && sameText(operation, request.operation())
        && everyNamedHeaderWasSent(request.headers());
  }

  public boolean matches(DeliveryFacts facts) {
    return sameText(merchantId, facts.merchantId())
        && sameText(providerObjectId, facts.providerObjectId())
        && sameText(eventType, facts.eventType());
  }

  private static boolean sameText(@Nullable String required, @Nullable String sent) {
    return required == null || required.equals(sent);
  }

  private boolean sameAmount(@Nullable Amount sent) {
    return amount == null || (sent != null && amount.value().equals(sent.value()));
  }

  private boolean everyNamedHeaderWasSent(Map<String, List<String>> sent) {
    Map<String, List<String>> lowercased = new LinkedHashMap<>();
    sent.forEach((name, values) -> lowercased.put(name.toLowerCase(Locale.ROOT), values));
    for (Map.Entry<String, String> required : header.entrySet()) {
      List<String> values = lowercased.get(required.getKey().toLowerCase(Locale.ROOT));
      if (values == null || !values.contains(required.getValue())) {
        return false;
      }
    }
    return true;
  }
}
