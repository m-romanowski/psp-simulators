package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSample;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSamples;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripePaymentIntentTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String THREE_DAYS = "P3D";
  private static final String FOURTEEN_DAYS = "P14D";

  private static final String CREATE_PATH = "/v1/payment_intents";

  private static final CheckJson JSON = new CheckJson();
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";

  private static final String CARD = "pm_card_visa";

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
  void holds_the_funds_of_a_manual_capture_intent_the_creating_call_confirmed() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response = api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(response.testRunId()).isEqualTo(E2E_RUN);
    assertThat(response.contentType()).isEqualTo("application/json");
    assertThat(bodyOf(response))
        .contains("\"id\":\"" + FIRST_INTENT + "\"")
        .contains("\"object\":\"payment_intent\"")
        .contains("\"amount\":1099")
        .contains("\"amount_capturable\":1099")
        .contains("\"amount_received\":0")
        .contains("\"capture_method\":\"manual\"")
        .contains("\"currency\":\"pln\"")
        .contains("\"last_payment_error\":null")
        .contains("\"livemode\":false")
        .contains("\"payment_method_types\":[\"card\"]")
        .contains("\"status\":\"requires_capture\"");
    assertThat(theStoredIntent().ledger().operations())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.kind()).isEqualTo(OperationKind.AUTHORIZATION);
              assertThat(operation.amount().value()).isEqualTo("1099");
              assertThat(operation.closesAuthorization()).isNull();
              assertThat(operation.normalizedStatus())
                  .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
            });
  }

  @Test
  void creates_an_intent_awaiting_a_payment_method_when_the_creating_call_does_not_confirm() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW).createPaymentIntent(create(Map.of("amount", "1099", "currency", "pln")));

    assertThat(bodyOf(response))
        .contains("\"status\":\"requires_payment_method\"")
        .contains("\"payment_method\":null");
    assertThat(theStoredIntent().ledger().operations()).isEmpty();
  }

  @Test
  void awaits_confirmation_when_a_payment_method_was_attached_without_confirming() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                create(Map.of("amount", "1099", "currency", "pln", "payment_method", CARD)));

    assertThat(bodyOf(response)).contains("\"status\":\"requires_confirmation\"");
  }

  @Test
  void carries_an_automatic_capture_intent_straight_to_succeeded_on_confirmation() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                create(
                    Map.of(
                        "amount",
                        "1099",
                        "currency",
                        "pln",
                        "payment_method",
                        CARD,
                        "confirm",
                        "true")));

    assertThat(bodyOf(response))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_received\":1099")
        .contains("\"amount_capturable\":0")
        .contains("\"capture_method\":\"automatic_async\"");
  }

  @Test
  void confirms_an_intent_that_was_created_without_a_payment_method() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(
        create(Map.of("amount", "1099", "currency", "pln", "capture_method", "manual")));

    StripeApiResponse response =
        api.confirmPaymentIntent(
            post(confirmPath(FIRST_INTENT), Map.of("payment_method", CARD)), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"status\":\"requires_capture\"")
        .contains("\"payment_method\":\"" + CARD + "\"");
  }

  @Test
  void refuses_a_confirmation_that_names_no_payment_method_anywhere() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(Map.of("amount", "1099", "currency", "pln")));

    StripeApiResponse response =
        api.confirmPaymentIntent(post(confirmPath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"type\":\"invalid_request_error\"")
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"payment_method\"");
  }

  @Test
  void captures_the_whole_authorized_amount_and_leaves_nothing_capturable() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_received\":1099")
        .contains("\"amount_capturable\":0");
    assertThat(theStoredIntent().ledger().operations()).hasSize(2);
  }

  @Test
  void releases_the_remainder_of_a_partial_capture_because_final_capture_is_the_default() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "700")), FIRST_INTENT);

    assertThat(bodyOf(response))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_received\":700")
        .contains("\"amount_capturable\":0");
    assertThat(theStoredIntent().ledger().operations().get(1).closesAuthorization()).isTrue();
  }

  @Test
  void refuses_a_capture_above_the_capturable_amount_and_leaves_the_hold_standing() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "1100")), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"amount_too_large\"")
        .contains("\"param\":\"amount_to_capture\"")
        .contains("\"payment_intent\":{")
        .contains("\"status\":\"requires_capture\"");
    assertThat(theStoredIntent().status()).isEqualTo(StripeIntentStatus.REQUIRES_CAPTURE);
  }

  @Test
  void refuses_a_capture_amount_that_is_not_a_positive_integer_in_minor_units() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "10.99")), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"code\":\"invalid_charge_amount\"");
  }

  @Test
  void cancels_a_hold_before_capture_and_releases_the_funds() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.cancelPaymentIntent(
            post(cancelPath(FIRST_INTENT), Map.of("cancellation_reason", "abandoned")),
            FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"status\":\"canceled\"")
        .contains("\"cancellation_reason\":\"abandoned\"")
        .contains("\"amount_capturable\":0");
  }

  @Test
  void refuses_a_cancellation_reason_that_stripe_generates_internally() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.cancelPaymentIntent(
            post(cancelPath(FIRST_INTENT), Map.of("cancellation_reason", "expired")), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"param\":\"cancellation_reason\"");
    assertThat(theStoredIntent().status()).isEqualTo(StripeIntentStatus.REQUIRES_CAPTURE);
  }

  @Test
  void refuses_a_capture_of_an_intent_that_was_already_captured() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));
    api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    StripeApiResponse response =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"payment_intent_unexpected_state\"")
        .contains("\"payment_intent\":{")
        .contains("\"status\":\"succeeded\"");
  }

  @Test
  void refuses_a_capture_after_the_authorization_deadline_the_account_configured() {
    accounts.with(stripeAccount(THREE_DAYS));
    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api(NOW.plus(Duration.ofDays(4)))
            .capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"charge_expired_for_capture\"")
        .contains("\"status\":\"canceled\"")
        .contains("\"cancellation_reason\":\"expired\"");
  }

  @Test
  void captures_on_the_tenth_day_when_the_account_configured_a_window_that_reaches_it() {
    accounts.with(stripeAccount(FOURTEEN_DAYS));
    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api(NOW.plus(Duration.ofDays(10)))
            .capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).contains("\"status\":\"succeeded\"");
  }

  @Test
  void reports_an_expired_hold_as_cancelled_when_the_intent_is_retrieved() {
    accounts.with(stripeAccount(THREE_DAYS));
    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api(NOW.plus(Duration.ofDays(4)))
            .retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"status\":\"canceled\"")
        .contains("\"cancellation_reason\":\"expired\"")
        .contains("\"amount_capturable\":0");
  }

  @Test
  void answers_an_unknown_payment_intent_with_the_published_resource_missing_code() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW).retrievePaymentIntent(get(intentPath("pi_sim_nothing")), "pi_sim_nothing");

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .contains("\"type\":\"invalid_request_error\"")
        .contains("\"code\":\"resource_missing\"");
  }

  @Test
  void hides_an_intent_of_one_account_from_a_request_authenticated_as_another() {
    accounts.with(stripeAccount(THREE_DAYS));
    accounts.with(StripeDoubles.account(E2E_RUN, "merchant-2", "sk_test_other", THREE_DAYS));
    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api(NOW)
            .retrievePaymentIntent(
                new StripeApiCall(
                    "GET", intentPath(FIRST_INTENT), bearer("sk_test_other"), Map.of()),
                FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(404);
  }

  @Test
  void refuses_a_request_that_carries_no_api_key_with_the_answer_the_vendor_was_probed_giving() {
    accounts.with(stripeAccount(THREE_DAYS));
    VendorSample probed = VendorSamples.of("stripe").sample("no-api-key");

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                new StripeApiCall("POST", CREATE_PATH, Map.of(), fieldsOf(manualHoldOf("1099"))));

    assertThat(String.valueOf(response.httpStatus())).isEqualTo(probed.status().value());
    assertThat(response.headers().get("Content-Type"))
        .containsExactly(probed.header("Content-Type").value());
    assertThat(response.headers().get("WWW-Authenticate"))
        .containsExactly(probed.header("WWW-Authenticate").value());
    assertThat(JSON.at(bodyOf(response), "/error/type"))
        .isEqualTo(probed.json("/error/type").value());
    assertThat(JSON.at(bodyOf(response), "/error/message"))
        .isEqualTo(probed.json("/error/message").value());
    assertThat(response.testRunId()).isEqualTo(DEFAULT_RUN);
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void refuses_a_key_this_test_run_does_not_configure_without_calling_it_missing() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                new StripeApiCall(
                    "POST", CREATE_PATH, bearer("sk_test_other"), fieldsOf(manualHoldOf("1099"))));

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(response.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("WWW-Authenticate"));
    assertThat(bodyOf(response)).contains("No valid API key provided.");
  }

  @Test
  void accepts_the_secret_key_as_the_basic_auth_username_the_vendor_publishes() {
    accounts.with(stripeAccount(THREE_DAYS));
    Map<String, List<String>> headers =
        Map.of(
            "Authorization",
            List.of(
                "Basic " + Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes(UTF_8))));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                new StripeApiCall("POST", CREATE_PATH, headers, fieldsOf(manualHoldOf("1099"))));

    assertThat(response.httpStatus()).isEqualTo(200);
  }

  @Test
  void refuses_a_creation_that_names_no_amount() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response = api(NOW).createPaymentIntent(create(Map.of("currency", "pln")));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"amount\"");
  }

  @Test
  void refuses_a_creation_whose_amount_is_not_a_positive_integer_in_minor_units() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW).createPaymentIntent(create(Map.of("amount", "0", "currency", "pln")));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"code\":\"invalid_charge_amount\"");
  }

  @Test
  void refuses_an_amount_no_json_reader_could_hold_as_a_whole_number() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW).createPaymentIntent(create(Map.of("amount", "1".repeat(19), "currency", "pln")));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"amount_too_large\"")
        .as(
            "the refusal is answered before any intent is read, so it attaches none, and the key"
                + " an attached one would sit under is what names that: a code or a message"
                + " naming the object is not the object")
        .doesNotContain("\"payment_intent\"");
  }

  @Test
  void refuses_a_capture_method_the_vendor_does_not_publish() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                create(Map.of("amount", "1099", "currency", "pln", "capture_method", "later")));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"param\":\"capture_method\"");
  }

  @Test
  void refuses_a_confirm_flag_that_is_neither_true_nor_false() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                create(Map.of("amount", "1099", "currency", "pln", "confirm", "yes")));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"param\":\"confirm\"");
  }

  @Test
  void refuses_a_confirming_creation_that_names_no_payment_method() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                create(Map.of("amount", "1099", "currency", "pln", "confirm", "true")));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"param\":\"payment_method\"");
  }

  @Test
  void answers_a_declined_confirmation_as_a_card_error_carrying_the_whole_payment_intent() {
    accounts.with(stripeAccount(THREE_DAYS));
    declining(StripeOperations.CONFIRM, StripeDecline.INSUFFICIENT_FUNDS);
    StripeApi api = api(NOW);
    api.createPaymentIntent(
        create(Map.of("amount", "1099", "currency", "pln", "capture_method", "manual")));

    StripeApiResponse response =
        api.confirmPaymentIntent(
            post(confirmPath(FIRST_INTENT), Map.of("payment_method", CARD)), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(402);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .contains("\"type\":\"card_error\"")
        .contains("\"code\":\"card_declined\"")
        .contains("\"decline_code\":\"insufficient_funds\"")
        .contains("\"payment_intent\":{")
        .contains("\"object\":\"payment_intent\"")
        .contains("\"amount\":1099")
        .contains(
            "\"last_payment_error\":{\"code\":\"card_declined\","
                + "\"decline_code\":\"insufficient_funds\",\"type\":\"card_error\"}");
  }

  @Test
  void leaves_a_declined_payment_retrievable_at_requires_payment_method() {
    accounts.with(stripeAccount(THREE_DAYS));
    declining(StripeOperations.CONFIRM, StripeDecline.EXPIRED_CARD);
    StripeApi api = api(NOW);
    api.createPaymentIntent(
        create(Map.of("amount", "1099", "currency", "pln", "capture_method", "manual")));
    api.confirmPaymentIntent(
        post(confirmPath(FIRST_INTENT), Map.of("payment_method", CARD)), FIRST_INTENT);

    StripeApiResponse response =
        api.retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"status\":\"requires_payment_method\"")
        .contains("\"last_payment_error\":{\"code\":\"expired_card\",\"type\":\"card_error\"}");
  }

  @Test
  void answers_a_declined_creation_with_the_intent_it_created_and_the_failure_on_it() {
    accounts.with(stripeAccount(THREE_DAYS));
    declining(StripeOperations.CREATE, StripeDecline.GENERIC_DECLINE);

    StripeApiResponse response = api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    assertThat(response.httpStatus()).isEqualTo(402);
    assertThat(response.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(bodyOf(response))
        .contains("\"decline_code\":\"generic_decline\"")
        .contains("\"status\":\"requires_payment_method\"");
    assertThat(theStoredIntent().lastPaymentError()).isEqualTo(StripeDecline.GENERIC_DECLINE);
  }

  @Test
  void answers_a_declined_capture_without_writing_anything_so_the_hold_survives_the_refusal() {
    accounts.with(stripeAccount(THREE_DAYS));
    declining(StripeOperations.CAPTURE, StripeDecline.PROCESSING_ERROR);
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(402);
    assertThat(bodyOf(response))
        .contains("\"type\":\"card_error\"")
        .contains("\"code\":\"processing_error\"");
    assertThat(theStoredIntent().status()).isEqualTo(StripeIntentStatus.REQUIRES_CAPTURE);
    assertThat(theStoredIntent().ledger().operations()).hasSize(1);
  }

  @Test
  void reports_a_capture_another_request_took_first_as_an_unexpected_state() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));
    intents.racingTheNextWrite();

    StripeApiResponse response =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"code\":\"payment_intent_unexpected_state\"");
  }

  @Test
  void reports_a_capture_of_an_intent_another_request_removed_as_a_missing_resource() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));
    intents.losingTheIntentOnTheNextWrite();

    StripeApiResponse response =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void draws_another_payment_intent_id_when_the_one_it_drew_is_already_claimed() {
    accounts.with(stripeAccount(THREE_DAYS));
    intents.withTheIdsAlreadyClaimed(FIRST_INTENT);

    StripeApiResponse response = api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    assertThat(response.providerObjectId()).isEqualTo("pi_sim_0000000000000002");
  }

  @Test
  void records_the_request_and_the_answer_of_one_call_in_the_audit_trail() {
    accounts.with(stripeAccount(THREE_DAYS));

    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    assertThat(audit.all()).hasSize(2);
    assertThat(audit.all().get(0).sanitizedHeaders().get("Authorization"))
        .containsExactly("[redacted]");
    assertThat(audit.all().get(0).sanitizedBody().get("payment_method"))
        .containsExactly("[redacted]");
    assertThat(audit.all().get(1).httpStatus()).isEqualTo(200);
    assertThat(audit.all().get(1).providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(audit.all().get(1).sanitizedBody().get("client_secret"))
        .containsExactly("[redacted]");
  }

  @Test
  void echoes_the_customer_and_the_receipt_email_the_creating_call_carried() {
    accounts.with(stripeAccount(THREE_DAYS));
    Map<String, String> form = new LinkedHashMap<>(manualHoldOf("1099"));
    form.put("customer", "cus_sim_1");
    form.put("receipt_email", "buyer@example.com");

    StripeApiResponse response = api(NOW).createPaymentIntent(create(form));

    assertThat(bodyOf(response))
        .contains("\"customer\":\"cus_sim_1\"")
        .contains("\"receipt_email\":\"buyer@example.com\"");
  }

  @Test
  void refuses_a_capture_at_the_instant_the_configured_window_closes() {
    accounts.with(stripeAccount(THREE_DAYS));
    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api(NOW.plus(Duration.ofDays(3)))
            .capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"code\":\"charge_expired_for_capture\"");
  }

  @Test
  void captures_in_the_last_instant_before_the_configured_window_closes() {
    accounts.with(stripeAccount(THREE_DAYS));
    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api(NOW.plus(Duration.ofDays(3)).minusMillis(1))
            .capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
  }

  @Test
  void credits_no_scenario_with_a_refusal_the_payment_intent_itself_made() {
    accounts.with(stripeAccount(THREE_DAYS));
    succeeding(StripeOperations.CAPTURE);
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));
    intents.racingTheNextWrite();

    StripeApiResponse response =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(audit.all().get(audit.all().size() - 1).scenarioId()).isNull();
  }

  @Test
  void credits_the_scenario_that_let_a_capture_through() {
    accounts.with(stripeAccount(THREE_DAYS));
    succeeding(StripeOperations.CAPTURE);
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(audit.all().get(audit.all().size() - 1).scenarioId()).isNotNull();
  }

  @Test
  void attributes_no_test_run_to_a_secret_key_two_test_runs_both_configured() {
    accounts.with(stripeAccount(THREE_DAYS));
    accounts.with(StripeDoubles.account(ANOTHER_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));

    StripeApiResponse response = api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(response.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void refuses_a_creation_that_names_no_currency() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response = api(NOW).createPaymentIntent(create(Map.of("amount", "1099")));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"currency\"");
  }

  @Test
  void accepts_the_largest_amount_a_json_reader_can_hold_as_a_whole_number() {
    accounts.with(stripeAccount(THREE_DAYS));
    String largest = "1".repeat(StripeAmounts.LONGEST_MINOR_UNITS_A_JSON_READER_HOLDS);

    StripeApiResponse response =
        api(NOW).createPaymentIntent(create(Map.of("amount", largest, "currency", "pln")));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).contains("\"amount\":" + largest);
  }

  @Test
  void refuses_a_capture_amount_no_json_reader_could_hold_as_a_whole_number() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "1".repeat(19))),
            FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"amount_too_large\"")
        .contains("\"param\":\"amount_to_capture\"")
        .as(
            "the capture is refused by the parameter check that runs before the intent is looked"
                + " up, so it attaches no intent, and the key an attached one would sit under is"
                + " what names that: a code or a message naming the object is not the object")
        .doesNotContain("\"payment_intent\"");
  }

  @Test
  void tells_a_capture_amount_that_is_too_long_apart_from_one_that_is_not_minor_units() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse tooLong =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "1".repeat(19))),
            FIRST_INTENT);
    StripeApiResponse notMinorUnits =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "10.99")), FIRST_INTENT);

    assertThat(bodyOf(tooLong)).contains("\"code\":\"amount_too_large\"");
    assertThat(bodyOf(notMinorUnits)).contains("\"code\":\"invalid_charge_amount\"");
  }

  @Test
  void answers_a_confirmation_of_an_unknown_payment_intent_as_a_missing_resource() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .confirmPaymentIntent(
                post(confirmPath("pi_sim_nothing"), Map.of("payment_method", CARD)),
                "pi_sim_nothing");

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void answers_a_capture_of_an_unknown_payment_intent_as_a_missing_resource() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .capturePaymentIntent(post(capturePath("pi_sim_nothing"), Map.of()), "pi_sim_nothing");

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void answers_a_cancellation_of_an_unknown_payment_intent_as_a_missing_resource() {
    accounts.with(stripeAccount(THREE_DAYS));

    StripeApiResponse response =
        api(NOW)
            .cancelPaymentIntent(post(cancelPath("pi_sim_nothing"), Map.of()), "pi_sim_nothing");

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void refuses_a_second_confirmation_of_a_payment_intent_that_already_holds_the_funds() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.confirmPaymentIntent(
            post(confirmPath(FIRST_INTENT), Map.of("payment_method", CARD)), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response))
        .contains("\"code\":\"payment_intent_unexpected_state\"")
        .contains("\"status\":\"requires_capture\"");
  }

  @Test
  void refuses_a_second_cancellation_of_a_payment_intent_already_cancelled() {
    accounts.with(stripeAccount(THREE_DAYS));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));
    api.cancelPaymentIntent(post(cancelPath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    StripeApiResponse response =
        api.cancelPaymentIntent(post(cancelPath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"code\":\"payment_intent_unexpected_state\"");
  }

  @Test
  void answers_a_declined_cancellation_without_releasing_the_hold_it_was_asked_to_release() {
    accounts.with(stripeAccount(THREE_DAYS));
    declining(StripeOperations.CANCEL, StripeDecline.PROCESSING_ERROR);
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse response =
        api.cancelPaymentIntent(post(cancelPath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(response.httpStatus()).isEqualTo(402);
    assertThat(bodyOf(response)).contains("\"type\":\"card_error\"");
    assertThat(theStoredIntent().status()).isEqualTo(StripeIntentStatus.REQUIRES_CAPTURE);
  }

  @Test
  void writes_the_error_object_the_vendors_own_worked_example_publishes() {
    accounts.with(stripeAccount(THREE_DAYS));
    declining(StripeOperations.CREATE, StripeDecline.AUTHENTICATION_REQUIRED);

    StripeApiResponse response = api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    assertThat(bodyOf(response))
        .startsWith(
            "{\"error\":{\"type\":\"card_error\",\"code\":\"authentication_required\","
                + "\"decline_code\":\"authentication_not_handled\","
                + "\"doc_url\":\"https://docs.stripe.com/error-codes#authentication-required\","
                + "\"message\":\"The payment requires authentication to proceed.");
  }

  @Test
  void keeps_the_answer_of_one_call_addressable_on_its_own_in_the_trail() {
    accounts.with(stripeAccount(THREE_DAYS));

    api(NOW).createPaymentIntent(create(manualHoldOf("1099")));

    assertThat(audit.all()).extracting(AuditRecord::auditId).doesNotHaveDuplicates();
    assertThat(audit.all().get(0).auditId()).startsWith("aud_");
    assertThat(audit.all().get(1).sanitizedHeaders())
        .containsEntry("Content-Type", List.of("application/json"));
  }

  @Test
  void picks_the_account_of_the_test_run_the_header_names_when_two_runs_share_a_secret_key() {
    accounts.with(stripeAccount(THREE_DAYS));
    accounts.with(StripeDoubles.account(ANOTHER_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
    testRuns.register(ANOTHER_RUN);
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer " + SECRET_KEY));
    headers.put("X-Simulator-Test-Run-Id", List.of(ANOTHER_RUN.value()));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                new StripeApiCall("POST", CREATE_PATH, headers, fieldsOf(manualHoldOf("1099"))));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.testRunId()).isEqualTo(ANOTHER_RUN);
  }

  @Test
  void gives_up_drawing_a_payment_intent_id_after_the_attempts_it_allows_itself() {
    accounts.with(stripeAccount(THREE_DAYS));
    intents.withTheIdsAlreadyClaimed(
        "pi_sim_0000000000000001",
        "pi_sim_0000000000000002",
        "pi_sim_0000000000000003",
        "pi_sim_0000000000000004",
        "pi_sim_0000000000000005");

    StripeApi api = api(NOW);
    StripeApiCall creating = create(manualHoldOf("1099"));

    assertThatThrownBy(() -> api.createPaymentIntent(creating))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(CreatePaymentIntent.ID_ATTEMPTS));
  }

  @Test
  void narrows_a_rule_to_the_capture_that_asks_for_an_amount_of_its_own() {
    accounts.with(stripeAccount(THREE_DAYS));
    decliningACaptureOf("700");
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse whole =
        api.capturePaymentIntent(post(capturePath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(whole.httpStatus()).isEqualTo(200);
  }

  @Test
  void fires_a_rule_that_names_an_amount_on_the_capture_that_carries_it() {
    accounts.with(stripeAccount(THREE_DAYS));
    decliningACaptureOf("700");
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(manualHoldOf("1099")));

    StripeApiResponse part =
        api.capturePaymentIntent(
            post(capturePath(FIRST_INTENT), Map.of("amount_to_capture", "700")), FIRST_INTENT);

    assertThat(part.httpStatus()).isEqualTo(402);
  }

  @Test
  void a_payment_a_rule_left_in_flight_answers_processing_so_a_merchant_cannot_read_it_as_paid() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CREATE, null);

    StripeApiResponse created = api(NOW).createPaymentIntent(create(chargeOf("1099")));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(created))
        .contains("\"status\":\"processing\"")
        .contains("\"amount_received\":0")
        .contains("\"last_payment_error\":null");
  }

  @Test
  void a_payment_still_in_flight_answers_processing_to_a_lookup_taken_before_it_was_owed() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CREATE, null);
    api(NOW).createPaymentIntent(create(chargeOf("1099")));

    StripeApiResponse read =
        api(NOW.plus(Duration.ofSeconds(4)))
            .retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(bodyOf(read)).contains("\"status\":\"processing\"");
  }

  @Test
  void a_payment_in_flight_comes_to_rest_as_succeeded_once_the_run_clock_passes_the_moment_owed() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CREATE, null);
    api(NOW).createPaymentIntent(create(chargeOf("1099")));

    StripeApiResponse read =
        api(NOW.plus(Duration.ofSeconds(5)))
            .retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(bodyOf(read))
        .contains("\"status\":\"succeeded\"")
        .contains("\"last_payment_error\":null");
  }

  @Test
  void a_payment_in_flight_comes_to_rest_as_the_decline_the_rule_named_it_would_fail_with() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CREATE, StripeDecline.INSUFFICIENT_FUNDS);
    api(NOW).createPaymentIntent(create(chargeOf("1099")));

    StripeApiResponse read =
        api(NOW.plus(Duration.ofSeconds(5)))
            .retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(bodyOf(read))
        .contains("\"status\":\"requires_payment_method\"")
        .contains(StripeDecline.INSUFFICIENT_FUNDS.declineCode());
  }

  @Test
  void a_confirmation_of_its_own_puts_a_payment_in_flight_where_the_rule_names_the_confirmation() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CONFIRM, null);
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(unconfirmedChargeOf("1099")));

    StripeApiResponse confirmed =
        api.confirmPaymentIntent(post(confirmPath(FIRST_INTENT), Map.of()), FIRST_INTENT);

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed)).contains("\"status\":\"processing\"");
  }

  @Test
  void a_confirmation_that_pends_to_fail_answers_the_object_carrying_the_decline_it_is_owed() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CONFIRM, StripeDecline.INSUFFICIENT_FUNDS);
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(unconfirmedChargeOf("1099")));

    StripeApiResponse confirmed =
        api.confirmPaymentIntent(post(confirmPath(FIRST_INTENT), Map.of()), FIRST_INTENT);
    StripeApiResponse afterwards =
        api(NOW.plus(Duration.ofSeconds(5)))
            .retrievePaymentIntent(get(intentPath(FIRST_INTENT)), FIRST_INTENT);

    assertThat(bodyOf(confirmed))
        .contains("\"status\":\"processing\"")
        .contains("\"last_payment_error\":null");
    assertThat(bodyOf(afterwards))
        .contains("\"status\":\"requires_payment_method\"")
        .contains(StripeDecline.INSUFFICIENT_FUNDS.declineCode());
    assertThat(theStoredIntent().settlesWith()).isEqualTo(StripeDecline.INSUFFICIENT_FUNDS);
  }

  @Test
  void a_creation_that_confirms_nothing_is_not_left_in_flight_by_a_rule_naming_the_creation() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CREATE, null);

    StripeApiResponse created = api(NOW).createPaymentIntent(create(unconfirmedChargeOf("1099")));

    assertThat(bodyOf(created)).contains("\"status\":\"requires_confirmation\"");
    assertThat(theStoredIntent().settlesAt()).isNull();
  }

  @Test
  void a_payment_a_rule_settled_moved_no_money_this_simulator_saw_so_it_carries_no_operation() {
    accounts.with(stripeAccount(THREE_DAYS));
    inFlightOn(StripeOperations.CREATE, null);
    api(NOW).createPaymentIntent(create(chargeOf("1099")));

    StripePaymentIntent settled = theStoredIntent();

    assertThat(settled.ledger().operations()).isEmpty();
    assertThat(settled.normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(settled.normalizedStatusAt(NOW.plus(Duration.ofSeconds(5))))
        .isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(settled.refundsAccepted()).isFalse();
  }

  private void inFlightOn(String operation, @Nullable StripeDecline comingToRestWith) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.ProcessingThenTerminal(
                Duration.ofSeconds(5),
                comingToRestWith == null ? null : comingToRestWith.scenarioCode()),
            null));
  }

  private static Map<String, String> chargeOf(String amount) {
    Map<String, String> form = unconfirmedChargeOf(amount);
    form.put("confirm", "true");
    return form;
  }

  private static Map<String, String> unconfirmedChargeOf(String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "pln");
    form.put("payment_method", CARD);
    return form;
  }

  private void decliningACaptureOf(String amount) {
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
                StripeOperations.CAPTURE,
                null,
                Map.of()),
            new ScenarioAction.TerminalFailure(StripeDecline.GENERIC_DECLINE.scenarioCode()),
            null));
  }

  private void succeeding(String operation) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.FixedSuccess(),
            null));
  }

  private StripePaymentIntent theStoredIntent() {
    return intents.all().get(0);
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

  private StripeAccount stripeAccount(String captureWindow) {
    return StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, captureWindow);
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

  static Map<String, String> manualHoldOf(String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", CARD);
    form.put("confirm", "true");
    return form;
  }

  static String intentPath(String intentId) {
    return CREATE_PATH + "/" + intentId;
  }

  static String confirmPath(String intentId) {
    return intentPath(intentId) + "/confirm";
  }

  static String capturePath(String intentId) {
    return intentPath(intentId) + "/capture";
  }

  static String cancelPath(String intentId) {
    return intentPath(intentId) + "/cancel";
  }

  private StripeApiCall create(Map<String, String> form) {
    return post(CREATE_PATH, form);
  }

  private StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall("POST", path, bearer(SECRET_KEY), fieldsOf(form));
  }

  private StripeApiCall get(String path) {
    return new StripeApiCall("GET", path, bearer(SECRET_KEY), Map.of());
  }

  @Test
  void the_client_secret_inside_a_declined_payment_intent_is_withheld_from_the_audit_trail() {
    accounts.with(stripeAccount(THREE_DAYS));
    declining(StripeOperations.CREATE, StripeDecline.GENERIC_DECLINE);

    api(NOW)
        .createPaymentIntent(
            new StripeApiCall(
                "POST",
                "/v1/payment_intents",
                bearer(SECRET_KEY),
                fieldsOf(new LinkedHashMap<>(Map.of("amount", "1000", "currency", "usd")))));

    String secret = intents.all().get(0).clientSecret();
    assertThat(audit.of(StripeApi.STRIPE, E2E_RUN))
        .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .allSatisfy(
            record ->
                assertThat(record.sanitizedBody().values())
                    .noneSatisfy(values -> assertThat(values).contains(secret)));
  }

  static Map<String, List<String>> bearer(String secretKey) {
    return Map.of("Authorization", List.of("Bearer " + secretKey));
  }

  static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  static String bodyOf(StripeApiResponse response) {
    return new String(response.body(), UTF_8);
  }
}
