package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class Disclosure {

  public enum Visibility {
    REDACTED_ON_WRITE,
    WITHHELD,
    SHOWN
  }

  public record Field(String name, Visibility visibility, int occurrences, List<String> values) {

    public Field {
      values = List.copyOf(values);
    }
  }

  private static final String ASKED = "show";

  private Disclosure() {}

  public static boolean valuesAsked(@Nullable String declared) {
    return ASKED.equals(declared);
  }

  public static List<Field> of(Map<String, List<String>> stored, boolean valuesAsked) {
    List<Field> fields = new ArrayList<>();
    for (Map.Entry<String, List<String>> entry : stored.entrySet()) {
      List<String> values = entry.getValue();
      fields.add(fieldOf(entry.getKey(), values, valuesAsked));
    }
    return List.copyOf(fields);
  }

  private static Field fieldOf(String name, List<String> values, boolean valuesAsked) {
    if (everyValueIsTheMarkerTheSimulatorStored(values)) {
      return new Field(name, Visibility.REDACTED_ON_WRITE, values.size(), List.of());
    }
    if (valuesAsked) {
      return new Field(name, Visibility.SHOWN, values.size(), values);
    }
    return new Field(name, Visibility.WITHHELD, values.size(), List.of());
  }

  private static boolean everyValueIsTheMarkerTheSimulatorStored(List<String> values) {
    if (values.isEmpty()) {
      return false;
    }
    for (String value : values) {
      if (!Redaction.REDACTED.equals(value)) {
        return false;
      }
    }
    return true;
  }
}
