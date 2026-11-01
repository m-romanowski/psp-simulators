package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StripePublishableKeyTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId RUN = TestRunId.of("stripe-publishable-run");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("stripe-publishable-elsewhere");

  private static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");
  private static final Instant TWO_HOURS_ON = Instant.parse("2026-10-31T11:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String PUBLISHABLE_KEY = "pk_test_51Simulator";
  private static final String ANOTHER_ACCOUNT = "merchant-2";
  private static final String ANOTHER_SECRET_KEY = "sk_test_51AnotherSimulator";
  private static final String ANOTHER_PUBLISHABLE_KEY = "pk_test_51AnotherSimulator";
  private static final String ELSEWHERE_SECRET_KEY = "sk_test_51ElsewhereSimulator";
  private static final String ELSEWHERE_PUBLISHABLE_KEY = "pk_test_51ElsewhereSimulator";
  private static final String THREE_DAYS = "P3D";
  private static final String ONE_HOUR = "PT1H";

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";
  private static final String SETUP_INTENTS = "/v1/setup_intents";
  private static final String A_RETURN_URL = "https://shop.example/checkout/return";
  private static final String IOS_PINS = "2020-08-27";
  private static final String ANDROID_PINS = "2020-03-02";
  private static final String AN_IDEMPOTENCY_KEY = "4b1e6f0e-0a52-4c39-9a6e-0c3a5f1d2e71";

  private static final String THE_CUSTOMER = "cus_the_one_paying_in_the_app";
  private static final String THE_CARD = "pm_the_card_the_app_confirms";
  private static final String ANOTHER_CUSTOMER = "cus_the_one_whose_card_it_is";
  private static final String ANOTHER_CUSTOMERS_CARD = "pm_the_card_another_customer_holds";

  private static final List<String> WHAT_A_PUBLISHABLE_KEY_SEES_OF_A_PAYMENT =
      List.of(
          "id",
          "object",
          "allowed_payment_method_types",
          "amount",
          "automatic_payment_methods",
          "cancellation_reason",
          "capture_method",
          "client_secret",
          "created",
          "currency",
          "excluded_payment_method_types",
          "last_payment_error",
          "livemode",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_types",
          "receipt_email",
          "status");

  private static final List<String> WHAT_A_PUBLISHABLE_KEY_SEES_OF_A_SETUP =
      List.of(
          "id",
          "object",
          "allowed_payment_method_types",
          "automatic_payment_methods",
          "client_secret",
          "created",
          "excluded_payment_method_types",
          "last_setup_error",
          "livemode",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_types",
          "status",
          "usage");

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
  private final StripeDoubles.Runs testRuns = new StripeDoubles.Runs(DEFAULT_RUN);
  private final StripeDoubles.Audit audit = new StripeDoubles.Audit();
  private final StripeDoubles.PaymentMethods paymentMethods = new StripeDoubles.PaymentMethods();
  private final StripeDoubles.Intents intents = new StripeDoubles.Intents(paymentMethods);
  private final StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
  private final StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
  private final StripeDoubles.Customers customers = new StripeDoubles.Customers();
  private final StripeDoubles.SetupIntents setupIntents =
      new StripeDoubles.SetupIntents(paymentMethods);
  private final StripeDoubles.Keys keys = new StripeDoubles.Keys();
  private final StripeDoubles.EphemeralKeys ephemeralKeys = new StripeDoubles.EphemeralKeys();
  private final StripeDoubles.CountingIds ids = new StripeDoubles.CountingIds();

  @Test
  void a_publishable_key_reads_a_payment_beside_its_client_secret_and_sees_only_what_stripe_shows()
      throws Exception {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse read =
        get(PUBLISHABLE_KEY, PAYMENT_INTENTS + "/" + payment.id(), secretOf(payment), Map.of());

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(membersOf(read)).containsExactlyElementsOf(WHAT_A_PUBLISHABLE_KEY_SEES_OF_A_PAYMENT);
    assertThat(bodyOf(read))
        .contains("\"id\":\"" + payment.id() + "\"")
        .contains("\"client_secret\":\"" + payment.clientSecret() + "\"")
        .doesNotContain(THE_CUSTOMER);
  }

  @Test
  void a_secret_key_reads_the_same_payment_whole() {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse read = get(SECRET_KEY, PAYMENT_INTENTS + "/" + payment.id(), Map.of());

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(membersOf(read))
        .contains("amount_capturable", "amount_received", "customer", "latest_charge");
    assertThat(bodyOf(read)).contains("\"customer\":\"" + THE_CUSTOMER + "\"");
  }

  @Test
  void a_publishable_key_confirms_a_payment_and_the_answer_is_reduced_the_same_way() {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse confirmed =
        confirmPayment(PUBLISHABLE_KEY, payment.id(), confirming(payment), Map.of());

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(membersOf(confirmed))
        .containsExactlyElementsOf(WHAT_A_PUBLISHABLE_KEY_SEES_OF_A_PAYMENT);
    assertThat(bodyOf(confirmed))
        .contains("\"status\":\"requires_capture\"")
        .contains("\"payment_method\":\"" + THE_CARD + "\"");
    assertThat(intents.find(RUN, payment.id()).orElseThrow().status())
        .isEqualTo(StripeIntentStatus.REQUIRES_CAPTURE);
  }

  @Test
  void a_publishable_key_reads_a_setup_and_a_secret_key_reads_it_whole() {
    theAccounts();
    Created setup = aSetup();

    StripeApiResponse publishable =
        get(PUBLISHABLE_KEY, SETUP_INTENTS + "/" + setup.id(), secretOf(setup), Map.of());
    StripeApiResponse secret = get(SECRET_KEY, SETUP_INTENTS + "/" + setup.id(), Map.of());

    assertThat(publishable.httpStatus()).isEqualTo(200);
    assertThat(membersOf(publishable))
        .containsExactlyElementsOf(WHAT_A_PUBLISHABLE_KEY_SEES_OF_A_SETUP);
    assertThat(bodyOf(publishable)).doesNotContain(THE_CUSTOMER);
    assertThat(secret.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(secret))
        .contains("\"object\":\"setup_intent\"")
        .contains("\"customer\":\"" + THE_CUSTOMER + "\"");
  }

  @Test
  void a_secret_key_reading_a_setup_this_run_does_not_hold_is_answered_resource_missing() {
    theAccounts();

    StripeApiResponse read =
        get(SECRET_KEY, SETUP_INTENTS + "/seti_sim_0000000000000099", Map.of());

    assertThat(read.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(read))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"intent\"");
  }

  @Test
  void a_publishable_key_confirms_a_setup_and_the_card_is_attached_to_the_customer() {
    theAccounts();
    Created setup = aSetup();

    StripeApiResponse confirmed =
        confirmSetup(PUBLISHABLE_KEY, setup.id(), confirming(setup), Map.of());

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(membersOf(confirmed))
        .containsExactlyElementsOf(WHAT_A_PUBLISHABLE_KEY_SEES_OF_A_SETUP);
    assertThat(bodyOf(confirmed)).contains("\"status\":\"succeeded\"");
    assertThat(paymentMethods.find(RUN, THE_CARD).orElseThrow().customerId())
        .isEqualTo(THE_CUSTOMER);
  }

  @Test
  void a_publishable_key_that_sends_no_client_secret_is_refused_on_the_missing_parameter() {
    theAccounts();
    Created payment = aPayment();
    Created setup = aSetup();

    StripeApiResponse noneOnAPayment =
        get(PUBLISHABLE_KEY, PAYMENT_INTENTS + "/" + payment.id(), Map.of());
    StripeApiResponse blankOnASetup =
        get(PUBLISHABLE_KEY, SETUP_INTENTS + "/" + setup.id(), Map.of("client_secret", " "));
    StripeApiResponse aHashOnAConfirm =
        confirmPayment(
            PUBLISHABLE_KEY,
            payment.id(),
            Map.of("client_secret[0]", payment.clientSecret(), "return_url", A_RETURN_URL),
            Map.of());

    for (StripeApiResponse refused : List.of(noneOnAPayment, blankOnASetup, aHashOnAConfirm)) {
      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(bodyOf(refused))
          .isEqualTo(
              "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"parameter_missing\","
                  + "\"doc_url\":\"https://docs.stripe.com/error-codes#parameter-missing\","
                  + "\"message\":\"Missing required param: client_secret.\","
                  + "\"param\":\"client_secret\"}}");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonsense", "pi_sim_0000000000000001_secret_bad-secret", ""})
  void a_client_secret_that_is_not_the_payments_own_is_refused_as_the_recording_answers(
      String sent) {
    theAccounts();
    Created payment = aPayment();
    Created another = aPayment();
    String wrong = sent.isEmpty() ? another.clientSecret() : sent;

    StripeApiResponse refused =
        get(PUBLISHABLE_KEY, PAYMENT_INTENTS + "/" + payment.id(), Map.of("client_secret", wrong));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\","
                + "\"code\":\"payment_intent_invalid_parameter\","
                + "\"doc_url\":\"https://docs.stripe.com/error-codes#payment-intent-invalid-parameter\","
                + "\"message\":\"The client_secret provided does not match the client_secret"
                + " associated with the PaymentIntent.\",\"param\":\"client_secret\"}}");
  }

  @Test
  void a_client_secret_that_is_not_the_setups_own_is_refused_with_the_setup_code() {
    theAccounts();
    Created setup = aSetup();
    Created payment = aPayment();

    StripeApiResponse refused =
        confirmSetup(
            PUBLISHABLE_KEY,
            setup.id(),
            Map.of("client_secret", payment.clientSecret(), "return_url", A_RETURN_URL),
            Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"setup_intent_invalid_parameter\"")
        .contains(
            "The client_secret provided does not match the client_secret associated with the"
                + " SetupIntent.")
        .contains("\"param\":\"client_secret\"");
    assertThat(setupIntents.find(RUN, setup.id()).orElseThrow().status())
        .isEqualTo(StripeSetupStatus.REQUIRES_CONFIRMATION);
  }

  @Test
  void an_intent_of_another_account_is_no_such_intent_to_a_publishable_key_even_with_its_secret() {
    theAccounts();
    Created payment = aPayment();
    Created setup = aSetup();

    StripeApiResponse payments =
        get(
            ANOTHER_PUBLISHABLE_KEY,
            PAYMENT_INTENTS + "/" + payment.id(),
            secretOf(payment),
            Map.of());
    StripeApiResponse setups =
        confirmSetup(ANOTHER_PUBLISHABLE_KEY, setup.id(), confirming(setup), Map.of());

    assertThat(payments.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(payments))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\","
                + "\"doc_url\":\"https://docs.stripe.com/error-codes#resource-missing\","
                + "\"message\":\"No such payment_intent: '"
                + payment.id()
                + "'\",\"param\":\"intent\"}}");
    assertThat(setups.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(setups))
        .contains("\"message\":\"No such setupintent: '" + setup.id() + "'\"");
    assertThat(setupIntents.find(RUN, setup.id()).orElseThrow().status())
        .isEqualTo(StripeSetupStatus.REQUIRES_CONFIRMATION);
  }

  @Test
  void a_secret_of_another_account_sent_for_this_accounts_intent_does_not_match_it() {
    theAccounts();
    Created mine = aPayment();
    Created theirs = aPaymentOf(ANOTHER_SECRET_KEY);

    StripeApiResponse refused =
        get(PUBLISHABLE_KEY, PAYMENT_INTENTS + "/" + mine.id(), secretOf(theirs), Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"code\":\"payment_intent_invalid_parameter\"");
  }

  @Test
  void an_intent_of_another_run_is_no_such_intent_to_the_publishable_key_of_that_run() {
    theAccounts();
    accounts.with(
        StripeDoubles.account(
            ANOTHER_RUN,
            ACCOUNT,
            ELSEWHERE_SECRET_KEY,
            THREE_DAYS,
            Map.of(StripeAccount.PUBLISHABLE_KEY_PROPERTY, ELSEWHERE_PUBLISHABLE_KEY)));
    Created payment = aPayment();

    StripeApiResponse refused =
        get(
            ELSEWHERE_PUBLISHABLE_KEY,
            PAYMENT_INTENTS + "/" + payment.id(),
            secretOf(payment),
            Map.of());

    assertThat(refused.testRunId()).isEqualTo(ANOTHER_RUN);
    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused)).contains("No such payment_intent: '" + payment.id() + "'");
  }

  @Test
  void
      a_publishable_key_on_any_other_route_is_refused_as_needing_a_secret_key_and_changes_nothing() {
    theAccounts();
    Created payment = aConfirmedHold();
    String charge = String.valueOf(intents.find(RUN, payment.id()).orElseThrow().latestCharge());
    StripeApi api = api(NOW);
    Map<String, List<String>> keyed = keyed(PUBLISHABLE_KEY);
    Map<String, String> carryingTheSecret = Map.of("client_secret", payment.clientSecret());

    List<StripeApiResponse> refused =
        List.of(
            api.capturePaymentIntent(
                post(keyed, PAYMENT_INTENTS + "/" + payment.id() + "/capture", carryingTheSecret),
                payment.id()),
            api.cancelPaymentIntent(
                post(keyed, PAYMENT_INTENTS + "/" + payment.id() + "/cancel", carryingTheSecret),
                payment.id()),
            api.createRefund(post(keyed, "/v1/refunds", Map.of("payment_intent", payment.id()))),
            api.retrieveCustomer(
                getting(PUBLISHABLE_KEY, "/v1/customers/" + THE_CUSTOMER, Map.of()), THE_CUSTOMER),
            api.listPaymentMethods(
                getting(PUBLISHABLE_KEY, "/v1/payment_methods", Map.of("customer", THE_CUSTOMER))),
            api.retrieveCharge(getting(PUBLISHABLE_KEY, "/v1/charges/" + charge, Map.of()), charge),
            api.createPaymentIntent(
                post(keyed, PAYMENT_INTENTS, Map.of("amount", "100", "currency", "pln"))));

    for (StripeApiResponse answer : refused) {
      assertThat(answer.httpStatus()).isEqualTo(403);
      assertThat(bodyOf(answer))
          .isEqualTo(
              "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"secret_key_required\","
                  + "\"doc_url\":\"https://docs.stripe.com/error-codes#secret-key-required\","
                  + "\"message\":\"The API key provided is a publishable key, but a secret key is"
                  + " required.\"}}");
      assertThat(answer.headers())
          .doesNotHave(aHeaderOfThisNameUnderAnySpelling(StripeFields.STRIPE_VERSION_HEADER));
    }
    StripePaymentIntent kept = intents.find(RUN, payment.id()).orElseThrow();
    assertThat(kept.status()).isEqualTo(StripeIntentStatus.REQUIRES_CAPTURE);
    assertThat(kept.ledger().operations()).hasSize(1);
    assertThat(intents.find(RUN, "pi_sim_0000000000000099")).isEmpty();
    StripeApiResponse refundedBySecretKey =
        api.createRefund(
            post(
                keyed(SECRET_KEY),
                "/v1/refunds",
                Map.of("payment_intent", payment.id(), "amount", "1")));
    assertThat(refundedBySecretKey.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(StripeFields.IDEMPOTENT_REPLAYED_HEADER));
    assertThat(bodyOf(refundedBySecretKey))
        .contains("\"code\":\"payment_intent_unexpected_state\"")
        .doesNotContain("idempotency_error");
  }

  @Test
  void a_secret_key_is_not_judged_on_a_client_secret_it_sends() {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse read =
        get(
            SECRET_KEY,
            PAYMENT_INTENTS + "/" + payment.id(),
            Map.of("client_secret", "pi_sim_0000000000000001_secret_not-its-own"));
    StripeApiResponse confirmed =
        confirmPayment(
            SECRET_KEY,
            payment.id(),
            Map.of(
                "client_secret", "nonsense",
                "payment_method", THE_CARD,
                "return_url", A_RETURN_URL),
            Map.of());

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(membersOf(read)).contains("customer");
    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed)).contains("\"status\":\"requires_capture\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {IOS_PINS, ANDROID_PINS, "2026-08-26.dahlia"})
  void a_publishable_key_is_answered_at_the_version_a_mobile_sdk_pins_or_one_answered(
      String version) {
    theAccounts();
    Created payment = aPayment();
    Created setup = aSetup();

    StripeApiResponse read =
        get(
            PUBLISHABLE_KEY,
            PAYMENT_INTENTS + "/" + payment.id(),
            secretOf(payment),
            Map.of(StripeFields.STRIPE_VERSION_HEADER, version));
    StripeApiResponse confirmed =
        confirmSetup(
            PUBLISHABLE_KEY,
            setup.id(),
            confirming(setup),
            Map.of(StripeFields.STRIPE_VERSION_HEADER, version));

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(read.headers()).containsEntry(StripeFields.STRIPE_VERSION_HEADER, List.of(version));
    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(confirmed.headers())
        .containsEntry(StripeFields.STRIPE_VERSION_HEADER, List.of(version));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2019-12-03", "2024-06-20", "2024-09-30.acacia"})
  void a_publishable_key_at_any_other_version_before_basil_is_refused(String version) {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse refused =
        get(
            PUBLISHABLE_KEY,
            PAYMENT_INTENTS + "/" + payment.id(),
            secretOf(payment),
            Map.of(StripeFields.STRIPE_VERSION_HEADER, version));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("Stripe-Version " + version);
  }

  @Test
  void a_secret_key_still_may_not_read_an_intent_at_a_mobile_sdks_version() {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse refused =
        get(
            SECRET_KEY,
            PAYMENT_INTENTS + "/" + payment.id(),
            Map.of(),
            Map.of(StripeFields.STRIPE_VERSION_HEADER, IOS_PINS));

    assertThat(refused.httpStatus()).isEqualTo(400);
  }

  @Test
  void an_authorization_that_expired_reads_as_cancelled_automatically_before_basil() {
    accounts.with(
        StripeDoubles.account(
            RUN,
            ACCOUNT,
            SECRET_KEY,
            ONE_HOUR,
            Map.of(StripeAccount.PUBLISHABLE_KEY_PROPERTY, PUBLISHABLE_KEY)));
    seedTheCustomersCard();
    Created payment = aConfirmedHold();
    String path = PAYMENT_INTENTS + "/" + payment.id();

    StripeApiResponse atTheMobileVersion =
        api(TWO_HOURS_ON)
            .retrievePaymentIntent(
                getting(
                    PUBLISHABLE_KEY,
                    path,
                    secretOf(payment),
                    Map.of(StripeFields.STRIPE_VERSION_HEADER, IOS_PINS)),
                payment.id());
    StripeApiResponse atTheAccountsDefault =
        api(TWO_HOURS_ON)
            .retrievePaymentIntent(
                getting(PUBLISHABLE_KEY, path, secretOf(payment), Map.of()), payment.id());

    assertThat(bodyOf(atTheMobileVersion))
        .contains("\"cancellation_reason\":\"automatic\"")
        .contains("\"status\":\"canceled\"");
    assertThat(bodyOf(atTheAccountsDefault)).contains("\"cancellation_reason\":\"expired\"");
  }

  @Test
  void a_publishable_key_expands_the_payment_method_and_reaches_no_customer_through_it() {
    theAccounts();
    Created payment = aConfirmedHold();
    Map<String, String> asked = new LinkedHashMap<>(secretOf(payment));
    asked.put("expand[0]", "payment_method.customer");
    asked.put("expand[1]", "customer");

    StripeApiResponse publishable =
        get(PUBLISHABLE_KEY, PAYMENT_INTENTS + "/" + payment.id(), asked, Map.of());
    StripeApiResponse secret =
        get(
            SECRET_KEY,
            PAYMENT_INTENTS + "/" + payment.id(),
            Map.of("expand[0]", "payment_method.customer"));

    assertThat(publishable.httpStatus()).isEqualTo(200);
    assertThat(membersOf(publishable)).doesNotContain("customer");
    assertThat(bodyOf(publishable))
        .contains("\"payment_method\":{\"id\":\"" + THE_CARD + "\"")
        .contains("\"customer\":\"" + THE_CUSTOMER + "\"")
        .doesNotContain("\"object\":\"customer\"");
    assertThat(bodyOf(secret)).contains("\"object\":\"customer\"");
  }

  @Test
  void a_declined_confirm_under_a_publishable_key_embeds_the_reduced_payment() {
    theAccounts();
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CONFIRM),
            new ScenarioAction.TerminalFailure(StripeDecline.INSUFFICIENT_FUNDS.scenarioCode()),
            null));
    Created payment = aPayment();

    StripeApiResponse declined =
        confirmPayment(PUBLISHABLE_KEY, payment.id(), confirming(payment), Map.of());

    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(bodyOf(declined))
        .contains("\"decline_code\":\"insufficient_funds\"")
        .contains("\"payment_intent\":{\"id\":\"" + payment.id() + "\"")
        .doesNotContain(THE_CUSTOMER)
        .doesNotContain("latest_charge")
        .doesNotContain("amount_received");
  }

  @Test
  void a_confirm_under_a_publishable_key_follows_the_return_url_rule_of_a_secret_key_confirm() {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse refused =
        confirmPayment(
            PUBLISHABLE_KEY,
            payment.id(),
            Map.of("client_secret", payment.clientSecret(), "payment_method", THE_CARD),
            Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"return_url\"").doesNotContain(THE_CUSTOMER);
  }

  @Test
  void a_publishable_key_repeating_its_idempotency_key_is_replayed_the_reduced_answer() {
    theAccounts();
    Created payment = aPayment();
    Map<String, String> confirming = confirming(payment);
    Map<String, String> theKey = Map.of(StripeFields.IDEMPOTENCY_KEY_HEADER, AN_IDEMPOTENCY_KEY);

    StripeApiResponse first = confirmPayment(SECRET_KEY, payment.id(), confirming, theKey);
    StripeApiResponse replayed = confirmPayment(PUBLISHABLE_KEY, payment.id(), confirming, theKey);
    StripeApiResponse again = confirmPayment(PUBLISHABLE_KEY, payment.id(), confirming, theKey);

    assertThat(membersOf(first)).contains("customer");
    assertThat(replayed.headers())
        .containsEntry(StripeFields.IDEMPOTENT_REPLAYED_HEADER, List.of("true"));
    assertThat(membersOf(replayed))
        .containsExactlyElementsOf(WHAT_A_PUBLISHABLE_KEY_SEES_OF_A_PAYMENT);
    assertThat(bodyOf(again)).isEqualTo(bodyOf(replayed));
    assertThat(intents.find(RUN, payment.id()).orElseThrow().ledger().operations()).hasSize(1);
  }

  @Test
  void the_trail_withholds_the_client_secret_a_publishable_key_sent() {
    theAccounts();
    Created payment = aPayment();

    get(PUBLISHABLE_KEY, PAYMENT_INTENTS + "/" + payment.id(), secretOf(payment), Map.of());
    confirmPayment(
        PUBLISHABLE_KEY,
        payment.id(),
        confirming(payment),
        Map.of(StripeFields.IDEMPOTENCY_KEY_HEADER, AN_IDEMPOTENCY_KEY));

    List<AuditRecord> inbound =
        audit.all().stream()
            .filter(row -> row.direction() == AuditDirection.INBOUND_REQUEST)
            .filter(row -> String.valueOf(row.path()).startsWith(PAYMENT_INTENTS + "/"))
            .toList();
    assertThat(inbound).hasSize(2);
    for (AuditRecord row : inbound) {
      assertThat(row.sanitizedBody())
          .containsEntry(StripeFields.CLIENT_SECRET, List.of(Redaction.REDACTED));
      assertThat(row.accountId()).isEqualTo(ACCOUNT);
    }
  }

  @Test
  void the_trail_names_the_account_a_secret_key_resolved_to_and_none_for_a_key_no_account_holds() {
    theAccounts();
    Created payment = aPayment();

    get(SECRET_KEY, PAYMENT_INTENTS + "/" + payment.id(), Map.of());
    get("sk_test_no_account_holds_this", PAYMENT_INTENTS + "/" + payment.id(), Map.of());

    List<AuditRecord> read =
        audit.all().stream()
            .filter(row -> (PAYMENT_INTENTS + "/" + payment.id()).equals(row.path()))
            .toList();
    assertThat(read).hasSize(4);
    assertThat(read.subList(0, 2)).extracting(AuditRecord::accountId).containsOnly(ACCOUNT);
    assertThat(read.subList(2, 4)).extracting(AuditRecord::accountId).containsOnlyNulls();
  }

  @Test
  void a_key_no_account_holds_is_refused_as_an_invalid_key() {
    theAccounts();
    Created payment = aPayment();

    StripeApiResponse refused =
        get(
            "pk_test_no_account_holds_this",
            PAYMENT_INTENTS + "/" + payment.id(),
            secretOf(payment),
            Map.of());

    assertThat(refused.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(refused)).doesNotContain("\"code\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {PUBLISHABLE_KEY, SECRET_KEY})
  void a_card_another_customer_holds_pays_for_no_intent_and_is_never_shown(String key) {
    theAccounts();
    aCardOfAnotherCustomer();
    Created payment = aPayment();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", payment.clientSecret());
    form.put("payment_method", ANOTHER_CUSTOMERS_CARD);
    form.put("return_url", A_RETURN_URL);
    form.put("expand[0]", "payment_method");

    StripeApiResponse refused = confirmPayment(key, payment.id(), form, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Payment methods"
                + " attached to other Customers cannot be used with this PaymentIntent. This"
                + " PaymentMethod is attached to a Customer other than the one this PaymentIntent"
                + " names, and provider stripe publishes that a PaymentMethod attached to a"
                + " Customer must match the customer set on the PaymentIntent.\","
                + "\"param\":\"payment_method\"}}");
    StripePaymentIntent kept = intents.find(RUN, payment.id()).orElseThrow();
    assertThat(kept.status()).isEqualTo(StripeIntentStatus.REQUIRES_PAYMENT_METHOD);
    assertThat(kept.latestCharge()).isNull();
  }

  @Test
  void a_card_some_customer_holds_pays_for_no_intent_that_names_no_customer() {
    theAccounts();
    Created payment =
        createdBy(
            api(NOW)
                .createPaymentIntent(
                    post(
                        bearer(SECRET_KEY),
                        PAYMENT_INTENTS,
                        Map.of("amount", "1099", "currency", "pln"))));

    StripeApiResponse refused =
        confirmPayment(PUBLISHABLE_KEY, payment.id(), confirming(payment), Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("This PaymentMethod is attached to a Customer and this PaymentIntent names none")
        .contains("\"param\":\"payment_method\"");
    assertThat(intents.find(RUN, payment.id()).orElseThrow().latestCharge()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {PUBLISHABLE_KEY, SECRET_KEY})
  void a_method_the_account_holds_none_of_is_refused_the_way_the_recording_answers(String key) {
    theAccounts();
    Created payment = aPayment();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", payment.clientSecret());
    form.put("payment_method", "pm_123");
    form.put("return_url", A_RETURN_URL);

    StripeApiResponse refused = confirmPayment(key, payment.id(), form, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .startsWith(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\","
                + "\"doc_url\":\"https://docs.stripe.com/error-codes#resource-missing\","
                + "\"message\":\"No such PaymentMethod: 'pm_123'; It's possible this"
                + " PaymentMethod exists on one of your connected accounts, in which case you"
                + " should retry this request on that connected account. Learn more at"
                + " https://stripe.com/docs/connect/authentication\",\"param\":\"payment_method\","
                + "\"payment_intent\":{\"id\":\""
                + payment.id()
                + "\"");
    assertThat(intents.find(RUN, payment.id()).orElseThrow().latestCharge()).isNull();
  }

  @Test
  void the_intent_an_unknown_method_refusal_embeds_is_reduced_for_a_publishable_key() {
    theAccounts();
    Created payment = aPayment();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", payment.clientSecret());
    form.put("payment_method", "pm_123");
    form.put("return_url", A_RETURN_URL);

    StripeApiResponse publishable = confirmPayment(PUBLISHABLE_KEY, payment.id(), form, Map.of());
    StripeApiResponse secret = confirmPayment(SECRET_KEY, payment.id(), form, Map.of());

    assertThat(bodyOf(publishable)).doesNotContain(THE_CUSTOMER);
    assertThat(bodyOf(secret)).contains("\"customer\":\"" + THE_CUSTOMER + "\"");
  }

  @Test
  void a_published_test_method_the_account_holds_none_of_pays_with_a_copy_of_its_card() {
    theAccounts();
    Created payment = aPayment();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", payment.clientSecret());
    form.put("payment_method", "pm_card_visa");
    form.put("return_url", A_RETURN_URL);

    StripeApiResponse confirmed = confirmPayment(PUBLISHABLE_KEY, payment.id(), form, Map.of());

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed)).contains("\"payment_method\":\"pm_sim_00000000000000000001\"");
    assertThat(paymentMethods.find(RUN, "pm_sim_00000000000000000001")).isPresent();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "pm_card_visa",
        "pm_card_chargeDeclinedExpiredCard",
        "pm_usBankAccount_success",
        "pm_sepaDebit_success_at"
      })
  void the_published_test_methods_are_those_of_the_published_stems(String published) {
    assertThat(StripePaymentMethodLookup.aPublishedTestMethod(published)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "pm_123",
        "pm_card",
        "card_visa",
        "pm_sepa_debit_at",
        "pm_usbankaccount_x",
        "pm_x_pm_card_visa"
      })
  void an_id_outside_the_published_stems_is_no_published_test_method(String named) {
    assertThat(StripePaymentMethodLookup.aPublishedTestMethod(named)).isFalse();
  }

  @Test
  void a_setup_that_names_no_customer_attaches_no_card_another_customer_holds() {
    theAccounts();
    Created setup =
        createdBy(api(NOW).createSetupIntent(post(bearer(SECRET_KEY), SETUP_INTENTS, Map.of())));

    StripeApiResponse refused =
        confirmSetup(PUBLISHABLE_KEY, setup.id(), confirming(setup), Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(
            "Payment methods attached to other Customers cannot be used with this SetupIntent."
                + " This PaymentMethod is attached to a Customer and this SetupIntent names none")
        .contains("\"param\":\"payment_method\"");
    assertThat(setupIntents.find(RUN, setup.id()).orElseThrow().status())
        .isEqualTo(StripeSetupStatus.REQUIRES_PAYMENT_METHOD);
  }

  @Test
  void a_setup_that_names_no_customer_attaches_a_card_nobody_holds() {
    theAccounts();
    paymentMethods.insertIfAbsent(
        new StripePaymentMethod(
            RUN,
            "pm_saved_to_nobody_yet",
            ACCOUNT,
            null,
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "4242", 12, 2030, "credit"),
            null,
            ObjectOrigin.CONTROL_API,
            NOW,
            NOW));
    Created setup =
        createdBy(api(NOW).createSetupIntent(post(bearer(SECRET_KEY), SETUP_INTENTS, Map.of())));
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", setup.clientSecret());
    form.put("payment_method", "pm_saved_to_nobody_yet");
    form.put("return_url", A_RETURN_URL);

    StripeApiResponse confirmed = confirmSetup(PUBLISHABLE_KEY, setup.id(), form, Map.of());

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed)).contains("\"status\":\"succeeded\"");
  }

  private void aCardOfAnotherCustomer() {
    customers.insertIfAbsent(
        new StripeCustomer(
            RUN, ANOTHER_CUSTOMER, ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
    paymentMethods.insertIfAbsent(
        new StripePaymentMethod(
            RUN,
            ANOTHER_CUSTOMERS_CARD,
            ACCOUNT,
            ANOTHER_CUSTOMER,
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "1111", 12, 2030, "credit"),
            null,
            ObjectOrigin.CONTROL_API,
            NOW,
            NOW));
  }

  private void theAccounts() {
    accounts.with(
        StripeDoubles.account(
            RUN,
            ACCOUNT,
            SECRET_KEY,
            THREE_DAYS,
            Map.of(StripeAccount.PUBLISHABLE_KEY_PROPERTY, PUBLISHABLE_KEY)));
    accounts.with(
        StripeDoubles.account(
            RUN,
            ANOTHER_ACCOUNT,
            ANOTHER_SECRET_KEY,
            THREE_DAYS,
            Map.of(StripeAccount.PUBLISHABLE_KEY_PROPERTY, ANOTHER_PUBLISHABLE_KEY)));
    seedTheCustomersCard();
  }

  private void seedTheCustomersCard() {
    customers.insertIfAbsent(
        new StripeCustomer(
            RUN, THE_CUSTOMER, ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
    paymentMethods.insertIfAbsent(
        new StripePaymentMethod(
            RUN,
            THE_CARD,
            ACCOUNT,
            THE_CUSTOMER,
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "4242", 12, 2030, "credit"),
            null,
            ObjectOrigin.CONTROL_API,
            NOW,
            NOW));
  }

  private Created aPayment() {
    return aPaymentOf(SECRET_KEY);
  }

  private Created aPaymentOf(String secretKey) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("customer", THE_CUSTOMER);
    return createdBy(api(NOW).createPaymentIntent(post(bearer(secretKey), PAYMENT_INTENTS, form)));
  }

  private Created aConfirmedHold() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("customer", THE_CUSTOMER);
    form.put("payment_method", THE_CARD);
    form.put("confirm", "true");
    form.put("return_url", A_RETURN_URL);
    return createdBy(api(NOW).createPaymentIntent(post(bearer(SECRET_KEY), PAYMENT_INTENTS, form)));
  }

  private Created aSetup() {
    return createdBy(
        api(NOW)
            .createSetupIntent(
                post(
                    bearer(SECRET_KEY),
                    SETUP_INTENTS,
                    Map.of("customer", THE_CUSTOMER, "payment_method", THE_CARD))));
  }

  private static Created createdBy(StripeApiResponse response) {
    assertThat(response.httpStatus()).isEqualTo(200);
    String body = bodyOf(response);
    String marker = "\"client_secret\":\"";
    int from = body.indexOf(marker) + marker.length();
    return new Created(
        String.valueOf(response.providerObjectId()), body.substring(from, body.indexOf('"', from)));
  }

  private static Map<String, String> secretOf(Created intent) {
    return Map.of("client_secret", intent.clientSecret());
  }

  private static Map<String, String> confirming(Created intent) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", intent.clientSecret());
    form.put("payment_method", THE_CARD);
    form.put("return_url", A_RETURN_URL);
    return form;
  }

  private StripeApiResponse get(String key, String path, Map<String, String> query) {
    return get(key, path, query, Map.of());
  }

  private StripeApiResponse get(
      String key, String path, Map<String, String> query, Map<String, String> headers) {
    String id = path.substring(path.lastIndexOf('/') + 1);
    StripeApiCall call = getting(key, path, query, headers);
    return path.startsWith(SETUP_INTENTS)
        ? api(NOW).retrieveSetupIntent(call, id)
        : api(NOW).retrievePaymentIntent(call, id);
  }

  private StripeApiResponse confirmPayment(
      String key, String intentId, Map<String, String> form, Map<String, String> headers) {
    return api(NOW)
        .confirmPaymentIntent(
            post(withHeaders(key, headers), PAYMENT_INTENTS + "/" + intentId + "/confirm", form),
            intentId);
  }

  private StripeApiResponse confirmSetup(
      String key, String setupIntentId, Map<String, String> form, Map<String, String> headers) {
    return api(NOW)
        .confirmSetupIntent(
            post(withHeaders(key, headers), SETUP_INTENTS + "/" + setupIntentId + "/confirm", form),
            setupIntentId);
  }

  private static StripeApiCall getting(String key, String path, Map<String, String> query) {
    return getting(key, path, query, Map.of());
  }

  private static StripeApiCall getting(
      String key, String path, Map<String, String> query, Map<String, String> headers) {
    return new StripeApiCall(
        "GET", path, withHeaders(key, headers), Map.of(), fieldsOf(new LinkedHashMap<>(query)));
  }

  private static StripeApiCall post(
      Map<String, List<String>> headers, String path, Map<String, String> form) {
    return new StripeApiCall("POST", path, headers, fieldsOf(new LinkedHashMap<>(form)));
  }

  private static Map<String, List<String>> keyed(String key) {
    return withHeaders(key, Map.of(StripeFields.IDEMPOTENCY_KEY_HEADER, AN_IDEMPOTENCY_KEY));
  }

  private static Map<String, List<String>> withHeaders(String key, Map<String, String> headers) {
    Map<String, List<String>> all = new LinkedHashMap<>(bearer(key));
    headers.forEach((name, value) -> all.put(name, List.of(value)));
    return all;
  }

  private static List<String> membersOf(StripeApiResponse response) {
    return List.copyOf(
        ((StripeJson.JsonObject) StripeJsonReader.read(bodyOf(response))).members().keySet());
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
        intents,
        charges,
        refunds,
        new CreateSetupIntent(setupIntents, paymentMethods, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, ids, scenarioEngine));
  }

  private record Created(String id, String clientSecret) {}
}
