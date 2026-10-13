package dev.marcinromanowski.pspsim.kernel.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public enum SettingKey {
  WEBHOOK_RETRY_PLAN("webhook.retryPlan");

  private final String key;

  SettingKey(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public static Optional<SettingKey> named(@Nullable String name) {
    if (name == null) {
      return Optional.empty();
    }
    String asked = name.strip();
    for (SettingKey known : values()) {
      if (known.key.equals(asked)) {
        return Optional.of(known);
      }
    }
    return Optional.empty();
  }

  public static List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (SettingKey known : values()) {
      keys.add(known.key);
    }
    return List.copyOf(keys);
  }
}
