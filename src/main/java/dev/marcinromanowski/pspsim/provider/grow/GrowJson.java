package dev.marcinromanowski.pspsim.provider.grow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public sealed interface GrowJson {

  record JsonText(String value) implements GrowJson {}

  record JsonNumber(long value) implements GrowJson {}

  record JsonObject(Map<String, GrowJson> members) implements GrowJson {

    public JsonObject {
      members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }
  }

  record JsonArray(List<GrowJson> elements) implements GrowJson {

    public JsonArray {
      elements = List.copyOf(elements);
    }
  }

  static JsonText text(String value) {
    return new JsonText(value);
  }

  static JsonNumber number(long value) {
    return new JsonNumber(value);
  }

  static JsonArray array(List<GrowJson> elements) {
    return new JsonArray(elements);
  }

  static JsonObject object(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("a JSON object literal needs a value for every key");
    }
    Map<String, GrowJson> members = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      if (!(keysAndValues[i] instanceof String key)) {
        throw new IllegalArgumentException(
            "a JSON object literal needs a String key at position " + i);
      }
      if (!(keysAndValues[i + 1] instanceof GrowJson value)) {
        throw new IllegalArgumentException(
            "a JSON object literal needs a GrowJson value at position " + (i + 1));
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
      case JsonText text -> valueAt(prefix, into).add(text.value());
      case JsonNumber number -> valueAt(prefix, into).add(Long.toString(number.value()));
      case JsonObject object -> {
        for (Map.Entry<String, GrowJson> member : object.members().entrySet()) {
          member.getValue().flattenInto(keyOf(prefix, member.getKey()), into);
        }
      }
      case JsonArray array -> {
        valueAt(prefix, into);
        for (GrowJson element : array.elements()) {
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
