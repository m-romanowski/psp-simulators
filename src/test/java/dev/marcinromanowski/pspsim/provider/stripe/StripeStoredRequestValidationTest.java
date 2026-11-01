package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aPaymentOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aSetupThatConfirmsWithoutARedirect;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theIdOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theValueAt;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.with;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork.Ending;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeStoredRequestValidationTest {
  @Test
  void a_scenario_preempt_keeps_its_transition_without_minting_a_method() {
    StripeCardWorld world = new StripeCardWorld();
    world.answeringInsteadOf("POST /v1/payment_intents");
    StripeApiResponse preempted =
        world.createPayment(
            SECRET_KEY,
            with(with(aPaymentOf("1000"), "confirm", "true"), "payment_method", "pm_card_visa"));
    assertThat(preempted.httpStatus()).isEqualTo(502);
    assertThat(world.units.endings()).containsExactly(Ending.KEPT);
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void both_creates_refuse_a_customer_the_store_cannot_hold_before_minting() {
    StripeCardWorld world = new StripeCardWorld();
    for (String value : List.of("c".repeat(129), "cus_" + Character.toString(0))) {
      StripeApiResponse payment =
          world.createPayment(SECRET_KEY, with(aPaymentOf("1000"), "customer", value));
      StripeApiResponse setup =
          world.createSetup(
              SECRET_KEY, with(aSetupThatConfirmsWithoutARedirect(), "customer", value));
      for (StripeApiResponse response : List.of(payment, setup)) {
        assertThat(response.httpStatus()).isEqualTo(400);
        assertThat(theValueAt(response, "error.param")).isEqualTo("customer");
      }
    }
    assertThat(world.units.endings()).containsOnly(Ending.DISCARDED);
  }

  @Test
  void every_create_and_confirm_refuses_an_unstoreable_method_id() {
    StripeCardWorld world = new StripeCardWorld();
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1000")));
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    for (String value : List.of("p".repeat(129), "pm_" + Character.toString(0))) {
      for (StripeApiResponse response :
          List.of(
              world.createPayment(SECRET_KEY, with(aPaymentOf("1000"), "payment_method", value)),
              world.createSetup(
                  SECRET_KEY, with(aSetupThatConfirmsWithoutARedirect(), "payment_method", value)),
              world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", value)),
              world.confirmSetup(SECRET_KEY, setup, Map.of("payment_method", value)))) {
        assertThat(response.httpStatus()).isEqualTo(400);
        assertThat(theValueAt(response, "error.param")).isEqualTo("payment_method");
      }
    }
    assertThat(world.units.endings()).contains(Ending.KEPT, Ending.DISCARDED);
  }

  @Test
  void unbounded_sql_text_and_billing_text_still_refuse_nul_before_any_write() {
    StripeCardWorld world = new StripeCardWorld();
    for (String parameter : List.of("currency", "receipt_email")) {
      StripeApiResponse refused =
          world.createPayment(
              SECRET_KEY, with(aPaymentOf("1000"), parameter, "x" + Character.toString(0)));
      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(theValueAt(refused, "error.param")).isEqualTo(parameter);
    }
    Map<String, String> card = StripeCardWorld.aVisa();
    StripeApiResponse refused =
        world.createMethod(
            SECRET_KEY, with(card, "billing_details[name]", Character.toString(0) + "payer"));
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(theValueAt(refused, "error.param")).isEqualTo("billing_details[name]");
    StripeApiResponse configuration =
        world.createPayment(
            SECRET_KEY,
            Map.of(
                "amount",
                "1000",
                "currency",
                "usd",
                "payment_method_configuration",
                Character.toString(0) + "pmc_"));
    assertThat(configuration.httpStatus()).isEqualTo(400);
    assertThat(theValueAt(configuration, "error.param")).isEqualTo("payment_method_configuration");
    assertThat(world.units.endings()).containsOnly(Ending.DISCARDED);
  }

  @Test
  void customer_writes_and_ephemeral_creation_refuse_unstoreable_text_before_execution() {
    StripeCardWorld world = new StripeCardWorld();
    String nul = Character.toString(0) + "payer";
    StripeApiCall customer =
        new StripeApiCall(
            "POST",
            "/v1/customers",
            StripeCardWorld.bearer(SECRET_KEY),
            Map.of("email", List.of(nul)));
    StripeApiCall update =
        new StripeApiCall(
            "POST",
            "/v1/customers/" + StripeCardWorld.THE_CUSTOMER,
            StripeCardWorld.bearer(SECRET_KEY),
            Map.of("email", List.of(nul)));
    for (StripeApiResponse refused :
        List.of(
            world.api().createCustomer(customer),
            world.api().updateCustomer(update, StripeCardWorld.THE_CUSTOMER))) {
      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(theValueAt(refused, "error.param")).isEqualTo("email");
    }
    for (String value : List.of("c".repeat(129), nul)) {
      StripeApiResponse refused =
          world
              .api()
              .createEphemeralKey(
                  new StripeApiCall(
                      "POST",
                      "/v1/ephemeral_keys",
                      StripeCardWorld.bearer(SECRET_KEY),
                      Map.of("customer", List.of(value))));
      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(theValueAt(refused, "error.param")).isEqualTo("customer");
    }
    assertThat(world.units.endings()).containsOnly(Ending.DISCARDED);
    assertThat(world.theMethodsStored()).isEmpty();
  }
}
