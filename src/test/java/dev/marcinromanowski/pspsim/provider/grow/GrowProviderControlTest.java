package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.smoke.Applicability;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredCard;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
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
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheObjectsADeclarationPromises;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.ui.APresetReadsBackInWords;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrowProviderControlTest {

  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private static final String NO_FURTHER_STATE = "no further state";

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId OTHER_RUN = TestRunId.of("another-run");

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();

  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          deliveries,
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          SimulatorDoubles.clockAt(Instant.parse("2026-09-07T12:00:00Z")),
          ids,
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final GrowCallbacks callbacks =
      SimulatorDoubles.callbacks(
          processes,
          transactions,
          accounts,
          dispatcher,
          SimulatorDoubles.clockAt(Instant.parse("2026-09-07T12:00:00Z")),
          ids);

  private final CompleteHostedProcess completions =
      new CompleteHostedProcess(processes, callbacks, new GrowPayerCards(seededMethods));

  private final SimulatorProvider provider =
      new GrowProvider(
          accounts,
          audit,
          processes,
          transactions,
          dispatcher,
          callbacks,
          SimulatorDoubles.clockAt(Instant.parse("2026-09-07T12:00:00Z")),
          seededMethods,
          scenarioRules,
          SimulatorDoubles.heldResponses(),
          new SettleSuspendedTransaction(
              transactions, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          new RefundTransaction(
              transactions, ids, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          SimulatorDoubles.paymentProcessCreation(processes, scenarioRules, ids),
          SimulatorDoubles.storedCardCharge(processes, transactions, ids, scenarioRules),
          SimulatorDoubles.retraceOfAStoredCardCharge(processes, transactions, scenarioRules),
          completions);

  @Test
  void the_wire_namespaces_and_the_answer_off_them_are_the_ones_this_module_declares() {
    assertThat(provider.wireRoutes()).isEqualTo(GrowWireFaults.ROUTES);
    assertThat(provider.wireRoutes().covers("/api/light/server/1.0/createPaymentProcess"))
        .as("a namespace this module forgot to claim leaves a client on the wrong answer")
        .isTrue();
    for (WireFault fault : WireFault.values()) {
      assertThat(provider.theAnswerOffItsRoutes(fault, GrowWireFaults.API))
          .as(fault.name())
          .isSameAs(GrowWireFaults.THE_ANSWER);
    }
  }

  @Test
  void a_grow_fixture_carries_no_provider_native_id_and_grow_says_why_for_each_published_key() {
    ProviderPaymentMethodFields declared = provider.paymentMethodFields();

    assertThat(declared.keys()).isEmpty();
    assertThat(declared.theRefusalOf(ProviderPaymentMethodFields.TOKEN))
        .contains("createTransactionWithToken")
        .contains("140");
    assertThat(declared.theRefusalOf(ProviderPaymentMethodFields.CUSTOMER_ID))
        .contains("publishes no customer or owner id");
  }

  @Test
  void a_card_seeded_for_grow_carries_the_card_facts_and_nothing_a_request_is_driven_by() {
    InMemoryPaymentMethodFixtures seeded = new InMemoryPaymentMethodFixtures();
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seeded,
            scenarioRules,
            new SimulatorDoubles.Runs(TestRunId.of("default")),
            new RecordedUnitsOfWork(),
            TestRunId.of("default"));

    assertThat(
            admission.seed(
                provider,
                new DeclaredPaymentMethodFixture(
                    "grow-fixtures",
                    "merchant-1",
                    "customer-123",
                    "card",
                    "visa-success",
                    new DeclaredCard("Visa", "4580", 4, 2024, "credit"),
                    Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
                    null),
                ObjectOrigin.CONTROL_API))
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> assertThat(refused.property()).isEqualTo("providerIds.token"));
  }

  @Test
  void resetting_a_test_run_counts_the_payment_methods_it_seeded_among_what_it_cleared() {
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seededMethods,
            scenarioRules,
            new SimulatorDoubles.Runs(TestRunId.of("default")),
            new RecordedUnitsOfWork(),
            TestRunId.of("default"));
    admission.seed(
        provider, aGrowCardFor("grow-fixtures", "visa-success"), ObjectOrigin.CONTROL_API);

    assertThat(provider.reset(TestRunId.of("grow-fixtures"), ResetScope.TEST_RUN).clearedObjects())
        .isEqualTo(1);
    assertThat(seededMethods.of(GrowLightApi.GROW, TestRunId.of("grow-fixtures"))).isEmpty();
  }

  @Test
  void resetting_every_test_run_counts_the_payment_methods_of_all_of_them_among_what_it_cleared() {
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seededMethods,
            scenarioRules,
            new SimulatorDoubles.Runs(TestRunId.of("default")),
            new RecordedUnitsOfWork(),
            TestRunId.of("default"));
    admission.seed(
        provider, aGrowCardFor("grow-fixtures", "visa-success"), ObjectOrigin.CONTROL_API);

    assertThat(provider.reset(TestRunId.of("grow-fixtures"), ResetScope.ALL).clearedObjects())
        .isEqualTo(1);
  }

  private static DeclaredPaymentMethodFixture aGrowCardFor(String testRunId, String alias) {
    return new DeclaredPaymentMethodFixture(
        testRunId,
        "merchant-1",
        "customer-123",
        "card",
        alias,
        new DeclaredCard("Visa", "4580", 4, 2024, "credit"),
        null,
        null);
  }

  @Test
  void a_grow_fixture_that_names_no_provider_native_id_is_seeded_and_removed_again() {
    InMemoryPaymentMethodFixtures seeded = new InMemoryPaymentMethodFixtures();
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seeded,
            scenarioRules,
            new SimulatorDoubles.Runs(TestRunId.of("default")),
            new RecordedUnitsOfWork(),
            TestRunId.of("default"));

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPaymentMethodFixture(
                "grow-fixtures",
                "merchant-1",
                "customer-123",
                "card",
                "visa-success",
                new DeclaredCard("Visa", "4580", 4, 2024, "credit"),
                null,
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict).isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Seeded.class);
    PaymentMethodFixture fixture =
        ((PaymentMethodFixtureAdmission.Verdict.Seeded) verdict).fixture();
    assertThat(fixture.providerIds()).isEmpty();
    assertThat(fixture.card()).isNotNull();

    assertThat(admission.forget(provider, "grow-fixtures", fixture.fixtureId()))
        .isInstanceOf(PaymentMethodFixtureAdmission.Removal.Removed.class);
    assertThat(seeded.of(GrowLightApi.GROW, TestRunId.of("grow-fixtures"))).isEmpty();
  }

  @Test
  void answers_to_the_provider_name_the_control_api_addresses_it_by() {
    assertThat(provider.id().value()).isEqualTo("grow");
  }

  @ParameterizedTest(name = "rejects an account with no {0}")
  @CsvSource({"userId,,grow-page", "pageCode,grow-user,"})
  void rejects_an_account_that_cannot_authenticate_a_grow_request(
      String missing, String userId, String pageCode) {
    Map<String, String> declared = new HashMap<>();
    declared.put("userId", userId);
    declared.put("pageCode", pageCode);
    ProviderConfiguration configuration = configurationWith(account("merchant-1", declared));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage("accounts[0].configuration." + missing + " is required by provider grow");
  }

  @Test
  void names_the_property_and_never_the_value_it_rejected() {
    ProviderConfiguration configuration =
        configurationWith(
            account(
                "merchant-1",
                Map.of("userId", "sk-live-secret", "pageCode", "  ", "captureWindow", "P7D")));

    assertThatThrownBy(() -> apply(configuration))
        .hasMessageContaining("accounts[0].configuration.pageCode")
        .hasMessageNotContaining("sk-live-secret");
  }

  @Test
  void applies_accounts_and_webhook_targets_together() {
    apply(
        new ProviderConfiguration(
            RUN,
            List.of(growAccount("merchant-1")),
            List.of(new WebhookTarget("merchant-1", "https://merchant.example/hook")),
            List.of(),
            Map.of()));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(account -> assertThat(account.testRunId()).isEqualTo(RUN));
    assertThat(accounts.identitiesOf(GrowLightApi.GROW, RUN)).containsExactly("merchant-1");
    assertThat(accounts.webhookTargetsOf(GrowLightApi.GROW, RUN)).hasSize(1);
  }

  @Test
  void refuses_a_configuration_key_no_grow_account_reads_and_stores_nothing_of_that_request() {
    apply(configurationWith(growAccount("merchant-1")));

    ProviderConfiguration unreadable =
        configurationWith(
            account(
                "merchant-2",
                Map.of(
                    "userId", "another-user", "pageCode", "another-page", "terminalId", "7777")));

    assertThatThrownBy(() -> apply(unreadable))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.terminalId")
        .hasMessageNotContaining("7777");
    assertThat(accounts.all())
        .singleElement()
        .satisfies(stored -> assertThat(stored.accountId()).isEqualTo("merchant-1"));
  }

  @Test
  void accepts_the_acknowledgement_mode_grow_reads_out_of_the_configuration() {
    apply(configurationWith(waitingAccount("merchant-1")));

    assertThat(accounts.all())
        .singleElement()
        .satisfies(
            stored ->
                assertThat(stored.acknowledgement())
                    .isEqualTo(GrowAcknowledgementMode.APPROVE_TRANSACTION));
  }

  @Test
  void rejects_an_acknowledgement_mode_it_does_not_recognise() {
    ProviderConfiguration configuration =
        configurationWith(accountWith("callbackAcknowledgement", "WHEN_THE_MOON_IS_FULL"));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.callbackAcknowledgement")
        .hasMessageContaining("HTTP_2XX")
        .hasMessageContaining("APPROVE_TRANSACTION")
        .hasMessageNotContaining("WHEN_THE_MOON_IS_FULL")
        .hasMessageNotContaining("sk-live-secret");
  }

  @Test
  void rejects_a_settlement_ceiling_it_does_not_recognise() {
    ProviderConfiguration configuration =
        configurationWith(accountWith(GrowSettlementCeiling.SETTING, "AS_HIGH_AS_YOU_LIKE"));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.settlementCeiling")
        .hasMessageContaining("FRAME_CAPTURE")
        .hasMessageContaining("THIRTY_PERCENT_ABOVE")
        .hasMessageNotContaining("AS_HIGH_AS_YOU_LIKE")
        .hasMessageNotContaining("sk-live-secret");
  }

  @ParameterizedTest(name = "rejects an unreadable {0} naming the shape {0} itself takes")
  @CsvSource({"captureWindow, P7D, P10D", "automaticVoidAfter, P10D, P7D"})
  void rejects_a_hold_lifetime_that_is_not_a_duration(
      String setting, String itsOwnExample, String theOtherSettingsExample) {
    ProviderConfiguration configuration = configurationWith(accountWith(setting, "seven days"));

    Throwable refusal = catchThrowable(() -> apply(configuration));

    assertThat(refusal)
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration." + setting)
        .hasMessageNotContaining("seven days")
        .hasMessageNotContaining("sk-live-secret");
    assertThat(refusal)
        .as(
            "both refusals named P7D, so a tester who wrote the void window unreadably was shown"
                + " the capture window's shape while the field's own sentence said P10D")
        .hasMessageContaining(itsOwnExample)
        .hasMessageNotContaining(theOtherSettingsExample);
  }

  @Test
  void rejects_a_refund_allowance_it_does_not_recognise() {
    ProviderConfiguration configuration =
        configurationWith(accountWith(GrowRefundAllowance.SETTING, "AS_MANY_AS_YOU_LIKE"));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.refundAllowance")
        .hasMessageContaining("ONE_REFUND")
        .hasMessageContaining("TWO_REFUNDS")
        .hasMessageNotContaining("AS_MANY_AS_YOU_LIKE")
        .hasMessageNotContaining("sk-live-secret");
  }

  @Test
  void rejects_a_refund_excess_rejection_it_does_not_recognise() {
    ProviderConfiguration configuration =
        configurationWith(accountWith(GrowRefundExcessRejection.SETTING, "WHATEVER_IS_HANDY"));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.refundExcessRejection")
        .hasMessageContaining("ORIGINAL_AMOUNT")
        .hasMessageContaining("CHARGE_AMOUNT")
        .hasMessageContaining("TRANSACTION_AMOUNT")
        .hasMessageNotContaining("WHATEVER_IS_HANDY")
        .hasMessageNotContaining("sk-live-secret");
  }

  @Test
  void rejects_a_merchant_balance_it_does_not_recognise() {
    ProviderConfiguration configuration =
        configurationWith(accountWith(GrowMerchantBalance.SETTING, "OVERDRAWN"));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.merchantBalance")
        .hasMessageContaining("AVAILABLE")
        .hasMessageContaining("EMPTY")
        .hasMessageNotContaining("OVERDRAWN")
        .hasMessageNotContaining("sk-live-secret");
  }

  @Test
  void rejects_a_process_lookup_rejection_it_does_not_recognise() {
    ProviderConfiguration configuration =
        configurationWith(accountWith(GrowProcessLookupRejection.SETTING, "WHICHEVER_IS_HANDY"));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[0].configuration.processLookupRejection")
        .hasMessageContaining("PUBLISHED_EXAMPLE")
        .hasMessageContaining("PUBLISHED_TABLE")
        .hasMessageNotContaining("WHICHEVER_IS_HANDY")
        .hasMessageNotContaining("sk-live-secret");
  }

  private static ConfiguredAccount accountWith(String setting, String value) {
    return account(
        "merchant-1", Map.of("userId", "sk-live-secret", "pageCode", "grow-page", setting, value));
  }

  @ParameterizedTest(name = "rejects two accounts of one test run sharing a {0}")
  @CsvSource({
    "userId,shared-user,page-1,shared-user,page-2",
    "pageCode,user-1,shared,user-2,shared"
  })
  void rejects_two_accounts_of_one_test_run_that_share_a_credential(
      String property, String firstUserId, String firstPageCode, String userId, String pageCode) {
    ProviderConfiguration configuration =
        configurationWith(
            account("merchant-1", Map.of("userId", firstUserId, "pageCode", firstPageCode)),
            account("merchant-2", Map.of("userId", userId, "pageCode", pageCode)));

    assertThatThrownBy(() -> apply(configuration))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "accounts[1].configuration."
                + property
                + " is already configured for another account in this test run")
        .hasMessageNotContaining("shared");
  }

  @Test
  void leaves_another_test_run_free_to_configure_the_same_credentials() {
    ConfiguredAccount account = growAccount("merchant-1");

    apply(new ProviderConfiguration(RUN, List.of(account), List.of(), List.of(), Map.of()));
    apply(new ProviderConfiguration(OTHER_RUN, List.of(account), List.of(), List.of(), Map.of()));

    assertThat(accounts.all()).hasSize(2);
  }

  @Test
  void a_configuration_it_refuses_leaves_the_accounts_and_targets_of_the_last_one_it_took() {
    apply(
        new ProviderConfiguration(
            RUN,
            List.of(growAccount("merchant-1")),
            List.of(new WebhookTarget("merchant-1", "https://merchant.example/hook")),
            List.of(),
            Map.of()));

    ProviderConfiguration oneAccountNamingNothing =
        new ProviderConfiguration(
            RUN,
            List.of(growAccount("merchant-2"), account("merchant-3", Map.of())),
            List.of(),
            List.of(),
            Map.of());

    assertThatThrownBy(() -> apply(oneAccountNamingNothing))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("accounts[1]");

    assertThat(accounts.all())
        .singleElement()
        .satisfies(stored -> assertThat(stored.accountId()).isEqualTo("merchant-1"));
    assertThat(accounts.webhookTargetsOf(GrowLightApi.GROW, RUN)).hasSize(1);
  }

  @Test
  void an_account_the_core_removed_is_gone_from_grows_own_table_and_the_others_are_not() {
    apply(configurationWith(growAccount("merchant-1"), growAccount("merchant-2")));

    boolean removed =
        new ProviderConfigurations(
                accounts,
                new BoundRouteAliases(),
                new DeclaredProviderSettings(List.of()),
                new InMemoryTestRunSettings())
            .remove(provider, RUN, "merchant-1");

    assertThat(removed).isTrue();
    assertThat(accounts.all()).extracting(GrowAccount::accountId).containsExactly("merchant-2");
    assertThat(accounts.find(RUN, "merchant-1")).isEmpty();
  }

  @Test
  void the_credential_a_removed_account_held_is_free_for_another_account_of_the_same_run() {
    apply(configurationWith(growAccount("merchant-1")));
    new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings())
        .remove(provider, RUN, "merchant-1");

    apply(
        configurationWith(
            account(
                "merchant-2",
                Map.of("userId", "grow-user-merchant-1", "pageCode", "grow-page-merchant-1"))));

    assertThat(accounts.find(RUN, "merchant-2"))
        .get()
        .satisfies(held -> assertThat(held.userId()).isEqualTo("grow-user-merchant-1"));
  }

  @Test
  void grow_publishes_the_fields_an_account_of_it_would_need_and_names_itself() {
    ProviderAccountFields declared = provider.accountFields();

    assertThat(declared.provider()).isEqualTo(GrowLightApi.GROW);
    assertThat(declared.keys()).containsExactlyInAnyOrderElementsOf(GrowAccount.CONFIGURATION_KEYS);
    assertThat(declared.secretKeys())
        .containsExactlyInAnyOrder(GrowAccount.USER_ID, GrowAccount.PAGE_CODE);
    assertThat(declared.field(GrowAccount.USER_ID))
        .get()
        .satisfies(field -> assertThat(field.required()).isTrue());
    assertThat(declared.field(GrowAcknowledgementMode.SETTING))
        .get()
        .satisfies(field -> assertThat(field.required()).isFalse());
  }

  @Test
  void a_second_account_is_added_without_the_first_one_being_sent_again() {
    apply(configurationWith(growAccount("merchant-1")));

    List<String> configured = apply(configurationWith(growAccount("merchant-2")));

    assertThat(accounts.all())
        .extracting(GrowAccount::accountId)
        .containsExactlyInAnyOrder("merchant-1", "merchant-2");
    assertThat(configured).containsExactly("merchant-1", "merchant-2");
    assertThat(accounts.find(RUN, "merchant-1"))
        .get()
        .satisfies(stored -> assertThat(stored.userId()).isEqualTo("grow-user-merchant-1"));
  }

  @Test
  void a_configuration_that_names_one_account_leaves_the_other_accounts_of_the_run_alone() {
    apply(configurationWith(growAccount("merchant-1"), growAccount("merchant-2")));

    apply(configurationWith(growAccount("merchant-2")));

    assertThat(accounts.all())
        .extracting(GrowAccount::accountId)
        .containsExactlyInAnyOrder("merchant-1", "merchant-2");
  }

  @Test
  void a_credential_an_account_this_call_leaves_in_place_holds_is_refused_by_name() {
    apply(configurationWith(growAccount("merchant-1")));

    ProviderConfiguration colliding =
        configurationWith(
            account(
                "merchant-2",
                Map.of("userId", "grow-user-merchant-1", "pageCode", "grow-page-merchant-2")));

    assertThatThrownBy(() -> apply(colliding))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessage(
            "accounts[0].configuration.userId is already configured for another account in this"
                + " test run");
  }

  @Test
  void an_account_may_take_the_credential_of_an_account_the_same_call_moves_off_it() {
    apply(configurationWith(growAccount("merchant-1")));

    apply(
        configurationWith(
            account("merchant-1", Map.of("userId", "moved-user", "pageCode", "moved-page")),
            account(
                "merchant-2",
                Map.of("userId", "grow-user-merchant-1", "pageCode", "grow-page-merchant-1"))));

    assertThat(accounts.find(RUN, "merchant-2"))
        .get()
        .satisfies(stored -> assertThat(stored.userId()).isEqualTo("grow-user-merchant-1"));
  }

  @Test
  void reset_clears_one_test_run_and_leaves_the_others_alone() {
    apply(configurationWith(growAccount("merchant-1")));
    processes.insertIfAbsent(process(RUN, "1"));
    processes.insertIfAbsent(process(OTHER_RUN, "2"));
    audit.record(GrowAuditFixtures.record(RUN, "aud_1"));
    audit.record(GrowAuditFixtures.record(RUN, "aud_2"));
    audit.record(GrowAuditFixtures.record(OTHER_RUN, "aud_3"));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.TEST_RUN);

    assertThat(outcome).isEqualTo(new ResetOutcome(1, 2));
    assertThat(processes.all())
        .singleElement()
        .satisfies(p -> assertThat(p.testRunId()).isEqualTo(OTHER_RUN));
    assertThat(audit.all())
        .singleElement()
        .satisfies(r -> assertThat(r.testRunId()).isEqualTo(OTHER_RUN));
  }

  @Test
  void reset_leaves_the_configuration_alone_so_the_next_run_still_authenticates() {
    apply(configurationWith(growAccount("merchant-1")));

    provider.reset(RUN, ResetScope.TEST_RUN);
    provider.reset(RUN, ResetScope.ALL);

    assertThat(accounts.all())
        .singleElement()
        .satisfies(stored -> assertThat(stored.accountId()).isEqualTo("merchant-1"));
  }

  @Test
  void reset_all_clears_every_test_run() {
    apply(configurationWith(growAccount("merchant-1")));
    processes.insertIfAbsent(process(RUN, "1"));
    processes.insertIfAbsent(process(OTHER_RUN, "2"));
    audit.record(GrowAuditFixtures.record(RUN, "aud_1"));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.ALL);

    assertThat(outcome).isEqualTo(new ResetOutcome(2, 1));
    assertThat(processes.all()).isEmpty();
    assertThat(audit.all()).isEmpty();
  }

  @ParameterizedTest(name = "accepts a scenario naming the Grow error id {0}")
  @ValueSource(strings = {"54", "701", "803", "440", "890", "1013"})
  void accepts_a_scenario_whose_provider_code_is_an_error_id_grow_can_answer_with(String code) {
    assertThat(provider.vocabulary().refusals(scenarioNaming(code))).isEmpty();
  }

  @ParameterizedTest(name = "accepts a scenario naming the published decline reason {0}")
  @ValueSource(
      strings = {
        "expiredCard",
        "stolenCard",
        "blockedCard",
        "creditCompanyDidNotApprove",
        "cardInactiveOrExpired",
        "cardBlockedByTheCreditCompany",
        "systemBlock"
      })
  void accepts_a_scenario_naming_a_decline_reason_grow_publishes(String reason) {
    assertThat(provider.vocabulary().refusals(scenarioNaming(reason))).isEmpty();
  }

  @Test
  void tells_a_tester_which_reasons_it_may_name_and_which_of_them_grow_calls_final() {
    assertThat(provider.vocabulary().failureCodes().describedAs())
        .contains("stolenCard (סופי)")
        .contains("creditCompanyDidNotApprove (לא סופי)");
  }

  @ParameterizedTest(name = "refuses a scenario naming the provider code {0}")
  @ValueSource(
      strings = {
        "GATEWAY_TIMEOUT",
        "701a",
        "7.01",
        "701 ",
        " 701",
        "-1",
        "+813",
        "0813",
        "0",
        "5",
        "81300"
      })
  void refuses_a_scenario_whose_provider_code_grow_could_never_answer_with(String code) {
    List<ScenarioRefusal> refused = provider.vocabulary().refusals(scenarioNaming(code));

    assertThat(refused)
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.providerCode");
    assertThat(refused.get(0).reason()).doesNotContain(code.trim());
  }

  @Test
  void reads_the_provider_code_of_a_step_of_a_story_the_same_way_it_reads_a_rule_with_none() {
    NewScenarioRule unanswerable =
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("GATEWAY_TIMEOUT"),
            new ScenarioStory(
                "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked"));

    assertThat(provider.vocabulary().refusals(unanswerable))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.providerCode");
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        GrowLightApi.GROW,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.TerminalFailure("803"),
                        new ScenarioStory(
                            "capture-retries",
                            StoryKeying.FLOW,
                            ScenarioStory.INITIAL_STATE,
                            "asked"))))
        .isEmpty();
  }

  @Test
  void refuses_a_scenario_matching_a_currency_because_grow_publishes_no_currency_parameter() {
    NewScenarioRule matchingCurrency =
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(null, null, null, null, null, null, "PLN", null, null, Map.of()),
            new ScenarioAction.TerminalFailure("701"),
            null);

    assertThat(provider.vocabulary().refusals(matchingCurrency))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.currency");
  }

  @Test
  void refuses_a_scenario_matching_an_order_because_grow_publishes_no_order_identifier() {
    NewScenarioRule matchingOrder =
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(null, "order-1", null, null, null, null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("701"),
            null);

    List<ScenarioRefusal> refused = provider.vocabulary().refusals(matchingOrder);

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("match.orderId");
    assertThat(refused.get(0).reason())
        .contains("transactionUniqueIdentifier", "transactionGroupIdentifier");
  }

  @Test
  void accepts_a_scenario_matching_a_payment_method_the_stored_card_flow_will_supply() {
    NewScenarioRule matchingPaymentMethod =
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(null, null, null, null, "pm-1", null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("701"),
            null);

    assertThat(provider.vocabulary().refusals(matchingPaymentMethod)).isEmpty();
  }

  @Test
  void refuses_a_scenario_matching_a_customer_because_grow_publishes_no_owner_id() {
    NewScenarioRule matchingCustomer =
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(
                null, null, null, "cus_sim_123", null, null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("701"),
            null);

    assertThat(provider.vocabulary().refusals(matchingCustomer))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.customerId");
  }

  @Test
  void answers_one_request_with_every_property_it_cannot_apply() {
    NewScenarioRule unapplicable =
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(
                null, null, null, "cus_sim_123", null, null, "PLN", null, null, Map.of()),
            new ScenarioAction.TerminalFailure("GATEWAY_TIMEOUT"),
            null);

    assertThat(provider.vocabulary().refusals(unapplicable))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.providerCode", "match.customerId", "match.currency");
  }

  @ParameterizedTest(name = "a story about {0} is keyed on the payment process")
  @ValueSource(
      strings = {
        "createPaymentProcess",
        "approveTransaction",
        "settleSuspendedTransaction",
        "refundTransaction",
        "getTransactionInfo",
        "getPaymentProcessInfo"
      })
  void keys_every_operation_it_exposes_on_the_process_so_a_story_can_span_a_whole_flow(
      String operation) {
    assertThat(provider.vocabulary().operation(operation).orElseThrow().storiesKeyedOn())
        .isEqualTo(GrowOperations.PAYMENT_PROCESS);
  }

  @Test
  void declares_the_eight_operations_it_answers_on_this_contract_and_no_other() {
    assertThat(provider.vocabulary().operationNames())
        .containsExactly(
            "createPaymentProcess",
            "createTransactionWithToken",
            "approveTransaction",
            "settleSuspendedTransaction",
            "refundTransaction",
            "getTransactionInfo",
            "getPaymentProcessInfo",
            "getTokenTransactionsByExternalIdentifiers");
  }

  @Test
  void declares_the_shape_of_a_decline_code_rather_than_only_judging_a_candidate_rule() {
    assertThat(provider.vocabulary().failureCodes().admits("813")).isTrue();
    assertThat(provider.vocabulary().failureCodes().admits("GATEWAY_TIMEOUT")).isFalse();
    assertThat(provider.vocabulary().failureCodes().describedAs())
        .contains("err.id", GrowErrors.ERROR_ID_SHAPE);
  }

  @Test
  void declares_a_story_of_two_grow_operations_as_one_a_step_of_the_other_can_see() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    List.of(
                        new NewScenarioRule(
                            GrowLightApi.GROW,
                            RUN,
                            0,
                            ScenarioMatch.anyRequest().naming("createPaymentProcess"),
                            new ScenarioAction.FixedSuccess(),
                            new ScenarioStory(
                                "preauth-then-capture",
                                StoryKeying.FLOW,
                                ScenarioStory.INITIAL_STATE,
                                "held")),
                        new NewScenarioRule(
                            GrowLightApi.GROW,
                            RUN,
                            0,
                            ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
                            new ScenarioAction.TerminalFailure("809"),
                            new ScenarioStory(
                                "preauth-then-capture", StoryKeying.FLOW, "held", null))),
                    List.of()))
        .isEmpty();
  }

  @Test
  void accepts_a_scenario_naming_the_object_it_wants_declined() {
    NewScenarioRule namingAHold =
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(null, null, "9000001", null, null, null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("814"),
            null);

    assertThat(provider.vocabulary().refusals(namingAHold)).isEmpty();
  }

  @Test
  void reset_clears_the_scenario_rules_of_one_test_run_and_counts_them() {
    scenarioRules.add(scenarioNaming("701"));
    scenarioRules.add(scenarioNaming("803"));
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            OTHER_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("701"),
            null));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.TEST_RUN);

    assertThat(outcome).isEqualTo(new ResetOutcome(2, 0));
    assertThat(scenarioRules.of(GrowLightApi.GROW, RUN)).isEmpty();
    assertThat(scenarioRules.of(GrowLightApi.GROW, OTHER_RUN)).hasSize(1);
  }

  @Test
  void reset_all_clears_the_scenario_rules_of_every_test_run() {
    scenarioRules.add(scenarioNaming("701"));
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            OTHER_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("701"),
            null));

    assertThat(provider.reset(RUN, ResetScope.ALL)).isEqualTo(new ResetOutcome(2, 0));
    assertThat(scenarioRules.of(GrowLightApi.GROW, RUN)).isEmpty();
    assertThat(scenarioRules.of(GrowLightApi.GROW, OTHER_RUN)).isEmpty();
  }

  @Test
  void the_disclosure_of_a_grow_account_names_every_configuration_key_grow_reads() {
    apply(configurationWith(growAccount("m1")));

    DisclosedAccount disclosed = provider.disclosureOf(RUN, "m1").orElseThrow();

    assertThat(disclosed.accountId()).isEqualTo("m1");
    assertThat(disclosed.disclosed().keySet())
        .containsExactlyInAnyOrderElementsOf(GrowAccount.CONFIGURATION_KEYS);
  }

  @Test
  void grow_answers_a_marker_for_the_credential_pair_a_request_authenticates_on() {
    apply(configurationWith(growAccount("m1")));

    Map<String, List<String>> disclosed =
        provider.disclosureOf(RUN, "m1").orElseThrow().disclosed();

    assertThat(disclosed.get(GrowAccount.USER_ID)).containsExactly(Redaction.REDACTED);
    assertThat(disclosed.get(GrowAccount.PAGE_CODE)).containsExactly(Redaction.REDACTED);
    assertThat(disclosed.values().stream().flatMap(List::stream))
        .doesNotContain("grow-user-m1", "grow-page-m1");
  }

  @Test
  void grow_hands_over_the_behaviour_it_was_configured_with_rather_than_hiding_it() {
    apply(configurationWith(growAccount("m1")));

    Map<String, List<String>> disclosed =
        provider.disclosureOf(RUN, "m1").orElseThrow().disclosed();

    assertThat(disclosed.get(GrowAcknowledgementMode.SETTING))
        .containsExactly(GrowAcknowledgementMode.of(null).name());
    assertThat(disclosed.get(GrowHoldWindow.CAPTURE_WINDOW))
        .containsExactly(GrowHoldWindow.PUBLISHED_DEFAULT.captureWindow().toString());
  }

  @Test
  void a_grow_account_of_another_test_run_is_not_disclosed_under_this_one() {
    apply(configurationWith(growAccount("m1")));

    assertThat(provider.disclosureOf(OTHER_RUN, "m1")).isEmpty();
  }

  private static NewScenarioRule scenarioNaming(String providerCode) {
    return new NewScenarioRule(
        GrowLightApi.GROW,
        RUN,
        0,
        ScenarioMatch.anyRequest(),
        new ScenarioAction.TerminalFailure(providerCode),
        null);
  }

  private List<String> apply(ProviderConfiguration configuration) {
    ProviderConfigurations configurations =
        new ProviderConfigurations(
            accounts,
            new BoundRouteAliases(),
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings());
    return configurations.store(
        provider, configuration, configurations.theWriteItWouldMake(provider, configuration));
  }

  private static ProviderConfiguration configurationWith(ConfiguredAccount... configured) {
    return new ProviderConfiguration(RUN, List.of(configured), List.of(), List.of(), Map.of());
  }

  private static ConfiguredAccount account(String accountId, Map<String, String> configuration) {
    return new ConfiguredAccount(accountId, configuration);
  }

  private static ConfiguredAccount growAccount(String accountId) {
    return account(
        accountId,
        Map.of("userId", "grow-user-" + accountId, "pageCode", "grow-page-" + accountId));
  }

  private static ConfiguredAccount waitingAccount(String accountId) {
    return account(
        accountId,
        Map.of(
            "userId",
            "grow-user",
            "pageCode",
            "grow-page",
            "callbackAcknowledgement",
            "APPROVE_TRANSACTION"));
  }

  private static GrowPaymentProcess process(TestRunId testRunId, String processId) {
    return GrowAuditFixtures.process(testRunId, processId);
  }

  @ParameterizedTest(name = "accepts a scenario naming the operation {0}")
  @ValueSource(
      strings = {
        "createPaymentProcess",
        "createTransactionWithToken",
        "approveTransaction",
        "settleSuspendedTransaction",
        "refundTransaction",
        "getTransactionInfo",
        "getPaymentProcessInfo"
      })
  void accepts_a_scenario_naming_an_operation_grow_answers_on_this_contract(String operation) {
    assertThat(provider.vocabulary().refusals(namingOperation(operation))).isEmpty();
  }

  @ParameterizedTest(name = "refuses a scenario naming the operation {0}")
  @ValueSource(
      strings = {
        "cancelBitTransaction",
        "createTransactionWithTokens",
        "SETTLE_SUSPENDED_TRANSACTION",
        "settleSuspendedTransaction ",
        "CAPTURE"
      })
  void refuses_a_scenario_naming_an_operation_grow_does_not_expose(String operation) {
    List<ScenarioRefusal> refused = provider.vocabulary().refusals(namingOperation(operation));

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("match.operation");
    assertThat(refused.get(0).reason()).contains("settleSuspendedTransaction", "refundTransaction");
  }

  @Test
  void names_the_operation_predicate_and_never_the_operation_name_it_could_not_apply() {
    List<ScenarioRefusal> refused =
        provider.vocabulary().refusals(namingOperation("cancelBitTransaction"));

    assertThat(refused.get(0).property()).isEqualTo("match.operation");
    assertThat(refused.get(0).reason()).doesNotContain("cancelBitTransaction");
  }

  @Test
  void expands_a_hold_cancellation_into_the_settlement_and_the_refund_grow_needs_for_one() {
    PresetExpansion.Rules expanded = expand(ScenarioPreset.CANCEL_HOLD_SUCCESS);

    assertThat(expanded.rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly("settleSuspendedTransaction", "refundTransaction");
    assertThat(expanded.rules())
        .extracting(NewScenarioRule::action)
        .containsOnly(new ScenarioAction.FixedSuccess());
  }

  @Test
  void expands_a_capture_decline_into_a_decline_of_the_capture_and_nothing_against_the_hold() {
    PresetExpansion.Rules expanded = expand(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE);

    assertThat(expanded.rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly("createPaymentProcess", "settleSuspendedTransaction");
    assertThat(expanded.rules().get(0).action()).isEqualTo(new ScenarioAction.FixedSuccess());
    assertThat(expanded.rules().get(1).action())
        .isEqualTo(new ScenarioAction.TerminalFailure(GrowPresets.CAPTURE_DECLINE_CODE));
  }

  @Test
  void expands_an_accepted_capture_whose_response_was_lost_into_a_fault_on_the_capture_alone() {
    PresetExpansion.Rules expanded = expand(ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST);

    assertThat(expanded.rules()).hasSize(1);
    assertThat(expanded.rules().get(0).match().operation()).isEqualTo("settleSuspendedTransaction");
    assertThat(expanded.rules().get(0).action())
        .isEqualTo(new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()));
  }

  @Test
  void expands_a_gateway_failure_and_its_repeat_into_two_steps_of_one_story_about_one_payment() {
    PresetExpansion.Rules expanded = expand(ScenarioPreset.PSP_5XX_THEN_SUCCESS);

    assertThat(expanded.rules())
        .extracting(NewScenarioRule::story)
        .containsExactly(
            new ScenarioStory(
                ScenarioPreset.PSP_5XX_THEN_SUCCESS.publishedName(),
                StoryKeying.FLOW,
                ScenarioStory.INITIAL_STATE,
                GrowPresets.ANSWERED_WITHOUT_THE_CAPTURE),
            new ScenarioStory(
                ScenarioPreset.PSP_5XX_THEN_SUCCESS.publishedName(),
                StoryKeying.FLOW,
                GrowPresets.ANSWERED_WITHOUT_THE_CAPTURE,
                null));
    assertThat(expanded.rules().get(0).action())
        .isEqualTo(
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(GrowPresets.UNREACHED_GATEWAY_STATUS)));
    assertThat(expanded.rules().get(1).action()).isEqualTo(new ScenarioAction.FixedSuccess());
    assertThat(provider.vocabulary().refusals(expanded.rules(), List.of())).isEmpty();
  }

  @ParameterizedTest(name = "{0} expands into {1}")
  @MethodSource("thePresetsGrowExpands")
  void every_preset_grow_expands_names_the_operations_of_the_flow_and_which_of_them_are_steps(
      ScenarioPreset preset, List<String> flowAsWritten) {
    assertThat(expand(preset).rules())
        .extracting(GrowProviderControlTest::asWritten)
        .containsExactlyElementsOf(flowAsWritten);
  }

  private static String asWritten(NewScenarioRule rule) {
    if (rule.action() instanceof ScenarioAction.OnTheDelivery shaping) {
      String eventType = rule.match().eventType();
      return (eventType == null ? "every delivery" : "a delivery reporting " + eventType)
          + ", "
          + ScenarioGrammar.typeOf(shaping);
    }
    ScenarioStory step = rule.story();
    return step == null
        ? rule.match().operation() + " outside any story"
        : rule.match().operation()
            + " on "
            + step.keyedOn().name()
            + " in story "
            + step.name()
            + " from "
            + step.whenState()
            + " to "
            + (step.thenState() == null ? NO_FURTHER_STATE : step.thenState());
  }

  private static String aStepOfTheGatewayFailureStory(String whenState, String thenState) {
    return "settleSuspendedTransaction on "
        + StoryKeying.FLOW.name()
        + " in story "
        + ScenarioPreset.PSP_5XX_THEN_SUCCESS.publishedName()
        + " from "
        + whenState
        + " to "
        + thenState;
  }

  private static List<Arguments> thePresetsGrowExpands() {
    return List.of(
        Arguments.of(
            ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
            List.of(
                "createPaymentProcess outside any story",
                "settleSuspendedTransaction outside any story")),
        Arguments.of(
            ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
            List.of(
                "createPaymentProcess outside any story",
                "settleSuspendedTransaction outside any story")),
        Arguments.of(
            ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST,
            List.of("settleSuspendedTransaction outside any story")),
        Arguments.of(
            ScenarioPreset.CANCEL_HOLD_SUCCESS,
            List.of(
                "settleSuspendedTransaction outside any story",
                "refundTransaction outside any story")),
        Arguments.of(
            ScenarioPreset.REFUND_FULL_SUCCESS, List.of("refundTransaction outside any story")),
        Arguments.of(
            ScenarioPreset.CARD_ADD_SUCCESS, List.of("createPaymentProcess outside any story")),
        Arguments.of(
            ScenarioPreset.CARD_ADD_CALLBACK_DELAYED,
            List.of(
                "createPaymentProcess outside any story",
                "a delivery reporting "
                    + GrowCallbacks.EVENT_COMPLETED
                    + ", "
                    + ScenarioGrammar.DELIVERY_DELAYED)),
        Arguments.of(
            ScenarioPreset.WEBHOOK_DELAYED,
            List.of("every delivery, " + ScenarioGrammar.DELIVERY_DELAYED)),
        Arguments.of(
            ScenarioPreset.WEBHOOK_DUPLICATE,
            List.of("every delivery, " + ScenarioGrammar.DELIVERY_DUPLICATED)),
        Arguments.of(
            ScenarioPreset.CARD_ADD_FAILED, List.of("createPaymentProcess outside any story")),
        Arguments.of(
            ScenarioPreset.PSP_5XX_THEN_SUCCESS,
            List.of(
                aStepOfTheGatewayFailureStory(
                    ScenarioStory.INITIAL_STATE, GrowPresets.ANSWERED_WITHOUT_THE_CAPTURE),
                aStepOfTheGatewayFailureStory(
                    GrowPresets.ANSWERED_WITHOUT_THE_CAPTURE, NO_FURTHER_STATE))));
  }

  @ParameterizedTest(name = "{0} is reported as one grow cannot express")
  @MethodSource("thePresetsGrowCannotExpress")
  void every_preset_grow_cannot_express_says_what_is_missing_rather_than_expanding(
      ScenarioPreset preset, String missing) {
    PresetExpansion expansion = provider.expand(presetRequest(preset));

    assertThat(expansion).isInstanceOf(PresetExpansion.NotExpressible.class);
    assertThat(((PresetExpansion.NotExpressible) expansion).reason()).contains(missing);
  }

  private static List<Arguments> thePresetsGrowCannotExpress() {
    return List.of(
        Arguments.of(
            ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS, "wall-clock duration"),
        Arguments.of(ScenarioPreset.REFUND_PARTIAL_THEN_FULL, "refund allowance"),
        Arguments.of(ScenarioPreset.WEBHOOK_OUT_OF_ORDER, "no second callback"),
        Arguments.of(ScenarioPreset.WEBHOOK_INVALID_SIGNATURE, "defines no such header"),
        Arguments.of(ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_SUCCESS, "no processing status"),
        Arguments.of(ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_FAILED, "no processing status"));
  }

  @Test
  void every_published_preset_is_either_expanded_or_reported_as_one_grow_cannot_express() {
    List<ScenarioPreset> expanded = new ArrayList<>();
    List<ScenarioPreset> unexpressed = new ArrayList<>();
    for (ScenarioPreset preset : ScenarioPreset.values()) {
      switch (provider.expand(presetRequest(preset))) {
        case PresetExpansion.Rules rules -> {
          expanded.add(preset);
          assertThat(rules.rules())
              .isNotEmpty()
              .allSatisfy(
                  rule -> {
                    if (rule.action() instanceof ScenarioAction.OnTheDelivery) {
                      assertThat(rule.match().operation()).isNull();
                    } else {
                      assertThat(provider.vocabulary().operationNames())
                          .contains(rule.match().operation());
                    }
                  });
        }
        case PresetExpansion.NotExpressible missing -> {
          unexpressed.add(preset);
          assertThat(missing.reason()).isNotBlank();
        }
      }
    }

    assertThat(expanded).hasSize(11).doesNotContainAnyElementsOf(unexpressed);
    assertThat(unexpressed).hasSize(6);
    assertThat(ScenarioPreset.values()).hasSize(17);
  }

  @ParameterizedTest(name = "a rule naming {0} may name {1} beside it")
  @CsvSource({
    "createPaymentProcess, match.merchantId",
    "createPaymentProcess, match.amount",
    "createTransactionWithToken, match.merchantId",
    "createTransactionWithToken, match.paymentMethodId",
    "createTransactionWithToken, match.amount",
    "approveTransaction, match.merchantId",
    "approveTransaction, match.providerObjectId",
    "settleSuspendedTransaction, match.merchantId",
    "settleSuspendedTransaction, match.providerObjectId",
    "settleSuspendedTransaction, match.amount",
    "refundTransaction, match.merchantId",
    "refundTransaction, match.providerObjectId",
    "refundTransaction, match.amount",
    "getTransactionInfo, match.merchantId",
    "getTransactionInfo, match.providerObjectId",
    "getPaymentProcessInfo, match.merchantId",
    "getPaymentProcessInfo, match.providerObjectId"
  })
  void accepts_a_rule_naming_an_operation_beside_a_predicate_that_operation_carries(
      String operation, String predicate) {
    assertThat(provider.vocabulary().refusals(naming(operation, predicate))).isEmpty();
  }

  @ParameterizedTest(name = "a rule naming {0} beside {1} can never fire and is refused")
  @CsvSource({
    "createPaymentProcess, match.providerObjectId",
    "createPaymentProcess, match.paymentMethodId",
    "createTransactionWithToken, match.providerObjectId",
    "approveTransaction, match.paymentMethodId",
    "approveTransaction, match.amount",
    "settleSuspendedTransaction, match.paymentMethodId",
    "refundTransaction, match.paymentMethodId",
    "getTransactionInfo, match.paymentMethodId",
    "getTransactionInfo, match.amount",
    "getPaymentProcessInfo, match.paymentMethodId",
    "getPaymentProcessInfo, match.amount"
  })
  void refuses_a_rule_naming_an_operation_beside_a_predicate_that_operation_never_carries(
      String operation, String predicate) {
    List<ScenarioRefusal> refused = provider.vocabulary().refusals(naming(operation, predicate));

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly(predicate);
    assertThat(refused.get(0).reason()).contains(operation, predicate, "can never fire");
  }

  @Test
  void an_operation_grow_does_not_expose_is_refused_once_rather_than_once_per_predicate() {
    List<ScenarioRefusal> refused =
        provider
            .vocabulary()
            .refusals(
                new NewScenarioRule(
                    GrowLightApi.GROW,
                    RUN,
                    0,
                    new ScenarioMatch(
                        null,
                        null,
                        "9000001",
                        null,
                        "pm-1",
                        Amount.of("120.50"),
                        null,
                        "cancelBitTransaction",
                        null,
                        Map.of()),
                    new ScenarioAction.FixedSuccess(),
                    null));

    assertThat(refused).extracting(ScenarioRefusal::property).containsExactly("match.operation");
  }

  @Test
  void a_preset_narrowed_by_an_object_id_expands_into_a_rule_grow_itself_refuses() {
    PresetExpansion.Rules expanded =
        (PresetExpansion.Rules)
            provider.expand(
                new PresetRequest(
                    ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                    RUN,
                    0,
                    new ScenarioMatch(
                        null, null, "9000001", null, null, null, null, null, null, Map.of())));

    assertThat(expanded.rules().get(0).match().operation()).isEqualTo("createPaymentProcess");
    assertThat(provider.vocabulary().refusals(expanded.rules().get(0)))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.providerObjectId");
    assertThat(provider.vocabulary().refusals(expanded.rules().get(1))).isEmpty();
  }

  private static NewScenarioRule naming(String operation, String predicate) {
    return new NewScenarioRule(
        GrowLightApi.GROW,
        RUN,
        0,
        new ScenarioMatch(
            valueFor("match.merchantId", predicate, "merchant-1"),
            valueFor(MatchFact.ORDER_ID.property(), predicate, "order-1"),
            valueFor(MatchFact.PROVIDER_OBJECT_ID.property(), predicate, "9000001"),
            null,
            valueFor(MatchFact.PAYMENT_METHOD_ID.property(), predicate, "pm-1"),
            MatchFact.AMOUNT.property().equals(predicate) ? Amount.of("120.50") : null,
            null,
            operation,
            null,
            Map.of()),
        new ScenarioAction.FixedSuccess(),
        null);
  }

  private static @Nullable String valueFor(String predicate, String named, String value) {
    return predicate.equals(named) ? value : null;
  }

  @Test
  void an_expansion_carries_the_narrowing_the_caller_asked_the_preset_to_be_scoped_to() {
    PresetExpansion.Rules expanded =
        (PresetExpansion.Rules)
            provider.expand(
                new PresetRequest(
                    ScenarioPreset.CANCEL_HOLD_SUCCESS,
                    RUN,
                    100,
                    new ScenarioMatch(
                        "merchant-1",
                        null,
                        "9000001",
                        null,
                        null,
                        Amount.of("120.50"),
                        null,
                        null,
                        null,
                        Map.of())));

    assertThat(expanded.rules())
        .hasSize(2)
        .allSatisfy(
            rule -> {
              assertThat(rule.priority()).isEqualTo(100);
              assertThat(rule.testRunId()).isEqualTo(RUN);
              assertThat(rule.match().merchantId()).isEqualTo("merchant-1");
              assertThat(rule.match().providerObjectId()).isEqualTo("9000001");
              assertThat(rule.match().amount()).isEqualTo(Amount.of("120.50"));
            });
  }

  @Test
  void a_preset_refuses_a_narrowing_that_names_the_operation_the_preset_itself_names() {
    ScenarioMatch namingAnOperation =
        ScenarioMatch.anyRequest().naming("settleSuspendedTransaction");

    assertThatThrownBy(
            () -> new PresetRequest(ScenarioPreset.CANCEL_HOLD_SUCCESS, RUN, 0, namingAnOperation))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("match.operation");
  }

  @Test
  void an_expansion_into_no_rule_is_refused_rather_than_reported_as_a_preset_that_did_something() {
    assertThatThrownBy(() -> new PresetExpansion.Rules(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("did nothing");
    assertThatThrownBy(() -> new PresetExpansion.NotExpressible(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("what is missing");
  }

  private PresetExpansion.Rules expand(ScenarioPreset preset) {
    return (PresetExpansion.Rules) provider.expand(presetRequest(preset));
  }

  private static PresetRequest presetRequest(ScenarioPreset preset) {
    return new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest());
  }

  private static NewScenarioRule namingOperation(String operation) {
    return new NewScenarioRule(
        GrowLightApi.GROW,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.TerminalFailure("813"),
        null);
  }

  @Test
  void a_harness_lists_the_transactions_of_its_test_run_newest_first_and_none_of_another_runs() {
    transactions.insertIfAbsent(aTransaction(RUN, "100001"));
    transactions.insertIfAbsent(aTransaction(RUN, "100002"));
    transactions.insertIfAbsent(aTransaction(OTHER_RUN, "100003"));

    ProviderObjectPage page = provider.objects(aListOf(RUN, null, 10));

    assertThat(idsOn(page)).containsExactly("100002", "100001");
  }

  @Test
  void a_hosted_page_and_the_transaction_a_payer_left_behind_are_named_apart_in_one_list() {
    processes.insertIfAbsent(process(RUN, "100003"));
    transactions.insertIfAbsent(aTransaction(RUN, "100001"));

    ProviderObjectPage page = provider.objects(aListOf(RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(object -> object.kind().asThisProviderNamesIt())
        .containsExactly("a payment a payer finished", "a hosted page that holds money");
  }

  @Test
  void a_page_carries_on_after_the_object_the_previous_page_ended_on() {
    transactions.insertIfAbsent(aTransaction(RUN, "100001"));
    transactions.insertIfAbsent(aTransaction(RUN, "100002"));

    ProviderObjectPage first = provider.objects(aListOf(RUN, null, 1));
    ProviderObjectPage second = provider.objects(aListOf(RUN, "100002", 1));

    assertThat(idsOn(first)).containsExactly("100002");
    assertThat(idsOn(second)).containsExactly("100001");
    assertThat(idsOn(provider.objects(aListOf(RUN, "100001", 1)))).isEmpty();
  }

  @Test
  void a_cursor_naming_no_transaction_of_this_run_is_refused_rather_than_read_as_a_first_page() {
    transactions.insertIfAbsent(aTransaction(RUN, "100001"));
    transactions.insertIfAbsent(aTransaction(OTHER_RUN, "100003"));

    assertThat(provider.objects(aListOf(RUN, "100003", 10)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
    assertThat(provider.objects(aListOf(RUN, "no-such-id", 10)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  @Test
  void a_test_run_that_holds_no_transaction_lists_nothing_rather_than_refusing() {
    assertThat(idsOn(provider.objects(aListOf(OTHER_RUN, null, 10)))).isEmpty();
  }

  @Test
  void
      every_listed_transaction_carries_what_created_it_and_is_opened_by_the_read_that_takes_one_id() {
    transactions.insertIfAbsent(aTransaction(RUN, "100001"));

    ProviderObjectState listed =
        ((ProviderObjectPage.Listed) provider.objects(aListOf(RUN, null, 10))).objects().get(0);

    assertThat(listed.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(provider.state(RUN, listed.providerObjectId())).isPresent();
  }

  private static ProviderObjectQuery aListOf(
      TestRunId testRunId, @Nullable String after, int limit) {
    return new ProviderObjectQuery(testRunId, after, limit);
  }

  private static List<String> idsOn(ProviderObjectPage page) {
    List<String> ids = new ArrayList<>();
    for (ProviderObjectState object : ((ProviderObjectPage.Listed) page).objects()) {
      ids.add(object.providerObjectId());
    }
    return ids;
  }

  private static GrowTransaction aTransaction(TestRunId testRunId, String transactionId) {
    return new GrowTransaction(
        testRunId,
        GrowTransactionKind.PAYMENT,
        transactionId,
        "token-" + transactionId,
        "merchant-1",
        "900" + transactionId,
        null,
        "89474280",
        "07/09/26",
        "A payment",
        "Israel Israeli",
        "0505503144",
        null,
        GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD,
        Amount.of("120.50"),
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        Instant.parse("2026-09-07T12:00:00Z"),
        Instant.parse("2026-09-07T12:00:00Z"));
  }

  @Test
  void a_secret_grow_was_sent_is_redacted_before_it_could_reach_an_audit_row() {
    ProviderAccountFields fields = GrowAccount.ACCOUNT_FIELDS;
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
  void the_spi_completes_a_hosted_process_into_the_hold_its_chargetype_asked_for() {
    accounts.withTarget(RUN, new WebhookTarget("merchant-1", "https://merchant.example/hook"));
    String processId = aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED);

    HostedCompletionOutcome answered =
        provider.complete(
            new HostedCompletionRequest(
                RUN, processId, PayerOutcome.PAID, "sandbox-4580", ObjectOrigin.CONTROL_API));

    assertThat(answered).isInstanceOf(HostedCompletionOutcome.Completed.class);
    HostedCompletionOutcome.Completed completed = (HostedCompletionOutcome.Completed) answered;
    assertThat(completed.providerObjectId()).isEqualTo(processId);
    assertThat(completed.outcome()).isEqualTo(PayerOutcome.PAID);
    assertThat(completed.createdObjectId()).isNotBlank();
    assertThat(completed.webhookJobIds()).hasSize(1);
    assertThat(completed.webhookJobIds().get(0)).startsWith("whjob_");
    assertThat(
            transactions
                .findByTransactionId(RUN, completed.createdObjectId())
                .orElseThrow()
                .holdsAnAuthorization())
        .isTrue();
  }

  @Test
  void the_spi_reports_an_abandoned_completion_as_causing_no_callback_at_all() {
    String processId = aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED);

    HostedCompletionOutcome answered =
        provider.complete(
            new HostedCompletionRequest(
                RUN, processId, PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API));

    HostedCompletionOutcome.Completed completed = (HostedCompletionOutcome.Completed) answered;
    assertThat(completed.webhookJobIds()).isEmpty();
    assertThat(completed.createdObjectId()).isNull();
    assertThat(completed.outcome()).isEqualTo(PayerOutcome.ABANDONED);
  }

  @Test
  void
      the_spi_refuses_a_completion_no_callback_target_addresses_and_leaves_the_process_unreached() {
    String processId = aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED);

    HostedCompletionOutcome answered =
        provider.complete(
            new HostedCompletionRequest(
                RUN, processId, PayerOutcome.PAID, "sandbox-4580", ObjectOrigin.CONTROL_API));

    assertThat(answered).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) answered).inItsOwnWords())
        .contains(GrowCallbacks.NOTIFY_URL_IS_NOT_A_CONFIGURED_TARGET)
        .contains(CompleteHostedProcess.STILL_COMPLETABLE);
    assertThat(processes.findByProcessId(RUN, processId).orElseThrow().outcome()).isNull();
  }

  @Test
  void the_spi_refuses_to_complete_a_process_that_already_reached_an_outcome() {
    String processId = aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED);
    provider.complete(
        new HostedCompletionRequest(
            RUN, processId, PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API));

    HostedCompletionOutcome again =
        provider.complete(
            new HostedCompletionRequest(
                RUN, processId, PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API));

    assertThat(again)
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(
                CompleteHostedProcess.ALREADY_COMPLETED));
  }

  @Test
  void the_spi_offers_a_payer_step_on_a_process_no_payer_has_finished_yet() {
    String processId = aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED);

    HostedCompletionOffer declared = provider.hostedCompletion(RUN, processId);

    assertThat(declared).isInstanceOf(HostedCompletionOffer.Offered.class);
    assertThat(((HostedCompletionOffer.Offered) declared).cardsAPayerMayName())
        .extracting(PayerCard::alias)
        .containsExactly("sandbox-4580", "sandbox-0000", "sandbox-1121");
  }

  @Test
  void the_spi_offers_a_payer_the_card_this_test_run_seeded_beside_the_ones_grow_publishes() {
    new PaymentMethodFixtureAdmission(
            seededMethods,
            scenarioRules,
            new SimulatorDoubles.Runs(RUN),
            new RecordedUnitsOfWork(),
            RUN)
        .seed(provider, aGrowCardFor(RUN.value(), "visa-success"), ObjectOrigin.CONTROL_API);
    String processId = aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED);

    HostedCompletionOffer declared = provider.hostedCompletion(RUN, processId);

    assertThat(((HostedCompletionOffer.Offered) declared).cardsAPayerMayName())
        .extracting(PayerCard::alias)
        .contains("visa-success", "sandbox-4580");
  }

  @Test
  void the_spi_offers_no_payer_step_on_an_object_that_is_no_process_of_this_run() {
    assertThat(provider.hostedCompletion(RUN, "000000"))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                GrowLightApi.GROW, GrowHostedCompletionOffer.ONLY_A_PROCESS_HAS_A_PAYER_STEP));
  }

  @Test
  void the_spi_offers_no_second_payer_step_on_a_process_a_payer_has_already_finished() {
    String processId = aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED);
    provider.complete(
        new HostedCompletionRequest(
            RUN, processId, PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API));

    assertThat(provider.hostedCompletion(RUN, processId))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                GrowLightApi.GROW, CompleteHostedProcess.ALREADY_COMPLETED));
  }

  @Test
  void the_spi_reports_a_process_this_run_does_not_hold_as_no_such_object() {
    assertThat(
            provider.complete(
                new HostedCompletionRequest(
                    RUN, "000000", PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API)))
        .isInstanceOf(HostedCompletionOutcome.NoSuchObject.class);
  }

  private String aStoredProcess(String chargeType) {
    String processId = ids.digits(6);
    assertThat(
            processes.insertIfAbsent(
                new GrowPaymentProcess(
                    RUN,
                    processId,
                    "token-" + processId,
                    "merchant-1",
                    chargeType,
                    "120.50",
                    "A hold",
                    true,
                    "https://merchant.example/thanks",
                    "https://merchant.example/cancel",
                    "https://merchant.example/hook",
                    "Israel Israeli",
                    "0505503144",
                    "payer@merchant.example",
                    Map.of("cField1", "session-abc"),
                    "link-" + processId,
                    GrowNativeStatus.NOT_PAID_CODE,
                    GrowNativeStatus.NOT_PAID,
                    NormalizedStatus.NOT_INITIALIZED,
                    null,
                    ObjectOrigin.PROVIDER_API,
                    Instant.parse("2026-09-07T12:00:00Z"),
                    Instant.parse("2026-09-07T12:00:00Z"))))
        .isTrue();
    return processId;
  }

  @Test
  void answers_a_pending_outcome_with_the_words_its_own_contract_uses_for_having_none() {
    assertThat(
            GrowOperations.VOCABULARY.refusals(
                new NewScenarioRule(
                    GrowLightApi.GROW,
                    TestRunId.of("grow-run"),
                    0,
                    ScenarioMatch.anyRequest().naming(GrowOperations.GET_TRANSACTION_INFO),
                    new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
                    null)))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.type");
              assertThat(refusal.reason()).isEqualTo(GrowOperations.NO_PROCESSING_OUTCOME);
            });
    assertThat(GrowOperations.VOCABULARY.theOperationsThatReportAProcessingState()).isEmpty();
  }

  @Test
  void refuses_to_answer_a_call_with_a_pending_outcome_this_provider_publishes_no_state_for() {
    ScenarioDecision.Pend pending = new ScenarioDecision.Pend("scn_1", Duration.ofSeconds(5), null);

    Assertions.assertThatThrownBy(
            () ->
                GrowOperationResult.decidedBy(
                    pending,
                    () ->
                        GrowOperationResult.failed(
                            RefusalSubject.noObjectWasNamed("in a test"), GrowJson.object()),
                    code ->
                        GrowOperationResult.failed(
                            RefusalSubject.noObjectWasNamed("in a test"), GrowJson.object())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("answers with none");
  }

  @Test
  void the_pairing_grow_publishes_is_its_light_api_path_and_the_two_body_fields_that_name_it() {
    ProviderPairing pairing = provider.pairing();

    assertThat(pairing.provider()).isEqualTo(GrowLightApi.GROW);
    assertThat(pairing.apiBasePath()).isEqualTo("/api/light/server/1.0");
    assertThat(pairing.settings())
        .extracting(PairingSetting::name)
        .containsExactly(
            "GROW_BASE_URL",
            "GROW_CONTENT_TYPE",
            "GROW_USER_ID",
            "GROW_PAGE_CODE",
            "SIMULATOR_ACCOUNT_ID",
            "SIMULATOR_TEST_RUN_HEADER",
            "SIMULATOR_TEST_RUN_ID");
    assertThat(pairing.accountFieldsItReads()).containsExactly("userId", "pageCode");
    assertThat(pairing.howARequestNamesItsAccount())
        .contains("carries no Authorization header at all")
        .contains("form-encoded body itself");
  }

  @Test
  void
      the_compatibility_checks_grow_publishes_name_seven_operations_and_withhold_only_the_signature() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), ids);

    assertThat(checks)
        .extracting(ContractCheck::operation, check -> check.provenance().sample())
        .containsExactly(
            tuple(
                GrowCompatibilityChecks.CREATE_PAYMENT_PROCESS,
                "grow/create-payment-process-approved"),
            tuple(GrowCompatibilityChecks.CREATE_PAYMENT_PROCESS, "grow/token-charge-declined"),
            tuple(
                GrowCompatibilityChecks.CREATE_PAYMENT_PROCESS,
                "grow/create-payment-process-unknown-business"),
            tuple(GrowCompatibilityChecks.THE_CALLBACK_CARRIES, "grow/unsigned-callback"),
            tuple(
                GrowCompatibilityChecks.THE_CALLBACK_IS_RESENT,
                "grow/unacknowledged-update-resends"),
            tuple(GrowCompatibilityChecks.THE_CONTROL_API_CREATES, "simulator/scenario-created"),
            tuple(GrowCompatibilityChecks.THE_TRAIL_RECORDS, "simulator/audit-record"));
    assertThat(checks)
        .filteredOn(check -> check.applicability() instanceof Applicability.DoesNotApply)
        .singleElement()
        .satisfies(
            withheld -> {
              assertThat(withheld.operation())
                  .isEqualTo(GrowCompatibilityChecks.THE_CALLBACK_CARRIES);
              assertThat(withheld.applicability())
                  .isEqualTo(
                      new Applicability.DoesNotApply(
                          "Grow signs no server-to-server callback, so a signature has nothing to"
                              + " be checked against"));
              assertThat(withheld.expectations()).isEmpty();
            });
    assertThat(checks)
        .filteredOn(check -> check.applicability() instanceof Applicability.Applies)
        .hasSize(6)
        .allSatisfy(applying -> assertThat(applying.expectations()).isNotEmpty());
  }

  @Test
  void the_retry_path_grow_publishes_is_the_callback_resend_and_the_check_reads_both_its_counts() {
    List<ContractCheck> checks = provider.compatibilityChecks(new CheckJson(), ids);

    assertThat(checks)
        .filteredOn(
            check -> GrowCompatibilityChecks.THE_CALLBACK_IS_RESENT.equals(check.operation()))
        .singleElement()
        .satisfies(
            resends -> {
              assertThat(resends.applicability()).isInstanceOf(Applicability.Applies.class);
              assertThat(resends.expectations())
                  .extracting(
                      ContractExpectation::field,
                      expectation -> expectation.published().value(),
                      expectation -> expectation.published().provenance().sample())
                  .containsExactly(
                      tuple("delivery.attemptCap", "6", "grow/server-update-retry-schedule"),
                      tuple(
                          "delivery.resendsAfterTheFirstAttempt",
                          "5",
                          "grow/unacknowledged-update-resends"));
            });
  }

  @Test
  void grow_publishes_no_persona_and_names_the_published_reason_for_every_intent() {
    ProviderPersonas published = provider.personas(ids);

    assertThat(published.provider()).isEqualTo(GrowLightApi.GROW);
    assertThat(published.personas()).isEmpty();
    assertThat(published.intentsItPublishesNoneFor()).hasSize(PersonaIntent.values().length);
    for (PersonaIntent intent : PersonaIntent.values()) {
      assertThat(published.intentsItPublishesNoneFor().get(intent))
          .contains("provider grow mints a cardToken only where a hosted process saved a card")
          .contains("err.id 140");
    }
  }

  @Test
  void a_name_seeded_against_grow_is_refused_by_saying_it_publishes_no_persona_at_all() {
    assertThat(provider.personas(ids).theRefusalOfAnUnknownPersona())
        .isEqualTo("provider grow publishes no persona at all, so no name could be seeded for it");
  }

  @Test
  void declares_that_its_contract_has_a_page_a_payer_finishes_and_names_the_object_that_waits() {
    assertThat(provider.hostedPages())
        .isEqualTo(
            new HostedPageContract.ObjectsAPayerFinishes(
                List.of(new HostedPageContract.AnObjectItKeepsWaiting("a payment process", true))));
  }

  @Test
  void publishes_the_facts_of_the_one_object_its_declaration_promises_a_payer_finishes() {
    assertThat(
            TheObjectsADeclarationPromises.of(
                provider, RUN, List.of(aStoredProcess(GrowFields.CHARGE_TYPE_SUSPENDED))))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @ParameterizedTest(name = "reads {0} back as one line per rule")
  @EnumSource(ScenarioPreset.class)
  void every_ready_made_flow_reads_back_as_one_line_per_rule(ScenarioPreset preset) {
    APresetReadsBackInWords.assertItDoes(
        preset, provider.expand(new PresetRequest(preset, RUN, 0, ScenarioMatch.anyRequest())));
  }
}
