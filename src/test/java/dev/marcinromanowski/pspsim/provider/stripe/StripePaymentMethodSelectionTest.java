package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StripePaymentMethodSelectionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-selection-run");

  private static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Selection";

  private static final String A_DAHLIA_RELEASE_BEFORE_ALLOWED_TYPES = "2026-04-22.dahlia";
  private static final String THE_DAHLIA_RELEASE_THAT_ADDED_ALLOWED_TYPES = "2026-07-29.dahlia";
  private static final String THE_JAVA_SDKS_RELEASE = "2026-08-26.dahlia";
  private static final String THE_RELEASE_THAT_OPENED_ENDIVE = "2026-09-30.endive";
  private static final String THE_NEWEST_RELEASE = "2026-10-28.endive";

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";
  private static final String SETUP_INTENTS = "/v1/setup_intents";

  private static final String FIRST_INTENT = "pi_sim_0000000000000001";
  private static final String THE_CUSTOMER = "cus_choosing_how_to_pay";
  private static final String THE_CARD = "pm_the_card";
  private static final String THE_WALLET = "pm_the_paypal_wallet";

  private static final String A_RETURN_URL = "https://shop.example/checkout/return";

  private static final String A_PAYMENT_NEEDS_A_RETURN_URL =
      "A `return_url` must be specified because this Payment Intent is configured to"
          + " automatically accept the payment methods enabled in the Dashboard, some of which may"
          + " require a full page redirect to succeed. If you do not want to accept redirect-based"
          + " payment methods, set `automatic_payment_methods[enabled]` to `true` and"
          + " `automatic_payment_methods[allow_redirects]` to `never` when creating Setup Intents"
          + " and Payment Intents.";

  private static final String A_SETUP_NEEDS_A_RETURN_URL =
      "A `return_url` must be specified because this Setup Intent is configured to"
          + " automatically accept the payment methods enabled in the Dashboard, some of which may"
          + " require a full page redirect to succeed. If you do not want to accept redirect-based"
          + " payment methods, set `automatic_payment_methods[enabled]` to `true` and"
          + " `automatic_payment_methods[allow_redirects]` to `never` when creating Setup Intents"
          + " and Payment Intents.";

  private static final String NO_LONGER_WRITABLE =
      "payment_method_types is no longer a writable parameter at the API version this request is"
          + " answered at. It stays on the object as the list of payment method types the intent"
          + " computed. Send allowed_payment_method_types to name the types you accept, or"
          + " excluded_payment_method_types to narrow the dynamic ones.";

  private final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
  private final StripeDoubles.Runs testRuns = new StripeDoubles.Runs(DEFAULT_RUN);
  private final StripeDoubles.Audit audit = new StripeDoubles.Audit();
  private final StripeDoubles.PaymentMethods paymentMethods = new StripeDoubles.PaymentMethods();
  private final StripeDoubles.Intents intents = new StripeDoubles.Intents(paymentMethods);
  private final StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
  private final StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
  private final StripeDoubles.Customers customers = new StripeDoubles.Customers();
  private final StripeDoubles.SetupIntents setupIntents =
      new StripeDoubles.SetupIntents(paymentMethods);
  private final StripeDoubles.Keys keys = new StripeDoubles.Keys();
  private final StripeDoubles.EphemeralKeys ephemeralKeys = new StripeDoubles.EphemeralKeys();
  private final StripeDoubles.CountingIds ids = new StripeDoubles.CountingIds();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(new InMemoryScenarioRules());

  @ParameterizedTest
  @ValueSource(
      strings = {
        A_DAHLIA_RELEASE_BEFORE_ALLOWED_TYPES,
        THE_DAHLIA_RELEASE_THAT_ADDED_ALLOWED_TYPES,
        THE_JAVA_SDKS_RELEASE
      })
  void a_dahlia_release_takes_payment_method_types_on_create_and_offers_exactly_those(
      String version) {
    anAccount();

    StripeApiResponse created =
        createAPayment(version, "pln", form("payment_method_types[]", "paypal"));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(created))
        .contains("\"payment_method_types\":[\"paypal\"]")
        .contains("\"automatic_payment_methods\":null")
        .contains("\"allowed_payment_method_types\":null");
  }

  @Test
  void a_dahlia_release_reads_payment_method_types_alike_in_both_list_encodings() {
    anAccount();

    StripeApiResponse appended =
        createAPayment(
            THE_JAVA_SDKS_RELEASE,
            "pln",
            form("payment_method_types[]", "paypal", "payment_method_types[]", "card"));
    StripeApiResponse indexed =
        createAPayment(
            THE_JAVA_SDKS_RELEASE,
            "pln",
            form("payment_method_types[1]", "card", "payment_method_types[0]", "paypal"));

    assertThat(bodyOf(appended)).contains("\"payment_method_types\":[\"paypal\",\"card\"]");
    assertThat(bodyOf(indexed)).contains("\"payment_method_types\":[\"paypal\",\"card\"]");
  }

  @ParameterizedTest
  @ValueSource(strings = {THE_RELEASE_THAT_OPENED_ENDIVE, THE_NEWEST_RELEASE})
  void an_endive_release_refuses_payment_method_types_on_create_with_the_published_code(
      String version) {
    anAccount();

    for (Map<String, List<String>> sent :
        List.of(form("payment_method_types[]", "card"), form("payment_method_types[0]", "card"))) {
      StripeApiResponse refused = createAPayment(version, "pln", sent);

      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(bodyOf(refused))
          .isEqualTo(
              "{\"error\":{\"type\":\"invalid_request_error\","
                  + "\"code\":\"payment_method_types_no_longer_supported\","
                  + "\"doc_url\":\"https://docs.stripe.com/error-codes"
                  + "#payment-method-types-no-longer-supported\","
                  + "\"message\":\""
                  + NO_LONGER_WRITABLE
                  + "\",\"param\":\"payment_method_types\"}}");
    }
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_dahlia_release_refuses_a_listed_type_the_payment_could_not_use() {
    anAccount();

    StripeApiResponse incompatible =
        createAPayment(THE_JAVA_SDKS_RELEASE, "jpy", form("payment_method_types[]", "paypal"));
    StripeApiResponse unknown =
        createAPayment(THE_JAVA_SDKS_RELEASE, "pln", form("payment_method_types[]", "bitcoin"));
    StripeApiResponse published =
        createAPayment(THE_JAVA_SDKS_RELEASE, "pln", form("payment_method_types[]", "sepa_debit"));

    assertThat(bodyOf(incompatible))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"The payment method type"
                + " `paypal` is not supported when creating a PaymentIntent.\","
                + "\"param\":\"payment_method_types\"}}");
    assertThat(bodyOf(unknown))
        .contains(
            "\"message\":\"The payment method type `bitcoin` is not supported when creating"
                + " a PaymentIntent.\"");
    assertThat(bodyOf(published))
        .contains(
            "\"message\":\"The payment method type `sepa_debit` is not supported when"
                + " creating a PaymentIntent.\"");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_payment_sending_no_selection_offers_every_modelled_type_its_currency_presents() {
    anAccount();

    StripeApiResponse inZloty = createAPayment(THE_NEWEST_RELEASE, "pln", form());
    StripeApiResponse inYen = createAPayment(THE_NEWEST_RELEASE, "jpy", form());

    assertThat(bodyOf(inZloty))
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]")
        .contains("\"automatic_payment_methods\":{\"enabled\":true}")
        .contains("\"allowed_payment_method_types\":null")
        .contains("\"excluded_payment_method_types\":null")
        .contains("\"payment_method_configuration_details\":null");
    assertThat(bodyOf(inYen)).contains("\"payment_method_types\":[\"card\"]");
  }

  @Test
  void the_selection_members_sit_where_the_alphabetical_object_puts_them() {
    anAccount();

    String body = bodyOf(createAPayment(THE_NEWEST_RELEASE, "pln", form()));

    assertThat(body)
        .contains("\"object\":\"payment_intent\",\"allowed_payment_method_types\":null,\"amount\":")
        .contains(
            "\"amount_received\":0,\"automatic_payment_methods\":{\"enabled\":true},"
                + "\"cancellation_reason\":")
        .contains(
            "\"customer\":null,\"excluded_payment_method_types\":null,\"last_payment_error\":")
        .contains(
            "\"payment_method\":null,\"payment_method_configuration_details\":null,"
                + "\"payment_method_types\":[\"card\",\"paypal\"],\"receipt_email\":");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        A_DAHLIA_RELEASE_BEFORE_ALLOWED_TYPES,
        THE_DAHLIA_RELEASE_THAT_ADDED_ALLOWED_TYPES,
        THE_RELEASE_THAT_OPENED_ENDIVE,
        THE_NEWEST_RELEASE
      })
  void allowed_types_silently_drop_what_the_payment_cannot_use_at_every_release(String version) {
    anAccount();

    StripeApiResponse created =
        createAPayment(
            version,
            "jpy",
            form(
                "allowed_payment_method_types[]", "paypal",
                "allowed_payment_method_types[]", "card",
                "allowed_payment_method_types[]", "sepa_debit"));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(created))
        .contains("\"allowed_payment_method_types\":[\"paypal\",\"card\",\"sepa_debit\"]")
        .contains("\"automatic_payment_methods\":null")
        .contains("\"payment_method_types\":[\"card\"]");
  }

  @Test
  void allowed_types_read_alike_in_both_list_encodings() {
    anAccount();

    StripeApiResponse appended =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "allowed_payment_method_types[]", "paypal",
                "allowed_payment_method_types[]", "card"));
    StripeApiResponse indexed =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "allowed_payment_method_types[1]", "card",
                "allowed_payment_method_types[0]", "paypal"));

    assertThat(bodyOf(appended)).contains("\"payment_method_types\":[\"paypal\",\"card\"]");
    assertThat(bodyOf(indexed)).contains("\"payment_method_types\":[\"paypal\",\"card\"]");
  }

  @Test
  void allowed_types_that_leave_nothing_the_payment_can_use_are_refused() {
    anAccount();

    StripeApiResponse refused =
        createAPayment(THE_NEWEST_RELEASE, "jpy", form("allowed_payment_method_types[]", "paypal"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_intent_invalid_parameter\"")
        .contains("\"param\":\"allowed_payment_method_types\"");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void an_allowed_type_stripe_does_not_publish_is_refused_the_way_an_invalid_enum_value_is() {
    anAccount();

    StripeApiResponse refused =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "allowed_payment_method_types[0]", "card",
                "allowed_payment_method_types[1]", "bitcoin"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    String body = bodyOf(refused);
    assertThat(body)
        .startsWith(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Invalid"
                + " allowed_payment_method_types[1]: must be one of acss_debit, affirm,"
                + " afterpay_clearpay,")
        .endsWith(" wechat_pay, wero, or zip\",\"param\":\"allowed_payment_method_types[1]\"}}")
        .contains(" card, card_present, cashapp,")
        .contains(" link,");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void an_excluded_type_is_checked_against_the_shorter_list_stripe_publishes_for_it() {
    anAccount();

    StripeApiResponse refused =
        createAPayment(THE_NEWEST_RELEASE, "pln", form("excluded_payment_method_types[]", "link"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"message\":\"Invalid excluded_payment_method_types[0]: must be one of")
        .doesNotContain(" link,")
        .contains("\"param\":\"excluded_payment_method_types[0]\"");
  }

  @Test
  void excluded_types_narrow_the_dynamic_set_and_are_shown_on_the_object() {
    anAccount();

    StripeApiResponse created =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("excluded_payment_method_types[]", "paypal"));

    assertThat(bodyOf(created))
        .contains("\"excluded_payment_method_types\":[\"paypal\"]")
        .contains("\"automatic_payment_methods\":{\"enabled\":true}")
        .contains("\"payment_method_types\":[\"card\"]");
  }

  @Test
  void excluding_every_type_the_payment_could_use_is_refused() {
    anAccount();

    StripeApiResponse refused =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "excluded_payment_method_types[]", "card",
                "excluded_payment_method_types[]", "paypal"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_intent_invalid_parameter\"")
        .contains("\"param\":\"excluded_payment_method_types\"");
  }

  @Test
  void automatic_payment_methods_never_redirecting_drops_the_type_that_redirects() {
    anAccount();

    StripeApiResponse created =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "automatic_payment_methods[enabled]", "true",
                "automatic_payment_methods[allow_redirects]", "never"));

    assertThat(bodyOf(created))
        .contains("\"automatic_payment_methods\":{\"allow_redirects\":\"never\",\"enabled\":true}")
        .contains("\"payment_method_types\":[\"card\"]");
  }

  @Test
  void automatic_payment_methods_switched_off_offers_the_card_stripe_offered_before_it_existed() {
    anAccount();

    StripeApiResponse created =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("automatic_payment_methods[enabled]", "false"));

    assertThat(bodyOf(created))
        .contains("\"automatic_payment_methods\":{\"enabled\":false}")
        .contains("\"payment_method_types\":[\"card\"]");
  }

  @Test
  void automatic_payment_methods_is_refused_in_any_shape_stripe_does_not_publish() {
    anAccount();

    StripeApiResponse noEnabled =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("automatic_payment_methods[allow_redirects]", "never"));
    StripeApiResponse notAFlag =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("automatic_payment_methods[enabled]", "yes"));
    StripeApiResponse notARedirectSetting =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "automatic_payment_methods[enabled]", "true",
                "automatic_payment_methods[allow_redirects]", "sometimes"));
    StripeApiResponse notAHash =
        createAPayment(THE_NEWEST_RELEASE, "pln", form("automatic_payment_methods", "true"));

    assertThat(bodyOf(noEnabled))
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"automatic_payment_methods[enabled]\"");
    assertThat(bodyOf(notAFlag)).contains("\"param\":\"automatic_payment_methods[enabled]\"");
    assertThat(bodyOf(notARedirectSetting))
        .contains(
            "\"message\":\"Invalid automatic_payment_methods[allow_redirects]: must be one of"
                + " always or never\"")
        .contains("\"param\":\"automatic_payment_methods[allow_redirects]\"");
    assertThat(bodyOf(notAHash)).contains("\"param\":\"automatic_payment_methods\"");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_payment_method_configuration_is_kept_and_answered_as_the_configuration_details() {
    anAccount();

    StripeApiResponse created =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("payment_method_configuration", "pmc_simulated"));
    StripeApiResponse tooLong =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("payment_method_configuration", "p".repeat(101)));

    assertThat(bodyOf(created))
        .contains(
            "\"payment_method_configuration_details\":{\"id\":\"pmc_simulated\",\"parent\":null}")
        .contains("\"automatic_payment_methods\":{\"enabled\":true}")
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]");
    assertThat(tooLong.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(tooLong)).contains("\"param\":\"payment_method_configuration\"");
  }

  @Test
  void a_payment_method_configuration_of_exactly_a_hundred_characters_is_the_longest_taken() {
    anAccount();
    String theLongestTaken = "p".repeat(100);

    StripeApiResponse taken =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("payment_method_configuration", theLongestTaken));
    StripeApiResponse oneLonger =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("payment_method_configuration", theLongestTaken + "p"));

    assertThat(taken.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(taken))
        .contains("\"payment_method_configuration_details\":{\"id\":\"" + theLongestTaken + "\"");
    assertThat(oneLonger.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(oneLonger)).contains("\"param\":\"payment_method_configuration\"");
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void a_dahlia_payment_method_types_that_is_no_list_is_refused_as_unreadable() {
    anAccount();

    StripeApiResponse aValue =
        createAPayment(THE_JAVA_SDKS_RELEASE, "pln", form("payment_method_types", "card"));
    StripeApiResponse aHashInAList =
        createAPayment(THE_JAVA_SDKS_RELEASE, "pln", form("payment_method_types[0][type]", "card"));

    for (StripeApiResponse refused : List.of(aValue, aHashInAList)) {
      assertThat(refused.httpStatus()).isEqualTo(400);
      assertThat(bodyOf(refused))
          .isEqualTo(
              "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"A parameter carries"
                  + " a value this API does not accept. It accepts a list, sent as"
                  + " payment_method_types[]=... once per element or as payment_method_types[0]=...,"
                  + " payment_method_types[1]=... numbered from 0 without a gap, and an empty"
                  + " payment_method_types= as a list of no elements.\","
                  + "\"param\":\"payment_method_types\"}}");
    }
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_dahlia_payment_method_types_listing_nothing_leaves_the_payment_no_type() {
    anAccount();

    StripeApiResponse refused =
        createAPayment(THE_JAVA_SDKS_RELEASE, "pln", form("payment_method_types", ""));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_intent_invalid_parameter\"")
        .contains("\"param\":\"payment_method_types\"");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void allowed_types_sent_as_a_single_value_are_refused_as_unreadable() {
    anAccount();

    StripeApiResponse refused =
        createAPayment(THE_NEWEST_RELEASE, "pln", form("allowed_payment_method_types", "card"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"type\":\"invalid_request_error\"")
        .contains("It accepts a list, sent as allowed_payment_method_types[]=...")
        .contains("\"param\":\"allowed_payment_method_types\"")
        .doesNotContain("\"code\"");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void allowed_types_are_refused_beside_each_parameter_stripe_publishes_them_exclusive_with() {
    anAccount();
    Map<String, String> pairs = new LinkedHashMap<>();
    pairs.put("payment_method_types[]", "payment_method_types");
    pairs.put("excluded_payment_method_types[]", "excluded_payment_method_types");
    pairs.put("automatic_payment_methods[enabled]", "automatic_payment_methods");
    pairs.put("payment_method_configuration", "payment_method_configuration");

    pairs.forEach(
        (sent, named) -> {
          String value = sent.startsWith("automatic") ? "true" : "card";
          StripeApiResponse refused =
              createAPayment(
                  THE_JAVA_SDKS_RELEASE,
                  "pln",
                  form("allowed_payment_method_types[]", "card", sent, value));

          assertThat(refused.httpStatus()).as(sent).isEqualTo(400);
          assertThat(bodyOf(refused))
              .as(sent)
              .isEqualTo(
                  "{\"error\":{\"type\":\"invalid_request_error\","
                      + "\"code\":\"parameters_exclusive\","
                      + "\"doc_url\":\"https://docs.stripe.com/error-codes#parameters-exclusive\","
                      + "\"message\":\"You may only specify one of these parameters:"
                      + " allowed_payment_method_types, "
                      + named
                      + ".\",\"param\":\"allowed_payment_method_types\"}}");
        });
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_listed_set_is_refused_beside_the_dynamic_settings_it_switches_off() {
    anAccount();

    StripeApiResponse beside =
        createAPayment(
            THE_JAVA_SDKS_RELEASE,
            "pln",
            form("payment_method_types[]", "card", "automatic_payment_methods[enabled]", "true"));
    StripeApiResponse excluding =
        createAPayment(
            THE_JAVA_SDKS_RELEASE,
            "pln",
            form("payment_method_types[]", "card", "excluded_payment_method_types[]", "paypal"));
    StripeApiResponse configured =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "automatic_payment_methods[enabled]", "false",
                "payment_method_configuration", "pmc_simulated"));
    StripeApiResponse switchedOffBeside =
        createAPayment(
            THE_JAVA_SDKS_RELEASE,
            "pln",
            form("payment_method_types[]", "card", "automatic_payment_methods[enabled]", "false"));

    assertThat(bodyOf(beside))
        .contains(
            "\"message\":\"You may only specify one of these parameters: payment_method_types,"
                + " automatic_payment_methods.\"");
    assertThat(bodyOf(excluding))
        .contains(
            "\"message\":\"You may only specify one of these parameters:"
                + " excluded_payment_method_types, payment_method_types.\"");
    assertThat(bodyOf(configured))
        .contains(
            "\"message\":\"You may only specify one of these parameters:"
                + " payment_method_configuration, automatic_payment_methods.\"");
    assertThat(switchedOffBeside.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(switchedOffBeside))
        .contains("\"automatic_payment_methods\":{\"enabled\":false}")
        .contains("\"payment_method_types\":[\"card\"]");
    assertThat(intents.all()).hasSize(1);
  }

  @Test
  void a_payment_confirmed_on_creation_refuses_a_method_of_a_type_it_does_not_offer() {
    anAccountHoldingACardAndAWallet();

    StripeApiResponse refused =
        createAPayment(
            THE_NEWEST_RELEASE,
            "pln",
            form(
                "allowed_payment_method_types[]", "card",
                "confirm", "true",
                "payment_method", THE_WALLET));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\","
                + "\"code\":\"payment_intent_incompatible_payment_method\","
                + "\"doc_url\":\"https://docs.stripe.com/error-codes"
                + "#payment-intent-incompatible-payment-method\","
                + "\"message\":\"The PaymentMethod provided (paypal) is not allowed for this"
                + " PaymentIntent. Attach a PaymentMethod of one of the following types: card.\","
                + "\"param\":\"payment_method\"}}");
    assertThat(intents.all()).isEmpty();
    assertThat(intents.allCharges()).isEmpty();
  }

  @Test
  void a_confirm_refuses_a_method_of_a_type_the_payment_does_not_offer_and_changes_nothing() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form("allowed_payment_method_types[]", "card"));
    StripePaymentIntent before = theStoredPayment();

    StripeApiResponse refused =
        confirmThePayment(THE_NEWEST_RELEASE, form("payment_method", THE_WALLET));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_intent_incompatible_payment_method\"")
        .contains("\"param\":\"payment_method\"")
        .contains(
            "\"message\":\"The PaymentMethod provided (paypal) is not allowed for this"
                + " PaymentIntent. Attach a PaymentMethod of one of the following types: card.\"")
        .contains("\"payment_intent\":{\"id\":\"" + FIRST_INTENT + "\"");
    assertThat(theStoredPayment()).isEqualTo(before);
    assertThat(intents.allCharges()).isEmpty();
  }

  @Test
  void a_confirm_narrowing_the_types_refuses_the_method_it_excludes_and_keeps_the_old_selection() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());
    StripePaymentIntent before = theStoredPayment();

    StripeApiResponse refused =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("allowed_payment_method_types[]", "card", "payment_method", THE_WALLET));
    StripeApiResponse retrieved = retrieveThePayment(THE_NEWEST_RELEASE);

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_intent_incompatible_payment_method\"")
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]");
    assertThat(theStoredPayment()).isEqualTo(before);
    assertThat(bodyOf(retrieved))
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]")
        .contains("\"allowed_payment_method_types\":null")
        .contains("\"status\":\"requires_payment_method\"");
  }

  @Test
  void a_confirm_naming_allowed_types_keeps_them_and_retrieve_answers_what_the_confirm_answered() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());

    StripeApiResponse confirmed =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("allowed_payment_method_types[0]", "card", "payment_method", THE_CARD));
    StripeApiResponse retrieved = retrieveThePayment(THE_NEWEST_RELEASE);

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    for (StripeApiResponse answered : List.of(confirmed, retrieved)) {
      assertThat(bodyOf(answered))
          .contains("\"allowed_payment_method_types\":[\"card\"]")
          .contains("\"automatic_payment_methods\":null")
          .contains("\"payment_method_types\":[\"card\"]")
          .contains("\"status\":\"succeeded\"");
    }
  }

  @Test
  void a_dahlia_confirm_takes_payment_method_types_and_an_endive_confirm_refuses_it_untouched() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());
    StripePaymentIntent before = theStoredPayment();

    StripeApiResponse refused =
        confirmThePayment(
            THE_RELEASE_THAT_OPENED_ENDIVE,
            form("payment_method_types[]", "card", "payment_method", THE_CARD));
    StripePaymentIntent afterTheRefusal = theStoredPayment();
    StripeApiResponse confirmed =
        confirmThePayment(
            THE_JAVA_SDKS_RELEASE,
            form("payment_method_types[0]", "card", "payment_method", THE_CARD));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_method_types_no_longer_supported\"")
        .contains("\"payment_intent\":{\"id\":\"" + FIRST_INTENT + "\"");
    assertThat(afterTheRefusal).isEqualTo(before);
    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"payment_method_types\":[\"card\"]")
        .contains("\"automatic_payment_methods\":null");
  }

  @Test
  void a_confirm_excluding_types_from_a_payment_that_named_its_types_is_refused_as_exclusive() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form("allowed_payment_method_types[]", "card"));
    StripePaymentIntent before = theStoredPayment();

    StripeApiResponse refused =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("excluded_payment_method_types[]", "paypal", "payment_method", THE_CARD));

    assertThat(bodyOf(refused))
        .contains(
            "\"message\":\"You may only specify one of these parameters:"
                + " excluded_payment_method_types, allowed_payment_method_types.\"");
    assertThat(theStoredPayment()).isEqualTo(before);
  }

  @Test
  void a_confirm_excluding_types_names_the_parameter_that_fixed_the_payments_list() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_JAVA_SDKS_RELEASE, "pln", form("payment_method_types[]", "card"));
    String switchedOff =
        String.valueOf(
            createAPayment(
                    THE_NEWEST_RELEASE, "pln", form("automatic_payment_methods[enabled]", "false"))
                .providerObjectId());

    StripeApiResponse listed =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("excluded_payment_method_types[]", "paypal", "payment_method", THE_CARD));
    StripeApiResponse automatic =
        api()
            .confirmPaymentIntent(
                call(
                    THE_NEWEST_RELEASE,
                    PAYMENT_INTENTS + "/" + switchedOff + "/confirm",
                    form("excluded_payment_method_types[]", "paypal", "payment_method", THE_CARD)),
                switchedOff);

    assertThat(bodyOf(listed))
        .contains(
            "\"message\":\"You may only specify one of these parameters:"
                + " excluded_payment_method_types, payment_method_types.\"");
    assertThat(bodyOf(automatic))
        .contains(
            "\"message\":\"You may only specify one of these parameters:"
                + " excluded_payment_method_types, automatic_payment_methods.\"")
        .contains("\"payment_intent\":{\"id\":\"" + switchedOff + "\"");
  }

  @Test
  void a_confirm_excluding_a_type_from_a_dynamic_payment_recomputes_what_it_offers() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());

    StripeApiResponse confirmed =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form(
                "excluded_payment_method_types[]", "paypal",
                "payment_method", THE_CARD,
                "return_url", A_RETURN_URL));

    assertThat(bodyOf(confirmed))
        .contains("\"excluded_payment_method_types\":[\"paypal\"]")
        .contains("\"payment_method_types\":[\"card\"]")
        .contains("\"status\":\"succeeded\"");
  }

  @Test
  void a_setup_takes_payment_method_types_at_a_dahlia_release_and_refuses_it_at_an_endive_one() {
    anAccount();

    StripeApiResponse taken =
        createASetup(THE_JAVA_SDKS_RELEASE, form("payment_method_types[0]", "card"));
    StripeApiResponse refused =
        createASetup(THE_NEWEST_RELEASE, form("payment_method_types[]", "card"));

    assertThat(taken.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(taken))
        .contains("\"payment_method_types\":[\"card\"]")
        .contains("\"automatic_payment_methods\":null");
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_method_types_no_longer_supported\"")
        .contains("\"param\":\"payment_method_types\"");
    assertThat(setupIntents.all()).hasSize(1);
  }

  @Test
  void a_setup_sending_no_selection_offers_every_modelled_type_and_allowed_types_narrow_it() {
    anAccount();

    StripeApiResponse dynamic = createASetup(THE_NEWEST_RELEASE, form());
    StripeApiResponse narrowed =
        createASetup(
            THE_NEWEST_RELEASE,
            form(
                "allowed_payment_method_types[]", "card",
                "allowed_payment_method_types[]", "us_bank_account"));

    assertThat(bodyOf(dynamic))
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]")
        .contains("\"automatic_payment_methods\":{\"enabled\":true}");
    assertThat(bodyOf(narrowed))
        .contains("\"allowed_payment_method_types\":[\"card\",\"us_bank_account\"]")
        .contains("\"payment_method_types\":[\"card\"]");
  }

  @Test
  void a_setup_confirm_refuses_a_method_of_a_type_the_setup_does_not_offer_and_attaches_nothing() {
    anAccountHoldingACardAndAWallet();
    String setup =
        String.valueOf(
            createASetup(
                    THE_NEWEST_RELEASE,
                    form("customer", THE_CUSTOMER, "allowed_payment_method_types[]", "card"))
                .providerObjectId());
    StripeSetupIntent before = setupIntents.find(E2E_RUN, setup).orElseThrow();

    StripeApiResponse refused =
        confirmTheSetup(THE_NEWEST_RELEASE, setup, form("payment_method", THE_WALLET));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"setup_intent_invalid_parameter\"")
        .contains(
            "\"message\":\"The PaymentMethod provided (paypal) is not allowed for this"
                + " SetupIntent. Attach a PaymentMethod of one of the following types: card.\"")
        .contains("\"param\":\"payment_method\"")
        .contains("\"setup_intent\":{\"id\":\"" + setup + "\"");
    assertThat(setupIntents.find(E2E_RUN, setup).orElseThrow()).isEqualTo(before);
    assertThat(paymentMethods.find(E2E_RUN, THE_WALLET).orElseThrow().attached()).isFalse();
  }

  @Test
  void a_setup_confirm_narrows_by_allowed_types_and_keeps_what_it_narrowed_to() {
    anAccountHoldingACardAndAWallet();
    String setup =
        String.valueOf(
            createASetup(THE_NEWEST_RELEASE, form("customer", THE_CUSTOMER)).providerObjectId());

    StripeApiResponse confirmed =
        confirmTheSetup(
            THE_NEWEST_RELEASE,
            setup,
            form("allowed_payment_method_types[]", "card", "payment_method", THE_CARD));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"allowed_payment_method_types\":[\"card\"]")
        .contains("\"automatic_payment_methods\":null")
        .contains("\"payment_method_types\":[\"card\"]")
        .contains("\"status\":\"succeeded\"");
    assertThat(setupIntents.find(E2E_RUN, setup).orElseThrow().selection().allowed())
        .containsExactly("card");
  }

  @Test
  void a_setup_confirm_accepts_payment_method_types_like_any_parameter_it_does_not_read() {
    anAccountHoldingACardAndAWallet();
    String setup =
        String.valueOf(
            createASetup(THE_NEWEST_RELEASE, form("customer", THE_CUSTOMER)).providerObjectId());

    StripeApiResponse confirmed =
        confirmTheSetup(
            THE_NEWEST_RELEASE,
            setup,
            form(
                "payment_method_types[]",
                "paypal",
                "a_parameter_stripe_publishes_nowhere",
                "anything",
                "payment_method",
                THE_CARD,
                "return_url",
                A_RETURN_URL));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed)).contains("\"payment_method_types\":[\"card\",\"paypal\"]");
  }

  @Test
  void a_dynamic_payment_confirmed_with_no_return_url_is_refused_in_the_reported_words() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());
    StripePaymentIntent before = theStoredPayment();

    StripeApiResponse refused =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("excluded_payment_method_types[]", "paypal", "payment_method", THE_CARD));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .startsWith(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\""
                + A_PAYMENT_NEEDS_A_RETURN_URL
                + "\",\"param\":\"return_url\",\"payment_intent\":{\"id\":\""
                + FIRST_INTENT
                + "\"")
        .contains("\"excluded_payment_method_types\":null")
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]");
    assertThat(theStoredPayment()).isEqualTo(before);
  }

  @Test
  void a_payment_confirmed_on_creation_with_no_return_url_is_refused_and_nothing_is_minted() {
    anAccountHoldingACardAndAWallet();

    StripeApiResponse refused =
        createAPayment(
            THE_NEWEST_RELEASE, "pln", form("confirm", "true", "payment_method", THE_CARD));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\""
                + A_PAYMENT_NEEDS_A_RETURN_URL
                + "\",\"param\":\"return_url\"}}");
    assertThat(intents.all()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "one_off", "recurring"})
  void a_dynamic_payment_confirmed_off_session_needs_no_return_url(String offSession) {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());

    StripeApiResponse confirmed =
        confirmThePayment(
            THE_NEWEST_RELEASE, form("off_session", offSession, "payment_method", THE_CARD));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed)).contains("\"status\":\"succeeded\"");
  }

  @ParameterizedTest
  @ValueSource(strings = "false")
  void an_off_session_that_does_not_say_the_customer_is_away_still_needs_a_return_url(
      String offSession) {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());

    StripeApiResponse refused =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("off_session", offSession, "return_url", "", "payment_method", THE_CARD));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"return_url\"");
  }

  @Test
  void a_payment_that_refuses_redirects_or_is_not_dynamic_confirms_with_no_return_url() {
    anAccountHoldingACardAndAWallet();
    createAPayment(
        THE_NEWEST_RELEASE,
        "pln",
        form(
            "automatic_payment_methods[enabled]",
            "true",
            "automatic_payment_methods[allow_redirects]",
            "never"));
    String allowed =
        String.valueOf(
            createAPayment(
                    THE_NEWEST_RELEASE, "pln", form("allowed_payment_method_types[]", "card"))
                .providerObjectId());
    String listed =
        String.valueOf(
            createAPayment(THE_JAVA_SDKS_RELEASE, "pln", form("payment_method_types[]", "card"))
                .providerObjectId());

    StripeApiResponse neverRedirecting =
        confirmThePayment(THE_NEWEST_RELEASE, form("payment_method", THE_CARD));
    StripeApiResponse allowedOnly =
        confirmTheIntent(THE_NEWEST_RELEASE, allowed, form("payment_method", THE_CARD));
    StripeApiResponse listedOnly =
        confirmTheIntent(THE_JAVA_SDKS_RELEASE, listed, form("payment_method", THE_CARD));

    for (StripeApiResponse confirmed : List.of(neverRedirecting, allowedOnly, listedOnly)) {
      assertThat(confirmed.httpStatus()).isEqualTo(200);
      assertThat(bodyOf(confirmed)).contains("\"status\":\"succeeded\"");
    }
  }

  @Test
  void a_confirm_whose_own_selection_switches_dynamic_off_needs_no_return_url() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());

    StripeApiResponse confirmed =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("allowed_payment_method_types[]", "card", "payment_method", THE_CARD));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
  }

  @Test
  void a_dynamic_setup_confirmed_with_no_return_url_is_refused_and_attaches_nothing() {
    anAccountHoldingACardAndAWallet();
    String setup =
        String.valueOf(
            createASetup(THE_NEWEST_RELEASE, form("customer", THE_CUSTOMER)).providerObjectId());
    StripeSetupIntent before = setupIntents.find(E2E_RUN, setup).orElseThrow();

    StripeApiResponse refused =
        confirmTheSetup(
            THE_NEWEST_RELEASE, setup, form("off_session", "true", "payment_method", THE_CARD));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .startsWith(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\""
                + A_SETUP_NEEDS_A_RETURN_URL
                + "\",\"param\":\"return_url\",\"setup_intent\":{\"id\":\""
                + setup
                + "\"");
    assertThat(setupIntents.find(E2E_RUN, setup).orElseThrow()).isEqualTo(before);
    assertThat(paymentMethods.find(E2E_RUN, THE_CARD).orElseThrow().attached()).isFalse();
  }

  @Test
  void a_setup_that_refuses_redirects_confirms_with_no_return_url() {
    anAccountHoldingACardAndAWallet();
    String setup =
        String.valueOf(
            createASetup(
                    THE_NEWEST_RELEASE,
                    form(
                        "customer", THE_CUSTOMER,
                        "automatic_payment_methods[enabled]", "true",
                        "automatic_payment_methods[allow_redirects]", "never"))
                .providerObjectId());

    StripeApiResponse confirmed =
        confirmTheSetup(THE_NEWEST_RELEASE, setup, form("payment_method", THE_CARD));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"automatic_payment_methods\":{\"allow_redirects\":\"never\",\"enabled\":true}")
        .contains("\"payment_method_types\":[\"card\"]");
  }

  @Test
  void an_empty_exclusion_on_confirm_unsets_it_rather_than_naming_no_type() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form("excluded_payment_method_types[]", "paypal"));

    StripeApiResponse confirmed =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form(
                "excluded_payment_method_types", "",
                "payment_method", THE_WALLET,
                "return_url", A_RETURN_URL));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"excluded_payment_method_types\":null")
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]");
    assertThat(theStoredPayment().selection().excluded()).isNull();
  }

  @Test
  void an_empty_exclusion_on_confirm_is_not_refused_beside_the_types_a_payment_allowed() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form("allowed_payment_method_types[]", "card"));

    StripeApiResponse confirmed =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("excluded_payment_method_types", "", "payment_method", THE_CARD));

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"allowed_payment_method_types\":[\"card\"]")
        .contains("\"excluded_payment_method_types\":null");
  }

  @Test
  void a_confirm_refused_by_the_selection_it_sent_names_that_selection_and_not_the_intents() {
    anAccountHoldingACardAndAWallet();
    createAPayment(THE_NEWEST_RELEASE, "pln", form());

    StripeApiResponse refused =
        confirmThePayment(
            THE_NEWEST_RELEASE,
            form("allowed_payment_method_types[]", "card", "payment_method", THE_WALLET));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"payment_intent_incompatible_payment_method\"")
        .contains(
            "\"message\":\"The PaymentMethod provided (paypal) is not allowed by the payment"
                + " method types this request selects: card. This PaymentIntent keeps the payment"
                + " method types it had.\"")
        .contains("\"allowed_payment_method_types\":null")
        .contains("\"payment_method_types\":[\"card\",\"paypal\"]");
  }

  @Test
  void a_setup_confirm_refused_by_the_selection_it_sent_names_that_selection() {
    anAccountHoldingACardAndAWallet();
    String setup =
        String.valueOf(
            createASetup(THE_NEWEST_RELEASE, form("customer", THE_CUSTOMER)).providerObjectId());

    StripeApiResponse refused =
        confirmTheSetup(
            THE_NEWEST_RELEASE,
            setup,
            form("allowed_payment_method_types[]", "card", "payment_method", THE_WALLET));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"setup_intent_invalid_parameter\"")
        .contains(
            "\"message\":\"The PaymentMethod provided (paypal) is not allowed by the payment"
                + " method types this request selects: card. This SetupIntent keeps the payment"
                + " method types it had.\"");
  }

  @Test
  void an_endive_confirm_addresses_the_intent_before_it_reads_payment_method_types() {
    anAccountHoldingACardAndAWallet();
    createAPayment(
        THE_NEWEST_RELEASE,
        "pln",
        form("confirm", "true", "payment_method", THE_CARD, "return_url", A_RETURN_URL));

    StripeApiResponse unknown =
        confirmTheIntent(
            THE_NEWEST_RELEASE, "pi_sim_nobody", form("payment_method_types[]", "card"));
    StripeApiResponse succeeded =
        confirmThePayment(THE_NEWEST_RELEASE, form("payment_method_types[]", "card"));

    assertThat(unknown.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(unknown)).contains("\"code\":\"resource_missing\"");
    assertThat(succeeded.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(succeeded)).contains("\"code\":\"payment_intent_unexpected_state\"");
  }

  @Test
  void a_setup_confirm_whose_selection_is_refused_embeds_the_setup_and_changes_nothing() {
    anAccountHoldingACardAndAWallet();
    String setup =
        String.valueOf(
            createASetup(THE_NEWEST_RELEASE, form("customer", THE_CUSTOMER)).providerObjectId());
    StripeSetupIntent before = setupIntents.find(E2E_RUN, setup).orElseThrow();

    StripeApiResponse refused =
        confirmTheSetup(
            THE_NEWEST_RELEASE,
            setup,
            form(
                "allowed_payment_method_types[]", "bitcoin",
                "payment_method", THE_CARD,
                "return_url", A_RETURN_URL));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"param\":\"allowed_payment_method_types[0]\"")
        .contains("\"setup_intent\":{\"id\":\"" + setup + "\"");
    assertThat(setupIntents.find(E2E_RUN, setup).orElseThrow()).isEqualTo(before);
    assertThat(paymentMethods.find(E2E_RUN, THE_CARD).orElseThrow().attached()).isFalse();
  }

  private void anAccount() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
  }

  private void anAccountHoldingACardAndAWallet() {
    anAccount();
    customers.insertIfAbsent(
        new StripeCustomer(
            E2E_RUN, THE_CUSTOMER, ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
    paymentMethods.insertIfAbsent(aMethod(THE_CARD, StripePaymentMethodType.CARD));
    paymentMethods.insertIfAbsent(aMethod(THE_WALLET, StripePaymentMethodType.PAYPAL));
  }

  private static StripePaymentMethod aMethod(String id, StripePaymentMethodType type) {
    return new StripePaymentMethod(
        E2E_RUN,
        id,
        ACCOUNT,
        null,
        type,
        type.describesACard() ? new FixtureCard("visa", "4242", 12, 2030, "credit") : null,
        null,
        ObjectOrigin.CONTROL_API,
        NOW,
        NOW);
  }

  private StripePaymentIntent theStoredPayment() {
    return intents.find(E2E_RUN, FIRST_INTENT).orElseThrow();
  }

  private StripeApiResponse createAPayment(
      String version, String currency, Map<String, List<String>> selection) {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    sent.put("amount", List.of("1000"));
    sent.put("currency", List.of(currency));
    sent.putAll(selection);
    return api().createPaymentIntent(call(version, PAYMENT_INTENTS, sent));
  }

  private StripeApiResponse confirmThePayment(String version, Map<String, List<String>> sent) {
    return confirmTheIntent(version, FIRST_INTENT, sent);
  }

  private StripeApiResponse confirmTheIntent(
      String version, String intentId, Map<String, List<String>> sent) {
    return api()
        .confirmPaymentIntent(
            call(version, PAYMENT_INTENTS + "/" + intentId + "/confirm", sent), intentId);
  }

  private StripeApiResponse retrieveThePayment(String version) {
    return api()
        .retrievePaymentIntent(
            new StripeApiCall(
                "GET", PAYMENT_INTENTS + "/" + FIRST_INTENT, headers(version), Map.of()),
            FIRST_INTENT);
  }

  private StripeApiResponse createASetup(String version, Map<String, List<String>> sent) {
    return api().createSetupIntent(call(version, SETUP_INTENTS, sent));
  }

  private StripeApiResponse confirmTheSetup(
      String version, String setup, Map<String, List<String>> sent) {
    return api()
        .confirmSetupIntent(call(version, SETUP_INTENTS + "/" + setup + "/confirm", sent), setup);
  }

  private static StripeApiCall call(String version, String path, Map<String, List<String>> form) {
    return new StripeApiCall("POST", path, headers(version), form);
  }

  private static Map<String, List<String>> headers(String version) {
    return Map.of(
        "Authorization", List.of("Bearer " + SECRET_KEY), "Stripe-Version", List.of(version));
  }

  private static Map<String, List<String>> form(String... namesAndValues) {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    for (int at = 0; at < namesAndValues.length; at += 2) {
      sent.computeIfAbsent(namesAndValues[at], name -> new ArrayList<>())
          .add(namesAndValues[at + 1]);
    }
    return sent;
  }

  private StripeApi api() {
    return new StripeApi(
        new RecordedUnitsOfWork(),
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
}
