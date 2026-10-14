package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeLogs;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
  TranzilaTransactions tranzilaTransactions(
      JdbcClient jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcTranzilaTransactions(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  TranzilaRecords tranzilaRecords(JdbcClient jdbc) {
    return new JdbcTranzilaRecords(jdbc);
  }

  @Bean
  TranzilaNonces tranzilaNonces(JdbcClient jdbc) {
    return new JdbcTranzilaNonces(jdbc);
  }

  @Bean
  TranzilaExchanges tranzilaExchanges(JdbcClient jdbc, KeyedExchangeLogs exchanges) {
    return new JdbcTranzilaExchanges(
        jdbc, exchanges.of(TranzilaKeyMechanisms.MECHANISMS, JdbcTranzilaExchanges.EXCHANGES));
  }

  @Bean
  TranzilaPages tranzilaPages(JdbcClient jdbc) {
    return new JdbcTranzilaPages(jdbc);
  }

  @Bean
  TranzilaHandshakes tranzilaHandshakes(JdbcClient jdbc) {
    return new JdbcTranzilaHandshakes(jdbc);
  }

  @Bean
  TranzilaNotify tranzilaNotify(
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      TranzilaPages pages,
      TranzilaTransactions transactions) {
    return new TranzilaNotify(accounts, dispatcher, pages, transactions);
  }

  @Bean
  AcknowledgementPolicy tranzilaNotifyAcknowledgement() {
    return new TranzilaNotifyAcknowledgement();
  }

  @Bean
  TranzilaDirectNg tranzilaDirectNg(
      TranzilaTerminals terminals,
      TranzilaPages pages,
      TranzilaHandshakes handshakes,
      TranzilaTransactions transactions,
      CreateTransaction createTransaction,
      PaymentMethodFixtures fixtures,
      TranzilaNotify notify,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      UnitOfWork unitOfWork,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new TranzilaDirectNg(
        terminals,
        pages,
        handshakes,
        transactions,
        createTransaction,
        new TranzilaPageCards(fixtures),
        notify,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        unitOfWork);
  }

  @Bean
  CreateTransaction tranzilaCreateTransaction(
      TranzilaTransactions transactions,
      TranzilaPages pages,
      PaymentMethodFixtures fixtures,
      TranzilaExchanges exchanges,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new CreateTransaction(
        transactions, pages, new TranzilaPayerCards(fixtures), exchanges, ids, scenarios);
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
      TranzilaHandshakes handshakes,
      TranzilaRecords records,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new TranzilaApi(
        terminals,
        nonces,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        createTransaction,
        new CreateHandshake(handshakes, ids),
        new TranzilaLookups(records));
  }

  @Bean
  TranzilaHostedFields tranzilaHostedFields(
      TranzilaTerminals terminals,
      TranzilaHandshakes handshakes,
      TranzilaTransactions transactions,
      CreateTransaction createTransaction,
      PaymentMethodFixtures fixtures,
      TranzilaNotify notify,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new TranzilaHostedFields(
        terminals,
        handshakes,
        transactions,
        createTransaction,
        new TranzilaHostedCards(fixtures, ids),
        notify,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        TranzilaHostedFieldsScript.read());
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
      CreateTransaction createTransaction,
      TranzilaPages pages,
      TranzilaHandshakes handshakes,
      TranzilaDirectNg directNg,
      TranzilaNotify notify) {
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
        createTransaction,
        pages,
        handshakes,
        directNg,
        notify);
  }
}
