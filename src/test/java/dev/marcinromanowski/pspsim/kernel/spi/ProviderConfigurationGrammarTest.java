package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProviderConfigurationGrammarTest {

  private static final TestRunId RUN = TestRunId.of("run-a");

  @Test
  void a_configuration_that_names_nothing_configures_nothing_and_is_not_refused() {
    ProviderConfiguration configuration =
        ProviderConfigurationGrammar.configurationOf(RUN, declaring(null, null, null));

    assertThat(configuration.testRunId()).isEqualTo(RUN);
    assertThat(configuration.accounts()).isEmpty();
    assertThat(configuration.webhookTargets()).isEmpty();
  }

  @Test
  void an_account_carries_its_identity_and_the_configuration_the_core_never_opens() {
    ProviderConfiguration configuration =
        ProviderConfigurationGrammar.configurationOf(
            RUN,
            declaring(
                List.of(
                    new DeclaredAccount(
                        "account-1", Map.of("processLookupRejection", "PUBLISHED_TABLE"))),
                null,
                null));

    assertThat(configuration.accounts())
        .containsExactly(
            new ConfiguredAccount(
                "account-1", Map.of("processLookupRejection", "PUBLISHED_TABLE")));
  }

  @Test
  void an_account_that_sends_no_configuration_carries_none_rather_than_failing_to_be_configured() {
    assertThat(
            ProviderConfigurationGrammar.configurationOf(
                    RUN, declaring(List.of(account("account-1")), null, null))
                .accounts())
        .singleElement()
        .satisfies(account -> assertThat(account.configuration()).isEmpty());
  }

  @Test
  void an_account_that_names_no_identity_is_rejected_by_its_position_and_property() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN, declaring(List.of(account("account-1"), account(null)), null, null)))
        .hasMessage("accounts[1].accountId is required");
  }

  @Test
  void an_account_position_that_holds_no_object_at_all_is_rejected_by_its_position() {
    DeclaredConfiguration declared = declaring(Collections.singletonList(null), null, null);

    assertThatThrownBy(() -> ProviderConfigurationGrammar.configurationOf(RUN, declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accounts[0] must name an account");
  }

  @Test
  void a_configured_value_that_is_absent_is_rejected_by_the_property_and_never_by_its_value() {
    Map<String, String> configuration = new HashMap<>();
    configuration.put("processLookupRejection", null);

    DeclaredConfiguration declared =
        declaring(List.of(new DeclaredAccount("account-1", configuration)), null, null);

    assertThatThrownBy(() -> ProviderConfigurationGrammar.configurationOf(RUN, declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "accounts[0].configuration.processLookupRejection must name the value the account is"
                + " configured with");
  }

  @Test
  void a_webhook_position_that_holds_no_object_at_all_is_rejected_by_its_position() {
    DeclaredConfiguration declared = declaring(null, Collections.singletonList(null), null);

    assertThatThrownBy(() -> ProviderConfigurationGrammar.configurationOf(RUN, declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("webhooks[0] must name a webhook target");
  }

  @Test
  void a_route_alias_position_that_holds_no_object_at_all_is_rejected_by_its_position() {
    DeclaredConfiguration declared = declaring(null, null, Collections.singletonList(null));

    assertThatThrownBy(() -> ProviderConfigurationGrammar.configurationOf(RUN, declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("routeAliases[0] must name a route alias");
  }

  @Test
  void a_second_account_of_one_identity_is_refused_rather_than_replacing_the_first() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(List.of(account("account-1"), account("account-1")), null, null)))
        .hasMessage("accounts[1].accountId is already configured by an earlier account");
  }

  @Test
  void a_webhook_target_carries_the_account_it_belongs_to_and_the_url_it_is_delivered_to() {
    assertThat(
            ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(
                        null,
                        List.of(
                            new DeclaredWebhookTarget(
                                "account-1", "https://merchant.example/hook")),
                        null))
                .webhookTargets())
        .containsExactly(new WebhookTarget("account-1", "https://merchant.example/hook"));
  }

  @Test
  void a_webhook_target_missing_the_account_or_the_url_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(
                        null,
                        List.of(
                            new DeclaredWebhookTarget("account-1", "https://a.example"),
                            new DeclaredWebhookTarget(null, "https://b.example")),
                        null)))
        .hasMessage("webhooks[1].accountId is required");
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(null, List.of(new DeclaredWebhookTarget("account-1", " ")), null)))
        .hasMessage("webhooks[0].url is required");
  }

  @Test
  void a_route_alias_is_validated_before_anything_is_configured_although_nothing_resolves_by_one() {
    assertThat(
            ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(
                        List.of(account("account-1")),
                        null,
                        List.of(
                            new DeclaredRouteAlias(
                                "https://sandbox.meshulam.co.il", "/api/light/server/1.0"))))
                .accounts())
        .hasSize(1);
  }

  @Test
  void a_route_alias_that_names_no_side_of_the_aliasing_is_rejected_by_its_position() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(
                        null,
                        null,
                        List.of(
                            new DeclaredRouteAlias("https://a.example", "/a"),
                            new DeclaredRouteAlias(null, "/x")))))
        .hasMessage("routeAliases[1].from is required");
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(
                        null, null, List.of(new DeclaredRouteAlias("https://a.example", " ")))))
        .hasMessage("routeAliases[0].to is required");
  }

  @Test
  void a_route_alias_is_refused_before_an_account_of_the_same_request_is_read() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(
                        List.of(account(null)), null, List.of(new DeclaredRouteAlias(null, "/x")))))
        .hasMessage("routeAliases[0].from is required");
  }

  @Test
  void the_urls_a_configuration_declares_reach_the_configuration_it_becomes() {
    ProviderConfiguration configuration =
        ProviderConfigurationGrammar.configurationOf(
            RUN,
            declaring(
                null,
                null,
                List.of(
                    new DeclaredRouteAlias(
                        "  https://api.provider.example/v2/merchant-1/payments  ",
                        "  /api/light/server/1.0/createPaymentProcess  "))));

    assertThat(configuration.routeAliases())
        .containsExactly(
            new ConfiguredRouteAlias(
                "https://api.provider.example/v2/merchant-1/payments",
                "/api/light/server/1.0/createPaymentProcess"));
  }

  @Test
  void a_configuration_that_declares_no_url_carries_none() {
    assertThat(
            ProviderConfigurationGrammar.configurationOf(RUN, declaring(null, null, null))
                .routeAliases())
        .isEmpty();
  }

  @Test
  void an_identity_a_harness_padded_is_configured_under_the_identity_a_caller_would_type() {
    ProviderConfiguration configuration =
        ProviderConfigurationGrammar.configurationOf(
            RUN,
            declaring(
                List.of(new DeclaredAccount("  account-1 ", Map.of("userId", "u"))), null, null));

    assertThat(configuration.accounts())
        .containsExactly(new ConfiguredAccount("account-1", Map.of("userId", "u")));
  }

  @Test
  void one_identity_spelled_two_ways_in_one_payload_is_refused_rather_than_configured_twice() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(List.of(account("account-1"), account("account-1 ")), null, null)))
        .hasMessage("accounts[1].accountId is already configured by an earlier account");
  }

  @Test
  void a_callback_target_padded_the_same_way_names_the_account_the_configuration_holds() {
    ProviderConfiguration configuration =
        ProviderConfigurationGrammar.configurationOf(
            RUN,
            declaring(
                List.of(account("account-1")),
                List.of(new DeclaredWebhookTarget(" account-1 ", "https://merchant.example/hook")),
                null));

    assertThat(configuration.webhookTargets())
        .containsExactly(new WebhookTarget("account-1", "https://merchant.example/hook"));
    assertThat(configuration.accounts())
        .containsExactly(new ConfiguredAccount("account-1", Map.of()));
  }

  @Test
  void an_identity_of_nothing_but_padding_is_rejected_rather_than_stripped_to_an_empty_one() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN, declaring(List.of(account("   ")), null, null)))
        .hasMessage("accounts[0].accountId is required");
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN,
                    declaring(
                        null,
                        List.of(new DeclaredWebhookTarget("  ", "https://merchant.example/hook")),
                        null)))
        .hasMessage("webhooks[0].accountId is required");
  }

  @Test
  void a_configuration_that_sets_no_setting_leaves_the_provider_on_what_it_declared() {
    ProviderConfiguration configuration =
        ProviderConfigurationGrammar.configurationOf(RUN, declaring(null, null, null));

    assertThat(configuration.settings()).isEmpty();
  }

  @Test
  void a_setting_a_harness_wrote_reaches_the_provider_under_the_key_it_wrote_it_under() {
    ProviderConfiguration configuration =
        ProviderConfigurationGrammar.configurationOf(
            RUN, declaringSettings(Map.of("  webhook.retryPlan  ", "PT0S,PT1S")));

    assertThat(configuration.settings())
        .containsExactly(Map.entry("webhook.retryPlan", "PT0S,PT1S"));
  }

  @Test
  void a_setting_written_under_no_key_at_all_is_refused_rather_than_silently_dropped() {
    assertThatThrownBy(
            () ->
                ProviderConfigurationGrammar.configurationOf(
                    RUN, declaringSettings(Map.of("   ", "PT0S"))))
        .hasMessage("settings names a setting with no key");
  }

  @Test
  void a_setting_written_with_no_value_is_refused_by_the_key_it_was_written_under() {
    Map<String, String> settings = new HashMap<>();
    settings.put("webhook.retryPlan", null);

    assertThatThrownBy(
            () -> ProviderConfigurationGrammar.configurationOf(RUN, declaringSettings(settings)))
        .hasMessage(
            "settings.webhook.retryPlan must name the value the setting is configured with");
  }

  @Test
  void one_setting_written_twice_under_one_key_is_refused_rather_than_taking_the_later_one() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("webhook.retryPlan", "PT0S");
    settings.put(" webhook.retryPlan ", "PT1S");

    assertThatThrownBy(
            () -> ProviderConfigurationGrammar.configurationOf(RUN, declaringSettings(settings)))
        .hasMessage("settings.webhook.retryPlan is already configured by an earlier key");
  }

  private static DeclaredConfiguration declaringSettings(Map<String, String> settings) {
    return new DeclaredConfiguration(null, null, null, null, null, settings);
  }

  private static DeclaredAccount account(@Nullable String accountId) {
    return new DeclaredAccount(accountId, null);
  }

  private static DeclaredConfiguration declaring(
      @Nullable List<DeclaredAccount> accounts,
      @Nullable List<DeclaredWebhookTarget> webhooks,
      @Nullable List<DeclaredRouteAlias> routeAliases) {
    return new DeclaredConfiguration(null, null, accounts, webhooks, routeAliases, null);
  }
}
