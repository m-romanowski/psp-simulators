package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.capturePath;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.manualHoldOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripeObjectActionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String THREE_DAYS = "P3D";

  private static final String CREATE_PATH = "/v1/payment_intents";
  private static final String REFUNDS_PATH = "/v1/refunds";

  private static final String FIRST_INTENT = "pi_sim_0000000000000001";
  private static final String SECOND_INTENT = "pi_sim_0000000000000003";
  private static final String NO_INTENT_OF_THIS_RUN = "pi_sim_0000000000000099";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
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

  @Test
  void a_tester_reading_the_screen_is_offered_every_action_stripe_publishes_on_a_payment() {
    ObjectActions offered = provider().objectActions();

    assertThat(offered.provider()).isEqualTo(StripeApi.STRIPE);
    assertThat(offered.whatItHasInstead()).isEmpty();
    for (ObjectAction action : ObjectAction.values()) {
      assertThat(offered.offers(action)).isTrue();
    }
  }

  @Test
  void a_tester_who_captures_a_hold_from_a_control_surface_moves_the_money_the_api_moves() {
    StripeApi api = twoHeldPaymentsOf("1000");

    ObjectActionOutcome captured = asked(ObjectAction.CAPTURE, FIRST_INTENT, "1000");
    api.capturePaymentIntent(
        post(capturePath(SECOND_INTENT), Map.of("amount_to_capture", "1000")), SECOND_INTENT);

    assertThat(captured).isEqualTo(new ObjectActionOutcome.Applied(FIRST_INTENT, null));
    assertThat(balancesOf(FIRST_INTENT)).isEqualTo(balancesOf(SECOND_INTENT));
    assertThat(balancesOf(FIRST_INTENT).capturedAmount().value()).isEqualTo("1000");
    assertThat(stateOf(FIRST_INTENT).nativeStatus())
        .isEqualTo(stateOf(SECOND_INTENT).nativeStatus());
    assertThat(stateOf(FIRST_INTENT).normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_tester_who_captures_part_of_a_hold_from_a_control_surface_closes_what_it_leaves() {
    twoHeldPaymentsOf("1000");

    ObjectActionOutcome captured = asked(ObjectAction.CAPTURE, FIRST_INTENT, "700");

    assertThat(captured).isEqualTo(new ObjectActionOutcome.Applied(FIRST_INTENT, null));
    ProviderObjectState state = stateOf(FIRST_INTENT);
    assertThat(state.nativeStatus()).isEqualTo("succeeded");
    assertThat(state.authorizationOpen()).isFalse();
    assertThat(balancesOf(FIRST_INTENT).capturedAmount().value()).isEqualTo("700");
    assertThat(balancesOf(FIRST_INTENT).capturableRemainder().value()).isEqualTo("0");
  }

  @Test
  void a_refund_a_tester_makes_from_a_control_surface_reads_back_as_made_from_the_screen() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    ObjectActionOutcome refunded = asked(ObjectAction.REFUND, FIRST_INTENT, "400");
    api.createRefund(post(REFUNDS_PATH, Map.of("payment_intent", FIRST_INTENT, "amount", "400")));

    ObjectActionOutcome.Applied applied = (ObjectActionOutcome.Applied) refunded;
    String created = applied.createdObjectId();
    assertThat(applied.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(created).isNotNull().startsWith(CreateRefund.ID_PREFIX).isNotEqualTo(FIRST_INTENT);
    assertThat(stateOf(created).origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(refunds.all()).hasSize(2);
    assertThat(refunds.all().get(1).origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(balancesOf(FIRST_INTENT).refundedAmount().value()).isEqualTo("800");
  }

  @Test
  void a_capture_stripe_refuses_reaches_the_tester_in_stripes_words_and_carries_no_client_secret() {
    twoHeldPaymentsOf("1000");
    ScenarioRule declining = declining(StripeOperations.CAPTURE, StripeDecline.INSUFFICIENT_FUNDS);

    ObjectActionOutcome refused = asked(ObjectAction.CAPTURE, FIRST_INTENT, "1000");

    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                "card_declined insufficient_funds The card has insufficient funds to complete the"
                    + " purchase.",
                declining.scenarioId()));
    assertThat(((ObjectActionOutcome.RefusedByTheProvider) refused).inItsOwnWords())
        .doesNotContain(theIntent(FIRST_INTENT).clientSecret());
    assertThat(stateOf(FIRST_INTENT).nativeStatus()).isEqualTo("requires_capture");
  }

  @Test
  void a_tester_who_cancels_an_open_hold_from_a_control_surface_names_no_reason_of_its_own() {
    twoHeldPaymentsOf("1000");

    ObjectActionOutcome cancelled = asked(ObjectAction.CANCEL, FIRST_INTENT, null);

    assertThat(cancelled).isEqualTo(new ObjectActionOutcome.Applied(FIRST_INTENT, null));
    assertThat(stateOf(FIRST_INTENT).nativeStatus()).isEqualTo("canceled");
    assertThat(theIntent(FIRST_INTENT).cancellationReason()).isNull();
  }

  @Test
  void a_cancel_of_a_payment_stripe_has_already_completed_is_refused_in_stripes_words() {
    aPaymentCapturedInFullOf("1000");

    ObjectActionOutcome refused = asked(ObjectAction.CANCEL, FIRST_INTENT, null);

    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                "payment_intent_unexpected_state A PaymentIntent with a status of succeeded cannot"
                    + " be canceled."));
  }

  @Test
  void a_payment_no_test_run_of_this_simulator_holds_is_answered_as_no_such_object() {
    twoHeldPaymentsOf("1000");

    ObjectActionOutcome answered = asked(ObjectAction.CAPTURE, NO_INTENT_OF_THIS_RUN, "1000");

    assertThat(answered).isEqualTo(new ObjectActionOutcome.NoSuchObject());
    assertThat(balancesOf(FIRST_INTENT).capturedAmount().value()).isEqualTo("0");
  }

  @Test
  void a_payment_whose_account_this_run_no_longer_configures_is_refused_in_stripes_words() {
    twoHeldPaymentsOf("1000");
    accounts.forget(E2E_RUN);

    ObjectActionOutcome refused = asked(ObjectAction.CAPTURE, FIRST_INTENT, "1000");

    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                "No valid API key provided. This test run configures no stripe account under the"
                    + " id "
                    + ACCOUNT
                    + ", which is the account this PaymentIntent was created on, so no secret key"
                    + " this run holds reaches it."));
  }

  @Test
  void a_capture_a_scenario_answers_instead_of_leaves_the_hold_where_the_tester_found_it() {
    twoHeldPaymentsOf("1000");
    ScenarioRule answering =
        scenarioRules.add(
            new NewScenarioRule(
                StripeApi.STRIPE,
                E2E_RUN,
                0,
                ScenarioMatch.anyRequest().naming(StripeOperations.CAPTURE),
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                null));

    ObjectActionOutcome answered = asked(ObjectAction.CAPTURE, FIRST_INTENT, "1000");

    assertThat(answered)
        .isEqualTo(new ObjectActionOutcome.AnsweredByAScenarioInstead(answering.scenarioId()));
    assertThat(stateOf(FIRST_INTENT).nativeStatus()).isEqualTo("requires_capture");
    assertThat(balancesOf(FIRST_INTENT).capturedAmount().value()).isEqualTo("0");
  }

  private ObjectActionOutcome asked(
      ObjectAction action, String providerObjectId, @Nullable String amount) {
    return provider()
        .act(ObjectActionRequest.of(E2E_RUN, providerObjectId, action, amount, ObjectOrigin.UI));
  }

  private ScenarioRule declining(String operation, StripeDecline decline) {
    return scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TerminalFailure(decline.scenarioCode()),
            null));
  }

  private StripeApi aPaymentCapturedInFullOf(String amount) {
    StripeApi api = twoHeldPaymentsOf(amount);
    api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);
    return api;
  }

  private StripeApi twoHeldPaymentsOf(String amount) {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
    StripeApi api = api();
    api.createPaymentIntent(post(CREATE_PATH, manualHoldOf(amount)));
    api.createPaymentIntent(post(CREATE_PATH, manualHoldOf(amount)));
    return api;
  }

  private DerivedBalances balancesOf(String providerObjectId) {
    return stateOf(providerObjectId).derivedBalances().orElseThrow();
  }

  private ProviderObjectState stateOf(String providerObjectId) {
    return provider().state(E2E_RUN, providerObjectId).orElseThrow();
  }

  private StripePaymentIntent theIntent(String intentId) {
    return intents.find(E2E_RUN, intentId).orElseThrow();
  }

  private StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall(
        "POST",
        path,
        StripePaymentIntentTest.bearer(SECRET_KEY),
        StripePaymentIntentTest.fieldsOf(form));
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
        scenarioRules,
        StripeDoubles.heldResponses(),
        StripeDoubles.clockAt(NOW),
        StripeDoubles.noDeliveries(accounts),
        new StripeWebhooks(
            intents,
            refunds,
            customers,
            paymentMethods,
            setupIntents,
            accounts,
            StripeDoubles.noDeliveries(accounts),
            StripeDoubles.clockAt(NOW),
            ids),
        new CapturePaymentIntent(intents, scenarioEngine),
        new CancelPaymentIntent(intents, scenarioEngine),
        new CreateRefund(intents, refunds, charges, ids, scenarioEngine),
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            scenarioEngine,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine));
  }

  private StripeApi api() {
    return new StripeApi(
        accounts,
        keys,
        ephemeralKeys,
        testRuns,
        audit,
        StripeDoubles.clockAt(NOW),
        ids,
        DEFAULT_RUN,
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            scenarioEngine,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new ConfirmPaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            scenarioEngine,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CapturePaymentIntent(intents, scenarioEngine),
        new CancelPaymentIntent(intents, scenarioEngine),
        new RetrievePaymentIntent(intents),
        new RetrieveCharge(charges, intents),
        new CreateRefund(intents, refunds, charges, ids, scenarioEngine),
        customers,
        paymentMethods,
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(
            setupIntents, paymentMethods, customers, StripeDoubles.noScenarios()));
  }
}
