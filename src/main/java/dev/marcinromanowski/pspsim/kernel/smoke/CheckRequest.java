package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record CheckRequest(
    String method, String path, Map<String, String> headers, @Nullable String body) {

  public CheckRequest {
    if (method == null || method.isBlank()) {
      throw new IllegalArgumentException("method must name the verb the client sends");
    }
    if (path == null || !path.startsWith("/")) {
      throw new IllegalArgumentException("path must be the route a client dials");
    }
    headers = theHeadersInTheOrderTheCheckNamesThem(headers);
  }

  public static CheckRequest form(String method, String path, Map<String, String> fields) {
    return new CheckRequest(
        method,
        path,
        Map.of("Content-Type", "application/x-www-form-urlencoded"),
        WireForm.encoded(fields));
  }

  public static CheckRequest json(String method, String path, String body) {
    return new CheckRequest(method, path, Map.of("Content-Type", "application/json"), body);
  }

  public static CheckRequest get(String path) {
    return new CheckRequest("GET", path, Map.of(), null);
  }

  public static CheckRequest delete(String path) {
    return new CheckRequest("DELETE", path, Map.of(), null);
  }

  public CheckRequest carrying(String name, String value) {
    LinkedHashMap<String, String> carried = new LinkedHashMap<>(headers);
    carried.put(name, value);
    return new CheckRequest(method, path, carried, body);
  }

  private static SequencedMap<String, String> theHeadersInTheOrderTheCheckNamesThem(
      Map<String, String> named) {
    LinkedHashMap<String, String> inTheOrderTheCheckNamedThem = new LinkedHashMap<>(named);
    return Collections.unmodifiableSequencedMap(inTheOrderTheCheckNamedThem);
  }
}
