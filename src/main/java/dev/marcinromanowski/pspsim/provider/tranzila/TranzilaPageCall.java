package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record TranzilaPageCall(
    String method,
    InboundRoute route,
    Map<String, List<String>> headers,
    Map<String, List<String>> parameters) {

  TranzilaPageCall {
    headers = deeplyImmutable(headers);
    parameters = deeplyImmutable(parameters);
  }

  static TranzilaPageCall of(String method, String path, Map<String, List<String>> parameters) {
    return new TranzilaPageCall(method, InboundRoute.notAliased(path), Map.of(), parameters);
  }

  String path() {
    return route.calledUrl();
  }

  @Nullable String parameter(String name) {
    return TranzilaPageRequest.first(parameters, name);
  }

  private static Map<String, List<String>> deeplyImmutable(Map<String, List<String>> values) {
    Map<String, List<String>> copied = new LinkedHashMap<>();
    values.forEach((name, sent) -> copied.put(name, List.copyOf(sent)));
    return Collections.unmodifiableMap(copied);
  }
}
