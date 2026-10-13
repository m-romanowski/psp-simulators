package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProviderRegistry {

  private final Map<String, SimulatorProvider> byName;

  public ProviderRegistry(List<SimulatorProvider> providers) {
    Map<String, SimulatorProvider> claimed = new LinkedHashMap<>();
    for (SimulatorProvider provider : providers) {
      SimulatorProvider clash = claimed.put(provider.id().value(), provider);
      if (clash != null) {
        throw new IllegalStateException(
            "two provider modules claim the provider id " + provider.id().value());
      }
    }
    this.byName = Collections.unmodifiableMap(claimed);
  }

  public Optional<SimulatorProvider> find(String providerName) {
    return Optional.ofNullable(byName.get(providerName));
  }

  public List<String> names() {
    return List.copyOf(byName.keySet());
  }

  public List<SimulatorProvider> modules() {
    return List.copyOf(byName.values());
  }

  public String theRefusalOfAnUnknownProvider() {
    return "unknown provider; this simulator knows " + names();
  }
}
