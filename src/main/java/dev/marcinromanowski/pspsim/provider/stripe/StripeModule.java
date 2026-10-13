package dev.marcinromanowski.pspsim.provider.stripe;

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
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptRendering;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class StripeModule {

  @Bean
  StripeAccounts stripeAccounts(JdbcClient jdbc) {
    return new JdbcStripeAccounts(jdbc);
  }

  @Bean
  ClaimConstraints stripeAccountClaims() {
    return JdbcStripeAccounts.CLAIMS;
  }

  @Bean
  StripePaymentIntents stripePaymentIntents(
      JdbcClient jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcStripePaymentIntents(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  StripeRefunds stripeRefunds(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcStripeRefunds(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  StripeCharges stripeCharges(JdbcClient jdbc) {
    return new JdbcStripeCharges(jdbc);
  }

  @Bean
  StripeCustomers stripeCustomers(JdbcClient jdbc) {
    return new JdbcStripeCustomers(jdbc);
  }

  @Bean
  StripePaymentMethods stripePaymentMethods(JdbcClient jdbc) {
    return new JdbcStripePaymentMethods(jdbc);
  }

  @Bean
  StripeSetupIntents stripeSetupIntents(
      JdbcClient jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcStripeSetupIntents(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  CreateSetupIntent stripeCreateSetupIntent(StripeSetupIntents setupIntents, IdGenerator ids) {
    return new CreateSetupIntent(setupIntents, ids);
  }

  @Bean
  ConfirmSetupIntent stripeConfirmSetupIntent(
      StripeSetupIntents setupIntents,
      StripePaymentMethods paymentMethods,
      StripeCustomers customers,
      ScenarioEngine scenarios) {
    return new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarios);
  }

  @Bean
  StripeEphemeralKeys stripeEphemeralKeys(JdbcClient jdbc) {
    return new JdbcStripeEphemeralKeys(jdbc);
  }

  @Bean
  StripeIdempotencyKeys stripeIdempotencyKeys(
      JdbcClient jdbc, PlatformTransactionManager transactionManager) {
    return new JdbcStripeIdempotencyKeys(jdbc, new TransactionTemplate(transactionManager));
  }

  @Bean
  CapturePaymentIntent stripeCapturePaymentIntent(
      StripePaymentIntents intents, ScenarioEngine scenarios) {
    return new CapturePaymentIntent(intents, scenarios);
  }

  @Bean
  CancelPaymentIntent stripeCancelPaymentIntent(
      StripePaymentIntents intents, ScenarioEngine scenarios) {
    return new CancelPaymentIntent(intents, scenarios);
  }

  @Bean
  CreateRefund stripeCreateRefund(
      StripePaymentIntents intents,
      StripeRefunds refunds,
      StripeCharges charges,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    return new CreateRefund(intents, refunds, charges, ids, scenarios);
  }

  @Bean
  CreatePaymentIntent stripeCreatePaymentIntent(
      StripePaymentIntents intents,
      StripePaymentMethods paymentMethods,
      StripeCharges charges,
      IdGenerator ids,
      ScenarioEngine scenarios,
      StripeWebhooks events) {
    return new CreatePaymentIntent(intents, paymentMethods, charges, ids, scenarios, events);
  }

  @Bean
  StripeApi stripeApi(
      StripeAccounts accounts,
      StripeIdempotencyKeys keys,
      StripeEphemeralKeys ephemeralKeys,
      StripePaymentIntents intents,
      StripeCharges charges,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods,
      TestRuns testRuns,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      ScenarioEngine scenarios,
      StripeWebhooks events,
      CapturePaymentIntent capturePaymentIntent,
      CancelPaymentIntent cancelPaymentIntent,
      CreateRefund createRefund,
      CreatePaymentIntent createPaymentIntent,
      CreateSetupIntent createSetupIntent,
      ConfirmSetupIntent confirmSetupIntent,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new StripeApi(
        accounts,
        keys,
        ephemeralKeys,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of(defaultTestRunId),
        createPaymentIntent,
        new ConfirmPaymentIntent(intents, paymentMethods, charges, ids, scenarios, events),
        capturePaymentIntent,
        cancelPaymentIntent,
        new RetrievePaymentIntent(intents),
        new RetrieveCharge(charges, intents),
        createRefund,
        customers,
        paymentMethods,
        createSetupIntent,
        confirmSetupIntent);
  }

  @Bean
  AcknowledgementPolicy stripeEventAcknowledgement() {
    return new StripeEventAcknowledgement();
  }

  @Bean
  AttemptRendering stripeWebhookRendering(StripeAccounts accounts) {
    return new StripeWebhookRendering(accounts);
  }

  @Bean
  StripeWebhooks stripeWebhooks(
      StripePaymentIntents intents,
      StripeRefunds refunds,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods,
      StripeSetupIntents setupIntents,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      SimulatorClock clock,
      IdGenerator ids) {
    return new StripeWebhooks(
        intents,
        refunds,
        customers,
        paymentMethods,
        setupIntents,
        accounts,
        dispatcher,
        clock,
        ids);
  }

  @Bean
  SimulatorProvider stripeProvider(
      StripeAccounts accounts,
      StripePaymentIntents intents,
      StripeCharges charges,
      StripeRefunds refunds,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods,
      StripeSetupIntents setupIntents,
      StripeEphemeralKeys ephemeralKeys,
      StripeIdempotencyKeys keys,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      WebhookDispatcher eventDeliveries,
      StripeWebhooks events,
      CapturePaymentIntent capturePaymentIntent,
      CancelPaymentIntent cancelPaymentIntent,
      CreateRefund createRefund,
      CreatePaymentIntent createPaymentIntent,
      CreateSetupIntent createSetupIntent,
      ConfirmSetupIntent confirmSetupIntent) {
    return new StripeProvider(
        accounts,
        intents,
        charges,
        refunds,
        customers,
        paymentMethods,
        setupIntents,
        ephemeralKeys,
        keys,
        audit,
        fixtures,
        scenarios,
        heldResponses,
        clock,
        eventDeliveries,
        events,
        capturePaymentIntent,
        cancelPaymentIntent,
        createRefund,
        createPaymentIntent,
        createSetupIntent,
        confirmSetupIntent);
  }
}
