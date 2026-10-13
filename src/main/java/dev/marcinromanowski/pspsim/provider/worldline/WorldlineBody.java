package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record WorldlineBody(
    int httpStatus, @Nullable WorldlineJson json, Map<String, List<String>> headers) {

  WorldlineBody {
    headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
  }

  static WorldlineBody of(int httpStatus, WorldlineJson json) {
    return new WorldlineBody(httpStatus, json, Map.of());
  }

  static WorldlineBody of(int httpStatus, WorldlineJson json, Map<String, List<String>> headers) {
    return new WorldlineBody(httpStatus, json, headers);
  }

  static WorldlineBody nothingRendered() {
    return new WorldlineBody(0, null, Map.of());
  }

  WorldlineBody alsoCarrying(String header, String value) {
    Map<String, List<String>> widened = new LinkedHashMap<>(headers);
    widened.put(header, List.of(value));
    return new WorldlineBody(httpStatus, json, widened);
  }

  @Nullable String contentType() {
    return json == null ? null : WorldlineFields.JSON;
  }

  byte[] bytes() {
    WorldlineJson rendered = json;
    return rendered == null ? new byte[0] : WorldlineJsonWriter.toBytes(rendered);
  }

  String text() {
    WorldlineJson rendered = json;
    return rendered == null ? "" : WorldlineJsonWriter.toText(rendered);
  }

  Map<String, List<String>> fields() {
    WorldlineJson rendered = json;
    return rendered == null ? Map.of() : rendered.flatten();
  }
}
