package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeChargeObjectTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-charge-object-run");

  private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String ANOTHER_RUNS_SECRET_KEY = "sk_test_51AnotherRun";
  private static final String THREE_DAYS = "P3D";
  private static final String A_CARD = "pm_the_card";
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
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
  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();

  @Test
  void a_payment_method_seeded_under_the_prefix_a_charge_is_minted_with_is_refused() {
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
                                StripeChargeMinting.ID_PREFIX + "stolen"))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.PAYMENT_METHOD_PROPERTY);
    assertThat(refused)
        .hasMessage(StripeFixtureCards.A_PAYMENT_METHOD_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  @Test
  void a_customer_seeded_under_the_prefix_a_charge_is_minted_with_is_refused() {
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
                                StripeChargeMinting.ID_PREFIX + "stolen"))));

    assertThat(refused.property()).isEqualTo(StripeFixtureCards.CUSTOMER_PROPERTY);
    assertThat(refused).hasMessage(StripeFixtureCards.A_CUSTOMER_ID_IS_NOT_A_MINTED_OBJECT_ID);
  }

  @Test
  void a_charge_is_named_by_the_payment_it_was_minted_on_and_by_nothing_on_the_object_surface() {
    aHoldOf("1000");
    String chargeId = intents.find(E2E_RUN, FIRST_INTENT).orElseThrow().latestCharge();

    ProviderObjectPage page = provider().objects(new ProviderObjectQuery(E2E_RUN, null, 10));

    assertThat(idsOn(page)).containsExactly(FIRST_INTENT).doesNotContain(chargeId);
    assertThat(provider().state(E2E_RUN, chargeId)).isEmpty();
    assertThat(provider().state(E2E_RUN, FIRST_INTENT)).isPresent();
  }

  @Test
  void resetting_a_test_run_takes_its_charges_and_counts_only_the_objects_it_lists() {
    aHoldOf("1000");
    assertThat(charges.all()).hasSize(1);

    ResetOutcome outcome = provider().reset(E2E_RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(1);
    assertThat(charges.all()).isEmpty();
    assertThat(intents.all()).isEmpty();
  }

  @Test
  void resetting_the_whole_simulator_takes_the_charges_of_every_test_run_with_it() {
    accounts.with(stripeAccount());
    accounts.with(StripeDoubles.account(DEFAULT_RUN, ACCOUNT, ANOTHER_RUNS_SECRET_KEY, THREE_DAYS));
    aHoldOf("1000", SECRET_KEY);
    aHoldOf("2000", ANOTHER_RUNS_SECRET_KEY);
    assertThat(theRunsHoldingACharge()).containsExactlyInAnyOrder(E2E_RUN, DEFAULT_RUN);

    provider().reset(E2E_RUN, ResetScope.ALL);

    assertThat(theRunsHoldingACharge()).isEmpty();
  }

  private List<TestRunId> theRunsHoldingACharge() {
    List<TestRunId> holding = new ArrayList<>();
    for (StripeCharge charge : charges.all()) {
      holding.add(charge.testRunId());
    }
    return holding;
  }

  private void aHoldOf(String amount) {
    accounts.with(stripeAccount());
    aHoldOf(amount, SECRET_KEY);
  }

  private void aHoldOf(String amount, String secretKey) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", A_CARD);
    form.put("confirm", "true");
    api()
        .createPaymentIntent(
            new StripeApiCall(
                "POST",
                "/v1/payment_intents",
                StripePaymentIntentTest.bearer(secretKey),
                StripePaymentIntentTest.fieldsOf(form)));
  }

  private static List<String> idsOn(ProviderObjectPage page) {
    List<String> listed = new ArrayList<>();
    for (ProviderObjectState object : ((ProviderObjectPage.Listed) page).objects()) {
      listed.add(object.providerObjectId());
    }
    return listed;
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
    return StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, THREE_DAYS);
  }

  private StripeApi api() {
    return new StripeApi(
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
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(setupIntents, paymentMethods, customers, scenarioEngine));
  }

  private SimulatorProvider provider() {
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
  }
}
