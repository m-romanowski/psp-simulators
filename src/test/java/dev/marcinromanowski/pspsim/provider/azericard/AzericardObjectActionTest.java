package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AzericardObjectActionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId RUN = TestRunId.of("azericard-object-actions");

  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-run");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

  private static final String NOW_ON_THE_WIRE = "20260910120000";

  private static final String TERMINAL_ID = "77777777";

  private static final String MERCHANT = "merchant-1";

  private static final String ROUTE = "/cgi-bin/cgi_link";

  private static final String NONCE = "F2B2DD7E603A7ADA";

  private static final String HELD = "11.48";

  private static final String OVER_THE_WIRE = "20220628084800";

  private static final String FROM_THE_SCREEN = "20220628084801";

  private static final String SEEDED_TOKEN = "SEEDED0000000000000000000001";

  private static final String NOT_ADMITTED = "provider azericard answered ACTION=3 RC=-3 DESC=";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();
  private final AzericardDoubles.CountingIds gatewayIds = new AzericardDoubles.CountingIds();
  private final CompleteOrReverse completeOrReverse =
      new CompleteOrReverse(payments, scenarioEngine);

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
          new InMemoryPaymentMethodFixtures(),
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
          completeOrReverse,
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

  private final AzericardGateway gateway =
      new AzericardGateway(
          accounts,
          testRuns,
          audit,
          AzericardDoubles.clockAt(NOW),
          gatewayIds,
          DEFAULT_RUN,
          new Authorize(payments, cards, gatewayIds, scenarioEngine),
          completeOrReverse,
          new LookUpStatus(payments, scenarioEngine),
          AzericardDoubles.checkoutOf(
              payments,
              cards,
              accounts,
              scenarioEngine,
              audit,
              AzericardDoubles.clockAt(NOW),
              gatewayIds));

  @ParameterizedTest(name = "azericard offers {0}")
  @EnumSource(ObjectAction.class)
  void a_tester_is_offered_every_action_on_an_azericard_payment_and_told_of_no_absence(
      ObjectAction action) {
    assertThat(provider.objectActions().provider()).isEqualTo(AzericardGateway.AZERICARD);
    assertThat(provider.objectActions().whatItHasInstead()).isEmpty();
    assertThat(provider.objectActions().offers(action)).isTrue();
  }

  @Test
  void a_tester_who_completes_a_hold_from_a_control_surface_moves_what_the_wire_moves() {
    aRunConfiguringTheTerminal();
    AzericardPayment overTheWire = aHoldFor(OVER_THE_WIRE);
    AzericardPayment fromTheScreen = aHoldFor(FROM_THE_SCREEN);
    gateway.cgiLink(call(signed(settlementOf(overTheWire, "21", HELD))));

    ObjectActionOutcome outcome = asked(fromTheScreen.intRef(), ObjectAction.CAPTURE, HELD);

    assertThat(outcome).isEqualTo(new ObjectActionOutcome.Applied(fromTheScreen.intRef(), null));
    assertThat(thePaymentFor(FROM_THE_SCREEN).ledger())
        .isEqualTo(thePaymentFor(OVER_THE_WIRE).ledger());
    assertThat(thePaymentFor(FROM_THE_SCREEN).normalizedStatus())
        .isEqualTo(thePaymentFor(OVER_THE_WIRE).normalizedStatus())
        .isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(thePaymentFor(FROM_THE_SCREEN).authorizationOpen())
        .isEqualTo(thePaymentFor(OVER_THE_WIRE).authorizationOpen());
  }

  @Test
  void a_tester_who_cancels_an_uncompleted_hold_from_a_control_surface_reads_it_back_cancelled() {
    aRunConfiguringTheTerminal();
    AzericardPayment hold = aHoldFor(OVER_THE_WIRE);

    ObjectActionOutcome outcome = asked(hold.intRef(), ObjectAction.CANCEL, null);

    assertThat(outcome).isEqualTo(new ObjectActionOutcome.Applied(hold.intRef(), null));
    assertThat(thePaymentFor(OVER_THE_WIRE).normalizedStatus())
        .isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(thePaymentFor(OVER_THE_WIRE).ledger().operations()).hasSize(1);
  }

  @Test
  void a_cancel_a_tester_took_reaches_the_gateway_as_the_reversal_trtype_azericard_publishes() {
    aRunConfiguringTheTerminal();
    AzericardPayment hold = aHoldFor(OVER_THE_WIRE);
    ScenarioRule declining = decline(AzericardTransactionType.ONLINE_REVERSAL, "05");

    ObjectActionOutcome refused = asked(hold.intRef(), ObjectAction.CANCEL, null);

    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                "provider azericard answered ACTION=2 RC=05 DESC=", declining.scenarioId()));
    assertThat(thePaymentFor(OVER_THE_WIRE).normalizedStatus())
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_settlement_a_control_surface_builds_carries_the_fields_the_gateway_reads_below_the_wire() {
    aRunConfiguringTheTerminal();
    AzericardPayment hold = aHoldFor(OVER_THE_WIRE);

    AzericardMessage built = theSettlementBuiltFor(hold, ObjectAction.CANCEL, null);

    assertThat(built.terminal()).isEqualTo(TERMINAL_ID);
    assertThat(built.trtype()).isEqualTo(AzericardTransactionType.ONLINE_REVERSAL.wireValue());
    assertThat(built.order()).isEqualTo(OVER_THE_WIRE);
    assertThat(built.amount()).isEqualTo(HELD);
    assertThat(built.currency()).isEqualTo("AZN");
    assertThat(built.rrn()).isEqualTo(hold.rrn());
    assertThat(built.intRef()).isEqualTo(hold.intRef());
    assertThat(built.sentAt()).isEqualTo(NOW);
    assertThat(built.nonce()).isEqualTo("0000000000000001");
  }

  @Test
  void a_completion_a_control_surface_builds_asks_for_the_amount_the_tester_named() {
    aRunConfiguringTheTerminal();
    AzericardPayment hold = aHoldFor(OVER_THE_WIRE);

    AzericardMessage built = theSettlementBuiltFor(hold, ObjectAction.CAPTURE, "1.00");

    assertThat(built.trtype()).isEqualTo(AzericardTransactionType.SALES_COMPLETION.wireValue());
    assertThat(built.amount()).isEqualTo("1.00");
  }

  @Test
  void a_control_surface_sends_no_mac_because_its_only_key_signs_one_the_gateway_rejects() {
    aRunConfiguringTheTerminal();
    AzericardPayment hold = aHoldFor(OVER_THE_WIRE);
    AzericardTerminal terminal = accounts.all().get(0);

    AzericardMessage built = theSettlementBuiltFor(hold, ObjectAction.CANCEL, null);

    assertThat(built.unusableField()).isEqualTo(AzericardFields.REQUIRED_P_SIGN);
    assertThat(
            AzericardSignature.verifies(
                built.macSource(),
                AzericardKeyFixture.signedWith(built.macSource(), terminal.gatewayKey()),
                terminal.merchantKey()))
        .isFalse();
  }

  @Test
  void a_tester_who_returns_a_completed_payment_in_full_reads_it_back_refunded_and_gains_nothing() {
    aRunConfiguringTheTerminal();
    AzericardPayment payment = aHoldFor(OVER_THE_WIRE);
    asked(payment.intRef(), ObjectAction.CAPTURE, HELD);

    ObjectActionOutcome outcome = asked(payment.intRef(), ObjectAction.REFUND, HELD);

    assertThat(outcome).isEqualTo(new ObjectActionOutcome.Applied(payment.intRef(), null));
    assertThat(thePaymentFor(OVER_THE_WIRE).normalizedStatus())
        .isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(payments.all()).hasSize(1);
    assertThat(cards.all()).isEmpty();
  }

  @Test
  void a_tester_who_asks_for_part_of_a_completed_payment_back_is_refused_in_azericards_own_words() {
    aRunConfiguringTheTerminal();
    AzericardPayment payment = aHoldFor(OVER_THE_WIRE);
    asked(payment.intRef(), ObjectAction.CAPTURE, HELD);

    ObjectActionOutcome refused = asked(payment.intRef(), ObjectAction.REFUND, "1.00");

    assertThat(refused).isEqualTo(new ObjectActionOutcome.RefusedByTheProvider(NOT_ADMITTED));
    assertThat(thePaymentFor(OVER_THE_WIRE).normalizedStatus())
        .isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(thePaymentFor(OVER_THE_WIRE).ledger().operations()).hasSize(2);
  }

  @Test
  void a_tester_completing_a_payment_the_wire_already_completed_is_refused_with_the_same_pair() {
    aRunConfiguringTheTerminal();
    AzericardPayment payment = aHoldFor(OVER_THE_WIRE);
    gateway.cgiLink(call(signed(settlementOf(payment, "21", HELD))));

    ObjectActionOutcome refused = asked(payment.intRef(), ObjectAction.CAPTURE, HELD);

    assertThat(bodyOf(gateway.cgiLink(call(signed(settlementOf(payment, "21", HELD))))))
        .contains("<action>" + AzericardOutcomes.ACTION_ERROR + "</action>")
        .contains("<rc>" + AzericardOutcomes.RC_NOT_ADMITTED + "</rc>");
    assertThat(refused).isEqualTo(new ObjectActionOutcome.RefusedByTheProvider(NOT_ADMITTED));
  }

  @Test
  void a_tester_naming_a_payment_this_test_run_does_not_hold_is_told_there_is_no_such_object() {
    aRunConfiguringTheTerminal();
    AzericardPayment payment = aHoldFor(OVER_THE_WIRE);

    assertThat(
            provider.act(
                ObjectActionRequest.of(
                    ANOTHER_RUN, payment.intRef(), ObjectAction.CAPTURE, HELD, ObjectOrigin.UI)))
        .isEqualTo(new ObjectActionOutcome.NoSuchObject());
  }

  @Test
  void a_tester_who_asks_to_capture_a_stored_card_is_told_a_saved_token_is_not_a_transaction() {
    aRunConfiguringTheTerminal();
    cards.holding(
        AzericardStoredCard.seededBy(
            RUN, SEEDED_TOKEN, TERMINAL_ID, MERCHANT, "fixture-1", ObjectOrigin.CONTROL_API, NOW));

    ObjectActionOutcome refused = asked(SEEDED_TOKEN, ObjectAction.CAPTURE, HELD);

    assertThat(provider.state(RUN, SEEDED_TOKEN)).isPresent();
    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                AzericardObjectActions.A_STORED_CARD_IS_NOT_A_TRANSACTION));
  }

  @Test
  void an_action_on_a_payment_whose_terminal_this_run_dropped_is_refused_in_azericards_words() {
    aRunConfiguringTheTerminal();
    AzericardPayment payment = aHoldFor(OVER_THE_WIRE);
    accounts.remove(RUN, MERCHANT);

    ObjectActionOutcome refused = asked(payment.intRef(), ObjectAction.CAPTURE, HELD);

    assertThat(refused)
        .isInstanceOf(ObjectActionOutcome.RefusedByTheProvider.class)
        .extracting(outcome -> ((ObjectActionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .asString()
        .startsWith(AzericardErrorPage.messageFor(AzericardFields.REQUIRED_TERMINAL))
        .contains(TERMINAL_ID);
    assertThat(thePaymentFor(OVER_THE_WIRE).normalizedStatus())
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_completion_a_scenario_answered_instead_of_running_moves_no_money_and_names_the_rule() {
    aRunConfiguringTheTerminal();
    AzericardPayment payment = aHoldFor(OVER_THE_WIRE);
    ScenarioRule answering =
        scenarioRules.add(
            new NewScenarioRule(
                AzericardGateway.AZERICARD,
                RUN,
                0,
                ScenarioMatch.anyRequest()
                    .naming(AzericardTransactionType.SALES_COMPLETION.operationName()),
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                null));

    ObjectActionOutcome outcome = asked(payment.intRef(), ObjectAction.CAPTURE, HELD);

    assertThat(outcome)
        .isEqualTo(new ObjectActionOutcome.AnsweredByAScenarioInstead(answering.scenarioId()));
    assertThat(thePaymentFor(OVER_THE_WIRE).normalizedStatus())
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  private AzericardMessage theSettlementBuiltFor(
      AzericardPayment payment, ObjectAction action, @Nullable String amount) {
    return AzericardObjectActions.messageOf(
        ObjectActionRequest.of(RUN, payment.intRef(), action, amount, ObjectOrigin.UI),
        payment,
        new AzericardDoubles.CountingIds(),
        NOW);
  }

  private ObjectActionOutcome asked(
      String providerObjectId, ObjectAction action, @Nullable String amount) {
    return provider.act(
        ObjectActionRequest.of(RUN, providerObjectId, action, amount, ObjectOrigin.UI));
  }

  private ScenarioRule decline(AzericardTransactionType type, String providerCode) {
    return scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(type.operationName()),
            new ScenarioAction.TerminalFailure(providerCode),
            null));
  }

  private void aRunConfiguringTheTerminal() {
    accounts.with(AzericardDoubles.terminal(RUN, MERCHANT, TERMINAL_ID));
    testRuns.register(RUN);
  }

  private AzericardPayment aHoldFor(String order) {
    gateway.cgiLink(call(signed(authorization(order, HELD))));
    return thePaymentFor(order);
  }

  private AzericardPayment thePaymentFor(String order) {
    List<AzericardPayment> held = payments.byOrder(RUN, TERMINAL_ID, order);
    assertThat(held).hasSize(1);
    return held.get(0);
  }

  private static String bodyOf(AzericardApiResponse response) {
    return new String(response.body(), UTF_8);
  }

  private static Map<String, String> authorization(String order, String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "0");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.AMOUNT, amount);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.MERCH_URL, "https://merchant.example");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> settlementOf(
      AzericardPayment payment, String trtype, String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, payment.terminalId());
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, payment.order());
    form.put(AzericardFields.AMOUNT, amount);
    form.put(AzericardFields.CURRENCY, payment.currency());
    form.put(AzericardFields.RRN, payment.rrn());
    form.put(AzericardFields.INT_REF, payment.intRef());
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(signed)).macSource()));
    return signed;
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static AzericardApiCall call(Map<String, String> form) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Content-Type", List.of("application/x-www-form-urlencoded"));
    return new AzericardApiCall("POST", ROUTE, headers, fieldsOf(form));
  }
}
