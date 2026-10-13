package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.cancelPath;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.capturePath;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.fieldsOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.intentPath;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.manualHoldOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeChargeTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String OTHER_ACCOUNT = "merchant-2";
  private static final String OTHER_SECRET_KEY = "sk_test_51Another";
  private static final String THREE_DAYS = "P3D";
  private static final String CARD = "pm_card_visa";

  private static final String CREATE_PATH = "/v1/payment_intents";
  private static final String REFUNDS_PATH = "/v1/refunds";
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";

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
  void a_payment_intent_names_no_charge_until_a_confirmation_has_been_attempted() {
    StripeApi api = anAccount();
    api.createPaymentIntent(post(CREATE_PATH, unconfirmedHoldOf("1000")));

    StripeApiResponse retrieved =
        api.retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(bodyOf(retrieved)).contains("\"latest_charge\":null");
    assertThat(charges.all()).isEmpty();
  }

  @Test
  void an_authorized_hold_names_the_charge_it_minted_and_that_charge_can_be_retrieved() {
    StripeApi api = aHoldOf("1000");

    StripeApiResponse retrieved =
        api.retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    String chargeId = theChargeOf(FIRST_INTENT);
    assertThat(chargeId).startsWith(StripeChargeMinting.ID_PREFIX);
    assertThat(bodyOf(retrieved)).contains("\"latest_charge\":\"" + chargeId + "\"");
    assertThat(bodyOf(charge(api, chargeId)))
        .contains("\"id\":\"" + chargeId + "\"")
        .contains("\"object\":\"charge\"")
        .contains("\"amount\":1000")
        .contains("\"currency\":\"pln\"")
        .contains("\"payment_intent\":\"" + FIRST_INTENT + "\"")
        .contains("\"payment_method\":\"" + CARD + "\"");
  }

  @Test
  void an_uncaptured_hold_is_paid_and_is_not_captured() {
    StripeApi api = aHoldOf("1000");

    StripeApiResponse retrieved = charge(api, theChargeOf(FIRST_INTENT));

    assertThat(bodyOf(retrieved))
        .contains("\"paid\":true")
        .contains("\"captured\":false")
        .contains("\"amount_captured\":0")
        .contains("\"refunded\":false")
        .contains("\"amount_refunded\":0");
  }

  @Test
  void a_charge_of_an_uncaptured_hold_succeeded_while_its_payment_intent_requires_capture() {
    StripeApi api = aHoldOf("1000");

    StripeApiResponse intent =
        api.retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(bodyOf(intent)).contains("\"status\":\"requires_capture\"");
    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT)))).contains("\"status\":\"succeeded\"");
  }

  @Test
  void a_charge_of_a_cancelled_hold_still_says_it_succeeded_while_its_payment_is_canceled() {
    StripeApi api = aHoldOf("1000");
    String chargeId = theChargeOf(FIRST_INTENT);

    StripeApiResponse cancelled =
        api.cancelPaymentIntent(post(cancelPath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(bodyOf(cancelled)).contains("\"status\":\"canceled\"");
    assertThat(bodyOf(charge(api, chargeId)))
        .contains("\"status\":\"succeeded\"")
        .contains("\"paid\":true")
        .contains("\"captured\":false");
  }

  @Test
  void a_non_final_capture_moves_the_captured_amount_and_leaves_the_charge_uncaptured() {
    StripeApi api = aHoldOf("1000");

    api.capturePaymentIntent(
        post(
            capturePath(FIRST_INTENT),
            Map.of("amount_to_capture", "700", "final_capture", "false")),
        FIRST_INTENT);

    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT))))
        .contains("\"amount_captured\":700")
        .contains("\"captured\":false")
        .contains("\"paid\":true");
  }

  @Test
  void a_final_capture_captures_the_charge() {
    StripeApi api = aHoldOf("1000");

    api.capturePaymentIntent(
        post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "1000")), FIRST_INTENT);

    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT))))
        .contains("\"captured\":true")
        .contains("\"amount_captured\":1000");
  }

  @Test
  void a_capture_that_closes_the_authorization_after_a_non_final_one_captures_the_charge() {
    StripeApi api = aHoldOf("1000");
    api.capturePaymentIntent(
        post(
            capturePath(FIRST_INTENT),
            Map.of("amount_to_capture", "700", "final_capture", "false")),
        FIRST_INTENT);

    api.capturePaymentIntent(
        post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "300")), FIRST_INTENT);

    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT))))
        .contains("\"captured\":true")
        .contains("\"amount_captured\":1000");
  }

  @Test
  void a_partly_refunded_charge_reports_the_amount_returned_and_is_not_refunded() {
    StripeApi api = aCapturedPaymentOf("1000");

    api.createRefund(post(REFUNDS_PATH, Map.of("payment_intent", FIRST_INTENT, "amount", "400")));

    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT))))
        .contains("\"amount_refunded\":400")
        .contains("\"refunded\":false");
  }

  @Test
  void a_charge_refunded_to_the_whole_captured_amount_is_refunded() {
    StripeApi api = aCapturedPaymentOf("1000");

    api.createRefund(post(REFUNDS_PATH, Map.of("payment_intent", FIRST_INTENT, "amount", "1000")));

    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT))))
        .contains("\"amount_refunded\":1000")
        .contains("\"refunded\":true");
  }

  @Test
  void a_charge_whose_authorization_is_still_open_is_not_refunded_though_every_captured_penny_is() {
    StripeApi api = aHoldOf("1000");
    api.capturePaymentIntent(
        post(
            capturePath(FIRST_INTENT),
            Map.of("amount_to_capture", "700", "final_capture", "false")),
        FIRST_INTENT);

    api.createRefund(post(REFUNDS_PATH, Map.of("payment_intent", FIRST_INTENT, "amount", "700")));

    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT))))
        .contains("\"amount_refunded\":700")
        .contains("\"amount_captured\":700")
        .contains("\"captured\":false")
        .contains("\"refunded\":false");
  }

  @Test
  void a_declined_confirmation_mints_a_failed_charge_that_was_never_paid() {
    StripeApi api = anAccount();
    declining(StripeOperations.CREATE, StripeDecline.GENERIC_DECLINE);

    StripeApiResponse declined = api.createPaymentIntent(post(CREATE_PATH, manualHoldOf("1000")));

    assertThat(declined.httpStatus()).isEqualTo(402);
    String chargeId = theChargeOf(FIRST_INTENT);
    assertThat(bodyOf(declined)).contains("\"latest_charge\":\"" + chargeId + "\"");
    assertThat(bodyOf(charge(api, chargeId)))
        .contains("\"status\":\"failed\"")
        .contains("\"paid\":false")
        .contains("\"captured\":false")
        .contains("\"amount_captured\":0");
  }

  @Test
  void a_charge_that_failed_reports_none_of_the_money_a_later_charge_of_its_payment_took() {
    StripeApi api = anAccount();
    declining(StripeOperations.CONFIRM, StripeDecline.GENERIC_DECLINE);
    api.createPaymentIntent(post(CREATE_PATH, unconfirmedHoldOf("1000")));
    api.confirmPaymentIntent(post(intentPath(FIRST_INTENT) + "/confirm", Map.of()), FIRST_INTENT);
    String failed = theChargeOf(FIRST_INTENT);
    scenarioRules.clear(StripeApi.STRIPE, E2E_RUN);
    api.confirmPaymentIntent(post(intentPath(FIRST_INTENT) + "/confirm", Map.of()), FIRST_INTENT);
    api.capturePaymentIntent(
        post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "1000")), FIRST_INTENT);
    api.createRefund(post(REFUNDS_PATH, Map.of("payment_intent", FIRST_INTENT, "amount", "400")));

    StripeApiResponse retrieved = charge(api, failed);

    assertThat(theChargeOf(FIRST_INTENT)).isNotEqualTo(failed);
    assertThat(bodyOf(retrieved))
        .contains("\"status\":\"failed\"")
        .contains("\"paid\":false")
        .contains("\"amount_captured\":0")
        .contains("\"amount_refunded\":0");
    assertThat(bodyOf(charge(api, theChargeOf(FIRST_INTENT))))
        .contains("\"amount_captured\":1000")
        .contains("\"amount_refunded\":400");
  }

  @Test
  void answers_an_unknown_charge_as_a_missing_resource() {
    StripeApi api = aHoldOf("1000");

    StripeApiResponse missing = charge(api, StripeChargeMinting.ID_PREFIX + "nothing");

    assertThat(missing.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(missing))
        .contains("\"code\":\"" + StripeErrors.RESOURCE_MISSING + "\"")
        .contains("\"param\":\"charge\"");
  }

  @Test
  void answers_a_charge_of_another_account_as_a_missing_resource() {
    StripeApi api = aHoldOf("1000");
    String chargeId = theChargeOf(FIRST_INTENT);
    accounts.with(StripeDoubles.account(E2E_RUN, OTHER_ACCOUNT, OTHER_SECRET_KEY, THREE_DAYS));

    StripeApiResponse missing =
        api.retrieveCharge(
            new StripeApiCall("GET", chargePath(chargeId), bearer(OTHER_SECRET_KEY), Map.of()),
            chargeId);

    assertThat(missing.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(missing)).contains("\"param\":\"charge\"");
  }

  @Test
  void refunds_a_charge_a_caller_names_instead_of_the_payment_intent() {
    StripeApi api = aCapturedPaymentOf("1000");
    String chargeId = theChargeOf(FIRST_INTENT);

    StripeApiResponse refunded =
        api.createRefund(post(REFUNDS_PATH, Map.of("charge", chargeId, "amount", "400")));

    assertThat(refunded.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(refunded))
        .contains("\"charge\":\"" + chargeId + "\"")
        .contains("\"payment_intent\":\"" + FIRST_INTENT + "\"")
        .contains("\"amount\":400");
    assertThat(bodyOf(charge(api, chargeId))).contains("\"amount_refunded\":400");
  }

  @Test
  void answers_a_refund_of_an_unknown_charge_as_a_missing_resource() {
    StripeApi api = aCapturedPaymentOf("1000");

    StripeApiResponse missing =
        api.createRefund(
            post(REFUNDS_PATH, Map.of("charge", StripeChargeMinting.ID_PREFIX + "nothing")));

    assertThat(missing.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(missing))
        .contains("\"code\":\"" + StripeErrors.RESOURCE_MISSING + "\"")
        .contains("\"param\":\"charge\"");
    assertThat(refunds.all()).isEmpty();
  }

  @Test
  void refuses_a_refund_of_a_charge_that_never_succeeded() {
    StripeApi api = anAccount();
    declining(StripeOperations.CREATE, StripeDecline.GENERIC_DECLINE);
    api.createPaymentIntent(post(CREATE_PATH, manualHoldOf("1000")));

    StripeApiResponse refused =
        api.createRefund(post(REFUNDS_PATH, Map.of("charge", theChargeOf(FIRST_INTENT))));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"charge\"");
    assertThat(refunds.all()).isEmpty();
  }

  @Test
  void refuses_a_refund_that_names_both_a_charge_and_a_payment_intent() {
    StripeApi api = aCapturedPaymentOf("1000");

    StripeApiResponse refused =
        api.createRefund(
            post(
                REFUNDS_PATH,
                Map.of("charge", theChargeOf(FIRST_INTENT), "payment_intent", FIRST_INTENT)));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"charge\"");
    assertThat(refunds.all()).isEmpty();
  }

  @Test
  void refuses_a_refund_that_names_neither_a_charge_nor_a_payment_intent() {
    StripeApi api = aCapturedPaymentOf("1000");

    StripeApiResponse refused = api.createRefund(post(REFUNDS_PATH, Map.of("amount", "400")));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"" + StripeErrors.PARAMETER_MISSING + "\"")
        .contains("\"param\":\"payment_intent\"");
    assertThat(refunds.all()).isEmpty();
  }

  @Test
  void gives_up_rather_than_drawing_charge_ids_for_ever_when_every_one_it_draws_is_taken() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
    String[] everyIdItWouldDraw = new String[StripeChargeMinting.ID_ATTEMPTS];
    for (int attempt = 0; attempt < StripeChargeMinting.ID_ATTEMPTS; attempt++) {
      everyIdItWouldDraw[attempt] =
          StripeChargeMinting.ID_PREFIX + String.format("%016x", attempt + 2);
    }
    charges.withTheIdsAlreadyTaken(everyIdItWouldDraw);
    StripeApi api = api();

    StripeApiCall creating = post(CREATE_PATH, manualHoldOf("1000"));

    assertThatThrownBy(() -> api.createPaymentIntent(creating))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(StripeChargeMinting.ID_ATTEMPTS));
    assertThat(charges.all()).isEmpty();
  }

  @Test
  void refuses_to_answer_a_charge_whose_payment_this_simulator_no_longer_stores() {
    StripeApi api = aHoldOf("1000");
    String chargeId = theChargeOf(FIRST_INTENT);
    intents.clear(E2E_RUN);

    StripeApiCall reading = get(chargePath(chargeId));

    assertThatThrownBy(() -> api.retrieveCharge(reading, chargeId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(StripeChargeLookup.A_CHARGE_OUTLIVED_ITS_PAYMENT);
  }

  @Test
  void refuses_to_refund_a_charge_whose_payment_this_simulator_no_longer_stores() {
    StripeApi api = aCapturedPaymentOf("1000");
    String chargeId = theChargeOf(FIRST_INTENT);
    intents.clear(E2E_RUN);

    StripeApiCall refunding = post(REFUNDS_PATH, Map.of("charge", chargeId, "amount", "400"));

    assertThatThrownBy(() -> api.createRefund(refunding))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(StripeChargeLookup.A_CHARGE_OUTLIVED_ITS_PAYMENT);
  }

  @Test
  void reads_no_charge_status_the_vendor_does_not_publish_for_a_charge() {
    assertThat(StripeChargeStatus.of("succeeded")).contains(StripeChargeStatus.SUCCEEDED);
    assertThat(StripeChargeStatus.of("failed")).contains(StripeChargeStatus.FAILED);
    assertThat(StripeChargeStatus.of("requires_capture")).isEmpty();
    assertThat(StripeChargeStatus.SUCCEEDED.paid()).isTrue();
    assertThat(StripeChargeStatus.FAILED.paid()).isFalse();
  }

  @Test
  void draws_another_charge_id_when_the_one_it_drew_is_already_claimed() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
    charges.withTheIdsAlreadyTaken(StripeChargeMinting.ID_PREFIX + "0000000000000002");
    StripeApi api = api();

    api.createPaymentIntent(post(CREATE_PATH, manualHoldOf("1000")));

    assertThat(theChargeOf(FIRST_INTENT))
        .isEqualTo(StripeChargeMinting.ID_PREFIX + "0000000000000003");
  }

  private StripeApi anAccount() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
    return api();
  }

  private StripeApi aHoldOf(String amount) {
    StripeApi api = anAccount();
    api.createPaymentIntent(post(CREATE_PATH, manualHoldOf(amount)));
    return api;
  }

  private StripeApi aCapturedPaymentOf(String amount) {
    StripeApi api = aHoldOf(amount);
    api.capturePaymentIntent(
        post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", amount)), FIRST_INTENT);
    return api;
  }

  private static Map<String, String> unconfirmedHoldOf(String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", CARD);
    return form;
  }

  private void declining(String operation, StripeDecline decline) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TerminalFailure(decline.scenarioCode()),
            null));
  }

  private String theChargeOf(String intentId) {
    return intents.find(E2E_RUN, intentId).orElseThrow().latestCharge();
  }

  private StripeApiResponse charge(StripeApi api, String chargeId) {
    return api.retrieveCharge(get(chargePath(chargeId)), chargeId);
  }

  private static String chargePath(String chargeId) {
    return "/v1/charges/" + chargeId;
  }

  private StripeApiCall get(String path) {
    return new StripeApiCall("GET", path, bearer(SECRET_KEY), Map.of());
  }

  private StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall("POST", path, bearer(SECRET_KEY), fieldsOf(form));
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
