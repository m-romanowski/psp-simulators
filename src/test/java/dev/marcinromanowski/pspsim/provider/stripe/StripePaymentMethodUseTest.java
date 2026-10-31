package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.ACCOUNT;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.ANOTHER_ACCOUNT;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.PUBLISHABLE_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.SECRET_KEY;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_CUSTOMER;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_FIRST_MINTED_METHOD;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.THE_SECOND_MINTED_METHOD;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aPaymentOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.aSetupThatConfirmsWithoutARedirect;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.flat;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theIdOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.theValueAt;
import static dev.marcinromanowski.pspsim.provider.stripe.StripeCardWorld.with;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StripePaymentMethodUseTest {

  private final StripeCardWorld world = new StripeCardWorld();

  @Test
  void a_payment_confirmed_with_a_published_test_id_is_answered_with_a_new_copy_of_its_card() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse confirmed =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));

    assertThat(confirmed.httpStatus()).as(bodyOf(confirmed)).isEqualTo(200);
    assertThat(theValueAt(confirmed, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    StripePaymentMethod copy = world.theMethodStored(THE_FIRST_MINTED_METHOD);
    assertThat(copy.accountId()).isEqualTo(ACCOUNT);
    assertThat(copy.customerId()).isNull();
    assertThat(copy.billingDetails()).isEqualTo(StripeBillingDetails.NONE);
    assertThat(copy.metadata()).isEmpty();
    assertThat(copy.card())
        .isEqualTo(
            new StripeCard(
                "visa",
                "4242",
                10,
                2027,
                "credit",
                "US",
                "visa",
                List.of("visa"),
                "unregulated",
                true));
    assertThat(world.intents.find(StripeCardWorld.RUN, payment).orElseThrow().paymentMethod())
        .isEqualTo(THE_FIRST_MINTED_METHOD);
  }

  @Test
  void every_use_of_a_published_test_id_makes_a_copy_of_its_own() {
    String first = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    String second = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("2050")));

    world.confirmPayment(SECRET_KEY, first, Map.of("payment_method", "pm_card_visa"));
    world.confirmPayment(SECRET_KEY, second, Map.of("payment_method", "pm_card_visa"));

    assertThat(world.theMethodsStored())
        .extracting(StripePaymentMethod::paymentMethodId)
        .containsExactly(THE_FIRST_MINTED_METHOD, THE_SECOND_MINTED_METHOD);
  }

  @ParameterizedTest
  @CsvSource({
    "pm_card_mastercard,mastercard,4444",
    "pm_card_amex,amex,0005",
    "pm_card_jcb,jcb,0000",
    "pm_card_gb_debit,visa,0005",
    "pm_card_visa_chargeDeclinedInsufficientFunds,visa,9995"
  })
  void a_payment_created_and_confirmed_with_a_test_id_copies_the_card_the_id_names(
      String testId, String brand, String last4) {
    Map<String, String> form = aPaymentOf("1050");
    form.put("payment_method", testId);
    form.put("confirm", "true");

    StripeApiResponse created = world.createPayment(SECRET_KEY, form);

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(theValueAt(created, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    StripeCard card = world.theMethodStored(THE_FIRST_MINTED_METHOD).card();
    assertThat(card).isNotNull();
    assertThat(card.brand()).isEqualTo(brand);
    assertThat(card.last4()).isEqualTo(last4);
  }

  @Test
  void a_payment_created_without_confirming_already_carries_the_copy() {
    Map<String, String> form = aPaymentOf("1050");
    form.put("payment_method", "pm_card_visa");

    StripeApiResponse created = world.createPayment(SECRET_KEY, form);

    assertThat(theValueAt(created, "status")).isEqualTo("requires_confirmation");
    assertThat(theValueAt(created, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(world.theMethodsStored()).hasSize(1);
  }

  @Test
  void a_fixture_this_account_holds_under_a_published_id_is_used_as_it_is() {
    world.seedAFixture(ACCOUNT, "pm_card_visa", null);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse confirmed =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));

    assertThat(theValueAt(confirmed, "payment_method")).isEqualTo("pm_card_visa");
    assertThat(world.theMethodsStored()).hasSize(1);
  }

  @Test
  void a_fixture_another_account_holds_under_a_published_id_does_not_stop_the_copy() {
    world.seedAFixture(ANOTHER_ACCOUNT, "pm_card_visa", null);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse confirmed =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));

    assertThat(theValueAt(confirmed, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).accountId()).isEqualTo(ACCOUNT);
  }

  @Test
  void a_test_id_the_catalogue_does_not_hold_is_confirmed_as_an_unknown_method_and_copied_never() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse confirmed =
        world.confirmPayment(
            SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa_chargeDeclinedLostCard"));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(theValueAt(confirmed, "payment_method"))
        .isEqualTo("pm_card_visa_chargeDeclinedLostCard");
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_confirm_refused_before_it_changes_the_intent_creates_no_copy() {
    String payment =
        theIdOf(
            world.createPayment(
                SECRET_KEY, with(aPaymentOf("1050"), "allowed_payment_method_types[0]", null)));

    StripeApiResponse refused =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused)).containsEntry("error.param", List.of("return_url"));
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_confirm_a_scenario_answers_instead_of_creates_no_copy() {
    world.answeringInsteadOf(StripeOperations.CONFIRM);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse answered =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));

    assertThat(answered.httpStatus()).isEqualTo(502);
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_declined_confirm_keeps_the_copy_it_made_as_stripe_keeps_it() {
    world.declining(StripeOperations.CONFIRM, StripeDecline.INSUFFICIENT_FUNDS);
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse declined =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));

    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(theValueAt(declined, "error.payment_intent.status"))
        .isEqualTo("requires_payment_method");
    assertThat(world.theMethodsStored())
        .extracting(StripePaymentMethod::paymentMethodId)
        .containsExactly(THE_FIRST_MINTED_METHOD);
  }

  @Test
  void a_setup_created_with_a_test_id_attaches_the_copy_to_its_customer_on_confirm() {
    Map<String, String> form = aSetupThatConfirmsWithoutARedirect();
    form.put("payment_method", "pm_card_visa");
    String setup = theIdOf(world.createSetup(SECRET_KEY, form));

    StripeApiResponse confirmed = world.confirmSetup(SECRET_KEY, setup, Map.of());

    assertThat(confirmed.httpStatus()).as(bodyOf(confirmed)).isEqualTo(200);
    assertThat(theValueAt(confirmed, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).customerId()).isEqualTo(THE_CUSTOMER);
    assertThat(flat(world.listTheCustomersMethods(THE_CUSTOMER)).get("data.id"))
        .containsExactly(THE_FIRST_MINTED_METHOD);
  }

  @Test
  void a_setup_confirmed_by_a_publishable_key_with_a_test_id_attaches_the_copy() {
    StripeApiResponse created = world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect());
    String setup = theIdOf(created);
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", theValueAt(created, "client_secret"));
    form.put("payment_method", "pm_card_visa");

    StripeApiResponse confirmed = world.confirmSetup(PUBLISHABLE_KEY, setup, form);

    assertThat(confirmed.httpStatus()).as(bodyOf(confirmed)).isEqualTo(200);
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).customerId()).isEqualTo(THE_CUSTOMER);
  }

  @Test
  void a_payment_confirmed_with_payment_method_data_creates_the_method_from_the_card_sent() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse confirmed =
        world.confirmPayment(SECRET_KEY, payment, aCardAsPaymentMethodData());

    assertThat(confirmed.httpStatus()).as(bodyOf(confirmed)).isEqualTo(200);
    assertThat(theValueAt(confirmed, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    StripePaymentMethod created = world.theMethodStored(THE_FIRST_MINTED_METHOD);
    assertThat(created.customerId()).isNull();
    assertThat(created.billingDetails().name()).isEqualTo("Jane Doe");
    assertThat(created.card())
        .isEqualTo(
            new StripeCard(
                "visa",
                "4242",
                7,
                2040,
                "credit",
                "US",
                "visa",
                List.of("visa"),
                "unregulated",
                true));
    assertThat(world.everythingTheTrailAndTheExchangesKeep())
        .doesNotContain("4242424242424242")
        .contains("payment_method_data[card][number]=[[redacted]]")
        .contains("payment_method_data[card][cvc]=[[redacted]]");
  }

  @Test
  void a_publishable_key_confirms_a_payment_with_payment_method_data() {
    StripeApiResponse created = world.createPayment(SECRET_KEY, aPaymentOf("1050"));
    Map<String, String> form = aCardAsPaymentMethodData();
    form.put("client_secret", theValueAt(created, "client_secret"));

    StripeApiResponse confirmed = world.confirmPayment(PUBLISHABLE_KEY, theIdOf(created), form);

    assertThat(confirmed.httpStatus()).as(bodyOf(confirmed)).isEqualTo(200);
    assertThat(theValueAt(confirmed, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(theValueAt(confirmed, "status")).isEqualTo("succeeded");
  }

  @Test
  void a_payment_created_and_confirmed_with_payment_method_data_creates_the_method() {
    Map<String, String> form = aPaymentOf("1050");
    form.putAll(aCardAsPaymentMethodData());
    form.put("confirm", "true");

    StripeApiResponse created = world.createPayment(SECRET_KEY, form);

    assertThat(created.httpStatus()).as(bodyOf(created)).isEqualTo(200);
    assertThat(theValueAt(created, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(world.theMethodsStored()).hasSize(1);
  }

  @Test
  void a_setup_confirmed_with_payment_method_data_attaches_the_new_method_to_its_customer() {
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));

    StripeApiResponse confirmed = world.confirmSetup(SECRET_KEY, setup, aCardAsPaymentMethodData());

    assertThat(confirmed.httpStatus()).as(bodyOf(confirmed)).isEqualTo(200);
    assertThat(theValueAt(confirmed, "status")).isEqualTo("succeeded");
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).customerId()).isEqualTo(THE_CUSTOMER);
  }

  @Test
  void a_setup_created_with_payment_method_data_waits_for_confirmation_with_the_new_method() {
    Map<String, String> form = aSetupThatConfirmsWithoutARedirect();
    form.putAll(aCardAsPaymentMethodData());

    StripeApiResponse created = world.createSetup(SECRET_KEY, form);

    assertThat(theValueAt(created, "status")).isEqualTo("requires_confirmation");
    assertThat(theValueAt(created, "payment_method")).isEqualTo(THE_FIRST_MINTED_METHOD);
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).customerId()).isNull();
  }

  @Test
  void a_declined_setup_keeps_the_method_unattached() {
    world.declining(StripeOperations.CONFIRM_SETUP_INTENT, StripeDecline.GENERIC_DECLINE);
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));

    StripeApiResponse declined = world.confirmSetup(SECRET_KEY, setup, aCardAsPaymentMethodData());

    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(world.theMethodStored(THE_FIRST_MINTED_METHOD).customerId()).isNull();
  }

  @Test
  void payment_method_data_without_its_card_is_refused_before_the_status_of_the_setup() {
    String setup = theIdOf(world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect()));
    world.confirmSetup(SECRET_KEY, setup, aCardAsPaymentMethodData());
    Map<String, String> form = new LinkedHashMap<>();
    form.put("payment_method_data[type]", "card");
    form.put("payment_method_data[allow_redisplay]", "unspecified");

    StripeApiResponse refused = world.confirmSetup(SECRET_KEY, setup, form);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.code", List.of("parameter_missing"))
        .containsEntry("error.param", List.of("payment_method_data[card]"))
        .containsEntry(
            "error.message", List.of("Missing required param: payment_method_data[card]."))
        .containsEntry("error.setup_intent.status", List.of("succeeded"));
    assertThat(world.theMethodsStored()).hasSize(1);
  }

  @Test
  void a_publishable_key_is_refused_payment_method_data_without_its_card_with_the_setup_embedded() {
    StripeApiResponse created = world.createSetup(SECRET_KEY, aSetupThatConfirmsWithoutARedirect());
    String setup = theIdOf(created);
    world.confirmSetup(SECRET_KEY, setup, aCardAsPaymentMethodData());
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", theValueAt(created, "client_secret"));
    form.put("expand[0]", "payment_method");
    form.put("payment_method_data[type]", "card");
    form.put("use_stripe_sdk", "true");

    StripeApiResponse refused = world.confirmSetup(PUBLISHABLE_KEY, setup, form);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.param", List.of("payment_method_data[card]"))
        .containsEntry("error.setup_intent.id", List.of(setup))
        .containsEntry("error.setup_intent.status", List.of("succeeded"));
  }

  @Test
  void payment_method_data_beside_a_payment_method_is_refused_as_two_exclusive_parameters() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));
    Map<String, String> form = aCardAsPaymentMethodData();
    form.put("payment_method", "pm_card_visa");

    StripeApiResponse refused = world.confirmPayment(SECRET_KEY, payment, form);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.code", List.of("parameters_exclusive"))
        .containsEntry("error.param", List.of("payment_method"))
        .containsEntry("error.payment_intent.id", List.of(payment));
    assertThat(world.theMethodsStored()).isEmpty();
  }

  @Test
  void a_card_refused_inside_payment_method_data_leaves_the_payment_as_it_was() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse refused =
        world.confirmPayment(
            SECRET_KEY,
            payment,
            with(
                aCardAsPaymentMethodData(),
                "payment_method_data[card][number]",
                "4242424242424241"));

    assertThat(refused.httpStatus()).isEqualTo(402);
    assertThat(flat(refused))
        .containsEntry("error.type", List.of("card_error"))
        .containsEntry("error.code", List.of("incorrect_number"))
        .containsEntry("error.param", List.of("number"))
        .containsEntry("error.payment_intent.status", List.of("requires_payment_method"));
    assertThat(world.theMethodsStored()).isEmpty();
    assertThat(world.intents.find(StripeCardWorld.RUN, payment).orElseThrow().paymentMethod())
        .isNull();
  }

  @Test
  void payment_method_data_of_another_type_is_refused_by_name() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse refused =
        world.confirmPayment(
            SECRET_KEY, payment, Map.of("payment_method_data[type]", "sepa_debit"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused)).containsEntry("error.param", List.of("payment_method_data[type]"));
  }

  @Test
  void payment_method_data_sent_as_a_value_is_refused_as_unreadable() {
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse refused =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method_data", "card"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused)).containsEntry("error.param", List.of("payment_method_data"));
  }

  @Test
  void a_created_and_confirmed_payment_judges_a_method_its_account_does_not_hold() {
    Map<String, String> form = aPaymentOf("1050");
    form.put("payment_method", "pm_123");
    form.put("confirm", "true");

    StripeApiResponse refused = world.createPayment(SECRET_KEY, form);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(flat(refused))
        .containsEntry("error.code", List.of("resource_missing"))
        .containsEntry("error.param", List.of("payment_method"))
        .doesNotContainKey("error.payment_intent.id");
    assertThat(world.intents.all()).isEmpty();
  }

  @Test
  void a_created_and_confirmed_payment_judges_the_customer_a_method_is_attached_to() {
    world.seedAFixture(ACCOUNT, "pm_saved_for_the_customer", THE_CUSTOMER);
    Map<String, String> form = aPaymentOf("1050");
    form.put("payment_method", "pm_saved_for_the_customer");
    form.put("confirm", "true");

    StripeApiResponse withoutTheCustomer = world.createPayment(SECRET_KEY, form);
    StripeApiResponse forAnother =
        world.createPayment(SECRET_KEY, with(form, "customer", "cus_someone_else"));
    StripeApiResponse forTheCustomer =
        world.createPayment(SECRET_KEY, with(form, "customer", THE_CUSTOMER));

    assertThat(withoutTheCustomer.httpStatus()).isEqualTo(400);
    assertThat(flat(withoutTheCustomer)).containsEntry("error.param", List.of("payment_method"));
    assertThat(theValueAt(withoutTheCustomer, "error.message"))
        .isEqualTo(StripePaymentMethodLookup.A_METHOD_OF_A_CUSTOMER_PAYS_FOR_NO_INTENT_WITHOUT_ONE);
    assertThat(theValueAt(forAnother, "error.message"))
        .isEqualTo(StripePaymentMethodLookup.A_METHOD_OF_ANOTHER_CUSTOMER_PAYS_FOR_NO_INTENT);
    assertThat(forTheCustomer.httpStatus()).as(bodyOf(forTheCustomer)).isEqualTo(200);
    assertThat(world.intents.all()).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({"pm_sim_00000000000000000001,402", "pm_card_visa,200"})
  void a_scenario_rule_is_decided_about_the_copy_the_operation_uses(String ruledOn, int answered) {
    world.scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            StripeCardWorld.RUN,
            0,
            new ScenarioMatch(
                null,
                null,
                null,
                null,
                ruledOn,
                null,
                null,
                StripeOperations.CONFIRM,
                null,
                Map.of()),
            new ScenarioAction.TerminalFailure(StripeDecline.GENERIC_DECLINE.scenarioCode()),
            null));
    String payment = theIdOf(world.createPayment(SECRET_KEY, aPaymentOf("1050")));

    StripeApiResponse confirmed =
        world.confirmPayment(SECRET_KEY, payment, Map.of("payment_method", "pm_card_visa"));

    assertThat(confirmed.httpStatus()).isEqualTo(answered);
  }

  private static Map<String, String> aCardAsPaymentMethodData() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("payment_method_data[type]", "card");
    form.put("payment_method_data[card][number]", "4242424242424242");
    form.put("payment_method_data[card][exp_month]", "7");
    form.put("payment_method_data[card][exp_year]", "2040");
    form.put("payment_method_data[card][cvc]", "123");
    form.put("payment_method_data[billing_details][name]", "Jane Doe");
    form.put("payment_method_data[allow_redisplay]", "unspecified");
    return form;
  }
}
