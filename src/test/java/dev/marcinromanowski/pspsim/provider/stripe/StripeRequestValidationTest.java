package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.AN_IDEMPOTENCY_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.NOW;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PAYMENT_INTENTS;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PAYMENT_METHODS;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PUBLISHABLE_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.RUN;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_CUSTOMER;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aPaymentOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aVisa;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theIdOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theValueAt;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.with;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class StripeRequestValidationTest {

  private final StripeCardWorld world = new StripeCardWorld();

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "amount|amount|Missing required param: amount.",
        "currency|currency|Missing required param: currency.",
        "confirm|payment_method|Missing required param: payment_method.",
        "selection|automatic_payment_methods[enabled]|Missing required param: automatic_payment_methods[enabled].",
        "type|type|Missing required param: type.",
        "card|card|Missing required param: card.",
        "number|card[number]|Missing required param: card[number].",
        "exp_month|card[exp_month]|Missing required param: card[exp_month].",
        "exp_year|card[exp_year]|Missing required param: card[exp_year].",
        "customer|customer|Missing required param: customer.",
        "refund|payment_intent|Missing required param: payment_intent.",
        "client_secret|client_secret|Missing required param: client_secret."
      })
  void every_missing_parameter_uses_the_recorded_sentence(
      String request, String param, String message) {
    StripeApiResponse refused = missing(request);
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"parameter_missing\",\"doc_url\":\"https://docs.stripe.com/error-codes#parameter-missing\",\"message\":\""
                + message
                + "\",\"param\":\""
                + param
                + "\"}}");
    if ("client_secret".equals(request)) {
      assertThat(world.intents.all())
          .extracting(StripePaymentIntent::intentId)
          .containsExactly("pi_sim_0000000000000001");
      assertThat(world.intents.all())
          .extracting(intent -> intent.statusAt(NOW).wireValue())
          .containsExactly("requires_payment_method");
    } else {
      assertThat(world.intents.all()).isEmpty();
    }
    assertThat(world.setupIntents.all()).isEmpty();
    assertThat(world.theMethodsStored()).isEmpty();
    assertThat(world.charges.all()).isEmpty();
    assertThat(world.refunds.all()).isEmpty();
    assertThat(world.ephemeralKeys.all()).isEmpty();
    assertNoKey();
  }

  private StripeApiResponse missing(String request) {
    return switch (request) {
      case "amount" ->
          world.api().createPaymentIntent(call("POST", PAYMENT_INTENTS, Map.of("currency", "usd")));
      case "currency" ->
          world.api().createPaymentIntent(call("POST", PAYMENT_INTENTS, Map.of("amount", "1050")));
      case "confirm" ->
          world
              .api()
              .createPaymentIntent(
                  call("POST", PAYMENT_INTENTS, with(aPaymentOf("1050"), "confirm", "true")));
      case "selection" ->
          world
              .api()
              .createPaymentIntent(
                  call(
                      "POST",
                      PAYMENT_INTENTS,
                      with(
                          aPaymentOf("1050"),
                          "automatic_payment_methods[allow_redirects]",
                          "never")));
      case "type" -> world.api().createPaymentMethod(call("POST", PAYMENT_METHODS, Map.of()));
      case "card" ->
          world.api().createPaymentMethod(call("POST", PAYMENT_METHODS, Map.of("type", "card")));
      case "number", "exp_month", "exp_year" ->
          world
              .api()
              .createPaymentMethod(
                  call("POST", PAYMENT_METHODS, with(aVisa(), "card[" + request + "]", null)));
      case "customer" ->
          world.api().createEphemeralKey(call("POST", "/v1/ephemeral_keys", Map.of()));
      case "refund" -> world.api().createRefund(call("POST", "/v1/refunds", Map.of()));
      case "client_secret" -> {
        String id = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
        yield world
            .api()
            .retrievePaymentIntent(
                new StripeApiCall(
                    "GET", PAYMENT_INTENTS + "/" + id, bearer(PUBLISHABLE_KEY), Map.of(), Map.of()),
                id);
      }
      default -> throw new IllegalArgumentException(request);
    };
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "retrieve_payment|intent|No such payment_intent: 'pi_missing'",
        "confirm_payment|intent|No such payment_intent: 'pi_missing'",
        "capture_payment|intent|No such payment_intent: 'pi_missing'",
        "cancel_payment|intent|No such payment_intent: 'pi_missing'",
        "retrieve_setup|intent|No such setupintent: 'seti_missing'",
        "confirm_setup|intent|No such setupintent: 'seti_missing'",
        "retrieve_customer|customer|No such customer: 'cus_missing'",
        "update_customer|customer|No such customer: 'cus_missing'",
        "delete_customer|customer|No such customer: 'cus_missing'",
        "list_customer_methods|customer|No such customer: 'cus_missing'",
        "ephemeral_key|customer|No such customer: 'cus_missing'",
        "retrieve_charge|charge|No such charge: 'ch_missing'",
        "refund_charge|charge|No such charge: 'ch_missing'",
        "refund_payment|payment_intent|No such payment_intent: 'pi_missing'"
      })
  void a_secret_key_gets_the_missing_objects_name_and_parameter(
      String route, String param, String message) {
    StripeApiResponse refused = missingObject(route, SECRET_KEY);
    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\",\"doc_url\":\"https://docs.stripe.com/error-codes#resource-missing\",\"message\":\""
                + message
                + "\",\"param\":\""
                + param
                + "\"}}");
    assertThat(world.intents.all()).isEmpty();
    assertThat(world.setupIntents.all()).isEmpty();
    assertThat(world.theMethodsStored()).isEmpty();
    assertThat(world.charges.all()).isEmpty();
    assertThat(world.refunds.all()).isEmpty();
    assertThat(world.ephemeralKeys.all()).isEmpty();
    assertNoKey();
  }

  @ParameterizedTest
  @ValueSource(strings = {"retrieve_payment", "confirm_payment", "retrieve_setup", "confirm_setup"})
  void either_account_key_gets_the_same_missing_intent(String route) {
    StripeApiResponse secret = missingObject(route, SECRET_KEY);
    StripeApiResponse publishable = missingObject(route, PUBLISHABLE_KEY);
    assertThat(publishable.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(publishable)).isEqualTo(bodyOf(secret));
    assertNoKey();
  }

  @ParameterizedTest
  @ValueSource(strings = {"retrieve", "detach"})
  void a_missing_method_keeps_the_recorded_connected_account_hint(String action) {
    StripeApiCall call =
        call(
            "retrieve".equals(action) ? "GET" : "POST",
            "/v1/payment_methods/pm_missing" + ("detach".equals(action) ? "/detach" : ""),
            Map.of());
    StripeApiResponse refused =
        "retrieve".equals(action)
            ? world.api().retrievePaymentMethod(call, "pm_missing")
            : world.api().detachPaymentMethod(call, "pm_missing");
    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\",\"doc_url\":\"https://docs.stripe.com/error-codes#resource-missing\",\"message\":\"No such PaymentMethod: 'pm_missing'; It's possible this PaymentMethod exists on one of your connected accounts, in which case you should retry this request on that connected account. Learn more at https://stripe.com/docs/connect/authentication\",\"param\":\"payment_method\"}}");
    assertNoKey();
  }

  @ParameterizedTest
  @ValueSource(strings = {"retrieve", "list_methods"})
  void a_short_lived_key_gets_the_same_per_customer_refusal_when_its_customer_is_gone(
      String route) {
    StripeApiResponse minted =
        world
            .api()
            .createEphemeralKey(
                new StripeApiCall(
                    "POST",
                    "/v1/ephemeral_keys",
                    bearer(SECRET_KEY),
                    Map.of("customer", List.of(THE_CUSTOMER)),
                    Map.of()));
    assertThat(minted.httpStatus()).isEqualTo(200);
    String secret = theValueAt(minted, "secret");
    world.customers.clear(RUN);
    String path = "/v1/customers/" + THE_CUSTOMER;
    StripeApiResponse refused =
        "retrieve".equals(route)
            ? world.api().retrieveCustomer(call("GET", path, Map.of(), secret), THE_CUSTOMER)
            : world
                .api()
                .listCustomerPaymentMethods(
                    call("GET", path + "/payment_methods", Map.of(), secret), THE_CUSTOMER);
    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\",\"doc_url\":\"https://docs.stripe.com/error-codes#resource-missing\",\"message\":\"No such customer: 'cus_the_one_saving_a_card'\",\"param\":\"customer\"}}");
    assertNoKey();
  }

  private StripeApiResponse missingObject(String route, String key) {
    String payment = "/v1/payment_intents/pi_missing";
    String setup = "/v1/setup_intents/seti_missing";
    String customer = "/v1/customers/cus_missing";
    return switch (route) {
      case "retrieve_payment" ->
          world
              .api()
              .retrievePaymentIntent(
                  call("GET", payment, Map.of("client_secret", "pi_missing_secret_a"), key),
                  "pi_missing");
      case "confirm_payment" ->
          world
              .api()
              .confirmPaymentIntent(
                  call(
                      "POST",
                      payment + "/confirm",
                      Map.of("client_secret", "pi_missing_secret_a"),
                      key),
                  "pi_missing");
      case "capture_payment" ->
          world
              .api()
              .capturePaymentIntent(call("POST", payment + "/capture", Map.of()), "pi_missing");
      case "cancel_payment" ->
          world
              .api()
              .cancelPaymentIntent(call("POST", payment + "/cancel", Map.of()), "pi_missing");
      case "retrieve_setup" ->
          world
              .api()
              .retrieveSetupIntent(
                  call("GET", setup, Map.of("client_secret", "seti_missing_secret_a"), key),
                  "seti_missing");
      case "confirm_setup" ->
          world
              .api()
              .confirmSetupIntent(
                  call(
                      "POST",
                      setup + "/confirm",
                      Map.of("client_secret", "seti_missing_secret_a"),
                      key),
                  "seti_missing");
      case "retrieve_customer" ->
          world.api().retrieveCustomer(call("GET", customer, Map.of()), "cus_missing");
      case "update_customer" ->
          world
              .api()
              .updateCustomer(
                  call("POST", customer, Map.of("email", "new@example.com")), "cus_missing");
      case "delete_customer" ->
          world.api().deleteCustomer(call("DELETE", customer, Map.of()), "cus_missing");
      case "list_customer_methods" ->
          world
              .api()
              .listCustomerPaymentMethods(
                  call("GET", customer + "/payment_methods", Map.of()), "cus_missing");
      case "ephemeral_key" ->
          world
              .api()
              .createEphemeralKey(
                  call("POST", "/v1/ephemeral_keys", Map.of("customer", "cus_missing")));
      case "retrieve_charge" ->
          world.api().retrieveCharge(call("GET", "/v1/charges/ch_missing", Map.of()), "ch_missing");
      case "refund_charge" ->
          world.api().createRefund(call("POST", "/v1/refunds", Map.of("charge", "ch_missing")));
      case "refund_payment" ->
          world
              .api()
              .createRefund(call("POST", "/v1/refunds", Map.of("payment_intent", "pi_missing")));
      default -> throw new IllegalArgumentException(route);
    };
  }

  private static StripeApiCall call(String method, String path, Map<String, String> params) {
    return call(method, path, params, SECRET_KEY);
  }

  private static StripeApiCall call(
      String method, String path, Map<String, String> params, String key) {
    Map<String, List<String>> headers = new LinkedHashMap<>(bearer(key));
    headers.put("Idempotency-Key", List.of(AN_IDEMPOTENCY_KEY));
    Map<String, List<String>> values = new LinkedHashMap<>();
    params.forEach((name, value) -> values.put(name, List.of(value)));
    return new StripeApiCall(
        method,
        path,
        headers,
        "GET".equals(method) ? Map.of() : values,
        "GET".equals(method) ? values : Map.of());
  }

  private void assertNoKey() {
    assertThat(
            world
                .keys
                .exchanges(new KeyedExchangeQuery(RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 50))
                .exchanges())
        .isEmpty();
  }
}
