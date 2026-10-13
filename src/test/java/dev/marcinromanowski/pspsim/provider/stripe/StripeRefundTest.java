package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.capturePath;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.manualHoldOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeRefundTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String THREE_DAYS = "P3D";

  private static final String REFUNDS_PATH = "/v1/refunds";
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";

  private static final int FIRST_DRAW_A_REFUND_TAKES = 3;

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
  void answers_a_refund_with_the_object_the_vendor_publishes_for_one() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    StripeApiResponse refunded = refund(api, Map.of("amount", "400", "reason", "duplicate"));

    assertThat(refunded.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(refunded))
        .contains("\"object\":\"refund\"")
        .contains("\"amount\":400")
        .contains("\"currency\":\"pln\"")
        .contains("\"payment_intent\":\"" + FIRST_INTENT + "\"")
        .contains("\"charge\":\"" + theChargeOf(FIRST_INTENT) + "\"")
        .contains("\"reason\":\"duplicate\"")
        .contains("\"status\":\"succeeded\"");
    assertThat(refunded.providerObjectId()).startsWith(CreateRefund.ID_PREFIX);
  }

  @Test
  void returns_the_whole_remaining_refundable_amount_when_the_caller_names_none() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    refund(api, Map.of("amount", "400"));

    StripeApiResponse rest = refund(api, Map.of());

    assertThat(bodyOf(rest)).contains("\"amount\":600");
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void repeats_partial_refunds_until_the_captured_amount_is_exhausted() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    refund(api, Map.of("amount", "400"));
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
    refund(api, Map.of("amount", "600"));

    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(refunds.all()).hasSize(2);
  }

  @Test
  void refuses_a_further_refund_once_everything_captured_has_been_returned() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    refund(api, Map.of("amount", "1000"));

    StripeApiResponse refused = refund(api, Map.of("amount", "1"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"" + StripeErrors.CHARGE_ALREADY_REFUNDED + "\"")
        .contains(CreateRefund.ALREADY_REFUNDED_IN_FULL);
  }

  @Test
  void refuses_a_refund_above_what_is_left_to_return() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    refund(api, Map.of("amount", "400"));

    StripeApiResponse refused = refund(api, Map.of("amount", "601"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains(CreateRefund.MORE_THAN_IS_LEFT);
  }

  @Test
  void returns_what_a_non_final_capture_took_while_the_payment_still_awaits_capture() {
    StripeApi api = anAccountHolding("1000");
    api.capturePaymentIntent(
        post(
            capturePath(FIRST_INTENT),
            Map.of("amount_to_capture", "700", "final_capture", "false")),
        FIRST_INTENT);

    StripeApiResponse refunded = refund(api, Map.of("amount", "700"));

    assertThat(refunded.httpStatus()).isEqualTo(200);
    assertThat(theIntent().statusAt(NOW)).isEqualTo(StripeIntentStatus.REQUIRES_CAPTURE);
    assertThat(theIntent().capturableRemainderAt(NOW).toPlainString()).isEqualTo("300");
  }

  @Test
  void refuses_to_return_more_than_a_non_final_capture_actually_took() {
    StripeApi api = anAccountHolding("1000");
    api.capturePaymentIntent(
        post(
            capturePath(FIRST_INTENT),
            Map.of("amount_to_capture", "700", "final_capture", "false")),
        FIRST_INTENT);

    StripeApiResponse refused = refund(api, Map.of("amount", "701"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains(CreateRefund.MORE_THAN_IS_LEFT);
  }

  @Test
  void refuses_a_refund_of_a_payment_that_has_captured_nothing() {
    StripeApi api = anAccountHolding("1000");

    StripeApiResponse refused = refund(api, Map.of("amount", "100"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"" + StripeErrors.PAYMENT_INTENT_UNEXPECTED_STATE + "\"")
        .contains(CreateRefund.NOTHING_WAS_CAPTURED.strip());
  }

  @Test
  void refuses_a_charge_this_simulator_holds_no_charge_object_for() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    StripeApiResponse refused =
        api.createRefund(post(REFUNDS_PATH, Map.of("charge", "ch_sim_1", "amount", "100")));

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused)).contains("\"param\":\"charge\"");
  }

  @Test
  void refuses_a_refund_that_names_no_payment_at_all() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    StripeApiResponse refused = api.createRefund(post(REFUNDS_PATH, Map.of("amount", "100")));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"payment_intent\"");
  }

  @Test
  void refuses_a_refund_of_a_payment_no_account_of_this_test_run_holds() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    StripeApiResponse refused =
        api.createRefund(
            post(REFUNDS_PATH, Map.of("payment_intent", "pi_sim_no_such_intent", "amount", "100")));

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"" + StripeErrors.RESOURCE_MISSING + "\"")
        .contains("\"param\":\"payment_intent\"");
  }

  @Test
  void refuses_a_refund_amount_no_json_reader_of_the_answer_could_hold() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    StripeApiResponse refused =
        refund(
            api,
            Map.of(
                "amount", "1".repeat(StripeAmounts.LONGEST_MINOR_UNITS_A_JSON_READER_HOLDS + 1)));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"" + StripeErrors.AMOUNT_TOO_LARGE + "\"")
        .contains("\"param\":\"amount\"");
  }

  @Test
  void refuses_a_refund_amount_that_is_not_a_positive_integer_in_minor_units() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    StripeApiResponse refused = refund(api, Map.of("amount", "4.00"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"code\":\"" + StripeErrors.INVALID_CHARGE_AMOUNT + "\"");
  }

  @Test
  void names_every_refund_status_the_vendor_publishes_by_the_value_it_puts_on_the_wire() {
    assertThat(StripeRefundStatus.of("succeeded")).contains(StripeRefundStatus.SUCCEEDED);
    assertThat(StripeRefundStatus.of("requires_action"))
        .contains(StripeRefundStatus.REQUIRES_ACTION);
    assertThat(StripeRefundStatus.of("disputed")).isEmpty();
    assertThat(StripeRefundOutcome.stored("CANCELED").status())
        .isEqualTo(StripeRefundStatus.CANCELED);
    assertThat(StripeRefundStatus.theStatusesTheVendorPublishes())
        .isEqualTo("PENDING, REQUIRES_ACTION, SUCCEEDED, FAILED, CANCELED");
  }

  @Test
  void refuses_a_reason_only_the_vendor_itself_generates() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    StripeApiResponse refused =
        refund(api, Map.of("amount", "100", "reason", "expired_uncaptured_charge"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"reason\"");
  }

  @Test
  void names_the_refund_it_created_on_the_operation_that_created_it() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    String refundId = refund(api, Map.of("amount", "400")).providerObjectId();

    assertThat(theIntent().ledger().operations())
        .filteredOn(operation -> operation.kind() == OperationKind.REFUND)
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.createdObjectId()).isEqualTo(refundId);
              assertThat(operation.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
            });
  }

  @Test
  void reports_the_payment_and_the_refund_as_two_objects_with_states_of_their_own() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    String refundId = refund(api, Map.of("amount", "400")).providerObjectId();

    ProviderObjectState payment = stateOf(FIRST_INTENT);
    ProviderObjectState refund = stateOf(refundId);

    assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
    assertThat(payment.refundsAccepted()).isTrue();
    assertThat(refund.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(refund.nativeStatus()).isEqualTo("succeeded");
    assertThat(refund.relatedObjects()).containsEntry("paymentIntent", FIRST_INTENT);
  }

  @Test
  void keeps_the_money_on_the_payment_when_the_refund_never_took_it() {
    StripeApi api = aPaymentCapturedInFullOf("1000", StripeRefundStatus.FAILED);

    StripeApiResponse failed = refund(api, Map.of("amount", "400"));

    assertThat(bodyOf(failed)).contains("\"status\":\"failed\"");
    assertThat(theIntent().ledger().holds(OperationKind.REFUND)).isFalse();
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(stateOf(failed.providerObjectId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.FAILED);
  }

  @Test
  void holds_the_money_against_a_refund_that_has_not_settled_yet() {
    StripeApi api = aPaymentCapturedInFullOf("1000", StripeRefundStatus.PENDING);

    StripeApiResponse pending = refund(api, Map.of("amount", "400"));

    assertThat(bodyOf(pending)).contains("\"status\":\"pending\"");
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(theIntent().refundableRemainder().toPlainString()).isEqualTo("600");
    assertThat(stateOf(pending.providerObjectId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.PROCESSING);
  }

  @Test
  void refuses_a_second_refund_of_money_a_refund_that_has_not_settled_already_holds() {
    StripeApi api = aPaymentCapturedInFullOf("1000", StripeRefundStatus.PENDING);
    refund(api, Map.of("amount", "700"));

    StripeApiResponse refused = refund(api, Map.of("amount", "400"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains(CreateRefund.MORE_THAN_IS_LEFT);
  }

  @Test
  void answers_a_refund_awaiting_an_action_without_a_next_action_it_cannot_publish() {
    StripeApi api = aPaymentCapturedInFullOf("1000", StripeRefundStatus.REQUIRES_ACTION);

    StripeApiResponse awaiting = refund(api, Map.of("amount", "400"));

    assertThat(bodyOf(awaiting))
        .contains("\"status\":\"requires_action\"")
        .contains("\"next_action\":null");
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(stateOf(awaiting.providerObjectId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.PROCESSING);
  }

  @Test
  void answers_a_refund_a_scenario_declines_with_the_decline_and_returns_nothing() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    decliningTheRefund();

    StripeApiResponse declined = refund(api, Map.of("amount", "400"));

    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(refunds.all()).isEmpty();
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void draws_another_refund_id_when_the_one_it_drew_is_already_taken() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    refunds.withTheIdsAlreadyTaken(theIdAtDraw(FIRST_DRAW_A_REFUND_TAKES));

    StripeApiResponse refunded = refund(api, Map.of("amount", "400"));

    assertThat(refunded.httpStatus()).isEqualTo(200);
    assertThat(refunded.providerObjectId()).isEqualTo(theIdAtDraw(FIRST_DRAW_A_REFUND_TAKES + 1));
  }

  @Test
  void gives_up_rather_than_drawing_refund_ids_for_ever_when_every_one_it_draws_is_taken() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    String[] everyIdItWouldDraw = new String[CreateRefund.ID_ATTEMPTS];
    for (int attempt = 0; attempt < CreateRefund.ID_ATTEMPTS; attempt++) {
      everyIdItWouldDraw[attempt] = theIdAtDraw(FIRST_DRAW_A_REFUND_TAKES + attempt);
    }
    refunds.withTheIdsAlreadyTaken(everyIdItWouldDraw);

    Map<String, String> form = Map.of("amount", "400");

    assertThatThrownBy(() -> refund(api, form))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(CreateRefund.ID_ATTEMPTS));
    assertThat(refunds.all()).isEmpty();
  }

  @Test
  void lets_a_scenario_name_the_refund_amount_it_declines_and_leaves_the_others_alone() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    decliningARefundOf("400");

    StripeApiResponse declined = refund(api, Map.of("amount", "400"));
    StripeApiResponse allowed = refund(api, Map.of("amount", "300"));

    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(allowed.httpStatus()).isEqualTo(200);
    assertThat(refunds.all())
        .singleElement()
        .satisfies(only -> assertThat(only.amount().value()).isEqualTo("300"));
  }

  @Test
  void stops_accepting_refunds_once_everything_captured_has_been_returned() {
    StripeApi api = aPaymentCapturedInFullOf("1000");

    refund(api, Map.of("amount", "400"));
    ProviderObjectState partly = stateOf(FIRST_INTENT);
    refund(api, Map.of("amount", "600"));

    assertThat(partly.refundsAccepted()).isTrue();
    assertThat(stateOf(FIRST_INTENT).refundsAccepted()).isFalse();
    assertThat(stateOf(FIRST_INTENT).normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void refuses_a_refund_that_lost_its_place_on_the_ledger_to_another_request() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    refunds.racingTheNextWrite();

    StripeApiResponse refused = refund(api, Map.of("amount", "400"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"" + StripeErrors.PAYMENT_INTENT_UNEXPECTED_STATE + "\"");
    assertThat(refunds.all()).isEmpty();
  }

  @Test
  void refuses_a_refund_of_a_payment_that_is_gone_by_the_time_the_refund_is_written() {
    StripeApi api = aPaymentCapturedInFullOf("1000");
    refunds.losingTheIntentOnTheNextWrite();

    StripeApiResponse refused = refund(api, Map.of("amount", "400"));

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused)).contains("\"param\":\"payment_intent\"");
  }

  private void decliningARefundOf(String amount) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            new ScenarioMatch(
                null,
                null,
                null,
                null,
                null,
                Amount.of(amount),
                null,
                StripeOperations.REFUND,
                null,
                Map.of()),
            new ScenarioAction.TerminalFailure(StripeDecline.GENERIC_DECLINE.scenarioCode()),
            null));
  }

  private void decliningTheRefund() {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.REFUND),
            new ScenarioAction.TerminalFailure(StripeDecline.GENERIC_DECLINE.scenarioCode()),
            null));
  }

  private ProviderObjectState stateOf(String providerObjectId) {
    return provider().state(E2E_RUN, providerObjectId).orElseThrow();
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

  private StripeApi aPaymentCapturedInFullOf(String amount) {
    return aPaymentCapturedInFullOf(amount, StripeRefundStatus.SUCCEEDED);
  }

  private StripeApi aPaymentCapturedInFullOf(String amount, StripeRefundStatus landing) {
    StripeApi api = anAccountHolding(amount, landing);
    api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);
    return api;
  }

  private StripeApi anAccountHolding(String amount) {
    return anAccountHolding(amount, StripeRefundStatus.SUCCEEDED);
  }

  private StripeApi anAccountHolding(String amount, StripeRefundStatus landing) {
    accounts.with(
        StripeDoubles.account(
            E2E_RUN,
            ACCOUNT,
            SECRET_KEY,
            THREE_DAYS,
            Map.of(StripeRefundOutcome.SETTING, landing.name())));
    StripeApi api = api();
    api.createPaymentIntent(post("/v1/payment_intents", manualHoldOf(amount)));
    return api;
  }

  private StripeApiResponse refund(StripeApi api, Map<String, String> form) {
    Map<String, String> sent = new LinkedHashMap<>();
    sent.put("payment_intent", FIRST_INTENT);
    sent.putAll(form);
    return api.createRefund(post(REFUNDS_PATH, sent));
  }

  private StripePaymentIntent theIntent() {
    return intents.find(E2E_RUN, FIRST_INTENT).orElseThrow();
  }

  private StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall(
        "POST",
        path,
        StripePaymentIntentTest.bearer(SECRET_KEY),
        StripePaymentIntentTest.fieldsOf(form));
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

  private static String theIdAtDraw(int draw) {
    return CreateRefund.ID_PREFIX + String.format("%016x", draw);
  }

  private String theChargeOf(String intentId) {
    return intents.find(E2E_RUN, intentId).orElseThrow().latestCharge();
  }
}
