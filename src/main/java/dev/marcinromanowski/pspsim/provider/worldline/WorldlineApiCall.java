package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record WorldlineApiCall(
    String method,
    InboundRoute route,
    Map<String, List<String>> headers,
    Map<String, List<String>> query,
    String rawBody) {

  WorldlineApiCall {
    headers = deeplyImmutable(headers);
    query = deeplyImmutable(query);
  }

  static WorldlineApiCall of(
      String method,
      String path,
      Map<String, List<String>> headers,
      Map<String, List<String>> query,
      String rawBody) {
    return new WorldlineApiCall(method, InboundRoute.notAliased(path), headers, query, rawBody);
  }

  String path() {
    return route.calledUrl();
  }

  Optional<WorldlineJson.JsonObject> body() {
    return WorldlineJsonReader.objectIn(rawBody);
  }

  boolean carriesABody() {
    return !rawBody.isBlank();
  }

  Optional<String> header(String name) {
    for (Map.Entry<String, List<String>> sent : headers.entrySet()) {
      if (sent.getKey().equalsIgnoreCase(name)) {
        for (String value : sent.getValue()) {
          if (!value.isBlank()) {
            return Optional.of(value);
          }
        }
      }
    }
    return Optional.empty();
  }

  Optional<String> queryParameter(String name) {
    List<String> sent = query.get(name);
    if (sent == null) {
      return Optional.empty();
    }
    for (String value : sent) {
      if (!value.isBlank()) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }

  boolean namesTheQueryParameter(String name) {
    return query.containsKey(name);
  }

  private static Map<String, List<String>> deeplyImmutable(Map<String, List<String>> values) {
    Map<String, List<String>> copied = new LinkedHashMap<>();
    values.forEach((name, sent) -> copied.put(name, List.copyOf(sent)));
    return Collections.unmodifiableMap(new LinkedHashMap<>(copied));
  }
}
