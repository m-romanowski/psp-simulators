package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
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
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeCustomerObjectTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant LATER = Instant.parse("2026-09-11T13:00:00Z");

  private static final String ACCOUNT = "merchant-1";

  private static final String A_CUSTOMER = "cus_sim_0000000000000001";
  private static final String A_SEEDED_CUSTOMER = "cus_the_tester_named";
  private static final String AN_INTENT = "pi_sim_0000000000000001";

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
  void a_customer_is_listed_beside_the_payments_of_its_test_run() {
    intents.insertIfAbsent(anIntent(), null);
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    ProviderObjectPage page = provider().objects(new ProviderObjectQuery(E2E_RUN, null, 10));

    assertThat(idsOn(page)).containsExactly(A_CUSTOMER, AN_INTENT);
    assertThat(idsOn(provider().objects(new ProviderObjectQuery(DEFAULT_RUN, null, 10)))).isEmpty();
  }

  @Test
  void a_customer_is_opened_by_the_read_that_takes_one_id() {
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    ProviderObjectState state = provider().state(E2E_RUN, A_CUSTOMER).orElseThrow();

    assertThat(state.providerObjectId()).isEqualTo(A_CUSTOMER);
    assertThat(state.nativeStatus()).isEqualTo(StripeCustomerObject.A_CUSTOMER_STRIPE_STILL_HOLDS);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(state.relatedObjects()).isEqualTo(Map.of(StripeCustomerObject.ACCOUNT, ACCOUNT));
  }

  @Test
  void a_deleted_customer_stays_on_the_read_surface_and_says_it_was_deleted() {
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, LATER));

    ProviderObjectState state = provider().state(E2E_RUN, A_CUSTOMER).orElseThrow();

    assertThat(state.nativeStatus()).isEqualTo(StripeCustomerObject.A_CUSTOMER_STRIPE_HAS_DELETED);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(idsOn(provider().objects(new ProviderObjectQuery(E2E_RUN, null, 10))))
        .contains(A_CUSTOMER);
  }

  @Test
  void a_customer_carries_no_money_so_no_balance_is_derived_from_it() {
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    ProviderObjectState state = provider().state(E2E_RUN, A_CUSTOMER).orElseThrow();

    assertThat(state.amount()).isNull();
    assertThat(state.currency()).isNull();
    assertThat(state.ledger().operations()).isEmpty();
    assertThat(state.authorizationOpen()).isFalse();
    assertThat(state.refundsAccepted()).isFalse();
    assertThat(state.derivedBalances().orElseThrow())
        .isEqualTo(
            new DerivedBalances(Amount.of("0"), Amount.of("0"), Amount.of("0"), Amount.of("0")));
  }

  @Test
  void every_money_action_on_a_customer_is_refused_in_the_providers_own_words() {
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    for (ObjectAction action : ObjectAction.values()) {
      ObjectActionOutcome outcome =
          provider()
              .act(
                  ObjectActionRequest.of(
                      E2E_RUN,
                      A_CUSTOMER,
                      action,
                      action.takesAnAmount() ? "100" : null,
                      ObjectOrigin.UI));

      assertThat(outcome)
          .isEqualTo(
              new ObjectActionOutcome.RefusedByTheProvider(
                  StripeCustomerObject.A_CUSTOMER_IS_NOT_A_PAYMENT));
    }
  }

  @Test
  void an_id_this_run_holds_no_object_under_is_still_reported_as_no_such_object() {
    ObjectActionOutcome outcome =
        provider()
            .act(
                ObjectActionRequest.of(
                    E2E_RUN, "cus_never_made", ObjectAction.CANCEL, null, ObjectOrigin.UI));

    assertThat(outcome).isEqualTo(new ObjectActionOutcome.NoSuchObject());
  }

  @Test
  void a_page_that_ended_on_a_customer_carries_on_from_it() {
    intents.insertIfAbsent(anIntent(), null);
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    ProviderObjectPage first = provider().objects(new ProviderObjectQuery(E2E_RUN, null, 1));
    ProviderObjectPage second =
        provider().objects(new ProviderObjectQuery(E2E_RUN, A_CUSTOMER, 10));

    assertThat(idsOn(first)).containsExactly(A_CUSTOMER);
    assertThat(idsOn(second)).containsExactly(AN_INTENT);
  }

  @Test
  void resetting_a_test_run_forgets_the_customers_it_held_and_counts_them() {
    intents.insertIfAbsent(anIntent(), null);
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    ResetOutcome outcome = provider().reset(E2E_RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(2);
    assertThat(customers.all()).isEmpty();
  }

  @Test
  void resetting_every_test_run_forgets_every_customer_and_counts_them() {
    intents.insertIfAbsent(anIntent(), null);
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    ResetOutcome outcome = provider().reset(E2E_RUN, ResetScope.ALL);

    assertThat(outcome.clearedObjects()).isEqualTo(2);
    assertThat(customers.all()).isEmpty();
  }

  @Test
  void a_seeded_payment_method_that_names_a_customer_records_that_customer() {
    accounts.with(stripeAccount());

    seed(fixtureNaming(Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, A_SEEDED_CUSTOMER)));

    StripeCustomer recorded = customers.find(E2E_RUN, A_SEEDED_CUSTOMER).orElseThrow();
    assertThat(recorded.accountId()).isEqualTo(ACCOUNT);
    assertThat(recorded.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(recorded.deleted()).isFalse();
    assertThat(provider().state(E2E_RUN, A_SEEDED_CUSTOMER)).isPresent();
  }

  @Test
  void a_seeded_payment_method_that_names_no_customer_records_none() {
    accounts.with(stripeAccount());

    seed(fixtureNaming(Map.of(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "pm_card_visa")));

    assertThat(customers.all()).isEmpty();
    assertThat(paymentMethods.find(E2E_RUN, "pm_card_visa").orElseThrow().customerId()).isNull();
  }

  @Test
  void a_seeded_customer_the_run_already_holds_keeps_the_customer_that_is_already_there() {
    accounts.with(stripeAccount());
    customers.insertIfAbsent(aCustomer(A_SEEDED_CUSTOMER, LATER));

    seed(fixtureNaming(Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, A_SEEDED_CUSTOMER)));

    assertThat(customers.all()).hasSize(1);
    assertThat(customers.find(E2E_RUN, A_SEEDED_CUSTOMER).orElseThrow().deleted()).isTrue();
  }

  @Test
  void a_fixture_naming_a_minted_object_id_as_its_customer_is_refused() {
    accounts.with(stripeAccount());

    SimulatorProvider provider = provider();
    NewPaymentMethodFixture namingAnIntentAsACustomer =
        fixtureNaming(Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, AN_INTENT));

    assertThatThrownBy(() -> provider.seed(namingAnIntentAsACustomer))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessage(StripeFixtureCards.A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID);

    NewPaymentMethodFixture namingARefundAsACustomer =
        fixtureNaming(
            Map.of(
                ProviderPaymentMethodFields.CUSTOMER_ID,
                CreateRefund.ID_PREFIX + "0000000000000001"));

    assertThatThrownBy(() -> provider.seed(namingARefundAsACustomer))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessage(StripeFixtureCards.A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  @Test
  void a_fixture_naming_a_customer_wider_than_a_payment_carries_is_refused() {
    accounts.with(stripeAccount());
    String tooWide = "c".repeat(StripeCustomer.LONGEST_CUSTOMER_ID + 1);

    SimulatorProvider provider = provider();
    NewPaymentMethodFixture namingACustomerWiderThanStripeCarries =
        fixtureNaming(Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, tooWide));

    assertThatThrownBy(() -> provider.seed(namingACustomerWiderThanStripeCarries))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessage(StripeFixtureCards.A_CUSTOMER_ID_IS_AS_LONG_AS_STRIPE_CARRIES);
    assertThat(
            provider()
                .seed(
                    fixtureNaming(
                        Map.of(
                            ProviderPaymentMethodFields.CUSTOMER_ID,
                            "c".repeat(StripeCustomer.LONGEST_CUSTOMER_ID)))))
        .isNotNull();
  }

  @Test
  void a_fixture_naming_a_customer_under_an_account_this_run_configures_none_of_is_refused() {
    SimulatorProvider provider = provider();
    NewPaymentMethodFixture namingACustomerOfAnotherAccount =
        fixtureNaming(Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, A_SEEDED_CUSTOMER));

    assertThatThrownBy(() -> provider.seed(namingACustomerOfAnotherAccount))
        .isInstanceOf(ProviderFixtureRejectedException.class)
        .hasMessage(StripeFixtureCards.A_CUSTOMER_IS_SERVED_THROUGH_AN_ACCOUNTS_OWN_KEY);
    assertThat(customers.all()).isEmpty();
  }

  @Test
  void the_refusal_of_an_unservable_customer_names_the_property_and_never_its_value() {
    ProviderFixtureRejectedException refused =
        catchThrowableOfType(
            ProviderFixtureRejectedException.class,
            () ->
                provider()
                    .seed(
                        fixtureNaming(
                            Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, A_SEEDED_CUSTOMER))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.CUSTOMER_PROPERTY);
    assertThat(refused.getMessage())
        .contains(StripeFixtureCards.CUSTOMER_PROPERTY)
        .contains("merchantId")
        .doesNotContain(A_SEEDED_CUSTOMER)
        .doesNotContain(ACCOUNT);
  }

  @Test
  void a_customer_a_fixture_seeded_belongs_to_the_account_that_will_answer_for_it() {
    accounts.with(stripeAccount());

    seed(fixtureNaming(Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, A_SEEDED_CUSTOMER)));

    assertThat(provider().state(E2E_RUN, A_SEEDED_CUSTOMER).orElseThrow().relatedObjects())
        .isEqualTo(Map.of(StripeCustomerObject.ACCOUNT, ACCOUNT));
  }

  @Test
  void no_callback_is_scheduled_against_a_customer_and_the_refusal_says_what_is_reported_instead() {
    customers.insertIfAbsent(aCustomer(A_CUSTOMER, null));

    SimulatorProvider provider = provider();
    WebhookScheduleRequest askingForACustomerEvent =
        new WebhookScheduleRequest(E2E_RUN, A_CUSTOMER, null, null, Duration.ZERO, null);

    assertThatThrownBy(() -> provider.schedule(askingForACustomerEvent))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(StripeWebhooks.NO_EVENT_REPORTS_A_CUSTOMER);
  }

  private void seed(NewPaymentMethodFixture asked) {
    PaymentMethodFixturePlan plan = provider().seed(asked);
    plan.store(storedFrom(asked));
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

  private static NewPaymentMethodFixture fixtureNaming(Map<String, String> providerIds) {
    Map<String, String> named = new LinkedHashMap<>();
    named.put(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, "pm_card_visa");
    named.putAll(providerIds);
    return fixtureCarrying(named);
  }

  private static NewPaymentMethodFixture fixtureCarrying(Map<String, String> providerIds) {
    return new NewPaymentMethodFixture(
        StripeApi.STRIPE,
        E2E_RUN,
        ACCOUNT,
        "owner-1",
        PaymentMethodKind.CARD,
        "a-visa",
        new FixtureCard("visa", "4242", 12, 2030, "credit"),
        providerIds,
        null,
        ObjectOrigin.UI);
  }

  private StripeAccount stripeAccount() {
    return StripeDoubles.account(E2E_RUN, ACCOUNT, "sk_test_51Simulator", "P3D");
  }

  private static StripeCustomer aCustomer(String customerId, Instant deletedAt) {
    return new StripeCustomer(
        E2E_RUN,
        customerId,
        ACCOUNT,
        null,
        deletedAt,
        ObjectOrigin.PROVIDER_API,
        NOW,
        deletedAt == null ? NOW : deletedAt);
  }

  private StripePaymentIntent anIntent() {
    return new StripePaymentIntent(
        E2E_RUN,
        AN_INTENT,
        ACCOUNT,
        Amount.of("1000"),
        "pln",
        StripeCaptureMethod.AUTOMATIC,
        StripeIntentStatus.SUCCEEDED,
        null,
        null,
        null,
        AN_INTENT + "_secret",
        null,
        null,
        null,
        NOW.plus(Duration.ofDays(7)),
        null,
        null,
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static List<String> idsOn(ProviderObjectPage page) {
    List<String> ids = new ArrayList<>();
    for (ProviderObjectState object : ((ProviderObjectPage.Listed) page).objects()) {
      ids.add(object.providerObjectId());
    }
    return ids;
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
