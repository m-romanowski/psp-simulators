package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredCard;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AzericardSeededCardTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId RUN = TestRunId.of("azericard-seeded");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

  private static final String TERMINAL_ID = "77777777";

  private static final String MERCHANT = "merchant-1";

  private static final String ORDER = "20220628084800";

  private static final String NONCE = "F2B2DD7E603A7ADA";

  private static final String SEEDED_TOKEN = "SEEDED0000000000000000000001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();
  private final InMemoryPaymentMethodFixtures fixtures =
      new InMemoryPaymentMethodFixtures().at(NOW);

  private final WebhookDispatcher callbackDeliveries =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          AzericardDoubles.clockAt(NOW),
          new AzericardDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new AzericardCallbackAcknowledgement()),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final AzericardDoubles.PayerSessions payerSessions = new AzericardDoubles.PayerSessions();

  private final SimulatorProvider provider =
      new AzericardProvider(
          accounts,
          audit,
          payments,
          cards,
          fixtures,
          scenarioRules,
          AzericardDoubles.heldResponses(),
          new AzericardCallbacks(
              payments,
              accounts,
              accounts,
              callbackDeliveries,
              AzericardDoubles.clockAt(NOW),
              new AzericardDoubles.CountingIds()),
          callbackDeliveries,
          AzericardDoubles.clockAt(NOW),
          new AzericardDoubles.CountingIds(),
          new Authorize(payments, cards, new AzericardDoubles.CountingIds(), scenarioEngine),
          new CompleteOrReverse(payments, scenarioEngine),
          payerSessions,
          AzericardDoubles.checkoutOf(
              payerSessions,
              payments,
              cards,
              accounts,
              scenarioEngine,
              audit,
              AzericardDoubles.clockAt(NOW),
              new AzericardDoubles.CountingIds()));

  private final PaymentMethodFixtureAdmission admission =
      new PaymentMethodFixtureAdmission(
          fixtures, scenarioRules, testRuns, new RecordedUnitsOfWork(), DEFAULT_RUN);

  @Test
  void a_harness_seeds_a_card_and_then_pays_with_the_token_it_chose_itself() {
    aConfiguredTerminal();
    seed(aCardSeeding(SEEDED_TOKEN, null));

    AzericardApiResponse response =
        gateway().tokenLink(tokenCall(signed(storedCardPayment(SEEDED_TOKEN))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(response)).contains("<action>0</action>").contains("<rc>00</rc>");
    assertThat(payments.all())
        .singleElement()
        .satisfies(p -> assertThat(p.token()).isEqualTo(SEEDED_TOKEN));
  }

  @Test
  void the_scenario_a_seeded_card_names_is_the_rule_that_decides_the_payment_it_is_used_for() {
    aConfiguredTerminal();
    ScenarioRule declining = aRuleDecliningTheToken();
    seed(aCardSeeding(SEEDED_TOKEN, declining.scenarioId()));

    AzericardApiResponse response =
        gateway().tokenLink(tokenCall(signed(storedCardPayment(SEEDED_TOKEN))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).contains("<action>2</action>").contains("<rc>05</rc>");
    assertThat(fixtures.of(AzericardGateway.AZERICARD, RUN))
        .singleElement()
        .satisfies(
            fixture -> assertThat(fixture.defaultScenario()).isEqualTo(declining.scenarioId()));
  }

  @Test
  void a_card_seeded_for_a_merchant_this_test_run_never_configured_is_refused_by_the_provider() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, aCardSeeding(SEEDED_TOKEN, null), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.property()).isEqualTo("merchantId");
              assertThat(refused.message()).contains("TERMINAL");
            });
    assertThat(cards.all()).isEmpty();
    assertThat(fixtures.of(AzericardGateway.AZERICARD, RUN)).isEmpty();
  }

  @Test
  void a_token_the_gateway_could_never_carry_is_refused_where_it_is_seeded() {
    aConfiguredTerminal();

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, aCardSeeding("TOO-SHORT", null), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.property()).isEqualTo("providerIds.token");
              assertThat(refused.message())
                  .contains(String.valueOf(AzericardStoredCard.TOKEN_LENGTH));
            });
    assertThat(cards.all()).isEmpty();
  }

  @Test
  void a_wallet_cannot_be_seeded_for_a_gateway_that_publishes_only_a_saved_card() {
    aConfiguredTerminal();

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPaymentMethodFixture(
                RUN.value(),
                MERCHANT,
                "customer-123",
                "wallet",
                "a-wallet",
                null,
                Map.of(ProviderPaymentMethodFields.TOKEN, SEEDED_TOKEN),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> assertThat(refused.property()).isEqualTo("type"));
  }

  @Test
  void a_token_a_payment_of_this_run_already_stored_a_card_under_cannot_be_seeded_over() {
    aConfiguredTerminal();
    cards.holding(
        AzericardStoredCard.storedBy(
            RUN, SEEDED_TOKEN, TERMINAL_ID, MERCHANT, "INTREF0000000001", ORDER, NOW));

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, aCardSeeding(SEEDED_TOKEN, null), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> assertThat(refused.message()).contains("already holds a stored card"));
  }

  @Test
  void a_seeded_card_reports_the_fixture_that_seeded_it_and_no_payment_that_stored_it() {
    aConfiguredTerminal();
    seed(aCardSeeding(SEEDED_TOKEN, null));

    Optional<ProviderObjectState> state = provider.state(RUN, SEEDED_TOKEN);

    assertThat(state)
        .get()
        .satisfies(
            object -> {
              assertThat(object.relatedObjects())
                  .containsEntry("terminal", TERMINAL_ID)
                  .containsEntry("merchantId", MERCHANT)
                  .containsKey("fixtureId")
                  .doesNotContainKey("order")
                  .doesNotContainKey("storedBy");
              assertThat(object.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
            });
  }

  @Test
  void a_card_a_tester_seeded_from_the_screen_reports_the_screen_and_not_the_control_api() {
    aConfiguredTerminal();
    admission.seed(provider, aCardSeeding(SEEDED_TOKEN, null), ObjectOrigin.UI);

    assertThat(provider.state(RUN, SEEDED_TOKEN))
        .get()
        .satisfies(object -> assertThat(object.origin()).isEqualTo(ObjectOrigin.UI));
  }

  @Test
  void a_seed_no_control_surface_asked_for_is_refused_rather_than_placing_a_card_for_nobody() {
    aConfiguredTerminal();

    PaymentMethodFixtureAdmission.Verdict refused =
        admission.seed(provider, aCardSeeding(SEEDED_TOKEN, null), ObjectOrigin.SCHEDULED_JOB);

    assertThat(refused)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(NewPaymentMethodFixture.A_FIXTURE_IS_SEEDED_BY_A_CONTROL_SURFACE));
    assertThat(cards.all()).isEmpty();
  }

  @Test
  void removing_the_fixture_takes_the_card_the_gateway_would_have_charged_with_it() {
    aConfiguredTerminal();
    PaymentMethodFixture fixture = seed(aCardSeeding(SEEDED_TOKEN, null));
    assertThat(cards.all())
        .singleElement()
        .satisfies(card -> assertThat(card.token()).isEqualTo(SEEDED_TOKEN));

    admission.forget(provider, RUN.value(), fixture.fixtureId());

    assertThat(cards.all()).isEmpty();
    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(storedCardPayment(SEEDED_TOKEN))))))
        .contains("<rc>" + AzericardOutcomes.RC_NO_SUCH_TOKEN + "</rc>");
  }

  @Test
  void resetting_a_test_run_clears_the_payment_methods_it_seeded_and_counts_them_as_cleared() {
    aConfiguredTerminal();
    seed(aCardSeeding(SEEDED_TOKEN, null));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.TEST_RUN);

    assertThat(fixtures.of(AzericardGateway.AZERICARD, RUN)).isEmpty();
    assertThat(outcome.clearedObjects()).isEqualTo(2);
  }

  @Test
  void resetting_every_test_run_clears_the_payment_methods_of_all_of_them_and_counts_them() {
    aConfiguredTerminal();
    seed(aCardSeeding(SEEDED_TOKEN, null));

    ResetOutcome outcome = provider.reset(RUN, ResetScope.ALL);

    assertThat(fixtures.of(AzericardGateway.AZERICARD, RUN)).isEmpty();
    assertThat(outcome.clearedObjects()).isEqualTo(2);
  }

  @Test
  void a_card_a_payment_stored_reports_the_payment_that_stored_it_and_the_provider_api() {
    aConfiguredTerminal();
    cards.holding(
        AzericardStoredCard.storedBy(
            RUN, SEEDED_TOKEN, TERMINAL_ID, MERCHANT, "INTREF0000000001", ORDER, NOW));

    assertThat(provider.state(RUN, SEEDED_TOKEN))
        .get()
        .satisfies(
            object -> {
              assertThat(object.relatedObjects())
                  .containsEntry("order", ORDER)
                  .containsEntry("storedBy", "INTREF0000000001")
                  .doesNotContainKey("fixtureId");
              assertThat(object.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
            });
  }

  @Test
  void the_gateway_publishes_the_token_as_the_only_provider_native_id_a_fixture_may_carry() {
    assertThat(provider.paymentMethodFields().keys())
        .containsExactly(ProviderPaymentMethodFields.TOKEN);
    assertThat(provider.paymentMethodFields().theRefusalOf(ProviderPaymentMethodFields.CUSTOMER_ID))
        .contains("publishes no customer or owner id");
  }

  @Test
  void the_card_store_refuses_a_row_that_claims_both_a_payment_and_a_fixture() {
    assertThatThrownBy(
            () ->
                new AzericardStoredCard(
                    RUN,
                    SEEDED_TOKEN,
                    TERMINAL_ID,
                    MERCHANT,
                    "INTREF0000000001",
                    ORDER,
                    "fx_both",
                    ObjectOrigin.PROVIDER_API,
                    NOW,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AzericardStoredCard.A_CARD_IS_STORED_BY_A_PAYMENT_OR_SEEDED_BY_A_FIXTURE);
  }

  @Test
  void the_card_store_refuses_a_row_that_claims_neither_a_payment_nor_a_fixture() {
    assertThatThrownBy(
            () ->
                new AzericardStoredCard(
                    RUN,
                    SEEDED_TOKEN,
                    TERMINAL_ID,
                    MERCHANT,
                    null,
                    null,
                    null,
                    ObjectOrigin.PROVIDER_API,
                    NOW,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AzericardStoredCard.A_CARD_IS_STORED_BY_A_PAYMENT_OR_SEEDED_BY_A_FIXTURE);
  }

  @Test
  void the_card_store_refuses_a_row_that_names_the_payment_that_stored_it_by_half() {
    assertThatThrownBy(
            () ->
                new AzericardStoredCard(
                    RUN,
                    SEEDED_TOKEN,
                    TERMINAL_ID,
                    MERCHANT,
                    "INTREF0000000001",
                    null,
                    null,
                    ObjectOrigin.PROVIDER_API,
                    NOW,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AzericardStoredCard.A_CARD_IS_STORED_BY_A_PAYMENT_OR_SEEDED_BY_A_FIXTURE);
  }

  private PaymentMethodFixture seed(DeclaredPaymentMethodFixture asked) {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, asked, ObjectOrigin.CONTROL_API);
    assertThat(verdict).isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Seeded.class);
    return ((PaymentMethodFixtureAdmission.Verdict.Seeded) verdict).fixture();
  }

  private ScenarioRule aRuleDecliningTheToken() {
    return scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            new ScenarioMatch(
                null, null, null, null, SEEDED_TOKEN, null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("05"),
            null));
  }

  private static DeclaredPaymentMethodFixture aCardSeeding(
      String token, @Nullable String defaultScenario) {
    return new DeclaredPaymentMethodFixture(
        RUN.value(),
        MERCHANT,
        "customer-123",
        "card",
        "visa-success",
        new DeclaredCard("visa", "4580", 4, 2024, "credit"),
        Map.of(ProviderPaymentMethodFields.TOKEN, token),
        defaultScenario);
  }

  private void aConfiguredTerminal() {
    accounts.with(AzericardDoubles.terminal(RUN, MERCHANT, TERMINAL_ID));
    testRuns.register(RUN);
  }

  private static Map<String, String> storedCardPayment(String token) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, "0.01");
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(NOW));
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.TOKEN, token);
    form.keySet().removeAll(AzericardFields.CARD_DATA);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.TOKEN_CGI_LINK, fieldsOf(signed)).macSource()));
    return signed;
  }

  private static AzericardApiCall tokenCall(Map<String, String> form) {
    return new AzericardApiCall("POST", "/token/cgi_link", Map.of(), fieldsOf(form));
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static String bodyOf(AzericardApiResponse response) {
    return new String(response.body(), UTF_8);
  }

  private AzericardGateway gateway() {
    AzericardDoubles.CountingIds ids = new AzericardDoubles.CountingIds();
    return new AzericardGateway(
        accounts,
        testRuns,
        audit,
        AzericardDoubles.clockAt(NOW),
        ids,
        DEFAULT_RUN,
        new Authorize(payments, cards, ids, scenarioEngine),
        new CompleteOrReverse(payments, scenarioEngine),
        new LookUpStatus(payments, scenarioEngine),
        AzericardDoubles.checkoutOf(
            payments, cards, accounts, scenarioEngine, audit, AzericardDoubles.clockAt(NOW), ids));
  }
}
