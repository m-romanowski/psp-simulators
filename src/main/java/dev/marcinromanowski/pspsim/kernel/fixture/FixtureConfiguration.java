package dev.marcinromanowski.pspsim.kernel.fixture;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fixture.internal.JdbcPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class FixtureConfiguration {

  @Bean
  public PaymentMethodFixtures paymentMethodFixtures(
      JdbcClient jdbc,
      ObjectMapper json,
      IdGenerator ids,
      SimulatorClock clock,
      PlatformTransactionManager transactionManager) {
    return new JdbcPaymentMethodFixtures(
        jdbc, json, ids, clock, new TransactionTemplate(transactionManager));
  }
}
