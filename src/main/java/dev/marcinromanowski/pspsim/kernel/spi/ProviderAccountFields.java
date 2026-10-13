package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ProviderAccountFields(ProviderId provider, List<ConfigurableField> fields) {

  public ProviderAccountFields {
    if (fields.isEmpty()) {
      throw new IllegalArgumentException(
          "a provider that declares no configurable account field could never have an account"
              + " configured, and a form drawn from it would have nothing to ask a tester for");
    }
    Map<String, ConfigurableField> byKey = new LinkedHashMap<>();
    for (ConfigurableField field : fields) {
      if (byKey.put(field.key(), field) != null) {
        throw new IllegalArgumentException(
            "configurable field "
                + field.key()
                + " is declared twice by one provider's account fields, so a form would draw two"
                + " inputs writing one value");
      }
      if (field.example() == null && field.acceptedValues().isEmpty()) {
        throw new IllegalArgumentException(
            "configurable field "
                + field.key()
                + " is drawn by a form as a box a tester types a value into, and declares no"
                + " example for that box to hint. The only other thing such a box could hint is"
                + " the key this provider reads the value under, which is a developer's name for"
                + " the field and not a value anybody could type: write an example this provider"
                + " would accept, and where no short value exists say so with a shape the form"
                + " draws differently");
      }
    }
    fields = List.copyOf(fields);
  }

  public List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (ConfigurableField field : fields) {
      keys.add(field.key());
    }
    return List.copyOf(keys);
  }

  public Set<String> secretKeys() {
    Set<String> secret = new LinkedHashSet<>();
    for (ConfigurableField field : fields) {
      if (field.secret()) {
        secret.add(field.key());
      }
    }
    return Set.copyOf(secret);
  }

  public boolean isShownInPlain(String key) {
    String asked = key.toLowerCase(Locale.ROOT);
    for (ConfigurableField field : fields) {
      if (field.key().toLowerCase(Locale.ROOT).equals(asked)) {
        return !field.secret();
      }
    }
    return false;
  }

  public Optional<ConfigurableField> field(String key) {
    for (ConfigurableField declared : fields) {
      if (declared.key().equals(key)) {
        return Optional.of(declared);
      }
    }
    return Optional.empty();
  }
}
