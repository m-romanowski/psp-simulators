package dev.marcinromanowski.pspsim.provider.azericard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

record AzericardForm(SequencedMap<String, List<String>> values) {

  static AzericardForm of(Map<String, List<String>> values) {
    LinkedHashMap<String, List<String>> inTheOrderTheCallerSentThem = new LinkedHashMap<>();
    values.forEach((name, sent) -> inTheOrderTheCallerSentThem.put(name, List.copyOf(sent)));
    return new AzericardForm(Collections.unmodifiableSequencedMap(inTheOrderTheCallerSentThem));
  }

  @Nullable String first(String name) {
    List<String> sent = values.get(name);
    if (sent == null || sent.isEmpty()) {
      return null;
    }
    String value = sent.get(0);
    return value.isBlank() ? null : value;
  }

  String required(String name) {
    String value = first(name);
    if (value == null) {
      throw new IllegalStateException("read " + name + " before checking it was supplied");
    }
    return value;
  }

  SequencedMap<String, List<String>> forAudit() {
    LinkedHashMap<String, List<String>> body = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> field : values.entrySet()) {
      body.put(field.getKey(), List.copyOf(field.getValue()));
    }
    return body;
  }
}
