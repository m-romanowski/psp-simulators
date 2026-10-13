package dev.marcinromanowski.pspsim.kernel.settings;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ProviderSettings(ProviderId provider, List<ConfigurableSetting> settings) {

  public ProviderSettings {
    Set<SettingKey> declared = EnumSet.noneOf(SettingKey.class);
    for (ConfigurableSetting setting : settings) {
      if (!declared.add(setting.key())) {
        throw new IllegalArgumentException(
            "setting "
                + setting.key().key()
                + " is declared twice by one provider, so two defaults would answer one key");
      }
    }
    settings = List.copyOf(settings);
  }

  public static ProviderSettings none(ProviderId provider) {
    return new ProviderSettings(provider, List.of());
  }

  public Optional<ConfigurableSetting> setting(SettingKey key) {
    for (ConfigurableSetting declared : settings) {
      if (declared.key() == key) {
        return Optional.of(declared);
      }
    }
    return Optional.empty();
  }

  public boolean declares(SettingKey key) {
    return setting(key).isPresent();
  }

  public List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (ConfigurableSetting declared : settings) {
      keys.add(declared.key().key());
    }
    return List.copyOf(keys);
  }
}
