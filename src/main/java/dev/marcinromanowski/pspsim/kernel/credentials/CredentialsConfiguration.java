package dev.marcinromanowski.pspsim.kernel.credentials;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.internal.JdbcProviderAccounts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class CredentialsConfiguration {

  @Bean
  public ProviderAccounts providerAccounts(
      JdbcClient jdbc, SimulatorClock clock, PlatformTransactionManager transactionManager) {
    return new JdbcProviderAccounts(jdbc, clock, new TransactionTemplate(transactionManager));
  }
}
