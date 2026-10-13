package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptRendering;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class AzericardModule {

  @Bean
  AzericardTerminals azericardTerminals(JdbcClient jdbc) {
    return new JdbcAzericardTerminals(jdbc);
  }

  @Bean
  ClaimConstraints azericardTerminalClaims() {
    return JdbcAzericardTerminals.CLAIMS;
  }

  @Bean
  AzericardPayments azericardPayments(
      JdbcClient jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcAzericardPayments(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  AzericardStoredCards azericardStoredCards(JdbcClient jdbc) {
    return new JdbcAzericardStoredCards(jdbc);
  }

  @Bean
  AzericardPayerSessions azericardPayerSessions(JdbcClient jdbc, ObjectMapper json) {
    return new JdbcAzericardPayerSessions(jdbc, json);
  }

  @Bean
  AzericardPayerCheckout azericardPayerCheckout(
      AzericardPayerSessions sessions,
      AzericardPayments payments,
      AzericardTerminals terminals,
      Authorize authorize,
      AzericardCallbacks callbacks,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      UnitOfWork unitOfWork) {
    return new AzericardPayerCheckout(
        sessions, payments, terminals, authorize, callbacks, audit, clock, ids, unitOfWork);
  }

  @Bean
  CompleteOrReverse azericardCompleteOrReverse(
      AzericardPayments payments, ScenarioEngine scenarios) {
    return new CompleteOrReverse(payments, scenarios);
  }

  @Bean
  Authorize azericardAuthorize(
      AzericardPayments payments,
      AzericardStoredCards cards,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new Authorize(payments, cards, ids, scenarios);
  }

  @Bean
  AzericardGateway azericardGateway(
      AzericardTerminals terminals,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      AzericardPayments payments,
      ScenarioEngine scenarios,
      Authorize authorize,
      CompleteOrReverse completeOrReverse,
      AzericardPayerCheckout checkout,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new AzericardGateway(
        terminals,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        authorize,
        completeOrReverse,
        new LookUpStatus(payments, scenarios),
        checkout);
  }

  @Bean
  AzericardCallbacks azericardCallbacks(
      AzericardPayments payments,
      AzericardTerminals terminals,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      SimulatorClock clock,
      IdGenerator ids) {
    return new AzericardCallbacks(payments, terminals, accounts, dispatcher, clock, ids);
  }

  @Bean
  AttemptRendering azericardCallbackRendering(ProviderAccounts accounts) {
    return new AzericardCallbackRendering(accounts);
  }

  @Bean
  AcknowledgementPolicy azericardCallbackAcknowledgement() {
    return new AzericardCallbackAcknowledgement();
  }

  @Bean
  SimulatorProvider azericardProvider(
      AzericardTerminals terminals,
      AuditTrail audit,
      AzericardPayments payments,
      AzericardStoredCards cards,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      AzericardCallbacks callbacks,
      WebhookDispatcher callbackDeliveries,
      SimulatorClock clock,
      IdGenerator ids,
      Authorize authorize,
      CompleteOrReverse completeOrReverse,
      AzericardPayerSessions sessions,
      AzericardPayerCheckout checkout) {
    return new AzericardProvider(
        terminals,
        audit,
        payments,
        cards,
        fixtures,
        scenarios,
        heldResponses,
        callbacks,
        callbackDeliveries,
        clock,
        ids,
        authorize,
        completeOrReverse,
        sessions,
        checkout);
  }
}
