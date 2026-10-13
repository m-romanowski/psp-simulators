package dev.marcinromanowski.pspsim.platform;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CompatibilityChecks;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeNamespace;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SmokeConfiguration {

  @Bean
  CompatibilityChecks compatibilityChecks(
      ProviderRegistry providers, CheckJson json, IdGenerator ids) {
    List<CompatibilityChecks.ProviderChecks> modules = new ArrayList<>();
    for (SimulatorProvider provider : providers.modules()) {
      modules.add(
          new CompatibilityChecks.ProviderChecks(
              provider.id().value(), provider.compatibilityChecks(json, ids)));
    }
    return new CompatibilityChecks(modules);
  }

  @Bean
  SmokeRunner smokeRunner(SimulatorClock clock, ProviderAccounts accounts) {
    return new SmokeRunner(
        () -> clock.now(SmokeNamespace.RUN),
        provider -> accounts.identitiesOf(provider, SmokeNamespace.RUN));
  }
}
