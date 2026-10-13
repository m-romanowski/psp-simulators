package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.fieldsOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripePaymentMethodTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant LATER = Instant.parse("2026-09-11T13:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String OTHER_ACCOUNT = "merchant-2";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String OTHER_SECRET_KEY = "sk_test_51Another";
  private static final String THREE_DAYS = "P3D";

  private static final String PAYMENT_METHODS = "/v1/payment_methods";
  private static final String CUSTOMERS = "/v1/customers";

  private static final String A_CUSTOMER = "cus_the_one_with_two_cards";
  private static final String ANOTHER_CUSTOMER = "cus_the_one_with_one_card";
  private static final String FIRST_CARD = "pm_the_first_card";
  private static final String SECOND_CARD = "pm_the_second_card";
  private static final String A_WALLET = "pm_a_paypal_wallet";
  private static final String AN_UNATTACHED_CARD = "pm_saved_to_nobody";
  private static final String NO_METHOD_OF_THIS_RUN = "pm_this_run_holds_nothing_under_this";

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
  void
      the_two_cards_one_customer_holds_are_both_listed_by_the_route_that_addresses_that_customer() {
    anAccountHoldingTwoCardsOfOneCustomer();

    StripeApiResponse listed =
        api(NOW).listCustomerPaymentMethods(get(customerMethods(A_CUSTOMER)), A_CUSTOMER);

    assertThat(listed.httpStatus()).isEqualTo(200);
    assertThat(idsIn(bodyOf(listed))).containsExactlyInAnyOrder(FIRST_CARD, SECOND_CARD);
    assertThat(bodyOf(listed))
        .contains("\"object\":\"list\"")
        .contains("\"url\":\"" + CUSTOMERS + "/" + A_CUSTOMER + "/payment_methods\"")
        .contains("\"has_more\":false");
  }

  @Test
  void the_list_of_all_methods_and_the_list_of_one_customers_methods_are_not_the_same_answer() {
    anAccountHoldingTwoCardsOfOneCustomer();
    paymentMethods.insertIfAbsent(aCardOf(AN_UNATTACHED_CARD, null));

    StripeApiResponse all = api(NOW).listPaymentMethods(get(PAYMENT_METHODS));
    StripeApiResponse ofTheCustomer =
        api(NOW).listCustomerPaymentMethods(get(customerMethods(A_CUSTOMER)), A_CUSTOMER);

    assertThat(idsIn(bodyOf(all)))
        .containsExactlyInAnyOrder(FIRST_CARD, SECOND_CARD, AN_UNATTACHED_CARD);
    assertThat(idsIn(bodyOf(ofTheCustomer))).containsExactlyInAnyOrder(FIRST_CARD, SECOND_CARD);
    assertThat(bodyOf(all)).contains("\"url\":\"" + PAYMENT_METHODS + "\"");
  }

  @Test
  void the_all_methods_route_filters_by_customer_and_the_customer_route_refuses_an_unknown_one() {
    anAccountHoldingTwoCardsOfOneCustomer();

    StripeApiResponse filtered =
        api(NOW)
            .listPaymentMethods(
                get(PAYMENT_METHODS, Map.of(StripeFields.CUSTOMER, "cus_nobody_created")));
    StripeApiResponse addressed =
        api(NOW)
            .listCustomerPaymentMethods(
                get(customerMethods("cus_nobody_created")), "cus_nobody_created");

    assertThat(filtered.httpStatus()).isEqualTo(200);
    assertThat(idsIn(bodyOf(filtered))).isEmpty();
    assertThat(addressed.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(addressed))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"customer\"");
  }

  @Test
  void a_list_filtered_by_type_carries_the_methods_of_that_published_type_and_no_other() {
    anAccountHoldingTwoCardsOfOneCustomer();
    paymentMethods.insertIfAbsent(aWalletOf(A_WALLET, A_CUSTOMER));

    StripeApiResponse cards =
        api(NOW).listPaymentMethods(get(PAYMENT_METHODS, Map.of(StripeFields.TYPE, "card")));
    StripeApiResponse wallets =
        api(NOW).listPaymentMethods(get(PAYMENT_METHODS, Map.of(StripeFields.TYPE, "paypal")));

    assertThat(idsIn(bodyOf(cards))).containsExactlyInAnyOrder(FIRST_CARD, SECOND_CARD);
    assertThat(idsIn(bodyOf(wallets))).containsExactly(A_WALLET);
  }

  @Test
  void a_type_this_build_serves_none_of_empties_the_list_rather_than_refusing_the_call() {
    anAccountHoldingTwoCardsOfOneCustomer();

    StripeApiResponse listed =
        api(NOW).listPaymentMethods(get(PAYMENT_METHODS, Map.of(StripeFields.TYPE, "sepa_debit")));

    assertThat(listed.httpStatus()).isEqualTo(200);
    assertThat(idsIn(bodyOf(listed))).isEmpty();
  }

  @Test
  void
      a_payment_method_of_another_account_is_listed_and_retrieved_by_neither_of_this_accounts_routes() {
    accounts
        .with(stripeAccount(ACCOUNT, SECRET_KEY))
        .with(stripeAccount(OTHER_ACCOUNT, OTHER_SECRET_KEY));
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, ACCOUNT));
    paymentMethods.insertIfAbsent(aCardOf(FIRST_CARD, A_CUSTOMER));

    StripeApiResponse listed =
        api(NOW)
            .listPaymentMethods(
                new StripeApiCall("GET", PAYMENT_METHODS, bearer(OTHER_SECRET_KEY), Map.of()));
    StripeApiResponse retrieved =
        api(NOW)
            .retrievePaymentMethod(
                new StripeApiCall("GET", method(FIRST_CARD), bearer(OTHER_SECRET_KEY), Map.of()),
                FIRST_CARD);

    assertThat(idsIn(bodyOf(listed))).isEmpty();
    assertThat(retrieved.httpStatus()).isEqualTo(404);
  }

  @Test
  void a_retrieved_card_carries_the_fields_this_contract_names_and_the_card_hash_beside_them() {
    anAccountHoldingTwoCardsOfOneCustomer();

    StripeApiResponse retrieved =
        api(NOW).retrievePaymentMethod(get(method(FIRST_CARD)), FIRST_CARD);

    assertThat(retrieved.httpStatus()).isEqualTo(200);
    assertThat(retrieved.providerObjectId()).isEqualTo(FIRST_CARD);
    assertThat(bodyOf(retrieved))
        .contains("\"id\":\"" + FIRST_CARD + "\"")
        .contains("\"object\":\"payment_method\"")
        .contains("\"type\":\"card\"")
        .contains("\"customer\":\"" + A_CUSTOMER + "\"")
        .contains(
            "\"billing_details\":{\"address\":null,\"email\":null,\"name\":null,\"phone\":null}")
        .contains("\"brand\":\"visa\"")
        .contains("\"last4\":\"4242\"")
        .contains("\"exp_month\":12")
        .contains("\"exp_year\":2030")
        .contains("\"country\":null")
        .contains("\"checks\":null");
  }

  @Test
  void a_payment_method_saved_to_no_customer_is_retrieved_with_a_customer_of_null() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    paymentMethods.insertIfAbsent(aCardOf(AN_UNATTACHED_CARD, null));

    StripeApiResponse retrieved =
        api(NOW).retrievePaymentMethod(get(method(AN_UNATTACHED_CARD)), AN_UNATTACHED_CARD);

    assertThat(retrieved.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(retrieved)).contains("\"customer\":null");
  }

  @Test
  void a_retrieve_that_asks_for_the_customer_expanded_carries_the_customer_object_in_its_place() {
    anAccountHoldingTwoCardsOfOneCustomer();

    StripeApiResponse retrieved =
        api(NOW)
            .retrievePaymentMethod(
                get(method(FIRST_CARD), Map.of(RetrievePaymentMethod.EXPAND, "customer")),
                FIRST_CARD);

    assertThat(bodyOf(retrieved))
        .contains("\"customer\":{\"id\":\"" + A_CUSTOMER + "\"")
        .contains("\"object\":\"customer\"");
  }

  @Test
  void a_paypal_method_carries_the_three_published_payer_fields_and_every_one_of_them_is_null() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, ACCOUNT));
    paymentMethods.insertIfAbsent(aWalletOf(A_WALLET, A_CUSTOMER));

    StripeApiResponse retrieved = api(NOW).retrievePaymentMethod(get(method(A_WALLET)), A_WALLET);

    assertThat(bodyOf(retrieved))
        .contains("\"type\":\"paypal\"")
        .contains("\"paypal\":{\"payer_email\":null,\"payer_id\":null,\"country\":null}")
        .doesNotContain("\"card\"");
  }

  @Test
  void retrieving_a_payment_method_this_run_holds_none_of_is_the_published_resource_missing() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse retrieved =
        api(NOW).retrievePaymentMethod(get(method(NO_METHOD_OF_THIS_RUN)), NO_METHOD_OF_THIS_RUN);

    assertThat(retrieved.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(retrieved))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"payment_method\"");
  }

  @Test
  void a_detached_method_answers_with_no_customer_and_leaves_that_customers_listing() {
    anAccountHoldingTwoCardsOfOneCustomer();

    StripeApiResponse detached =
        api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);
    StripeApiResponse listed =
        api(LATER).listCustomerPaymentMethods(get(customerMethods(A_CUSTOMER)), A_CUSTOMER);

    assertThat(detached.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(detached))
        .contains("\"id\":\"" + FIRST_CARD + "\"")
        .contains("\"customer\":null");
    assertThat(idsIn(bodyOf(listed))).containsExactly(SECOND_CARD);
  }

  @Test
  void a_detached_method_is_still_retrievable_and_is_still_on_the_list_of_all_methods() {
    anAccountHoldingTwoCardsOfOneCustomer();
    api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);

    StripeApiResponse retrieved =
        api(LATER).retrievePaymentMethod(get(method(FIRST_CARD)), FIRST_CARD);
    StripeApiResponse all = api(LATER).listPaymentMethods(get(PAYMENT_METHODS));

    assertThat(retrieved.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(retrieved)).contains("\"customer\":null");
    assertThat(idsIn(bodyOf(all))).containsExactlyInAnyOrder(FIRST_CARD, SECOND_CARD);
  }

  @Test
  void a_second_detach_of_one_method_is_refused_rather_than_answered_again() {
    anAccountHoldingTwoCardsOfOneCustomer();
    api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);

    StripeApiResponse again = api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);

    assertThat(again.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(again))
        .contains("permanent and irreversible")
        .contains("\"param\":\"payment_method\"");
  }

  @Test
  void detaching_a_method_that_was_never_saved_to_a_customer_is_refused_with_its_own_reason() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    paymentMethods.insertIfAbsent(aCardOf(AN_UNATTACHED_CARD, null));

    StripeApiResponse refused =
        api(NOW).detachPaymentMethod(post(detach(AN_UNATTACHED_CARD)), AN_UNATTACHED_CARD);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("not attached to a Customer")
        .doesNotContain("permanent and irreversible");
  }

  @Test
  void detaching_a_method_this_run_holds_none_of_is_the_published_resource_missing() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));

    StripeApiResponse refused =
        api(NOW).detachPaymentMethod(post(detach(NO_METHOD_OF_THIS_RUN)), NO_METHOD_OF_THIS_RUN);

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void a_method_detached_while_this_detach_was_deciding_is_refused_as_already_detached() {
    anAccountHoldingTwoCardsOfOneCustomer();
    paymentMethods.detached(paymentMethods.find(E2E_RUN, FIRST_CARD).orElseThrow(), NOW);

    StripeApiResponse refused =
        api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("permanent and irreversible");
  }

  @Test
  void a_method_reset_away_while_this_detach_was_deciding_reports_it_missing() {
    anAccountHoldingTwoCardsOfOneCustomer();
    paymentMethods.losingTheMethodOnTheNextWrite();

    StripeApiResponse refused =
        api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused)).contains("\"code\":\"resource_missing\"");
  }

  @Test
  void a_confirm_that_names_a_detached_method_is_refused_rather_than_charging_it() {
    anAccountHoldingTwoCardsOfOneCustomer();
    api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);
    String intentId =
        api(LATER)
            .createPaymentIntent(
                post("/v1/payment_intents", Map.of("amount", "1000", "currency", "usd")))
            .providerObjectId();

    StripeApiResponse refused =
        api(LATER)
            .confirmPaymentIntent(
                post(
                    "/v1/payment_intents/" + intentId + "/confirm",
                    Map.of(StripeFields.PAYMENT_METHOD, FIRST_CARD)),
                intentId);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("can no longer be used for payments");
  }

  @Test
  void a_creation_that_confirms_with_a_detached_method_is_refused_before_an_intent_exists() {
    anAccountHoldingTwoCardsOfOneCustomer();
    api(LATER).detachPaymentMethod(post(detach(FIRST_CARD)), FIRST_CARD);

    StripeApiResponse refused =
        api(LATER)
            .createPaymentIntent(
                post(
                    "/v1/payment_intents",
                    Map.of(
                        "amount",
                        "1000",
                        "currency",
                        "usd",
                        StripeFields.CONFIRM,
                        "true",
                        StripeFields.PAYMENT_METHOD,
                        FIRST_CARD)));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("can no longer be used for payments");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_confirm_that_names_a_method_still_attached_is_not_refused_by_the_detachment_rule() {
    anAccountHoldingTwoCardsOfOneCustomer();
    String intentId =
        api(NOW)
            .createPaymentIntent(
                post("/v1/payment_intents", Map.of("amount", "1000", "currency", "usd")))
            .providerObjectId();

    StripeApiResponse confirmed =
        api(NOW)
            .confirmPaymentIntent(
                post(
                    "/v1/payment_intents/" + intentId + "/confirm",
                    Map.of(StripeFields.PAYMENT_METHOD, FIRST_CARD)),
                intentId);

    assertThat(confirmed.httpStatus()).isEqualTo(200);
  }

  @Test
  void deleting_a_customer_detaches_every_payment_method_it_held_and_leaves_them_retrievable() {
    anAccountHoldingTwoCardsOfOneCustomer();
    customers.insertIfAbsent(aCustomer(ANOTHER_CUSTOMER, ACCOUNT));
    paymentMethods.insertIfAbsent(aCardOf(A_WALLET, ANOTHER_CUSTOMER));

    StripeApiResponse deleted =
        api(LATER).deleteCustomer(delete(CUSTOMERS + "/" + A_CUSTOMER), A_CUSTOMER);

    assertThat(deleted.httpStatus()).isEqualTo(200);
    assertThat(
            idsIn(
                bodyOf(
                    api(LATER)
                        .listCustomerPaymentMethods(get(customerMethods(A_CUSTOMER)), A_CUSTOMER))))
        .isEmpty();
    assertThat(bodyOf(api(LATER).retrievePaymentMethod(get(method(FIRST_CARD)), FIRST_CARD)))
        .contains("\"customer\":null");
    assertThat(
            idsIn(
                bodyOf(
                    api(LATER)
                        .listCustomerPaymentMethods(
                            get(customerMethods(ANOTHER_CUSTOMER)), ANOTHER_CUSTOMER))))
        .containsExactly(A_WALLET);
  }

  @Test
  void a_method_a_customer_deletion_detached_can_never_be_used_for_a_payment_again() {
    anAccountHoldingTwoCardsOfOneCustomer();
    api(LATER).deleteCustomer(delete(CUSTOMERS + "/" + A_CUSTOMER), A_CUSTOMER);
    String intentId =
        api(LATER)
            .createPaymentIntent(
                post("/v1/payment_intents", Map.of("amount", "1000", "currency", "usd")))
            .providerObjectId();

    StripeApiResponse refused =
        api(LATER)
            .confirmPaymentIntent(
                post(
                    "/v1/payment_intents/" + intentId + "/confirm",
                    Map.of(StripeFields.PAYMENT_METHOD, SECOND_CARD)),
                intentId);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("can no longer be used for payments");
  }

  @Test
  void the_trail_records_the_customer_a_list_was_filtered_by_rather_than_an_empty_request() {
    anAccountHoldingTwoCardsOfOneCustomer();

    api(NOW).listPaymentMethods(get(PAYMENT_METHODS, Map.of(StripeFields.CUSTOMER, A_CUSTOMER)));

    assertThat(audit.of(StripeApi.STRIPE, E2E_RUN))
        .filteredOn(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.sanitizedBody())
                    .containsEntry(StripeFields.CUSTOMER, List.of(A_CUSTOMER)));
  }

  @Test
  void a_field_a_caller_sent_in_the_body_and_in_the_query_is_on_the_trail_twice_over() {
    anAccountHoldingTwoCardsOfOneCustomer();

    api(NOW)
        .createPaymentIntent(
            new StripeApiCall(
                "POST",
                "/v1/payment_intents",
                bearer(SECRET_KEY),
                fieldsOf(
                    new LinkedHashMap<>(
                        Map.of("amount", "1000", "currency", "usd", "customer", A_CUSTOMER))),
                fieldsOf(Map.of("customer", ANOTHER_CUSTOMER))));

    assertThat(audit.of(StripeApi.STRIPE, E2E_RUN))
        .filteredOn(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.sanitizedBody())
                    .containsEntry(StripeFields.CUSTOMER, List.of(A_CUSTOMER, ANOTHER_CUSTOMER)));
  }

  private void anAccountHoldingTwoCardsOfOneCustomer() {
    accounts.with(stripeAccount(ACCOUNT, SECRET_KEY));
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, ACCOUNT));
    paymentMethods.insertIfAbsent(aCardOf(FIRST_CARD, A_CUSTOMER));
    paymentMethods.insertIfAbsent(aCardOf(SECOND_CARD, A_CUSTOMER));
  }

  private static List<String> idsIn(String body) {
    List<String> ids = new ArrayList<>();
    int from = 0;
    String marker = "{\"id\":\"";
    while (true) {
      int at = body.indexOf(marker, from);
      if (at < 0) {
        return List.copyOf(ids);
      }
      int start = at + marker.length();
      int end = body.indexOf('"', start);
      ids.add(body.substring(start, end));
      from = end;
    }
  }

  private static StripeCustomer aCustomer(String customerId, String accountId) {
    return new StripeCustomer(
        E2E_RUN, customerId, accountId, null, null, ObjectOrigin.CONTROL_API, NOW, NOW);
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

  private static StripePaymentMethod aWalletOf(
      String paymentMethodId, @Nullable String customerId) {
    return new StripePaymentMethod(
        E2E_RUN,
        paymentMethodId,
        ACCOUNT,
        customerId,
        StripePaymentMethodType.PAYPAL,
        null,
        null,
        ObjectOrigin.CONTROL_API,
        NOW,
        NOW);
  }

  private static String method(String paymentMethodId) {
    return PAYMENT_METHODS + "/" + paymentMethodId;
  }

  private static String detach(String paymentMethodId) {
    return method(paymentMethodId) + "/detach";
  }

  private static String customerMethods(String customerId) {
    return CUSTOMERS + "/" + customerId + "/payment_methods";
  }

  private static StripeApiCall get(String path) {
    return new StripeApiCall("GET", path, bearer(SECRET_KEY), Map.of());
  }

  private static StripeApiCall get(String path, Map<String, String> query) {
    return new StripeApiCall("GET", path, bearer(SECRET_KEY), Map.of(), fieldsOf(query));
  }

  private static StripeApiCall post(String path) {
    return new StripeApiCall("POST", path, bearer(SECRET_KEY), Map.of());
  }

  private static StripeApiCall post(String path, Map<String, String> form) {
    return new StripeApiCall("POST", path, bearer(SECRET_KEY), fieldsOf(new LinkedHashMap<>(form)));
  }

  private static StripeApiCall delete(String path) {
    return new StripeApiCall("DELETE", path, bearer(SECRET_KEY), Map.of());
  }

  private StripeAccount stripeAccount(String accountId, String secretKey) {
    return StripeDoubles.account(E2E_RUN, accountId, secretKey, THREE_DAYS);
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
