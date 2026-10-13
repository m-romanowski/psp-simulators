package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.bundle.BundleCompatibility;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;

public final class BundleVocabulary {

  private BundleVocabulary() {}

  public static BundleCompatibility of(SimulatorProvider module, ProviderSettings settings) {
    return new BundleCompatibility(
        module.accountFields().keys(),
        module.paymentMethodFields().keys(),
        settings.keys(),
        ScenarioGrammar.theDeclarableActionTypes());
  }
}
