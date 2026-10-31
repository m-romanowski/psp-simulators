package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripeExpansionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-run");

  private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String OTHER_ACCOUNT = "merchant-2";
  private static final String OTHER_SECRET_KEY = "sk_test_51Another";
  private static final String THREE_DAYS = "P3D";

  private static final String THE_CUSTOMER = "cus_the_one_paying";
  private static final String THE_CARD = "pm_the_card_the_customer_saved";
  private static final String A_CARD_OF_NOBODY = "pm_the_card_saved_to_nobody";
  private static final String ANOTHER_ACCOUNTS_CUSTOMER = "cus_held_by_another_account";
  private static final String ANOTHER_RUNS_CUSTOMER = "cus_held_in_another_run";
  private static final String A_PUBLISHED_TEST_CARD = "pm_card_visa";

  private static final String FIRST_INTENT = "pi_sim_0000000000000001";
  private static final String FIRST_CHARGE = "ch_sim_0000000000000002";
  private static final String FIRST_REFUND = "re_sim_0000000000000003";
  private static final String SECOND_REFUND = "re_sim_0000000000000004";
  private static final String ANOTHER_CUSTOMER = "cus_the_other_one_paying";
  private static final String A_SECOND_CARD_OF_THE_CUSTOMER =
      "pm_the_second_card_the_customer_saved";
  private static final String THE_OTHER_CUSTOMERS_CARD = "pm_the_card_the_other_customer_saved";

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";
  private static final String PAYMENT_METHODS = "/v1/payment_methods";
  private static final String CUSTOMERS = "/v1/customers";
  private static final String SETUP_INTENTS = "/v1/setup_intents";

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
  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  @Test
  void a_retrieve_without_expand_answers_the_customer_the_method_and_the_charge_by_their_ids() {
    aHeldPaymentOfTheCustomer();

    Map<String, List<String>> answered = fieldsOf(retrieve(FIRST_INTENT, Map.of()));

    assertThat(answered)
        .containsEntry("customer", List.of(THE_CUSTOMER))
        .containsEntry("payment_method", List.of(THE_CARD))
        .containsEntry("latest_charge", List.of(FIRST_CHARGE))
        .doesNotContainKeys("customer.id", "payment_method.id", "latest_charge.id");
  }

  @Test
  void a_retrieve_expanding_the_customer_answers_the_customer_object_in_place_of_its_id() {
    aHeldPaymentOfTheCustomer();

    Map<String, List<String>> answered =
        fieldsOf(retrieve(FIRST_INTENT, Map.of("expand[]", List.of("customer"))));

    assertThat(answered)
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("customer.object", List.of("customer"))
        .containsEntry("customer.livemode", List.of("false"))
        .containsEntry("payment_method", List.of(THE_CARD))
        .containsEntry("latest_charge", List.of(FIRST_CHARGE))
        .doesNotContainKey("customer");
  }

  @Test
  void a_retrieve_expanding_the_payment_method_answers_the_payment_method_object() {
    aHeldPaymentOfTheCustomer();

    Map<String, List<String>> answered =
        fieldsOf(retrieve(FIRST_INTENT, Map.of("expand[]", List.of("payment_method"))));

    assertThat(answered)
        .containsEntry("payment_method.id", List.of(THE_CARD))
        .containsEntry("payment_method.object", List.of("payment_method"))
        .containsEntry("payment_method.card.last4", List.of("4242"))
        .containsEntry("payment_method.customer", List.of(THE_CUSTOMER))
        .containsEntry("customer", List.of(THE_CUSTOMER))
        .containsEntry("latest_charge", List.of(FIRST_CHARGE));
  }

  @Test
  void a_retrieve_expanding_the_latest_charge_answers_the_charge_object() {
    aHeldPaymentOfTheCustomer();

    Map<String, List<String>> answered =
        fieldsOf(retrieve(FIRST_INTENT, Map.of("expand[]", List.of("latest_charge"))));

    assertThat(answered)
        .containsEntry("latest_charge.id", List.of(FIRST_CHARGE))
        .containsEntry("latest_charge.object", List.of("charge"))
        .containsEntry("latest_charge.amount", List.of("1000"))
        .containsEntry("latest_charge.payment_intent", List.of(FIRST_INTENT))
        .containsEntry("customer", List.of(THE_CUSTOMER))
        .containsEntry("payment_method", List.of(THE_CARD));
  }

  @Test
  void a_retrieve_expanding_three_relations_at_once_answers_every_one_of_them_as_an_object() {
    aHeldPaymentOfTheCustomer();

    Map<String, List<String>> answered =
        fieldsOf(
            retrieve(
                FIRST_INTENT,
                Map.of(
                    "expand[0]",
                    List.of("customer"),
                    "expand[1]",
                    List.of("payment_method"),
                    "expand[2]",
                    List.of("latest_charge"))));

    assertThat(answered)
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("payment_method.id", List.of(THE_CARD))
        .containsEntry("latest_charge.id", List.of(FIRST_CHARGE))
        .doesNotContainKeys("customer", "payment_method", "latest_charge");
  }

  @Test
  void a_nested_path_expands_the_object_inside_the_object_it_expanded() {
    aHeldPaymentOfTheCustomer();

    Map<String, List<String>> answered =
        fieldsOf(
            retrieve(
                FIRST_INTENT,
                Map.of(
                    "expand[]",
                    List.of("latest_charge.payment_intent", "payment_method.customer"))));

    assertThat(answered)
        .containsEntry("latest_charge.id", List.of(FIRST_CHARGE))
        .containsEntry("latest_charge.payment_intent.id", List.of(FIRST_INTENT))
        .containsEntry("latest_charge.payment_intent.object", List.of("payment_intent"))
        .containsEntry("latest_charge.payment_intent.latest_charge", List.of(FIRST_CHARGE))
        .containsEntry("payment_method.id", List.of(THE_CARD))
        .containsEntry("payment_method.customer.id", List.of(THE_CUSTOMER))
        .containsEntry("payment_method.customer.object", List.of("customer"))
        .containsEntry("customer", List.of(THE_CUSTOMER));
  }

  @Test
  void a_path_of_four_levels_is_expanded_and_a_path_of_five_is_refused_naming_it() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse four =
        retrieve(
            FIRST_INTENT,
            Map.of(
                "expand[]", List.of("latest_charge.payment_intent.latest_charge.payment_intent")));
    StripeApiResponse five =
        retrieve(
            FIRST_INTENT,
            Map.of(
                "expand[]",
                List.of("latest_charge.payment_intent.latest_charge.payment_intent.customer")));

    assertThat(four.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(four))
        .containsEntry(
            "latest_charge.payment_intent.latest_charge.payment_intent.id", List.of(FIRST_INTENT))
        .containsEntry(
            "latest_charge.payment_intent.latest_charge.payment_intent.customer",
            List.of(THE_CUSTOMER));
    assertThat(five.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(five))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"This property cannot be"
                + " expanded"
                + " (latest_charge.payment_intent.latest_charge.payment_intent.customer)."
                + " Expansions have a maximum depth of four levels.\"}}");
  }

  @Test
  void a_relation_that_is_not_set_stays_null_when_it_is_asked_to_be_expanded() {
    anAccount();
    api().createPaymentIntent(post(PAYMENT_INTENTS, Map.of("amount", "1000", "currency", "pln")));

    Map<String, List<String>> answered =
        fieldsOf(
            retrieve(
                FIRST_INTENT,
                Map.of("expand[]", List.of("customer", "payment_method", "latest_charge"))));

    assertThat(answered)
        .containsEntry("customer", List.of())
        .containsEntry("payment_method", List.of())
        .containsEntry("latest_charge", List.of())
        .doesNotContainKeys("customer.id", "payment_method.id", "latest_charge.id");
  }

  @Test
  void the_appended_and_the_numbered_list_encodings_answer_the_same_expanded_body() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse appended =
        retrieve(FIRST_INTENT, Map.of("expand[]", List.of("customer", "latest_charge")));
    StripeApiResponse numbered =
        retrieve(
            FIRST_INTENT,
            Map.of("expand[0]", List.of("customer"), "expand[1]", List.of("latest_charge")));

    assertThat(fieldsOf(appended)).containsEntry("customer.id", List.of(THE_CUSTOMER));
    assertThat(bodyOf(numbered)).isEqualTo(bodyOf(appended));
  }

  @Test
  void a_get_reads_expand_from_its_query_and_not_from_a_body() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse inTheBody =
        api()
            .retrievePaymentIntent(
                new StripeApiCall(
                    "GET",
                    PAYMENT_INTENTS + "/" + FIRST_INTENT,
                    bearer(SECRET_KEY),
                    Map.of("expand[]", List.of("customer")),
                    Map.of("expand[]", List.of("payment_method"))),
                FIRST_INTENT);

    assertThat(inTheBody.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(inTheBody))
        .containsEntry("customer", List.of(THE_CUSTOMER))
        .containsEntry("payment_method.id", List.of(THE_CARD));
  }

  @Test
  void a_creation_reads_expand_from_its_body_and_answers_the_new_intent_expanded() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse created =
        api()
            .createPaymentIntent(
                post(
                    PAYMENT_INTENTS,
                    Map.of(
                        "amount",
                        "1000",
                        "currency",
                        "pln",
                        "customer",
                        THE_CUSTOMER,
                        "payment_method",
                        THE_CARD,
                        "return_url",
                        StripePaymentIntentTest.A_RETURN_URL,
                        "confirm",
                        "true",
                        "expand[0]",
                        "customer",
                        "expand[1]",
                        "latest_charge")));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(created))
        .containsEntry("id", List.of(FIRST_INTENT))
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("latest_charge.id", List.of(FIRST_CHARGE))
        .containsEntry("latest_charge.payment_intent", List.of(FIRST_INTENT))
        .containsEntry("payment_method", List.of(THE_CARD));
  }

  @Test
  void a_post_reads_expand_from_its_body_and_not_from_its_query() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse created =
        api()
            .createPaymentIntent(
                new StripeApiCall(
                    "POST",
                    PAYMENT_INTENTS,
                    bearer(SECRET_KEY),
                    formOf(
                        Map.of(
                            "amount",
                            "1000",
                            "currency",
                            "pln",
                            "customer",
                            THE_CUSTOMER,
                            "payment_method",
                            THE_CARD,
                            "expand[]",
                            "payment_method")),
                    Map.of("expand[]", List.of("customer"))));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(created))
        .containsEntry("customer", List.of(THE_CUSTOMER))
        .containsEntry("payment_method.id", List.of(THE_CARD));
  }

  @Test
  void a_confirmation_and_a_capture_each_answer_the_intent_expanded() {
    aCustomerAndTheCardSavedToIt();
    api()
        .createPaymentIntent(
            post(
                PAYMENT_INTENTS,
                Map.of(
                    "amount",
                    "1000",
                    "currency",
                    "pln",
                    "capture_method",
                    "manual",
                    "customer",
                    THE_CUSTOMER)));

    StripeApiResponse confirmed =
        api()
            .confirmPaymentIntent(
                post(
                    PAYMENT_INTENTS + "/" + FIRST_INTENT + "/confirm",
                    Map.of(
                        "payment_method",
                        THE_CARD,
                        "return_url",
                        StripePaymentIntentTest.A_RETURN_URL,
                        "expand[]",
                        "payment_method")),
                FIRST_INTENT);
    StripeApiResponse captured =
        api()
            .capturePaymentIntent(
                post(
                    PAYMENT_INTENTS + "/" + FIRST_INTENT + "/capture",
                    Map.of("amount_to_capture", "400", "expand[]", "latest_charge")),
                FIRST_INTENT);

    assertThat(fieldsOf(confirmed))
        .containsEntry("status", List.of("requires_capture"))
        .containsEntry("payment_method.id", List.of(THE_CARD))
        .containsEntry("latest_charge", List.of(FIRST_CHARGE));
    assertThat(fieldsOf(captured))
        .containsEntry("status", List.of("succeeded"))
        .containsEntry("latest_charge.id", List.of(FIRST_CHARGE))
        .containsEntry("latest_charge.amount_captured", List.of("400"))
        .containsEntry("payment_method", List.of(THE_CARD));
  }

  @Test
  void a_cancel_answers_the_cancelled_intent_with_its_customer_expanded() {
    aCustomerAndTheCardSavedToIt();
    api()
        .createPaymentIntent(
            post(
                PAYMENT_INTENTS,
                Map.of("amount", "1000", "currency", "pln", "customer", THE_CUSTOMER)));

    StripeApiResponse cancelled =
        api()
            .cancelPaymentIntent(
                post(
                    PAYMENT_INTENTS + "/" + FIRST_INTENT + "/cancel",
                    Map.of("expand[]", "customer")),
                FIRST_INTENT);

    assertThat(fieldsOf(cancelled))
        .containsEntry("status", List.of("canceled"))
        .containsEntry("customer.id", List.of(THE_CUSTOMER));
  }

  @Test
  void a_path_naming_a_property_the_object_does_not_have_is_refused_naming_the_path() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse unknown = retrieve(FIRST_INTENT, Map.of("expand[]", List.of("no_such")));
    StripeApiResponse nestedUnknown =
        retrieve(FIRST_INTENT, Map.of("expand[]", List.of("latest_charge.no_such.customer")));

    assertThat(unknown.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(unknown))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"This property cannot be"
                + " expanded because it doesn't exist: no_such\"}}");
    assertThat(nestedUnknown.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(nestedUnknown))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"This property cannot be"
                + " expanded because it doesn't exist: latest_charge.no_such\"}}");
  }

  @Test
  void a_property_the_object_has_and_does_not_publish_as_expandable_is_accepted_and_left_alone() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse answered =
        retrieve(
            FIRST_INTENT,
            Map.of("expand[]", List.of("amount", "metadata", "latest_charge.payment_method")));

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(answered))
        .containsEntry("amount", List.of("1000"))
        .containsEntry("latest_charge.id", List.of(FIRST_CHARGE))
        .containsEntry("latest_charge.payment_method", List.of(THE_CARD))
        .doesNotContainKeys("metadata", "latest_charge.payment_method.id");
  }

  @Test
  void one_refused_path_refuses_the_request_whether_it_is_sent_first_in_the_middle_or_last() {
    aHeldPaymentOfTheCustomer();

    for (List<String> paths :
        List.of(
            List.of("", "customer", "latest_charge"),
            List.of("customer", "", "latest_charge"),
            List.of("customer", "latest_charge", ""))) {
      StripeApiResponse refused = retrieve(FIRST_INTENT, Map.of("expand[]", paths));

      assertThat(refused.httpStatus()).as(paths.toString()).isEqualTo(400);
      assertThat(bodyOf(refused))
          .as(paths.toString())
          .contains("\"message\":\"This property cannot be expanded because it doesn't exist: \"");
    }
  }

  @Test
  void a_creation_whose_expand_is_refused_creates_no_intent() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse refused =
        api()
            .createPaymentIntent(
                post(
                    PAYMENT_INTENTS,
                    Map.of("amount", "1000", "currency", "pln", "expand[]", "no_such")));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("This property cannot be expanded because it doesn't exist: no_such");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void
      a_published_expandable_field_this_simulator_holds_nothing_under_is_accepted_and_left_alone() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse answered =
        retrieve(
            FIRST_INTENT,
            Map.of(
                "expand[]",
                List.of("review", "customer.test_clock", "payment_method.card", "on_behalf_of")));

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(answered))
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("payment_method.id", List.of(THE_CARD))
        .doesNotContainKeys("review", "on_behalf_of", "customer.test_clock");
  }

  @Test
  void a_customer_of_another_account_of_the_run_is_never_expanded() {
    aCustomerAndTheCardSavedToIt();
    accounts.with(StripeDoubles.account(E2E_RUN, OTHER_ACCOUNT, OTHER_SECRET_KEY, THREE_DAYS));
    customers.insertIfAbsent(aCustomerOf(E2E_RUN, ANOTHER_ACCOUNTS_CUSTOMER, OTHER_ACCOUNT));
    api()
        .createPaymentIntent(
            post(
                PAYMENT_INTENTS,
                Map.of(
                    "amount",
                    "1000",
                    "currency",
                    "pln",
                    "customer",
                    ANOTHER_ACCOUNTS_CUSTOMER,
                    "payment_method",
                    THE_CARD)));

    Map<String, List<String>> answered =
        fieldsOf(retrieve(FIRST_INTENT, Map.of("expand[]", List.of("customer", "payment_method"))));

    assertThat(answered)
        .containsEntry("customer", List.of(ANOTHER_ACCOUNTS_CUSTOMER))
        .containsEntry("payment_method.id", List.of(THE_CARD))
        .doesNotContainKey("customer.id");
  }

  @Test
  void a_customer_held_only_by_another_run_is_never_expanded() {
    aCustomerAndTheCardSavedToIt();
    customers.insertIfAbsent(aCustomerOf(ANOTHER_RUN, ANOTHER_RUNS_CUSTOMER, ACCOUNT));
    api()
        .createPaymentIntent(
            post(
                PAYMENT_INTENTS,
                Map.of(
                    "amount",
                    "1000",
                    "currency",
                    "pln",
                    "customer",
                    ANOTHER_RUNS_CUSTOMER,
                    "payment_method",
                    THE_CARD)));

    Map<String, List<String>> answered =
        fieldsOf(retrieve(FIRST_INTENT, Map.of("expand[]", List.of("customer", "payment_method"))));

    assertThat(answered)
        .containsEntry("customer", List.of(ANOTHER_RUNS_CUSTOMER))
        .containsEntry("payment_method.id", List.of(THE_CARD))
        .doesNotContainKey("customer.id");
  }

  @Test
  void a_payment_method_this_run_holds_no_object_for_is_answered_by_its_id() {
    aCustomerAndTheCardSavedToIt();
    api()
        .createPaymentIntent(
            post(
                PAYMENT_INTENTS,
                Map.of(
                    "amount",
                    "1000",
                    "currency",
                    "pln",
                    "customer",
                    THE_CUSTOMER,
                    "payment_method",
                    A_PUBLISHED_TEST_CARD,
                    "return_url",
                    StripePaymentIntentTest.A_RETURN_URL,
                    "confirm",
                    "true")));

    Map<String, List<String>> answered =
        fieldsOf(retrieve(FIRST_INTENT, Map.of("expand[]", List.of("payment_method", "customer"))));

    assertThat(answered)
        .containsEntry("payment_method", List.of(A_PUBLISHED_TEST_CARD))
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .doesNotContainKey("payment_method.id");
  }

  @Test
  void a_deleted_customer_is_expanded_as_the_deleted_customer_object() {
    aHeldPaymentOfTheCustomer();
    api().deleteCustomer(delete(CUSTOMERS + "/" + THE_CUSTOMER), THE_CUSTOMER);

    Map<String, List<String>> answered =
        fieldsOf(retrieve(FIRST_INTENT, Map.of("expand[]", List.of("customer"))));

    assertThat(answered)
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("customer.object", List.of("customer"))
        .containsEntry("customer.deleted", List.of("true"))
        .doesNotContainKeys("customer.created", "customer.email");
  }

  @Test
  void a_declined_creation_answers_the_intent_inside_the_error_by_the_ids_it_names() {
    aCustomerAndTheCardSavedToIt();
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CREATE),
            new ScenarioAction.TerminalFailure(StripeDecline.GENERIC_DECLINE.scenarioCode()),
            null));

    StripeApiResponse declined =
        api()
            .createPaymentIntent(
                post(
                    PAYMENT_INTENTS,
                    Map.of(
                        "amount",
                        "1000",
                        "currency",
                        "pln",
                        "customer",
                        THE_CUSTOMER,
                        "payment_method",
                        THE_CARD,
                        "return_url",
                        StripePaymentIntentTest.A_RETURN_URL,
                        "confirm",
                        "true",
                        "expand[]",
                        "customer")));

    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(fieldsOf(declined))
        .containsEntry("error.payment_intent.id", List.of(FIRST_INTENT))
        .containsEntry("error.payment_intent.customer", List.of(THE_CUSTOMER))
        .doesNotContainKey("error.payment_intent.customer.id");
  }

  @Test
  void a_charge_retrieve_expands_its_payment_intent_and_the_customer_inside_it() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse retrieved =
        api()
            .retrieveCharge(
                get(
                    "/v1/charges/" + FIRST_CHARGE,
                    Map.of("expand[]", List.of("payment_intent.customer"))),
                FIRST_CHARGE);

    assertThat(fieldsOf(retrieved))
        .containsEntry("id", List.of(FIRST_CHARGE))
        .containsEntry("payment_intent.id", List.of(FIRST_INTENT))
        .containsEntry("payment_intent.customer.id", List.of(THE_CUSTOMER))
        .containsEntry("payment_intent.payment_method", List.of(THE_CARD))
        .containsEntry("payment_method", List.of(THE_CARD));
  }

  @Test
  void a_charge_answers_its_payment_method_as_an_id_because_a_charge_does_not_expand_it() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse retrieved =
        api()
            .retrieveCharge(
                get("/v1/charges/" + FIRST_CHARGE, Map.of("expand[]", List.of("payment_method"))),
                FIRST_CHARGE);

    assertThat(retrieved.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(retrieved))
        .containsEntry("payment_method", List.of(THE_CARD))
        .doesNotContainKey("payment_method.id");
  }

  @Test
  void a_charge_answers_its_customer_and_expands_it_as_the_published_example_does() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse plain =
        api().retrieveCharge(get("/v1/charges/" + FIRST_CHARGE, Map.of()), FIRST_CHARGE);
    StripeApiResponse expanded =
        api()
            .retrieveCharge(
                get(
                    "/v1/charges/" + FIRST_CHARGE,
                    Map.of("expand[]", List.of("customer", "payment_intent.customer"))),
                FIRST_CHARGE);

    assertThat(fieldsOf(plain)).containsEntry("customer", List.of(THE_CUSTOMER));
    assertThat(fieldsOf(expanded))
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("customer.object", List.of("customer"))
        .containsEntry("payment_intent.customer.id", List.of(THE_CUSTOMER));
  }

  @Test
  void a_charge_includes_its_refunds_as_a_list_only_when_asked_to() {
    aHeldPaymentOfTheCustomer();
    api()
        .capturePaymentIntent(
            post(PAYMENT_INTENTS + "/" + FIRST_INTENT + "/capture", Map.of()), FIRST_INTENT);
    api()
        .createRefund(post("/v1/refunds", Map.of("payment_intent", FIRST_INTENT, "amount", "300")));
    api()
        .createRefund(post("/v1/refunds", Map.of("payment_intent", FIRST_INTENT, "amount", "200")));

    StripeApiResponse plain =
        api().retrieveCharge(get("/v1/charges/" + FIRST_CHARGE, Map.of()), FIRST_CHARGE);
    StripeApiResponse included =
        api()
            .retrieveCharge(
                get("/v1/charges/" + FIRST_CHARGE, Map.of("expand[]", List.of("refunds"))),
                FIRST_CHARGE);

    assertThat(fieldsOf(plain)).doesNotContainKeys("refunds", "refunds.object");
    assertThat(fieldsOf(included))
        .containsEntry("refunds.object", List.of("list"))
        .containsEntry("refunds.data.id", List.of(SECOND_REFUND, FIRST_REFUND))
        .containsEntry("refunds.data.object", List.of("refund", "refund"))
        .containsEntry("refunds.data.amount", List.of("200", "300"))
        .containsEntry("refunds.data.charge", List.of(FIRST_CHARGE, FIRST_CHARGE))
        .containsEntry("refunds.has_more", List.of("false"))
        .containsEntry("refunds.url", List.of("/v1/charges/" + FIRST_CHARGE + "/refunds"));
  }

  @Test
  void a_charge_with_no_refund_includes_an_empty_list_and_a_path_inside_it_reaches_each_refund() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse none =
        api()
            .retrieveCharge(
                get("/v1/charges/" + FIRST_CHARGE, Map.of("expand[]", List.of("refunds"))),
                FIRST_CHARGE);
    api()
        .capturePaymentIntent(
            post(PAYMENT_INTENTS + "/" + FIRST_INTENT + "/capture", Map.of()), FIRST_INTENT);
    api()
        .createRefund(post("/v1/refunds", Map.of("payment_intent", FIRST_INTENT, "amount", "300")));
    StripeApiResponse nested =
        api()
            .retrieveCharge(
                get(
                    "/v1/charges/" + FIRST_CHARGE,
                    Map.of("expand[]", List.of("refunds.data.payment_intent"))),
                FIRST_CHARGE);
    StripeApiResponse refused =
        api()
            .retrieveCharge(
                get("/v1/charges/" + FIRST_CHARGE, Map.of("expand[]", List.of("refunds.charge"))),
                FIRST_CHARGE);

    assertThat(fieldsOf(none))
        .containsEntry("refunds.object", List.of("list"))
        .containsEntry("refunds.data", List.of())
        .doesNotContainKey("refunds.data.id");
    assertThat(fieldsOf(nested))
        .containsEntry("refunds.data.id", List.of(FIRST_REFUND))
        .containsEntry("refunds.data.payment_intent.id", List.of(FIRST_INTENT))
        .containsEntry("refunds.data.payment_intent.object", List.of("payment_intent"));
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(
            "This property cannot be expanded (refunds.charge). You may want to try expanding"
                + " 'refunds.data.charge' instead.");
  }

  @Test
  void a_charge_this_run_does_not_hold_is_refused_as_missing_whatever_it_asks_to_include() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse missing =
        api()
            .retrieveCharge(
                get("/v1/charges/ch_sim_none", Map.of("expand[]", List.of("refunds"))),
                "ch_sim_none");

    assertThat(missing.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(missing)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void a_refund_expands_the_charge_and_the_payment_intent_it_names() {
    aHeldPaymentOfTheCustomer();
    api()
        .capturePaymentIntent(
            post(PAYMENT_INTENTS + "/" + FIRST_INTENT + "/capture", Map.of()), FIRST_INTENT);

    StripeApiResponse refunded =
        api()
            .createRefund(
                post(
                    "/v1/refunds",
                    Map.of(
                        "payment_intent",
                        FIRST_INTENT,
                        "amount",
                        "300",
                        "expand[0]",
                        "charge.payment_intent",
                        "expand[1]",
                        "payment_intent.customer")));

    assertThat(refunded.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(refunded))
        .containsEntry("id", List.of(FIRST_REFUND))
        .containsEntry("charge.id", List.of(FIRST_CHARGE))
        .containsEntry("charge.amount_refunded", List.of("300"))
        .containsEntry("charge.payment_intent.id", List.of(FIRST_INTENT))
        .containsEntry("payment_intent.id", List.of(FIRST_INTENT))
        .containsEntry("payment_intent.customer.id", List.of(THE_CUSTOMER));
  }

  @Test
  void a_setup_intent_expands_its_customer_on_creation_and_its_payment_method_on_confirmation() {
    anAccount();
    customers.insertIfAbsent(aCustomerOf(E2E_RUN, THE_CUSTOMER, ACCOUNT));
    paymentMethods.insertIfAbsent(aCardOf(A_CARD_OF_NOBODY, null));

    StripeApiResponse created =
        api()
            .createSetupIntent(
                post(SETUP_INTENTS, Map.of("customer", THE_CUSTOMER, "expand[]", "customer")));
    String setupIntentId = String.valueOf(created.providerObjectId());
    StripeApiResponse confirmed =
        api()
            .confirmSetupIntent(
                post(
                    SETUP_INTENTS + "/" + setupIntentId + "/confirm",
                    Map.of(
                        "return_url",
                        StripePaymentIntentTest.A_RETURN_URL,
                        "payment_method",
                        A_CARD_OF_NOBODY,
                        "expand[0]",
                        "payment_method.customer",
                        "expand[1]",
                        "customer")),
                setupIntentId);

    assertThat(fieldsOf(created))
        .containsEntry("object", List.of("setup_intent"))
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("payment_method", List.of());
    assertThat(fieldsOf(confirmed))
        .containsEntry("status", List.of("succeeded"))
        .containsEntry("payment_method.id", List.of(A_CARD_OF_NOBODY))
        .containsEntry("payment_method.customer.id", List.of(THE_CUSTOMER))
        .containsEntry("customer.id", List.of(THE_CUSTOMER));
  }

  @Test
  void a_list_expands_a_relation_of_every_item_through_data_and_reads_each_customer_once() {
    aCustomerAndTheCardSavedToIt();
    customers.insertIfAbsent(aCustomerOf(E2E_RUN, ANOTHER_CUSTOMER, ACCOUNT));
    paymentMethods.insertIfAbsent(aCardOf(A_SECOND_CARD_OF_THE_CUSTOMER, THE_CUSTOMER));
    paymentMethods.insertIfAbsent(aCardOf(A_CARD_OF_NOBODY, null));
    paymentMethods.insertIfAbsent(aCardOf(THE_OTHER_CUSTOMERS_CARD, ANOTHER_CUSTOMER));
    int before = customers.lookups();

    StripeApiResponse listed =
        api()
            .listPaymentMethods(get(PAYMENT_METHODS, Map.of("expand[]", List.of("data.customer"))));

    assertThat(listed.httpStatus()).isEqualTo(200);
    Map<String, List<String>> answered = fieldsOf(listed);
    assertThat(answered).containsEntry("object", List.of("list"));
    assertThat(answered.get("data.id"))
        .containsExactlyInAnyOrder(
            THE_CARD, A_SECOND_CARD_OF_THE_CUSTOMER, A_CARD_OF_NOBODY, THE_OTHER_CUSTOMERS_CARD);
    assertThat(answered.get("data.customer.id"))
        .containsExactlyInAnyOrder(THE_CUSTOMER, THE_CUSTOMER, ANOTHER_CUSTOMER);
    assertThat(answered.get("data.customer.object")).containsOnly("customer").hasSize(3);
    assertThat(customers.lookups() - before).isEqualTo(2);
  }

  @Test
  void a_customers_list_expands_through_data_in_either_list_encoding() {
    aCustomerAndTheCardSavedToIt();
    String path = CUSTOMERS + "/" + THE_CUSTOMER + "/payment_methods";

    StripeApiResponse appended =
        api()
            .listCustomerPaymentMethods(
                get(path, Map.of("expand[]", List.of("data.customer"))), THE_CUSTOMER);
    StripeApiResponse numbered =
        api()
            .listCustomerPaymentMethods(
                get(path, Map.of("expand[0]", List.of("data.customer"))), THE_CUSTOMER);

    assertThat(fieldsOf(appended))
        .containsEntry("data.id", List.of(THE_CARD))
        .containsEntry("data.customer.id", List.of(THE_CUSTOMER))
        .containsEntry("data.customer.object", List.of("customer"));
    assertThat(bodyOf(numbered)).isEqualTo(bodyOf(appended));
  }

  @Test
  void a_list_refuses_a_path_that_does_not_begin_with_data_and_suggests_the_one_that_would() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse refused =
        api().listPaymentMethods(get(PAYMENT_METHODS, Map.of("expand[]", List.of("customer"))));
    StripeApiResponse inside =
        api().listPaymentMethods(get(PAYMENT_METHODS, Map.of("expand[]", List.of("data.no_such"))));
    StripeApiResponse ownMember =
        api().listPaymentMethods(get(PAYMENT_METHODS, Map.of("expand[]", List.of("has_more"))));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"This property cannot be"
                + " expanded (customer). You may want to try expanding 'data.customer'"
                + " instead.\"}}");
    assertThat(inside.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(inside))
        .contains("This property cannot be expanded because it doesn't exist: data.no_such");
    assertThat(ownMember.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(ownMember)).containsEntry("has_more", List.of("false"));
  }

  @Test
  void a_detached_method_answers_a_customer_of_null_however_it_is_asked_to_be_expanded() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse refused =
        api()
            .detachPaymentMethod(
                post(PAYMENT_METHODS + "/" + THE_CARD + "/detach", Map.of("expand[]", "no_such")),
                THE_CARD);
    StripeApiResponse detached =
        api()
            .detachPaymentMethod(
                post(PAYMENT_METHODS + "/" + THE_CARD + "/detach", Map.of("expand[]", "customer")),
                THE_CARD);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("This property cannot be expanded because it doesn't exist: no_such");
    assertThat(detached.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(detached))
        .containsEntry("id", List.of(THE_CARD))
        .containsEntry("customer", List.of());
  }

  @Test
  void a_customer_includes_what_it_publishes_as_includable_only_when_asked_to() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse plain =
        api().retrieveCustomer(get(CUSTOMERS + "/" + THE_CUSTOMER, Map.of()), THE_CUSTOMER);
    StripeApiResponse included =
        api()
            .retrieveCustomer(
                get(
                    CUSTOMERS + "/" + THE_CUSTOMER,
                    Map.of(
                        "expand[]",
                        List.of(
                            "sources",
                            "subscriptions",
                            "tax_ids",
                            "cash_balance",
                            "invoice_credit_balance",
                            "tax"))),
                THE_CUSTOMER);

    assertThat(fieldsOf(plain))
        .doesNotContainKeys(
            "sources.object",
            "subscriptions.object",
            "tax_ids.object",
            "cash_balance",
            "tax.provider");
    assertThat(bodyOf(included))
        .contains(
            "\"sources\":{\"object\":\"list\",\"data\":[],\"has_more\":false,\"url\":\"/v1/customers/"
                + THE_CUSTOMER
                + "/sources\"}")
        .contains(
            "\"subscriptions\":{\"object\":\"list\",\"data\":[],\"has_more\":false,\"url\":\"/v1/customers/"
                + THE_CUSTOMER
                + "/subscriptions\"}")
        .contains(
            "\"tax_ids\":{\"object\":\"list\",\"data\":[],\"has_more\":false,\"url\":\"/v1/customers/"
                + THE_CUSTOMER
                + "/tax_ids\"}")
        .contains("\"cash_balance\":null")
        .contains("\"invoice_credit_balance\":{}")
        .contains(
            "\"tax\":{\"automatic_tax\":\"unrecognized_location\",\"ip_address\":null,\"location\":null,\"provider\":\"stripe\"}");
  }

  @Test
  void a_customer_reached_through_an_intent_includes_too_and_a_deleted_one_includes_nothing() {
    aHeldPaymentOfTheCustomer();

    StripeApiResponse live =
        retrieve(FIRST_INTENT, Map.of("expand[]", List.of("customer.sources")));
    api().deleteCustomer(delete(CUSTOMERS + "/" + THE_CUSTOMER), THE_CUSTOMER);
    StripeApiResponse deleted =
        retrieve(FIRST_INTENT, Map.of("expand[]", List.of("customer.sources")));
    StripeApiResponse retrievedDeleted =
        api()
            .retrieveCustomer(
                get(CUSTOMERS + "/" + THE_CUSTOMER, Map.of("expand[]", List.of("tax_ids"))),
                THE_CUSTOMER);

    assertThat(fieldsOf(live))
        .containsEntry("customer.id", List.of(THE_CUSTOMER))
        .containsEntry("customer.sources.object", List.of("list"));
    assertThat(fieldsOf(deleted))
        .containsEntry("customer.deleted", List.of("true"))
        .doesNotContainKey("customer.sources.object");
    assertThat(retrievedDeleted.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(retrievedDeleted))
        .containsEntry("deleted", List.of("true"))
        .doesNotContainKey("tax_ids.object");
  }

  @Test
  void a_customer_route_accepts_a_property_the_customer_has_and_refuses_one_it_does_not() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse created =
        api().createCustomer(post(CUSTOMERS, Map.of("expand[]", "test_clock")));
    StripeApiResponse updated =
        api()
            .updateCustomer(
                post(CUSTOMERS + "/" + THE_CUSTOMER, Map.of("expand[]", "email")), THE_CUSTOMER);
    StripeApiResponse refused =
        api()
            .updateCustomer(
                post(CUSTOMERS + "/" + THE_CUSTOMER, Map.of("expand[]", "no_such")), THE_CUSTOMER);

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(created)).containsEntry("object", List.of("customer"));
    assertThat(updated.httpStatus()).isEqualTo(200);
    assertThat(fieldsOf(updated)).containsEntry("id", List.of(THE_CUSTOMER));
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("This property cannot be expanded because it doesn't exist: no_such");
  }

  @Test
  void an_ephemeral_key_publishes_no_expandable_field_and_refuses_every_path() {
    aCustomerAndTheCardSavedToIt();

    StripeApiResponse refused =
        api()
            .createEphemeralKey(
                post(
                    "/v1/ephemeral_keys",
                    Map.of("customer", THE_CUSTOMER, "expand[]", "associated_objects")));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("This property cannot be expanded (associated_objects).");
  }

  @Test
  void every_route_that_answers_an_object_reads_expand_and_refuses_one_that_is_not_a_list() {
    aHeldPaymentOfTheCustomer();
    StripeApi api = api();
    String intent = PAYMENT_INTENTS + "/" + FIRST_INTENT;
    Map<String, String> notAList = Map.of("expand", "customer");
    Map<String, List<String>> notAListInTheQuery = Map.of("expand", List.of("customer"));

    Map<String, StripeApiResponse> answered = new LinkedHashMap<>();
    answered.put("create an intent", api.createPaymentIntent(post(PAYMENT_INTENTS, notAList)));
    answered.put("retrieve an intent", retrieve(FIRST_INTENT, notAListInTheQuery));
    answered.put(
        "confirm an intent",
        api.confirmPaymentIntent(
            post(
                intent + "/confirm",
                Map.of("expand", "customer", "return_url", StripePaymentIntentTest.A_RETURN_URL)),
            FIRST_INTENT));
    answered.put(
        "capture an intent",
        api.capturePaymentIntent(post(intent + "/capture", notAList), FIRST_INTENT));
    answered.put(
        "cancel an intent",
        api.cancelPaymentIntent(post(intent + "/cancel", notAList), FIRST_INTENT));
    answered.put(
        "retrieve a charge",
        api.retrieveCharge(get("/v1/charges/" + FIRST_CHARGE, notAListInTheQuery), FIRST_CHARGE));
    answered.put("create a refund", api.createRefund(post("/v1/refunds", notAList)));
    answered.put("create a customer", api.createCustomer(post(CUSTOMERS, notAList)));
    answered.put(
        "retrieve a customer",
        api.retrieveCustomer(
            get(CUSTOMERS + "/" + THE_CUSTOMER, notAListInTheQuery), THE_CUSTOMER));
    answered.put(
        "update a customer",
        api.updateCustomer(post(CUSTOMERS + "/" + THE_CUSTOMER, notAList), THE_CUSTOMER));
    answered.put(
        "list payment methods", api.listPaymentMethods(get(PAYMENT_METHODS, notAListInTheQuery)));
    answered.put(
        "list a customer's payment methods",
        api.listCustomerPaymentMethods(
            get(CUSTOMERS + "/" + THE_CUSTOMER + "/payment_methods", notAListInTheQuery),
            THE_CUSTOMER));
    answered.put(
        "retrieve a payment method",
        api.retrievePaymentMethod(
            get(PAYMENT_METHODS + "/" + THE_CARD, notAListInTheQuery), THE_CARD));
    answered.put(
        "detach a payment method",
        api.detachPaymentMethod(
            post(PAYMENT_METHODS + "/" + THE_CARD + "/detach", notAList), THE_CARD));
    answered.put("create a setup intent", api.createSetupIntent(post(SETUP_INTENTS, notAList)));
    answered.put(
        "confirm a setup intent",
        api.confirmSetupIntent(
            post(
                SETUP_INTENTS + "/seti_sim_none/confirm",
                Map.of("expand", "customer", "return_url", StripePaymentIntentTest.A_RETURN_URL)),
            "seti_sim_none"));
    answered.put(
        "create an ephemeral key", api.createEphemeralKey(post("/v1/ephemeral_keys", notAList)));

    assertThat(answered).hasSize(17);
    answered.forEach(
        (route, refused) -> {
          assertThat(refused.httpStatus()).as(route).isEqualTo(400);
          assertThat(bodyOf(refused)).as(route).contains("\"param\":\"expand\"");
        });
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void a_client_secret_inside_an_expanded_intent_is_answered_and_withheld_from_the_trail() {
    aHeldPaymentOfTheCustomer();
    String secret = intents.find(E2E_RUN, FIRST_INTENT).orElseThrow().clientSecret();

    StripeApiResponse retrieved =
        api()
            .retrieveCharge(
                get("/v1/charges/" + FIRST_CHARGE, Map.of("expand[]", List.of("payment_intent"))),
                FIRST_CHARGE);

    assertThat(fieldsOf(retrieved)).containsEntry("payment_intent.client_secret", List.of(secret));
    assertThat(audit.of(StripeApi.STRIPE, E2E_RUN))
        .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .last()
        .satisfies(
            record ->
                assertThat(record.sanitizedBody())
                    .containsEntry("payment_intent.client_secret", List.of("[redacted]"))
                    .containsEntry("payment_intent.id", List.of(FIRST_INTENT)));
  }

  @Test
  void a_replay_under_the_same_key_answers_the_expanded_body_the_first_request_stored() {
    aCustomerAndTheCardSavedToIt();
    StripeApiCall keyed =
        new StripeApiCall(
            "POST",
            PAYMENT_INTENTS,
            withTheKey("expand-once"),
            formOf(
                Map.of(
                    "amount",
                    "1000",
                    "currency",
                    "pln",
                    "customer",
                    THE_CUSTOMER,
                    "expand[]",
                    "customer")));
    StripeApiCall anotherExpansion =
        new StripeApiCall(
            "POST",
            PAYMENT_INTENTS,
            withTheKey("expand-once"),
            formOf(
                Map.of(
                    "amount",
                    "1000",
                    "currency",
                    "pln",
                    "customer",
                    THE_CUSTOMER,
                    "expand[]",
                    "latest_charge")));

    StripeApiResponse first = api().createPaymentIntent(keyed);
    StripeApiResponse replayed = api().createPaymentIntent(keyed);
    StripeApiResponse reused = api().createPaymentIntent(anotherExpansion);

    assertThat(fieldsOf(first)).containsEntry("customer.id", List.of(THE_CUSTOMER));
    assertThat(bodyOf(replayed)).isEqualTo(bodyOf(first));
    assertThat(reused.httpStatus()).isEqualTo(400);
    assertThat(fieldsOf(reused)).containsEntry("error.type", List.of("idempotency_error"));
    assertThat(intents.all()).hasSize(1);
  }

  private void anAccount() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
  }

  private void aCustomerAndTheCardSavedToIt() {
    anAccount();
    customers.insertIfAbsent(aCustomerOf(E2E_RUN, THE_CUSTOMER, ACCOUNT));
    paymentMethods.insertIfAbsent(aCardOf(THE_CARD, THE_CUSTOMER));
  }

  private void aHeldPaymentOfTheCustomer() {
    aCustomerAndTheCardSavedToIt();
    api()
        .createPaymentIntent(
            post(
                PAYMENT_INTENTS,
                Map.of(
                    "amount",
                    "1000",
                    "currency",
                    "pln",
                    "capture_method",
                    "manual",
                    "customer",
                    THE_CUSTOMER,
                    "payment_method",
                    THE_CARD,
                    "return_url",
                    StripePaymentIntentTest.A_RETURN_URL,
                    "confirm",
                    "true")));
  }

  private StripeApiResponse retrieve(String intentId, Map<String, List<String>> query) {
    return api().retrievePaymentIntent(get(PAYMENT_INTENTS + "/" + intentId, query), intentId);
  }

  private static Map<String, List<String>> fieldsOf(StripeApiResponse response) {
    return StripeJsonReader.read(bodyOf(response)).flatten();
  }

  private static StripeCustomer aCustomerOf(TestRunId testRunId, String customerId, String owner) {
    return new StripeCustomer(
        testRunId, customerId, owner, null, null, ObjectOrigin.CONTROL_API, NOW, NOW);
  }

  private static StripePaymentMethod aCardOf(String paymentMethodId, @Nullable String customerId) {
    return new StripePaymentMethod(
        E2E_RUN,
        paymentMethodId,
        ACCOUNT,
        customerId,
        StripePaymentMethodType.CARD,
        new FixtureCard("visa", "4242", 12, 2030, "credit"),
        null,
        ObjectOrigin.CONTROL_API,
        NOW,
        NOW);
  }

  private static Map<String, List<String>> formOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static Map<String, List<String>> withTheKey(String key) {
    Map<String, List<String>> headers = new LinkedHashMap<>(bearer(SECRET_KEY));
    headers.put("Idempotency-Key", List.of(key));
    return headers;
  }

  private static StripeApiCall get(String path, Map<String, List<String>> query) {
    return new StripeApiCall("GET", path, bearer(SECRET_KEY), Map.of(), query);
  }

  private static StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall("POST", path, bearer(SECRET_KEY), formOf(new LinkedHashMap<>(form)));
  }

  private static StripeApiCall delete(String path) {
    return new StripeApiCall("DELETE", path, bearer(SECRET_KEY), Map.of());
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
        intents,
        charges,
        refunds,
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine));
  }
}
