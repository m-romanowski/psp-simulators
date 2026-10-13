package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.capturePath;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.intentPath;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.manualHoldOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeMulticaptureTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String THREE_DAYS = "P3D";

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
  void keeps_the_remainder_authorized_when_the_capture_is_not_the_final_one() {
    StripeApi api = anAccountHolding("1000");

    StripeApiResponse captured = capture(api, Map.of("amount_to_capture", "700"), false);

    assertThat(bodyOf(captured))
        .contains("\"status\":\"requires_capture\"")
        .contains("\"amount_capturable\":300")
        .contains("\"amount_received\":700");
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.PARTIALLY_CAPTURED);
  }

  @Test
  void closes_the_authorization_and_forfeits_the_remainder_on_the_final_capture() {
    StripeApi api = anAccountHolding("1000");
    capture(api, Map.of("amount_to_capture", "700"), false);

    StripeApiResponse closed = capture(api, Map.of("amount_to_capture", "100"), true);

    assertThat(bodyOf(closed))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_capturable\":0")
        .contains("\"amount_received\":800");
    assertThat(theIntent().normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void treats_a_capture_that_names_no_final_capture_as_the_published_default_of_a_final_one() {
    StripeApi api = anAccountHolding("1000");

    StripeApiResponse captured =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "700")), FIRST_INTENT);

    assertThat(bodyOf(captured))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_capturable\":0");
  }

  @Test
  void measures_a_later_capture_against_the_remainder_the_earlier_one_left() {
    StripeApi api = anAccountHolding("1000");
    capture(api, Map.of("amount_to_capture", "700"), false);

    StripeApiResponse tooMuch = capture(api, Map.of("amount_to_capture", "400"), true);

    assertThat(tooMuch.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(tooMuch)).contains("\"code\":\"" + StripeErrors.AMOUNT_TOO_LARGE + "\"");
  }

  @Test
  void refuses_a_non_final_capture_once_the_published_ceiling_of_them_is_reached() {
    StripeApi api = anAccountHolding("1000");
    for (int taken = 0; taken < StripeMulticapture.PUBLISHED_NON_FINAL_CAPTURE_CEILING; taken++) {
      capture(api, Map.of("amount_to_capture", "1"), false);
    }

    StripeApiResponse refused = capture(api, Map.of("amount_to_capture", "1"), false);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("final_capture");
    assertThat(theIntent().nonFinalCaptures())
        .isEqualTo(StripeMulticapture.PUBLISHED_NON_FINAL_CAPTURE_CEILING);
  }

  @Test
  void still_allows_the_one_final_capture_that_completes_a_payment_at_the_ceiling() {
    StripeApi api = anAccountHolding("1000");
    for (int taken = 0; taken < StripeMulticapture.PUBLISHED_NON_FINAL_CAPTURE_CEILING; taken++) {
      capture(api, Map.of("amount_to_capture", "1"), false);
    }

    StripeApiResponse closed = capture(api, Map.of("amount_to_capture", "1"), true);

    assertThat(bodyOf(closed))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_received\":51");
  }

  @Test
  void refuses_a_capture_of_a_remainder_an_earlier_non_final_capture_left_at_nothing() {
    StripeApi api = anAccountHolding("1000");
    capture(api, Map.of("amount_to_capture", "1000"), false);

    StripeApiResponse refused =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"code\":\"" + StripeErrors.INVALID_CHARGE_AMOUNT + "\"");
  }

  @Test
  void refuses_a_final_capture_flag_the_vendor_publishes_no_value_for() {
    StripeApi api = anAccountHolding("1000");

    StripeApiResponse refused =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("final_capture", "maybe")), FIRST_INTENT);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"final_capture\"");
  }

  @Test
  void writes_every_capture_of_one_payment_onto_the_ledger_that_answers_for_it() {
    StripeApi api = anAccountHolding("1000");

    capture(api, Map.of("amount_to_capture", "600"), false);
    capture(api, Map.of("amount_to_capture", "200"), true);

    assertThat(theIntent().ledger().count(OperationKind.CAPTURE)).isEqualTo(2);
    assertThat(theIntent().ledger().operations()).hasSize(3);
    assertThat(theIntent().nonFinalCaptures()).isEqualTo(1);
    assertThat(theIntent().ledger().operations())
        .satisfiesExactly(
            authorization ->
                assertThat(authorization.kind()).isEqualTo(OperationKind.AUTHORIZATION),
            first -> {
              assertThat(first.closesAuthorization()).isFalse();
              assertThat(first.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_CAPTURED);
            },
            last -> {
              assertThat(last.closesAuthorization()).isTrue();
              assertThat(last.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
            });
  }

  @Test
  void reports_the_reduced_remainder_to_a_caller_that_retrieves_the_payment() {
    StripeApi api = anAccountHolding("1000");
    capture(api, Map.of("amount_to_capture", "700"), false);

    StripeApiResponse retrieved =
        api.retrievePaymentIntent(
            new StripeApiCall(
                "GET",
                intentPath(FIRST_INTENT),
                StripePaymentIntentTest.bearer(SECRET_KEY),
                Map.of()),
            FIRST_INTENT);

    assertThat(bodyOf(retrieved))
        .contains("\"amount_capturable\":300")
        .contains("\"status\":\"requires_capture\"");
  }

  private StripeApi anAccountHolding(String amount) {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
    StripeApi api = api();
    api.createPaymentIntent(post("/v1/payment_intents", manualHoldOf(amount)));
    return api;
  }

  private StripeApiResponse capture(StripeApi api, Map<String, String> form, boolean closing) {
    Map<String, String> sent = new LinkedHashMap<>(form);
    sent.put("final_capture", closing ? "true" : "false");
    return api.capturePaymentIntent(post(capturePath(FIRST_INTENT), sent), FIRST_INTENT);
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
}
