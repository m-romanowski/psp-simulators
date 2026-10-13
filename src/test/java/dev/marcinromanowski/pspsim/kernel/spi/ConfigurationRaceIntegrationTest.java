package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class ConfigurationRaceIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RACING = TestRunId.of("config-race-it");

  private static final TestRunId FIRST = TestRunId.of("config-race-it-first");

  private static final String PINNED = "2026-09-22T07:15:00Z";

  private static final String FROM =
      "https://race.provider.example/api/light/server/1.0/createPaymentProcess";

  private static final String TO = "/api/light/server/1.0/createPaymentProcess";

  private static final List<String> TABLES_OF_A_RUN =
      List.of(
          "simulator_provider_account",
          "simulator_webhook_target",
          "simulator_route_alias",
          "grow_account",
          "simulator_test_run");

  @Autowired private ProviderRegistry providers;
  @Autowired private ProviderAccounts accounts;
  @Autowired private RouteAliases routeAliases;
  @Autowired private DeclaredProviderSettings declaredSettings;
  @Autowired private TestRunSettings testRunSettings;
  @Autowired private FixedTimes fixedTimes;
  @Autowired private SimulatorClock clock;
  @Autowired private TestRuns testRuns;
  @Autowired private UnitOfWork unitOfWork;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcClient jdbc;

  @BeforeEach
  @AfterEach
  void forgetBothRuns() {
    for (TestRunId run : List.of(RACING, FIRST)) {
      for (String table : TABLES_OF_A_RUN) {
        jdbc.sql("delete from " + table + " where test_run_id = ?").param(run.value()).update();
      }
    }
  }

  @Test
  void a_route_alias_another_run_binds_after_the_dry_run_refuses_the_call_and_keeps_none_of_it() {
    RouteAliases claimedByAnotherRunFirst =
        new ClaimingFirst(routeAliases, () -> committedApart(this::bindTheAliasToTheFirstRun));

    ConfigurationAdmission.Verdict verdict =
        admission(accounts, claimedByAnotherRunFirst)
            .apply(grow(), configuring("u-racing", List.of(new DeclaredRouteAlias(FROM, TO))));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusal().message())
                    .isEqualTo(
                        "routeAliases[0].from names a URL this simulator has bound to another"
                            + " test run"));
    assertThatTheRacingRunHoldsNothing();
    assertThat(routeAliases.of(grow().id(), FIRST))
        .extracting(RouteAlias::from)
        .containsExactly(FROM);
  }

  @Test
  void the_store_names_the_alias_another_run_holds_as_the_property_of_its_claim() {
    bindTheAliasToTheFirstRun();

    assertThatThrownBy(
            () ->
                routeAliases.merge(
                    grow().id(),
                    RACING,
                    List.of(
                        RouteAliasGrammar.aliasOf(
                            grow().id(), RACING, "routeAliases[0]", FROM, TO))))
        .isInstanceOfSatisfying(
            ClaimedMeanwhileException.class,
            claimed -> assertThat(claimed.property()).isEqualTo("routeAliases[0].from"));
    assertThat(routeAliases.of(grow().id(), RACING)).isEmpty();
  }

  @Test
  void a_credential_another_account_stores_after_the_dry_run_refuses_the_call_and_keeps_none() {
    ProviderAccounts claimedByAnotherAccountFirst =
        new StoringFirst(accounts, () -> committedApart(this::storeAnotherAccountHoldingTheUser));

    ConfigurationAdmission.Verdict verdict =
        admission(claimedByAnotherAccountFirst, routeAliases)
            .apply(grow(), configuring("u-raced", List.of()));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusal().message())
                    .isEqualTo(
                        ClaimedMeanwhileException
                            .A_VALUE_THIS_REQUEST_WRITES_WAS_CLAIMED_MEANWHILE));
    assertThatTheRacingRunHoldsNothing();
    assertThat(
            jdbc.sql("select account_id from grow_account where test_run_id = ?")
                .param(RACING.value())
                .query(String.class)
                .list())
        .containsExactly("someone-else");
  }

  private void assertThatTheRacingRunHoldsNothing() {
    assertThat(testRuns.exists(RACING)).isFalse();
    assertThat(fixedTimes.of(RACING)).isEmpty();
    assertThat(accounts.identitiesOf(grow().id(), RACING)).isEmpty();
    assertThat(
            jdbc.sql("select count(*) from grow_account where test_run_id = ? and account_id = ?")
                .params(RACING.value(), "account-a")
                .query(Integer.class)
                .single())
        .isZero();
  }

  private void bindTheAliasToTheFirstRun() {
    routeAliases.merge(
        grow().id(),
        FIRST,
        List.of(RouteAliasGrammar.aliasOf(grow().id(), FIRST, "routeAliases[0]", FROM, TO)));
  }

  private void storeAnotherAccountHoldingTheUser() {
    grow()
        .parse(
            new ProviderConfiguration(
                RACING,
                List.of(
                    new ConfiguredAccount(
                        "someone-else", Map.of("userId", "u-raced", "pageCode", "p-someone"))),
                List.of(),
                List.of(),
                Map.of()))
        .store();
  }

  private void committedApart(Runnable write) {
    TransactionTemplate apart = new TransactionTemplate(transactionManager);
    apart.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    apart.executeWithoutResult(status -> write.run());
  }

  private SimulatorProvider grow() {
    return providers.find("grow").orElseThrow();
  }

  private static DeclaredConfiguration configuring(
      String userId, List<DeclaredRouteAlias> aliases) {
    return new DeclaredConfiguration(
        RACING.value(),
        new DeclaredClock(PINNED, null, null),
        List.of(new DeclaredAccount("account-a", Map.of("userId", userId, "pageCode", "p-racing"))),
        List.of(),
        aliases,
        Map.of());
  }

  private ConfigurationAdmission admission(ProviderAccounts accountsSeen, RouteAliases aliases) {
    return new ConfigurationAdmission(
        new ProviderConfigurations(accountsSeen, aliases, declaredSettings, testRunSettings),
        fixedTimes,
        clock,
        testRuns,
        unitOfWork,
        TestRunId.of("default"));
  }

  private record ClaimingFirst(RouteAliases delegate, Runnable claim) implements RouteAliases {

    @Override
    public void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> aliases) {
      claim.run();
      delegate.merge(provider, testRunId, aliases);
    }

    @Override
    public boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key) {
      return delegate.release(provider, testRunId, key);
    }

    @Override
    public List<RouteAlias> of(ProviderId provider, TestRunId testRunId) {
      return delegate.of(provider, testRunId);
    }

    @Override
    public Optional<RouteAlias> boundTo(RouteAliasKey key) {
      return delegate.boundTo(key);
    }
  }

  private record StoringFirst(ProviderAccounts delegate, Runnable claim)
      implements ProviderAccounts {

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      claim.run();
      delegate.merge(provider, testRunId, accountIds, webhookTargets, plan);
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      return delegate.remove(provider, testRunId, accountId, plan);
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return delegate.identitiesOf(provider, testRunId);
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return delegate.webhookTargetsOf(provider, testRunId);
    }
  }
}
