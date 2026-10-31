package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

final class StripeForm {

  private final SequencedMap<String, List<String>> values;
  private final SequencedMap<String, StripeParameter> parameters;
  private final @Nullable StripeError unreadable;

  private StripeForm(
      SequencedMap<String, List<String>> values,
      SequencedMap<String, StripeParameter> parameters,
      @Nullable StripeError unreadable) {
    this.values = values;
    this.parameters = parameters;
    this.unreadable = unreadable;
  }

  static StripeForm of(Map<String, List<String>> values) {
    LinkedHashMap<String, List<String>> inTheOrderTheCallerSentThem = new LinkedHashMap<>();
    values.forEach((name, sent) -> inTheOrderTheCallerSentThem.put(name, List.copyOf(sent)));
    StripeFormGrammar.Decoded decoded = StripeFormGrammar.decoded(inTheOrderTheCallerSentThem);
    return new StripeForm(
        Collections.unmodifiableSequencedMap(inTheOrderTheCallerSentThem),
        Collections.unmodifiableSequencedMap(decoded.parameters()),
        decoded.unreadable());
  }

  static String theListShapesOf(String name) {
    return "a list, sent as "
        + name
        + "[]=... once per element or as "
        + name
        + "[0]=..., "
        + name
        + "[1]=... numbered from 0 without a gap, and an empty "
        + name
        + "= as a list of no elements";
  }

  SequencedMap<String, List<String>> values() {
    return values;
  }

  SequencedMap<String, StripeParameter> parameters() {
    return parameters;
  }

  @Nullable StripeError unreadable() {
    return unreadable;
  }

  @Nullable String first(String name) {
    if (!(parameters.get(name) instanceof StripeParameter.Scalar scalar)) {
      return null;
    }
    return scalar.value().isBlank() ? null : scalar.value();
  }

  boolean has(String name) {
    return first(name) != null;
  }

  boolean carries(String name) {
    return parameters.containsKey(name);
  }

  @Nullable List<String> listOf(String name) {
    StripeParameter sent = parameters.get(name);
    if (sent == null) {
      return List.of();
    }
    Optional<List<StripeParameter>> elements = StripeParameter.elementsOf(sent);
    if (elements.isEmpty()) {
      return null;
    }
    List<String> listed = new ArrayList<>();
    for (StripeParameter element : elements.get()) {
      if (!(element instanceof StripeParameter.Scalar scalar)) {
        return null;
      }
      listed.add(scalar.value());
    }
    return List.copyOf(listed);
  }

  String required(String name) {
    String value = first(name);
    if (value == null) {
      throw new IllegalStateException("read " + name + " before checking it was supplied");
    }
    return value;
  }

  SequencedMap<String, List<String>> compared() {
    LinkedHashMap<String, List<String>> compared = new LinkedHashMap<>();
    parameters.forEach((name, parameter) -> flattened(name, parameter, compared));
    return Collections.unmodifiableSequencedMap(compared);
  }

  SequencedMap<String, List<String>> forAudit() {
    LinkedHashMap<String, List<String>> body = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> field : values.entrySet()) {
      body.put(field.getKey(), List.copyOf(field.getValue()));
    }
    return body;
  }

  private static void flattened(
      String path, StripeParameter parameter, Map<String, List<String>> into) {
    switch (parameter) {
      case StripeParameter.Scalar scalar -> into.put(path, List.of(scalar.value()));
      case StripeParameter.Appended appended -> {
        for (int index = 0; index < appended.values().size(); index++) {
          into.put(path + "[" + index + "]", List.of(appended.values().get(index)));
        }
      }
      case StripeParameter.Hashed hashed ->
          hashed.entries().forEach((key, entry) -> flattened(path + "[" + key + "]", entry, into));
    }
  }
}
