package dev.marcinromanowski.pspsim.kernel.bundle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record PortableAccount(
    @Nullable String accountId,
    @Nullable Map<String, String> configuration,
    @Nullable List<String> secretsItDoesNotCarry) {

  public PortableAccount {
    configuration =
        configuration == null ? null : theConfigurationInTheOrderTheBundleCarriesIt(configuration);
    secretsItDoesNotCarry =
        secretsItDoesNotCarry == null ? null : List.copyOf(secretsItDoesNotCarry);
  }

  public Map<String, String> carriedConfiguration() {
    Map<String, String> carried = configuration;
    return carried == null ? Map.of() : carried;
  }

  public List<String> theSecretsItNames() {
    List<String> named = secretsItDoesNotCarry;
    return named == null ? List.of() : named;
  }

  public SequencedMap<String, String> alsoCarrying(Map<String, String> supplied) {
    LinkedHashMap<String, String> together = new LinkedHashMap<>(carriedConfiguration());
    together.putAll(supplied);
    return Collections.unmodifiableSequencedMap(together);
  }

  private static SequencedMap<String, String> theConfigurationInTheOrderTheBundleCarriesIt(
      Map<String, String> carried) {
    LinkedHashMap<String, String> inTheOrderTheBundleCarriesIt = new LinkedHashMap<>(carried);
    return Collections.unmodifiableSequencedMap(inTheOrderTheBundleCarriesIt);
  }
}
