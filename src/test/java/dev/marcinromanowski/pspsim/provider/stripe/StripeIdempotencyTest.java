package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeIdempotencyTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String OTHER_ACCOUNT = "merchant-2";
  private static final String OTHER_SECRET_KEY = "sk_test_51Other";

  private static final String CREATE_PATH = "/v1/payment_intents";
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";
  private static final String SECOND_INTENT = "pi_sim_0000000000000003";

  private static final String KEY = "8f1a1e2c-0f1a-4f2b-9c3d-5a6b7c8d9e01";

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
  void replays_the_whole_answer_of_the_first_request_made_under_a_key() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    StripeApiResponse first = api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse replay = api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(replay.httpStatus()).isEqualTo(first.httpStatus());
    assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
    assertThat(replay.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(replay.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void a_replayed_answer_names_its_headers_in_one_order_whatever_the_process() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse replay = api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(replay.headers().keySet())
        .as(
            "a client cannot tell this simulator from the provider by a difference that moves,"
                + " and a header order drawn from an unordered copy varies per process")
        .containsExactly("Idempotent-Replayed", "Request-Id", "Content-Type");
  }

  @Test
  void replays_a_failed_answer_as_faithfully_as_a_successful_one() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    declining(StripeOperations.CREATE, StripeDecline.INSUFFICIENT_FUNDS);
    StripeApi api = api(NOW);
    StripeApiResponse first = api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse replay = api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(first.httpStatus()).isEqualTo(402);
    assertThat(replay.httpStatus()).isEqualTo(402);
    assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
    assertThat(replay.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void a_replayed_refusal_names_the_intent_the_first_refusal_named() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    declining(StripeOperations.CREATE, StripeDecline.INSUFFICIENT_FUNDS);
    StripeApi api = api(NOW);
    StripeApiResponse first = api.createPaymentIntent(create(hold("1099"), KEY));

    api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(theRefusalsAnswered())
        .containsExactly(
            RefusalSubject.theObject(first.providerObjectId()),
            RefusalSubject.theObject(first.providerObjectId()));
  }

  private List<RefusalSubject> theRefusalsAnswered() {
    return audit.all().stream()
        .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .filter(record -> record.outcome() == ProviderOutcome.FAILURE)
        .map(AuditRecord::refusal)
        .toList();
  }

  @Test
  void answers_a_key_reused_with_other_parameters_as_an_idempotency_error() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse reused = api.createPaymentIntent(create(hold("2000"), KEY));

    assertThat(reused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(reused)).contains("\"type\":\"idempotency_error\"");
    assertThat(reused.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void answers_a_key_reused_on_another_endpoint_as_an_idempotency_error() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse reused =
        api.capturePaymentIntent(
            new StripeApiCall(
                "POST",
                CREATE_PATH + "/" + FIRST_INTENT + "/capture",
                headers(SECRET_KEY, KEY),
                fields(Map.of())),
            FIRST_INTENT);

    assertThat(reused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(reused)).contains("\"type\":\"idempotency_error\"");
  }

  @Test
  void answers_a_key_another_request_is_still_executing_under_with_a_conflict() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    keys.heldInProgress(E2E_RUN, ACCOUNT, KEY, "whatever-the-first-request-sent");

    StripeApiResponse response = api(NOW).createPaymentIntent(create(hold("1099"), KEY));

    assertThat(response.httpStatus()).isEqualTo(409);
    assertThat(bodyOf(response))
        .contains("\"type\":\"idempotency_error\"")
        .contains("\"code\":\"idempotency_key_in_use\"");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void runs_the_request_again_once_the_key_has_aged_out_of_the_published_window() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    api(NOW).createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse afterTheWindow =
        api(NOW.plus(StripeIdempotency.WINDOW).plus(Duration.ofMinutes(1)))
            .createPaymentIntent(create(hold("1099"), KEY));

    assertThat(afterTheWindow.providerObjectId()).isEqualTo(SECOND_INTENT);
    assertThat(afterTheWindow.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
    assertThat(intents.all()).hasSize(2);
  }

  @Test
  void keeps_a_key_that_is_still_inside_the_published_window() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    api(NOW).createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse insideTheWindow =
        api(NOW.plus(StripeIdempotency.WINDOW).minus(Duration.ofMinutes(1)))
            .createPaymentIntent(create(hold("1099"), KEY));

    assertThat(insideTheWindow.providerObjectId()).isEqualTo(FIRST_INTENT);
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void ignores_the_header_on_a_retrieval_because_only_post_accepts_a_key() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));
    keys.clearAll();

    StripeApiResponse read =
        api.retrievePaymentIntent(
            new StripeApiCall(
                "GET", CREATE_PATH + "/" + FIRST_INTENT, headers(SECRET_KEY, KEY), Map.of()),
            FIRST_INTENT);

    assertThat(read.httpStatus()).isEqualTo(200);
    assertThat(read.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
    assertThat(keys.size()).isZero();
  }

  @Test
  void keeps_no_answer_for_a_request_refused_before_the_endpoint_began_executing() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    StripeApiResponse refused = api.createPaymentIntent(create(Map.of("currency", "pln"), KEY));

    StripeApiResponse corrected = api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(corrected.httpStatus()).isEqualTo(200);
    assertThat(corrected.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void keeps_no_answer_for_a_call_a_fault_answered_instead_of_the_operation() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));
    answeringInsteadOfTheOperation(StripeOperations.CAPTURE);
    String captureKey = KEY + "-capture";

    StripeApiResponse lost = capture(api, captureKey);
    scenarioRules.clear(StripeApi.STRIPE, E2E_RUN);
    StripeApiResponse repeated = capture(api, captureKey);

    assertThat(lost.httpStatus()).isEqualTo(502);
    assertThat(repeated.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(repeated)).contains("\"status\":\"succeeded\"");
    assertThat(repeated.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
  }

  @Test
  void replays_an_answer_whose_delivery_a_fault_damaged_rather_than_the_damaged_bytes() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));
    droppingTheConnectionOn(StripeOperations.CAPTURE);
    String captureKey = KEY + "-capture";

    StripeApiResponse lost = capture(api, captureKey);
    StripeApiResponse replay = capture(api, captureKey);

    assertThat(lost.declaredContentLength()).isGreaterThan(lost.body().length);
    assertThat(replay.declaredContentLength()).isEqualTo(replay.body().length);
    assertThat(bodyOf(replay)).contains("\"status\":\"succeeded\"");
    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
  }

  @Test
  void refuses_a_key_longer_than_the_published_bound() {
    accounts.with(account(SECRET_KEY, ACCOUNT));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(
                create(hold("1099"), "k".repeat(StripeIdempotency.LONGEST_KEY + 1)));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response)).contains("\"type\":\"invalid_request_error\"");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void accepts_a_key_of_exactly_the_published_bound() {
    accounts.with(account(SECRET_KEY, ACCOUNT));

    StripeApiResponse response =
        api(NOW)
            .createPaymentIntent(create(hold("1099"), "k".repeat(StripeIdempotency.LONGEST_KEY)));

    assertThat(response.httpStatus()).isEqualTo(200);
  }

  @Test
  void keeps_one_key_of_two_accounts_apart() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    accounts.with(account(OTHER_SECRET_KEY, OTHER_ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse other =
        api.createPaymentIntent(
            new StripeApiCall(
                "POST", CREATE_PATH, headers(OTHER_SECRET_KEY, KEY), fields(hold("1099"))));

    assertThat(other.providerObjectId()).isEqualTo(SECOND_INTENT);
    assertThat(other.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
  }

  @Test
  void reads_the_key_from_the_header_whatever_case_the_client_chose() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));

    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("authorization", List.of("Bearer " + SECRET_KEY));
    headers.put("idempotency-key", List.of(KEY));
    StripeApiResponse replay =
        api.createPaymentIntent(
            new StripeApiCall("POST", CREATE_PATH, headers, fields(hold("1099"))));

    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
  }

  @Test
  void compares_the_parameters_of_a_reused_key_whatever_order_they_were_sent_in() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));

    Map<String, String> reordered = new LinkedHashMap<>();
    List<String> names = new ArrayList<>(hold("1099").keySet());
    for (int i = names.size() - 1; i >= 0; i--) {
      reordered.put(names.get(i), hold("1099").get(names.get(i)));
    }
    StripeApiResponse replay = api.createPaymentIntent(create(reordered, KEY));

    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
  }

  @Test
  void frees_the_key_of_a_call_the_simulator_could_not_answer_at_all() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    intents.rejectingTheNextInsert();

    StripeApiCall creating = create(hold("1099"), KEY);

    assertThatThrownBy(() -> api.createPaymentIntent(creating))
        .isInstanceOf(IllegalStateException.class);
    StripeApiResponse repeated = api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(repeated.httpStatus()).isEqualTo(200);
    assertThat(repeated.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
  }

  @Test
  void answers_a_request_it_did_not_hold_back_without_a_delay() {
    accounts.with(account(SECRET_KEY, ACCOUNT));

    StripeApiResponse response = api(NOW).createPaymentIntent(create(hold("1099"), KEY));

    assertThat(response.delay()).isEqualTo(Duration.ZERO);
    assertThat(response.declaredContentLength()).isEqualTo(response.body().length);
  }

  @Test
  void records_the_replayed_header_on_the_trail_as_well_as_on_the_answer() {
    accounts.with(account(SECRET_KEY, ACCOUNT));
    StripeApi api = api(NOW);
    api.createPaymentIntent(create(hold("1099"), KEY));

    api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(audit.all().get(audit.all().size() - 1).sanitizedHeaders())
        .containsEntry("Idempotent-Replayed", List.of("true"))
        .containsEntry("Content-Type", List.of("application/json"));
  }

  private StripeApiResponse capture(StripeApi api, String key) {
    return api.capturePaymentIntent(
        new StripeApiCall(
            "POST",
            CREATE_PATH + "/" + FIRST_INTENT + "/capture",
            headers(SECRET_KEY, key),
            fields(Map.of())),
        FIRST_INTENT);
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

  private void answeringInsteadOfTheOperation(String operation) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));
  }

  private void droppingTheConnectionOn(String operation) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()),
            null));
  }

  private static StripeAccount account(String secretKey, String accountId) {
    return StripeDoubles.account(E2E_RUN, accountId, secretKey, "P3D");
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
    return new StripeApiCall("POST", CREATE_PATH, headers(SECRET_KEY, key), fields(form));
  }

  private static Map<String, List<String>> headers(String secretKey, String key) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer " + secretKey));
    headers.put("Idempotency-Key", List.of(key));
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
