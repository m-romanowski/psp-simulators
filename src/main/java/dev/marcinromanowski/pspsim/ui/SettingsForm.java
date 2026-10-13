package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingGrammar;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationGrammar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class SettingsForm {

  public static final String SUBMITTED_SETTING_PREFIX = "setting.";

  public record Setting(
      String key,
      String label,
      String shape,
      String theValueTheTestRunUses,
      String theValueTheFieldCarries,
      boolean setByThisTestRun,
      boolean refused,
      String theValueTheProviderDefaultsTo) {}

  public record View(String provider, String testRunId, List<Setting> settings) {

    public View {
      settings = List.copyOf(settings);
    }
  }

  private SettingsForm() {}

  public static View of(
      String provider,
      String testRunId,
      ProviderSettings declared,
      Map<SettingKey, String> setByThisTestRun,
      Map<String, String> submitted,
      @Nullable String refusal) {
    List<Setting> settings = new ArrayList<>();
    for (ConfigurableSetting setting : declared.settings()) {
      SettingKey key = setting.key();
      @Nullable String configured = setByThisTestRun.get(key);
      String inUse = configured == null ? setting.valueItDefaultsTo() : configured;
      @Nullable String typed = submitted.get(key.key());
      settings.add(
          new Setting(
              key.key(),
              labelOf(key),
              shapeOf(key),
              inUse,
              typed == null ? inUse : typed,
              configured != null,
              isNamedBy(refusal, key),
              setting.valueItDefaultsTo()));
    }
    return new View(provider, testRunId, settings);
  }

  public static Map<String, String> submittedSettingsOf(Map<String, String> parameters) {
    Map<String, String> settings = new LinkedHashMap<>();
    for (Map.Entry<String, String> parameter : parameters.entrySet()) {
      String name = parameter.getKey();
      if (name.startsWith(SUBMITTED_SETTING_PREFIX)) {
        settings.put(name.substring(SUBMITTED_SETTING_PREFIX.length()), parameter.getValue());
      }
    }
    return Map.copyOf(settings);
  }

  public static DeclaredConfiguration settingsOf(
      String testRunId,
      ProviderSettings declared,
      Map<SettingKey, String> setByThisTestRun,
      Map<String, String> submitted) {
    Map<String, String> asked = new LinkedHashMap<>();
    for (Map.Entry<String, String> field : submitted.entrySet()) {
      if (!repeatsADefaultThisRunHasNotTakenOver(declared, setByThisTestRun, field)) {
        asked.put(field.getKey(), field.getValue());
      }
    }
    return new DeclaredConfiguration(testRunId, null, null, null, null, Map.copyOf(asked));
  }

  private static boolean repeatsADefaultThisRunHasNotTakenOver(
      ProviderSettings declared,
      Map<SettingKey, String> setByThisTestRun,
      Map.Entry<String, String> field) {
    Optional<ConfigurableSetting> setting =
        SettingKey.named(field.getKey()).flatMap(declared::setting);
    if (setting.isEmpty()) {
      return false;
    }
    return !setByThisTestRun.containsKey(setting.get().key())
        && setting.get().valueItDefaultsTo().equals(field.getValue());
  }

  private static boolean isNamedBy(@Nullable String refusal, SettingKey key) {
    return refusal != null
        && refusal.startsWith(ProviderConfigurationGrammar.settingProperty(key.key()));
  }

  private static String labelOf(SettingKey key) {
    return switch (key) {
      case WEBHOOK_RETRY_PLAN -> "the delay before each callback attempt";
    };
  }

  private static String shapeOf(SettingKey key) {
    return switch (key) {
      case WEBHOOK_RETRY_PLAN ->
          "one ISO-8601 duration per attempt, separated by commas, the first PT0S, none shorter"
              + " than the one before it, at most "
              + SettingGrammar.MOST_ATTEMPTS_A_RETRY_PLAN_MAY_HOLD;
    };
  }
}
