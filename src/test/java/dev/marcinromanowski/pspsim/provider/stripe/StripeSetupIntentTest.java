package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bearer;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.bodyOf;
import static dev.marcinromanowski.pspsim.provider.stripe.StripePaymentIntentTest.fieldsOf;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripeSetupIntentTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-setup-run");

  private static final Instant NOW = Instant.parse("2026-09-21T09:00:00Z");
  private static final Instant LATER = Instant.parse("2026-09-21T10:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String THREE_DAYS = "P3D";

  private static final String SETUP_INTENTS = "/v1/setup_intents";
  private static final String CUSTOMERS = "/v1/customers";

  private static final String THE_CUSTOMER = "cus_the_one_saving_a_card";
  private static final String ANOTHER_CUSTOMER = "cus_the_one_with_a_card_of_their_own";
  private static final String THE_CARD = "pm_the_card_being_set_up";
  private static final String ANOTHER_CUSTOMERS_CARD = "pm_the_card_another_customer_holds";
  private static final String A_DETACHED_CARD = "pm_the_card_that_was_detached";
  private static final String NO_METHOD_OF_THIS_RUN = "pm_this_run_holds_nothing_under_this";
  private static final String NO_SETUP_OF_THIS_RUN = "seti_this_run_holds_nothing_under_this";

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
  void a_confirmed_setup_attaches_the_seeded_card_to_the_customer_the_setup_names() {
    anAccountHoldingAnUnattachedCardOfNobody();

    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));
    StripeApiResponse confirmed = confirm(setup, Map.of());

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"object\":\"setup_intent\"")
        .contains("\"status\":\"succeeded\"")
        .contains("\"customer\":\"" + THE_CUSTOMER + "\"")
        .contains("\"payment_method\":\"" + THE_CARD + "\"");
    assertThat(theStoredCard(THE_CARD).customerId()).isEqualTo(THE_CUSTOMER);
  }

  @Test
  void the_card_a_confirmed_setup_attached_is_listed_under_that_customer_and_was_not_before() {
    anAccountHoldingAnUnattachedCardOfNobody();

    List<String> before = idsIn(bodyOf(theMethodsOf(THE_CUSTOMER)));
    confirm(theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD)), Map.of());
    List<String> after = idsIn(bodyOf(theMethodsOf(THE_CUSTOMER)));

    assertThat(before).isEmpty();
    assertThat(after).containsExactly(THE_CARD);
  }

  @Test
  void a_setup_created_with_no_payment_method_waits_for_one_and_the_confirm_names_it() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse created = aSetupIntentFor(THE_CUSTOMER, null);
    StripeApiResponse confirmed =
        confirm(theSetupIdIn(created), Map.of(StripeFields.PAYMENT_METHOD, THE_CARD));

    assertThat(bodyOf(created))
        .contains("\"status\":\"requires_payment_method\"")
        .contains("\"payment_method\":null");
    assertThat(bodyOf(confirmed))
        .contains("\"status\":\"succeeded\"")
        .contains("\"payment_method\":\"" + THE_CARD + "\"");
    assertThat(theStoredCard(THE_CARD).customerId()).isEqualTo(THE_CUSTOMER);
  }

  @Test
  void a_setup_created_with_a_payment_method_stands_ready_to_confirm_rather_than_waiting_for_one() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse created = aSetupIntentFor(THE_CUSTOMER, THE_CARD);

    assertThat(bodyOf(created)).contains("\"status\":\"requires_confirmation\"");
  }

  @Test
  void usage_is_off_session_when_the_request_names_none_and_carries_the_value_it_named() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse defaulted = aSetupIntentFor(THE_CUSTOMER, null);
    StripeApiResponse named =
        create(Map.of(StripeFields.CUSTOMER, THE_CUSTOMER, StripeFields.USAGE, "on_session"));

    assertThat(bodyOf(defaulted)).contains("\"usage\":\"off_session\"");
    assertThat(bodyOf(named)).contains("\"usage\":\"on_session\"");
  }

  @Test
  void a_usage_provider_stripe_does_not_publish_is_refused_by_the_name_of_the_parameter() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse refused = create(Map.of(StripeFields.USAGE, "whenever"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"param\":\"usage\"")
        .contains("on_session|off_session")
        .doesNotContain("whenever");
    assertThat(setupIntents.all()).isEmpty();
  }

  @Test
  void payment_method_types_is_answered_on_the_object_and_refused_as_a_creation_parameter() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse created = aSetupIntentFor(THE_CUSTOMER, null);
    StripeApiResponse refused = create(Map.of(StripeFields.PAYMENT_METHOD_TYPES, "card"));

    assertThat(bodyOf(created)).contains("\"payment_method_types\":[\"card\",\"paypal\"]");
    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"parameter_unknown\"")
        .contains("\"param\":\"payment_method_types\"")
        .contains("automatic_payment_methods");
    assertThat(setupIntents.all()).hasSize(1);
  }

  @Test
  void payment_method_types_is_refused_in_the_bracketed_form_stripes_own_encoding_produces() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse refused = create(Map.of("payment_method_types[]", "card"));
    StripeApiResponse indexed = create(Map.of("payment_method_types[0]", "card"));

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("\"param\":\"payment_method_types\"");
    assertThat(indexed.httpStatus()).isEqualTo(400);
    assertThat(setupIntents.all()).isEmpty();
  }

  @Test
  void the_selection_parameter_whose_name_begins_like_a_payment_method_is_not_mistaken_for_one() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse created =
        create(Map.of(StripeFields.PAYMENT_METHOD_CONFIGURATION, "pmc_simulated"));

    assertThat(created.httpStatus()).isEqualTo(200);
  }

  @Test
  void the_four_selection_parameters_provider_stripe_publishes_are_taken_without_refusal() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse created =
        create(
            Map.of(
                StripeFields.CUSTOMER, THE_CUSTOMER,
                StripeFields.AUTOMATIC_PAYMENT_METHODS, "true",
                StripeFields.ALLOWED_PAYMENT_METHOD_TYPES, "card",
                StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES, "paypal",
                StripeFields.PAYMENT_METHOD_CONFIGURATION, "pmc_simulated"));

    assertThat(created.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(created)).contains("\"object\":\"setup_intent\"");
  }

  @Test
  void a_confirm_of_a_setup_this_account_holds_none_of_is_answered_resource_missing() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse refused =
        confirm(NO_SETUP_OF_THIS_RUN, Map.of(StripeFields.PAYMENT_METHOD, THE_CARD));

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"intent\"");
    assertThat(theStoredCard(THE_CARD).attached()).isFalse();
  }

  @Test
  void a_confirm_naming_a_payment_method_this_account_holds_none_of_is_resource_missing() {
    anAccountHoldingAnUnattachedCardOfNobody();

    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, null));
    StripeApiResponse refused =
        confirm(setup, Map.of(StripeFields.PAYMENT_METHOD, NO_METHOD_OF_THIS_RUN));

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"payment_method\"");
    assertThat(theStoredSetup(setup).status()).isEqualTo(StripeSetupStatus.REQUIRES_PAYMENT_METHOD);
  }

  @Test
  void a_confirm_naming_a_method_another_customer_holds_is_refused_in_the_published_sentence() {
    anAccountHoldingAnUnattachedCardOfNobody();
    customers.insertIfAbsent(aCustomer(ANOTHER_CUSTOMER));
    paymentMethods.insertIfAbsent(aCardOf(ANOTHER_CUSTOMERS_CARD, ANOTHER_CUSTOMER));

    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, ANOTHER_CUSTOMERS_CARD));
    StripeApiResponse refused = confirm(setup, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(
            "Payment methods attached to other Customers cannot be used with this SetupIntent.")
        .contains("\"param\":\"payment_method\"");
    assertThat(theStoredCard(ANOTHER_CUSTOMERS_CARD).customerId()).isEqualTo(ANOTHER_CUSTOMER);
  }

  @Test
  void a_confirm_naming_a_detached_method_is_refused_and_re_attaches_nothing() {
    anAccountHoldingAnUnattachedCardOfNobody();
    paymentMethods.insertIfAbsent(aDetachedCard());

    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, A_DETACHED_CARD));
    StripeApiResponse refused = confirm(setup, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("Detachment is permanent and irreversible.")
        .contains("\"param\":\"payment_method\"");
    assertThat(theStoredCard(A_DETACHED_CARD).customerId()).isNull();
    assertThat(theStoredCard(A_DETACHED_CARD).detached()).isTrue();
    assertThat(theStoredSetup(setup).status()).isEqualTo(StripeSetupStatus.REQUIRES_CONFIRMATION);
  }

  @Test
  void a_confirm_for_a_customer_this_account_deleted_is_refused_in_the_words_that_deletion_earns() {
    anAccountHoldingAnUnattachedCardOfNobody();
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));
    api(NOW).deleteCustomer(delete(CUSTOMERS + "/" + THE_CUSTOMER), THE_CUSTOMER);

    StripeApiResponse refused = confirm(setup, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("Deleting customers prevents any further operations to be performed.")
        .contains("\"param\":\"customer\"");
    assertThat(theStoredCard(THE_CARD).attached()).isFalse();
  }

  @Test
  void a_confirm_for_a_customer_this_account_never_held_is_answered_resource_missing() {
    anAccountHoldingAnUnattachedCardOfNobody();

    String setup = theSetupIdIn(aSetupIntentFor("cus_nobody_created", THE_CARD));
    StripeApiResponse refused = confirm(setup, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"customer\"");
    assertThat(theStoredCard(THE_CARD).attached()).isFalse();
  }

  @Test
  void
      a_confirmed_setup_that_names_no_customer_succeeds_and_leaves_the_method_attached_to_nobody() {
    anAccountHoldingAnUnattachedCardOfNobody();

    String setup = theSetupIdIn(create(Map.of(StripeFields.PAYMENT_METHOD, THE_CARD)));
    StripeApiResponse confirmed = confirm(setup, Map.of());

    assertThat(confirmed.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"status\":\"succeeded\"")
        .contains("\"customer\":null");
    assertThat(theStoredCard(THE_CARD).attached()).isFalse();
  }

  @Test
  void a_declined_setup_returns_to_requires_payment_method_and_carries_the_decline_it_met() {
    anAccountHoldingAnUnattachedCardOfNobody();
    declining(StripeDecline.GENERIC_DECLINE);

    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));
    StripeApiResponse declined = confirm(setup, Map.of());

    assertThat(declined.httpStatus()).isEqualTo(402);
    assertThat(bodyOf(declined))
        .contains("\"type\":\"card_error\"")
        .contains("\"code\":\"card_declined\"")
        .contains("\"decline_code\":\"generic_decline\"")
        .contains("\"setup_intent\":")
        .contains("\"status\":\"requires_payment_method\"")
        .contains("\"last_setup_error\":");
    assertThat(theStoredSetup(setup).status()).isEqualTo(StripeSetupStatus.REQUIRES_PAYMENT_METHOD);
    assertThat(theStoredSetup(setup).lastSetupError()).isEqualTo(StripeDecline.GENERIC_DECLINE);
  }

  @Test
  void a_setup_that_met_a_decline_attached_nothing_to_the_customer_it_named() {
    anAccountHoldingAnUnattachedCardOfNobody();
    declining(StripeDecline.EXPIRED_CARD);

    confirm(theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD)), Map.of());

    assertThat(theStoredCard(THE_CARD).attached()).isFalse();
    assertThat(idsIn(bodyOf(theMethodsOf(THE_CUSTOMER)))).isEmpty();
  }

  @Test
  void a_setup_that_needed_authentication_answers_the_published_code_and_its_decline_code() {
    anAccountHoldingAnUnattachedCardOfNobody();
    declining(StripeDecline.AUTHENTICATION_REQUIRED);

    StripeApiResponse refused =
        confirm(theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD)), Map.of());

    assertThat(refused.httpStatus()).isEqualTo(402);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"authentication_required\"")
        .contains("\"decline_code\":\"authentication_not_handled\"")
        .contains("The payment requires authentication to proceed.");
  }

  @Test
  void a_setup_that_already_succeeded_refuses_a_second_confirmation_by_the_state_it_stands_in() {
    anAccountHoldingAnUnattachedCardOfNobody();
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));
    confirm(setup, Map.of());

    StripeApiResponse again = confirm(setup, Map.of());

    assertThat(again.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(again))
        .contains("\"code\":\"setup_intent_unexpected_state\"")
        .contains("A SetupIntent with a status of succeeded cannot be confirmed.")
        .contains("\"setup_intent\":");
  }

  @Test
  void a_confirm_that_names_no_payment_method_anywhere_is_refused_by_the_parameter_it_needs() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse refused =
        confirm(theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, null)), Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"payment_method\"");
  }

  @Test
  void a_setup_whose_status_moved_under_a_confirmation_is_refused_rather_than_written_twice() {
    anAccountHoldingAnUnattachedCardOfNobody();
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));
    setupIntents.losingTheStatusRaceOnTheNextWrite();

    StripeApiResponse refused = confirm(setup, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"setup_intent_unexpected_state\"")
        .contains("changed by two requests at once");
  }

  @Test
  void a_setup_the_store_lost_under_a_confirmation_is_answered_resource_missing() {
    anAccountHoldingAnUnattachedCardOfNobody();
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));
    setupIntents.losingTheSetupOnTheNextWrite();

    StripeApiResponse refused = confirm(setup, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"intent\"");
  }

  @Test
  void the_client_secret_is_answered_on_the_wire_and_withheld_from_the_audit_trail() {
    anAccountHoldingAnUnattachedCardOfNobody();

    StripeApiResponse created = aSetupIntentFor(THE_CUSTOMER, null);

    String secret = theStoredSetup(theSetupIdIn(created)).clientSecret();
    assertThat(bodyOf(created)).contains("\"client_secret\":\"" + secret + "\"");
    assertThat(audit.of(StripeApi.STRIPE, E2E_RUN))
        .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .allSatisfy(
            record -> {
              assertThat(record.sanitizedBody()).containsKey(StripeFields.CLIENT_SECRET);
              assertThat(record.sanitizedBody().get(StripeFields.CLIENT_SECRET))
                  .doesNotContain(secret);
            });
  }

  @Test
  void a_declined_setup_shows_the_state_it_returned_to_and_carries_no_client_secret_at_all() {
    anAccountHoldingAnUnattachedCardOfNobody();
    declining(StripeDecline.GENERIC_DECLINE);
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));

    StripeApiResponse declined = confirm(setup, Map.of());

    assertThat(bodyOf(declined))
        .contains("\"setup_intent\":")
        .contains("\"status\":\"requires_payment_method\"")
        .doesNotContain(StripeFields.CLIENT_SECRET);
  }

  @Test
  void a_client_secret_nested_inside_an_error_object_is_withheld_from_the_audit_trail_too() {
    Map<String, List<String>> answered =
        new LinkedHashMap<>(
            Map.of(
                "error.setup_intent.client_secret", List.of("seti_sim_1_secret_2"),
                "error.setup_intent.id", List.of("seti_sim_1")));

    Map<String, List<String>> withheld =
        StripeFields.withholding(answered, StripeFields.SENSITIVE_RESPONSE_FIELDS);

    assertThat(withheld.get("error.setup_intent.client_secret")).containsExactly("[redacted]");
    assertThat(withheld.get("error.setup_intent.id")).containsExactly("seti_sim_1");
  }

  @Test
  void a_setup_of_one_account_is_not_confirmable_with_the_secret_key_of_another() {
    anAccountHoldingAnUnattachedCardOfNobody();
    accounts.with(StripeDoubles.account(E2E_RUN, "merchant-2", "sk_test_51Another", THREE_DAYS));
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));

    StripeApiResponse refused =
        api(NOW)
            .confirmSetupIntent(
                new StripeApiCall(
                    "POST",
                    SETUP_INTENTS + "/" + setup + "/confirm",
                    bearer("sk_test_51Another"),
                    Map.of()),
                setup);

    assertThat(refused.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .contains("\"param\":\"intent\"")
        .doesNotContain("\"param\":\"payment_method\"");
    assertThat(theStoredCard(THE_CARD).attached()).isFalse();
  }

  @Test
  void a_confirmed_setup_is_recorded_against_the_setup_it_confirmed_on_the_audit_trail() {
    anAccountHoldingAnUnattachedCardOfNobody();
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));

    confirm(setup, Map.of());

    assertThat(audit.of(StripeApi.STRIPE, E2E_RUN))
        .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .filteredOn(record -> SETUP_INTENTS.length() < String.valueOf(record.path()).length())
        .last()
        .satisfies(record -> assertThat(record.providerObjectId()).isEqualTo(setup));
  }

  @Test
  void a_setup_whose_attachment_the_store_refused_is_refused_and_the_setup_does_not_succeed() {
    anAccountHoldingAnUnattachedCardOfNobody();
    String setup = theSetupIdIn(aSetupIntentFor(THE_CUSTOMER, THE_CARD));
    setupIntents.losingTheAttachmentOnTheNextWrite();

    StripeApiResponse refused = confirm(setup, Map.of());

    assertThat(refused.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(StripeSetupLookup.THE_METHOD_IS_NO_LONGER_ATTACHABLE)
        .contains("\"param\":\"payment_method\"");
    assertThat(theStoredSetup(setup).status()).isEqualTo(StripeSetupStatus.REQUIRES_CONFIRMATION);
    assertThat(theStoredCard(THE_CARD).attached()).isFalse();
  }

  @Test
  void a_setup_draws_a_second_id_when_the_first_one_this_run_already_holds() {
    anAccountHoldingAnUnattachedCardOfNobody();
    setupIntents.insertIfAbsent(aSetupUnder(CreateSetupIntent.ID_PREFIX + "0000000000000001"));

    StripeApiResponse created = aSetupIntentFor(THE_CUSTOMER, null);

    assertThat(created.providerObjectId())
        .isEqualTo(CreateSetupIntent.ID_PREFIX + "0000000000000002");
  }

  @Test
  void a_setup_that_cannot_claim_an_id_gives_up_loudly_rather_than_looping() {
    anAccountHoldingAnUnattachedCardOfNobody();
    for (int drawn = 1; drawn <= CreateSetupIntent.ID_ATTEMPTS; drawn++) {
      setupIntents.insertIfAbsent(
          aSetupUnder(CreateSetupIntent.ID_PREFIX + String.format("%016x", drawn)));
    }

    Assertions.assertThatThrownBy(() -> aSetupIntentFor(THE_CUSTOMER, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(CreateSetupIntent.ID_ATTEMPTS));
  }

  @Test
  void a_stored_setup_refuses_a_setup_error_in_any_status_but_the_one_a_failure_returns_to() {
    Assertions.assertThatThrownBy(
            () ->
                aSetupAt(
                    StripeSetupStatus.REQUIRES_CONFIRMATION, THE_CARD, StripeDecline.EXPIRED_CARD))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(StripeSetupIntent.A_FAILED_SETUP_RETURNS_TO_REQUIRES_PAYMENT_METHOD);
  }

  @Test
  void a_stored_setup_refuses_to_report_a_setup_of_nothing() {
    Assertions.assertThatThrownBy(() -> aSetupAt(StripeSetupStatus.SUCCEEDED, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(StripeSetupIntent.A_SUCCEEDED_SETUP_SET_A_METHOD_UP);
  }

  private static StripeSetupIntent aSetupUnder(String setupIntentId) {
    return new StripeSetupIntent(
        E2E_RUN,
        setupIntentId,
        ACCOUNT,
        null,
        null,
        StripeSetupUsage.OFF_SESSION,
        StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
        setupIntentId + StripeClientSecret.INFIX + "x",
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static StripeSetupIntent aSetupAt(
      StripeSetupStatus status, @Nullable String paymentMethod, @Nullable StripeDecline decline) {
    return new StripeSetupIntent(
        E2E_RUN,
        "seti_sim_under_test",
        ACCOUNT,
        THE_CUSTOMER,
        paymentMethod,
        StripeSetupUsage.OFF_SESSION,
        status,
        "seti_sim_under_test_secret_x",
        decline,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private void anAccountHoldingAnUnattachedCardOfNobody() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS));
    customers.insertIfAbsent(aCustomer(THE_CUSTOMER));
    paymentMethods.insertIfAbsent(aCardOf(THE_CARD, null));
  }

  private void declining(StripeDecline decline) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CONFIRM_SETUP_INTENT),
            new ScenarioAction.TerminalFailure(decline.scenarioCode()),
            null));
  }

  private StripeApiResponse aSetupIntentFor(String customerId, @Nullable String paymentMethod) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(StripeFields.CUSTOMER, customerId);
    if (paymentMethod != null) {
      form.put(StripeFields.PAYMENT_METHOD, paymentMethod);
    }
    return create(form);
  }

  private StripeApiResponse create(Map<String, String> form) {
    return api(NOW)
        .createSetupIntent(
            new StripeApiCall(
                "POST", SETUP_INTENTS, bearer(SECRET_KEY), fieldsOf(new LinkedHashMap<>(form))));
  }

  private StripeApiResponse confirm(String setupIntentId, Map<String, String> form) {
    return api(LATER)
        .confirmSetupIntent(
            new StripeApiCall(
                "POST",
                SETUP_INTENTS + "/" + setupIntentId + "/confirm",
                bearer(SECRET_KEY),
                fieldsOf(new LinkedHashMap<>(form))),
            setupIntentId);
  }

  private StripeApiResponse theMethodsOf(String customerId) {
    String path = CUSTOMERS + "/" + customerId + "/payment_methods";
    return api(LATER)
        .listCustomerPaymentMethods(
            new StripeApiCall("GET", path, bearer(SECRET_KEY), Map.of()), customerId);
  }

  private static StripeApiCall delete(String path) {
    return new StripeApiCall("DELETE", path, bearer(SECRET_KEY), Map.of());
  }

  private static String theSetupIdIn(StripeApiResponse response) {
    return String.valueOf(response.providerObjectId());
  }

  private StripeSetupIntent theStoredSetup(String setupIntentId) {
    return setupIntents.find(E2E_RUN, setupIntentId).orElseThrow();
  }

  private StripePaymentMethod theStoredCard(String paymentMethodId) {
    return paymentMethods.find(E2E_RUN, paymentMethodId).orElseThrow();
  }

  private static List<String> idsIn(String body) {
    List<String> found = new ArrayList<>();
    String marker = "{\"id\":\"";
    int from = 0;
    while (true) {
      int at = body.indexOf(marker, from);
      if (at < 0) {
        return List.copyOf(found);
      }
      int start = at + marker.length();
      int end = body.indexOf('"', start);
      found.add(body.substring(start, end));
      from = end;
    }
  }

  private static StripeCustomer aCustomer(String customerId) {
    return new StripeCustomer(
        E2E_RUN, customerId, ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW);
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

  private static StripePaymentMethod aDetachedCard() {
    return new StripePaymentMethod(
        E2E_RUN,
        A_DETACHED_CARD,
        ACCOUNT,
        null,
        StripePaymentMethodType.CARD,
        new FixtureCard("visa", "1881", 1, 2031, "credit"),
        NOW,
        ObjectOrigin.CONTROL_API,
        NOW,
        NOW);
  }

  private StripeApi api(Instant now) {
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
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine));
  }
}
