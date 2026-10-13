package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingGrammar;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ProviderRetryPlans implements CallbackRetryPlans {

  private final DeclaredProviderSettings declared;
  private final TestRunSettings configured;
  private final RetryPlan whenNoProviderDeclaresOne;

  public ProviderRetryPlans(
      DeclaredProviderSettings declared,
      TestRunSettings configured,
      RetryPlan whenNoProviderDeclaresOne) {
    this.declared = declared;
    this.configured = configured;
    this.whenNoProviderDeclaresOne = whenNoProviderDeclaresOne;
  }

  @Override
  public RetryPlan of(ProviderId provider, TestRunId testRunId) {
    Optional<ConfigurableSetting> setting =
        declared.of(provider).setting(SettingKey.WEBHOOK_RETRY_PLAN);
    if (setting.isEmpty()) {
      return whenNoProviderDeclaresOne;
    }
    @Nullable String chosen = configured.of(provider, testRunId).get(SettingKey.WEBHOOK_RETRY_PLAN);
    String value = chosen == null ? setting.get().valueItDefaultsTo() : chosen;
    return new RetryPlan(SettingGrammar.delaysOf(SettingKey.WEBHOOK_RETRY_PLAN.key(), value));
  }
}
