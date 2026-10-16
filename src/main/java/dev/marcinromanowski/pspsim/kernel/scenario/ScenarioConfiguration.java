package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.internal.JdbcScenarioRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class ScenarioConfiguration {

  @Bean
  public ScenarioRules scenarioRules(
      JdbcClient jdbc,
      ObjectMapper json,
      IdGenerator ids,
      SimulatorClock clock,
      PlatformTransactionManager transactionManager) {
    return new JdbcScenarioRules(
        jdbc, json, ids, clock, new TransactionTemplate(transactionManager));
  }

  @Bean
  public ScenarioEngine scenarioEngine(ScenarioRules rules, OperationCodes codes) {
    return new ScenarioEngine(rules, codes);
  }

  @Bean
  public DeliveryShaping deliveryShaping(ScenarioRules rules) {
    return new DeliveryShaping(rules);
  }

  @Bean
  public ScenarioGrammar scenarioGrammar(FaultDelayCeiling faultDelayCeiling) {
    return new ScenarioGrammar(faultDelayCeiling);
  }
}
