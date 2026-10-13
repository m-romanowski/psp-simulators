package dev.marcinromanowski.pspsim.kernel.settings;

public record ConfigurableSetting(SettingKey key, String valueItDefaultsTo) {

  public ConfigurableSetting {
    SettingGrammar.rejectAValueTheSettingCannotCarry(key, key.key(), valueItDefaultsTo);
  }
}
