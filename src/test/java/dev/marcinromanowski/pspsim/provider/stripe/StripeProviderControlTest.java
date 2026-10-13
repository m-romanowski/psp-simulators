package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.smoke.Applicability;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredCard;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheObjectsADeclarationPromises;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.ui.APresetReadsBackInWords;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StripeProviderControlTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";

  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
  private final StripeDoubles.Runs testRuns = new StripeDoubles.Runs(DEFAULT_RUN);
  private final StripeDoubles.Audit audit = new StripeDoubles.Audit();
  private final StripeDoubles.Intents intents = new StripeDoubles.Intents();
  private final StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
  private final StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
  private final StripeDoubles.Customers customers = new StripeDoubles.Customers();
  private final StripeDoubles.PaymentMethods paymentMethods = new StripeDoubles.PaymentMethods();
  private final StripeDoubles.SetupIntents setupIntents =
      new StripeDoubles.SetupIntents(paymentMethods);
  private final StripeDoubles.Keys keys = new StripeDoubles.Keys();
  private final StripeDoubles.EphemeralKeys ephemeralKeys = new StripeDoubles.EphemeralKeys();
  private final StripeDoubles.CountingIds ids = new StripeDoubles.CountingIds();
  private final WebhookDispatcher deliveries = StripeDoubles.noDeliveries(accounts);

  @Test
  void reports_a_held_payment_intent_with_the_ledger_its_capture_will_be_written_on() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());

    ProviderObjectState state = provider(NOW).state(E2E_RUN, FIRST_INTENT).orElseThrow();

    assertThat(state.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(state.nativeStatus()).isEqualTo("requires_capture");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(state.amount()).isNotNull();
    assertThat(state.currency()).isEqualTo("pln");
    assertThat(state.authorizationOpen()).isTrue();
    assertThat(state.capturesAccepted()).isTrue();
    assertThat(state.refundsAccepted()).isFalse();
    assertThat(state.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(state.relatedObjects())
        .containsEntry("account", ACCOUNT)
        .containsEntry("paymentMethod", "pm_card_visa")
        .containsEntry("customer", "cus_sim_1");
    assertThat(state.derivedBalances())
        .hasValueSatisfying(
            balances -> {
              assertThat(balances.capturedAmount().value()).isEqualTo("0");
              assertThat(balances.capturableRemainder().value()).isEqualTo("1099");
              assertThat(balances.refundableRemainder().value()).isEqualTo("0");
            });
  }

  @Test
  void reports_a_hold_whose_deadline_has_passed_as_cancelled_without_a_call_having_moved_it() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());

    ProviderObjectState state =
        provider(NOW.plus(Duration.ofDays(4))).state(E2E_RUN, FIRST_INTENT).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo("canceled");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(state.authorizationOpen()).isFalse();
    assertThat(state.capturesAccepted()).isFalse();
    assertThat(state.derivedBalances())
        .map(DerivedBalances::capturableRemainder)
        .hasValueSatisfying(remainder -> assertThat(remainder.value()).isEqualTo("0"));
  }

  @Test
  void
      the_instant_before_its_deadline_a_hold_accepts_a_capture_and_the_instant_after_it_does_not() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());
    Instant deadline = NOW.plus(Duration.ofDays(3));

    ProviderObjectState before =
        provider(deadline.minusMillis(1)).state(E2E_RUN, FIRST_INTENT).orElseThrow();
    ProviderObjectState after = provider(deadline).state(E2E_RUN, FIRST_INTENT).orElseThrow();

    assertThat(before.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(before.capturesAccepted()).isTrue();
    assertThat(before.derivedBalances())
        .map(DerivedBalances::capturableRemainder)
        .hasValueSatisfying(remainder -> assertThat(remainder.value()).isEqualTo("1099"));
    assertThat(after.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(after.capturesAccepted()).isFalse();
    assertThat(after.derivedBalances())
        .map(DerivedBalances::capturableRemainder)
        .hasValueSatisfying(remainder -> assertThat(remainder.value()).isEqualTo("0"));
  }

  @Test
  void a_hold_long_past_its_deadline_reports_what_it_reported_at_the_deadline_itself() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());

    ProviderObjectState atTheDeadline =
        provider(NOW.plus(Duration.ofDays(3))).state(E2E_RUN, FIRST_INTENT).orElseThrow();
    ProviderObjectState farPastIt =
        provider(NOW.plus(Duration.ofDays(30))).state(E2E_RUN, FIRST_INTENT).orElseThrow();

    assertThat(farPastIt.nativeStatus()).isEqualTo(atTheDeadline.nativeStatus());
    assertThat(farPastIt.normalizedStatus()).isEqualTo(atTheDeadline.normalizedStatus());
    assertThat(farPastIt.authorizationOpen()).isEqualTo(atTheDeadline.authorizationOpen());
    assertThat(farPastIt.capturesAccepted()).isEqualTo(atTheDeadline.capturesAccepted());
    assertThat(farPastIt.derivedBalances()).isEqualTo(atTheDeadline.derivedBalances());
  }

  @Test
  void reports_nothing_for_an_object_of_another_test_run() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());

    Optional<ProviderObjectState> state = provider(NOW).state(DEFAULT_RUN, FIRST_INTENT);

    assertThat(state).isEmpty();
  }

  @Test
  void clears_the_intents_the_keys_the_rules_and_the_trail_of_one_test_run() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW)
        .createPaymentIntent(
            new StripeApiCall(
                "POST", "/v1/payment_intents", authenticatedWithAKey(), fieldsOfAHold()));

    ResetOutcome outcome = provider(NOW).reset(E2E_RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(1);
    assertThat(outcome.clearedAuditRecords()).isEqualTo(2);
    assertThat(intents.all()).isEmpty();
    assertThat(keys.size()).isZero();
  }

  @Test
  void clears_every_test_run_when_the_whole_simulator_is_reset() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());

    ResetOutcome outcome = provider(NOW).reset(E2E_RUN, ResetScope.ALL);

    assertThat(outcome.clearedObjects()).isEqualTo(1);
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void counts_the_rules_it_cleared_beside_the_payment_intents_of_the_test_run() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CAPTURE),
            new ScenarioAction.FixedSuccess(),
            null));

    ResetOutcome outcome = provider(NOW).reset(E2E_RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(2);
  }

  @Test
  void leaves_the_payment_intents_of_another_test_run_where_they_were() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    accounts.with(StripeDoubles.account(DEFAULT_RUN, ACCOUNT, "sk_test_other", "P3D"));
    api(NOW).createPaymentIntent(hold());
    api(NOW).createPaymentIntent(heldBy("sk_test_other"));

    provider(NOW).reset(E2E_RUN, ResetScope.TEST_RUN);

    assertThat(intents.all())
        .singleElement()
        .satisfies(intent -> assertThat(intent.testRunId()).isEqualTo(DEFAULT_RUN));
  }

  @Test
  void clears_the_payment_intents_of_every_test_run_when_the_whole_simulator_is_reset() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    accounts.with(StripeDoubles.account(DEFAULT_RUN, ACCOUNT, "sk_test_other", "P3D"));
    api(NOW).createPaymentIntent(hold());
    api(NOW).createPaymentIntent(heldBy("sk_test_other"));

    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CAPTURE),
            new ScenarioAction.FixedSuccess(),
            null));

    ResetOutcome outcome = provider(NOW).reset(E2E_RUN, ResetScope.ALL);

    assertThat(outcome.clearedObjects()).isEqualTo(3);
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void resetting_one_test_run_leaves_no_ephemeral_key_of_that_run_behind_to_authenticate_with() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    accounts.with(StripeDoubles.account(DEFAULT_RUN, ACCOUNT, "sk_test_other", "P3D"));
    aCustomerWithAKeyMintedFor(SECRET_KEY);
    aCustomerWithAKeyMintedFor("sk_test_other");

    provider(NOW).reset(E2E_RUN, ResetScope.TEST_RUN);

    assertThat(ephemeralKeys.all())
        .singleElement()
        .satisfies(key -> assertThat(key.testRunId()).isEqualTo(DEFAULT_RUN));
  }

  @Test
  void resetting_the_whole_simulator_leaves_no_ephemeral_key_of_any_test_run_behind() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    accounts.with(StripeDoubles.account(DEFAULT_RUN, ACCOUNT, "sk_test_other", "P3D"));
    aCustomerWithAKeyMintedFor(SECRET_KEY);
    aCustomerWithAKeyMintedFor("sk_test_other");

    provider(NOW).reset(E2E_RUN, ResetScope.ALL);

    assertThat(ephemeralKeys.all()).isEmpty();
  }

  private void aCustomerWithAKeyMintedFor(String secretKey) {
    StripeApiResponse customer =
        api(NOW)
            .createCustomer(
                new StripeApiCall("POST", "/v1/customers", bearerOf(secretKey), Map.of()));
    api(NOW)
        .createEphemeralKey(
            new StripeApiCall(
                "POST",
                "/v1/ephemeral_keys",
                bearerOf(secretKey),
                Map.of("customer", List.of(Objects.requireNonNull(customer.providerObjectId())))));
  }

  private static Map<String, List<String>> bearerOf(String secretKey) {
    return Map.of("Authorization", List.of("Bearer " + secretKey));
  }

  @Test
  void expands_every_rule_of_a_preset_into_an_action_this_simulator_performs() {
    PresetExpansion expansion = expand(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .hasSize(3)
        .allSatisfy(
            rule -> assertThat(rule.action()).isInstanceOf(ScenarioAction.FixedSuccess.class));
  }

  @Test
  void expands_the_pre_authorization_preset_into_the_three_calls_that_make_the_hold_and_take_it() {
    PresetExpansion expansion = expand(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly(
            StripeOperations.CREATE, StripeOperations.CONFIRM, StripeOperations.CAPTURE);
  }

  @Test
  void expands_the_five_hundred_then_success_preset_into_a_story_of_two_captures() {
    PresetExpansion expansion = expand(ScenarioPreset.PSP_5XX_THEN_SUCCESS);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .allSatisfy(
            rule -> assertThat(rule.match().operation()).isEqualTo(StripeOperations.CAPTURE))
        .extracting(rule -> rule.story().whenState())
        .containsExactly("Started", StripePresets.ANSWERED_WITHOUT_THE_CAPTURE);
  }

  @Test
  void expands_the_full_refund_preset_into_the_one_refund_call_this_contract_publishes() {
    PresetExpansion expansion = expand(ScenarioPreset.REFUND_FULL_SUCCESS);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .singleElement()
        .satisfies(rule -> assertThat(rule.match().operation()).isEqualTo(StripeOperations.REFUND));
  }

  @Test
  void expands_the_partial_then_full_refund_preset_into_a_story_of_two_refunds() {
    PresetExpansion expansion = expand(ScenarioPreset.REFUND_PARTIAL_THEN_FULL);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .allSatisfy(rule -> assertThat(rule.match().operation()).isEqualTo(StripeOperations.REFUND))
        .extracting(rule -> rule.story().whenState())
        .containsExactly("Started", StripePresets.REFUNDED_IN_PART);
  }

  @Test
  void expands_the_processing_then_success_preset_onto_both_calls_that_can_report_one() {
    PresetExpansion expansion = expand(ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_SUCCESS);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly(StripeOperations.CREATE, StripeOperations.CONFIRM);
    assertThat(((PresetExpansion.Rules) expansion).rules())
        .allSatisfy(
            rule ->
                assertThat(rule.action())
                    .isEqualTo(
                        new ScenarioAction.ProcessingThenTerminal(
                            ScenarioPreset.THE_OUTCOME_SETTLES_AFTER, null)));
  }

  @Test
  void expands_the_processing_then_failed_preset_onto_the_decline_it_comes_to_rest_with() {
    PresetExpansion expansion = expand(ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_FAILED);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .extracting(rule -> rule.match().operation())
        .containsExactly(StripeOperations.CREATE, StripeOperations.CONFIRM);
    assertThat(((PresetExpansion.Rules) expansion).rules())
        .allSatisfy(
            rule ->
                assertThat(rule.action())
                    .isEqualTo(
                        new ScenarioAction.ProcessingThenTerminal(
                            ScenarioPreset.THE_OUTCOME_SETTLES_AFTER,
                            StripePresets.ASYNCHRONOUS_PAYMENT_DECLINE.scenarioCode())));
  }

  @Test
  void the_two_processing_presets_differ_only_by_the_code_the_scheduled_outcome_rests_with() {
    ScenarioAction succeeding =
        ((PresetExpansion.Rules) expand(ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_SUCCESS))
            .rules()
            .get(0)
            .action();
    ScenarioAction failing =
        ((PresetExpansion.Rules) expand(ScenarioPreset.STATUS_LOOKUP_PROCESSING_THEN_FAILED))
            .rules()
            .get(0)
            .action();

    assertThat(succeeding.theProviderCodeItComesToRestWith()).isNull();
    assertThat(failing.theProviderCodeItComesToRestWith())
        .isEqualTo(StripePresets.ASYNCHRONOUS_PAYMENT_DECLINE.scenarioCode());
    assertThat(failing).isNotEqualTo(succeeding);
  }

  @ParameterizedTest
  @EnumSource(
      value = ScenarioPreset.class,
      names = {
        "CARD_ADD_CALLBACK_DELAYED",
        "PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS",
        "WEBHOOK_INVALID_SIGNATURE"
      })
  void refuses_a_preset_this_build_has_no_call_to_express_it_with(ScenarioPreset preset) {
    PresetExpansion expansion = expand(preset);

    assertThat(expansion).isInstanceOf(PresetExpansion.NotExpressible.class);
    assertThat(((PresetExpansion.NotExpressible) expansion).reason()).isNotBlank();
  }

  @Test
  void expands_the_card_added_preset_into_a_rule_on_the_setup_confirmation_that_attaches_it() {
    PresetExpansion expansion = expand(ScenarioPreset.CARD_ADD_SUCCESS);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.match().operation()).isEqualTo(StripeOperations.CONFIRM_SETUP_INTENT);
              assertThat(rule.action()).isInstanceOf(ScenarioAction.FixedSuccess.class);
            });
  }

  @Test
  void expands_the_card_not_added_preset_into_a_decline_on_the_setup_confirmation() {
    PresetExpansion expansion = expand(ScenarioPreset.CARD_ADD_FAILED);

    assertThat(((PresetExpansion.Rules) expansion).rules())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.match().operation()).isEqualTo(StripeOperations.CONFIRM_SETUP_INTENT);
              assertThat(((ScenarioAction.TerminalFailure) rule.action()).providerCode())
                  .isEqualTo(StripePresets.CARD_SETUP_DECLINE.scenarioCode());
            });
  }

  @Test
  void the_out_of_order_preset_holds_back_the_first_of_the_two_events_one_intent_reaches() {
    List<NewScenarioRule> expanded =
        ((PresetExpansion.Rules) expand(ScenarioPreset.WEBHOOK_OUT_OF_ORDER)).rules();

    assertThat(expanded).hasSize(1);
    assertThat(expanded.get(0).match().eventType())
        .isEqualTo("payment_intent.amount_capturable_updated");
    assertThat(expanded.get(0).action())
        .isEqualTo(new ScenarioAction.DeliveryDelayed(ScenarioPreset.DELIVERY_HELD_BACK_BY));
  }

  @ParameterizedTest(name = "the preset {0} shapes every delivery of the test run")
  @EnumSource(
      value = ScenarioPreset.class,
      names = {"WEBHOOK_DELAYED", "WEBHOOK_DUPLICATE"})
  void a_callback_preset_shapes_every_delivery_and_narrows_on_no_request_fact(
      ScenarioPreset preset) {
    List<NewScenarioRule> expanded = ((PresetExpansion.Rules) expand(preset)).rules();

    assertThat(expanded).hasSize(1);
    assertThat(expanded.get(0).action()).isInstanceOf(ScenarioAction.OnTheDelivery.class);
    assertThat(expanded.get(0).match().operation()).isNull();
    assertThat(expanded.get(0).match().eventType()).isNull();
  }

  @Test
  void a_harness_seeds_a_card_with_the_payment_method_id_a_confirm_will_send() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seededMethods, scenarioRules, testRuns, new RecordedUnitsOfWork(), DEFAULT_RUN);

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider(NOW),
            new DeclaredPaymentMethodFixture(
                E2E_RUN.value(),
                ACCOUNT,
                "cus_sim_123",
                "card",
                "visa-success",
                new DeclaredCard("visa", "4242", 12, 2030, "credit"),
                Map.of(
                    ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                    "pm_card_visa",
                    ProviderPaymentMethodFields.CUSTOMER_ID,
                    "cus_sim_123"),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Seeded.class,
            fixture ->
                assertThat(fixture.fixture().providerIds())
                    .containsEntry("paymentMethodId", "pm_card_visa")
                    .containsEntry("customerId", "cus_sim_123"));

    PaymentMethodFixture fixture =
        ((PaymentMethodFixtureAdmission.Verdict.Seeded) verdict).fixture();
    assertThat(admission.forget(provider(NOW), E2E_RUN.value(), fixture.fixtureId()))
        .isInstanceOf(PaymentMethodFixtureAdmission.Removal.Removed.class);
    assertThat(seededMethods.of(StripeApi.STRIPE, E2E_RUN)).isEmpty();
  }

  @Test
  void a_harness_seeds_a_second_card_for_one_customer_because_a_customer_id_names_an_owner() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seededMethods, scenarioRules, testRuns, new RecordedUnitsOfWork(), DEFAULT_RUN);

    admission.seed(
        provider(NOW), aCardOfOneCustomer("first", "pm_first"), ObjectOrigin.CONTROL_API);
    PaymentMethodFixtureAdmission.Verdict second =
        admission.seed(
            provider(NOW), aCardOfOneCustomer("second", "pm_second"), ObjectOrigin.CONTROL_API);

    assertThat(second).isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Seeded.class);
    assertThat(seededMethods.of(StripeApi.STRIPE, E2E_RUN)).hasSize(2);
    assertThat(paymentMethods.of(E2E_RUN, ACCOUNT, "cus_one_owner", null))
        .extracting(StripePaymentMethod::paymentMethodId)
        .containsExactlyInAnyOrder("pm_first", "pm_second");
  }

  @Test
  void a_harness_that_reuses_one_payment_method_id_is_refused_because_it_names_the_card_itself() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seededMethods, scenarioRules, testRuns, new RecordedUnitsOfWork(), DEFAULT_RUN);

    admission.seed(
        provider(NOW), aCardOfOneCustomer("first", "pm_shared"), ObjectOrigin.CONTROL_API);
    PaymentMethodFixtureAdmission.Verdict second =
        admission.seed(
            provider(NOW), aCardOfOneCustomer("second", "pm_shared"), ObjectOrigin.CONTROL_API);

    assertThat(second)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.message())
                    .contains("providerIds.paymentMethodId")
                    .contains("already claimed"));
  }

  private static DeclaredPaymentMethodFixture aCardOfOneCustomer(
      String alias, String paymentMethodId) {
    return new DeclaredPaymentMethodFixture(
        E2E_RUN.value(),
        ACCOUNT,
        "owner-1",
        "card",
        alias,
        new DeclaredCard("visa", "4242", 12, 2030, "credit"),
        Map.of(
            ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
            paymentMethodId,
            ProviderPaymentMethodFields.CUSTOMER_ID,
            "cus_one_owner"),
        null);
  }

  @Test
  void a_token_this_build_models_no_object_for_is_refused_in_stripes_own_words() {
    ProviderPaymentMethodFields declared = provider(NOW).paymentMethodFields();

    assertThat(declared.keys()).containsExactly("paymentMethodId", "customerId");
    assertThat(declared.theRefusalOf(ProviderPaymentMethodFields.TOKEN))
        .contains("no Token object");
  }

  @Test
  void resetting_a_test_run_counts_the_payment_methods_it_seeded_among_what_it_cleared() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seededMethods, scenarioRules, testRuns, new RecordedUnitsOfWork(), DEFAULT_RUN);
    admission.seed(
        provider(NOW), aStripeCard("visa-success", "pm_reset_1"), ObjectOrigin.CONTROL_API);

    assertThat(provider(NOW).reset(E2E_RUN, ResetScope.TEST_RUN).clearedObjects()).isEqualTo(2);
    assertThat(seededMethods.of(StripeApi.STRIPE, E2E_RUN)).isEmpty();
    assertThat(paymentMethods.all()).isEmpty();
  }

  @Test
  void resetting_every_test_run_counts_the_payment_methods_of_all_of_them_among_what_it_cleared() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    PaymentMethodFixtureAdmission admission =
        new PaymentMethodFixtureAdmission(
            seededMethods, scenarioRules, testRuns, new RecordedUnitsOfWork(), DEFAULT_RUN);
    admission.seed(
        provider(NOW), aStripeCard("visa-success", "pm_reset_2"), ObjectOrigin.CONTROL_API);

    assertThat(provider(NOW).reset(E2E_RUN, ResetScope.ALL).clearedObjects()).isEqualTo(2);
    assertThat(paymentMethods.all()).isEmpty();
  }

  private static DeclaredPaymentMethodFixture aStripeCard(String alias, String paymentMethodId) {
    return new DeclaredPaymentMethodFixture(
        E2E_RUN.value(),
        ACCOUNT,
        "cus_sim_123",
        "card",
        alias,
        new DeclaredCard("visa", "4242", 12, 2030, "credit"),
        Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, paymentMethodId),
        null);
  }

  @Test
  void names_itself_stripe() {
    assertThat(provider(NOW).id()).isEqualTo(StripeApi.STRIPE);
    assertThat(provider(NOW).vocabulary()).isEqualTo(StripeOperations.VOCABULARY);
  }

  @Test
  void the_wire_namespaces_and_the_answer_off_them_are_the_ones_this_module_declares() {
    assertThat(provider(NOW).wireRoutes()).isEqualTo(StripeWireFaults.ROUTES);
    assertThat(provider(NOW).wireRoutes().covers(StripeApiController.PAYMENT_INTENTS))
        .as("a namespace this module forgot to claim leaves a client on the wrong answer")
        .isTrue();
    assertThat(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    provider(NOW)
                        .theAnswerOffItsRoutes(
                            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
                            StripeApiController.PAYMENT_INTENTS))
                .httpStatus())
        .isEqualTo(StripeErrors.NOT_FOUND);
    assertThat(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    provider(NOW)
                        .theAnswerOffItsRoutes(
                            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER,
                            StripeApiController.PAYMENT_INTENTS))
                .httpStatus())
        .isEqualTo(StripeErrors.NOT_FOUND);
    assertThat(
            ((ProviderWireAnswer.NothingThisContractPublishes)
                    provider(NOW)
                        .theAnswerOffItsRoutes(
                            WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED,
                            StripeApiController.PAYMENT_INTENTS))
                .whatThisContractPublishesInstead())
        .isEqualTo(StripeWireFaults.WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
  }

  private PresetExpansion expand(ScenarioPreset preset) {
    return provider(NOW).expand(new PresetRequest(preset, E2E_RUN, 0, ScenarioMatch.anyRequest()));
  }

  private SimulatorProvider provider(Instant now) {
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
        seededMethods,
        scenarioRules,
        StripeDoubles.heldResponses(),
        StripeDoubles.clockAt(now),
        deliveries,
        new StripeWebhooks(
            intents,
            refunds,
            customers,
            paymentMethods,
            setupIntents,
            accounts,
            deliveries,
            StripeDoubles.clockAt(now),
            ids),
        new CapturePaymentIntent(intents, scenarioEngine()),
        new CancelPaymentIntent(intents, scenarioEngine()),
        new CreateRefund(intents, refunds, charges, ids, scenarioEngine()),
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            scenarioEngine(),
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine()));
  }

  private ScenarioEngine scenarioEngine() {
    return new ScenarioEngine(scenarioRules);
  }

  private StripeApi api(Instant now) {
    return new StripeApi(
        accounts,
        keys,
        ephemeralKeys,
        testRuns,
        audit,
        StripeDoubles.clockAt(now),
        ids,
        DEFAULT_RUN,
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new ConfirmPaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CapturePaymentIntent(intents, new ScenarioEngine(scenarioRules)),
        new CancelPaymentIntent(intents, new ScenarioEngine(scenarioRules)),
        new RetrievePaymentIntent(intents),
        new RetrieveCharge(charges, intents),
        new CreateRefund(intents, refunds, charges, ids, new ScenarioEngine(scenarioRules)),
        customers,
        paymentMethods,
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(
            setupIntents, paymentMethods, customers, StripeDoubles.noScenarios()));
  }

  private static StripeApiCall heldBy(String secretKey) {
    return new StripeApiCall(
        "POST",
        "/v1/payment_intents",
        Map.of("Authorization", List.of("Bearer " + secretKey)),
        fieldsOfAHold());
  }

  private static StripeApiCall hold() {
    return new StripeApiCall(
        "POST",
        "/v1/payment_intents",
        Map.of("Authorization", List.of("Bearer " + SECRET_KEY)),
        fieldsOfAHold());
  }

  private static Map<String, List<String>> authenticatedWithAKey() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer " + SECRET_KEY));
    headers.put("Idempotency-Key", List.of("a-key-of-one-request"));
    return headers;
  }

  private static Map<String, List<String>> fieldsOfAHold() {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("amount", List.of("1099"));
    fields.put("currency", List.of("pln"));
    fields.put("capture_method", List.of("manual"));
    fields.put("payment_method", List.of("pm_card_visa"));
    fields.put("customer", List.of("cus_sim_1"));
    fields.put("confirm", List.of("true"));
    return fields;
  }

  @Test
  void a_harness_lists_the_intents_and_the_refunds_of_its_test_run_newest_first() {
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000001"), null);
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000002"), null);
    refunds.refund(
        anIntent("pi_sim_0000000000000001"), null, aRefund("re_sim_0000000000000001"), NOW);

    ProviderObjectPage page = provider(NOW).objects(new ProviderObjectQuery(E2E_RUN, null, 10));

    assertThat(idsOn(page))
        .containsExactly(
            "re_sim_0000000000000001", "pi_sim_0000000000000002", "pi_sim_0000000000000001");
    assertThat(idsOn(provider(NOW).objects(new ProviderObjectQuery(DEFAULT_RUN, null, 10))))
        .isEmpty();
  }

  @Test
  void a_refund_is_named_apart_from_the_intent_it_returned_the_money_from_in_the_same_list() {
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000001"), null);
    refunds.refund(
        anIntent("pi_sim_0000000000000001"), null, aRefund("re_sim_0000000000000001"), NOW);

    ProviderObjectPage page = provider(NOW).objects(new ProviderObjectQuery(E2E_RUN, null, 10));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(object -> object.kind().asThisProviderNamesIt())
        .containsExactly("a refund", "a card charged in one call");
  }

  @Test
  void a_stripe_page_carries_on_after_the_object_the_previous_page_ended_on() {
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000001"), null);
    refunds.refund(
        anIntent("pi_sim_0000000000000001"), null, aRefund("re_sim_0000000000000001"), NOW);

    ProviderObjectPage first = provider(NOW).objects(new ProviderObjectQuery(E2E_RUN, null, 1));
    ProviderObjectPage second =
        provider(NOW).objects(new ProviderObjectQuery(E2E_RUN, "re_sim_0000000000000001", 10));

    assertThat(idsOn(first)).containsExactly("re_sim_0000000000000001");
    assertThat(idsOn(second)).containsExactly("pi_sim_0000000000000001");
    assertThat(provider(NOW).objects(new ProviderObjectQuery(E2E_RUN, "pi_never_made", 10)))
        .isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  @Test
  void one_answer_for_many_identifiers_holds_what_asking_one_at_a_time_holds() {
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000001"), null);
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000002"), null);
    refunds.refund(
        anIntent("pi_sim_0000000000000001"), null, aRefund("re_sim_0000000000000001"), NOW);
    List<String> asked =
        List.of(
            "re_sim_0000000000000001",
            "pi_sim_0000000000000001",
            ACCOUNT,
            "pi_never_made",
            "pi_sim_0000000000000002",
            "re_sim_0000000000000001");

    List<ProviderObjectState> batched = provider(NOW).states(E2E_RUN, asked);

    assertThat(batched)
        .as(
            "a screen labels every identifier an object names from this answer, so one answer for"
                + " many identifiers that held anything other than what asking one at a time"
                + " holds would change what a tester reads beside those names. %s",
            TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(
                provider(NOW), E2E_RUN, asked));
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider(NOW), E2E_RUN, asked));
    assertThat(batched)
        .extracting(ProviderObjectState::providerObjectId)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(
            "re_sim_0000000000000001", "pi_sim_0000000000000001", "pi_sim_0000000000000002");
    assertThat(provider(NOW).states(E2E_RUN, List.of("pi_never_made"))).isEmpty();
  }

  @Test
  void a_refund_names_the_account_it_was_taken_on_before_the_intent_it_returned_money_from() {
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000001"), null);
    refunds.refund(
        anIntent("pi_sim_0000000000000001"), null, aRefund("re_sim_0000000000000001"), NOW);

    ProviderObjectState refund =
        provider(NOW).state(E2E_RUN, "re_sim_0000000000000001").orElseThrow();

    assertThat(refund.relatedObjects().keySet())
        .as(
            "a screen, a read and a control answer print the objects a refund names in the order"
                + " this provider named them, and an unordered copy prints them in a different"
                + " order in every JVM that runs this simulator")
        .containsExactly("account", "paymentIntent");
    assertThat(refund.relatedObjects())
        .containsExactly(
            Map.entry("account", ACCOUNT), Map.entry("paymentIntent", "pi_sim_0000000000000001"));
  }

  @Test
  void every_listed_stripe_object_is_opened_by_the_read_that_takes_one_id() {
    intents.insertIfAbsent(anIntent("pi_sim_0000000000000001"), null);
    refunds.refund(
        anIntent("pi_sim_0000000000000001"), null, aRefund("re_sim_0000000000000001"), NOW);

    for (String id : idsOn(provider(NOW).objects(new ProviderObjectQuery(E2E_RUN, null, 10)))) {
      assertThat(provider(NOW).state(E2E_RUN, id)).isPresent();
    }
  }

  private static List<String> idsOn(ProviderObjectPage page) {
    List<String> ids = new ArrayList<>();
    for (ProviderObjectState object : ((ProviderObjectPage.Listed) page).objects()) {
      ids.add(object.providerObjectId());
    }
    return ids;
  }

  private static StripePaymentIntent anIntent(String intentId) {
    return new StripePaymentIntent(
        E2E_RUN,
        intentId,
        ACCOUNT,
        Amount.of("1000"),
        "pln",
        StripeCaptureMethod.AUTOMATIC,
        StripeIntentStatus.SUCCEEDED,
        null,
        null,
        null,
        intentId + "_secret",
        null,
        null,
        null,
        NOW.plus(Duration.ofDays(7)),
        null,
        null,
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static StripeRefund aRefund(String refundId) {
    return new StripeRefund(
        E2E_RUN,
        refundId,
        "pi_sim_0000000000000001",
        null,
        ACCOUNT,
        Amount.of("400"),
        "pln",
        StripeRefundReason.REQUESTED_BY_CUSTOMER,
        StripeRefundStatus.SUCCEEDED,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  @Test
  void stripe_completes_no_hosted_page_and_says_what_its_contract_has_instead() {
    HostedCompletionOutcome answered =
        provider(NOW)
            .complete(
                new HostedCompletionRequest(
                    E2E_RUN, "pi_1", PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API));

    assertThat(answered)
        .isEqualTo(new HostedCompletionOutcome.NotOffered(StripeProvider.NO_PAGE_TO_COMPLETE));
    assertThat(StripeProvider.NO_PAGE_TO_COMPLETE).contains(StripeApiController.PAYMENT_INTENTS);
  }

  @Test
  void stripe_offers_a_screen_no_payer_step_and_says_it_in_the_words_its_completion_uses() {
    HostedCompletionOffer declared = provider(NOW).hostedCompletion(E2E_RUN, "pi_1");

    assertThat(declared)
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                StripeApi.STRIPE, StripeProvider.NO_PAGE_TO_COMPLETE));
  }

  @Test
  void the_pairing_stripe_publishes_is_its_v1_path_and_the_secret_key_that_is_its_account() {
    ProviderPairing pairing = provider(NOW).pairing();

    assertThat(pairing.provider()).isEqualTo(StripeApi.STRIPE);
    assertThat(pairing.apiBasePath()).isEqualTo("/v1");
    assertThat(pairing.settings())
        .extracting(PairingSetting::name)
        .containsExactly(
            "STRIPE_API_BASE",
            "STRIPE_SECRET_KEY",
            "STRIPE_WEBHOOK_SECRET",
            "STRIPE_CONTENT_TYPE",
            "SIMULATOR_ACCOUNT_ID",
            "SIMULATOR_TEST_RUN_HEADER",
            "SIMULATOR_TEST_RUN_ID");
    assertThat(pairing.accountFieldsItReads()).containsExactly("secretKey", "webhookSecret");
    assertThat(pairing.howARequestNamesItsAccount())
        .contains("Authorization: Bearer <secretKey>")
        .contains("HTTP Basic username with an empty password")
        .contains("publishes no merchant id, terminal id or key pair");
  }

  @Test
  void the_compatibility_checks_stripe_publishes_name_seven_operations_and_withhold_none() {
    List<ContractCheck> checks = provider(NOW).compatibilityChecks(new CheckJson(), ids);

    assertThat(checks)
        .extracting(ContractCheck::operation, check -> check.provenance().sample())
        .containsExactly(
            tuple(
                StripeCompatibilityChecks.CREATE_PAYMENT_INTENT,
                "stripe/payment-intent-requires-capture"),
            tuple(
                StripeCompatibilityChecks.CREATE_PAYMENT_INTENT,
                "stripe/card-declined-insufficient-funds"),
            tuple(StripeCompatibilityChecks.CREATE_PAYMENT_INTENT, "stripe/idempotent-replay"),
            tuple(StripeCompatibilityChecks.THE_WEBHOOK_CARRIES, "stripe/webhook-signature"),
            tuple(StripeCompatibilityChecks.CREATE_PAYMENT_INTENT, "stripe/no-api-key"),
            tuple(StripeCompatibilityChecks.THE_CONTROL_API_CREATES, "simulator/scenario-created"),
            tuple(StripeCompatibilityChecks.THE_TRAIL_RECORDS, "simulator/audit-record"));
    assertThat(checks)
        .filteredOn(check -> check.applicability() instanceof Applicability.DoesNotApply)
        .isEmpty();
    assertThat(checks).allSatisfy(applying -> assertThat(applying.expectations()).isNotEmpty());
    assertThat(theSigningCheckOf(checks).expectations())
        .extracting(ContractExpectation::field, expectation -> expectation.published().value())
        .containsExactly(tuple("timestamp.prefix", "t="), tuple("signature.scheme", "v1"));
  }

  private static ContractCheck theSigningCheckOf(List<ContractCheck> checks) {
    return checks.stream()
        .filter(check -> StripeCompatibilityChecks.THE_WEBHOOK_CARRIES.equals(check.operation()))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void the_personas_stripe_publishes_mint_a_customer_and_carry_the_one_published_payment_method() {
    ProviderPersonas published = provider(NOW).personas(ids);

    assertThat(published.provider()).isEqualTo(StripeApi.STRIPE);
    assertThat(published.theNamesItPublishes())
        .containsExactly(
            "stripe-customer-with-visa-success",
            "stripe-customer-with-visa-that-is-declined",
            "stripe-customer-whose-capture-answer-is-lost");
    assertThat(published.intentsItPublishesNoneFor()).isEmpty();
    for (DeclaredPersona persona : published.personas()) {
      PersonaFixture instrument = persona.fixtures().get(0);
      assertThat(instrument.theProviderNativeKeysItCarries())
          .containsExactly("paymentMethodId", "customerId");
      Map<String, String> minted = instrument.mint();
      assertThat(minted.get("paymentMethodId")).isEqualTo("pm_card_visa");
      assertThat(minted.get("customerId")).matches("cus_sim_[0-9a-f]{16}");
      assertThat(instrument.card()).isNotNull();
      assertThat(instrument.card().last4()).isEqualTo("4242");
    }
  }

  @Test
  void a_stripe_persona_says_it_mints_no_payment_method_id_because_this_build_mints_none() {
    for (DeclaredPersona persona : provider(NOW).personas(ids).personas()) {
      assertThat(persona.fixtures().get(0).howThoseAreMinted())
          .contains("this simulator mints no PaymentMethod id anywhere");
    }
  }

  @Test
  void every_persona_stripe_publishes_writes_a_preset_this_module_can_expand() {
    for (DeclaredPersona persona : provider(NOW).personas(ids).personas()) {
      for (String named : persona.thePresetNamesItWrites()) {
        ScenarioPreset preset = ScenarioPreset.of(named).orElseThrow();
        assertThat(
                provider(NOW)
                    .expand(
                        new PresetRequest(
                            preset, TestRunId.of("run-a"), 0, ScenarioMatch.anyRequest())))
            .isInstanceOf(PresetExpansion.Rules.class);
      }
    }
  }

  @Test
  void declares_in_its_own_words_that_its_contract_has_no_page_a_payer_finishes() {
    assertThat(provider(NOW).hostedPages())
        .isInstanceOfSatisfying(
            HostedPageContract.NoPageAPayerFinishes.class,
            declared ->
                assertThat(declared.whatThisProviderHasInstead())
                    .contains("provider stripe hosts a payer-facing page as a Checkout Session")
                    .contains("this build serves no route of that family"));
  }

  @Test
  void
      is_reported_as_keeping_no_object_a_payer_finishes_rather_than_as_keeping_an_unpublished_one() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    api(NOW).createPaymentIntent(hold());

    assertThat(TheObjectsADeclarationPromises.of(provider(NOW), E2E_RUN, List.of(FIRST_INTENT)))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @ParameterizedTest(name = "reads {0} back as one line per rule")
  @EnumSource(ScenarioPreset.class)
  void every_ready_made_flow_reads_back_as_one_line_per_rule(ScenarioPreset preset) {
    APresetReadsBackInWords.assertItDoes(preset, expand(preset));
  }
}
