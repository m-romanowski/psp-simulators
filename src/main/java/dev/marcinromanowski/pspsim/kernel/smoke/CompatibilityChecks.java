package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.ArrayList;
import java.util.List;

public final class CompatibilityChecks {

  public record ProviderChecks(String provider, List<ContractCheck> declared) {

    public ProviderChecks {
      if (provider == null || provider.isBlank()) {
        throw new IllegalArgumentException("provider must name the module that published these");
      }
      declared = List.copyOf(declared);
    }
  }

  private final List<ProviderChecks> modules;

  public CompatibilityChecks(List<ProviderChecks> modules) {
    this.modules = List.copyOf(modules);
  }

  public List<ContractCheck> all() {
    List<ContractCheck> collected = new ArrayList<>();
    for (ProviderChecks module : modules) {
      collected.addAll(module.declared());
    }
    return List.copyOf(collected);
  }

  public List<String> providersWithNoCheckYet() {
    List<String> uncovered = new ArrayList<>();
    for (ProviderChecks module : modules) {
      if (module.declared().isEmpty()) {
        uncovered.add(module.provider());
      }
    }
    return List.copyOf(uncovered);
  }
}
