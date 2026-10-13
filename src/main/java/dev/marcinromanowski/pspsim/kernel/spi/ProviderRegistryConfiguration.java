package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveries;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class ProviderRegistryConfiguration {

  @Bean
  public ProviderRegistry providerRegistry(List<SimulatorProvider> providers) {
    return new ProviderRegistry(providers);
  }

  @Bean
  @Primary
  public AuditTrail recordedRefusals(
      @Qualifier("auditTrail") AuditTrail trail, ObjectProvider<ProviderRegistry> providers) {
    return new RecordedRefusals(trail, providers::getObject);
  }

  @Bean
  public ProviderConfigurations providerConfigurations(
      ProviderAccounts accounts,
      RouteAliases routeAliases,
      DeclaredProviderSettings declaredSettings,
      TestRunSettings testRunSettings) {
    return new ProviderConfigurations(accounts, routeAliases, declaredSettings, testRunSettings);
  }

  @Bean
  public ConfigurationAdmission configurationAdmission(
      ProviderConfigurations configurations,
      FixedTimes fixedTimes,
      SimulatorClock clock,
      TestRuns testRuns,
      UnitOfWork unitOfWork,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new ConfigurationAdmission(
        configurations, fixedTimes, clock, testRuns, unitOfWork, TestRunId.of(defaultTestRunId));
  }

  @Bean
  public ClockAdvanceAdmission clockAdvanceAdmission(
      FixedTimes fixedTimes, @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new ClockAdvanceAdmission(fixedTimes, TestRunId.of(defaultTestRunId));
  }

  @Bean
  public PaymentMethodFixtureAdmission paymentMethodFixtureAdmission(
      PaymentMethodFixtures fixtures,
      ScenarioRules rules,
      TestRuns testRuns,
      UnitOfWork unitOfWork,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new PaymentMethodFixtureAdmission(
        fixtures, rules, testRuns, unitOfWork, TestRunId.of(defaultTestRunId));
  }

  @Bean
  public PersonaAdmission personaAdmission(
      ScenarioAdmission scenarios,
      PaymentMethodFixtureAdmission fixtureAdmission,
      PaymentMethodFixtures fixtures,
      IdGenerator ids,
      UnitOfWork unitOfWork,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new PersonaAdmission(
        scenarios, fixtureAdmission, fixtures, ids, unitOfWork, TestRunId.of(defaultTestRunId));
  }

  @Bean
  public RunBundles runBundles(
      ProviderAccounts accounts,
      RouteAliases routeAliases,
      TestRunSettings testRunSettings,
      DeclaredProviderSettings declaredSettings,
      PaymentMethodFixtures fixtures,
      ScenarioRules rules,
      FixedTimes fixedTimes,
      SimulatorClock clock,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new RunBundles(
        accounts,
        routeAliases,
        testRunSettings,
        declaredSettings,
        fixtures,
        rules,
        fixedTimes,
        clock,
        TestRunId.of(defaultTestRunId));
  }

  @Bean
  public BundleAdmission bundleAdmission(
      ProviderConfigurations configurations,
      ScenarioAdmission scenarios,
      ScenarioRules rules,
      PaymentMethodFixtureAdmission fixtureAdmission,
      ProviderAccounts accounts,
      RouteAliases routeAliases,
      TestRunSettings testRunSettings,
      DeclaredProviderSettings declaredSettings,
      PaymentMethodFixtures fixtures,
      FixedTimes fixedTimes,
      TestRuns testRuns,
      UnitOfWork unitOfWork,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new BundleAdmission(
        configurations,
        scenarios,
        rules,
        fixtureAdmission,
        accounts,
        routeAliases,
        testRunSettings,
        declaredSettings,
        fixtures,
        fixedTimes,
        testRuns,
        unitOfWork,
        TestRunId.of(defaultTestRunId));
  }

  @Bean
  public ReproductionBundles reproductionBundles(
      RunBundles runBundles,
      AuditReads audit,
      WebhookDeliveries deliveries,
      SimulatorClock clock,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new ReproductionBundles(
        runBundles, audit, deliveries, clock, TestRunId.of(defaultTestRunId));
  }

  @Bean
  public ReproductionAdmission reproductionAdmission(
      BundleAdmission bundleAdmission,
      PaymentMethodFixtures fixtures,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new ReproductionAdmission(bundleAdmission, fixtures, TestRunId.of(defaultTestRunId));
  }

  @Bean
  public ScenarioAdmission scenarioAdmission(
      ScenarioGrammar grammar,
      ScenarioRules rules,
      TestRuns testRuns,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    return new ScenarioAdmission(grammar, rules, testRuns, TestRunId.of(defaultTestRunId));
  }
}
