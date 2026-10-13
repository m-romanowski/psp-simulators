package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum GrowWireForm {
  LIVE_WIRE("text/html; charset=UTF-8", true),

  PUBLISHED_EXAMPLE("application/json", false);

  static final String SETTING = "wireForm";

  private final String contentType;
  private final boolean escapesNonAscii;

  GrowWireForm(String contentType, boolean escapesNonAscii) {
    this.contentType = contentType;
    this.escapesNonAscii = escapesNonAscii;
  }

  String contentType() {
    return contentType;
  }

  boolean escapesNonAscii() {
    return escapesNonAscii;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            LIVE_WIRE.name(),
            "answer as the live wire does: content type text/html, and every non-ASCII character"
                + " escaped"),
        new AcceptedValue(
            PUBLISHED_EXAMPLE.name(),
            "answer as the published example does: content type application/json, with non-ASCII"
                + " characters left as they are"));
  }

  static GrowWireForm of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return LIVE_WIRE;
    }
    for (GrowWireForm form : values()) {
      if (form.name().equals(configured)) {
        return form;
      }
    }
    throw new IllegalArgumentException("must be one of " + LIVE_WIRE + ", " + PUBLISHED_EXAMPLE);
  }
}
