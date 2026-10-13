package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheSingleIdentifierFloor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeOriginationTest {

  private static final TestRunId RUN = TestRunId.of("stripe-origination");

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

  private static final String ACCOUNT = "merchant-1";

  private static final String A_HOLD = "capture_method=manual";

  private static final String A_CHARGE = "capture_method=automatic";

  private static final String SEEDED_CARD = "pm_card_visa";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
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

  private final SimulatorProvider provider =
      new StripeProvider(
          accounts,
          intents,
          charges,
          refunds,
          customers,
          paymentMethods,
          setupIntents,
          ephemeralKeys,
          keys,
          audit,
          new InMemoryPaymentMethodFixtures(),
          scenarioRules,
          StripeDoubles.heldResponses(),
          StripeDoubles.clockAt(NOW),
          StripeDoubles.noDeliveries(accounts),
          new StripeWebhooks(
              intents,
              refunds,
              customers,
              paymentMethods,
              setupIntents,
              accounts,
              StripeDoubles.noDeliveries(accounts),
              StripeDoubles.clockAt(NOW),
              ids),
          new CapturePaymentIntent(intents, scenarioEngine),
          new CancelPaymentIntent(intents, scenarioEngine),
          new CreateRefund(intents, refunds, charges, ids, scenarioEngine),
          new CreatePaymentIntent(
              intents,
              paymentMethods,
              charges,
              ids,
              scenarioEngine,
              StripeDoubles.NO_SCHEDULED_OUTCOMES),
          new CreateSetupIntent(setupIntents, ids),
          new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine));

  @Test
  void stripe_offers_a_hold_a_charge_and_two_ways_of_setting_a_card_up_for_a_later_charge() {
    assertThat(provider.originationFields().names())
        .containsExactly(
            A_HOLD,
            A_CHARGE,
            StripeOrigination.A_CARD_SET_UP,
            StripeOrigination.A_CARD_SET_UP_AND_CONFIRMED);
    assertThat(provider.originationFields().fieldsItFillsInItself())
        .containsOnlyKeys(StripeFields.CAPTURE_METHOD, StripeFields.CLIENT_SECRET);
  }

  @Test
  void the_screen_says_it_never_shows_a_client_secret_and_never_asks_a_tester_for_one() {
    assertThat(provider.originationFields().fieldsItFillsInItself())
        .hasEntrySatisfying(
            StripeFields.CLIENT_SECRET,
            why ->
                assertThat(why)
                    .contains("serves no publishable-key surface")
                    .contains("never puts a client_secret on a screen"));
    assertThat(provider.originationFields().waysToStartAPayment())
        .hasSize(4)
        .allSatisfy(
            way -> assertThat(way.keys()).isNotEmpty().doesNotContain(StripeFields.CLIENT_SECRET));
  }

  @Test
  void the_way_that_confirms_a_setup_itself_leaves_a_card_attached_to_the_customer_it_named() {
    aConfiguredAccount();
    customers.insertIfAbsent(
        new StripeCustomer(
            RUN, "cus_saving", ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
    paymentMethods.insertIfAbsent(
        new StripePaymentMethod(
            RUN,
            "pm_to_save",
            ACCOUNT,
            null,
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "4242", 12, 2030, "credit"),
            null,
            ObjectOrigin.CONTROL_API,
            NOW,
            NOW));

    OriginationOutcome outcome =
        startedAs(
            StripeOrigination.A_CARD_SET_UP_AND_CONFIRMED,
            Map.of(StripeFields.CUSTOMER, "cus_saving", StripeFields.PAYMENT_METHOD, "pm_to_save"));

    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    assertThat(paymentMethods.find(RUN, "pm_to_save").orElseThrow().customerId())
        .isEqualTo("cus_saving");
    assertThat(setupIntents.all())
        .singleElement()
        .satisfies(setup -> assertThat(setup.status()).isEqualTo(StripeSetupStatus.SUCCEEDED));
  }

  @Test
  void a_setup_whose_confirmation_a_scenario_answered_instead_reports_that_rule_to_the_screen() {
    aConfiguredAccount();
    customers.insertIfAbsent(
        new StripeCustomer(
            RUN, "cus_saving", ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
    paymentMethods.insertIfAbsent(
        new StripePaymentMethod(
            RUN,
            "pm_to_save",
            ACCOUNT,
            null,
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "4242", 12, 2030, "credit"),
            null,
            ObjectOrigin.CONTROL_API,
            NOW,
            NOW));
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CONFIRM_SETUP_INTENT),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    OriginationOutcome outcome =
        startedAs(
            StripeOrigination.A_CARD_SET_UP_AND_CONFIRMED,
            Map.of(StripeFields.CUSTOMER, "cus_saving", StripeFields.PAYMENT_METHOD, "pm_to_save"));

    assertThat(outcome).isInstanceOf(OriginationOutcome.AnsweredByAScenarioInstead.class);
    assertThat(paymentMethods.find(RUN, "pm_to_save").orElseThrow().attached()).isFalse();
    assertThat(setupIntents.all())
        .singleElement()
        .satisfies(
            setup -> assertThat(setup.status()).isEqualTo(StripeSetupStatus.REQUIRES_CONFIRMATION));
  }

  @Test
  void the_way_that_leaves_a_setup_waiting_attaches_nothing_and_says_what_confirms_it() {
    aConfiguredAccount();

    OriginationOutcome outcome =
        provider.originate(
            OriginationRequest.of(
                RUN,
                ACCOUNT,
                StripeOrigination.A_CARD_SET_UP,
                Map.of(StripeFields.CUSTOMER, "cus_saving"),
                ObjectOrigin.UI));

    assertThat(((OriginationOutcome.Originated) outcome).whatExistsNow())
        .contains("POST /v1/setup_intents/{setupIntentId}/confirm");
    assertThat(setupIntents.all())
        .singleElement()
        .satisfies(
            setup ->
                assertThat(setup.status()).isEqualTo(StripeSetupStatus.REQUIRES_PAYMENT_METHOD));
  }

  @Test
  void a_setup_a_tester_confirmed_with_a_method_another_customer_holds_says_so_and_attaches_none() {
    aConfiguredAccount();
    customers.insertIfAbsent(
        new StripeCustomer(
            RUN, "cus_saving", ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
    customers.insertIfAbsent(
        new StripeCustomer(
            RUN, "cus_other", ACCOUNT, null, null, ObjectOrigin.CONTROL_API, NOW, NOW));
    paymentMethods.insertIfAbsent(
        new StripePaymentMethod(
            RUN,
            "pm_of_the_other",
            ACCOUNT,
            "cus_other",
            StripePaymentMethodType.CARD,
            new FixtureCard("visa", "4242", 12, 2030, "credit"),
            null,
            ObjectOrigin.CONTROL_API,
            NOW,
            NOW));

    OriginationOutcome outcome =
        startedAs(
            StripeOrigination.A_CARD_SET_UP_AND_CONFIRMED,
            Map.of(
                StripeFields.CUSTOMER,
                "cus_saving",
                StripeFields.PAYMENT_METHOD,
                "pm_of_the_other"));

    assertThat(((OriginationOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains(
            "Payment methods attached to other Customers cannot be used with this SetupIntent.")
        .contains("The SetupIntent itself was created and is on the Payments screen");
    assertThat(paymentMethods.find(RUN, "pm_of_the_other").orElseThrow().customerId())
        .isEqualTo("cus_other");
  }

  @Test
  void a_hold_a_tester_confirmed_stands_at_requires_capture_and_is_recorded_as_theirs() {
    aConfiguredAccount();

    OriginationOutcome outcome = startedAs(A_HOLD, Map.of(StripeFields.CONFIRM, StripeFlag.TRUE));

    ProviderObjectState intent = theStateOf(outcome);
    assertThat(intent.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(intent.origin()).isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void a_charge_a_tester_confirmed_succeeds_in_the_one_call_and_leaves_no_hold_to_capture() {
    aConfiguredAccount();

    OriginationOutcome outcome = startedAs(A_CHARGE, Map.of(StripeFields.CONFIRM, StripeFlag.TRUE));

    assertThat(theStateOf(outcome).normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_payment_a_tester_did_not_confirm_waits_to_be_confirmed_and_holds_nothing() {
    aConfiguredAccount();

    OriginationOutcome outcome = startedAs(A_HOLD, Map.of());

    ProviderObjectState intent = theStateOf(outcome);
    assertThat(intent.nativeStatus())
        .isEqualTo(StripeIntentStatus.REQUIRES_CONFIRMATION.wireValue());
    assertThat(intent.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
  }

  @Test
  void a_payment_started_on_an_account_this_run_no_longer_configures_names_no_such_account() {
    assertThat(startedAs(A_HOLD, Map.of())).isEqualTo(new OriginationOutcome.NoSuchAccount());
  }

  @Test
  void an_amount_stripe_would_not_take_is_refused_in_stripes_own_words_and_stores_no_intent() {
    aConfiguredAccount();

    OriginationOutcome refused = startedAs(A_HOLD, Map.of(StripeFields.AMOUNT, "0"));

    assertThat(refused)
        .isInstanceOf(OriginationOutcome.RefusedByTheProvider.class)
        .extracting(outcome -> ((OriginationOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .asString()
        .contains("The charge amount must be a positive integer in the smallest currency unit.");
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void a_payment_a_scenario_rule_declined_carries_that_rules_id_back_to_the_screen() {
    aConfiguredAccount();
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CREATE),
            new ScenarioAction.TerminalFailure(StripeDecline.INSUFFICIENT_FUNDS.scenarioCode()),
            null));

    OriginationOutcome refused = startedAs(A_HOLD, Map.of(StripeFields.CONFIRM, StripeFlag.TRUE));

    assertThat(refused).isInstanceOf(OriginationOutcome.RefusedByTheProvider.class);
    assertThat(refused.theRuleThatDecidedIt()).isNotNull();
  }

  @Test
  void a_payment_a_scenario_rule_answered_instead_of_running_starts_nothing_and_says_so() {
    aConfiguredAccount();
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(StripeOperations.CREATE),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    OriginationOutcome outcome = startedAs(A_HOLD, Map.of());

    assertThat(outcome).isInstanceOf(OriginationOutcome.AnsweredByAScenarioInstead.class);
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void every_object_stripe_reports_keeps_the_one_identifier_it_was_created_under() {
    aConfiguredAccount();

    OriginationOutcome outcome = startedAs(A_HOLD, Map.of());

    TheSingleIdentifierFloor.holdsFor(
        provider, RUN, ((OriginationOutcome.Originated) outcome).providerObjectId());
    TheSingleIdentifierFloor.holdsForEveryObjectOf(provider, RUN);
  }

  private ProviderObjectState theStateOf(OriginationOutcome outcome) {
    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    return provider
        .state(RUN, ((OriginationOutcome.Originated) outcome).providerObjectId())
        .orElseThrow();
  }

  private OriginationOutcome startedAs(String way, Map<String, String> extra) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(StripeFields.AMOUNT, "1000");
    values.put(StripeFields.CURRENCY, "usd");
    values.put(StripeFields.PAYMENT_METHOD, SEEDED_CARD);
    values.putAll(extra);
    return provider.originate(OriginationRequest.of(RUN, ACCOUNT, way, values, ObjectOrigin.UI));
  }

  private void aConfiguredAccount() {
    accounts.merge(RUN, List.of(StripeDoubles.account(RUN, ACCOUNT, "sk_test_51Simulator", "P3D")));
  }
}
