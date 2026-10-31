package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

sealed interface StripeParameter {

  record Scalar(String value) implements StripeParameter {}

  record Appended(List<String> values) implements StripeParameter {

    public Appended {
      values = List.copyOf(values);
    }
  }

  record Hashed(SequencedMap<String, StripeParameter> entries) implements StripeParameter {

    public Hashed {
      entries = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(entries));
    }
  }

  static Optional<List<StripeParameter>> elementsOf(StripeParameter parameter) {
    return switch (parameter) {
      case Scalar scalar -> scalar.value().isEmpty() ? Optional.of(List.of()) : Optional.empty();
      case Appended appended ->
          Optional.of(appended.values().stream().<StripeParameter>map(Scalar::new).toList());
      case Hashed hashed -> inIndexOrder(hashed);
    };
  }

  private static Optional<List<StripeParameter>> inIndexOrder(Hashed hashed) {
    List<StripeParameter> elements = new ArrayList<>();
    for (int index = 0; index < hashed.entries().size(); index++) {
      StripeParameter element = hashed.entries().get(Integer.toString(index));
      if (element == null) {
        return Optional.empty();
      }
      elements.add(element);
    }
    return Optional.of(List.copyOf(elements));
  }
}
