package dev.marcinromanowski.pspsim.provider.azericard;

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
import java.util.Locale;
import java.util.Map;

final class AzericardWireRequest {

  private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

  private AzericardWireRequest() {}

  static AzericardApiCall callOf(HttpServletRequest request) {
    return new AzericardApiCall(
        request.getMethod(), InboundRoute.of(request), headersOf(request), bodyFieldsOf(request));
  }

  private static Map<String, List<String>> headersOf(HttpServletRequest request) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (Enumeration<String> names = request.getHeaderNames(); names.hasMoreElements(); ) {
      String name = names.nextElement();
      headers.put(name, Collections.list(request.getHeaders(name)));
    }
    return headers;
  }

  private static Map<String, List<String>> bodyFieldsOf(HttpServletRequest request) {
    if (!contentTypeOf(request).startsWith(FORM_URLENCODED)) {
      return Map.of();
    }
    try {
      return urlencodedFieldsOf(request);
    } catch (IOException | IllegalArgumentException unreadable) {
      return Map.of();
    }
  }

  private static String contentTypeOf(HttpServletRequest request) {
    String contentType = request.getContentType();
    return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
  }

  private static Map<String, List<String>> urlencodedFieldsOf(HttpServletRequest request)
      throws IOException {
    String body;
    try (InputStream content = request.getInputStream()) {
      body = new String(content.readAllBytes(), StandardCharsets.UTF_8);
    }
    Map<String, List<String>> fields = new LinkedHashMap<>();
    for (String pair : body.split("&", -1)) {
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

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
