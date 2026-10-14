package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

sealed interface TranzilaJson {

  record JsonText(String value) implements TranzilaJson {}

  record JsonNumber(String lexical) implements TranzilaJson {

    public JsonNumber {
      if (!TranzilaJsonReader.isANumber(lexical)) {
        throw new IllegalArgumentException(
            "provider tranzila types this field as a number and "
                + lexical
                + " is not a JSON number, so a body carrying it there would not parse at all in"
                + " the client, which would then read a value it got wrong as a body tranzila"
                + " could not read. A value this simulator did not produce reaches the wire"
                + " through numberOrText, which puts it where the client can see which property"
                + " is wrong");
      }
    }
  }

  record JsonFlag(boolean value) implements TranzilaJson {}

  record JsonNothing() implements TranzilaJson {}

  record JsonObject(Map<String, TranzilaJson> members) implements TranzilaJson {

    public JsonObject {
      members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
    }

    boolean has(String name) {
      return members.containsKey(name);
    }

    @Nullable TranzilaJson member(String name) {
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

    Optional<JsonArray> array(String name) {
      return member(name) instanceof JsonArray listed ? Optional.of(listed) : Optional.empty();
    }

    Optional<JsonObject> object(String name) {
      return member(name) instanceof JsonObject nested ? Optional.of(nested) : Optional.empty();
    }
  }

  record JsonArray(List<TranzilaJson> elements) implements TranzilaJson {

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

  static TranzilaJson numberOrText(String value) {
    return TranzilaJsonReader.isANumber(value) ? number(value) : text(value);
  }

  static JsonArray array(List<TranzilaJson> elements) {
    return new JsonArray(elements);
  }

  static JsonObject object(Map<String, TranzilaJson> members) {
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
      case JsonNumber value -> valueAt(prefix, into).add(value.lexical());
      case JsonFlag value -> valueAt(prefix, into).add(Boolean.toString(value.value()));
      case JsonNothing _ -> valueAt(prefix, into).add("");
      case JsonObject value -> {
        for (Map.Entry<String, TranzilaJson> member : value.members().entrySet()) {
          member.getValue().flattenInto(keyOf(prefix, member.getKey()), into);
        }
      }
      case JsonArray value -> {
        valueAt(prefix, into);
        for (TranzilaJson element : value.elements()) {
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
