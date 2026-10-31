package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
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

final class StripeCardWorld {

  static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  static final TestRunId RUN = TestRunId.of("stripe-card-run");
  static final TestRunId ANOTHER_RUN = TestRunId.of("stripe-card-elsewhere");

  static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");

  static final String ACCOUNT = "merchant-1";
  static final String SECRET_KEY = "sk_test_51CardWorld";
  static final String PUBLISHABLE_KEY = "pk_test_51CardWorld";
  static final String ANOTHER_ACCOUNT = "merchant-2";
  static final String ANOTHER_SECRET_KEY = "sk_test_51AnotherCardWorld";
  static final String ANOTHER_PUBLISHABLE_KEY = "pk_test_51AnotherCardWorld";
  static final String ELSEWHERE_SECRET_KEY = "sk_test_51ElsewhereCardWorld";

  static final String THE_CUSTOMER = "cus_the_one_saving_a_card";
  static final String A_RETURN_URL = "https://shop.example/checkout/return";
  static final String AN_IDEMPOTENCY_KEY = "8b8f4c4e-6a8e-4c6b-9a55-3c7e2f0d1a29";

  static final String PAYMENT_METHODS = "/v1/payment_methods";
  static final String PAYMENT_INTENTS = "/v1/payment_intents";
  static final String SETUP_INTENTS = "/v1/setup_intents";

  static final String THE_FIRST_MINTED_METHOD = "pm_sim_00000000000000000001";
  static final String THE_SECOND_MINTED_METHOD = "pm_sim_00000000000000000002";

  final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
  final StripeDoubles.Runs testRuns = new StripeDoubles.Runs(DEFAULT_RUN);
  final StripeDoubles.Audit audit = new StripeDoubles.Audit();
  final StripeDoubles.PaymentMethods paymentMethods = new StripeDoubles.PaymentMethods();
  final StripeDoubles.Intents intents = new StripeDoubles.Intents(paymentMethods);
  final StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
  final StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
  final StripeDoubles.Customers customers = new StripeDoubles.Customers();
  final StripeDoubles.SetupIntents setupIntents = new StripeDoubles.SetupIntents(paymentMethods);
  final StripeDoubles.Keys keys = new StripeDoubles.Keys();
  final StripeDoubles.EphemeralKeys ephemeralKeys = new StripeDoubles.EphemeralKeys();
  final StripeDoubles.CountingIds ids = new StripeDoubles.CountingIds();

  StripeCardWorld() {
    accounts.with(
        StripeDoubles.account(
            RUN,
            ACCOUNT,
            SECRET_KEY,
            "P3D",
            Map.of(StripeAccount.PUBLISHABLE_KEY_PROPERTY, PUBLISHABLE_KEY)));
    accounts.with(
        StripeDoubles.account(
            RUN,
            ANOTHER_ACCOUNT,
            ANOTHER_SECRET_KEY,
            "P3D",
            Map.of(StripeAccount.PUBLISHABLE_KEY_PROPERTY, ANOTHER_PUBLISHABLE_KEY)));
    accounts.with(StripeDoubles.account(ANOTHER_RUN, ACCOUNT, ELSEWHERE_SECRET_KEY, "P3D"));
    customers.insertIfAbsent(
        new StripeCustomer(
            RUN, THE_CUSTOMER, ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
  }

  StripeApi api() {
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
        new CreateSetupIntent(setupIntents, paymentMethods, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, ids, scenarioEngine));
  }

  StripeApiResponse createMethod(String key, Map<String, String> form) {
    return api().createPaymentMethod(post(key, PAYMENT_METHODS, form, Map.of()));
  }

  StripeApiResponse createMethodUnderTheKey(String key, Map<String, String> form) {
    return api()
        .createPaymentMethod(
            post(
                key,
                PAYMENT_METHODS,
                form,
                Map.of(StripeFields.IDEMPOTENCY_KEY_HEADER, AN_IDEMPOTENCY_KEY)));
  }

  StripeApiResponse retrieveMethod(String key, String paymentMethodId) {
    return api()
        .retrievePaymentMethod(
            new StripeApiCall(
                "GET", PAYMENT_METHODS + "/" + paymentMethodId, bearer(key), Map.of(), Map.of()),
            paymentMethodId);
  }

  StripeApiResponse listTheCustomersMethods(String customerId) {
    String path = "/v1/customers/" + customerId + "/payment_methods";
    return api()
        .listCustomerPaymentMethods(
            new StripeApiCall("GET", path, bearer(SECRET_KEY), Map.of(), Map.of()), customerId);
  }

  StripeApiResponse createPayment(String key, Map<String, String> form) {
    return api().createPaymentIntent(post(key, PAYMENT_INTENTS, form, Map.of()));
  }

  StripeApiResponse confirmPayment(String key, String intentId, Map<String, String> form) {
    return api()
        .confirmPaymentIntent(
            post(key, PAYMENT_INTENTS + "/" + intentId + "/confirm", form, Map.of()), intentId);
  }

  StripeApiResponse confirmPaymentUnderTheKey(
      String key, String intentId, Map<String, String> form) {
    return api()
        .confirmPaymentIntent(
            post(
                key,
                PAYMENT_INTENTS + "/" + intentId + "/confirm",
                form,
                Map.of(StripeFields.IDEMPOTENCY_KEY_HEADER, AN_IDEMPOTENCY_KEY)),
            intentId);
  }

  StripeApiResponse confirmSetupUnderTheKey(
      String key, String setupIntentId, Map<String, String> form) {
    return api()
        .confirmSetupIntent(
            post(
                key,
                SETUP_INTENTS + "/" + setupIntentId + "/confirm",
                form,
                Map.of(StripeFields.IDEMPOTENCY_KEY_HEADER, AN_IDEMPOTENCY_KEY)),
            setupIntentId);
  }

  StripeApiResponse createSetup(String key, Map<String, String> form) {
    return api().createSetupIntent(post(key, SETUP_INTENTS, form, Map.of()));
  }

  StripeApiResponse confirmSetup(String key, String setupIntentId, Map<String, String> form) {
    return api()
        .confirmSetupIntent(
            post(key, SETUP_INTENTS + "/" + setupIntentId + "/confirm", form, Map.of()),
            setupIntentId);
  }

  void seedAFixture(String accountId, String paymentMethodId, @Nullable String customerId) {
    paymentMethods.insertIfAbsent(
        new StripePaymentMethod(
            RUN,
            paymentMethodId,
            accountId,
            customerId,
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "1881", 12, 2030, "debit"),
            null,
            ObjectOrigin.CONTROL_API,
            NOW,
            NOW));
  }

  void declining(String operation, StripeDecline decline) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TerminalFailure(decline.scenarioCode()),
            null));
  }

  void answeringInsteadOf(String operation) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));
  }

  List<StripePaymentMethod> theMethodsStored() {
    return paymentMethods.all();
  }

  StripePaymentMethod theMethodStored(String paymentMethodId) {
    return paymentMethods.find(RUN, paymentMethodId).orElseThrow();
  }

  String everythingTheTrailAndTheExchangesKeep() {
    StringBuilder kept = new StringBuilder();
    audit.all().forEach(record -> kept.append(record).append('\n'));
    for (KeyedExchange exchange :
        keys.exchanges(new KeyedExchangeQuery(RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 50))
            .exchanges()) {
      kept.append(exchange).append('\n');
    }
    return kept.toString();
  }

  static Map<String, List<String>> flat(StripeApiResponse response) {
    return StripeJsonReader.read(bodyOf(response)).flatten();
  }

  static String theValueAt(StripeApiResponse response, String path) {
    List<String> values = flat(response).get(path);
    assertThat(values).as(path + " in " + bodyOf(response)).isNotNull().hasSize(1);
    return values.get(0);
  }

  static Map<String, String> aVisa() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("type", "card");
    form.put("card[number]", "4242424242424242");
    form.put("card[exp_month]", "10");
    form.put("card[exp_year]", "2028");
    form.put("card[cvc]", "100");
    return form;
  }

  static Map<String, String> with(Map<String, String> form, String name, @Nullable String value) {
    Map<String, String> changed = new LinkedHashMap<>(form);
    if (value == null) {
      changed.remove(name);
    } else {
      changed.put(name, value);
    }
    return changed;
  }

  static Map<String, String> aSetupThatConfirmsWithoutARedirect() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("customer", THE_CUSTOMER);
    form.put("automatic_payment_methods[enabled]", "true");
    form.put("automatic_payment_methods[allow_redirects]", "never");
    return form;
  }

  static Map<String, String> aPaymentOf(String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "usd");
    form.put("allowed_payment_method_types[0]", "card");
    return form;
  }

  static String theIdOf(StripeApiResponse created) {
    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    return theValueAt(created, "id");
  }

  static Map<String, List<String>> bearer(String key) {
    return Map.of("Authorization", List.of("Bearer " + key));
  }

  private static StripeApiCall post(
      String key, String path, Map<String, String> form, Map<String, String> headers) {
    Map<String, List<String>> all = new LinkedHashMap<>(bearer(key));
    headers.forEach((name, value) -> all.put(name, List.of(value)));
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return new StripeApiCall("POST", path, all, fields);
  }
}
