package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class GrowWireRequest {

  private static final String MULTIPART_FORM_DATA = "multipart/form-data";

  private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

  private GrowWireRequest() {}

  static GrowApiCall callOf(HttpServletRequest request) {
    return new GrowApiCall(
        request.getMethod(), InboundRoute.of(request), headersOf(request), bodyFieldsOf(request));
  }

  static GrowApiCall queryCallOf(HttpServletRequest request) {
    return new GrowApiCall(
        request.getMethod(),
        InboundRoute.of(request),
        headersOf(request),
        pairsOf(orEmpty(request.getQueryString())));
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
    String contentType = contentTypeOf(request);
    try {
      if (contentType.startsWith(MULTIPART_FORM_DATA)) {
        return multipartFieldsOf(request);
      }
      if (contentType.startsWith(FORM_URLENCODED)) {
        return urlencodedFieldsOf(request);
      }
    } catch (IOException | ServletException | IllegalArgumentException unreadable) {
      return Map.of();
    }
    return Map.of();
  }

  private static String contentTypeOf(HttpServletRequest request) {
    String contentType = request.getContentType();
    return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
  }

  private static Map<String, List<String>> multipartFieldsOf(HttpServletRequest request)
      throws IOException, ServletException {
    Collection<Part> parts;
    try {
      parts = request.getParts();
    } catch (IllegalStateException refusedTheBody) {
      return Map.of();
    }
    Map<String, List<String>> fields = new LinkedHashMap<>();
    for (Part part : parts) {
      if (part.getSubmittedFileName() != null) {
        continue;
      }
      fields.computeIfAbsent(part.getName(), name -> new ArrayList<>()).add(valueOf(part));
    }
    return fields;
  }

  private static String valueOf(Part part) throws IOException {
    try (InputStream content = part.getInputStream()) {
      return new String(content.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Map<String, List<String>> urlencodedFieldsOf(HttpServletRequest request)
      throws IOException {
    try (InputStream content = request.getInputStream()) {
      return pairsOf(new String(content.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static String orEmpty(@Nullable String queryString) {
    return queryString == null ? "" : queryString;
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

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
