package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredRouteAlias;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

class GrowAccountTransactionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-account-transaction-it");

  private static final String USER_ID = "transaction-user";

  private static final String PAGE_CODE = "transaction-page";

  @Autowired private ProviderAccounts accounts;
  @Autowired private GrowAccounts growAccounts;
  @Autowired private ProviderRegistry providers;
  @Autowired private ProviderConfigurations configurations;
  @Autowired private RouteAliases routeAliases;
  @Autowired private JdbcClient jdbc;

  private static final String AN_IDENTITY_WIDER_THAN_THE_COLUMN = "w".repeat(129);

  private static final String ROLLED_BACK_URL =
      "https://rolled-back.provider.example/v2/merchant-1/payments";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  @AfterEach
  void clearWhatThisTestWrote() {
    jdbc.sql("delete from simulator_provider_account where test_run_id = ?")
        .param(RUN.value())
        .update();
    jdbc.sql("delete from simulator_webhook_target where test_run_id = ?")
        .param(RUN.value())
        .update();
    jdbc.sql("delete from grow_account where test_run_id = ?").param(RUN.value()).update();
    jdbc.sql("delete from simulator_route_alias where test_run_id = ?").param(RUN.value()).update();
  }

  @Test
  void a_url_the_core_transaction_rolled_back_answers_for_no_test_run() {
    SimulatorProvider grow = providers.find("grow").orElseThrow();
    ProviderConfiguration namingAnIdentityWiderThanTheColumn =
        new ProviderConfiguration(
            RUN,
            List.of(account("account-1")),
            List.of(
                new WebhookTarget(
                    AN_IDENTITY_WIDER_THAN_THE_COLUMN, "https://merchant.example/hook")),
            List.of(new ConfiguredRouteAlias(ROLLED_BACK_URL, CREATE)),
            Map.of());

    ProviderConfigurations.Storable storable =
        configurations.theWriteItWouldMake(grow, namingAnIdentityWiderThanTheColumn);

    assertThatThrownBy(
            () -> configurations.store(grow, namingAnIdentityWiderThanTheColumn, storable))
        .isInstanceOf(DataAccessException.class);

    assertThat(routeAliases.boundTo(RouteAliasGrammar.keyOf("from", ROLLED_BACK_URL))).isEmpty();
    assertThat(growAccounts.byUserId(USER_ID)).isEmpty();
    assertThat(accounts.identitiesOf(GrowLightApi.GROW, RUN)).isEmpty();
  }

  @Test
  void the_same_url_answers_for_the_test_run_when_the_core_transaction_commits() {
    SimulatorProvider grow = providers.find("grow").orElseThrow();
    ProviderConfiguration bindingTheUrl =
        new ProviderConfiguration(
            RUN,
            List.of(account("account-1")),
            List.of(),
            List.of(new ConfiguredRouteAlias(ROLLED_BACK_URL, CREATE)),
            Map.of());

    configurations.store(
        grow, bindingTheUrl, configurations.theWriteItWouldMake(grow, bindingTheUrl));

    assertThat(routeAliases.boundTo(RouteAliasGrammar.keyOf("from", ROLLED_BACK_URL)))
        .get()
        .satisfies(bound -> assertThat(bound.testRunId()).isEqualTo(RUN));
  }

  @Test
  void a_grow_account_the_core_transaction_rolled_back_is_gone_from_the_provider_table() {
    List<String> identities = List.of("account-1");
    List<WebhookTarget> targets =
        List.of(
            new WebhookTarget(AN_IDENTITY_WIDER_THAN_THE_COLUMN, "https://merchant.example/hook"));
    ProviderConfigurationPlan plan = planFor(account("account-1"));

    assertThatThrownBy(() -> accounts.merge(GrowLightApi.GROW, RUN, identities, targets, plan))
        .isInstanceOf(DataAccessException.class);

    assertThat(growAccounts.byUserId(USER_ID)).isEmpty();
    assertThat(accounts.identitiesOf(GrowLightApi.GROW, RUN)).isEmpty();
  }

  @Test
  void the_same_grow_account_is_there_when_the_core_transaction_commits() {
    accounts.merge(
        GrowLightApi.GROW, RUN, List.of("account-1"), List.of(), planFor(account("account-1")));

    assertThat(growAccounts.byUserId(USER_ID))
        .singleElement()
        .satisfies(stored -> assertThat(stored.accountId()).isEqualTo("account-1"));
  }

  private ProviderConfigurationPlan planFor(ConfiguredAccount... configured) {
    return providers
        .find("grow")
        .orElseThrow()
        .parse(new ProviderConfiguration(RUN, List.of(configured), List.of(), List.of(), Map.of()));
  }

  private static ConfiguredAccount account(String accountId) {
    return new ConfiguredAccount(accountId, Map.of("userId", USER_ID, "pageCode", PAGE_CODE));
  }
}
