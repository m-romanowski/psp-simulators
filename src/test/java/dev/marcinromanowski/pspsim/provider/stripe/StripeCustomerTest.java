package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeCustomerTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant LATER = Instant.parse("2026-09-11T13:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String OTHER_ACCOUNT = "merchant-2";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String OTHER_SECRET_KEY = "sk_test_51Another";
  private static final String THREE_DAYS = "P3D";

  private static final String CUSTOMERS_PATH = "/v1/customers";
  private static final String FIRST_CUSTOMER = "cus_sim_0000000000000001";
  private static final String NO_CUSTOMER_OF_THIS_RUN = "cus_sim_0000000000000099";

  private static final String AN_EMAIL = "payer@example.test";
  private static final String ANOTHER_EMAIL = "second-payer@example.test";

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
  void a_tester_who_creates_a_customer_is_answered_the_object_stripe_publishes_for_one() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse response = api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.providerObjectId()).isEqualTo(FIRST_CUSTOMER);
    assertThat(bodyOf(response))
        .contains("\"id\":\"" + FIRST_CUSTOMER + "\"")
        .contains("\"object\":\"customer\"")
        .contains("\"email\":\"" + AN_EMAIL + "\"");
  }

  @Test
  void a_customer_created_without_an_email_carries_none_rather_than_being_refused() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse response = api(NOW).createCustomer(post(CUSTOMERS_PATH, Map.of()));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).contains("\"email\":null");
  }

  @Test
  void a_tester_retrieves_the_customer_they_created_under_the_id_the_creation_answered() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response = api(NOW).retrieveCustomer(get(FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"id\":\"" + FIRST_CUSTOMER + "\"")
        .contains("\"email\":\"" + AN_EMAIL + "\"");
  }

  @Test
  void retrieving_a_customer_this_run_holds_none_of_is_the_published_resource_missing_refusal() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse response =
        api(NOW).retrieveCustomer(get(NO_CUSTOMER_OF_THIS_RUN), NO_CUSTOMER_OF_THIS_RUN);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"customer\"")
        .contains("\"type\":\"invalid_request_error\"");
  }

  @Test
  void a_customer_of_another_account_is_not_readable_with_this_accounts_secret_key() {
    accounts
        .with(stripeAccount(ACCOUNT, SECRET_KEY))
        .with(stripeAccount(OTHER_ACCOUNT, OTHER_SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse response =
        api(NOW)
            .retrieveCustomer(
                new StripeApiCall(
                    "GET",
                    CUSTOMERS_PATH + "/" + FIRST_CUSTOMER,
                    bearer(OTHER_SECRET_KEY),
                    Map.of()),
                FIRST_CUSTOMER);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void a_tester_who_updates_the_email_reads_the_new_one_back() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse updated =
        api(LATER)
            .updateCustomer(
                post(customerPath(FIRST_CUSTOMER), withEmail(ANOTHER_EMAIL)), FIRST_CUSTOMER);

    assertThat(updated.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(updated)).contains("\"email\":\"" + ANOTHER_EMAIL + "\"");
    assertThat(bodyOf(api(LATER).retrieveCustomer(get(FIRST_CUSTOMER), FIRST_CUSTOMER)))
        .contains("\"email\":\"" + ANOTHER_EMAIL + "\"");
  }

  @Test
  void an_update_that_names_no_email_leaves_the_one_the_customer_already_had() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse updated =
        api(LATER).updateCustomer(post(customerPath(FIRST_CUSTOMER), Map.of()), FIRST_CUSTOMER);

    assertThat(updated.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(updated)).contains("\"email\":\"" + AN_EMAIL + "\"");
  }

  @Test
  void updating_a_customer_this_run_holds_none_of_is_the_published_resource_missing_refusal() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse response =
        api(NOW)
            .updateCustomer(
                post(customerPath(NO_CUSTOMER_OF_THIS_RUN), withEmail(AN_EMAIL)),
                NO_CUSTOMER_OF_THIS_RUN);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void deleting_a_customer_answers_the_deleted_object_stripe_publishes_for_one() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse deleted = api(LATER).deleteCustomer(delete(FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(deleted.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(deleted))
        .isEqualTo("{\"id\":\"" + FIRST_CUSTOMER + "\",\"object\":\"customer\",\"deleted\":true}");
  }

  @Test
  void a_deleted_customer_is_still_retrievable_so_a_test_can_read_its_history() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(LATER).deleteCustomer(delete(FIRST_CUSTOMER), FIRST_CUSTOMER);

    StripeApiResponse retrieved = api(LATER).retrieveCustomer(get(FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(retrieved.httpStatus()).isEqualTo(200);
    assertThat(retrieved.providerObjectId()).isEqualTo(FIRST_CUSTOMER);
    assertThat(bodyOf(retrieved)).contains("\"deleted\":true");
  }

  @Test
  void a_deleted_customer_refuses_the_update_that_would_be_a_further_operation_on_it() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(LATER).deleteCustomer(delete(FIRST_CUSTOMER), FIRST_CUSTOMER);

    StripeApiResponse refused =
        api(LATER)
            .updateCustomer(
                post(customerPath(FIRST_CUSTOMER), withEmail(ANOTHER_EMAIL)), FIRST_CUSTOMER);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(StripeCustomerObject.A_DELETED_CUSTOMER_TAKES_NO_FURTHER_OPERATION)
        .contains("\"type\":\"invalid_request_error\"");
    assertThat(bodyOf(api(LATER).retrieveCustomer(get(FIRST_CUSTOMER), FIRST_CUSTOMER)))
        .doesNotContain(ANOTHER_EMAIL);
  }

  @Test
  void a_deleted_customer_refuses_a_second_deletion() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    api(LATER).deleteCustomer(delete(FIRST_CUSTOMER), FIRST_CUSTOMER);

    StripeApiResponse refused = api(LATER).deleteCustomer(delete(FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(StripeCustomerObject.A_DELETED_CUSTOMER_TAKES_NO_FURTHER_OPERATION);
  }

  @Test
  void deleting_a_customer_this_run_holds_none_of_raises_the_error_stripe_publishes() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse response =
        api(NOW).deleteCustomer(delete(NO_CUSTOMER_OF_THIS_RUN), NO_CUSTOMER_OF_THIS_RUN);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void a_customer_deleted_while_this_update_was_deciding_is_refused_rather_than_written() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    customers.deletingTheCustomerOnTheNextWrite();

    StripeApiResponse refused =
        api(LATER)
            .updateCustomer(
                post(customerPath(FIRST_CUSTOMER), withEmail(ANOTHER_EMAIL)), FIRST_CUSTOMER);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(StripeCustomerObject.A_DELETED_CUSTOMER_TAKES_NO_FURTHER_OPERATION);
  }

  @Test
  void a_customer_reset_away_while_this_delete_was_deciding_reports_it_missing() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    customers.losingTheCustomerOnTheNextWrite();

    StripeApiResponse refused = api(LATER).deleteCustomer(delete(FIRST_CUSTOMER), FIRST_CUSTOMER);

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void a_customer_id_the_run_already_holds_is_redrawn_rather_than_answered_twice() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    customers.insertIfAbsent(
        new StripeCustomer(
            E2E_RUN, FIRST_CUSTOMER, ACCOUNT, null, null, ObjectOrigin.PROVIDER_API, NOW, NOW));

    StripeApiResponse response = api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.providerObjectId()).isEqualTo("cus_sim_0000000000000002");
  }

  @Test
  void a_creation_that_cannot_claim_an_id_gives_up_loudly_rather_than_looping() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    for (int drawn = 1; drawn <= CreateCustomer.ID_ATTEMPTS; drawn++) {
      customers.insertIfAbsent(
          new StripeCustomer(
              E2E_RUN,
              CreateCustomer.ID_PREFIX + String.format("%016x", drawn),
              ACCOUNT,
              null,
              null,
              ObjectOrigin.PROVIDER_API,
              NOW,
              NOW));
    }

    StripeApi api = api(NOW);
    StripeApiCall creating = post(CUSTOMERS_PATH, withEmail(AN_EMAIL));

    assertThatThrownBy(() -> api.createCustomer(creating))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(CreateCustomer.ID_ATTEMPTS));
  }

  @Test
  void a_customer_id_can_never_collide_with_an_intent_id_or_a_refund_id() {
    assertThat(CreateCustomer.ID_PREFIX).doesNotStartWith(CreatePaymentIntent.ID_PREFIX);
    assertThat(CreateCustomer.ID_PREFIX).doesNotStartWith(CreateRefund.ID_PREFIX);
    assertThat(CreatePaymentIntent.ID_PREFIX).doesNotStartWith(CreateCustomer.ID_PREFIX);
    assertThat(CreateRefund.ID_PREFIX).doesNotStartWith(CreateCustomer.ID_PREFIX);
  }

  @Test
  void a_repeated_creation_under_one_idempotency_key_answers_the_first_customer_again() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    StripeApiCall keyed = keyed(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));

    StripeApiResponse first = api(NOW).createCustomer(keyed);
    StripeApiResponse replayed = api(LATER).createCustomer(keyed);

    assertThat(first.providerObjectId()).isEqualTo(FIRST_CUSTOMER);
    assertThat(replayed.providerObjectId()).isEqualTo(FIRST_CUSTOMER);
    assertThat(bodyOf(replayed)).isEqualTo(bodyOf(first));
    assertThat(replayed.headers()).containsEntry("Idempotent-Replayed", List.of("true"));
    assertThat(customers.all()).hasSize(1);
  }

  @Test
  void a_delete_carries_no_replay_so_a_repeated_one_is_refused_rather_than_answered_again() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    api(NOW).createCustomer(post(CUSTOMERS_PATH, withEmail(AN_EMAIL)));
    StripeApiCall keyed = keyed(delete(FIRST_CUSTOMER));

    StripeApiResponse first = api(LATER).deleteCustomer(keyed, FIRST_CUSTOMER);
    StripeApiResponse second = api(LATER).deleteCustomer(keyed, FIRST_CUSTOMER);

    assertThat(first.httpStatus()).isEqualTo(200);
    assertThat(second.httpStatus()).isEqualTo(400);
    assertThat(second.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Idempotent-Replayed"));
  }

  private StripeAccount stripeAccount(String accountId, String secretKey) {
    return StripeDoubles.account(E2E_RUN, accountId, secretKey, THREE_DAYS);
  }

  private static String customerPath(String customerId) {
    return CUSTOMERS_PATH + "/" + customerId;
  }

  private static Map<String, String> withEmail(String email) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("email", email);
    return form;
  }

  private static StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall("POST", path, bearer(SECRET_KEY), fieldsOf(form));
  }

  private static StripeApiCall get(String customerId) {
    return new StripeApiCall("GET", customerPath(customerId), bearer(SECRET_KEY), Map.of());
  }

  private static StripeApiCall delete(String customerId) {
    return new StripeApiCall("DELETE", customerPath(customerId), bearer(SECRET_KEY), Map.of());
  }

  private static StripeApiCall keyed(StripeApiCall call) {
    Map<String, List<String>> headers = new LinkedHashMap<>(call.headers());
    headers.put("Idempotency-Key", List.of("a-key-a-client-retries-under"));
    return new StripeApiCall(call.method(), call.path(), headers, call.form());
  }

  private StripeApi api(Instant now) {
    ScenarioEngine noScenarios = new ScenarioEngine(new InMemoryScenarioRules());
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
            noScenarios,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new ConfirmPaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            noScenarios,
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CapturePaymentIntent(intents, noScenarios),
        new CancelPaymentIntent(intents, noScenarios),
        new RetrievePaymentIntent(intents),
        new RetrieveCharge(charges, intents),
        new CreateRefund(intents, refunds, charges, ids, noScenarios),
        customers,
        paymentMethods,
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(
            setupIntents, paymentMethods, customers, StripeDoubles.noScenarios()));
  }
}
