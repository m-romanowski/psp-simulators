package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record TranzilaApiCall(
    String method, InboundRoute route, Map<String, List<String>> headers, String rawBody) {

  TranzilaApiCall {
    Map<String, List<String>> copied = new LinkedHashMap<>();
    headers.forEach((name, sent) -> copied.put(name, List.copyOf(sent)));
    headers = Collections.unmodifiableMap(copied);
  }

  static TranzilaApiCall of(
      String method, String path, Map<String, List<String>> headers, String rawBody) {
    return new TranzilaApiCall(method, InboundRoute.notAliased(path), headers, rawBody);
  }

  String path() {
    return route.calledUrl();
  }

  Optional<TranzilaJson.JsonObject> body() {
    return TranzilaJsonReader.objectIn(rawBody);
  }

  Optional<String> header(String name) {
    return headers.entrySet().stream()
        .filter(sent -> sent.getKey().equalsIgnoreCase(name))
        .flatMap(sent -> sent.getValue().stream())
        .filter(value -> !value.isBlank())
        .findFirst();
  }
}
