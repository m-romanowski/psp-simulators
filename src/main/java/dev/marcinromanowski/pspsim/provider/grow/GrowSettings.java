package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import java.util.List;

final class GrowSettings {

  static final String SIX_ATTEMPTS = "PT0S,PT1S,PT5S,PT30S,PT2M,PT5M";

  static final ProviderSettings DECLARED =
      new ProviderSettings(
          GrowLightApi.GROW,
          List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, SIX_ATTEMPTS)));

  private GrowSettings() {}
}
