package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

sealed interface StripeJson {

  record JsonText(String value) implements StripeJson {}

  record JsonRawNumber(String lexicalForm) implements StripeJson {}

  record JsonBoolean(boolean value) implements StripeJson {}

  record JsonNull() implements StripeJson {}

  record JsonObject(Map<String, StripeJson> members) implements StripeJson {

    public JsonObject {
      members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }
  }

  record JsonArray(List<StripeJson> elements) implements StripeJson {

    public JsonArray {
      elements = List.copyOf(elements);
    }
  }

  static JsonText text(String value) {
    return new JsonText(value);
  }

  static StripeJson textOrNull(@Nullable String value) {
    return value == null ? new JsonNull() : new JsonText(value);
  }

  static JsonRawNumber number(String lexicalForm) {
    return new JsonRawNumber(lexicalForm);
  }

  static JsonBoolean bool(boolean value) {
    return new JsonBoolean(value);
  }

  static JsonNull nothing() {
    return new JsonNull();
  }

  static JsonArray array(List<StripeJson> elements) {
    return new JsonArray(elements);
  }

  static JsonObject object(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("a JSON object literal needs a value for every key");
    }
    Map<String, StripeJson> members = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      if (!(keysAndValues[i] instanceof String key)) {
        throw new IllegalArgumentException(
            "a JSON object literal needs a String key at position " + i);
      }
      if (!(keysAndValues[i + 1] instanceof StripeJson value)) {
        throw new IllegalArgumentException(
            "a JSON object literal needs a StripeJson value at position " + (i + 1));
      }
      members.put(key, value);
    }
    return new JsonObject(members);
  }

  default Map<String, List<String>> flatten() {
    Map<String, List<String>> collected = new LinkedHashMap<>();
    flattenInto("", collected);
    Map<String, List<String>> flattened = new LinkedHashMap<>();
    collected.forEach((key, values) -> flattened.put(key, List.copyOf(values)));
    return flattened;
  }

  private void flattenInto(String prefix, Map<String, List<String>> into) {
    switch (this) {
      case JsonText value -> valueAt(prefix, into).add(value.value());
      case JsonRawNumber value -> valueAt(prefix, into).add(value.lexicalForm());
      case JsonBoolean value -> valueAt(prefix, into).add(Boolean.toString(value.value()));
      case JsonNull _ -> valueAt(prefix, into);
      case JsonObject object -> {
        for (Map.Entry<String, StripeJson> member : object.members().entrySet()) {
          member.getValue().flattenInto(keyOf(prefix, member.getKey()), into);
        }
      }
      case JsonArray array -> {
        valueAt(prefix, into);
        for (StripeJson element : array.elements()) {
          element.flattenInto(prefix, into);
        }
      }
    }
  }

  private static List<String> valueAt(String key, Map<String, List<String>> into) {
    return into.computeIfAbsent(key, absent -> new ArrayList<>());
  }

  private static String keyOf(String prefix, String name) {
    return prefix.isEmpty() ? name : prefix + "." + name;
  }
}
