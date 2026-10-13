package dev.marcinromanowski.pspsim.provider.autra;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record AutraBody(int httpStatus, @Nullable AutraJson json) {

  static AutraBody of(int httpStatus, AutraJson json) {
    return new AutraBody(httpStatus, json);
  }

  static AutraBody nothingRendered() {
    return new AutraBody(0, null);
  }

  @Nullable String contentType() {
    return json == null ? null : AutraFields.JSON;
  }

  byte[] bytes() {
    AutraJson rendered = json;
    return rendered == null ? new byte[0] : AutraJsonWriter.toBytes(rendered);
  }

  String text() {
    AutraJson rendered = json;
    return rendered == null ? "" : AutraJsonWriter.toText(rendered);
  }

  Map<String, List<String>> fields() {
    AutraJson rendered = json;
    return rendered == null ? Map.of() : rendered.flatten();
  }
}
