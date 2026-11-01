package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.AN_IDEMPOTENCY_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.NOW;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PAYMENT_INTENTS;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.RUN;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aPaymentOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aSetupThatConfirmsWithoutARedirect;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.flat;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theIdOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theValueAt;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.with;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class StripeConfirmationValidationTest {

  private final StripeCardWorld world = new StripeCardWorld();

  @ParameterizedTest
  @CsvSource({
    "off_session,true,The parameter `off_session` can only be used with `confirm=true`.",
    "off_session,false,The parameter `off_session` can only be used with `confirm=true`.",
    "off_session,one_off,The parameter `off_session` can only be used with `confirm=true`.",
    "off_session,recurring,The parameter `off_session` can only be used with `confirm=true`.",
    "off_session,'',The parameter `off_session` can only be used with `confirm=true`.",
    "return_url,https://shop.example/return,The parameter `return_url` can only be used with `confirm=true`.",
    "return_url,'',The parameter `return_url` can only be used with `confirm=true`."
  })
  void confirmation_only_parameters_require_an_immediate_confirmation(
      String param, String value, String message) {
    for (Map<String, String> form :
        List.of(
            with(aPaymentOf("1050"), param, value),
            with(with(aPaymentOf("1050"), "confirm", "false"), param, value))) {
      StripeApiResponse refused = createUnderTheKey(form);
      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(bodyOf(refused))
          .isEqualTo(
              "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\""
                  + message
                  + "\",\"param\":\""
                  + param
                  + "\"}}");
      assertNoWrites();
    }
    assertThat(createUnderTheKey(aPaymentOf("1050")).httpStatus()).isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "one_off", "recurring"})
  void every_published_off_session_value_waives_the_dynamic_return_url_on_create_and_confirm(
      String offSession) {
    Map<String, String> dynamic = Map.of("amount", "1050", "currency", "usd");
    StripeApiResponse created =
        world.createPayment(
            SECRET_KEY,
            with(
                with(with(dynamic, "confirm", "true"), "payment_method", "pm_card_visa"),
                "off_session",
                offSession));
    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(theValueAt(created, "status")).isEqualTo("succeeded");
    String intent = theIdOf(world.createPayment(SECRET_KEY, dynamic));
    StripeApiResponse confirmed =
        world.confirmPayment(
            SECRET_KEY,
            intent,
            Map.of("payment_method", "pm_card_visa", "off_session", offSession));
    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(theValueAt(confirmed, "status")).isEqualTo("succeeded");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "false")
  void on_session_confirmation_still_requires_a_return_url(String offSession) {
    String intent =
        theIdOf(world.createPayment(SECRET_KEY, Map.of("amount", "1050", "currency", "usd")));
    Map<String, String> form =
        with(Map.of("payment_method", "pm_card_visa"), "off_session", offSession);
    StripeApiResponse refused = world.confirmPaymentUnderTheKey(SECRET_KEY, intent, form);
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(theValueAt(refused, "error.param")).isEqualTo("return_url");
    assertThat(world.intents.find(RUN, intent).orElseThrow().statusAt(NOW).wireValue())
        .isEqualTo("requires_payment_method");
    assertThat(world.theMethodsStored()).isEmpty();
    assertNoKey();
    assertThat(
            world
                .confirmPaymentUnderTheKey(
                    SECRET_KEY, intent, with(form, "return_url", "https://shop.example/return"))
                .httpStatus())
        .isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"yes", "0", "on_session", "TRUE", "", "sometimes"})
  void an_invalid_off_session_is_refused_on_both_confirmation_paths(String offSession) {
    Map<String, String> form =
        with(
            with(with(aPaymentOf("1050"), "confirm", "true"), "payment_method", "pm_card_visa"),
            "off_session",
            offSession);
    StripeApiResponse create = createUnderTheKey(form);
    assertInvalidOffSession(create);
    assertNoWrites();
    String intent = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse confirm =
        world.confirmPaymentUnderTheKey(
            SECRET_KEY,
            intent,
            Map.of(
                "payment_method",
                "pm_card_visa",
                "off_session",
                offSession,
                "return_url",
                "https://shop.example/return"));
    assertInvalidOffSession(confirm);
    assertThat(theValueAt(confirm, "error.payment_intent.status"))
        .isEqualTo("requires_payment_method");
    assertThat(world.intents.find(RUN, intent).orElseThrow().paymentMethod()).isNull();
    assertThat(world.theMethodsStored()).isEmpty();
    assertThat(world.charges.all()).isEmpty();
    assertNoKey();
    assertThat(
            world
                .confirmPaymentUnderTheKey(
                    SECRET_KEY,
                    intent,
                    Map.of("payment_method", "pm_card_visa", "off_session", "false"))
                .httpStatus())
        .isEqualTo(200);
  }

  @ParameterizedTest
  @ValueSource(strings = {"off_session[]", "off_session[value]"})
  void a_structured_off_session_is_not_a_boolean_or_enum(String parameter) {
    String intent = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse refused =
        world.confirmPaymentUnderTheKey(
            SECRET_KEY, intent, Map.of("payment_method", "pm_card_visa", parameter, "true"));
    assertInvalidOffSession(refused);
    assertThat(world.intents.find(RUN, intent).orElseThrow().paymentMethod()).isNull();
    assertThat(world.theMethodsStored()).isEmpty();
    assertNoKey();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "false")
  void a_return_url_allows_on_session_create_and_confirm(String offSession) {
    StripeApiResponse created =
        world.createPayment(
            SECRET_KEY,
            with(
                Map.of(
                    "amount",
                    "1050",
                    "currency",
                    "usd",
                    "confirm",
                    "true",
                    "payment_method",
                    "pm_card_visa",
                    "return_url",
                    "https://shop.example/return"),
                "off_session",
                offSession));
    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(theValueAt(created, "status")).isEqualTo("succeeded");
  }

  @Test
  void no_payment_method_is_the_recorded_unexpected_state_with_the_unchanged_intent() {
    String intent = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse refused = world.confirmPaymentUnderTheKey(SECRET_KEY, intent, Map.of());
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.type", List.of("invalid_request_error"))
        .containsEntry("error.code", List.of("payment_intent_unexpected_state"))
        .containsEntry(
            "error.doc_url",
            List.of("https://docs.stripe.com/error-codes#payment-intent-unexpected-state"))
        .containsEntry(
            "error.message",
            List.of(
                "You cannot confirm this PaymentIntent because it's missing a payment method. You can either update the PaymentIntent with a payment method and then confirm it again, or confirm it again directly with a payment method or ConfirmationToken."))
        .containsEntry("error.payment_intent.id", List.of("pi_sim_0000000000000001"))
        .containsEntry("error.payment_intent.status", List.of("requires_payment_method"))
        .containsEntry("error.payment_intent.amount", List.of("1050"))
        .containsEntry("error.payment_intent.payment_method", List.of())
        .doesNotContainKey("error.param");
    assertThat(world.intents.find(RUN, intent).orElseThrow().paymentMethod()).isNull();
    assertThat(world.theMethodsStored()).isEmpty();
    assertThat(world.charges.all()).isEmpty();
    assertNoKey();
    assertThat(
            world
                .confirmPaymentUnderTheKey(
                    SECRET_KEY, intent, Map.of("payment_method", "pm_card_visa"))
                .httpStatus())
        .isEqualTo(200);
  }

  @Test
  void an_unrecorded_setup_without_a_method_keeps_the_documented_missing_parameter_decision() {
    String intent = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    StripeApiResponse refused = world.confirmSetupUnderTheKey(SECRET_KEY, intent, Map.of());
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"parameter_missing\",\"doc_url\":\"https://docs.stripe.com/error-codes#parameter-missing\",\"message\":\"Missing required param: payment_method.\",\"param\":\"payment_method\"}}");
    assertThat(world.setupIntents.find(RUN, intent).orElseThrow().status().wireValue())
        .isEqualTo("requires_payment_method");
    assertThat(world.theMethodsStored()).isEmpty();
    assertNoKey();
    assertThat(
            world
                .confirmSetupUnderTheKey(
                    SECRET_KEY, intent, Map.of("payment_method", "pm_card_visa"))
                .httpStatus())
        .isEqualTo(200);
  }

  private static void assertInvalidOffSession(StripeApiResponse refused) {
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.type", List.of("invalid_request_error"))
        .containsEntry("error.param", List.of("off_session"))
        .containsEntry(
            "error.message",
            List.of("Invalid off_session: must be one of true, false, one_off, recurring"))
        .doesNotContainKeys("error.code", "error.doc_url");
  }

  private StripeApiResponse createUnderTheKey(Map<String, String> form) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    form.forEach((name, value) -> values.put(name, List.of(value)));
    Map<String, List<String>> headers = new LinkedHashMap<>(bearer(SECRET_KEY));
    headers.put("Idempotency-Key", List.of(AN_IDEMPOTENCY_KEY));
    return world
        .api()
        .createPaymentIntent(new StripeApiCall("POST", PAYMENT_INTENTS, headers, values, Map.of()));
  }

  private void assertNoWrites() {
    assertThat(world.intents.all()).isEmpty();
    assertThat(world.setupIntents.all()).isEmpty();
    assertThat(world.theMethodsStored()).isEmpty();
    assertThat(world.charges.all()).isEmpty();
    assertNoKey();
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
