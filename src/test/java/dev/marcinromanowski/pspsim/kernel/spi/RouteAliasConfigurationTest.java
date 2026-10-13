package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.MergedIdentities;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteAliasConfigurationTest {

  private static final TestRunId RUN = TestRunId.of("route-alias-config");

  private static final TestRunId ANOTHER_RUN = TestRunId.of("route-alias-config-other");

  private static final String PAYMENTS = "https://api.provider.example/v2/merchant-1/payments";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  private final Accounts accounts = new Accounts();
  private final BoundRouteAliases aliases = new BoundRouteAliases();
  private final ProviderConfigurations configurations =
      new ProviderConfigurations(
          accounts,
          aliases,
          new DeclaredProviderSettings(List.of()),
          new InMemoryTestRunSettings());

  @Test
  void a_harness_configures_a_url_and_the_test_run_answers_on_it() {
    ProviderDouble provider = new ProviderDouble().reading("userId");

    stored(
        configurations,
        provider,
        configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE))));

    assertThat(configurations.routeAliasesOf(provider, RUN))
        .singleElement()
        .satisfies(
            alias -> {
              assertThat(alias.from()).isEqualTo(PAYMENTS);
              assertThat(alias.to()).isEqualTo(CREATE);
              assertThat(alias.testRunId()).isEqualTo(RUN);
            });
  }

  @Test
  void a_url_is_bound_only_by_the_same_write_that_stores_the_accounts_beside_it() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    ProviderConfigurations abandoning =
        new ProviderConfigurations(
            new AccountsThatNeverCommit(),
            aliases,
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings());

    stored(
        abandoning, provider, configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE))));

    assertThat(aliases.of(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void a_configuration_that_says_nothing_about_an_alias_leaves_the_one_already_bound_alone() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    stored(
        configurations,
        provider,
        configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE))));

    stored(configurations, provider, configuration(List.of()));

    assertThat(configurations.routeAliasesOf(provider, RUN)).hasSize(1);
  }

  @Test
  void a_url_this_run_already_holds_is_repointed_at_the_route_the_latest_call_names() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    stored(
        configurations,
        provider,
        configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE))));

    stored(
        configurations,
        provider,
        configuration(
            List.of(
                new ConfiguredRouteAlias(PAYMENTS, "/api/light/server/1.0/refundTransaction"))));

    assertThat(configurations.routeAliasesOf(provider, RUN))
        .singleElement()
        .satisfies(
            alias -> assertThat(alias.to()).isEqualTo("/api/light/server/1.0/refundTransaction"));
  }

  @Test
  void a_url_another_test_run_holds_is_refused_and_names_the_run_that_holds_it() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    stored(
        configurations,
        provider,
        configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE))));

    ProviderConfiguration ofAnotherRun =
        new ProviderConfiguration(
            ANOTHER_RUN,
            List.of(),
            List.of(),
            List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE)),
            Map.of());

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, ofAnotherRun))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "routeAliases[0].from is already bound to test run route-alias-config of provider"
                + " twokeys")
        .isInstanceOfSatisfying(
            ProviderConfigurationRejectedException.class,
            refused -> assertThat(refused.property()).isEqualTo("routeAliases[0].from"));
    assertThat(aliases.of(ProviderDouble.ID, ANOTHER_RUN)).isEmpty();
  }

  @Test
  void a_url_another_provider_holds_is_refused_because_one_url_reaches_one_route() {
    ProviderDouble grow = new ProviderDouble("grow").reading("userId");
    ProviderDouble stripe = new ProviderDouble("stripe").reading("userId");
    stored(
        configurations, grow, configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE))));

    ProviderConfiguration claimingTheSameFrom =
        configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE)));

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(stripe, claimingTheSameFrom))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("of provider grow");
  }

  @Test
  void two_entries_of_one_call_claiming_one_url_are_refused_rather_than_resolved_by_the_last() {
    ProviderDouble provider = new ProviderDouble().reading("userId");

    ProviderConfiguration claimingOneFromTwice =
        configuration(
            List.of(
                new ConfiguredRouteAlias(PAYMENTS, CREATE),
                new ConfiguredRouteAlias(
                    "http://API.provider.example:9000/v2/merchant-1/payments",
                    "/api/light/server/1.0/refundTransaction")));

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, claimingOneFromTwice))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "routeAliases[1].from is already claimed by an earlier route alias of this"
                + " configuration")
        .isInstanceOfSatisfying(
            ProviderConfigurationRejectedException.class,
            refused -> assertThat(refused.property()).isEqualTo("routeAliases[1].from"));
    assertThat(aliases.of(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void an_alias_this_simulator_cannot_read_is_refused_before_any_account_of_the_call_is_stored() {
    ProviderDouble provider = new ProviderDouble().reading("userId");

    ProviderConfiguration namingARelativeFrom =
        configuration(List.of(new ConfiguredRouteAlias("/payments", CREATE)));

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, namingARelativeFrom))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage("routeAliases[0].from must be an absolute URL carrying a scheme and a host")
        .isInstanceOfSatisfying(
            ProviderConfigurationRejectedException.class,
            refused -> assertThat(refused.property()).isEqualTo("routeAliases[0].from"));
    assertThat(provider.itStored()).isEmpty();
    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void a_route_this_simulator_cannot_serve_is_refused_under_the_to_of_its_alias() {
    ProviderDouble provider = new ProviderDouble().reading("userId");

    ProviderConfiguration namingARouteOfTheControlApi =
        configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, "/__simulator/grow/config")));

    assertThatThrownBy(
            () -> configurations.theWriteItWouldMake(provider, namingARouteOfTheControlApi))
        .isInstanceOfSatisfying(
            ProviderConfigurationRejectedException.class,
            refused -> assertThat(refused.property()).isEqualTo("routeAliases[0].to"));
  }

  @Test
  void removing_an_account_leaves_the_urls_of_its_test_run_bound_because_no_alias_names_one() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    stored(
        configurations,
        provider,
        configuration(List.of(new ConfiguredRouteAlias(PAYMENTS, CREATE))));

    assertThat(configurations.remove(provider, RUN, "account-1")).isTrue();

    assertThat(configurations.identitiesOf(provider, RUN)).isEmpty();
    assertThat(configurations.routeAliasesOf(provider, RUN)).hasSize(1);
  }

  private static ProviderConfiguration configuration(List<ConfiguredRouteAlias> aliases) {
    return new ProviderConfiguration(
        RUN,
        List.of(new ConfiguredAccount("account-1", Map.of("userId", "u"))),
        List.of(),
        aliases,
        Map.of());
  }

  private static class Accounts implements ProviderAccounts {

    private final Map<TestRunId, List<String>> identities = new LinkedHashMap<>();
    private final Map<TestRunId, List<WebhookTarget>> targets = new LinkedHashMap<>();

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      if (!MergedIdentities.outOf(identities, targets, testRunId, accountId)) {
        return false;
      }
      plan.remove();
      return true;
    }

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      plan.store();
      MergedIdentities.into(identities, targets, testRunId, accountIds, webhookTargets);
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(identities.getOrDefault(testRunId, new ArrayList<>()));
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(targets.getOrDefault(testRunId, new ArrayList<>()));
    }
  }

  private static final class AccountsThatNeverCommit extends Accounts {

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {}
  }

  private static List<String> stored(
      ProviderConfigurations configurations,
      SimulatorProvider provider,
      ProviderConfiguration configuration) {
    return configurations.store(
        provider, configuration, configurations.theWriteItWouldMake(provider, configuration));
  }
}
