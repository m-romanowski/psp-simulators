package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class StripePaymentMethodObjectTest {

  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant LATER = Instant.parse("2026-09-11T13:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String A_CARD = "pm_the_tester_named";
  private static final String A_CUSTOMER = "cus_the_tester_named";

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
  void a_seeded_card_is_stored_as_the_payment_method_the_published_routes_answer_for() {
    accounts.with(stripeAccount());

    seed(aFixtureNaming(Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD)));

    StripePaymentMethod stored = paymentMethods.find(E2E_RUN, A_CARD).orElseThrow();
    assertThat(stored.accountId()).isEqualTo(ACCOUNT);
    assertThat(stored.type()).isEqualTo(StripePaymentMethodType.CARD);
    assertThat(stored.customerId()).isNull();
    assertThat(stored.detached()).isFalse();
    assertThat(stored.origin()).isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void a_seeded_wallet_is_stored_as_the_one_wallet_type_this_contract_publishes_a_hash_for() {
    accounts.with(stripeAccount());

    seed(
        aFixtureOf(
            PaymentMethodKind.WALLET,
            null,
            Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD)));

    assertThat(paymentMethods.find(E2E_RUN, A_CARD).orElseThrow().type())
        .isEqualTo(StripePaymentMethodType.PAYPAL);
  }

  @Test
  void a_seeded_psp_token_is_refused_because_this_build_models_no_token_object() {
    accounts.with(stripeAccount());

    ProviderFixtureRejectedException refused =
        catchThrowableOfType(
            ProviderFixtureRejectedException.class,
            () ->
                provider()
                    .seed(
                        aFixtureOf(
                            PaymentMethodKind.PSP_TOKEN,
                            null,
                            Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.TYPE_PROPERTY);
    assertThat(refused.getMessage()).contains("no Token object");
  }

  @Test
  void a_card_seeded_under_an_account_no_key_of_this_run_belongs_to_is_refused_on_the_account() {
    ProviderFixtureRejectedException refused =
        catchThrowableOfType(
            ProviderFixtureRejectedException.class,
            () ->
                provider()
                    .seed(
                        aFixtureNaming(
                            Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.MERCHANT_PROPERTY);
    assertThat(refused.getMessage())
        .contains("resolves the method through the secret key")
        .doesNotContain(A_CARD);
    assertThat(paymentMethods.all()).isEmpty();
  }

  @Test
  void a_payment_method_id_that_is_a_minted_object_id_is_refused_on_its_own_property() {
    accounts.with(stripeAccount());

    SimulatorProvider provider = provider();
    NewPaymentMethodFixture namingACustomerAsAPaymentMethod =
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                CreateCustomer.ID_PREFIX + "0000000000000001"));

    assertThatThrownBy(() -> provider.seed(namingACustomerAsAPaymentMethod))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessage(StripeFixtureCards.A_PAYMENT_METHOD_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  @Test
  void a_payment_method_id_wider_than_a_payment_carries_is_refused_on_its_own_property() {
    accounts.with(stripeAccount());

    SimulatorProvider provider = provider();
    NewPaymentMethodFixture namingAnIdWiderThanStripeCarries =
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                "p".repeat(StripePaymentMethod.LONGEST_PAYMENT_METHOD_ID + 1)));

    assertThatThrownBy(() -> provider.seed(namingAnIdWiderThanStripeCarries))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessage(StripeFixtureCards.A_PAYMENT_METHOD_ID_IS_AS_LONG_AS_STRIPE_CARRIES);
  }

  @Test
  void a_payment_method_id_exactly_as_wide_as_a_payment_carries_is_seeded_rather_than_refused() {
    accounts.with(stripeAccount());
    String widest = "p".repeat(StripePaymentMethod.LONGEST_PAYMENT_METHOD_ID);

    seed(aFixtureNaming(Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, widest)));

    assertThat(paymentMethods.find(E2E_RUN, widest)).isPresent();
  }

  @Test
  void a_page_that_ended_on_a_payment_method_carries_on_from_it() {
    accounts.with(stripeAccount());
    seed(aFixtureNaming(Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD)));
    seed(
        aFixtureOf(
            PaymentMethodKind.CARD,
            new FixtureCard("visa", "4242", 12, 2030, "credit"),
            Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "pm_the_second")));

    ProviderObjectPage first = provider().objects(new ProviderObjectQuery(E2E_RUN, null, 1));
    ProviderObjectPage second =
        provider().objects(new ProviderObjectQuery(E2E_RUN, "pm_the_second", 10));

    assertThat(idsOn(first)).containsExactly("pm_the_second");
    assertThat(idsOn(second)).containsExactly(A_CARD);
  }

  @Test
  void an_attached_method_reports_the_object_value_stripe_publishes_and_a_registered_method() {
    accounts.with(stripeAccount());
    seed(
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD,
                ProviderPaymentMethodFields.CUSTOMER_ID, A_CUSTOMER)));

    ProviderObjectState state = provider().state(E2E_RUN, A_CARD).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo(StripePaymentMethodObject.A_METHOD_A_CUSTOMER_HOLDS);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(state.relatedObjects())
        .containsEntry(StripePaymentMethodObject.ACCOUNT, ACCOUNT)
        .containsEntry(StripeFields.CUSTOMER, A_CUSTOMER);
  }

  @Test
  void an_attached_method_names_the_account_it_belongs_to_before_the_customer_that_holds_it() {
    accounts.with(stripeAccount());
    seed(
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD,
                ProviderPaymentMethodFields.CUSTOMER_ID, A_CUSTOMER)));

    ProviderObjectState state = provider().state(E2E_RUN, A_CARD).orElseThrow();

    assertThat(state.relatedObjects().keySet())
        .as(
            "a screen, a read and a control answer print the objects a method names in the order"
                + " this provider named them, and an unordered copy prints them in a different"
                + " order in every JVM that runs this simulator")
        .containsExactly(StripePaymentMethodObject.ACCOUNT, StripeFields.CUSTOMER);
  }

  @Test
  void one_answer_for_a_method_and_the_customer_it_names_holds_what_two_reads_hold() {
    accounts.with(stripeAccount());
    seed(
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD,
                ProviderPaymentMethodFields.CUSTOMER_ID, A_CUSTOMER)));
    List<String> asked = List.of(A_CARD, ACCOUNT, A_CUSTOMER, "pm_never_seeded");

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
        .containsExactly(A_CARD, A_CUSTOMER);
  }

  @Test
  void one_identifier_two_of_this_providers_stores_hold_is_labelled_as_the_single_read_labels_it() {
    accounts.with(stripeAccount());
    seed(
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "one_id_two_stores_hold",
                ProviderPaymentMethodFields.CUSTOMER_ID, "one_id_two_stores_hold")));
    List<String> asked = List.of("one_id_two_stores_hold");

    List<ProviderObjectState> batched = provider().states(E2E_RUN, asked);

    assertThat(paymentMethods.find(E2E_RUN, "one_id_two_stores_hold"))
        .as(
            "the identifiers a seeded method is driven by are tester-supplied strings, so one"
                + " string can name a payment method and a customer at once, and the label a"
                + " tester reads for it is then decided by the order this provider walks its"
                + " stores in")
        .isPresent();
    assertThat(customers.find(E2E_RUN, "one_id_two_stores_hold")).isPresent();
    assertThat(batched).hasSize(2);
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider(), E2E_RUN, asked));
  }

  @Test
  void a_detached_method_reports_a_customer_of_null_and_an_object_a_caller_ended() {
    accounts.with(stripeAccount());
    seed(
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD,
                ProviderPaymentMethodFields.CUSTOMER_ID, A_CUSTOMER)));
    paymentMethods.detached(paymentMethods.find(E2E_RUN, A_CARD).orElseThrow(), LATER);

    ProviderObjectState state = provider().state(E2E_RUN, A_CARD).orElseThrow();

    assertThat(state.nativeStatus())
        .isEqualTo(StripePaymentMethodObject.A_METHOD_NO_CUSTOMER_HOLDS);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(state.relatedObjects()).doesNotContainKey(StripeFields.CUSTOMER);
  }

  @Test
  void no_money_action_is_offered_on_a_payment_method_and_the_refusal_names_the_intent_instead() {
    accounts.with(stripeAccount());
    seed(aFixtureNaming(Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD)));

    ObjectActionOutcome outcome =
        provider()
            .act(
                new ObjectActionRequest(
                    E2E_RUN, A_CARD, ObjectAction.CANCEL, null, ObjectOrigin.UI));

    assertThat(outcome)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                StripePaymentMethodObject.A_PAYMENT_METHOD_IS_NOT_A_PAYMENT));
  }

  @Test
  void removing_the_fixture_removes_the_payment_method_it_seeded_and_leaves_the_customer() {
    accounts.with(stripeAccount());
    NewPaymentMethodFixture asked =
        aFixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD,
                ProviderPaymentMethodFields.CUSTOMER_ID, A_CUSTOMER));
    seed(asked);

    provider().forgetFixture(storedFrom(asked)).remove();

    assertThat(paymentMethods.find(E2E_RUN, A_CARD)).isEmpty();
    assertThat(customers.find(E2E_RUN, A_CUSTOMER)).isPresent();
  }

  @Test
  void no_callback_is_scheduled_against_a_payment_method_and_the_refusal_says_what_is_reported() {
    accounts.with(stripeAccount());
    seed(aFixtureNaming(Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, A_CARD)));

    SimulatorProvider provider = provider();
    WebhookScheduleRequest askingForAPaymentMethodEvent =
        new WebhookScheduleRequest(E2E_RUN, A_CARD, null, null, Duration.ZERO, null);

    assertThatThrownBy(() -> provider.schedule(askingForAPaymentMethodEvent))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(StripeWebhooks.NO_EVENT_REPORTS_A_PAYMENT_METHOD);
  }

  private static List<String> idsOn(ProviderObjectPage page) {
    List<String> ids = new ArrayList<>();
    for (ProviderObjectState object : ((ProviderObjectPage.Listed) page).objects()) {
      ids.add(object.providerObjectId());
    }
    return List.copyOf(ids);
  }

  private void seed(NewPaymentMethodFixture asked) {
    provider().seed(asked).store(storedFrom(asked));
  }

  private static PaymentMethodFixture storedFrom(NewPaymentMethodFixture asked) {
    return new PaymentMethodFixture(
        "fx_1",
        asked.provider(),
        asked.testRunId(),
        asked.merchantId(),
        asked.ownerId(),
        asked.type(),
        asked.fixtureAlias(),
        asked.card(),
        asked.providerIds(),
        asked.defaultScenario(),
        NOW);
  }

  private static NewPaymentMethodFixture aFixtureNaming(Map<String, String> providerIds) {
    return aFixtureOf(
        PaymentMethodKind.CARD, new FixtureCard("visa", "4242", 12, 2030, "credit"), providerIds);
  }

  private static NewPaymentMethodFixture aFixtureOf(
      PaymentMethodKind type, @Nullable FixtureCard card, Map<String, String> providerIds) {
    return new NewPaymentMethodFixture(
        StripeApi.STRIPE,
        E2E_RUN,
        ACCOUNT,
        "owner-1",
        type,
        "a-card",
        card,
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
