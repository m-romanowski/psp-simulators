package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AutraWireRequest {

  private AutraWireRequest() {}

  static AutraApiCall callOf(HttpServletRequest request) {
    return new AutraApiCall(
        request.getMethod(),
        InboundRoute.of(request),
        headersOf(request),
        queryFieldsOf(request),
        bodyOf(request));
  }

  private static Map<String, List<String>> headersOf(HttpServletRequest request) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (Enumeration<String> names = request.getHeaderNames(); names.hasMoreElements(); ) {
      String name = names.nextElement();
      headers.put(name, Collections.list(request.getHeaders(name)));
    }
    return headers;
  }

  private static Map<String, List<String>> queryFieldsOf(HttpServletRequest request) {
    String queryString = request.getQueryString();
    if (queryString == null || queryString.isEmpty()) {
      return Map.of();
    }
    try {
      return pairsOf(queryString);
    } catch (IllegalArgumentException unreadable) {
      return Map.of();
    }
  }

  private static String bodyOf(HttpServletRequest request) {
    try (InputStream content = request.getInputStream()) {
      return new String(content.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      return "";
    }
  }

  private static Map<String, List<String>> pairsOf(String encoded) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    for (String pair : encoded.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      int separator = pair.indexOf('=');
      String name = separator < 0 ? pair : pair.substring(0, separator);
      String value = separator < 0 ? "" : pair.substring(separator + 1);
      fields.computeIfAbsent(decode(name), field -> new ArrayList<>()).add(decode(value));
    }
    return fields;
  }

  private static String decode(@Nullable String value) {
    return URLDecoder.decode(String.valueOf(value), StandardCharsets.UTF_8);
  }
}
