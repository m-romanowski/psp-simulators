package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record DeclaredOrigination(
    String name,
    String label,
    String whatItLeavesBehind,
    ThePayerStep thePayerStep,
    List<AFieldATesterFillsIn> fields) {

  public DeclaredOrigination {
    if (name.isBlank()) {
      throw new IllegalArgumentException(
          "a way of starting a payment with no name cannot be asked for, because the name is what"
              + " a wizard sends back when a tester picks it");
    }
    if (label.isBlank()) {
      throw new IllegalArgumentException(
          "way of starting a payment "
              + name
              + " must carry a label, because a tester picks between these by what each one does"
              + " and not by the wire value the provider happens to spell it with");
    }
    if (whatItLeavesBehind.isBlank()) {
      throw new IllegalArgumentException(
          "way of starting a payment "
              + name
              + " must say what a tester is left holding once it has run: this is the choice that"
              + " decides the shape of everything after it, and a tester who cannot see that"
              + " consequence at the moment of choosing is guessing");
    }
    if (fields.isEmpty()) {
      throw new IllegalArgumentException(
          "way of starting a payment "
              + name
              + " asks a tester for nothing, so every payment started this way would be the same"
              + " payment and a wizard drawn from it would have nothing to lead anyone through");
    }
    Map<String, AFieldATesterFillsIn> byKey = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : fields) {
      if (byKey.put(field.key(), field) != null) {
        throw new IllegalArgumentException(
            "origination field "
                + field.key()
                + " is declared twice by "
                + name
                + ", so a wizard would draw two inputs writing one value");
      }
    }
    fields = List.copyOf(fields);
  }

  public List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (AFieldATesterFillsIn field : fields) {
      keys.add(field.key());
    }
    return List.copyOf(keys);
  }

  public Optional<AFieldATesterFillsIn> field(String key) {
    for (AFieldATesterFillsIn declared : fields) {
      if (declared.key().equals(key)) {
        return Optional.of(declared);
      }
    }
    return Optional.empty();
  }
}
