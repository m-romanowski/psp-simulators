package dev.marcinromanowski.pspsim.provider.azericard;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

record AzericardBody(
    String contentType,
    String text,
    Map<String, List<String>> fields,
    Map<String, List<String>> headers) {

  AzericardBody(String contentType, String text, Map<String, List<String>> fields) {
    this(contentType, text, fields, Map.of());
  }

  static AzericardBody nothingRendered() {
    return new AzericardBody(AzericardResponse.CONTENT_TYPE, "", Map.of());
  }

  byte[] bytes() {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
