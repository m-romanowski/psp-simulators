package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
public class TranzilaModule {

  @Bean
  TranzilaTerminals tranzilaTerminals(JdbcClient jdbc) {
    return new JdbcTranzilaTerminals(jdbc);
  }

  @Bean
  ClaimConstraints tranzilaTerminalClaims() {
    return JdbcTranzilaTerminals.CLAIMS;
  }

  @Bean
  TranzilaTransactions tranzilaTransactions(JdbcClient jdbc) {
    return new JdbcTranzilaTransactions(jdbc);
  }

  @Bean
  TranzilaNonces tranzilaNonces(JdbcClient jdbc) {
    return new JdbcTranzilaNonces(jdbc);
  }

  @Bean
  TranzilaExchanges tranzilaExchanges(JdbcClient jdbc) {
    return new JdbcTranzilaExchanges(jdbc);
  }

  @Bean
  CreateTransaction tranzilaCreateTransaction(
      TranzilaTransactions transactions,
      PaymentMethodFixtures fixtures,
      TranzilaExchanges exchanges,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new CreateTransaction(
        transactions, new TranzilaPayerCards(fixtures), exchanges, ids, scenarios);
  }

  @Bean
  TranzilaApi tranzilaApi(
      TranzilaTerminals terminals,
      TranzilaNonces nonces,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      CreateTransaction createTransaction,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new TranzilaApi(
        terminals,
        nonces,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        createTransaction);
  }

  @Bean
  TranzilaDelivery tranzilaDelivery(DelayedDeliveries delays, FaultDelayCeiling faultDelayCeiling) {
    return new TranzilaDelivery(delays, faultDelayCeiling.deliveryDeadline());
  }

  @Bean
  SimulatorProvider tranzilaProvider(
      TranzilaTerminals terminals,
      TranzilaTransactions transactions,
      TranzilaNonces nonces,
      TranzilaExchanges exchanges,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      CreateTransaction createTransaction) {
    return new TranzilaProvider(
        terminals,
        transactions,
        nonces,
        exchanges,
        audit,
        fixtures,
        scenarios,
        heldResponses,
        clock,
        createTransaction);
  }
}
