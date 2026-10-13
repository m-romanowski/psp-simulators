package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StripeApiCall(
    String method,
    InboundRoute route,
    Map<String, List<String>> headers,
    Map<String, List<String>> form,
    Map<String, List<String>> query) {

  public StripeApiCall {
    headers = deeplyImmutable(headers);
    form = deeplyImmutable(form);
    query = deeplyImmutable(query);
  }

  public StripeApiCall(
      String method,
      InboundRoute route,
      Map<String, List<String>> headers,
      Map<String, List<String>> form) {
    this(method, route, headers, form, Map.of());
  }

  public StripeApiCall(
      String method,
      String path,
      Map<String, List<String>> headers,
      Map<String, List<String>> form) {
    this(method, InboundRoute.notAliased(path), headers, form, Map.of());
  }

  public StripeApiCall(
      String method,
      String path,
      Map<String, List<String>> headers,
      Map<String, List<String>> form,
      Map<String, List<String>> query) {
    this(method, InboundRoute.notAliased(path), headers, form, query);
  }

  public String path() {
    return route.calledUrl();
  }

  private static Map<String, List<String>> deeplyImmutable(Map<String, List<String>> values) {
    Map<String, List<String>> copied = new LinkedHashMap<>();
    values.forEach((name, sent) -> copied.put(name, List.copyOf(sent)));
    return Collections.unmodifiableMap(new LinkedHashMap<>(copied));
  }
}
