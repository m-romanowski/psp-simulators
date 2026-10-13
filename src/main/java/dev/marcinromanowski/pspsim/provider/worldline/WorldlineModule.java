package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class WorldlineModule {

  @Bean
  WorldlineMerchants worldlineMerchants(JdbcClient jdbc) {
    return new JdbcWorldlineMerchants(jdbc);
  }

  @Bean
  ClaimConstraints worldlineMerchantClaims() {
    return JdbcWorldlineMerchants.CLAIMS;
  }

  @Bean
  WorldlineTokenizations worldlineTokenizations(JdbcClient jdbc) {
    return new JdbcWorldlineTokenizations(jdbc);
  }

  @Bean
  WorldlinePayments worldlinePayments(
      JdbcClient jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcWorldlinePayments(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  WorldlineIdempotenceKeys worldlineIdempotenceKeys(JdbcClient jdbc) {
    return new JdbcWorldlineIdempotenceKeys(jdbc);
  }

  @Bean
  WorldlineAnswers worldlineAnswers(IdGenerator ids) {
    return new WorldlineAnswers(ids);
  }

  @Bean
  WorldlinePayerCards worldlinePayerCards(PaymentMethodFixtures fixtures) {
    return new WorldlinePayerCards(fixtures);
  }

  @Bean
  CreateHostedTokenization worldlineCreateHostedTokenization(
      WorldlineTokenizations sessions,
      WorldlineAnswers answers,
      IdGenerator ids,
      ScenarioEngine scenarios,
      @Value("${simulator.public-base-url}") String publicBaseUrl) {
    return new CreateHostedTokenization(sessions, answers, ids, scenarios, publicBaseUrl);
  }

  @Bean
  GetHostedTokenization worldlineGetHostedTokenization(
      WorldlineTokenizations sessions, WorldlineAnswers answers, ScenarioEngine scenarios) {
    return new GetHostedTokenization(sessions, answers, scenarios);
  }

  @Bean
  TokenizeOnTheHostedPage worldlineTokenizeOnTheHostedPage(
      WorldlineTokenizations sessions, WorldlinePayerCards cards, IdGenerator ids) {
    return new TokenizeOnTheHostedPage(sessions, cards, ids);
  }

  @Bean
  CreatePayment worldlineCreatePayment(
      WorldlinePayments payments,
      WorldlineTokenizations sessions,
      WorldlinePayerCards cards,
      WorldlineIdempotenceKeys keys,
      WorldlineAnswers answers,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new CreatePayment(payments, sessions, cards, keys, answers, ids, scenarios);
  }

  @Bean
  CapturePayment worldlineCapturePayment(
      WorldlinePayments payments,
      WorldlineAnswers answers,
      WorldlineIdempotenceKeys keys,
      ScenarioEngine scenarios) {
    return new CapturePayment(payments, answers, keys, scenarios);
  }

  @Bean
  CancelPayment worldlineCancelPayment(
      WorldlinePayments payments,
      WorldlineAnswers answers,
      WorldlineIdempotenceKeys keys,
      ScenarioEngine scenarios) {
    return new CancelPayment(payments, answers, keys, scenarios);
  }

  @Bean
  WorldlineApi worldlineApi(
      WorldlineMerchants merchants,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      WorldlineAnswers answers,
      CreateHostedTokenization createHostedTokenization,
      GetHostedTokenization getHostedTokenization,
      CreatePayment createPayment,
      CapturePayment capturePayment,
      CancelPayment cancelPayment,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new WorldlineApi(
        merchants,
        testRuns,
        audit,
        clock,
        ids,
        answers,
        TestRunId.of(defaultTestRunId),
        createHostedTokenization,
        getHostedTokenization,
        createPayment,
        capturePayment,
        cancelPayment);
  }

  @Bean
  WorldlineHostedTokenizationCheckout worldlineHostedTokenizationCheckout(
      WorldlineTokenizations sessions,
      @Qualifier("worldlineProvider") SimulatorProvider provider,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    return new WorldlineHostedTokenizationCheckout(sessions, provider, audit, clock, ids);
  }

  @Bean
  SimulatorProvider worldlineProvider(
      WorldlineMerchants merchants,
      WorldlineTokenizations sessions,
      WorldlinePayments payments,
      WorldlineIdempotenceKeys keys,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      CreateHostedTokenization createHostedTokenization,
      CreatePayment createPayment,
      CapturePayment capturePayment,
      CancelPayment cancelPayment,
      TokenizeOnTheHostedPage tokenizeOnTheHostedPage,
      IdGenerator ids) {
    return new WorldlineProvider(
        merchants,
        sessions,
        payments,
        keys,
        audit,
        fixtures,
        scenarios,
        heldResponses,
        clock,
        createHostedTokenization,
        createPayment,
        capturePayment,
        cancelPayment,
        tokenizeOnTheHostedPage,
        ids);
  }
}
