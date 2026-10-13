package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.Disclosure;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class StripeRequestIdentityTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";

  private static final String CREATE_PATH = "/v1/payment_intents";

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
  void names_the_request_a_caller_would_quote_to_support_on_an_answer_that_succeeded() {
    accounts.with(account());

    StripeApiResponse created = api().createPaymentIntent(create(hold("1099"), KEY));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(requestIdOf(created)).startsWith("req_sim_");
  }

  @Test
  void names_the_request_a_caller_would_quote_to_support_on_an_answer_that_failed() {
    StripeApiResponse refused = api().createPaymentIntent(create(hold("1099"), KEY));

    assertThat(refused.httpStatus()).isEqualTo(401);
    assertThat(requestIdOf(refused)).startsWith("req_sim_");
  }

  @Test
  void tells_two_requests_apart_so_a_caller_can_say_which_of_them_it_is_reporting() {
    accounts.with(account());
    StripeApi api = api();

    StripeApiResponse first = api.createPaymentIntent(createUnkeyed(hold("1099")));
    StripeApiResponse second = api.createPaymentIntent(createUnkeyed(hold("1099")));

    assertThat(requestIdOf(first)).isNotEqualTo(requestIdOf(second));
  }

  @Test
  void holds_the_identifier_it_gave_the_caller_on_the_trail_so_the_request_can_be_found_again() {
    accounts.with(account());

    StripeApiResponse created = api().createPaymentIntent(create(hold("1099"), KEY));

    assertThat(audit.all().get(audit.all().size() - 1).sanitizedHeaders())
        .containsEntry("Request-Id", List.of(requestIdOf(created)));
  }

  @Test
  void answers_a_replay_under_an_identifier_of_its_own_rather_than_the_first_requests() {
    accounts.with(account());
    StripeApi api = api();
    StripeApiResponse first = api.createPaymentIntent(create(hold("1099"), KEY));

    StripeApiResponse replay = api.createPaymentIntent(create(hold("1099"), KEY));

    assertThat(replay.headers().get("Idempotent-Replayed")).containsExactly("true");
    assertThat(requestIdOf(replay)).isNotEqualTo(requestIdOf(first));
  }

  @Test
  void mints_an_identifier_that_can_never_be_read_as_one_of_this_providers_object_ids() {
    accounts.with(account());

    String requestId = requestIdOf(api().createPaymentIntent(create(hold("1099"), KEY)));

    for (String minted :
        List.of(
            CreatePaymentIntent.ID_PREFIX,
            CreateRefund.ID_PREFIX,
            CreateCustomer.ID_PREFIX,
            CreateSetupIntent.ID_PREFIX,
            StripeChargeMinting.ID_PREFIX,
            CreateEphemeralKey.ID_PREFIX)) {
      assertThat(requestId).doesNotStartWith(minted);
      assertThat(minted).doesNotStartWith(StripeFields.REQUEST_ID_PREFIX);
    }
  }

  @Test
  void
      a_trail_row_and_the_request_fields_a_screen_lists_from_it_keep_the_twelve_parameters_a_caller_sent_in_the_order_it_sent_them() {
    accounts.with(account());
    Map<String, String> sent = aHoldOfTwelveParameters();

    api().createPaymentIntent(create(sent, KEY));

    AuditRecord inbound = audit.of(StripeApi.STRIPE, E2E_RUN).get(0);
    assertThat(inbound.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(sent).hasSize(12);
    assertThat(inbound.sanitizedBody().keySet())
        .as(
            "twelve parameters have 479001600 orders, so a copy carrying no order of its own"
                + " landing on the order the caller sent is not something one run does by chance")
        .containsExactlyElementsOf(sent.keySet());
    assertThat(Disclosure.of(inbound.sanitizedBody(), false))
        .as("the trail screen lists the request fields of that row in the same order")
        .extracting(Disclosure.Field::name)
        .containsExactlyElementsOf(sent.keySet());
  }

  @Test
  void
      a_form_read_out_of_a_call_hands_its_parameters_over_as_a_sequenced_map_no_caller_can_write_to_even_though_the_call_itself_carries_an_unmodifiable_map_no_type_can_check() {
    StripeApiCall call = create(aHoldOfTwelveParameters(), KEY);

    assertThat(StripeForm.of(call.form()).values()).isInstanceOf(SequencedMap.class);
    assertThat(StripeForm.of(call.form()).forAudit()).isInstanceOf(SequencedMap.class);
    assertThatThrownBy(() -> StripeForm.of(call.form()).values().put("extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static Map<String, String> aHoldOfTwelveParameters() {
    Map<String, String> form = hold("1099");
    form.put("description", "IT Books. Qty: 2");
    form.put("receipt_email", "payer@merchant.example");
    form.put("statement_descriptor", "SIMULATOR");
    form.put("statement_descriptor_suffix", "BOOKS");
    form.put("metadata[order_id]", "order-99");
    form.put("metadata[cart_id]", "cart-7");
    form.put("transfer_group", "group-1");
    return form;
  }

  private static String requestIdOf(StripeApiResponse response) {
    List<String> carried = response.headers().get("Request-Id");
    assertThat(carried).hasSize(1);
    return carried.get(0);
  }

  private static StripeAccount account() {
    return StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D");
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
    Map<String, List<String>> headers = authorized();
    headers.put("Idempotency-Key", List.of(key));
    return new StripeApiCall("POST", CREATE_PATH, headers, fields(form));
  }

  private static StripeApiCall createUnkeyed(Map<String, String> form) {
    return new StripeApiCall("POST", CREATE_PATH, authorized(), fields(form));
  }

  private static Map<String, List<String>> authorized() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Authorization", List.of("Bearer " + SECRET_KEY));
    return headers;
  }

  private static Map<String, List<String>> fields(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }
}
