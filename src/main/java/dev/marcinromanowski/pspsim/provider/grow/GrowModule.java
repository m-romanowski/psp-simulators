package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class GrowModule {

  @Bean
  GrowAccounts growAccounts(JdbcClient jdbc) {
    return new JdbcGrowAccounts(jdbc);
  }

  @Bean
  ClaimConstraints growAccountClaims() {
    return JdbcGrowAccounts.CLAIMS;
  }

  @Bean
  GrowPaymentProcesses growPaymentProcesses(JdbcClient jdbc, ObjectMapper json) {
    return new JdbcGrowPaymentProcesses(jdbc, json);
  }

  @Bean
  GrowTransactions growTransactions(
      JdbcClient jdbc, PlatformTransactionManager transactionManager, ObjectMapper json) {
    return new JdbcGrowTransactions(jdbc, new TransactionTemplate(transactionManager), json);
  }

  @Bean
  SettleSuspendedTransaction growSettleSuspendedTransaction(
      GrowTransactions transactions, ScenarioEngine scenarios) {
    return new SettleSuspendedTransaction(transactions, scenarios, GrowScalarType.INTEGER);
  }

  @Bean
  RefundTransaction growRefundTransaction(
      GrowTransactions transactions, IdGenerator ids, ScenarioEngine scenarios) {
    return new RefundTransaction(transactions, ids, scenarios, GrowScalarType.INTEGER);
  }

  @Bean
  CreatePaymentProcess growCreatePaymentProcess(
      GrowPaymentProcesses processes,
      IdGenerator ids,
      ScenarioEngine scenarios,
      @Value("${simulator.public-base-url}") String publicBaseUrl) {
    return new CreatePaymentProcess(
        processes, ids, publicBaseUrl, scenarios, GrowScalarType.INTEGER);
  }

  @Bean
  CreateTransactionWithToken growCreateTransactionWithToken(
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new CreateTransactionWithToken(
        processes, transactions, ids, scenarios, GrowScalarType.INTEGER);
  }

  @Bean
  GetTokenTransactionsByExternalIdentifiers growGetTokenTransactionsByExternalIdentifiers(
      GrowPaymentProcesses processes, GrowTransactions transactions, ScenarioEngine scenarios) {
    return new GetTokenTransactionsByExternalIdentifiers(
        processes, transactions, scenarios, GrowScalarType.INTEGER);
  }

  @Bean
  GrowLightApi growLightApi(
      GrowAccounts accounts,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      WebhookDispatcher dispatcher,
      ScenarioEngine scenarios,
      SettleSuspendedTransaction settleSuspendedTransaction,
      RefundTransaction refundTransaction,
      CreatePaymentProcess createPaymentProcess,
      CreateTransactionWithToken createTransactionWithToken,
      GetTokenTransactionsByExternalIdentifiers retraceTheChargeOfASavedCard,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new GrowLightApi(
        accounts,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        createPaymentProcess,
        createTransactionWithToken,
        new ApproveTransaction(processes, dispatcher, scenarios, GrowScalarType.INTEGER),
        settleSuspendedTransaction,
        refundTransaction,
        new GetTransactionInfo(transactions, processes, scenarios, GrowScalarType.INTEGER),
        new GetPaymentProcessInfo(processes, transactions, scenarios, GrowScalarType.INTEGER),
        retraceTheChargeOfASavedCard,
        new CancelBitTransaction(GrowScalarType.INTEGER),
        new UpdateMyUrl(dispatcher, GrowScalarType.INTEGER));
  }

  @Bean
  AcknowledgementPolicy growCallbackAcknowledgement(GrowAccounts accounts) {
    return new GrowCallbackAcknowledgement(accounts);
  }

  @Bean
  ProviderSettings growSettings() {
    return GrowSettings.DECLARED;
  }

  @Bean
  GrowCallbacks growCallbacks(
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      SimulatorClock clock,
      IdGenerator ids,
      PlatformTransactionManager transactionManager) {
    return new GrowCallbacks(
        processes,
        transactions,
        accounts,
        dispatcher,
        clock,
        ids,
        new TransactionTemplate(transactionManager));
  }

  @Bean
  GrowPayerCards growPayerCards(PaymentMethodFixtures fixtures) {
    return new GrowPayerCards(fixtures);
  }

  @Bean
  CompleteHostedProcess growCompleteHostedProcess(
      GrowPaymentProcesses processes, GrowCallbacks callbacks, GrowPayerCards cards) {
    return new CompleteHostedProcess(processes, callbacks, cards);
  }

  @Bean
  GrowHostedCheckout growHostedCheckout(
      GrowPaymentProcesses processes,
      CompleteHostedProcess completions,
      GrowPayerCards cards,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    return new GrowHostedCheckout(processes, completions, cards, audit, clock, ids);
  }

  @Bean
  SimulatorProvider growProvider(
      GrowAccounts accounts,
      AuditTrail audit,
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      WebhookDispatcher dispatcher,
      GrowCallbacks callbacks,
      SimulatorClock clock,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SettleSuspendedTransaction settleSuspendedTransaction,
      RefundTransaction refundTransaction,
      CreatePaymentProcess createPaymentProcess,
      CreateTransactionWithToken createTransactionWithToken,
      GetTokenTransactionsByExternalIdentifiers retraceTheChargeOfASavedCard,
      CompleteHostedProcess completions) {
    return new GrowProvider(
        accounts,
        audit,
        processes,
        transactions,
        dispatcher,
        callbacks,
        clock,
        fixtures,
        scenarios,
        heldResponses,
        settleSuspendedTransaction,
        refundTransaction,
        createPaymentProcess,
        createTransactionWithToken,
        retraceTheChargeOfASavedCard,
        completions);
  }
}
