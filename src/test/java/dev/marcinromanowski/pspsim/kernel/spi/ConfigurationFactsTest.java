package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigurationFactsTest {

  private static final ProviderId PROVIDER = ProviderId.of("aprovider");

  private static final String SECRET = "MUST-NEVER-REACH-AN-AUDIT-ROW";

  private static final ProviderAccountFields FIELDS =
      new ProviderAccountFields(
          PROVIDER,
          List.of(
              new ConfigurableField(
                  "userId",
                  "The user id",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "apiSecret",
                  "The signing secret",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "captureWindow",
                  "How long a hold lasts",
                  "a sentence a test does not read",
                  false,
                  false,
                  List.of(),
                  "a value a test does not read")));

  @Test
  void a_harness_reading_back_what_it_configured_sees_the_behaviour_and_never_the_credentials() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(FIELDS, configuring("merchant-1", everyFieldWithASecret()));

    assertThat(recorded)
        .containsEntry("accounts[0].accountId", List.of("merchant-1"))
        .containsEntry("accounts[0].configuration.captureWindow", List.of("PT168H"))
        .containsEntry("accounts[0].configuration.userId", List.of(Redaction.REDACTED))
        .containsEntry("accounts[0].configuration.apiSecret", List.of(Redaction.REDACTED));
    assertThat(String.valueOf(recorded)).doesNotContain(SECRET);
  }

  @Test
  void a_field_this_provider_never_declared_is_withheld_rather_than_printed_into_the_trail() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS, configuring("merchant-1", Map.of("aKeyNoProviderDeclares", SECRET)));

    assertThat(recorded)
        .containsEntry(
            "accounts[0].configuration.aKeyNoProviderDeclares", List.of(Redaction.REDACTED));
    assertThat(String.valueOf(recorded)).doesNotContain(SECRET);
  }

  @Test
  void a_secret_named_in_another_case_than_the_provider_declared_it_in_is_still_withheld() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(FIELDS, configuring("merchant-1", Map.of("APISECRET", SECRET)));

    assertThat(recorded)
        .containsEntry("accounts[0].configuration.APISECRET", List.of(Redaction.REDACTED));
  }

  @Test
  void a_call_configuring_two_accounts_keeps_them_apart_so_a_reader_can_tell_which_was_which() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run",
                null,
                List.of(
                    new DeclaredAccount("merchant-1", Map.of("captureWindow", "PT1H")),
                    new DeclaredAccount("merchant-2", Map.of("captureWindow", "PT2H"))),
                null,
                null,
                null));

    assertThat(recorded)
        .containsEntry("accounts[0].accountId", List.of("merchant-1"))
        .containsEntry("accounts[0].configuration.captureWindow", List.of("PT1H"))
        .containsEntry("accounts[1].accountId", List.of("merchant-2"))
        .containsEntry("accounts[1].configuration.captureWindow", List.of("PT2H"));
  }

  @Test
  void an_account_named_without_a_configuration_is_recorded_as_the_identity_it_named() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run", null, List.of(new DeclaredAccount("merchant-1", null)), null, null, null));

    assertThat(recorded).containsExactly(Map.entry("accounts[0].accountId", List.of("merchant-1")));
  }

  @Test
  void an_account_carrying_no_identity_at_all_is_left_unnamed_rather_than_named_as_nothing() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run",
                null,
                List.of(new DeclaredAccount(null, Map.of("captureWindow", "PT1H"))),
                null,
                null,
                null));

    assertThat(recorded).doesNotContainKey("accounts[0].accountId");
    assertThat(recorded).containsEntry("accounts[0].configuration.captureWindow", List.of("PT1H"));
  }

  @Test
  void a_tester_who_pinned_a_clock_finds_the_instant_they_pinned_it_to() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run",
                new DeclaredClock("2026-09-08T09:30:00Z", null, null),
                null,
                null,
                null,
                null));

    assertThat(recorded)
        .containsExactly(Map.entry("clock.fixedNow", List.of("2026-09-08T09:30:00Z")));
  }

  @Test
  void a_tester_who_let_the_clock_go_finds_the_release_and_not_an_empty_row() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run", new DeclaredClock(null, null, null), null, null, null, null));

    assertThat(recorded).containsExactly(Map.entry("clock.fixedNow", List.of("released")));
  }

  @Test
  void a_callback_target_and_a_route_alias_are_recorded_as_sent_because_neither_is_a_secret() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run",
                null,
                null,
                List.of(new DeclaredWebhookTarget("merchant-1", "https://merchant.test/hook")),
                List.of(
                    new DeclaredRouteAlias(
                        "https://provider.test/pay", "/api/light/server/1.0/createPaymentProcess")),
                null));

    assertThat(recorded)
        .containsEntry("webhooks[0].accountId", List.of("merchant-1"))
        .containsEntry("webhooks[0].url", List.of("https://merchant.test/hook"))
        .containsEntry("routeAliases[0].from", List.of("https://provider.test/pay"))
        .containsEntry("routeAliases[0].to", List.of("/api/light/server/1.0/createPaymentProcess"));
  }

  @Test
  void a_half_written_callback_target_or_alias_records_the_half_it_carried() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run",
                null,
                null,
                List.of(new DeclaredWebhookTarget(null, "https://merchant.test/hook")),
                List.of(new DeclaredRouteAlias("https://provider.test/pay", null)),
                null));

    assertThat(recorded).doesNotContainKey("webhooks[0].accountId");
    assertThat(recorded).containsEntry("webhooks[0].url", List.of("https://merchant.test/hook"));
    assertThat(recorded)
        .containsEntry("routeAliases[0].from", List.of("https://provider.test/pay"));
    assertThat(recorded).doesNotContainKey("routeAliases[0].to");
  }

  @Test
  void a_configuration_that_named_nothing_records_nothing_rather_than_a_row_of_empty_names() {
    assertThat(
            ConfigurationFacts.of(
                FIELDS, new DeclaredConfiguration("a-run", null, null, null, null, null)))
        .isEmpty();
  }

  @Test
  void the_trail_shows_the_setting_a_harness_wrote_and_the_run_it_wrote_it_for() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run", null, null, null, null, Map.of("webhook.retryPlan", "PT0S,PT1S")));

    assertThat(recorded)
        .containsExactly(Map.entry("settings.webhook.retryPlan", List.of("PT0S,PT1S")));
  }

  @Test
  void a_configuration_that_set_no_setting_records_no_setting_row() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run",
                null,
                null,
                List.of(new DeclaredWebhookTarget("merchant-1", "https://merchant.test/hook")),
                null,
                null));

    assertThat(recorded).doesNotContainKey("settings.webhook.retryPlan");
    assertThat(recorded).containsKey("webhooks[0].url");
  }

  @Test
  void a_configuration_refused_for_holding_nothing_where_an_account_belongs_still_records_facts() {
    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            FIELDS,
            new DeclaredConfiguration(
                "a-run", null, nothingAtAll(), nothingAtAll(), nothingAtAll(), null));

    assertThat(recorded).isEmpty();
  }

  @Test
  void a_configured_value_sent_as_nothing_at_all_is_left_out_rather_than_printed() {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put("captureWindow", null);
    configuration.put("apiSecret", SECRET);

    Map<String, List<String>> recorded =
        ConfigurationFacts.of(FIELDS, configuring("merchant-1", configuration));

    assertThat(recorded)
        .containsEntry("accounts[0].accountId", List.of("merchant-1"))
        .containsEntry("accounts[0].configuration.apiSecret", List.of(Redaction.REDACTED))
        .doesNotContainKey("accounts[0].configuration.captureWindow");
    assertThat(String.valueOf(recorded)).doesNotContain(SECRET);
  }

  private static <T> List<T> nothingAtAll() {
    List<T> holding = new ArrayList<>();
    holding.add(null);
    return Collections.unmodifiableList(holding);
  }

  private static DeclaredConfiguration configuring(
      String accountId, Map<String, String> configuration) {
    return new DeclaredConfiguration(
        "a-run", null, List.of(new DeclaredAccount(accountId, configuration)), null, null, null);
  }

  private static Map<String, String> everyFieldWithASecret() {
    Map<String, String> sent = new LinkedHashMap<>();
    sent.put("userId", SECRET);
    sent.put("apiSecret", SECRET);
    sent.put("captureWindow", "PT168H");
    return sent;
  }
}
