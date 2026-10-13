package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import java.util.ArrayList;
import java.util.List;

final class AutraSettings {

  static final int ATTEMPTS_A_MINUTE_APART = 10;

  static final int ATTEMPTS_TEN_MINUTES_APART = 5;

  static final int PUBLISHED_ATTEMPTS = 1 + ATTEMPTS_A_MINUTE_APART + ATTEMPTS_TEN_MINUTES_APART;

  static final String SIXTEEN_ATTEMPTS_IN_ABOUT_AN_HOUR = thePublishedPlan();

  static final ProviderSettings DECLARED =
      new ProviderSettings(
          AutraApi.AUTRA,
          List.of(
              new ConfigurableSetting(
                  SettingKey.WEBHOOK_RETRY_PLAN, SIXTEEN_ATTEMPTS_IN_ABOUT_AN_HOUR)));

  private AutraSettings() {}

  static String thePublishedPlan() {
    List<String> delays = new ArrayList<>();
    delays.add("PT0S");
    for (int attempt = 0; attempt < ATTEMPTS_A_MINUTE_APART; attempt++) {
      delays.add("PT1M");
    }
    for (int attempt = 0; attempt < ATTEMPTS_TEN_MINUTES_APART; attempt++) {
      delays.add("PT10M");
    }
    return String.join(",", delays);
  }
}
