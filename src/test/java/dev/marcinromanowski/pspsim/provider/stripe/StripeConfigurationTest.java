package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeConfigurationTest {

  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final String SECRET_KEY = "sk_test_51Simulator";

  private static final String SIGNING_SECRET = "whsec_theEndpointSecret";

  private final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
  private final StripeDoubles.Intents intents = new StripeDoubles.Intents();
  private final StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
  private final StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
  private final StripeDoubles.Customers customers = new StripeDoubles.Customers();
  private final StripeDoubles.PaymentMethods paymentMethods = new StripeDoubles.PaymentMethods();
  private final StripeDoubles.SetupIntents setupIntents =
      new StripeDoubles.SetupIntents(paymentMethods);
  private final StripeDoubles.Keys keys = new StripeDoubles.Keys();
  private final StripeDoubles.EphemeralKeys ephemeralKeys = new StripeDoubles.EphemeralKeys();
  private final StripeDoubles.Audit audit = new StripeDoubles.Audit();
  private final WebhookDispatcher deliveries = StripeDoubles.noDeliveries(accounts);

  @Test
  void refuses_an_account_that_leaves_the_capture_window_to_the_simulator() {
    Map<String, String> namingNoCaptureWindow =
        Map.of(StripeAccount.SECRET_KEY_PROPERTY, SECRET_KEY);

    assertThatThrownBy(() -> configure(namingNoCaptureWindow))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration." + StripeCaptureWindow.SETTING)
        .hasMessageContaining("will not adopt the vendor's default");
  }

  @Test
  void
      a_refusal_of_a_span_tells_whoever_reads_it_how_they_state_one_rather_than_only_how_a_call_does() {
    Map<String, String> namingNoCaptureWindow =
        Map.of(StripeAccount.SECRET_KEY_PROPERTY, SECRET_KEY);

    assertThatCode(() -> configure(namingNoCaptureWindow))
        .as(
            "this one sentence is read by a tester who states this on a form and by whoever"
                + " writes the value into a control call, and it told both of them to write an"
                + " ISO-8601 duration")
        .hasMessageContaining("a form states it as a number and a unit")
        .hasMessageContaining("the control API writes it as an ISO-8601 duration such as P3D");
    assertThatThrownBy(() -> configure(configuration("seven days")))
        .hasMessageContaining("a form states one as a number and a unit")
        .hasMessageContaining("the control API writes one as an ISO-8601 duration");
  }

  @Test
  void keeps_the_capture_window_the_account_chose_rather_than_the_vendors_default() {
    configure(configuration("P3D"));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            account -> assertThat(account.captureWindow().window()).isEqualTo(Duration.ofDays(3)));
    assertThat(Duration.ofDays(3)).isNotEqualTo(StripeCaptureWindow.THE_VENDORS_PUBLISHED_DEFAULT);
  }

  @Test
  void refuses_a_capture_window_that_is_not_a_duration() {
    Map<String, String> notADuration = configuration("seven days");

    assertThatThrownBy(() -> configure(notADuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeCaptureWindow.MUST_BE_A_DURATION);
  }

  @Test
  void refuses_a_capture_window_an_authorization_could_never_be_captured_within() {
    Map<String, String> noWindowAtAll = configuration("PT0S");

    assertThatThrownBy(() -> configure(noWindowAtAll))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeCaptureWindow.MUST_BE_POSITIVE);
  }

  @Test
  void keeps_the_longest_capture_window_a_deadline_can_still_be_travelled_to() {
    configure(configuration(StripeCaptureWindow.LONGEST.toString()));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            account ->
                assertThat(account.captureWindow().window())
                    .isEqualTo(StripeCaptureWindow.LONGEST));
  }

  @Test
  void hands_the_store_a_capture_window_it_can_read_back_unchanged() {
    StripeCaptureWindow configured = StripeCaptureWindow.of("accounts[0].configuration.", "P3D");

    assertThat(StripeCaptureWindow.stored(configured.storedForm())).isEqualTo(configured);
  }

  @Test
  void keeps_a_secret_key_of_exactly_the_width_the_store_holds() {
    String longest =
        "sk_test_"
            + "k".repeat(StripeAccount.LONGEST_SECRET_KEY_THE_STORE_HOLDS - "sk_test_".length());
    Map<String, String> declared = new LinkedHashMap<>();
    declared.put(StripeAccount.SECRET_KEY_PROPERTY, longest);
    declared.put(StripeCaptureWindow.SETTING, "P3D");

    configure(declared);

    assertThat(accounts.all())
        .singleElement()
        .satisfies(account -> assertThat(account.secretKey()).isEqualTo(longest));
  }

  @Test
  void refuses_a_capture_window_no_test_could_travel_to_the_end_of() {
    Map<String, String> longerThanTheStoreHolds = configuration("P400D");

    assertThatThrownBy(() -> configure(longerThanTheStoreHolds))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeCaptureWindow.MUST_BE_STORABLE);
  }

  @Test
  void refuses_an_account_that_names_no_secret_key() {
    Map<String, String> namingNoSecretKey = Map.of(StripeCaptureWindow.SETTING, "P3D");

    assertThatThrownBy(() -> configure(namingNoSecretKey))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeAccount.SECRET_KEY_IS_THE_ACCOUNT);
  }

  @Test
  void refuses_a_secret_key_that_is_not_a_test_mode_key() {
    Map<String, String> namingALiveModeKey =
        Map.of(
            StripeAccount.SECRET_KEY_PROPERTY,
            "sk_live_51Simulator",
            StripeCaptureWindow.SETTING,
            "P3D");

    assertThatThrownBy(() -> configure(namingALiveModeKey))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeAccount.SECRET_KEY_IS_A_TEST_MODE_KEY);
  }

  @Test
  void refuses_a_secret_key_wider_than_the_column_every_request_is_matched_against() {
    Map<String, String> namingAKeyWiderThanTheStoreHolds =
        Map.of(
            StripeAccount.SECRET_KEY_PROPERTY,
            "sk_test_" + "k".repeat(StripeAccount.LONGEST_SECRET_KEY_THE_STORE_HOLDS),
            StripeCaptureWindow.SETTING,
            "P3D");

    assertThatThrownBy(() -> configure(namingAKeyWiderThanTheStoreHolds))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeAccount.SECRET_KEY_IS_KEPT_WITH_EVERY_ACCOUNT);
  }

  @Test
  void refuses_two_accounts_of_one_test_run_that_claim_one_secret_key() {
    SimulatorProvider provider = provider();
    ProviderConfiguration twoAccountsOnOneSecretKey =
        new ProviderConfiguration(
            E2E_RUN,
            List.of(
                new ConfiguredAccount("merchant-1", configuration("P3D")),
                new ConfiguredAccount("merchant-2", configuration("P3D"))),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> provider.parse(twoAccountsOnOneSecretKey))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[1].configuration." + StripeAccount.SECRET_KEY_PROPERTY);
  }

  @Test
  void refuses_a_configuration_key_no_stripe_account_reads() {
    Map<String, String> declared = new LinkedHashMap<>(configuration("P3D"));
    declared.put("terminalId", "77777777");

    ProviderConfigurations configurations =
        new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings());
    SimulatorProvider provider = provider();
    ProviderConfiguration namingAKeyStripeNeverReads = configurationOf(declared);

    assertThatThrownBy(
            () -> configurations.theWriteItWouldMake(provider, namingAKeyStripeNeverReads))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId")
        .hasMessageContaining("is read by no account of provider stripe");
  }

  @Test
  void a_secret_key_an_account_this_call_leaves_in_place_holds_is_refused_by_name() {
    configure(configuration("P3D"));

    ProviderConfigurations configurations =
        new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings());
    SimulatorProvider provider = provider();
    ProviderConfiguration takingTheSecretKeyOfAnotherAccount =
        new ProviderConfiguration(
            E2E_RUN,
            List.of(new ConfiguredAccount("merchant-2", configuration("P3D"))),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(
            () -> configurations.theWriteItWouldMake(provider, takingTheSecretKeyOfAnotherAccount))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "accounts[0].configuration.secretKey is already configured for another account in"
                + " this test run");
  }

  @Test
  void an_account_the_core_removed_is_gone_from_stripes_own_table() {
    configure(configuration("P3D"));

    boolean removed =
        new ProviderConfigurations(
                accounts,
                new BoundRouteAliases(),
                new DeclaredProviderSettings(List.of()),
                new InMemoryTestRunSettings())
            .remove(provider(), E2E_RUN, "merchant-1");

    assertThat(removed).isTrue();
    assertThat(accounts.all()).isEmpty();
    assertThat(accounts.find(E2E_RUN, "merchant-1")).isEmpty();
  }

  @Test
  void the_secret_key_a_removed_account_held_is_free_for_another_account_of_the_same_run() {
    configure(configuration("P3D"));
    new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings())
        .remove(provider(), E2E_RUN, "merchant-1");

    store(
        new ProviderConfiguration(
            E2E_RUN,
            List.of(new ConfiguredAccount("merchant-2", configuration("P3D"))),
            List.of(),
            List.of(),
            Map.of()));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(held -> assertThat(held.accountId()).isEqualTo("merchant-2"));
  }

  @Test
  void stripe_publishes_the_fields_an_account_of_it_would_need_and_names_itself() {
    ProviderAccountFields declared = provider().accountFields();

    assertThat(declared.provider()).isEqualTo(StripeApi.STRIPE);
    assertThat(declared.keys())
        .containsExactlyInAnyOrderElementsOf(StripeAccount.CONFIGURATION_KEYS);
    assertThat(declared.secretKeys())
        .containsExactlyInAnyOrder(
            StripeAccount.SECRET_KEY_PROPERTY, StripeWebhookSigning.SECRET_SETTING);
    assertThat(declared.field(StripeAccount.SECRET_KEY_PROPERTY))
        .get()
        .satisfies(field -> assertThat(field.required()).isTrue());
    assertThat(declared.field(StripeWebhookSigning.SECRET_SETTING))
        .get()
        .satisfies(field -> assertThat(field.required()).isFalse());
  }

  @Test
  void answers_the_core_with_the_keys_it_reads_and_no_story_key_of_its_own() {
    ProviderConfigurationPlan plan = provider().parse(configurationOf(configuration("P3D")));

    assertThat(plan.configurationKeysItReads())
        .containsExactlyInAnyOrder(
            StripeAccount.SECRET_KEY_PROPERTY,
            StripeCaptureWindow.SETTING,
            StripeWebhookSigning.SECRET_SETTING,
            StripeWebhookSigning.EMISSION_SETTING,
            StripeWebhookSigning.AGE_SETTING,
            StripeRefundOutcome.SETTING);
    assertThat(plan.longestStoryKeysItCouldSpell()).isEmpty();
  }

  @Test
  void replaces_the_accounts_of_the_test_run_when_the_core_stores_the_plan() {
    configure(configuration("P3D"));

    configure(configuration("P14D"));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            account -> assertThat(account.captureWindow().window()).isEqualTo(Duration.ofDays(14)));
  }

  private void configure(Map<String, String> declared) {
    store(configurationOf(declared));
  }

  private void store(ProviderConfiguration configuration) {
    ProviderConfigurations configurations =
        new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings());
    SimulatorProvider provider = provider();
    configurations.store(
        provider, configuration, configurations.theWriteItWouldMake(provider, configuration));
  }

  private static ProviderConfiguration configurationOf(Map<String, String> declared) {
    return new ProviderConfiguration(
        E2E_RUN,
        List.of(new ConfiguredAccount("merchant-1", declared)),
        List.of(),
        List.of(),
        Map.of());
  }

  @Test
  void keeps_an_account_that_signs_nothing_signing_nothing() {
    configure(configuration("P3D"));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            account -> {
              assertThat(account.webhookSigning().secret()).isNull();
              assertThat(account.webhookSigning().emission())
                  .isEqualTo(StripeSignatureEmission.VALID);
              assertThat(account.webhookSigning().timestampAge()).isEqualTo(Duration.ZERO);
            });
  }

  @Test
  void refuses_a_signing_secret_of_a_shape_stripe_never_issues() {
    Map<String, String> namingASecretKeyToSignWith =
        signing(StripeWebhookSigning.SECRET_SETTING, "sk_test_51Simulator");

    assertThatThrownBy(() -> configure(namingASecretKeyToSignWith))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.SECRET_IS_A_SIGNING_SECRET);
  }

  @Test
  void keeps_a_signing_secret_of_exactly_the_width_the_store_holds() {
    String longest =
        StripeWebhookSigning.PUBLISHED_PREFIX
            + "s"
                .repeat(
                    StripeWebhookSigning.LONGEST_SECRET_THE_STORE_HOLDS
                        - StripeWebhookSigning.PUBLISHED_PREFIX.length());

    configure(signing(StripeWebhookSigning.SECRET_SETTING, longest));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(account -> assertThat(account.webhookSigning().secret()).isEqualTo(longest));
  }

  @Test
  void keeps_an_age_of_exactly_the_reach_the_store_holds() {
    configure(
        signing(
            StripeWebhookSigning.SECRET_SETTING,
            SIGNING_SECRET,
            StripeWebhookSigning.AGE_SETTING,
            StripeWebhookSigning.OLDEST_TIMESTAMP_THE_STORE_HOLDS.toString()));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            account ->
                assertThat(account.webhookSigning().timestampAge())
                    .isEqualTo(StripeWebhookSigning.OLDEST_TIMESTAMP_THE_STORE_HOLDS));
  }

  @Test
  void reads_back_an_account_the_store_kept_exactly_as_it_was_configured() {
    StripeWebhookSigning kept = StripeWebhookSigning.stored(SIGNING_SECRET, "CORRUPTED", "PT5M");

    assertThat(kept.secret()).isEqualTo(SIGNING_SECRET);
    assertThat(kept.emission()).isEqualTo(StripeSignatureEmission.CORRUPTED);
    assertThat(kept.timestampAge()).isEqualTo(Duration.ofMinutes(5));
    assertThat(kept.storedAge()).isEqualTo("PT5M");
    assertThat(StripeSignatureEmission.stored("VALID")).isEqualTo(StripeSignatureEmission.VALID);
  }

  @Test
  void refuses_a_signing_secret_longer_than_the_account_row_holds() {
    Map<String, String> namingASecretWiderThanTheStoreHolds =
        signing(
            StripeWebhookSigning.SECRET_SETTING,
            StripeWebhookSigning.PUBLISHED_PREFIX
                + "s"
                    .repeat(
                        StripeWebhookSigning.LONGEST_SECRET_THE_STORE_HOLDS
                            - StripeWebhookSigning.PUBLISHED_PREFIX.length()
                            + 1));

    assertThatThrownBy(() -> configure(namingASecretWiderThanTheStoreHolds))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.SECRET_IS_KEPT_WITH_EVERY_ACCOUNT);
  }

  @Test
  void keeps_the_age_the_account_gives_the_timestamp_it_emits() {
    configure(
        signing(
            StripeWebhookSigning.SECRET_SETTING,
            SIGNING_SECRET,
            StripeWebhookSigning.AGE_SETTING,
            "PT5M"));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            account -> {
              assertThat(account.webhookSigning().secret()).isEqualTo(SIGNING_SECRET);
              assertThat(account.webhookSigning().timestampAge()).isEqualTo(Duration.ofMinutes(5));
              assertThat(
                      account.webhookSigning().timestampFor(Instant.parse("2026-09-11T12:00:00Z")))
                  .isEqualTo(Instant.parse("2026-09-11T11:55:00Z"));
            });
  }

  @Test
  void refuses_an_age_that_would_date_a_delivery_ahead_of_the_runs_own_clock() {
    Map<String, String> namingANegativeAge =
        signing(
            StripeWebhookSigning.SECRET_SETTING,
            SIGNING_SECRET,
            StripeWebhookSigning.AGE_SETTING,
            "PT-5M");

    assertThatThrownBy(() -> configure(namingANegativeAge))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.AGE_IS_NOT_A_FUTURE_TIMESTAMP);
  }

  @Test
  void refuses_an_age_that_is_not_a_duration() {
    Map<String, String> namingAnAgeThatIsNoDuration =
        signing(
            StripeWebhookSigning.SECRET_SETTING,
            SIGNING_SECRET,
            StripeWebhookSigning.AGE_SETTING,
            "five minutes");

    assertThatThrownBy(() -> configure(namingAnAgeThatIsNoDuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.AGE_IS_A_DURATION);
  }

  @Test
  void refuses_an_age_beyond_what_the_account_row_holds() {
    Map<String, String> namingAnAgeOlderThanTheStoreHolds =
        signing(
            StripeWebhookSigning.SECRET_SETTING,
            SIGNING_SECRET,
            StripeWebhookSigning.AGE_SETTING,
            StripeWebhookSigning.OLDEST_TIMESTAMP_THE_STORE_HOLDS.plusSeconds(1).toString());

    assertThatThrownBy(() -> configure(namingAnAgeOlderThanTheStoreHolds))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.AGE_IS_STORABLE);
  }

  @Test
  void refuses_a_signature_emission_no_delivery_of_the_account_would_carry() {
    Map<String, String> namingAnEmissionWithNothingToSign =
        signing(StripeWebhookSigning.EMISSION_SETTING, StripeSignatureEmission.CORRUPTED.name());

    assertThatThrownBy(() -> configure(namingAnEmissionWithNothingToSign))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.EMISSION_SETTING)
        .hasMessageContaining(StripeWebhookSigning.NOTHING_TO_SIGN);
  }

  @Test
  void refuses_a_timestamp_age_no_delivery_of_the_account_would_carry() {
    Map<String, String> anAgeWithNothingToSign = signing(StripeWebhookSigning.AGE_SETTING, "PT5M");

    assertThatThrownBy(() -> configure(anAgeWithNothingToSign))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.AGE_SETTING)
        .hasMessageContaining(StripeWebhookSigning.NOTHING_TO_SIGN);
  }

  @Test
  void refuses_a_signature_emission_this_simulator_has_no_name_for() {
    Map<String, String> namingAnEmissionOutsideTheTwo =
        signing(
            StripeWebhookSigning.SECRET_SETTING,
            SIGNING_SECRET,
            StripeWebhookSigning.EMISSION_SETTING,
            "WRONG");

    assertThatThrownBy(() -> configure(namingAnEmissionOutsideTheTwo))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeWebhookSigning.EMISSION_IS_ONE_OF_TWO);
  }

  @Test
  void refuses_a_refund_status_the_vendor_does_not_publish_on_the_refund_object() {
    Map<String, String> notAPublishedRefundStatus =
        signing(StripeRefundOutcome.SETTING, "DISPUTED");

    assertThatThrownBy(() -> configure(notAPublishedRefundStatus))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(StripeRefundOutcome.MUST_BE_A_PUBLISHED_REFUND_STATUS);
  }

  @Test
  void settles_refunds_the_way_a_card_refund_settles_when_the_account_names_no_status() {
    configure(configuration("P3D"));

    Map<String, List<String>> disclosed =
        provider().disclosureOf(E2E_RUN, "merchant-1").orElseThrow().disclosed();

    assertThat(disclosed.get(StripeRefundOutcome.SETTING))
        .containsExactly(StripeRefundOutcome.THE_ONE_A_CARD_REFUND_REACHES.name());
    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            account ->
                assertThat(account.refundOutcome().status())
                    .isEqualTo(StripeRefundStatus.SUCCEEDED));
  }

  @Test
  void keeps_the_refund_status_an_account_names_so_a_test_can_reach_all_five() {
    configure(signing(StripeRefundOutcome.SETTING, StripeRefundStatus.REQUIRES_ACTION.name()));

    Map<String, List<String>> disclosed =
        provider().disclosureOf(E2E_RUN, "merchant-1").orElseThrow().disclosed();

    assertThat(disclosed.get(StripeRefundOutcome.SETTING))
        .containsExactly(StripeRefundStatus.REQUIRES_ACTION.name());
  }

  @Test
  void refuses_a_stored_signature_emission_this_simulator_has_no_name_for() {
    assertThatThrownBy(() -> StripeWebhookSigning.stored(SIGNING_SECRET, "WRONG", "PT0S"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("WRONG");
  }

  private static Map<String, String> signing(String... keysAndValues) {
    Map<String, String> declared = configuration("P3D");
    for (int i = 0; i < keysAndValues.length; i += 2) {
      declared.put(keysAndValues[i], keysAndValues[i + 1]);
    }
    return declared;
  }

  @Test
  void the_disclosure_of_a_stripe_account_names_every_configuration_key_stripe_reads() {
    configure(configuration("P3D"));

    DisclosedAccount disclosed = provider().disclosureOf(E2E_RUN, "merchant-1").orElseThrow();

    assertThat(disclosed.accountId()).isEqualTo("merchant-1");
    assertThat(disclosed.disclosed().keySet())
        .containsExactlyInAnyOrderElementsOf(StripeAccount.CONFIGURATION_KEYS);
  }

  @Test
  void stripe_answers_a_marker_for_the_secret_key_a_request_authenticates_on() {
    configure(configuration("P3D"));

    Map<String, List<String>> disclosed =
        provider().disclosureOf(E2E_RUN, "merchant-1").orElseThrow().disclosed();

    assertThat(disclosed.get(StripeAccount.SECRET_KEY_PROPERTY))
        .containsExactly(Redaction.REDACTED);
    assertThat(disclosed.get(StripeCaptureWindow.SETTING)).containsExactly("PT72H");
    assertThat(disclosed.values().stream().flatMap(List::stream)).doesNotContain(SECRET_KEY);
  }

  @Test
  void an_account_that_signs_nothing_discloses_its_signing_settings_as_configured_by_nobody() {
    configure(configuration("P3D"));

    Map<String, List<String>> disclosed =
        provider().disclosureOf(E2E_RUN, "merchant-1").orElseThrow().disclosed();

    assertThat(disclosed.get(StripeWebhookSigning.SECRET_SETTING)).isEmpty();
    assertThat(disclosed.get(StripeWebhookSigning.EMISSION_SETTING)).isEmpty();
    assertThat(disclosed.get(StripeWebhookSigning.AGE_SETTING)).isEmpty();
  }

  @Test
  void an_account_that_signs_its_callbacks_discloses_the_signing_plan_but_never_the_secret() {
    Map<String, String> declared = configuration("P3D");
    declared.put(StripeWebhookSigning.SECRET_SETTING, SIGNING_SECRET);
    declared.put(StripeWebhookSigning.AGE_SETTING, "PT6M");
    configure(declared);

    Map<String, List<String>> disclosed =
        provider().disclosureOf(E2E_RUN, "merchant-1").orElseThrow().disclosed();

    assertThat(disclosed.get(StripeWebhookSigning.SECRET_SETTING))
        .containsExactly(Redaction.REDACTED);
    assertThat(disclosed.get(StripeWebhookSigning.EMISSION_SETTING))
        .containsExactly(StripeSignatureEmission.VALID.name());
    assertThat(disclosed.get(StripeWebhookSigning.AGE_SETTING)).containsExactly("PT6M");
    assertThat(disclosed.values().stream().flatMap(List::stream)).doesNotContain(SIGNING_SECRET);
  }

  @Test
  void a_stripe_account_of_another_test_run_is_not_disclosed_under_this_one() {
    configure(configuration("P3D"));

    assertThat(provider().disclosureOf(TestRunId.of("another-run"), "merchant-1")).isEmpty();
  }

  private static Map<String, String> configuration(String captureWindow) {
    Map<String, String> declared = new LinkedHashMap<>();
    declared.put(StripeAccount.SECRET_KEY_PROPERTY, SECRET_KEY);
    declared.put(StripeCaptureWindow.SETTING, captureWindow);
    return declared;
  }

  private SimulatorProvider provider() {
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
        new InMemoryPaymentMethodFixtures(),
        new InMemoryScenarioRules(),
        StripeDoubles.heldResponses(),
        StripeDoubles.clockAt(Instant.EPOCH),
        deliveries,
        new StripeWebhooks(
            intents,
            refunds,
            customers,
            paymentMethods,
            setupIntents,
            accounts,
            deliveries,
            StripeDoubles.clockAt(Instant.EPOCH),
            new StripeDoubles.CountingIds()),
        new CapturePaymentIntent(intents, scenarioEngine()),
        new CancelPaymentIntent(intents, scenarioEngine()),
        new CreateRefund(
            intents, refunds, charges, new StripeDoubles.CountingIds(), scenarioEngine()),
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            new StripeDoubles.CountingIds(),
            scenarioEngine(),
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CreateSetupIntent(setupIntents, new StripeDoubles.CountingIds()),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine()));
  }

  private static ScenarioEngine scenarioEngine() {
    return new ScenarioEngine(new InMemoryScenarioRules());
  }

  @Test
  void a_secret_stripe_was_sent_is_redacted_before_it_could_reach_an_audit_row() {
    ProviderAccountFields fields = StripeAccount.ACCOUNT_FIELDS;
    Map<String, String> sent = new LinkedHashMap<>();
    for (String secret : fields.secretKeys()) {
      sent.put(secret, "LEAKED-" + secret + "-MUST-NEVER-REACH-AN-AUDIT-ROW");
    }
    assertThat(sent).isNotEmpty();

    Map<String, List<String>> recorded =
        ConfigurationFacts.of(
            fields,
            new DeclaredConfiguration(
                "a-run", null, List.of(new DeclaredAccount("merchant-1", sent)), null, null, null));

    for (String secret : fields.secretKeys()) {
      assertThat(recorded.get("accounts[0].configuration." + secret))
          .as("%s auditing %s", fields.provider().value(), secret)
          .containsExactly(Redaction.REDACTED);
    }
    assertThat(String.valueOf(recorded)).doesNotContain("MUST-NEVER-REACH-AN-AUDIT-ROW");
  }
}
