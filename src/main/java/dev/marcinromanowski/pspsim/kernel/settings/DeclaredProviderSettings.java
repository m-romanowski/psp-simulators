package dev.marcinromanowski.pspsim.kernel.settings;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeclaredProviderSettings {

  private final Map<ProviderId, ProviderSettings> byProvider;

  public DeclaredProviderSettings(List<ProviderSettings> declarations) {
    Map<ProviderId, ProviderSettings> claimed = new LinkedHashMap<>();
    for (ProviderSettings declaration : declarations) {
      ProviderSettings clash = claimed.put(declaration.provider(), declaration);
      if (clash != null) {
        throw new IllegalStateException(
            "two settings declarations claim the provider id " + declaration.provider().value());
      }
    }
    this.byProvider = Map.copyOf(claimed);
  }

  public ProviderSettings of(ProviderId provider) {
    ProviderSettings declared = byProvider.get(provider);
    return declared == null ? ProviderSettings.none(provider) : declared;
  }
}
