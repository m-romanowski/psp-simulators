package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

sealed interface WorldlineJson {

  record JsonText(String value) implements WorldlineJson {}

  record JsonNumber(String lexical) implements WorldlineJson {

    public JsonNumber {
      if (!WorldlineJsonReader.isANumber(lexical)) {
        throw new IllegalArgumentException(
            "provider worldline types this field as a number and "
                + lexical
                + " is not a JSON number, so a body carrying it there would not parse at all in"
                + " the client, which would then read a value it got wrong as a body worldline"
                + " could not read. A value this simulator did not produce reaches the wire"
                + " through numberOrText, which puts it where the client can see which property"
                + " is wrong");
      }
    }
  }

  record JsonFlag(boolean value) implements WorldlineJson {}

  record JsonNothing() implements WorldlineJson {}

  record JsonObject(Map<String, WorldlineJson> members) implements WorldlineJson {

    public JsonObject {
      members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }

    boolean has(String name) {
      return members.containsKey(name);
    }

    @Nullable WorldlineJson member(String name) {
      return members.get(name);
    }

    Optional<String> text(String name) {
      return member(name) instanceof JsonText text ? Optional.of(text.value()) : Optional.empty();
    }

    Optional<String> scalar(String name) {
      Optional<String> number = number(name);
      return number.isPresent() ? number : text(name);
    }

    Optional<String> number(String name) {
      return member(name) instanceof JsonNumber number
          ? Optional.of(number.lexical())
          : Optional.empty();
    }

    Optional<Boolean> flag(String name) {
      return member(name) instanceof JsonFlag flag
          ? Optional.of(Boolean.valueOf(flag.value()))
          : Optional.empty();
    }

    Optional<JsonArray> array(String name) {
      return member(name) instanceof JsonArray listed ? Optional.of(listed) : Optional.empty();
    }

    Optional<JsonObject> object(String name) {
      return member(name) instanceof JsonObject nested ? Optional.of(nested) : Optional.empty();
    }
  }

  record JsonArray(List<WorldlineJson> elements) implements WorldlineJson {

    public JsonArray {
      elements = List.copyOf(elements);
    }
  }

  static JsonText text(String value) {
    return new JsonText(value);
  }

  static JsonNumber number(String lexical) {
    return new JsonNumber(lexical);
  }

  static WorldlineJson numberOrText(String value) {
    return WorldlineJsonReader.isANumber(value) ? number(value) : text(value);
  }

  static JsonFlag flag(boolean value) {
    return new JsonFlag(value);
  }

  static JsonArray array(List<WorldlineJson> elements) {
    return new JsonArray(elements);
  }

  static JsonObject object(Map<String, WorldlineJson> members) {
    return new JsonObject(members);
  }

  static JsonObject empty() {
    return new JsonObject(Map.of());
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
      case JsonNumber value -> valueAt(prefix, into).add(value.lexical());
      case JsonFlag value -> valueAt(prefix, into).add(Boolean.toString(value.value()));
      case JsonNothing _ -> valueAt(prefix, into).add("");
      case JsonObject value -> {
        for (Map.Entry<String, WorldlineJson> member : value.members().entrySet()) {
          member.getValue().flattenInto(keyOf(prefix, member.getKey()), into);
        }
      }
      case JsonArray value -> {
        valueAt(prefix, into);
        for (WorldlineJson element : value.elements()) {
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
