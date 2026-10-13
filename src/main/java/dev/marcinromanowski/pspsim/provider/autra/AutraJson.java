package dev.marcinromanowski.pspsim.provider.autra;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

sealed interface AutraJson {

  record JsonText(String value) implements AutraJson {}

  record JsonNumber(String lexical) implements AutraJson {

    public JsonNumber {
      if (!AutraJsonReader.isANumber(lexical)) {
        throw new IllegalArgumentException(
            "provider autra types this field as a number and "
                + lexical
                + " is not a JSON number, so a body carrying it there would not parse at all in"
                + " the client, which would then read a value it got wrong as a body autra could"
                + " not read. A value this simulator did not produce reaches the wire through"
                + " numberOrText, which puts it where the client can see which field is wrong");
      }
    }
  }

  record JsonFlag(boolean value) implements AutraJson {}

  record JsonNothing() implements AutraJson {}

  record JsonObject(Map<String, AutraJson> members) implements AutraJson {

    public JsonObject {
      members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }

    boolean has(String name) {
      return members.containsKey(name);
    }

    @Nullable AutraJson member(String name) {
      return members.get(name);
    }

    Optional<String> text(String name) {
      return member(name) instanceof JsonText text ? Optional.of(text.value()) : Optional.empty();
    }

    Optional<String> number(String name) {
      return member(name) instanceof JsonNumber number
          ? Optional.of(number.lexical())
          : Optional.empty();
    }

    Optional<JsonObject> object(String name) {
      return member(name) instanceof JsonObject nested ? Optional.of(nested) : Optional.empty();
    }
  }

  record JsonArray(List<AutraJson> elements) implements AutraJson {

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

  static AutraJson numberOrText(String value) {
    return AutraJsonReader.isANumber(value) ? number(value) : text(value);
  }

  static JsonFlag flag(boolean value) {
    return new JsonFlag(value);
  }

  static JsonArray array(List<AutraJson> elements) {
    return new JsonArray(elements);
  }

  static JsonObject object(Map<String, AutraJson> members) {
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
        for (Map.Entry<String, AutraJson> member : value.members().entrySet()) {
          member.getValue().flattenInto(keyOf(prefix, member.getKey()), into);
        }
      }
      case JsonArray value -> {
        valueAt(prefix, into);
        for (AutraJson element : value.elements()) {
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
