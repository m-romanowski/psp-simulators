package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.smoke.Applicability;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheObjectsADeclarationPromises;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.spi.TheSingleIdentifierFloor;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.ui.APresetReadsBackInWords;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AzericardProviderControlTest {

  private static final TestRunId RUN = TestRunId.of("azericard-control");

  private static final TestRunId ANOTHER_RUN = TestRunId.of("azericard-control-other");
  private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "77777777";
  private static final String MERCHANT = "merchant-1";
  private static final String CALLBACK_URL = "https://merchant.example/azericard/result";
  private static final String STORED_TOKEN = "TOKEN00000000000000000001";

  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final InMemoryScenarioRules scenarios = new InMemoryScenarioRules();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();

  private final WebhookDispatcher callbackDeliveries =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          AzericardDoubles.clockAt(AT),
          new AzericardDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarios),
          List.of(new AzericardCallbackAcknowledgement()),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final AzericardCallbacks callbacks =
      new AzericardCallbacks(
          payments,
          accounts,
          accounts,
          callbackDeliveries,
          AzericardDoubles.clockAt(AT),
          new AzericardDoubles.CountingIds());

  private final AzericardDoubles.PayerSessions payerSessions = new AzericardDoubles.PayerSessions();

  private final SimulatorProvider provider =
      new AzericardProvider(
          accounts,
          audit,
          payments,
          cards,
          new InMemoryPaymentMethodFixtures(),
          scenarios,
          AzericardDoubles.heldResponses(),
          callbacks,
          callbackDeliveries,
          AzericardDoubles.clockAt(AT),
          new AzericardDoubles.CountingIds(),
          new Authorize(
              payments, cards, new AzericardDoubles.CountingIds(), new ScenarioEngine(scenarios)),
          new CompleteOrReverse(payments, new ScenarioEngine(scenarios)),
          payerSessions,
          AzericardDoubles.checkoutOf(
              payerSessions,
              payments,
              cards,
              accounts,
              new ScenarioEngine(scenarios),
              audit,
              AzericardDoubles.clockAt(AT),
              new AzericardDoubles.CountingIds()));

  @Test
  void the_wire_namespaces_and_the_answer_off_them_are_the_ones_this_module_declares() {
    assertThat(provider.wireRoutes()).isEqualTo(AzericardWireFaults.ROUTES);
    assertThat(provider.wireRoutes().covers(AzericardGatewayController.CGI_LINK))
        .as("a namespace this module forgot to claim leaves a client on the wrong answer")
        .isTrue();
    for (WireFault fault : WireFault.values()) {
      assertThat(provider.theAnswerOffItsRoutes(fault, AzericardWireFaults.GATEWAY))
          .as(fault.name())
          .isSameAs(AzericardWireFaults.THE_ANSWER);
    }
  }

  @Test
  void hands_back_the_keyed_exchanges_its_payment_store_holds_rather_than_an_empty_page() {
    payments.observed(
        new KeyedObservation(
            RUN,
            MERCHANT,
            AzericardKeyMechanisms.ORDER,
            "084800",
            "TRTYPE=0",
            ExchangeVerdict.FIRST_REQUEST,
            new FingerprintedRequest("POST", "TRTYPE=0", Map.of()),
            new KeyedAnswer(200, "text/html", Map.of("ACTION", List.of("0"))),
            "INTREF01",
            true,
            null,
            AT));

    KeyedExchangePage page =
        provider.keyedExchanges(
            new KeyedExchangeQuery(
                RUN, KeyedExchangeQuery.FROM_THE_NEWEST, KeyedExchangeQuery.DEFAULT_LIMIT));

    assertThat(page.exchanges())
        .singleElement()
        .satisfies(
            exchange -> {
              assertThat(exchange.keyName()).isEqualTo(AzericardKeyMechanisms.ORDER);
              assertThat(exchange.keyValue()).isEqualTo("084800");
            });
  }

  @Test
  void is_registered_under_the_provider_id_its_routes_are_published_beneath() {
    assertThat(provider.id().value()).isEqualTo("azericard");
  }

  @Test
  void accepts_an_account_carrying_a_terminal_and_both_halves_of_the_signing_material() {
    apply(configurationOf(account("merchant-1", TERMINAL_ID)));

    assertThat(accounts.all()).hasSize(1);
    assertThat(accounts.identitiesOf(AzericardGateway.AZERICARD, RUN))
        .containsExactly("merchant-1");
  }

  @Test
  void refuses_an_account_that_names_no_terminal_because_a_request_arrives_on_one() {
    ConfiguredAccount account = accountWithout(AzericardTerminal.TERMINAL_PROPERTY);

    ProviderConfiguration namingNoTerminal = configurationOf(account);

    assertThatThrownBy(() -> apply(namingNoTerminal))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId");
  }

  @Test
  void refuses_an_account_whose_terminal_is_nothing_but_spaces() {
    ProviderConfiguration namingATerminalOfSpaces = configurationOf(account("merchant-1", "   "));

    assertThatThrownBy(() -> apply(namingATerminalOfSpaces))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId");
  }

  @Test
  void refuses_an_account_with_no_merchant_public_key_to_verify_a_request_mac_with() {
    ConfiguredAccount account = accountWithout(AzericardTerminal.MERCHANT_KEY_PROPERTY);

    ProviderConfiguration namingNoMerchantKey = configurationOf(account);

    assertThatThrownBy(() -> apply(namingNoMerchantKey))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.apiKey");
  }

  @Test
  void refuses_an_account_with_no_gateway_private_key_to_sign_an_answer_with() {
    ConfiguredAccount account = accountWithout(AzericardTerminal.GATEWAY_KEY_PROPERTY);

    ProviderConfiguration namingNoGatewayKey = configurationOf(account);

    assertThatThrownBy(() -> apply(namingNoGatewayKey))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.apiSecret");
  }

  @Test
  void refuses_two_accounts_of_one_test_run_that_claim_the_same_terminal() {
    ProviderConfiguration twoAccountsOnOneTerminal =
        configurationOf(account("merchant-1", TERMINAL_ID), account("merchant-2", TERMINAL_ID));

    assertThatThrownBy(() -> apply(twoAccountsOnOneTerminal))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[1].configuration.terminalId");
  }

  @Test
  void a_terminal_an_account_this_call_leaves_in_place_holds_is_refused_by_name() {
    apply(configurationOf(account("merchant-1", TERMINAL_ID)));

    ProviderConfiguration takingTheTerminalOfAnotherAccount =
        configurationOf(account("merchant-2", TERMINAL_ID));

    assertThatThrownBy(() -> apply(takingTheTerminalOfAnotherAccount))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "accounts[0].configuration.terminalId is already configured for another account in"
                + " this test run");
  }

  @Test
  void an_account_may_take_the_terminal_of_an_account_the_same_call_moves_off_it() {
    apply(configurationOf(account("merchant-1", TERMINAL_ID)));

    apply(configurationOf(account("merchant-1", "9999"), account("merchant-2", TERMINAL_ID)));

    assertThat(accounts.find(RUN, "merchant-2"))
        .get()
        .satisfies(held -> assertThat(held.terminalId()).isEqualTo(TERMINAL_ID));
  }

  @Test
  void an_account_the_core_removed_is_gone_from_azericards_own_table_and_the_others_are_not() {
    apply(configurationOf(account("merchant-1", TERMINAL_ID), account("merchant-2", "9999")));

    boolean removed =
        new ProviderConfigurations(
                accounts,
                new BoundRouteAliases(),
                new DeclaredProviderSettings(List.of()),
                new InMemoryTestRunSettings())
            .remove(provider, RUN, "merchant-1");

    assertThat(removed).isTrue();
    assertThat(accounts.all())
        .extracting(AzericardTerminal::accountId)
        .containsExactly("merchant-2");
    assertThat(accounts.find(RUN, "merchant-1")).isEmpty();
  }

  @Test
  void the_terminal_a_removed_account_held_is_free_for_another_account_of_the_same_run() {
    apply(configurationOf(account("merchant-1", TERMINAL_ID)));
    new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings())
        .remove(provider, RUN, "merchant-1");

    apply(configurationOf(account("merchant-2", TERMINAL_ID)));

    assertThat(accounts.find(RUN, "merchant-2"))
        .get()
        .satisfies(held -> assertThat(held.terminalId()).isEqualTo(TERMINAL_ID));
  }

  @Test
  void azericard_publishes_the_fields_an_account_of_it_would_need_and_names_itself() {
    ProviderAccountFields declared = provider.accountFields();

    assertThat(declared.provider()).isEqualTo(AzericardGateway.AZERICARD);
    assertThat(declared.keys())
        .containsExactlyInAnyOrderElementsOf(AzericardTerminal.CONFIGURATION_KEYS);
    assertThat(declared.secretKeys()).containsExactly(AzericardTerminal.GATEWAY_KEY_PROPERTY);
    assertThat(declared.field(AzericardTerminal.TERMINAL_PROPERTY))
        .get()
        .satisfies(field -> assertThat(field.required()).isTrue());
    assertThat(declared.field(AzericardPartialCompletion.SETTING))
        .get()
        .satisfies(field -> assertThat(field.required()).isFalse());
  }

  @Test
  void a_configuration_it_refuses_leaves_the_accounts_and_targets_of_the_last_one_it_took() {
    apply(
        new ProviderConfiguration(
            RUN,
            List.of(account("merchant-1", TERMINAL_ID)),
            List.of(new WebhookTarget("merchant-1", "https://merchant.example/hook")),
            List.of(),
            Map.of()));

    ProviderConfiguration oneAccountNamingNothing =
        new ProviderConfiguration(
            RUN,
            List.of(
                account("merchant-2", "88888888"), new ConfiguredAccount("merchant-3", Map.of())),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> apply(oneAccountNamingNothing))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[1].configuration.terminalId");

    assertThat(accounts.all())
        .singleElement()
        .satisfies(stored -> assertThat(stored.accountId()).isEqualTo("merchant-1"));
    assertThat(accounts.webhookTargetsOf(AzericardGateway.AZERICARD, RUN)).hasSize(1);
  }

  @Test
  void refuses_a_terminal_wider_than_the_column_every_payment_of_it_would_be_kept_in() {
    String wider = "7".repeat(AzericardTerminal.LONGEST_TERMINAL_THE_STORE_HOLDS + 1);

    ProviderConfiguration namingAWiderTerminal = configurationOf(account("merchant-1", wider));

    assertThatThrownBy(() -> apply(namingAWiderTerminal))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId")
        .hasMessageContaining("order trace")
        .hasMessageNotContaining(wider);
  }

  @Test
  void refuses_a_stored_card_source_terminal_wider_than_the_column_this_account_is_kept_in() {
    String wider = "7".repeat(AzericardTerminal.LONGEST_TERMINAL_THE_STORE_HOLDS + 1);

    ProviderConfiguration namingAWiderSource =
        configurationOf(
            accountWith(Map.of(AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY, wider)));

    assertThatThrownBy(() -> apply(namingAWiderSource))
        .as(
            "the terminals a stored card may be charged from are kept in the same column width"
                + " terminalId is, so a source this account could never be written with is"
                + " refused by name here rather than failing the write of the account")
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining(
            "accounts[0].configuration." + AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY)
        .hasMessageNotContaining(wider);
  }

  @Test
  void refuses_a_terminal_that_fits_the_column_but_spells_a_story_key_no_story_can_be_kept_under() {
    String holed = "7777\u000077777777";

    ProviderConfiguration namingAHoledTerminal = configurationOf(account("merchant-1", holed));

    assertThatThrownBy(() -> apply(namingAHoledTerminal))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId")
        .hasMessageContaining("story key")
        .hasMessageNotContaining(holed);
  }

  @Test
  void refuses_a_terminal_spelled_in_half_a_character_though_it_is_short_enough_to_store() {
    String halved = "7777\uD80077777777";

    ProviderConfiguration namingAHalvedTerminal = configurationOf(account("merchant-1", halved));

    assertThatThrownBy(() -> apply(namingAHalvedTerminal))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId")
        .hasMessageContaining("story key")
        .hasMessageNotContaining(halved);
  }

  @Test
  void accepts_both_terminal_widths_the_vendor_publishes_and_the_widest_the_store_can_keep() {
    apply(
        configurationOf(
            account("merchant-1", TERMINAL_ID),
            account("merchant-2", "1111111"),
            account("merchant-3", "7".repeat(AzericardTerminal.LONGEST_TERMINAL_THE_STORE_HOLDS))));

    assertThat(accounts.all()).hasSize(3);
  }

  @Test
  void takes_the_published_default_for_the_duplicate_code_the_vendor_never_published() {
    assertThat(AzericardDuplicateCode.of("settings.x", null)).isEqualTo("-1");
    assertThat(AzericardDuplicateCode.of("settings.x", "  ")).isEqualTo("-1");
    assertThat(AzericardDuplicateCode.of("settings.x", "05")).isEqualTo("05");
  }

  @Test
  void the_sign_the_duplicate_code_words_promise_is_the_one_leading_hyphen_the_shape_admits() {
    assertThat(AzericardDuplicateCode.of("settings.x", AzericardDuplicateCode.DEFAULT))
        .as(
            "the code the words offer as an example is one the shape accepts, so a tester"
                + " copying it is not refused for copying it")
        .isEqualTo(AzericardDuplicateCode.DEFAULT);
    assertThat(AzericardDuplicateCode.of("settings.x", "1")).isEqualTo("1");
    assertThat(AzericardDuplicateCode.of("settings.x", "-05")).isEqualTo("-05");
    assertThat(AzericardDuplicateCode.of("settings.x", "0A")).isEqualTo("0A");
    for (String noSignThisShapeAdmits : List.of("--1", "+1", "-", "1-", "-123")) {
      assertThatThrownBy(() -> AzericardDuplicateCode.of("settings.x", noSignThisShapeAdmits))
          .as(
              "the words say one leading hyphen and a code of at most two characters, and %s is"
                  + " neither, so the sentence beside the pattern cannot drift into promising a"
                  + " value the pattern turns away",
              noSignThisShapeAdmits)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must match -?[0-9A-Za-z]{1,2}")
          .hasMessageContaining("optionally signed with a leading hyphen");
    }
  }

  @Test
  void the_words_beside_the_duplicate_code_describe_every_code_the_box_itself_takes() {
    assertThat(AzericardDuplicateCode.of("settings.x", "-zZ"))
        .as(
            "a signed code is three characters on the wire and the box takes it, while the words"
                + " read \"at most 2 characters, each a letter or a digit\" and were followed by"
                + " the example -1, which is neither two characters nor a letter or a digit")
        .isEqualTo("-zZ");
    assertThatThrownBy(() -> AzericardDuplicateCode.of("settings.x", "100"))
        .as(
            "one sentence of 49 words with the pattern in the middle apposed a range to a single"
                + " width; the measured words now say what the count covers and the vendor"
                + " evidence follows in a sentence of its own")
        .hasMessage(
            "settings.x must match -?[0-9A-Za-z]{1,2}, which is a response code of at most 2"
                + " characters, each a letter or a digit, optionally signed with a leading hyphen"
                + " that count does not include, for example -1. Its width is the size provider"
                + " azericard publishes RC at, and the sign is admitted because every code the"
                + " gateway was observed answering a refused message with carries one");
  }

  @Test
  void the_duplicate_code_names_its_width_in_the_cores_words_read_off_its_own_pattern() {
    assertThatThrownBy(() -> AzericardDuplicateCode.of("settings.x", "1234"))
        .as(
            "the two characters azericard sizes RC at are stated by measuring the code shape"
                + " rather than written beside it, so the figure cannot contradict the pattern")
        .hasMessageContaining("at most 2 characters, each a letter or a digit")
        .hasMessageContaining("optionally signed with a leading hyphen");
  }

  @Test
  void refuses_a_duplicate_code_wider_than_the_field_the_vendor_sizes_at_two_characters() {
    ConfiguredAccount account = accountWith(Map.of(AzericardDuplicateCode.SETTING, "1234"));

    ProviderConfiguration namingNoDuplicateCode = configurationOf(account);

    assertThatThrownBy(() -> apply(namingNoDuplicateCode))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration." + AzericardDuplicateCode.SETTING);
  }

  @Test
  void closes_the_authorization_on_a_partial_completion_unless_an_account_asks_otherwise() {
    assertThat(AzericardPartialCompletion.of("settings.x", null))
        .isEqualTo(AzericardPartialCompletion.CLOSES_THE_AUTHORIZATION);
    assertThat(AzericardPartialCompletion.of("settings.x", "  "))
        .isEqualTo(AzericardPartialCompletion.CLOSES_THE_AUTHORIZATION);
    assertThat(
            AzericardPartialCompletion.of(
                "settings.x", AzericardPartialCompletion.LEAVES_THE_REMAINDER_CAPTURABLE.name()))
        .isEqualTo(AzericardPartialCompletion.LEAVES_THE_REMAINDER_CAPTURABLE);
  }

  @Test
  void refuses_a_partial_completion_behaviour_this_contract_does_not_name() {
    ConfiguredAccount account =
        accountWith(Map.of(AzericardPartialCompletion.SETTING, "LEAVES_IT_OPEN_FOREVER"));

    ProviderConfiguration namingNoPartialCompletion = configurationOf(account);

    assertThatThrownBy(() -> apply(namingNoPartialCompletion))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration." + AzericardPartialCompletion.SETTING);
  }

  @Test
  void signs_every_callback_unless_an_account_asks_for_one_a_merchant_has_to_refuse() {
    assertThat(AzericardCallbackSignature.of("settings.x", null))
        .isEqualTo(AzericardCallbackSignature.VALID);
    assertThat(AzericardCallbackSignature.of("settings.x", "  "))
        .isEqualTo(AzericardCallbackSignature.VALID);
    assertThat(AzericardCallbackSignature.of("settings.x", "ABSENT"))
        .isEqualTo(AzericardCallbackSignature.ABSENT);
  }

  @Test
  void refuses_a_callback_emission_this_contract_does_not_name() {
    ConfiguredAccount account =
        accountWith(Map.of(AzericardCallbackSignature.SETTING, "SOMETIMES"));

    ProviderConfiguration namingNoCallbackSignature = configurationOf(account);

    assertThatThrownBy(() -> apply(namingNoCallbackSignature))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration." + AzericardCallbackSignature.SETTING);
  }

  @Test
  void exposes_every_operation_this_contract_answers_on_its_two_routes_and_nothing_else() {
    assertThat(provider.vocabulary().operationNames())
        .containsExactly(
            "TRTYPE=0",
            "TRTYPE=0 TOKEN_ACTION=REGISTER",
            "TRTYPE=0 TOKEN",
            "TRTYPE=1",
            "TRTYPE=1 TOKEN_ACTION=REGISTER",
            "TRTYPE=1 TOKEN",
            "TRTYPE=21",
            "TRTYPE=22",
            "TRTYPE=24",
            "TRTYPE=90");
  }

  @Test
  void refuses_a_rule_that_names_an_operation_this_contract_does_not_expose() {
    List<ScenarioRefusal> refusals =
        provider
            .vocabulary()
            .refusals(
                new NewScenarioRule(
                    AzericardGateway.AZERICARD,
                    RUN,
                    0,
                    ScenarioMatch.anyRequest().naming("TRTYPE=185"),
                    new ScenarioAction.FixedSuccess(),
                    null));

    assertThat(refusals)
        .singleElement()
        .satisfies(refusal -> assertThat(refusal.property()).isEqualTo("match.operation"));
  }

  @Test
  void admits_a_rule_that_addresses_a_settlement_by_the_gateway_reference_it_carries() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        AzericardGateway.AZERICARD,
                        RUN,
                        0,
                        aMatchNaming(MatchFact.PROVIDER_OBJECT_ID).naming("TRTYPE=21"),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .isEmpty();
  }

  @Test
  void refuses_a_rule_that_addresses_an_authorization_by_a_reference_it_cannot_carry() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        AzericardGateway.AZERICARD,
                        RUN,
                        0,
                        aMatchNaming(MatchFact.PROVIDER_OBJECT_ID).naming("TRTYPE=0"),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .extracting(ScenarioRefusal::property)
        .containsExactly(MatchFact.PROVIDER_OBJECT_ID.property());
  }

  @Test
  void refuses_a_rule_naming_a_customer_no_azericard_request_carries() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        AzericardGateway.AZERICARD,
                        RUN,
                        0,
                        aMatchNaming(MatchFact.CUSTOMER_ID),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .extracting(ScenarioRefusal::property)
        .containsExactly(MatchFact.CUSTOMER_ID.property());
  }

  @Test
  void admits_a_rule_that_addresses_a_stored_card_payment_by_the_token_it_posts() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        AzericardGateway.AZERICARD,
                        RUN,
                        0,
                        aMatchNaming(MatchFact.PAYMENT_METHOD_ID).naming("TRTYPE=1 TOKEN"),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .isEmpty();
  }

  @Test
  void refuses_a_rule_that_addresses_a_card_storage_by_a_token_the_request_cannot_carry() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        AzericardGateway.AZERICARD,
                        RUN,
                        0,
                        aMatchNaming(MatchFact.PAYMENT_METHOD_ID)
                            .naming("TRTYPE=1 TOKEN_ACTION=REGISTER"),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .extracting(ScenarioRefusal::property)
        .containsExactly(MatchFact.PAYMENT_METHOD_ID.property());
  }

  @Test
  void refuses_a_rule_that_addresses_a_status_inquiry_by_an_amount_it_never_posts() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        AzericardGateway.AZERICARD,
                        RUN,
                        0,
                        new ScenarioMatch(
                                null,
                                null,
                                null,
                                null,
                                null,
                                Amount.of("11.48"),
                                null,
                                null,
                                null,
                                Map.of())
                            .naming("TRTYPE=90"),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .extracting(ScenarioRefusal::property)
        .containsExactly(MatchFact.AMOUNT.property());
  }

  private static ScenarioMatch aMatchNaming(MatchFact fact) {
    return new ScenarioMatch(
        null,
        null,
        fact == MatchFact.PROVIDER_OBJECT_ID ? "x" : null,
        fact == MatchFact.CUSTOMER_ID ? "x" : null,
        fact == MatchFact.PAYMENT_METHOD_ID ? "x" : null,
        null,
        null,
        null,
        null,
        Map.of());
  }

  @Test
  void admits_a_decline_code_of_the_two_characters_the_response_field_is_sized_at() {
    assertThat(provider.vocabulary().failureCodes().admits("05")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("N7")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("00")).isFalse();
    assertThat(provider.vocabulary().failureCodes().admits("-17")).isFalse();
    assertThat(provider.vocabulary().failureCodes().admits("5")).isFalse();
  }

  @Test
  void expands_the_lost_response_preset_into_a_story_over_every_operation_it_exposes() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.PSP_5XX_THEN_SUCCESS, RUN, 0, ScenarioMatch.anyRequest()));

    assertThat(expansion).isInstanceOf(PresetExpansion.Rules.class);
    List<NewScenarioRule> rules = ((PresetExpansion.Rules) expansion).rules();
    assertThat(rules).hasSize(20);
    assertThat(rules)
        .allSatisfy(
            rule -> {
              ScenarioStory story = rule.story();
              assertThat(story).isNotNull();
              assertThat(story.name())
                  .isEqualTo(ScenarioPreset.PSP_5XX_THEN_SUCCESS.publishedName());
              assertThat(story.keyedOn()).isEqualTo(StoryKeying.FLOW);
            });
    assertThat(rules)
        .extracting(rule -> rule.match().operation())
        .containsExactly(
            "TRTYPE=0",
            "TRTYPE=0",
            "TRTYPE=0 TOKEN_ACTION=REGISTER",
            "TRTYPE=0 TOKEN_ACTION=REGISTER",
            "TRTYPE=0 TOKEN",
            "TRTYPE=0 TOKEN",
            "TRTYPE=1",
            "TRTYPE=1",
            "TRTYPE=1 TOKEN_ACTION=REGISTER",
            "TRTYPE=1 TOKEN_ACTION=REGISTER",
            "TRTYPE=1 TOKEN",
            "TRTYPE=1 TOKEN",
            "TRTYPE=21",
            "TRTYPE=21",
            "TRTYPE=22",
            "TRTYPE=22",
            "TRTYPE=24",
            "TRTYPE=24",
            "TRTYPE=90",
            "TRTYPE=90");
    assertThat(provider.vocabulary().refusals(rules, List.of())).isEmpty();
  }

  @ParameterizedTest(name = "expands {0} into rules the vocabulary admits")
  @EnumSource(
      value = ScenarioPreset.class,
      names = {
        "PREAUTH_SUCCESS_CAPTURE_SUCCESS",
        "PREAUTH_SUCCESS_CAPTURE_DECLINE",
        "CAPTURE_ACCEPTED_RESPONSE_LOST",
        "CANCEL_HOLD_SUCCESS",
        "REFUND_FULL_SUCCESS",
        "CARD_ADD_SUCCESS",
        "CARD_ADD_FAILED"
      })
  void expands_the_presets_this_slice_made_reachable(ScenarioPreset preset) {
    PresetExpansion expansion =
        provider.expand(new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest()));

    assertThat(expansion).isInstanceOf(PresetExpansion.Rules.class);
    List<NewScenarioRule> rules = ((PresetExpansion.Rules) expansion).rules();
    assertThat(provider.vocabulary().refusals(rules, List.of())).isEmpty();
    assertThat(rules).extracting(rule -> rule.match().operation()).doesNotContainNull();
  }

  @Test
  void the_card_add_callback_preset_stores_the_card_and_holds_the_callback_that_reports_it() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.CARD_ADD_CALLBACK_DELAYED, RUN, 0, ScenarioMatch.anyRequest()));

    List<NewScenarioRule> expanded = ((PresetExpansion.Rules) expansion).rules();
    assertThat(expanded).hasSizeGreaterThan(1);
    assertThat(expanded.subList(0, expanded.size() - 1))
        .extracting(rule -> rule.match().operation())
        .doesNotContainNull();
    NewScenarioRule held = expanded.get(expanded.size() - 1);
    assertThat(held.action())
        .isEqualTo(new ScenarioAction.DeliveryDelayed(ScenarioPreset.DELIVERY_HELD_BACK_BY));
    assertThat(held.match().operation()).isNull();
    assertThat(provider.vocabulary().refusals(expanded, List.of())).isEmpty();
  }

  @Test
  void expands_the_hold_cancellation_preset_into_the_reversals_that_release_a_hold() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.CANCEL_HOLD_SUCCESS, RUN, 0, ScenarioMatch.anyRequest()));

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly("TRTYPE=22", "TRTYPE=24");
    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(NewScenarioRule::action)
        .containsOnly(new ScenarioAction.FixedSuccess());
  }

  @Test
  void expands_the_capture_decline_preset_into_a_hold_that_takes_and_a_completion_that_declines() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                RUN,
                0,
                ScenarioMatch.anyRequest()));

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(NewScenarioRule::action)
        .containsExactly(
            new ScenarioAction.FixedSuccess(),
            new ScenarioAction.TerminalFailure(AzericardPresets.CAPTURE_DECLINE_CODE));
  }

  @Test
  void expands_the_lost_capture_response_preset_into_a_fault_that_damages_only_the_delivery() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST, RUN, 0, ScenarioMatch.anyRequest()));

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.match().operation()).isEqualTo("TRTYPE=21");
              assertThat(rule.action()).isInstanceOf(ScenarioAction.TransportFaultAlways.class);
            });
  }

  @Test
  void expands_the_card_add_preset_into_the_two_card_storage_calls_the_token_route_answers() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(ScenarioPreset.CARD_ADD_SUCCESS, RUN, 0, ScenarioMatch.anyRequest()));

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly("TRTYPE=0 TOKEN_ACTION=REGISTER", "TRTYPE=1 TOKEN_ACTION=REGISTER");
  }

  @Test
  void expands_the_failed_card_add_preset_into_a_card_storage_the_issuer_declines() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(ScenarioPreset.CARD_ADD_FAILED, RUN, 0, ScenarioMatch.anyRequest()));

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(NewScenarioRule::action)
        .containsOnly(
            new ScenarioAction.TerminalFailure(AzericardPresets.CARD_STORAGE_DECLINE_CODE));
  }

  @Test
  void expands_the_capture_success_preset_into_a_hold_and_a_completion_that_both_take() {
    PresetExpansion expansion =
        provider.expand(
            new PresetRequest(
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                RUN,
                0,
                ScenarioMatch.anyRequest()));

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly("TRTYPE=0", "TRTYPE=21");
    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(NewScenarioRule::action)
        .containsOnly(new ScenarioAction.FixedSuccess());
  }

  @ParameterizedTest(name = "says in its own words why it cannot express {0}")
  @EnumSource(
      value = ScenarioPreset.class,
      names = {
        "PSP_5XX_THEN_SUCCESS",
        "PREAUTH_SUCCESS_CAPTURE_SUCCESS",
        "PREAUTH_SUCCESS_CAPTURE_DECLINE",
        "CAPTURE_ACCEPTED_RESPONSE_LOST",
        "CANCEL_HOLD_SUCCESS",
        "REFUND_FULL_SUCCESS",
        "CARD_ADD_SUCCESS",
        "CARD_ADD_FAILED",
        "CARD_ADD_CALLBACK_DELAYED",
        "WEBHOOK_DELAYED",
        "WEBHOOK_DUPLICATE"
      },
      mode = EnumSource.Mode.EXCLUDE)
  void says_why_every_other_preset_is_inexpressible_on_this_contract(ScenarioPreset preset) {
    PresetExpansion expansion =
        provider.expand(new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest()));

    assertThat(expansion).isInstanceOf(PresetExpansion.NotExpressible.class);
    assertThat(((PresetExpansion.NotExpressible) expansion).reason()).contains("azericard");
  }

  @ParameterizedTest(name = "shapes the callbacks of a test run for {0}")
  @EnumSource(
      value = ScenarioPreset.class,
      names = {"WEBHOOK_DELAYED", "WEBHOOK_DUPLICATE"})
  void a_callback_preset_expands_into_one_rule_that_narrows_on_no_event_azericard_never_names(
      ScenarioPreset preset) {
    PresetExpansion expansion =
        provider.expand(new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest()));

    List<NewScenarioRule> expanded = ((PresetExpansion.Rules) expansion).rules();
    assertThat(expanded).hasSize(1);
    assertThat(expanded.get(0).action()).isInstanceOf(ScenarioAction.OnTheDelivery.class);
    assertThat(expanded.get(0).match().eventType()).isNull();
    assertThat(provider.vocabulary().refusals(expanded, List.of())).isEmpty();
  }

  @Test
  void a_rule_narrowed_on_an_event_type_is_refused_because_this_gateway_names_no_callback_event() {
    NewScenarioRule narrowed =
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest().reporting("authorization.completed"),
            new ScenarioAction.DeliveryDropped(),
            null);

    assertThat(provider.vocabulary().refusals(narrowed))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.eventType");
  }

  @Test
  void reports_a_stored_card_as_an_object_of_its_own_under_the_token_it_was_saved_as() {
    cards.claim(aStoredCard());

    ProviderObjectState card = provider.state(RUN, STORED_TOKEN).orElseThrow();

    assertThat(card.providerObjectId()).isEqualTo(STORED_TOKEN);
    assertThat(card.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(card.amount()).isNull();
    assertThat(card.ledger().operations()).isEmpty();
    assertThat(card.authorizationOpen()).isFalse();
    assertThat(card.relatedObjects())
        .containsEntry("terminal", TERMINAL_ID)
        .containsEntry("storedBy", "INTREF0000000001");
  }

  @Test
  void answers_nothing_for_an_identifier_neither_a_payment_nor_a_stored_card_of_this_run_carries() {
    assertThat(provider.state(RUN, "TOKEN00000000000000000009")).isEmpty();
  }

  @Test
  void clears_the_cards_the_callbacks_and_the_rules_a_test_run_left_when_that_run_is_reset() {
    aTestRunWithACallbackTarget();
    payments.insertIfAbsent(aHoldOf("11.48"));
    cards.claim(aStoredCard());
    provider.schedule(
        new WebhookScheduleRequest(
            RUN, "INTREF0000000001", null, null, null, ObjectOrigin.CONTROL_API));
    scenarios.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.FixedSuccess(),
            null));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(4);
    assertThat(provider.state(RUN, STORED_TOKEN)).isEmpty();
    assertThat(callbackDeliveries.of(AzericardGateway.AZERICARD, RUN)).isEmpty();
  }

  @Test
  void reports_a_stored_payment_as_the_object_the_control_api_reads() {
    payments.insertIfAbsent(aHoldOf("11.48"));

    Optional<ProviderObjectState> state = provider.state(RUN, "INTREF0000000001");

    assertThat(state).isPresent();
    ProviderObjectState held = state.orElseThrow();
    assertThat(held.providerObjectId()).isEqualTo("INTREF0000000001");
    assertThat(held.nativeStatus()).isEqualTo("0/00");
    assertThat(held.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(held.amount()).isEqualTo(Amount.of("11.48"));
    assertThat(held.currency()).isEqualTo("AZN");
    assertThat(held.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(held.relatedObjects())
        .containsEntry("merchantId", "merchant-1")
        .containsEntry("terminal", TERMINAL_ID)
        .containsEntry("order", "20220628084800")
        .containsEntry("rrn", "112676199769")
        .containsEntry("approval", "111111");
    assertThat(held.authorizationOpen()).isTrue();
    assertThat(held.capturesAccepted()).isTrue();
    assertThat(held.refundsAccepted())
        .as("azericard reverses a hold it has captured nothing of, so it has none of it to return")
        .isTrue();
    assertThat(held.derivedBalances().orElseThrow().capturableRemainder())
        .isEqualTo(Amount.of("11.48"));
    assertThat(held.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of("0"));
  }

  @Test
  void reports_a_completed_payment_as_succeeded_with_the_captured_amount_reversible() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    AzericardPayment held = payments.all().get(0);
    payments.append(held, aCaptureOf("11.48"), AT);

    ProviderObjectState completed = provider.state(RUN, "INTREF0000000001").orElseThrow();

    assertThat(completed.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(completed.authorizationOpen()).isFalse();
    assertThat(completed.capturesAccepted()).isFalse();
    assertThat(completed.refundsAccepted()).isTrue();
    assertThat(completed.derivedBalances().orElseThrow().capturableRemainder())
        .isEqualTo(Amount.of("0"));
    assertThat(completed.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of("11.48"));
  }

  @Test
  void reports_a_released_hold_as_cancelled_with_nothing_left_to_capture() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    payments.reverseTheHold(payments.all().get(0), AT);

    ProviderObjectState released = provider.state(RUN, "INTREF0000000001").orElseThrow();

    assertThat(released.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(released.authorizationOpen()).isFalse();
    assertThat(released.capturesAccepted()).isFalse();
    assertThat(released.refundsAccepted()).isFalse();
    assertThat(released.ledger().operations()).hasSize(1);
    assertThat(released.derivedBalances().orElseThrow().capturableRemainder())
        .isEqualTo(Amount.of("0"));
  }

  @Test
  void reports_a_reversed_payment_as_refunded_and_accepting_no_further_reversal() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    payments.append(payments.all().get(0), aCaptureOf("11.48"), AT);
    payments.append(payments.all().get(0), aReversalOf("11.48"), AT);

    ProviderObjectState reversed = provider.state(RUN, "INTREF0000000001").orElseThrow();

    assertThat(reversed.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(reversed.refundsAccepted()).isFalse();
    assertThat(reversed.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of("0.00"));
  }

  @Test
  void a_partly_completed_hold_still_accepts_a_completion_of_what_it_has_not_taken() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    payments.append(payments.all().get(0), aPartialCaptureOf("4.00"), AT);

    ProviderObjectState partly = provider.state(RUN, "INTREF0000000001").orElseThrow();

    assertThat(partly.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_CAPTURED);
    assertThat(partly.authorizationOpen()).isTrue();
    assertThat(partly.capturesAccepted()).isTrue();
    assertThat(partly.derivedBalances().orElseThrow().capturableRemainder())
        .isEqualTo(Amount.of("7.48"));
  }

  @Test
  void a_hold_completed_in_full_without_closing_accepts_no_further_completion() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    payments.append(payments.all().get(0), aPartialCaptureOf("11.48"), AT);

    ProviderObjectState taken = provider.state(RUN, "INTREF0000000001").orElseThrow();

    assertThat(taken.authorizationOpen()).isTrue();
    assertThat(taken.capturesAccepted()).isFalse();
    assertThat(taken.derivedBalances().orElseThrow().capturableRemainder())
        .isEqualTo(Amount.of("0.00"));
  }

  @Test
  void a_reversed_slice_leaves_a_remainder_no_completion_can_take() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    payments.append(payments.all().get(0), aPartialCaptureOf("4.00"), AT);
    payments.append(payments.all().get(0), aReversalOf("4.00"), AT);

    ProviderObjectState reversed = provider.state(RUN, "INTREF0000000001").orElseThrow();

    assertThat(reversed.authorizationOpen()).isTrue();
    assertThat(reversed.capturesAccepted()).isFalse();
    assertThat(reversed.refundsAccepted()).isTrue();
    assertThat(reversed.derivedBalances().orElseThrow().capturableRemainder())
        .isEqualTo(Amount.of("7.48"));
    assertThat(reversed.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of("0.00"));
  }

  @Test
  void reports_nothing_for_an_internal_reference_this_test_run_never_answered_with() {
    assertThat(provider.state(RUN, "INTREF0000000001")).isEmpty();
  }

  @Test
  void a_declined_payment_leaves_no_approval_among_the_objects_it_is_related_to() {
    payments.insertIfAbsent(aDecline());

    ProviderObjectState declined = provider.state(RUN, "INTREF0000000002").orElseThrow();

    assertThat(declined.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
    assertThat(declined.authorizationOpen()).isFalse();
    assertThat(declined.relatedObjects()).doesNotContainKey("approval");
  }

  @Test
  void clears_the_payments_of_one_test_run_and_leaves_another_run_holding_its_own() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    payments.insertIfAbsent(aHoldOfAnotherRun());
    scenarios.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.FixedSuccess(),
            null));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(2);
    assertThat(payments.all())
        .singleElement()
        .satisfies(p -> assertThat(p.testRunId()).isEqualTo(ANOTHER_RUN));
    assertThat(scenarios.of(AzericardGateway.AZERICARD, RUN)).isEmpty();
  }

  @Test
  void clears_every_test_run_when_the_whole_simulator_is_reset() {
    aTestRunWithACallbackTarget();
    payments.insertIfAbsent(aHoldOf("11.48"));
    payments.insertIfAbsent(aHoldOfAnotherRun());
    cards.claim(aStoredCard());
    provider.schedule(
        new WebhookScheduleRequest(
            RUN, "INTREF0000000001", null, null, null, ObjectOrigin.CONTROL_API));
    ScenarioRule rule =
        scenarios.add(
            new NewScenarioRule(
                AzericardGateway.AZERICARD,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                null));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.ALL);

    assertThat(outcome.clearedObjects()).isEqualTo(5);
    assertThat(payments.all()).isEmpty();
    assertThat(callbackDeliveries.of(AzericardGateway.AZERICARD, RUN)).isEmpty();
    assertThat(provider.state(RUN, STORED_TOKEN)).isEmpty();
    assertThat(scenarios.find(AzericardGateway.AZERICARD, RUN, rule.scenarioId())).isEmpty();
  }

  @Test
  void posts_the_authorization_result_to_the_one_callback_target_the_terminal_declared() {
    aTestRunWithACallbackTarget();
    payments.insertIfAbsent(aHoldOf("11.48"));

    List<ScheduledWebhook> scheduled =
        provider.schedule(
            new WebhookScheduleRequest(
                RUN, "INTREF0000000001", null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(scheduled).hasSize(1);
    assertThat(scheduled.get(0).webhookJobId()).startsWith("whjob_");
    assertThat(scheduled.get(0).eventId()).startsWith("evt_");
    assertThat(callbackDeliveries.of(AzericardGateway.AZERICARD, RUN))
        .singleElement()
        .satisfies(
            delivery -> {
              assertThat(delivery.targetUrl()).isEqualTo(CALLBACK_URL);
              assertThat(delivery.providerObjectId()).isEqualTo("INTREF0000000001");
              assertThat(delivery.eventType()).isNull();
            });
  }

  @Test
  void refuses_to_post_an_authorization_result_no_payment_of_this_run_ever_reached() {
    WebhookScheduleRequest request =
        new WebhookScheduleRequest(
            RUN, "INTREF0000000009", null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> provider.schedule(request))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessageContaining("INT_REF");
  }

  @Test
  void refuses_to_name_an_event_provider_azericard_publishes_no_name_for() {
    apply(configurationOf(account("merchant-1", TERMINAL_ID)));
    payments.insertIfAbsent(aHoldOf("11.48"));

    WebhookScheduleRequest request =
        new WebhookScheduleRequest(
            RUN,
            "INTREF0000000001",
            "authorization.approved",
            null,
            null,
            ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> provider.schedule(request))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("BACKREF");
  }

  private void aTestRunWithACallbackTarget() {
    apply(
        new ProviderConfiguration(
            RUN,
            List.of(account("merchant-1", TERMINAL_ID)),
            List.of(new WebhookTarget("merchant-1", CALLBACK_URL)),
            List.of(),
            Map.of()));
  }

  @Test
  void every_object_azericard_reports_keeps_the_one_identifier_it_was_created_under() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    cards.claim(aStoredCard());

    TheSingleIdentifierFloor.holdsFor(provider, RUN, "INTREF0000000001");
    TheSingleIdentifierFloor.holdsFor(provider, RUN, STORED_TOKEN);
    TheSingleIdentifierFloor.holdsForEveryObjectOf(provider, RUN);
  }

  private static AzericardStoredCard aStoredCard() {
    return AzericardStoredCard.storedBy(
        RUN, STORED_TOKEN, TERMINAL_ID, "merchant-1", "INTREF0000000001", "20220628084800", AT);
  }

  private static AzericardPayment aHoldOf(String amount) {
    return new AzericardPayment(
        RUN,
        "INTREF0000000001",
        TERMINAL_ID,
        "merchant-1",
        "20220628084800",
        AzericardTransactionType.PRE_AUTHORIZATION,
        Amount.of(amount),
        "AZN",
        "111111",
        "112676199769",
        "0",
        "00",
        Ledger.empty()
            .with(
                new MonetaryOperation(
                    1,
                    OperationKind.AUTHORIZATION,
                    Amount.of(amount),
                    null,
                    null,
                    "0/00",
                    NormalizedStatus.READY_FOR_CAPTURE,
                    AT)),
        null,
        null,
        null,
        null,
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        AT,
        AT);
  }

  private static MonetaryOperation aCaptureOf(String amount) {
    return new MonetaryOperation(
        2,
        OperationKind.CAPTURE,
        Amount.of(amount),
        Boolean.TRUE,
        null,
        "0/00",
        NormalizedStatus.SUCCEEDED,
        AT);
  }

  private static MonetaryOperation aPartialCaptureOf(String amount) {
    return new MonetaryOperation(
        2,
        OperationKind.CAPTURE,
        Amount.of(amount),
        Boolean.FALSE,
        null,
        "0/00",
        NormalizedStatus.SUCCEEDED,
        AT);
  }

  private static MonetaryOperation aReversalOf(String amount) {
    return new MonetaryOperation(
        3,
        OperationKind.REFUND,
        Amount.of(amount),
        null,
        null,
        "0/00",
        NormalizedStatus.REFUNDED,
        AT);
  }

  private static AzericardPayment aHoldOfAnotherRun() {
    AzericardPayment held = aHoldOf("11.48");
    return new AzericardPayment(
        ANOTHER_RUN,
        held.intRef(),
        held.terminalId(),
        held.merchantId(),
        held.order(),
        held.transactionType(),
        held.amount(),
        held.currency(),
        held.approval(),
        held.rrn(),
        held.action(),
        held.responseCode(),
        held.ledger(),
        held.holdReversedAt(),
        held.backref(),
        held.email(),
        held.token(),
        held.cardLast4(),
        held.extNetRef(),
        held.origin(),
        held.createdAt(),
        held.updatedAt());
  }

  private static AzericardPayment aDecline() {
    return new AzericardPayment(
        RUN,
        "INTREF0000000002",
        TERMINAL_ID,
        "merchant-1",
        "20220628084801",
        AzericardTransactionType.AUTHORIZATION,
        Amount.of("11.48"),
        "AZN",
        null,
        "112676199770",
        "2",
        "05",
        Ledger.empty(),
        null,
        null,
        null,
        null,
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        AT,
        AT);
  }

  @Test
  void accepts_the_widest_stored_card_source_terminal_the_column_this_account_is_kept_in_holds() {
    String widest = "7".repeat(AzericardTerminal.LONGEST_TERMINAL_THE_STORE_HOLDS);

    apply(
        configurationOf(
            accountWith(Map.of(AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY, widest))));

    assertThat(accounts.all())
        .as(
            "the bound is the width the store keeps, so the widest terminal it can keep is"
                + " accepted and only one character more is refused; without this the refusal"
                + " could stand one character early and nothing would say so")
        .hasSize(1);
  }

  @Test
  void reads_the_terminals_a_stored_card_may_be_charged_from_out_of_one_comma_separated_value() {
    apply(
        configurationOf(
            accountWith(
                Map.of(
                    AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY,
                    " 70000002 , 70000003 ,, 70000002 "))));

    assertThat(provider.disclosureOf(RUN, "merchant-1").orElseThrow().disclosed())
        .as(
            "a tester types these in one box, so the simulator reads them apart on the separator"
                + " the sentence beside that box names, keeps each one once and discloses them"
                + " back in the order they were typed")
        .containsEntry(
            AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY, List.of("70000002,70000003"));
  }

  @Test
  void the_disclosure_of_an_azericard_terminal_names_every_configuration_key_azericard_reads() {
    apply(configurationOf(accountWith(Map.of())));

    DisclosedAccount disclosed = provider.disclosureOf(RUN, "merchant-1").orElseThrow();

    assertThat(disclosed.accountId()).isEqualTo("merchant-1");
    assertThat(disclosed.disclosed().keySet())
        .containsExactlyInAnyOrderElementsOf(AzericardTerminal.CONFIGURATION_KEYS);
  }

  @Test
  void azericard_answers_a_marker_for_the_private_key_it_signs_a_gateway_response_with() {
    apply(configurationOf(accountWith(Map.of())));

    Map<String, List<String>> disclosed =
        provider.disclosureOf(RUN, "merchant-1").orElseThrow().disclosed();

    assertThat(disclosed.get(AzericardTerminal.GATEWAY_KEY_PROPERTY))
        .containsExactly(Redaction.REDACTED);
    assertThat(disclosed.values().stream().flatMap(List::stream))
        .doesNotContain(AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8);
  }

  @Test
  void azericard_hands_over_the_terminal_and_the_merchants_public_key_a_tester_must_compare() {
    apply(configurationOf(accountWith(Map.of())));

    Map<String, List<String>> disclosed =
        provider.disclosureOf(RUN, "merchant-1").orElseThrow().disclosed();

    assertThat(disclosed.get(AzericardTerminal.TERMINAL_PROPERTY)).containsExactly(TERMINAL_ID);
    assertThat(disclosed.get(AzericardTerminal.MERCHANT_KEY_PROPERTY))
        .containsExactly(AzericardKeyFixture.MERCHANT_PUBLIC);
  }

  @Test
  void an_azericard_terminal_of_another_test_run_is_not_disclosed_under_this_one() {
    apply(configurationOf(accountWith(Map.of())));

    assertThat(provider.disclosureOf(ANOTHER_RUN, "merchant-1")).isEmpty();
  }

  private void apply(ProviderConfiguration configuration) {
    ProviderConfigurations configurations =
        new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings());
    configurations.store(
        provider, configuration, configurations.theWriteItWouldMake(provider, configuration));
  }

  private static ProviderConfiguration configurationOf(ConfiguredAccount... configured) {
    return new ProviderConfiguration(RUN, List.of(configured), List.of(), List.of(), Map.of());
  }

  private static ConfiguredAccount account(String accountId, String terminalId) {
    return new ConfiguredAccount(accountId, declaredWith(terminalId, Map.of()));
  }

  private static ConfiguredAccount accountWith(Map<String, String> more) {
    return new ConfiguredAccount("merchant-1", declaredWith(TERMINAL_ID, more));
  }

  private static ConfiguredAccount accountWithout(String key) {
    Map<String, String> declared = new LinkedHashMap<>(declaredWith(TERMINAL_ID, Map.of()));
    declared.remove(key);
    return new ConfiguredAccount("merchant-1", declared);
  }

  private static Map<String, String> declaredWith(String terminalId, Map<String, String> more) {
    Map<String, String> declared = new LinkedHashMap<>(more);
    declared.put(AzericardTerminal.TERMINAL_PROPERTY, terminalId);
    declared.put(AzericardTerminal.MERCHANT_KEY_PROPERTY, AzericardKeyFixture.MERCHANT_PUBLIC);
    declared.put(AzericardTerminal.GATEWAY_KEY_PROPERTY, AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8);
    return declared;
  }

  @Test
  void a_payment_and_a_stored_card_listed_side_by_side_each_say_which_of_the_two_they_are() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    cards.claim(aStoredCard());

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(object -> object.kind().asThisProviderNamesIt())
        .containsExactly("a saved card", "a hold on a card");
  }

  @Test
  void one_answer_for_many_identifiers_holds_what_asking_one_at_a_time_holds() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    cards.claim(aStoredCard());
    List<String> asked =
        List.of(
            STORED_TOKEN,
            "INTREF0000000001",
            TERMINAL_ID,
            "20220628084800",
            "nothing-of-this-run",
            STORED_TOKEN);

    List<ProviderObjectState> batched = provider.states(RUN, asked);

    assertThat(batched)
        .as(
            "a screen labels every identifier an object names from this answer, so one answer for"
                + " many identifiers that held anything other than what asking one at a time"
                + " holds would change what a tester reads beside those names. %s",
            TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(provider, RUN, asked));
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider, RUN, asked));
    assertThat(batched)
        .extracting(ProviderObjectState::providerObjectId)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(STORED_TOKEN, "INTREF0000000001");
    assertThat(provider.states(RUN, List.of("nothing-of-this-run"))).isEmpty();
  }

  @Test
  void a_harness_lists_the_payments_and_the_stored_cards_of_its_test_run_in_one_page() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    cards.claim(aStoredCard());

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(idsOn(page)).containsExactly(STORED_TOKEN, "INTREF0000000001");
    assertThat(idsOn(provider.objects(new ProviderObjectQuery(ANOTHER_RUN, null, 10)))).isEmpty();
  }

  @Test
  void a_page_of_two_stores_carries_on_after_the_object_the_previous_page_ended_on() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    cards.claim(aStoredCard());

    ProviderObjectPage first = provider.objects(new ProviderObjectQuery(RUN, null, 1));
    ProviderObjectPage second = provider.objects(new ProviderObjectQuery(RUN, STORED_TOKEN, 10));

    assertThat(idsOn(first)).containsExactly(STORED_TOKEN);
    assertThat(idsOn(second)).containsExactly("INTREF0000000001");
    assertThat(provider.objects(new ProviderObjectQuery(RUN, "never-stored", 10)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  @Test
  void every_listed_azericard_object_is_opened_by_the_read_that_takes_one_id() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    cards.claim(aStoredCard());

    for (String id : idsOn(provider.objects(new ProviderObjectQuery(RUN, null, 10)))) {
      assertThat(provider.state(RUN, id)).isPresent();
    }
  }

  private static List<String> idsOn(ProviderObjectPage page) {
    List<String> ids = new ArrayList<>();
    for (ProviderObjectState object : ((ProviderObjectPage.Listed) page).objects()) {
      ids.add(object.providerObjectId());
    }
    return ids;
  }

  @Test
  void a_secret_azericard_was_sent_is_redacted_before_it_could_reach_an_audit_row() {
    ProviderAccountFields fields = AzericardTerminal.ACCOUNT_FIELDS;
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

  @Test
  void azericard_completes_no_hosted_page_and_says_what_its_contract_has_instead() {
    payments.insertIfAbsent(aHoldOf("11.48"));
    HostedCompletionOutcome answered =
        provider.complete(
            new HostedCompletionRequest(
                RUN, "INTREF0000000001", PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API));

    assertThat(answered)
        .isEqualTo(new HostedCompletionOutcome.NotOffered(AzericardProvider.NO_PAGE_TO_COMPLETE));
    assertThat(AzericardProvider.NO_PAGE_TO_COMPLETE)
        .contains(AzericardGatewayController.CGI_LINK)
        .contains(AzericardGatewayController.TOKEN_CGI_LINK);
  }

  @Test
  void a_completion_of_an_id_nothing_in_the_run_answers_to_is_no_such_object() {
    assertThat(
            provider.complete(
                new HostedCompletionRequest(
                    RUN, "order-1", PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API)))
        .isInstanceOf(HostedCompletionOutcome.NoSuchObject.class);
  }

  @Test
  void azericard_offers_a_screen_no_payer_step_and_says_it_in_the_words_its_completion_uses() {
    HostedCompletionOffer declared = provider.hostedCompletion(RUN, "order-1");

    assertThat(declared)
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                AzericardGateway.AZERICARD, AzericardProvider.NO_PAGE_TO_COMPLETE));
  }

  @Test
  void answers_a_pending_outcome_with_the_words_its_own_contract_uses_for_having_none() {
    assertThat(
            AzericardOperations.VOCABULARY.refusals(
                new NewScenarioRule(
                    AzericardGateway.AZERICARD,
                    TestRunId.of("azericard-run"),
                    0,
                    ScenarioMatch.anyRequest()
                        .naming(AzericardOperations.VOCABULARY.operationNames().get(0)),
                    new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
                    null)))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.type");
              assertThat(refusal.reason()).isEqualTo(AzericardOperations.NO_PROCESSING_OUTCOME);
            });
    assertThat(AzericardOperations.VOCABULARY.theOperationsThatReportAProcessingState()).isEmpty();
  }

  @Test
  void refuses_to_answer_a_call_with_a_pending_outcome_this_provider_publishes_no_state_for() {
    ScenarioDecision.Pend pending = new ScenarioDecision.Pend("scn_1", Duration.ofSeconds(5), null);

    Assertions.assertThatThrownBy(
            () ->
                AzericardOperationResult.decidedBy(
                    pending,
                    () -> AzericardOperationResult.failed(null, AzericardBody.nothingRendered()),
                    code -> AzericardOperationResult.failed(null, AzericardBody.nothingRendered())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("answers with none");
  }

  @Test
  void the_pairing_azericard_publishes_is_its_cgi_link_the_terminal_and_both_of_its_keys() {
    ProviderPairing pairing = provider.pairing();

    assertThat(pairing.provider()).isEqualTo(AzericardGateway.AZERICARD);
    assertThat(pairing.apiBasePath()).isEqualTo("/cgi-bin/cgi_link");
    assertThat(pairing.settings())
        .extracting(PairingSetting::name)
        .containsExactly(
            "AZERICARD_GATEWAY_URL",
            "AZERICARD_TOKEN_GATEWAY_PATH",
            "AZERICARD_CONTENT_TYPE",
            "AZERICARD_TERMINAL",
            "AZERICARD_MERCHANT_PUBLIC_KEY_PEM",
            "AZERICARD_GATEWAY_PRIVATE_KEY_PEM",
            "SIMULATOR_ACCOUNT_ID",
            "SIMULATOR_TEST_RUN_HEADER",
            "SIMULATOR_TEST_RUN_ID");
    assertThat(pairing.accountFieldsItReads()).containsExactly("terminalId", "apiKey", "apiSecret");
    assertThat(pairing.howARequestNamesItsAccount())
        .contains("TERMINAL names the account")
        .contains("P_SIGN proves it")
        .contains("uppercase-hex SHA256withRSA signature");
  }

  @Test
  void
      the_personas_azericard_publishes_each_seed_a_token_of_the_width_a_stored_card_payment_sends() {
    ProviderPersonas published = provider.personas(new CountingIds());

    assertThat(published.provider()).isEqualTo(AzericardGateway.AZERICARD);
    assertThat(published.theNamesItPublishes())
        .containsExactly(
            "azericard-stored-card-that-succeeds",
            "azericard-stored-card-that-is-declined",
            "azericard-stored-card-whose-completion-answer-is-lost");
    assertThat(published.intentsItPublishesNoneFor()).isEmpty();
    for (DeclaredPersona persona : published.personas()) {
      PersonaFixture instrument = persona.fixtures().get(0);
      assertThat(instrument.theProviderNativeKeysItCarries()).containsExactly("token");
      assertThat(instrument.mint().get("token")).matches("[0-9A-F]{28}");
      assertThat(instrument.card()).isNotNull();
      assertThat(instrument.card().last4()).isEqualTo("2601");
      assertThat(instrument.card().expMonth()).isEqualTo(5);
      assertThat(instrument.card().expYear()).isEqualTo(2031);
    }
  }

  @Test
  void every_persona_azericard_publishes_writes_a_preset_this_module_can_expand() {
    for (DeclaredPersona persona : provider.personas(new CountingIds()).personas()) {
      for (String named : persona.thePresetNamesItWrites()) {
        ScenarioPreset preset = ScenarioPreset.of(named).orElseThrow();
        assertThat(
                provider.expand(
                    new PresetRequest(
                        preset, TestRunId.of("run-a"), 0, ScenarioMatch.anyRequest())))
            .isInstanceOf(PresetExpansion.Rules.class);
      }
    }
  }

  @Test
  void every_azericard_persona_says_a_stored_card_belongs_to_the_terminal_that_stored_it() {
    for (DeclaredPersona persona : provider.personas(new CountingIds()).personas()) {
      assertThat(persona.whatItGives()).contains("account this test run configures for azericard");
    }
  }

  @Test
  void the_compatibility_checks_azericard_publishes_name_five_operations_and_withhold_none() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), new CountingIds());

    assertThat(checks)
        .extracting(ContractCheck::operation, check -> check.provenance().sample())
        .containsExactly(
            tuple(
                AzericardCompatibilityChecks.THE_GATEWAY_AUTHORIZES,
                "azericard/authorization-approved"),
            tuple(
                AzericardCompatibilityChecks.THE_GATEWAY_REFUSES_A_MAC, "azericard/refused-p-sign"),
            tuple(
                AzericardCompatibilityChecks.THE_GATEWAY_MISSES_A_FIELD,
                "azericard/missing-terminal"),
            tuple(
                AzericardCompatibilityChecks.THE_GATEWAY_LOOKS_UP_A_STATUS,
                "azericard/status-inquiry-refused"),
            tuple(AzericardCompatibilityChecks.THE_TRAIL_RECORDS, "simulator/audit-record"));
    assertThat(checks)
        .filteredOn(check -> check.applicability() instanceof Applicability.DoesNotApply)
        .isEmpty();
    assertThat(checks).allSatisfy(check -> assertThat(check.expectations()).isNotEmpty());
  }

  @Test
  void the_authorization_check_azericard_publishes_asserts_the_pair_the_vendor_prints() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), new CountingIds());

    assertThat(checks.get(0).expectations())
        .extracting(ContractExpectation::field, expectation -> expectation.published().value())
        .containsExactly(
            tuple("contentType", "text/html"),
            tuple("action", "0"),
            tuple("rc", "00"),
            tuple("desc", "Approved"));
  }

  @Test
  void declares_the_payment_page_it_keeps_waiting_and_that_a_payer_names_a_card_on_it() {
    assertThat(provider.hostedPages())
        .isEqualTo(
            new HostedPageContract.ObjectsAPayerFinishes(
                List.of(
                    new HostedPageContract.AnObjectItKeepsWaiting(
                        AzericardProvider.A_PAYMENT_PAGE, true))));
  }

  @Test
  void is_reported_apart_from_its_declaration_where_a_run_holds_an_authorization_and_no_page() {
    payments.insertIfAbsent(aHoldOf("11.48"));

    assertThat(TheObjectsADeclarationPromises.of(provider, RUN, List.of("INTREF0000000001")))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("declares it keeps 1 object(s) waiting for a payer")
                    .contains(AzericardProvider.A_PAYMENT_PAGE)
                    .contains("0 of the objects read here publish the facts of one"));
  }

  @ParameterizedTest(name = "reads {0} back as one line per rule")
  @EnumSource(ScenarioPreset.class)
  void every_ready_made_flow_reads_back_as_one_line_per_rule(ScenarioPreset preset) {
    APresetReadsBackInWords.assertItDoes(
        preset, provider.expand(new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest())));
  }
}
