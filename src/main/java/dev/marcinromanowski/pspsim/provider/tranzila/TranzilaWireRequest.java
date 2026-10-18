package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TranzilaWireRequest {

  private TranzilaWireRequest() {}

  static TranzilaApiCall callOf(HttpServletRequest request) {
    return new TranzilaApiCall(
        request.getMethod(), InboundRoute.of(request), headersOf(request), bodyOf(request));
  }

  static TranzilaPageCall pageCallOf(HttpServletRequest request) {
    Map<String, List<String>> parameters = new LinkedHashMap<>();
    request.getParameterMap().forEach((name, values) -> parameters.put(name, List.of(values)));
    return new TranzilaPageCall(
        request.getMethod(), InboundRoute.of(request), headersOf(request), parameters);
  }

  private static Map<String, List<String>> headersOf(HttpServletRequest request) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : Collections.list(request.getHeaderNames())) {
      headers.put(name, Collections.list(request.getHeaders(name)));
    }
    return headers;
  }

  private static String bodyOf(HttpServletRequest request) {
    try (InputStream content = request.getInputStream()) {
      return new String(content.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      return "";
    }
  }
}
