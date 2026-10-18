package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeLogs;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class AutraModule {

  @Bean
  AutraMerchants autraMerchants(JdbcClient jdbc) {
    return new JdbcAutraMerchants(jdbc);
  }

  @Bean
  ClaimConstraints autraMerchantClaims() {
    return JdbcAutraMerchants.CLAIMS;
  }

  @Bean
  AutraSessions autraSessions(JdbcClient jdbc) {
    return new JdbcAutraSessions(jdbc);
  }

  @Bean
  AutraPayments autraPayments(
      JdbcClient jdbc, PlatformTransactionManager transactionManager, KeyedExchangeLogs exchanges) {
    return new JdbcAutraPayments(
        jdbc,
        new TransactionTemplate(transactionManager),
        exchanges.of(AutraKeyMechanisms.MECHANISMS, JdbcAutraPayments.EXCHANGES),
        exchanges.sequenced());
  }

  @Bean
  AutraPixPayments autraPixPayments(JdbcClient jdbc) {
    return new JdbcAutraPixPayments(jdbc);
  }

  @Bean
  AutraCardSessions autraCardSessions(JdbcClient jdbc) {
    return new JdbcAutraCardSessions(jdbc);
  }

  @Bean
  AutraPayerCards autraPayerCards(PaymentMethodFixtures fixtures) {
    return new AutraPayerCards(fixtures);
  }

  @Bean
  OpenCardSession autraOpenCardSession(
      AutraCardSessions cardSessions,
      IdGenerator ids,
      ScenarioEngine scenarios,
      @Value("${simulator.public-base-url}") String publicBaseUrl) {
    return new OpenCardSession(cardSessions, ids, scenarios, publicBaseUrl);
  }

  @Bean
  TokenizeOnTheHostedPage autraTokenizeOnTheHostedPage(
      AutraCardSessions cardSessions, AutraPayerCards cards, IdGenerator ids) {
    return new TokenizeOnTheHostedPage(cardSessions, cards, ids);
  }

  @Bean
  PayerOnTheHostedCardPage autraPayerOnTheHostedCardPage(
      AutraCardSessions cardSessions,
      @Qualifier("autraProvider") SimulatorProvider autraProvider,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    return new PayerOnTheHostedCardPage(cardSessions, autraProvider, audit, clock, ids);
  }

  @Bean
  AutraStoredCards autraStoredCards(
      AutraCardSessions cardSessions, PaymentMethodFixtures fixtures) {
    return new AutraStoredCards(cardSessions, fixtures);
  }

  @Bean
  ChargeStoredCard autraChargeStoredCard(
      AutraPayments payments,
      AutraSessions sessions,
      AutraStoredCards storedCards,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new ChargeStoredCard(payments, sessions, storedCards, ids, scenarios);
  }

  @Bean
  ProviderSettings autraSettings() {
    return AutraSettings.DECLARED;
  }

  @Bean
  AcknowledgementPolicy autraCallbackAcknowledgement() {
    return new AutraCallbackAcknowledgement();
  }

  @Bean
  AutraCallbacks autraCallbacks(
      AutraPixPayments pixPayments,
      AutraMerchants merchants,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      IdGenerator ids) {
    return new AutraCallbacks(pixPayments, merchants, accounts, dispatcher, ids);
  }

  @Bean
  CreatePixCheckout autraCreatePixCheckout(
      AutraPixPayments pixPayments,
      AutraSessions sessions,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new CreatePixCheckout(pixPayments, sessions, ids, scenarios);
  }

  @Bean
  CompletePixPayment autraCompletePixPayment(
      AutraPixPayments pixPayments, AutraCallbacks callbacks, UnitOfWork unitOfWork) {
    return new CompletePixPayment(pixPayments, callbacks, unitOfWork);
  }

  @Bean
  SettlePixPayment autraSettlePixPayment(
      AutraPixPayments pixPayments, AutraCallbacks callbacks, UnitOfWork unitOfWork) {
    return new SettlePixPayment(pixPayments, callbacks, unitOfWork);
  }

  @Bean
  IssueAccessToken autraIssueAccessToken(AutraSessions sessions, IdGenerator ids) {
    return new IssueAccessToken(sessions, ids);
  }

  @Bean
  OpenAcquiringSession autraOpenAcquiringSession(
      AutraSessions sessions, IdGenerator ids, ScenarioEngine scenarios) {
    return new OpenAcquiringSession(sessions, ids, scenarios);
  }

  @Bean
  PreAuthorize autraPreAuthorize(
      AutraPayments payments,
      AutraSessions sessions,
      AutraStoredCards storedCards,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new PreAuthorize(payments, sessions, storedCards, ids, scenarios);
  }

  @Bean
  CaptureHold autraCaptureHold(AutraPayments payments, ScenarioEngine scenarios) {
    return new CaptureHold(payments, scenarios);
  }

  @Bean
  ReverseOrRelease autraReverseOrRelease(AutraPayments payments, ScenarioEngine scenarios) {
    return new ReverseOrRelease(payments, scenarios);
  }

  @Bean
  LookUpPayment autraLookUpPayment(AutraPayments payments) {
    return new LookUpPayment(payments);
  }

  @Bean
  ListPayments autraListPayments(AutraPayments payments) {
    return new ListPayments(payments);
  }

  @Bean
  AutraApi autraApi(
      AutraMerchants merchants,
      AutraSessions sessions,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      IssueAccessToken issueAccessToken,
      OpenAcquiringSession openAcquiringSession,
      OpenCardSession openCardSession,
      ChargeStoredCard chargeStoredCard,
      PreAuthorize preAuthorize,
      CaptureHold captureHold,
      ReverseOrRelease reverseOrRelease,
      CreatePixCheckout createPixCheckout,
      LookUpPayment lookUpPayment,
      ListPayments listPayments,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new AutraApi(
        merchants,
        sessions,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        issueAccessToken,
        openAcquiringSession,
        openCardSession,
        chargeStoredCard,
        preAuthorize,
        captureHold,
        reverseOrRelease,
        createPixCheckout,
        lookUpPayment,
        listPayments);
  }

  @Bean
  SimulatorProvider autraProvider(
      AutraMerchants merchants,
      AutraSessions sessions,
      AutraPayments payments,
      AutraPixPayments pixPayments,
      AutraCardSessions cardSessions,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      OpenAcquiringSession openAcquiringSession,
      OpenCardSession openCardSession,
      ChargeStoredCard chargeStoredCard,
      TokenizeOnTheHostedPage tokenizeOnTheHostedPage,
      PreAuthorize preAuthorize,
      CaptureHold captureHold,
      ReverseOrRelease reverseOrRelease,
      CreatePixCheckout createPixCheckout,
      LookUpPayment lookUpPayment,
      ListPayments listPayments,
      CompletePixPayment completePixPayment,
      SettlePixPayment settlePixPayment,
      AutraCallbacks callbacks,
      WebhookDispatcher callbackDeliveries) {
    return new AutraProvider(
        merchants,
        sessions,
        payments,
        pixPayments,
        cardSessions,
        audit,
        fixtures,
        scenarios,
        heldResponses,
        clock,
        openAcquiringSession,
        openCardSession,
        chargeStoredCard,
        tokenizeOnTheHostedPage,
        preAuthorize,
        captureHold,
        reverseOrRelease,
        createPixCheckout,
        lookUpPayment,
        listPayments,
        completePixPayment,
        settlePixPayment,
        callbacks,
        callbackDeliveries);
  }
}
