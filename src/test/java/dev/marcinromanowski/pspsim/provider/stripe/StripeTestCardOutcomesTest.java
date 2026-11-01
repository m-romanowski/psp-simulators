package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.ACCOUNT;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PAYMENT_INTENTS;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.RUN;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_CUSTOMER;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_FIRST_MINTED_METHOD;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_SECOND_MINTED_METHOD;
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

import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class StripeTestCardOutcomesTest {
  private final StripeCardWorld world = new StripeCardWorld();

  @ParameterizedTest
  @CsvSource({
    "4000000000000002,card_declined,generic_decline,visa_chargeDeclined",
    "4000000000000002,card_declined,generic_decline,chargeDeclined",
    "4000000000009995,card_declined,insufficient_funds,visa_chargeDeclinedInsufficientFunds",
    "4000000000009995,card_declined,insufficient_funds,chargeDeclinedInsufficientFunds",
    "4000000000009987,card_declined,lost_card,visa_chargeDeclinedLostCard",
    "4000000000009987,card_declined,lost_card,chargeDeclinedLostCard",
    "4000000000009979,card_declined,stolen_card,visa_chargeDeclinedStolenCard",
    "4000000000009979,card_declined,stolen_card,chargeDeclinedStolenCard",
    "4000000000000069,expired_card,,chargeDeclinedExpiredCard",
    "4000000000000069,expired_card,,visa_chargeDeclinedExpiredCard",
    "4000000000000127,incorrect_cvc,,chargeDeclinedIncorrectCvc",
    "4000000000000127,incorrect_cvc,,visa_chargeDeclinedIncorrectCvc",
    "4000000000000119,processing_error,,chargeDeclinedProcessingError",
    "4000000000000119,processing_error,,visa_chargeDeclinedProcessingError",
    "4000000000006975,card_declined,card_velocity_exceeded,visa_chargeDeclinedVelocityLimitExceeded",
    "4000000000006975,card_declined,card_velocity_exceeded,chargeDeclinedVelocityLimitExceeded",
    "4000000000000341,card_declined,generic_decline,chargeCustomerFail",
    "4000000000000341,card_declined,generic_decline,visa_chargeCustomerFail",
  })
  void each_number_declines_a_charge_after_creation_without_claiming_to_attach_it(
      String number, String code, @Nullable String decline, String name) {
    String method = theIdOf(world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", number)));
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse answer =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", method));
    assertDecline(answer, code, decline, "payment_intent", "last_payment_error");
    assertThat(theValueAt(answer, "error.payment_method.id")).isEqualTo(method);
    assertThat(world.theMethodStored(method).customerId()).isNull();
    assertThat(world.intents.find(RUN, payment).orElseThrow().status())
        .isEqualTo(StripeIntentStatus.REQUIRES_PAYMENT_METHOD);
    StripeApiResponse retrieved =
        world
            .api()
            .retrievePaymentIntent(
                new StripeApiCall(
                    "GET", PAYMENT_INTENTS + "/" + payment, bearer(SECRET_KEY), Map.of(), Map.of()),
                payment);
    assertThat(theValueAt(retrieved, "last_payment_error.payment_method.id")).isEqualTo(method);
    assertThat(flat(retrieved).get("payment_method")).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "4000000000000002,card_declined,generic_decline,visa_chargeDeclined",
    "4000000000000002,card_declined,generic_decline,chargeDeclined",
    "4000000000009995,card_declined,insufficient_funds,visa_chargeDeclinedInsufficientFunds",
    "4000000000009995,card_declined,insufficient_funds,chargeDeclinedInsufficientFunds",
    "4000000000009987,card_declined,lost_card,visa_chargeDeclinedLostCard",
    "4000000000009987,card_declined,lost_card,chargeDeclinedLostCard",
    "4000000000009979,card_declined,stolen_card,visa_chargeDeclinedStolenCard",
    "4000000000009979,card_declined,stolen_card,chargeDeclinedStolenCard",
    "4000000000000069,expired_card,,chargeDeclinedExpiredCard",
    "4000000000000069,expired_card,,visa_chargeDeclinedExpiredCard",
    "4000000000000127,incorrect_cvc,,chargeDeclinedIncorrectCvc",
    "4000000000000127,incorrect_cvc,,visa_chargeDeclinedIncorrectCvc",
    "4000000000000119,processing_error,,chargeDeclinedProcessingError",
    "4000000000000119,processing_error,,visa_chargeDeclinedProcessingError",
    "4000000000006975,card_declined,card_velocity_exceeded,visa_chargeDeclinedVelocityLimitExceeded",
    "4000000000006975,card_declined,card_velocity_exceeded,chargeDeclinedVelocityLimitExceeded",
    "4000000000000341,card_declined,generic_decline,chargeCustomerFail",
    "4000000000000341,card_declined,generic_decline,visa_chargeCustomerFail",
  })
  void each_test_id_declines_a_confirming_creation_and_keeps_its_new_method(
      String number, String code, @Nullable String decline, String name) {
    Map<String, String> form =
        with(with(aPaymentOf("1050"), "confirm", "true"), "payment_method", "pm_card_" + name);
    StripeApiResponse answer = world.createPayment(SECRET_KEY, form);
    assertDecline(answer, code, decline, "payment_intent", "last_payment_error");
    assertThat(theValueAt(answer, "error.payment_method.id")).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).customerId()).isNull();
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse confirmed =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_" + name));
    assertDecline(confirmed, code, decline, "payment_intent", "last_payment_error");

    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).copiedFrom())
        .isEqualTo("pm_card_" + name);
    String charge = theValueAt(answer, "error.charge");
    StripeApiResponse charged =
        world
            .api()
            .retrieveCharge(
                new StripeApiCall(
                    "GET", "/v1/charges/" + charge, bearer(SECRET_KEY), Map.of(), Map.of()),
                charge);
    assertThat(theValueAt(charged, "status")).isEqualTo("failed");
    assertThat(theValueAt(charged, "outcome.type")).isEqualTo("issuer_declined");
  }

  @ParameterizedTest
  @CsvSource({
    "4000000000000002,card_declined,generic_decline,visa_chargeDeclined",
    "4000000000000002,card_declined,generic_decline,chargeDeclined",
    "4000000000009995,card_declined,insufficient_funds,visa_chargeDeclinedInsufficientFunds",
    "4000000000009995,card_declined,insufficient_funds,chargeDeclinedInsufficientFunds",
    "4000000000009987,card_declined,lost_card,visa_chargeDeclinedLostCard",
    "4000000000009987,card_declined,lost_card,chargeDeclinedLostCard",
    "4000000000009979,card_declined,stolen_card,visa_chargeDeclinedStolenCard",
    "4000000000009979,card_declined,stolen_card,chargeDeclinedStolenCard",
    "4000000000000069,expired_card,,chargeDeclinedExpiredCard",
    "4000000000000069,expired_card,,visa_chargeDeclinedExpiredCard",
    "4000000000000127,incorrect_cvc,,chargeDeclinedIncorrectCvc",
    "4000000000000127,incorrect_cvc,,visa_chargeDeclinedIncorrectCvc",
    "4000000000000119,processing_error,,chargeDeclinedProcessingError",
    "4000000000000119,processing_error,,visa_chargeDeclinedProcessingError",
    "4000000000006975,card_declined,card_velocity_exceeded,visa_chargeDeclinedVelocityLimitExceeded",
    "4000000000006975,card_declined,card_velocity_exceeded,chargeDeclinedVelocityLimitExceeded",
    "4000000000000341,card_declined,generic_decline,chargeCustomerFail",
    "4000000000000341,card_declined,generic_decline,visa_chargeCustomerFail",
  })
  void each_number_and_test_id_has_the_published_setup_and_attachment_outcome(
      String number, String code, @Nullable String decline, String name) {
    for (boolean raw : List.of(true, false)) {
      String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
      String method =
          raw
              ? theIdOf(world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", number)))
              : "pm_card_" + name;
      StripeApiResponse answer =
          world.confirmSetup(SECRET_KEY, setup, Map.of("payment_method", method));
      if (number.equals("4000000000000341")) {
        assertThat(answer.httpStatus()).as(bodyOf(answer)).isEqualTo(200);
        assertThat(world.theMethodStored(theValueAt(answer, "payment_method")).customerId())
            .isEqualTo(THE_CUSTOMER);
      } else {
        assertDecline(answer, code, decline, "setup_intent", "last_setup_error");
        assertThat(
                world.theMethodStored(theValueAt(answer, "error.payment_method.id")).customerId())
            .isNull();
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "4000000000000002,card_declined,generic_decline,visa_chargeDeclined",
    "4000000000000002,card_declined,generic_decline,chargeDeclined",
    "4000000000009995,card_declined,insufficient_funds,visa_chargeDeclinedInsufficientFunds",
    "4000000000009995,card_declined,insufficient_funds,chargeDeclinedInsufficientFunds",
    "4000000000009987,card_declined,lost_card,visa_chargeDeclinedLostCard",
    "4000000000009987,card_declined,lost_card,chargeDeclinedLostCard",
    "4000000000009979,card_declined,stolen_card,visa_chargeDeclinedStolenCard",
    "4000000000009979,card_declined,stolen_card,chargeDeclinedStolenCard",
    "4000000000000069,expired_card,,chargeDeclinedExpiredCard",
    "4000000000000069,expired_card,,visa_chargeDeclinedExpiredCard",
    "4000000000000127,incorrect_cvc,,chargeDeclinedIncorrectCvc",
    "4000000000000127,incorrect_cvc,,visa_chargeDeclinedIncorrectCvc",
    "4000000000000119,processing_error,,chargeDeclinedProcessingError",
    "4000000000000119,processing_error,,visa_chargeDeclinedProcessingError",
    "4000000000006975,card_declined,card_velocity_exceeded,visa_chargeDeclinedVelocityLimitExceeded",
    "4000000000006975,card_declined,card_velocity_exceeded,chargeDeclinedVelocityLimitExceeded",
    "4000000000000341,card_declined,generic_decline,chargeCustomerFail",
    "4000000000000341,card_declined,generic_decline,visa_chargeCustomerFail",
  })
  void an_explicit_success_overrides_each_card_on_every_confirmation_route(
      String number, String code, @Nullable String decline, String name) {
    for (String operation :
        List.of(
            StripeOperations.CREATE,
            StripeOperations.CONFIRM,
            StripeOperations.CONFIRM_SETUP_INTENT)) {
      world.scenarioRules.add(
          new NewScenarioRule(
              StripeApi.STRIPE,
              RUN,
              0,
              ScenarioMatch.anyRequest().naming(operation),
              new ScenarioAction.FixedSuccess(),
              null));
    }
    String method = theIdOf(world.createMethod(SECRET_KEY, with(aVisa(), "card[number]", number)));
    StripeApiResponse created =
        world.createPayment(
            SECRET_KEY,
            with(with(aPaymentOf("1050"), "confirm", "true"), "payment_method", method));
    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    assertThat(
            world
                .confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_" + name))
                .httpStatus())
        .isEqualTo(200);
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    StripeApiResponse attached =
        world.confirmSetup(SECRET_KEY, setup, Map.of("payment_method", "pm_card_" + name));
    assertThat(attached.httpStatus()).as(bodyOf(attached)).isEqualTo(200);
    assertThat(world.theMethodStored(theValueAt(attached, "payment_method")).customerId())
        .isEqualTo(THE_CUSTOMER);
  }

  @Test
  void a_matching_failure_rule_overrides_a_cards_different_decline_and_a_successful_setup() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    world.declining(StripeOperations.CREATE, StripeDecline.LOST_CARD);
    world.declining(StripeOperations.CONFIRM, StripeDecline.STOLEN_CARD);
    world.declining(StripeOperations.CONFIRM_SETUP_INTENT, StripeDecline.INSUFFICIENT_FUNDS);
    StripeApiResponse created =
        world.createPayment(
            SECRET_KEY,
            with(
                with(aPaymentOf("1050"), "confirm", "true"),
                "payment_method",
                "pm_card_chargeDeclined"));
    assertThat(theValueAt(created, "error.decline_code")).isEqualTo("lost_card");
    assertThat(
            theValueAt(
                world.confirmPayment(
                    SECRET_KEY, payment, Map.of("payment_method", "pm_card_chargeDeclined")),
                "error.decline_code"))
        .isEqualTo("stolen_card");
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    StripeApiResponse refused =
        world.confirmSetup(
            SECRET_KEY, setup, Map.of("payment_method", "pm_card_chargeCustomerFail"));
    assertThat(theValueAt(refused, "error.decline_code")).isEqualTo("insufficient_funds");
    assertThat(world.theMethodStored(theValueAt(refused, "error.payment_method.id")).customerId())
        .isNull();
  }

  @Test
  void a_card_authorized_by_an_explicit_rule_has_no_published_second_decline_at_capture() {
    world.scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CREATE),
            new ScenarioAction.FixedSuccess(),
            null));
    String payment =
        theIdOf(
            world.createPayment(
                SECRET_KEY,
                with(
                    with(with(aPaymentOf("1050"), "confirm", "true"), "capture_method", "manual"),
                    "payment_method",
                    "pm_card_chargeDeclined")));
    StripeApiResponse captured =
        world
            .api()
            .capturePaymentIntent(
                new StripeApiCall(
                    "POST",
                    PAYMENT_INTENTS + "/" + payment + "/capture",
                    bearer(SECRET_KEY),
                    Map.of()),
                payment);
    assertThat(theValueAt(captured, "status")).isEqualTo("succeeded");
  }

  @Test
  void a_decline_keeps_the_charge_reason_when_a_successful_retry_changes_the_intent() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse failed =
        world.confirmPayment(
            SECRET_KEY,
            payment,
            Map.of("payment_method", "pm_card_chargeDeclinedInsufficientFunds"));
    String charge = theValueAt(failed, "error.charge");
    world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));
    StripeApiResponse oldCharge =
        world
            .api()
            .retrieveCharge(
                new StripeApiCall(
                    "GET", "/v1/charges/" + charge, bearer(SECRET_KEY), Map.of(), Map.of()),
                charge);
    assertThat(theValueAt(oldCharge, "outcome.reason")).isEqualTo("insufficient_funds");
    assertThat(theValueAt(oldCharge, "outcome.seller_message"))
        .isEqualTo("Your card has insufficient funds.");
    assertThat(theValueAt(oldCharge, "status")).isEqualTo("failed");
  }

  @Test
  void a_failed_method_is_kept_in_the_error_but_a_retry_must_name_a_method_again() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_chargeDeclined"));
    StripeApiResponse retry = world.confirmPayment(SECRET_KEY, payment, Map.of());
    assertThat(retry.httpStatus()).isEqualTo(400);
    assertThat(theValueAt(retry, "error.code")).isEqualTo("parameter_missing");
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    world.confirmSetup(SECRET_KEY, setup, Map.of("payment_method", "pm_card_chargeDeclined"));
    StripeApiResponse setupRetry = world.confirmSetup(SECRET_KEY, setup, Map.of());
    assertThat(setupRetry.httpStatus()).isEqualTo(400);
    assertThat(theValueAt(setupRetry, "error.code")).isEqualTo("parameter_missing");
    assertThat(world.theMethodsStored())
        .extracting(StripePaymentMethod::paymentMethodId)
        .containsExactly(THE_FIRST_MINTED_METHOD, THE_SECOND_MINTED_METHOD);
  }

  @ParameterizedTest
  @EnumSource(StripeTestCardBehavior.class)
  void no_published_decline_card_introduces_a_second_failure_at_capture(
      StripeTestCardBehavior behavior) {
    assertThat(behavior.outcomeOf(StripeOperations.CAPTURE)).isNull();
  }

  @Test
  void
      a_fixture_held_under_a_published_decline_id_keeps_its_implicit_outcome_until_a_rule_overrides_it() {
    world.seedAFixture(ACCOUNT, "pm_card_chargeDeclined", null);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse declined =
        world.confirmPayment(
            SECRET_KEY, payment, Map.of("payment_method", "pm_card_chargeDeclined"));
    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(theValueAt(declined, "error.decline_code")).isEqualTo("generic_decline");
    assertThat(theValueAt(declined, "error.payment_method.id")).isEqualTo("pm_card_chargeDeclined");
    world.scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CONFIRM),
            new ScenarioAction.FixedSuccess(),
            null));
    assertThat(
            world
                .confirmPayment(
                    SECRET_KEY, payment, Map.of("payment_method", "pm_card_chargeDeclined"))
                .httpStatus())
        .isEqualTo(200);
    assertThat(world.theMethodsStored())
        .extracting(StripePaymentMethod::paymentMethodId)
        .containsExactly("pm_card_chargeDeclined");
  }

  @Test
  void an_omitted_cvc_skips_the_published_incorrect_cvc_check() {
    String method =
        theIdOf(
            world.createMethod(
                SECRET_KEY,
                with(with(aVisa(), "card[number]", "4000000000000127"), "card[cvc]", null)));
    StripeApiResponse answer =
        world.createPayment(
            SECRET_KEY,
            with(with(aPaymentOf("1050"), "confirm", "true"), "payment_method", method));
    assertThat(answer.httpStatus()).as(bodyOf(answer)).isEqualTo(200);
  }

  @Test
  void creating_an_intent_without_confirming_does_not_try_the_card() {
    StripeApiResponse answer =
        world.createPayment(
            SECRET_KEY, with(aPaymentOf("1050"), "payment_method", "pm_card_chargeDeclined"));
    assertThat(theValueAt(answer, "status")).isEqualTo("requires_confirmation");
    String payment = theIdOf(answer);
    assertThat(world.confirmPayment(SECRET_KEY, payment, Map.of()).httpStatus()).isEqualTo(402);
  }

  @Test
  void a_rule_for_another_operation_leaves_the_card_in_control_and_the_decline_replays() {
    world.declining(StripeOperations.CONFIRM_SETUP_INTENT, StripeDecline.EXPIRED_CARD);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse first =
        world.confirmPaymentUnderTheKey(
            SECRET_KEY,
            payment,
            Map.of("payment_method", "pm_card_chargeDeclinedInsufficientFunds"));
    StripeApiResponse replay =
        world.confirmPaymentUnderTheKey(
            SECRET_KEY,
            payment,
            Map.of("payment_method", "pm_card_chargeDeclinedInsufficientFunds"));
    assertThat(theValueAt(first, "error.decline_code")).isEqualTo("insufficient_funds");
    assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
    assertThat(world.theMethodsStored())
        .extracting(StripePaymentMethod::paymentMethodId)
        .containsExactly(THE_FIRST_MINTED_METHOD);
  }

  @ParameterizedTest
  @CsvSource({"4000000000000127,0127", "4000000000009995,9995"})
  void omitting_cvc_keeps_the_published_card_instead_of_falling_back_to_an_unknown_card(
      String number, String last4) {
    StripeApiResponse created =
        world.createMethod(
            SECRET_KEY, with(with(aVisa(), "card[number]", number), "card[cvc]", null));
    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(theValueAt(created, "card.brand")).isEqualTo("visa");
    assertThat(theValueAt(created, "card.display_brand")).isEqualTo("visa");
    assertThat(theValueAt(created, "card.last4")).isEqualTo(last4);
    assertThat(theValueAt(created, "card.exp_month")).isEqualTo("10");
    assertThat(theValueAt(created, "card.exp_year")).isEqualTo("2028");
    assertThat(flat(created).get("card.networks.available")).containsExactly("visa");
    String method = theIdOf(created);
    StripeApiResponse charged =
        world.createPayment(
            SECRET_KEY,
            with(with(aPaymentOf("1050"), "confirm", "true"), "payment_method", method));
    if (number.equals("4000000000000127")) {
      assertThat(theValueAt(charged, "status")).isEqualTo("succeeded");
    } else {
      assertThat(charged.httpStatus()).isEqualTo(402);
      assertThat(theValueAt(charged, "error.decline_code")).isEqualTo("insufficient_funds");
    }
  }

  @Test
  void a_confirming_creation_keeps_the_decline_reason_on_its_charge() {
    StripeApiResponse declined =
        world.createPayment(
            SECRET_KEY,
            with(
                with(aPaymentOf("1050"), "confirm", "true"),
                "payment_method",
                "pm_card_chargeDeclinedInsufficientFunds"));
    assertThat(declined.httpStatus()).isEqualTo(402);
    String charge = theValueAt(declined, "error.charge");
    StripeApiResponse retrieved =
        world
            .api()
            .retrieveCharge(
                new StripeApiCall(
                    "GET", "/v1/charges/" + charge, bearer(SECRET_KEY), Map.of(), Map.of()),
                charge);
    assertThat(theValueAt(retrieved, "status")).isEqualTo("failed");
    assertThat(theValueAt(retrieved, "outcome.reason")).isEqualTo("insufficient_funds");
    assertThat(theValueAt(retrieved, "outcome.seller_message"))
        .isEqualTo("Your card has insufficient funds.");
    assertThat(world.charges.find(RUN, charge).orElseThrow().failure())
        .isEqualTo(StripeDecline.INSUFFICIENT_FUNDS);
  }

  @Test
  void a_declined_confirmation_renders_the_new_method_in_both_error_positions() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    StripeApiResponse declined =
        world.confirmPayment(
            SECRET_KEY,
            payment,
            Map.of(
                "payment_method_data[type]", "card",
                "payment_method_data[card][number]", "4000000000009995",
                "payment_method_data[card][exp_month]", "12",
                "payment_method_data[card][exp_year]", "2034",
                "payment_method_data[billing_details][name]", "Buyer of the declined order",
                "payment_method_data[metadata][order]", "declined-order"));
    assertThat(declined.httpStatus()).isEqualTo(402);
    for (String prefix :
        List.of("error.payment_method", "error.payment_intent.last_payment_error.payment_method")) {
      assertThat(theValueAt(declined, prefix + ".id")).isEqualTo("pm_sim_00000000000000000001");
      assertThat(theValueAt(declined, prefix + ".card.brand")).isEqualTo("visa");
      assertThat(theValueAt(declined, prefix + ".card.last4")).isEqualTo("9995");
      assertThat(theValueAt(declined, prefix + ".card.exp_month")).isEqualTo("12");
      assertThat(theValueAt(declined, prefix + ".card.exp_year")).isEqualTo("2034");
      assertThat(theValueAt(declined, prefix + ".billing_details.name"))
          .isEqualTo("Buyer of the declined order");
      assertThat(theValueAt(declined, prefix + ".metadata.order")).isEqualTo("declined-order");
    }
  }

  @Test
  void a_parameter_refusal_after_a_failed_setup_keeps_the_failed_method_only_in_the_last_error() {
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    assertThat(
            world
                .confirmSetup(
                    SECRET_KEY,
                    setup,
                    Map.of("payment_method", "pm_card_chargeDeclinedInsufficientFunds"))
                .httpStatus())
        .isEqualTo(402);
    StripeApiResponse refused =
        world.confirmSetup(SECRET_KEY, setup, Map.of("payment_method_data[type]", "card"));
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(theValueAt(refused, "error.param")).isEqualTo("payment_method_data[card]");
    assertThat(theValueAt(refused, "error.setup_intent.status"))
        .isEqualTo("requires_payment_method");
    assertThat(flat(refused).get("error.setup_intent.payment_method")).isEmpty();
    assertThat(theValueAt(refused, "error.setup_intent.last_setup_error.payment_method.id"))
        .isEqualTo("pm_sim_00000000000000000001");
    assertThat(theValueAt(refused, "error.setup_intent.last_setup_error.payment_method.card.last4"))
        .isEqualTo("9995");
    assertThat(flat(refused)).doesNotContainKey("error.setup_intent.client_secret");
  }

  @Test
  void a_parameter_refusal_before_setup_keeps_the_method_the_intent_has_not_tried() {
    String setup =
        theIdOf(
            world.createSetup(
                SECRET_KEY,
                with(aSetupThatConfirmsWithoutARedirect(), "payment_method", "pm_card_visa")));
    StripeApiResponse refused =
        world.confirmSetup(SECRET_KEY, setup, Map.of("payment_method_data[type]", "card"));
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(theValueAt(refused, "error.setup_intent.payment_method"))
        .isEqualTo("pm_sim_00000000000000000001");
    assertThat(flat(refused).get("error.setup_intent.last_setup_error")).isEmpty();
  }

  private static void assertDecline(
      StripeApiResponse answer, String code, @Nullable String decline, String intent, String last) {
    assertThat(answer.httpStatus()).as(bodyOf(answer)).isEqualTo(402);
    assertThat(theValueAt(answer, "error.type")).isEqualTo("card_error");
    assertThat(theValueAt(answer, "error.code")).isEqualTo(code);
    String message =
        switch (code) {
          case "card_declined" ->
              "insufficient_funds".equals(decline)
                  ? "Your card has insufficient funds."
                  : "Your card was declined.";
          case "expired_card" -> "Your card has expired.";
          case "incorrect_cvc" -> "Your card's security code is incorrect.";
          case "processing_error" ->
              "An error occurred while processing your card. Try again in a little bit.";
          default -> throw new IllegalArgumentException(code);
        };
    assertThat(theValueAt(answer, "error.message")).isEqualTo(message);
    assertThat(theValueAt(answer, "error." + intent + "." + last + ".message")).isEqualTo(message);
    assertThat(theValueAt(answer, "error." + intent + "." + last + ".code")).isEqualTo(code);

    assertThat(flat(answer).get("error.decline_code"))
        .isEqualTo(decline == null ? null : List.of(decline));
    assertThat(theValueAt(answer, "error." + intent + ".status"))
        .isEqualTo("requires_payment_method");
    assertThat(flat(answer).get("error." + intent + ".payment_method")).isEmpty();
    assertThat(theValueAt(answer, "error." + intent + "." + last + ".payment_method.id"))
        .isEqualTo(theValueAt(answer, "error.payment_method.id"));
    assertThat(flat(answer).get("error.param"))
        .isEqualTo(code.equals("incorrect_cvc") ? List.of("cvc") : null);
  }
}
