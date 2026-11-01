package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.ACCOUNT;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PAYMENT_INTENTS;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PAYMENT_METHODS;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SETUP_INTENTS;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_CUSTOMER;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aPaymentOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aSetupThatConfirmsWithoutARedirect;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aVisa;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.flat;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theIdOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theValueAt;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.with;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StripeUnreadParametersTest {

  @ParameterizedTest
  @CsvSource({
    "create_payment,status,requires_payment_method",
    "confirm_payment,status,succeeded",
    "capture_payment,status,succeeded",
    "cancel_payment,status,canceled",
    "retrieve_payment,status,requires_payment_method",
    "retrieve_charge,status,succeeded",
    "refund,status,succeeded",
    "create_customer,email,saved@example.com",
    "retrieve_customer,id,cus_the_one_saving_a_card",
    "update_customer,email,changed@example.com",
    "delete_customer,deleted,true",
    "create_method,card.last4,4242",
    "retrieve_method,id,pm_a_fixture",
    "detach_method,id,pm_a_fixture",
    "list_methods,object,list",
    "list_customer_methods,object,list",
    "create_setup,status,requires_payment_method",
    "confirm_setup,status,succeeded",
    "retrieve_setup,status,requires_payment_method",
    "ephemeral_key,object,ephemeral_key"
  })
  void every_route_accepts_unread_parameters_without_changing_its_answer(
      String route, String field, String value) {
    StripeApiResponse plain = answered(route, false);
    StripeApiResponse withUnread = answered(route, true);
    assertThat(plain.httpStatus()).as(bodyOf(plain)).isEqualTo(200);
    assertThat(withUnread.httpStatus()).as(bodyOf(withUnread)).isEqualTo(200);
    assertThat(flat(withUnread)).containsEntry(field, List.of(value));
    assertThat(bodyOf(withUnread)).isEqualTo(bodyOf(plain));
  }

  private static StripeApiResponse answered(String route, boolean unread) {
    StripeCardWorld world = new StripeCardWorld();
    world.seedAFixture(ACCOUNT, "pm_a_fixture", THE_CUSTOMER);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse paid =
        world.createPayment(
            SECRET_KEY,
            with(with(aPaymentOf("1050"), "confirm", "true"), "payment_method", "pm_card_visa"));
    String charge = theValueAt(paid, "latest_charge");
    String settled = theValueAt(paid, "id");
    String held =
        theIdOf(
            world.createPayment(
                SECRET_KEY,
                with(
                    with(
                        with(aPaymentOf("1050"), "confirm", "true"),
                        "payment_method",
                        "pm_card_visa"),
                    "capture_method",
                    "manual")));
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    StripeApi api = world.api();
    String payments = PAYMENT_INTENTS + "/" + payment;
    String setups = SETUP_INTENTS + "/" + setup;
    String customer = "/v1/customers/" + THE_CUSTOMER;
    String method = PAYMENT_METHODS + "/pm_a_fixture";
    return switch (route) {
      case "create_payment" ->
          api.createPaymentIntent(call("POST", PAYMENT_INTENTS, aPaymentOf("1050"), unread));
      case "confirm_payment" ->
          api.confirmPaymentIntent(
              call("POST", payments + "/confirm", Map.of("payment_method", "pm_card_visa"), unread),
              payment);
      case "capture_payment" ->
          api.capturePaymentIntent(
              call("POST", PAYMENT_INTENTS + "/" + held + "/capture", Map.of(), unread), held);
      case "cancel_payment" ->
          api.cancelPaymentIntent(call("POST", payments + "/cancel", Map.of(), unread), payment);
      case "retrieve_payment" ->
          api.retrievePaymentIntent(call("GET", payments, Map.of(), unread), payment);
      case "retrieve_charge" ->
          api.retrieveCharge(call("GET", "/v1/charges/" + charge, Map.of(), unread), charge);
      case "refund" ->
          api.createRefund(call("POST", "/v1/refunds", Map.of("payment_intent", settled), unread));
      case "create_customer" ->
          api.createCustomer(
              call("POST", "/v1/customers", Map.of("email", "saved@example.com"), unread));
      case "retrieve_customer" ->
          api.retrieveCustomer(call("GET", customer, Map.of(), unread), THE_CUSTOMER);
      case "update_customer" ->
          api.updateCustomer(
              call("POST", customer, Map.of("email", "changed@example.com"), unread), THE_CUSTOMER);
      case "delete_customer" ->
          api.deleteCustomer(call("DELETE", customer, Map.of(), unread), THE_CUSTOMER);
      case "create_method" ->
          api.createPaymentMethod(call("POST", PAYMENT_METHODS, aVisa(), unread));
      case "retrieve_method" ->
          api.retrievePaymentMethod(call("GET", method, Map.of(), unread), "pm_a_fixture");
      case "detach_method" ->
          api.detachPaymentMethod(
              call("POST", method + "/detach", Map.of(), unread), "pm_a_fixture");
      case "list_methods" ->
          api.listPaymentMethods(call("GET", PAYMENT_METHODS, Map.of("type", "card"), unread));
      case "list_customer_methods" ->
          api.listCustomerPaymentMethods(
              call("GET", customer + "/payment_methods", Map.of("type", "card"), unread),
              THE_CUSTOMER);
      case "create_setup" ->
          api.createSetupIntent(
              call("POST", SETUP_INTENTS, aSetupThatConfirmsWithoutARedirect(), unread));
      case "confirm_setup" ->
          api.confirmSetupIntent(
              call("POST", setups + "/confirm", Map.of("payment_method", "pm_card_visa"), unread),
              setup);
      case "retrieve_setup" ->
          api.retrieveSetupIntent(call("GET", setups, Map.of(), unread), setup);
      case "ephemeral_key" ->
          api.createEphemeralKey(
              call("POST", "/v1/ephemeral_keys", Map.of("customer", THE_CUSTOMER), unread));
      default -> throw new IllegalArgumentException(route);
    };
  }

  private static StripeApiCall call(
      String method, String path, Map<String, String> parameters, boolean unread) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    parameters.forEach((name, value) -> values.put(name, List.of(value)));
    if (unread) {
      values.put("an_unread_parameter", List.of("a value"));
      values.put("another_unread_parameter[nested]", List.of("a value"));
      values.put("an_unread_list[]", List.of("one", "two"));
      values.put(
          "billing_details[address][invalid_thing]", List.of("the recorded unknown parameter"));
    }
    return new StripeApiCall(
        method,
        path,
        bearer(SECRET_KEY),
        "GET".equals(method) ? Map.of() : values,
        "GET".equals(method) ? values : Map.of());
  }
}
