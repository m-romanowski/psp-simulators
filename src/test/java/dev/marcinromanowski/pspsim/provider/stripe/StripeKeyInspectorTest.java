package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StripeKeyInspectorTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOON = Instant.parse("2026-09-15T12:00:00Z");
  private static final Instant A_MINUTE_LATER = NOON.plus(Duration.ofMinutes(1));

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String CREATE_PATH = "/v1/payment_intents";
  private static final String SETUP_PATH = "/v1/setup_intents";
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";
  private static final String KEY = "8f1a1e2c-0f1a-4f2b-9c3d-5a6b7c8d9e01";
  private static final String CARD = "pm_card_visa";
  private static final String CAPTURE_KEY = "8f1a1e2c-0f1a-4f2b-9c3d-5a6b7c8d9e02";
  private static final String CONFIRM_KEY = "8f1a1e2c-0f1a-4f2b-9c3d-5a6b7c8d9e03";
  private static final String SETUP_KEY = "8f1a1e2c-0f1a-4f2b-9c3d-5a6b7c8d9e04";
  private static final String THE_CUSTOMER = "cus_the_one_saving_a_card";
  private static final String THE_CARD = "pm_the_card_being_set_up";
  private static final String FIRST_SETUP = "seti_sim_0000000000000001";

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
  void a_tester_who_replays_a_request_sees_one_first_request_and_one_replay_of_its_answer() {
    accounts.with(anAccount());
    StripeApiResponse first = api(NOON).createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse replay = api(A_MINUTE_LATER).createPaymentIntent(create(hold("1099"), KEY));

    KeyedExchange exchange = theOnlyExchange();
    assertThat(exchange.keyName()).isEqualTo(StripeFields.IDEMPOTENCY_KEY_HEADER);
    assertThat(exchange.keyValue()).isEqualTo(KEY);
    assertThat(exchange.role()).isEqualTo(KeyRole.TRUE_IDEMPOTENCY_KEY);
    assertThat(exchange.operation()).isEqualTo(StripeOperations.CREATE);
    assertThat(exchange.firstRequestAt()).isEqualTo(NOON);
    assertThat(exchange.replayTimestamps()).containsExactly(A_MINUTE_LATER);
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY);

    KeyedRequest original = exchange.firstRequest();
    KeyedRequest replayed = exchange.repeats().get(0);
    assertThat(original.body()).contains("\"id\":[\"" + FIRST_INTENT + "\"]");
    assertThat(replayed.body()).isEqualTo(original.body());
    assertThat(original.httpStatus()).isEqualTo(first.httpStatus()).isEqualTo(replay.httpStatus());
    assertThat(original.stateChanged()).isTrue();
    assertThat(replayed.stateChanged()).isFalse();
    assertThat(exchange.conflictReasonOf(replayed)).isNull();
    assertThat(original.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void neither_row_of_a_replayed_creation_shows_the_client_secret_the_wire_answered() {
    accounts.with(anAccount());
    StripeApiResponse first = api(NOON).createPaymentIntent(create(hold("1099"), KEY));
    api(A_MINUTE_LATER).createPaymentIntent(create(hold("1099"), KEY));

    KeyedExchange exchange = theOnlyExchange();
    String clientSecret = theClientSecretIn(bodyOf(first));
    assertThat(clientSecret).startsWith(FIRST_INTENT + "_secret_");
    assertThat(exchange.requests())
        .hasSize(2)
        .allSatisfy(
            request ->
                assertThat(request.body())
                    .doesNotContain(clientSecret)
                    .contains("\"client_secret\":[\"" + Redaction.REDACTED + "\"]"));
  }

  @Test
  void a_replay_that_sent_other_parameters_shows_the_conflict_and_never_a_second_answer() {
    accounts.with(anAccount());
    api(NOON).createPaymentIntent(create(hold("1099"), KEY));

    api(A_MINUTE_LATER).createPaymentIntent(create(hold("2000"), KEY));

    KeyedExchange exchange = theOnlyExchange();
    KeyedRequest conflicting = exchange.repeats().get(0);
    assertThat(conflicting.verdict()).isEqualTo(ExchangeVerdict.CONFLICTING_PAYLOAD);
    assertThat(exchange.conflictReasonOf(conflicting))
        .contains(conflicting.fingerprint())
        .contains(exchange.fingerprint())
        .contains("parameters the first request did not");
    assertThat(conflicting.fingerprint()).isNotEqualTo(exchange.fingerprint());
    assertThat(conflicting.body()).contains("idempotency_error");
    assertThat(conflicting.stateChanged()).isFalse();
    assertThat(exchange.replayTimestamps()).isEmpty();
  }

  @Test
  void a_key_reused_on_another_endpoint_shows_the_conflict_as_the_endpoint_it_named() {
    accounts.with(anAccount());
    StripeApi api = api(NOON);
    api.createPaymentIntent(create(hold("1099"), KEY));

    api.capturePaymentIntent(
        new StripeApiCall(
            "POST", CREATE_PATH + "/" + FIRST_INTENT + "/capture", headers(KEY), Map.of()),
        FIRST_INTENT);

    KeyedExchange exchange = theOnlyExchange();
    KeyedRequest conflicting = exchange.repeats().get(0);
    assertThat(conflicting.operation()).isEqualTo(StripeOperations.CAPTURE);
    assertThat(exchange.conflictReasonOf(conflicting))
        .contains(StripeOperations.CAPTURE)
        .contains(StripeOperations.CREATE)
        .contains("not the same request under one key");
  }

  @Test
  void
      a_declined_payment_is_replayed_as_faithfully_as_a_successful_one_and_the_inspector_says_so() {
    accounts.with(anAccount());
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CREATE),
            new ScenarioAction.TerminalFailure(StripeDecline.INSUFFICIENT_FUNDS.scenarioCode()),
            null));
    api(NOON).createPaymentIntent(create(hold("1099"), KEY));

    api(A_MINUTE_LATER).createPaymentIntent(create(hold("1099"), KEY));

    KeyedExchange exchange = theOnlyExchange();
    assertThat(exchange.firstRequest().httpStatus()).isEqualTo(402);
    assertThat(exchange.repeats().get(0).httpStatus()).isEqualTo(402);
    assertThat(exchange.repeats().get(0).body()).isEqualTo(exchange.firstRequest().body());
    assertThat(exchange.replayTimestamps()).containsExactly(A_MINUTE_LATER);
  }

  @Test
  void a_capture_refused_for_more_than_the_hold_is_recorded_as_having_changed_nothing() {
    accounts.with(anAccount());
    StripeApi api = api(NOON);
    api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse refused =
        api.capturePaymentIntent(capture("9999", CAPTURE_KEY), FIRST_INTENT);

    assertThat(refused.httpStatus()).isEqualTo(400);
    KeyedExchange exchange = theExchangeUnder(CAPTURE_KEY);
    KeyedRequest capture = exchange.firstRequest();
    assertThat(capture.verdict()).isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(capture.operation()).isEqualTo(StripeOperations.CAPTURE);
    assertThat(capture.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(capture.stateChanged()).isFalse();
    assertThat(exchange.stateChanged()).isFalse();
  }

  @Test
  void a_declined_creation_that_stored_the_payment_is_recorded_as_having_changed_state() {
    accounts.with(anAccount());
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CREATE),
            new ScenarioAction.TerminalFailure(StripeDecline.INSUFFICIENT_FUNDS.scenarioCode()),
            null));

    StripeApiResponse declined = api(NOON).createPaymentIntent(create(hold("1099"), KEY));

    assertThat(declined.httpStatus()).isEqualTo(402);
    KeyedRequest recorded = theOnlyExchange().firstRequest();
    assertThat(recorded.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(recorded.stateChanged()).isTrue();
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void a_confirmation_a_rule_declined_recorded_the_failed_attempt_and_says_state_changed() {
    accounts.with(anAccount());
    decliningEvery(StripeOperations.CONFIRM);
    api(NOON).createPaymentIntent(create(aHoldAwaitingConfirmation(), KEY));

    StripeApiResponse declined =
        api(A_MINUTE_LATER).confirmPaymentIntent(confirmation(CONFIRM_KEY), FIRST_INTENT);

    assertThat(declined.httpStatus()).isEqualTo(402);
    KeyedRequest recorded = theExchangeUnder(CONFIRM_KEY).firstRequest();
    assertThat(recorded.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(recorded.stateChanged()).isTrue();
    assertThat(charges.all())
        .singleElement()
        .satisfies(minted -> assertThat(minted.status()).isEqualTo(StripeChargeStatus.FAILED));
  }

  @Test
  void a_setup_confirmation_a_rule_declined_moved_the_setup_and_says_state_changed() {
    accounts.with(anAccount());
    customers.insertIfAbsent(aCustomer());
    paymentMethods.insertIfAbsent(anUnattachedCard());
    decliningEvery(StripeOperations.CONFIRM_SETUP_INTENT);
    api(NOON).createSetupIntent(aSetupIntentCall());

    StripeApiResponse declined =
        api(A_MINUTE_LATER).confirmSetupIntent(setupConfirmation(SETUP_KEY), FIRST_SETUP);

    assertThat(declined.httpStatus()).isEqualTo(402);
    KeyedRequest recorded = theExchangeUnder(SETUP_KEY).firstRequest();
    assertThat(recorded.providerObjectId()).isEqualTo(FIRST_SETUP);
    assertThat(recorded.stateChanged()).isTrue();
    assertThat(setupIntents.all())
        .singleElement()
        .satisfies(
            moved ->
                assertThat(moved.status()).isEqualTo(StripeSetupStatus.REQUIRES_PAYMENT_METHOD));
  }

  @Test
  void a_request_that_carried_no_key_leaves_the_inspector_with_nothing_to_show() {
    accounts.with(anAccount());
    Map<String, List<String>> withoutAKey = new LinkedHashMap<>();
    withoutAKey.put("Authorization", List.of("Bearer " + SECRET_KEY));

    api(NOON)
        .createPaymentIntent(
            new StripeApiCall("POST", CREATE_PATH, withoutAKey, fields(hold("1099"))));

    assertThat(exchanges()).isEmpty();
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void
      a_request_refused_before_the_endpoint_ran_is_not_recorded_as_a_first_request_under_the_key() {
    accounts.with(anAccount());

    api(NOON).createPaymentIntent(create(Map.of("currency", "pln"), KEY));

    assertThat(exchanges()).isEmpty();
  }

  @Test
  void provider_stripe_declares_the_header_it_replays_under_and_says_it_refuses_no_repeat() {
    assertThat(provider().keyMechanisms()).isSameAs(StripeKeyMechanisms.MECHANISMS);
    assertThat(StripeKeyMechanisms.MECHANISMS.has(KeyRole.TRUE_IDEMPOTENCY_KEY)).isTrue();
    assertThat(StripeKeyMechanisms.MECHANISMS.roleOf(StripeFields.IDEMPOTENCY_KEY_HEADER))
        .isEqualTo(KeyRole.TRUE_IDEMPOTENCY_KEY);
    assertThat(StripeKeyMechanisms.MECHANISMS.has(KeyRole.DUPLICATE_DETECTION_KEY)).isFalse();
    assertThat(StripeKeyMechanisms.MECHANISMS.theAbsenceOf(KeyRole.DUPLICATE_DETECTION_KEY))
        .contains("refuses no request for repeating a merchant's own reference");
    assertThat(StripeKeyMechanisms.MECHANISMS.inTheRoleOf(KeyRole.CORRELATION_KEY))
        .singleElement()
        .satisfies(key -> assertThat(key.name()).isEqualTo(StripeKeyMechanisms.PAYMENT_INTENT_ID));
  }

  @Test
  void
      provider_stripe_declares_its_header_claimed_before_the_operation_and_its_intent_id_never_held() {
    assertThat(
            StripeKeyMechanisms.MECHANISMS.claimOf(StripeFields.IDEMPOTENCY_KEY_HEADER).canBeHeld())
        .isTrue();
    assertThat(
            StripeKeyMechanisms.MECHANISMS
                .claimOf(StripeFields.IDEMPOTENCY_KEY_HEADER)
                .describedAs())
        .contains("idempotency_key_in_use");
    assertThat(StripeKeyMechanisms.MECHANISMS.claimOf(StripeKeyMechanisms.PAYMENT_INTENT_ID))
        .isInstanceOf(KeyClaim.NeverHeldByARequest.class);
  }

  @Test
  void
      a_second_request_that_met_the_key_still_held_is_recorded_under_that_key_and_not_only_answered()
          throws Exception {
    TwoAtOnce race = aSecondRequestWhileTheFirstStillHoldsTheKey();

    assertThat(race.raced().httpStatus()).isEqualTo(409);
    assertThat(theOnlyExchange().requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(
            ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD, ExchangeVerdict.FIRST_REQUEST);
  }

  @Test
  void the_row_of_a_request_refused_while_the_key_was_held_carries_the_refusal_the_wire_answered()
      throws Exception {
    aSecondRequestWhileTheFirstStillHoldsTheKey();

    KeyedRequest refused = theOnlyExchange().repeats().get(0);
    assertThat(refused.httpStatus()).isEqualTo(409);
    assertThat(refused.body()).contains("\"error.code\":[\"idempotency_key_in_use\"]");
    assertThat(refused.operation()).isEqualTo(StripeOperations.CREATE);
    assertThat(refused.at()).isEqualTo(A_MINUTE_LATER);
  }

  @Test
  void a_request_refused_while_the_key_was_held_names_no_object_and_reports_no_state_change()
      throws Exception {
    aSecondRequestWhileTheFirstStillHoldsTheKey();

    KeyedRequest refused = theOnlyExchange().repeats().get(0);
    assertThat(refused.providerObjectId()).isNull();
    assertThat(refused.stateChanged()).isFalse();
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void the_first_request_of_the_key_is_the_one_that_held_it_and_not_the_refusal_recorded_before_it()
      throws Exception {
    TwoAtOnce race = aSecondRequestWhileTheFirstStillHoldsTheKey();

    KeyedExchange exchange = theOnlyExchange();
    assertThat(exchange.firstRequest().verdict()).isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(exchange.firstRequest().httpStatus()).isEqualTo(race.held().httpStatus());
    assertThat(exchange.firstRequest().providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(exchange.firstRequestAt()).isEqualTo(NOON);
    assertThat(exchange.stateChanged()).isTrue();
  }

  @Test
  void a_replay_after_a_refusal_while_the_key_was_held_replays_the_answer_the_holder_stored()
      throws Exception {
    aSecondRequestWhileTheFirstStillHoldsTheKey();

    StripeApiResponse replay = api(A_MINUTE_LATER).createPaymentIntent(create(hold("1099"), KEY));

    KeyedExchange exchange = theOnlyExchange();
    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
    assertThat(exchange.repeats())
        .filteredOn(request -> request.verdict() == ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY)
        .singleElement()
        .satisfies(
            replayed -> assertThat(replayed.body()).isEqualTo(exchange.firstRequest().body()));
  }

  private record TwoAtOnce(StripeApiResponse held, StripeApiResponse raced) {}

  private TwoAtOnce aSecondRequestWhileTheFirstStillHoldsTheKey() throws Exception {
    accounts.with(anAccount());
    CountDownLatch theHolderMayFinish = new CountDownLatch(1);
    CountDownLatch theHolderIsInsideTheOperation =
        intents.holdingTheNextInsertUntil(theHolderMayFinish);
    ExecutorService threads = Executors.newSingleThreadExecutor();
    try {
      Future<StripeApiResponse> holding =
          threads.submit(() -> api(NOON).createPaymentIntent(create(hold("1099"), KEY)));
      assertThat(theHolderIsInsideTheOperation.await(10, TimeUnit.SECONDS)).isTrue();
      StripeApiResponse raced = api(A_MINUTE_LATER).createPaymentIntent(create(hold("1099"), KEY));
      theHolderMayFinish.countDown();
      return new TwoAtOnce(holding.get(10, TimeUnit.SECONDS), raced);
    } finally {
      threads.shutdownNow();
    }
  }

  private static String theClientSecretIn(String body) {
    int at = body.indexOf("\"client_secret\":\"") + "\"client_secret\":\"".length();
    return body.substring(at, body.indexOf('"', at));
  }

  private KeyedExchange theExchangeUnder(String keyValue) {
    List<KeyedExchange> found =
        exchanges().stream().filter(exchange -> exchange.keyValue().equals(keyValue)).toList();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private KeyedExchange theOnlyExchange() {
    List<KeyedExchange> found = exchanges();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private List<KeyedExchange> exchanges() {
    return provider()
        .keyedExchanges(new KeyedExchangeQuery(E2E_RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 50))
        .exchanges();
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
        StripeDoubles.clockAt(NOON),
        StripeDoubles.noDeliveries(accounts),
        new StripeWebhooks(
            intents,
            refunds,
            customers,
            paymentMethods,
            setupIntents,
            accounts,
            StripeDoubles.noDeliveries(accounts),
            StripeDoubles.clockAt(NOON),
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

  private static StripeAccount anAccount() {
    return StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D");
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
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine));
  }

  private static Map<String, String> hold(String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", CARD);
    form.put("confirm", "true");
    return form;
  }

  private static StripeApiCall create(Map<String, String> form, String key) {
    return new StripeApiCall("POST", CREATE_PATH, headers(key), fields(form));
  }

  private void decliningEvery(String operation) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TerminalFailure(StripeDecline.INSUFFICIENT_FUNDS.scenarioCode()),
            null));
  }

  private static Map<String, String> aHoldAwaitingConfirmation() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("confirm", "false");
    return form;
  }

  private static StripeApiCall confirmation(String key) {
    return new StripeApiCall(
        "POST",
        CREATE_PATH + "/" + FIRST_INTENT + "/confirm",
        headers(key),
        fields(Map.of(StripeFields.PAYMENT_METHOD, CARD)));
  }

  private static StripeApiCall aSetupIntentCall() {
    return new StripeApiCall(
        "POST",
        SETUP_PATH,
        headers(SETUP_KEY + "-create"),
        fields(Map.of(StripeFields.CUSTOMER, THE_CUSTOMER, StripeFields.PAYMENT_METHOD, THE_CARD)));
  }

  private static StripeApiCall setupConfirmation(String key) {
    return new StripeApiCall(
        "POST", SETUP_PATH + "/" + FIRST_SETUP + "/confirm", headers(key), Map.of());
  }

  private static StripeCustomer aCustomer() {
    return new StripeCustomer(
        E2E_RUN, THE_CUSTOMER, ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOON, NOON);
  }

  private static StripePaymentMethod anUnattachedCard() {
    return new StripePaymentMethod(
        E2E_RUN,
        THE_CARD,
        ACCOUNT,
        null,
        StripePaymentMethodType.CARD,
        new FixtureCard("visa", "4242", 12, 2030, "credit"),
        null,
        ObjectOrigin.CONTROL_API,
        NOON,
        NOON);
  }

  private static StripeApiCall capture(String amount, String key) {
    return new StripeApiCall(
        "POST",
        CREATE_PATH + "/" + FIRST_INTENT + "/capture",
        headers(key),
        fields(Map.of(StripeFields.AMOUNT_TO_CAPTURE, amount)));
  }

  private static Map<String, List<String>> headers(String key) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer " + SECRET_KEY));
    headers.put(StripeFields.IDEMPOTENCY_KEY_HEADER, List.of(key));
    return headers;
  }

  private static Map<String, List<String>> fields(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static String bodyOf(StripeApiResponse response) {
    return new String(response.body(), UTF_8);
  }
}
