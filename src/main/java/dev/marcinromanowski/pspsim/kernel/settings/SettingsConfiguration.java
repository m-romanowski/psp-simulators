package dev.marcinromanowski.pspsim.kernel.settings;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.settings.internal.JdbcTestRunSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class SettingsConfiguration {

  @Bean
  public TestRunSettings testRunSettings(JdbcClient jdbc, SimulatorClock clock) {
    return new JdbcTestRunSettings(jdbc, clock);
  }

  @Bean
  public DeclaredProviderSettings declaredProviderSettings(
      ObjectProvider<ProviderSettings> declarations) {
    return new DeclaredProviderSettings(declarations.stream().toList());
  }
}
