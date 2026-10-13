package dev.marcinromanowski.pspsim.kernel.webhook.internal;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class StoredAttemptHeaders {

  private static final TypeReference<Map<String, List<String>>> MULTI_VALUED =
      new TypeReference<>() {};

  private StoredAttemptHeaders() {}

  public static @Nullable String storedForm(
      @Nullable Map<String, List<String>> onTheWire, ObjectMapper json) {
    return onTheWire == null ? null : json.writeValueAsString(onTheWire);
  }

  public static @Nullable Map<String, List<String>> theWireOf(
      @Nullable String stored, ObjectMapper json) {
    return stored == null ? null : json.readValue(stored, MULTI_VALUED);
  }
}
