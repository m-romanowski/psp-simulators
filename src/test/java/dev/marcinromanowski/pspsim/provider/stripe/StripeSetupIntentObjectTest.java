package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripeSetupIntentObjectTest {

  private static final TestRunId E2E_RUN = TestRunId.of("stripe-setup-object-run");

  private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String A_SETUP = "seti_sim_the_one_a_tester_reads";
  private static final String A_CUSTOMER = "cus_the_owner";
  private static final String A_CARD = "pm_the_card";
  private static final String AN_EARLIER_SETUP = "seti_sim_the_one_before_it";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
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
  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();

  @Test
  void a_setup_intent_is_listed_among_the_objects_of_the_run_and_opened_by_its_own_id() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.REQUIRES_CONFIRMATION, null));

    ProviderObjectPage page =
        provider()
            .objects(
                ProviderObjectQuery.of(
                    new DeclaredObjectQuery(E2E_RUN.value(), null, "10"), E2E_RUN));
    ProviderObjectState opened = provider().state(E2E_RUN, A_SETUP).orElseThrow();

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .contains(A_SETUP);
    assertThat(opened.relatedObjects())
        .containsEntry(StripeSetupIntentObject.ACCOUNT, ACCOUNT)
        .containsEntry(StripeFields.CUSTOMER, A_CUSTOMER);
  }

  @Test
  void
      a_setup_awaiting_its_confirmation_reads_as_the_status_stripe_publishes_and_initializes_none() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.REQUIRES_CONFIRMATION, null));

    ProviderObjectState state = provider().state(E2E_RUN, A_SETUP).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo("setup_intent requires_confirmation");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
  }

  @Test
  void a_setup_that_set_a_card_up_reads_as_succeeded_and_names_the_method_it_attached() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.SUCCEEDED, null));

    ProviderObjectState state = provider().state(E2E_RUN, A_SETUP).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo("setup_intent succeeded");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(state.relatedObjects()).containsEntry("paymentMethod", A_CARD);
  }

  @Test
  void a_setup_that_met_a_decline_reads_as_failed_although_the_status_stripe_publishes_is_not() {
    setupIntents.insertIfAbsent(
        aSetupAt(StripeSetupStatus.REQUIRES_PAYMENT_METHOD, StripeDecline.GENERIC_DECLINE));

    ProviderObjectState state = provider().state(E2E_RUN, A_SETUP).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo("setup_intent requires_payment_method");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
  }

  @Test
  void a_setup_names_its_account_then_the_customer_then_the_method_it_attached() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.SUCCEEDED, null));

    ProviderObjectState state = provider().state(E2E_RUN, A_SETUP).orElseThrow();

    assertThat(state.relatedObjects().keySet())
        .as(
            "a screen, a read and a control answer print the objects a setup intent names in the"
                + " order this provider named them, and an unordered copy prints them in a"
                + " different order in every JVM that runs this simulator")
        .containsExactly(StripeSetupIntentObject.ACCOUNT, StripeFields.CUSTOMER, "paymentMethod");
  }

  @Test
  void one_answer_for_two_setup_intents_holds_what_asking_for_each_of_them_holds() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.SUCCEEDED, null));
    setupIntents.insertIfAbsent(anEarlierSetup());
    List<String> asked = List.of(AN_EARLIER_SETUP, ACCOUNT, A_SETUP, "seti_never_minted");

    List<ProviderObjectState> batched = provider().states(E2E_RUN, asked);

    assertThat(batched)
        .as(
            "a screen labels every identifier an object names from this answer, so one answer for"
                + " many identifiers that held anything other than what asking one at a time"
                + " holds would change what a tester reads beside those names. %s",
            TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(provider(), E2E_RUN, asked));
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider(), E2E_RUN, asked));
    assertThat(batched)
        .extracting(ProviderObjectState::providerObjectId)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(AN_EARLIER_SETUP, A_SETUP);
  }

  @Test
  void a_setup_naming_no_customer_and_no_method_is_tied_to_its_account_and_to_nothing_else() {
    setupIntents.insertIfAbsent(
        new StripeSetupIntent(
            E2E_RUN,
            A_SETUP,
            ACCOUNT,
            null,
            null,
            StripeSetupUsage.ON_SESSION,
            StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
            A_SETUP + "_secret_never_on_a_screen",
            null,
            ObjectOrigin.PROVIDER_API,
            NOW,
            NOW));

    ProviderObjectState state = provider().state(E2E_RUN, A_SETUP).orElseThrow();

    assertThat(state.relatedObjects()).containsOnlyKeys(StripeSetupIntentObject.ACCOUNT);
  }

  @Test
  void a_setup_intent_carries_no_amount_and_no_money_verb_is_offered_on_it() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.SUCCEEDED, null));

    ProviderObjectState state = provider().state(E2E_RUN, A_SETUP).orElseThrow();
    ObjectActionOutcome refused =
        provider()
            .act(
                new ObjectActionRequest(
                    E2E_RUN, A_SETUP, ObjectAction.REFUND, Amount.of("100"), ObjectOrigin.UI));

    assertThat(state.amount()).isNull();
    assertThat(state.currency()).isNull();
    assertThat(((ObjectActionOutcome.RefusedByTheProvider) refused).inItsOwnWords())
        .isEqualTo(StripeSetupIntentObject.A_SETUP_INTENT_IS_NOT_A_PAYMENT);
  }

  @Test
  void no_callback_this_build_delivers_reports_a_setup_intent_and_the_refusal_names_the_two() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.SUCCEEDED, null));

    WebhookNotSchedulableException refused =
        catchThrowableOfType(
            WebhookNotSchedulableException.class,
            () ->
                provider()
                    .schedule(
                        new WebhookScheduleRequest(
                            E2E_RUN, A_SETUP, null, null, null, ObjectOrigin.UI)));

    assertThat(refused).hasMessage(StripeWebhooks.NO_EVENT_REPORTS_A_SETUP_INTENT);
    assertThat(refused.getMessage())
        .contains("setup_intent.succeeded")
        .contains("setup_intent.setup_failed");
  }

  @Test
  void a_payment_method_seeded_under_the_prefix_a_setup_intent_is_minted_with_is_refused() {
    accounts.with(stripeAccount());

    ProviderFixtureRejectedException refused =
        catchThrowableOfType(
            ProviderFixtureRejectedException.class,
            () ->
                provider()
                    .seed(
                        aFixtureOf(
                            Map.of(
                                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                                CreateSetupIntent.ID_PREFIX + "stolen"))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.PAYMENT_METHOD_PROPERTY);
    assertThat(refused)
        .hasMessage(StripeFixtureCards.A_PAYMENT_METHOD_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  @Test
  void a_customer_seeded_under_the_prefix_a_setup_intent_is_minted_with_is_refused() {
    accounts.with(stripeAccount());

    ProviderFixtureRejectedException refused =
        catchThrowableOfType(
            ProviderFixtureRejectedException.class,
            () ->
                provider()
                    .seed(
                        aFixtureOf(
                            Map.of(
                                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                                A_CARD,
                                ProviderPaymentMethodFields.CUSTOMER_ID,
                                CreateSetupIntent.ID_PREFIX + "stolen"))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.CUSTOMER_PROPERTY);
    assertThat(refused).hasMessage(StripeFixtureCards.A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  @Test
  void resetting_a_test_run_counts_the_setup_intents_it_held_among_what_it_cleared() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.SUCCEEDED, null));

    int cleared = provider().reset(E2E_RUN, ResetScope.TEST_RUN).clearedObjects();

    assertThat(cleared).isEqualTo(1);
    assertThat(setupIntents.all()).isEmpty();
  }

  @Test
  void resetting_every_test_run_counts_the_setup_intents_of_all_of_them_among_what_it_cleared() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.SUCCEEDED, null));

    int cleared = provider().reset(E2E_RUN, ResetScope.ALL).clearedObjects();

    assertThat(cleared).isEqualTo(1);
    assertThat(setupIntents.all()).isEmpty();
  }

  @Test
  void a_page_of_objects_carries_on_after_a_setup_intent_the_previous_page_ended_on() {
    setupIntents.insertIfAbsent(anEarlierSetup());
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.REQUIRES_CONFIRMATION, null));

    ProviderObjectPage page =
        provider()
            .objects(
                ProviderObjectQuery.of(
                    new DeclaredObjectQuery(E2E_RUN.value(), A_SETUP, "10"), E2E_RUN));

    assertThat(((ProviderObjectPage.Listed) page).objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(AN_EARLIER_SETUP);
  }

  @Test
  void a_cursor_no_object_of_this_run_stands_at_is_refused_rather_than_read_as_the_first_page() {
    setupIntents.insertIfAbsent(aSetupAt(StripeSetupStatus.REQUIRES_CONFIRMATION, null));

    ProviderObjectPage page =
        provider()
            .objects(
                ProviderObjectQuery.of(
                    new DeclaredObjectQuery(E2E_RUN.value(), "seti_sim_nobody_minted", "10"),
                    E2E_RUN));

    assertThat(page).isInstanceOf(ProviderObjectPage.NoSuchCursor.class);
  }

  private StripeSetupIntent anEarlierSetup() {
    return new StripeSetupIntent(
        E2E_RUN,
        AN_EARLIER_SETUP,
        ACCOUNT,
        A_CUSTOMER,
        A_CARD,
        StripeSetupUsage.OFF_SESSION,
        StripeSetupStatus.SUCCEEDED,
        AN_EARLIER_SETUP + "_secret_never_on_a_screen",
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private StripeSetupIntent aSetupAt(StripeSetupStatus status, @Nullable StripeDecline decline) {
    return new StripeSetupIntent(
        E2E_RUN,
        A_SETUP,
        ACCOUNT,
        A_CUSTOMER,
        A_CARD,
        StripeSetupUsage.OFF_SESSION,
        status,
        A_SETUP + "_secret_never_on_a_screen",
        decline,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static NewPaymentMethodFixture aFixtureOf(Map<String, String> providerIds) {
    return new NewPaymentMethodFixture(
        StripeApi.STRIPE,
        E2E_RUN,
        ACCOUNT,
        "owner-1",
        PaymentMethodKind.CARD,
        "a-card",
        new FixtureCard("visa", "4242", 12, 2030, "credit"),
        new LinkedHashMap<>(providerIds),
        null,
        ObjectOrigin.UI);
  }

  private StripeAccount stripeAccount() {
    return StripeDoubles.account(E2E_RUN, ACCOUNT, "sk_test_51Simulator", "P3D");
  }

  private SimulatorProvider provider() {
    ScenarioEngine engine = new ScenarioEngine(scenarioRules);
    return new StripeProvider(
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
        seededMethods,
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
        new CapturePaymentIntent(intents, engine),
        new CancelPaymentIntent(intents, engine),
        new CreateRefund(intents, refunds, charges, ids, engine),
        new CreatePaymentIntent(
            intents, paymentMethods, charges, ids, engine, StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, engine));
  }
}
