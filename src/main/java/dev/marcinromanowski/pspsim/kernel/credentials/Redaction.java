package dev.marcinromanowski.pspsim.kernel.credentials;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Redaction {

  public static final String REDACTED = "[redacted]";

  private Redaction() {}

  public static Map<String, List<String>> redact(
      Map<String, List<String>> values, Set<String> sensitiveKeys) {
    Set<String> sensitive = lowercased(sensitiveKeys);
    Map<String, List<String>> redacted = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : values.entrySet()) {
      boolean secret = sensitive.contains(entry.getKey().toLowerCase(Locale.ROOT));
      redacted.put(
          entry.getKey(), secret ? markerFor(entry.getValue()) : List.copyOf(entry.getValue()));
    }
    return redacted;
  }

  private static List<String> markerFor(List<String> values) {
    List<String> markers = new ArrayList<>();
    for (int i = 0; i < values.size(); i++) {
      markers.add(REDACTED);
    }
    return List.copyOf(markers);
  }

  private static Set<String> lowercased(Set<String> keys) {
    Set<String> lowercased = new HashSet<>();
    for (String key : keys) {
      lowercased.add(key.toLowerCase(Locale.ROOT));
    }
    return lowercased;
  }
}
