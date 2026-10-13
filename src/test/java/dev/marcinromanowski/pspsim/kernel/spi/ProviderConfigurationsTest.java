package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.MergedIdentities;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.StoryKeyWitness;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderConfigurationsTest {

  private static final TestRunId RUN = TestRunId.of("configurations-test");

  private static final String SIX_ATTEMPTS = "PT0S,PT1S,PT5S,PT30S,PT2M,PT5M";

  private final Accounts accounts = new Accounts();

  private final InMemoryTestRunSettings settings = new InMemoryTestRunSettings();

  private final ProviderConfigurations configurations =
      new ProviderConfigurations(
          accounts, new BoundRouteAliases(), new DeclaredProviderSettings(List.of()), settings);

  private ProviderConfigurations configurationsOfAProviderThatDeclaresARetryPlan() {
    return new ProviderConfigurations(
        accounts,
        new BoundRouteAliases(),
        new DeclaredProviderSettings(
            List.of(
                new ProviderSettings(
                    ProviderDouble.ID,
                    List.of(
                        new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, SIX_ATTEMPTS))))),
        settings);
  }

  @Test
  void a_setting_a_provider_does_not_declare_is_refused_by_name() {
    ProviderDouble provider = new ProviderDouble();

    ProviderConfiguration configuration =
        new ProviderConfiguration(
            RUN,
            List.of(),
            List.of(),
            List.of(),
            Map.of(SettingKey.WEBHOOK_RETRY_PLAN.key(), SIX_ATTEMPTS));

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("settings.webhook.retryPlan")
        .hasMessageContaining("provider twokeys")
        .hasMessageNotContaining(SIX_ATTEMPTS);

    assertThat(settings.of(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void a_setting_no_provider_could_ever_declare_is_refused_by_the_name_the_harness_wrote() {
    ProviderDouble provider = new ProviderDouble();

    ProviderConfigurations configurations = configurationsOfAProviderThatDeclaresARetryPlan();
    ProviderConfiguration configuration =
        new ProviderConfiguration(
            RUN, List.of(), List.of(), List.of(), Map.of("webhook.retryBudget", SIX_ATTEMPTS));

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("settings.webhook.retryBudget")
        .hasMessageContaining("webhook.retryPlan");
  }

  @Test
  void a_retry_plan_a_harness_wrote_is_kept_for_the_test_run_it_named() {
    ProviderDouble provider = new ProviderDouble();

    stored(
        configurationsOfAProviderThatDeclaresARetryPlan(),
        provider,
        new ProviderConfiguration(
            RUN,
            List.of(),
            List.of(),
            List.of(),
            Map.of(SettingKey.WEBHOOK_RETRY_PLAN.key(), "PT0S,PT1S")));

    assertThat(settings.of(ProviderDouble.ID, RUN))
        .containsExactly(Map.entry(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S"));
    assertThat(settings.of(ProviderDouble.ID, TestRunId.of("another-run"))).isEmpty();
  }

  @Test
  void a_delay_a_harness_mistyped_is_refused_by_its_position_and_never_kept() {
    ProviderDouble provider = new ProviderDouble();

    ProviderConfigurations configurations = configurationsOfAProviderThatDeclaresARetryPlan();
    ProviderConfiguration configuration =
        new ProviderConfiguration(
            RUN,
            List.of(),
            List.of(),
            List.of(),
            Map.of(SettingKey.WEBHOOK_RETRY_PLAN.key(), "PT0S,every minute"));

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("settings.webhook.retryPlan[1]")
        .hasMessageContaining("ISO-8601")
        .hasMessageNotContaining("every minute");

    assertThat(settings.of(ProviderDouble.ID, RUN)).isEmpty();
    assertThat(provider.itStored()).isEmpty();
  }

  @Test
  void answers_the_identities_the_store_committed_rather_than_the_ones_the_request_listed() {
    ProviderDouble provider = new ProviderDouble().reading("terminalId");

    List<String> configured =
        stored(
            configurations,
            provider,
            new ProviderConfiguration(
                RUN,
                List.of(account("account-1", "terminalId", "7777")),
                List.of(new WebhookTarget("account-1", "https://merchant.example/hook")),
                List.of(),
                Map.of()));

    assertThat(configured).containsExactly("account-1");
    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).containsExactly("account-1");
    assertThat(accounts.webhookTargetsOf(ProviderDouble.ID, RUN)).hasSize(1);
    assertThat(provider.itStored()).hasSize(1);
  }

  @Test
  void refuses_a_configuration_key_the_provider_reads_nothing_of_naming_the_property() {
    ProviderDouble provider = new ProviderDouble().reading("terminalId");

    ProviderConfiguration configuration =
        new ProviderConfiguration(
            RUN,
            List.of(
                account("account-1", "terminalId", "7777"),
                account("account-2", "pageCode", "a-page")),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[1].configuration.pageCode")
        .hasMessageContaining("provider twokeys")
        .hasMessageNotContaining("a-page");

    assertThat(provider.itStored()).isEmpty();
    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void refuses_a_story_key_the_provider_could_spell_and_the_simulator_could_not_store() {
    ProviderDouble provider =
        new ProviderDouble()
            .reading("terminalId")
            .spelling(
                new StoryKeyWitness(
                    "accounts[0].configuration.terminalId",
                    StoryKey.inSpace("payment", "7".repeat(StoryKey.LONGEST_KEY))));

    ProviderConfiguration configuration =
        new ProviderConfiguration(
            RUN,
            List.of(account("account-1", "terminalId", "7777")),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> configurations.theWriteItWouldMake(provider, configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId")
        .hasMessageContaining("story key");

    assertThat(provider.itStored()).isEmpty();
  }

  @Test
  void takes_a_story_key_the_simulator_can_store_without_learning_a_word_of_it() {
    ProviderDouble provider =
        new ProviderDouble()
            .reading("terminalId")
            .spelling(
                new StoryKeyWitness(
                    "accounts[0].configuration.terminalId",
                    StoryKey.inSpace("payment", "8-7777777799999999")));

    stored(
        configurations,
        provider,
        new ProviderConfiguration(
            RUN,
            List.of(account("account-1", "terminalId", "7777")),
            List.of(),
            List.of(),
            Map.of()));

    assertThat(provider.itStored()).hasSize(1);
  }

  @Test
  void an_account_that_configures_nothing_is_taken_by_a_provider_that_reads_nothing() {
    ProviderDouble provider = new ProviderDouble();

    stored(
        configurations,
        provider,
        new ProviderConfiguration(
            RUN,
            List.of(new ConfiguredAccount("account-1", Map.of())),
            List.of(),
            List.of(),
            Map.of()));

    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).containsExactly("account-1");
  }

  private static ConfiguredAccount account(String accountId, String key, String value) {
    return new ConfiguredAccount(accountId, Map.of(key, value));
  }

  private static final class Accounts implements ProviderAccounts {

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
      return List.copyOf(identities.getOrDefault(testRunId, List.of()));
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(targets.getOrDefault(testRunId, List.of()));
    }
  }

  private static List<String> stored(
      ProviderConfigurations configurations,
      SimulatorProvider provider,
      ProviderConfiguration configuration) {
    return configurations.store(
        provider, configuration, configurations.theWriteItWouldMake(provider, configuration));
  }
}
