package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrowSettleSuspendedTransactionTest {

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String NOTIFY_URL = "https://merchant.example/callbacks/grow";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";

  private static final String HELD = "120.50";

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final MovableClock clock = new MovableClock(NOW);

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          clock,
          ids,
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final GrowLightApi api =
      new GrowLightApi(
          accounts,
          testRuns,
          audit,
          clock,
          ids,
          DEFAULT_RUN,
          new CreatePaymentProcess(
              processes, ids, "https://simulator.internal", scenarioEngine, GrowScalarType.INTEGER),
          new CreateTransactionWithToken(
              processes, transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
          new ApproveTransaction(processes, dispatcher, scenarioEngine, GrowScalarType.INTEGER),
          new SettleSuspendedTransaction(transactions, scenarioEngine, GrowScalarType.INTEGER),
          new RefundTransaction(transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
          new GetTransactionInfo(transactions, processes, scenarioEngine, GrowScalarType.INTEGER),
          new GetPaymentProcessInfo(
              processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
          new GetTokenTransactionsByExternalIdentifiers(
              processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
          new CancelBitTransaction(GrowScalarType.INTEGER),
          new UpdateMyUrl(dispatcher, GrowScalarType.INTEGER));

  private final SimulatorProvider provider =
      new GrowProvider(
          accounts,
          audit,
          processes,
          transactions,
          dispatcher,
          SimulatorDoubles.callbacks(processes, transactions, accounts, dispatcher, clock, ids),
          clock,
          new InMemoryPaymentMethodFixtures(),
          scenarioRules,
          SimulatorDoubles.heldResponses(),
          new SettleSuspendedTransaction(
              transactions, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          new RefundTransaction(
              transactions, ids, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          SimulatorDoubles.paymentProcessCreation(processes, scenarioRules, ids),
          SimulatorDoubles.storedCardCharge(processes, transactions, ids, scenarioRules),
          SimulatorDoubles.retraceOfAStoredCardCharge(processes, transactions, scenarioRules),
          SimulatorDoubles.completions(processes));

  @Test
  void settles_a_hold_in_full_and_answers_with_the_transaction_record_as_paid() {
    GrowTransaction hold = aHold();

    GrowApiResponse response = settle(hold, HELD);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(hold.transactionId());
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":1,\"err\":\"\",\"data\":{"
                + "\"asmachta\":\""
                + hold.asmachta()
                + "\",\"cardSuffix\":\"4580\",\"cardType\":\"Foreign\",\"cardTypeCode\":\"2\","
                + "\"cardBrand\":\"Visa\",\"cardBrandCode\":\"3\",\"cardExp\":\"0424\","
                + "\"firstPaymentSum\":\"0\",\"periodicalPaymentSum\":\"0\","
                + "\"status\":\"שולם\",\"statusCode\":\"2\","
                + "\"transactionTypeId\":\"1\",\"paymentType\":\"2\",\"sum\":\"120.50\","
                + "\"paymentsNum\":\"0\",\"allPaymentsNum\":\"1\",\"paymentDate\":\"28/11/26\","
                + "\"description\":\"A hold\",\"fullName\":\"Israel Israeli\","
                + "\"payerPhone\":\"0505503144\",\"payerEmail\":\"payer@merchant.example\","
                + "\"transactionId\":\""
                + hold.transactionId()
                + "\",\"transactionToken\":\""
                + hold.transactionToken()
                + "\"}}");
  }

  @Test
  void a_settled_transaction_is_no_longer_suspended() {
    GrowTransaction hold = aHold();
    assertThat(stateOf(hold).nativeStatus()).isEqualTo("11");

    clock.moveTo(NOW.plusSeconds(30));
    settle(hold, HELD);

    ProviderObjectState settled = stateOf(hold);
    assertThat(settled.nativeStatus()).isEqualTo("2");
    assertThat(settled.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(settled.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION, OperationKind.CAPTURE);
    assertThat(settled.ledger().operations())
        .extracting(MonetaryOperation::sequence)
        .containsExactly(1, 2);
    assertThat(balancesOf(settled).capturedAmount().value()).isEqualTo(HELD);
    assertThat(balancesOf(settled).capturableRemainder().value()).isEqualTo("0");
    assertThat(settled.updatedAt()).isEqualTo(NOW.plusSeconds(30));
  }

  @Test
  void settles_part_of_a_hold_and_leaves_the_remainder_visible_and_not_capturable() {
    GrowTransaction hold = aHold();

    assertThat(bodyOf(settle(hold, "40.00"))).contains("\"sum\":\"40.00\"");

    ProviderObjectState settled = stateOf(hold);
    assertThat(settled.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(settled.amount().value()).isEqualTo(HELD);
    assertThat(settled.ledger().operations())
        .extracting(operation -> operation.amount().value())
        .containsExactly(HELD, "40.00");
    assertThat(balancesOf(settled).capturedAmount().value()).isEqualTo("40.00");
    assertThat(balancesOf(settled).capturableRemainder().value()).isEqualTo("0");
  }

  @Test
  void refuses_to_settle_the_remainder_of_a_partly_settled_hold_with_808() {
    GrowTransaction hold = aHold();
    settle(hold, "40.00");

    GrowApiResponse again = settle(hold, "80.50");

    assertThat(again.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(again))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":808,"
                + "\"message\":\"לא ניתן לבצע שחרור לעסקה ששוחררה\"},\"data\":\"\"}");
    assertThat(stateOf(hold).ledger().operations()).hasSize(2);
  }

  @Test
  void keeps_the_exact_characters_of_the_amount_it_was_settled_for() {
    GrowTransaction hold = aHold();

    assertThat(bodyOf(settle(hold, "40.0"))).contains("\"sum\":\"40.0\"");

    assertThat(stateOf(hold).ledger().operations().get(1).amount().value()).isEqualTo("40.0");
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo("40.0");
  }

  @Test
  void refuses_a_settlement_above_the_held_amount_with_804() {
    GrowTransaction hold = aHold();

    assertThat(bodyOf(settle(hold, "120.51")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":804,"
                + "\"message\":\"לא ניתן לשחרר עסקה בסכום הגבוה מתפיסת המסגרת\"},\"data\":\"\"}");
    assertThat(stateOf(hold).ledger().operations()).hasSize(1);
  }

  @Test
  void admits_a_settlement_of_the_held_amount_written_with_another_scale() {
    GrowTransaction hold = aHold();

    assertThat(bodyOf(settle(hold, "120.5000"))).contains("\"statusCode\":\"2\"");
  }

  @Test
  void an_account_configured_for_the_other_published_ceiling_admits_thirty_percent_more() {
    GrowTransaction hold =
        aHold(
            Map.of(
                GrowSettlementCeiling.SETTING, GrowSettlementCeiling.THIRTY_PERCENT_ABOVE.name()));

    assertThat(bodyOf(settle(hold, "156.65"))).contains("\"sum\":\"156.65\"");
  }

  @Test
  void refuses_a_settlement_above_the_thirty_percent_ceiling_with_814() {
    GrowTransaction hold =
        aHold(
            Map.of(
                GrowSettlementCeiling.SETTING, GrowSettlementCeiling.THIRTY_PERCENT_ABOVE.name()));

    assertThat(bodyOf(settle(hold, "156.66")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":814,\"message\":\""
                + "הסכום המירבי לביצוע החיוב הוא עד 30% יותר מסכום העסקה."
                + "\"},\"data\":\"\"}");
  }

  @Test
  void refuses_a_settlement_after_the_capture_window_with_803() {
    GrowTransaction hold = aHold();

    clock.moveTo(NOW.plus(Duration.ofDays(7)));

    assertThat(bodyOf(settle(hold, HELD)))
        .isEqualTo("{\"status\":0,\"err\":{\"id\":803,\"message\":\"J5 פג תוקף\"},\"data\":\"\"}");
    assertThat(stateOf(hold).ledger().operations()).hasSize(1);
  }

  @Test
  void settles_on_the_last_day_of_the_capture_window() {
    GrowTransaction hold = aHold();

    clock.moveTo(NOW.plus(Duration.ofDays(7)).minusMillis(1));

    assertThat(bodyOf(settle(hold, HELD))).contains("\"statusCode\":\"2\"");
  }

  @Test
  void a_hold_nobody_settled_is_cancelled_when_its_automatic_release_passes() {
    GrowTransaction hold = aHold();

    clock.moveTo(NOW.plus(Duration.ofDays(10)));

    ProviderObjectState voided = stateOf(hold);
    assertThat(voided.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(voided.nativeStatus()).isEqualTo("11");
    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":803");
  }

  @Test
  void a_hold_past_its_capture_window_is_still_held_until_its_automatic_release() {
    GrowTransaction hold = aHold();

    clock.moveTo(NOW.plus(Duration.ofDays(8)));

    assertThat(stateOf(hold).normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(balancesOf(stateOf(hold)).capturableRemainder().value()).isEqualTo(HELD);
    assertThat(stateOf(hold).authorizationOpen()).isTrue();
    assertThat(stateOf(hold).capturesAccepted()).isFalse();
    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":803");
  }

  @Test
  void the_instant_before_its_capture_window_passes_a_hold_still_accepts_a_capture() {
    GrowTransaction hold = aHold();

    clock.moveTo(NOW.plus(Duration.ofDays(7)).minusMillis(1));

    ProviderObjectState held = stateOf(hold);
    assertThat(held.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(balancesOf(held).capturableRemainder().value()).isEqualTo(HELD);
    assertThat(held.authorizationOpen()).isTrue();
    assertThat(held.capturesAccepted()).isTrue();
    assertThat(bodyOf(settle(hold, HELD))).contains("\"statusCode\":\"2\"");
  }

  @Test
  void the_instant_its_capture_window_passes_a_hold_accepts_no_capture_and_still_reports_one() {
    GrowTransaction hold = aHold();

    clock.moveTo(NOW.plus(Duration.ofDays(7)));

    ProviderObjectState held = stateOf(hold);
    assertThat(held.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(balancesOf(held).capturableRemainder().value()).isEqualTo(HELD);
    assertThat(held.authorizationOpen()).isTrue();
    assertThat(held.capturesAccepted()).isFalse();
    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":803");
  }

  @Test
  void the_instant_its_automatic_release_passes_a_hold_reports_neither_a_remainder_nor_a_capture() {
    GrowTransaction hold = aHold();

    clock.moveTo(NOW.plus(Duration.ofDays(10)));

    ProviderObjectState voided = stateOf(hold);
    assertThat(voided.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(balancesOf(voided).capturableRemainder().value()).isEqualTo("0");
    assertThat(voided.authorizationOpen()).isFalse();
    assertThat(voided.capturesAccepted()).isFalse();
    assertThat(voided.refundsAccepted())
        .as("grow released a hold it never charged, so there is nothing of it left to return")
        .isFalse();
  }

  @Test
  void a_settled_hold_inside_its_window_accepts_no_further_capture() {
    GrowTransaction hold = aHold();
    assertThat(bodyOf(settle(hold, HELD))).contains("\"statusCode\":\"2\"");

    ProviderObjectState settled = stateOf(hold);
    assertThat(balancesOf(settled).capturableRemainder().value()).isEqualTo("0");
    assertThat(settled.capturesAccepted()).isFalse();
  }

  @Test
  void a_transaction_that_holds_no_authorization_accepts_no_capture_inside_the_window() {
    GrowTransaction saved = aSavedCard();

    ProviderObjectState state = stateOf(saved);
    assertThat(state.ledger().operations()).isEmpty();
    assertThat(state.authorizationOpen()).isTrue();
    assertThat(state.capturesAccepted()).isFalse();
  }

  @Test
  void refuses_a_settlement_after_the_automatic_release_even_inside_an_open_capture_window() {
    GrowTransaction hold = aHold(Map.of(GrowHoldWindow.CAPTURE_WINDOW, "P20D"));

    clock.moveTo(NOW.plus(Duration.ofDays(10)));

    assertThat(stateOf(hold).normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(bodyOf(settle(hold, HELD)))
        .isEqualTo("{\"status\":0,\"err\":{\"id\":803,\"message\":\"J5 פג תוקף\"},\"data\":\"\"}");
    assertThat(stateOf(hold).ledger().operations()).hasSize(1);
    assertThat(stateOf(hold).normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_hold_that_has_been_automatically_released_has_nothing_left_to_capture() {
    GrowTransaction hold = aHold();
    assertThat(balancesOf(stateOf(hold)).capturableRemainder().value()).isEqualTo(HELD);

    clock.moveTo(NOW.plus(Duration.ofDays(10)));

    ProviderObjectState voided = stateOf(hold);
    assertThat(voided.normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(balancesOf(voided).capturableRemainder().value()).isEqualTo("0");
    assertThat(balancesOf(voided).capturedAmount().value()).isEqualTo("0");
    assertThat(voided.ledger().operations())
        .singleElement()
        .satisfies(held -> assertThat(held.amount().value()).isEqualTo(HELD));
  }

  @Test
  void moving_the_clock_back_across_the_automatic_release_makes_a_hold_capturable_again() {
    GrowTransaction hold = aHold();
    clock.moveTo(NOW.plus(Duration.ofDays(10)));
    assertThat(stateOf(hold).normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":803");

    clock.moveTo(NOW.plus(Duration.ofDays(1)));

    assertThat(stateOf(hold).normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(balancesOf(stateOf(hold)).capturableRemainder().value()).isEqualTo(HELD);
    assertThat(bodyOf(settle(hold, HELD))).contains("\"statusCode\":\"2\"");
  }

  @Test
  void shortening_the_capture_window_refuses_a_settlement_the_earlier_window_admitted() {
    GrowTransaction settledUnderTheOldWindow = aHold();
    GrowTransaction settledUnderTheNewOne = anotherHold();
    clock.moveTo(NOW.plus(Duration.ofDays(5)));
    assertThat(bodyOf(settle(settledUnderTheOldWindow, HELD))).contains("\"statusCode\":\"2\"");

    reconfigure(Map.of(GrowHoldWindow.CAPTURE_WINDOW, "PT72H"));

    assertThat(bodyOf(settle(settledUnderTheNewOne, HELD))).contains("\"id\":803");
    assertThat(stateOf(settledUnderTheNewOne).ledger().operations()).hasSize(1);
  }

  @Test
  void the_capture_window_is_account_configuration_and_the_other_published_figure_is_reachable() {
    GrowTransaction hold = aHold(Map.of(GrowHoldWindow.CAPTURE_WINDOW, "PT72H"));

    clock.moveTo(NOW.plus(Duration.ofHours(73)));

    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":803");
  }

  @Test
  void the_automatic_release_is_account_configuration_too() {
    GrowTransaction hold =
        aHold(
            Map.of(
                GrowHoldWindow.CAPTURE_WINDOW,
                "PT72H",
                GrowHoldWindow.AUTOMATIC_VOID_AFTER,
                "PT100H"));

    clock.moveTo(NOW.plus(Duration.ofHours(100)));

    assertThat(stateOf(hold).normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_hold_whose_account_this_run_configures_none_of_falls_back_on_the_window() {
    GrowTransaction hold = aHold(Map.of(GrowHoldWindow.AUTOMATIC_VOID_AFTER, "PT100H"));
    accounts.forget(RUN);

    clock.moveTo(NOW.plus(Duration.ofHours(100)));

    assertThat(stateOf(hold).normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void refuses_a_second_settlement_of_one_hold_with_808() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":808");
  }

  @Test
  void refuses_to_settle_a_saved_card_that_holds_nothing_with_810() {
    GrowTransaction registration = aSavedCard();

    assertThat(bodyOf(settle(registration, "1")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":810,"
                + "\"message\":\"לא ניתן לבצע שחרור לסוג עסקה זה\"},\"data\":\"\"}");
    assertThat(stateOf(registration).ledger().operations()).isEmpty();
    assertThat(stateOf(registration).normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void answers_813_for_a_transaction_it_holds_no_record_of() {
    aHold();
    Map<String, String> settlement = new LinkedHashMap<>();
    settlement.put(GrowFields.USER_ID, USER_ID);
    settlement.put(GrowFields.TRANSACTION_ID, "999999");
    settlement.put(GrowFields.TRANSACTION_TOKEN, "a1b2c3d4e5f6a7b8c9");
    settlement.put(GrowFields.SUM, HELD);

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":813,"
                + "\"message\":\"לא ניתן לבצע חיוב לעסקה שלא בוצעה\"},\"data\":\"\"}");
  }

  @Test
  void answers_813_when_the_token_does_not_match_the_transaction_it_names() {
    GrowTransaction hold = aHold();
    Map<String, String> settlement = settlementFor(hold, HELD);
    settlement.put(GrowFields.TRANSACTION_TOKEN, "a1b2c3d4e5f6a7b8c9");

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .contains("\"id\":813");
    assertThat(stateOf(hold).ledger().operations()).hasSize(1);
  }

  @Test
  void answers_813_for_a_transaction_that_belongs_to_another_business() {
    GrowTransaction hold = aHold();
    accounts.with(SimulatorDoubles.account(RUN, "merchant-2", "other-user", "other-page"));
    Map<String, String> settlement = settlementFor(hold, HELD);
    settlement.put(GrowFields.USER_ID, "other-user");

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .contains("\"id\":813");
  }

  @Test
  void answers_743_for_a_userId_no_account_is_configured_for() {
    GrowTransaction hold = aHold();
    Map<String, String> settlement = settlementFor(hold, HELD);
    settlement.put(GrowFields.USER_ID, "not-a-configured-user");

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":743,"
                + "\"message\":\"אין עסק תואם ל userId שנשלח\"},\"data\":\"\"}");
    assertThat(stateOf(hold).ledger().operations()).hasSize(1);
  }

  @Test
  void answers_743_for_a_userId_two_accounts_of_one_test_run_answer_to() {
    GrowTransaction hold = aHold();
    accounts.with(SimulatorDoubles.account(RUN, "merchant-2", USER_ID, "another-page"));

    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":743");
    assertThat(stateOf(hold).ledger().operations()).hasSize(1);
  }

  @Test
  void checks_the_credential_before_it_looks_for_the_transaction() {
    aHold();
    Map<String, String> settlement = new LinkedHashMap<>();
    settlement.put(GrowFields.USER_ID, "not-a-configured-user");
    settlement.put(GrowFields.TRANSACTION_ID, "999999");
    settlement.put(GrowFields.TRANSACTION_TOKEN, "a1b2c3d4e5f6a7b8c9");
    settlement.put(GrowFields.SUM, HELD);

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .contains("\"id\":743");
  }

  @Test
  void authenticates_on_the_userId_alone_and_ignores_a_pageCode_it_has_no_field_for() {
    GrowTransaction hold = aHold();
    Map<String, String> settlement = settlementFor(hold, HELD);
    settlement.put(GrowFields.PAGE_CODE, "not-this-businesss-page-code");

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .contains("\"statusCode\":\"2\"");
  }

  @ParameterizedTest(name = "answers 54 naming the missing {0}")
  @ValueSource(strings = {"userId", "transactionId", "transactionToken"})
  void answers_54_naming_the_field_a_settlement_left_out(String missing) {
    GrowTransaction hold = aHold();
    Map<String, String> settlement = settlementFor(hold, HELD);
    settlement.remove(missing);

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:"
                + missing
                + "\"},\"data\":\"\"}");
  }

  @ParameterizedTest(name = "refuses a settlement of {0}")
  @ValueSource(strings = {"0", "-1", "0.00", "not-a-number"})
  void refuses_a_settlement_that_is_not_a_positive_amount_with_707(String sum) {
    GrowTransaction hold = aHold();

    assertThat(bodyOf(settle(hold, sum)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":707,\"message\":\"הסכום אינו תקין\"},\"data\":\"\"}");
    assertThat(stateOf(hold).ledger().operations()).hasSize(1);
  }

  @Test
  void refuses_a_settlement_that_names_no_amount_before_it_reads_the_credential() {
    GrowTransaction hold = aHold();
    Map<String, String> settlement = settlementFor(hold, HELD);
    settlement.remove(GrowFields.SUM);
    settlement.put(GrowFields.USER_ID, "not-a-configured-user");

    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlement))))
        .contains("\"id\":707")
        .doesNotContain("\"id\":743");
  }

  @Test
  void a_hold_whose_amount_grow_never_validated_settles_nowhere_and_derives_no_balances() {
    GrowTransaction hold = anAmountNoLongerReachableThroughTheApi(aHold());

    assertThat(bodyOf(settle(hold, "1"))).contains("\"id\":804");

    ProviderObjectState state = stateOf(hold);
    assertThat(state.ledger().operations()).hasSize(1);
    assertThat(state.derivedBalances()).isEmpty();
  }

  @Test
  void two_settlements_of_one_hold_apply_once() {
    GrowTransaction hold = aHold();
    List<GrowApiResponse> interleaved = new ArrayList<>();
    transactions.whileAppending(() -> interleaved.add(settle(hold, "40.00")));

    GrowApiResponse first = settle(hold, HELD);

    assertThat(interleaved).hasSize(1);
    assertThat(bodyOf(interleaved.get(0))).contains("\"statusCode\":\"2\"");
    assertThat(bodyOf(first)).contains("\"id\":808");
    ProviderObjectState settled = stateOf(hold);
    assertThat(settled.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION, OperationKind.CAPTURE);
    assertThat(balancesOf(settled).capturedAmount().value()).isEqualTo("40.00");
  }

  @Test
  void a_reset_landing_inside_a_settlement_is_answered_813_in_grows_own_envelope() {
    GrowTransaction hold = aHold();
    transactions.whileAppending(() -> transactions.clear(RUN));

    GrowApiResponse response = settle(hold, HELD);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":813,"
                + "\"message\":\"לא ניתן לבצע חיוב לעסקה שלא בוצעה\"},\"data\":\"\"}");
    assertThat(provider.state(RUN, hold.transactionId())).isEmpty();
  }

  @Test
  void reports_a_hold_as_one_authorization_with_the_amount_still_capturable() {
    GrowTransaction hold = aHold();

    ProviderObjectState state = stateOf(hold);

    assertThat(state.providerObjectId()).isEqualTo(hold.transactionId());
    assertThat(state.nativeStatus()).isEqualTo("11");
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(state.amount().value()).isEqualTo(HELD);
    assertThat(state.currency()).isNull();
    assertThat(state.ledger().operations())
        .singleElement()
        .satisfies(
            authorization -> {
              assertThat(authorization.sequence()).isEqualTo(1);
              assertThat(authorization.kind()).isEqualTo(OperationKind.AUTHORIZATION);
              assertThat(authorization.amount().value()).isEqualTo(HELD);
              assertThat(authorization.closesAuthorization()).isFalse();
              assertThat(authorization.nativeStatus()).isEqualTo("11");
              assertThat(authorization.normalizedStatus())
                  .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
              assertThat(authorization.at()).isEqualTo(NOW);
            });
    assertThat(balancesOf(state).capturedAmount().value()).isEqualTo("0");
    assertThat(balancesOf(state).capturableRemainder().value()).isEqualTo(HELD);
    assertThat(state.relatedObjects())
        .containsOnly(Map.entry("processId", hold.processId()), Map.entry("merchantId", MERCHANT));
    assertThat(state.relatedObjects().values()).doesNotContain(hold.transactionToken());
  }

  @Test
  void reports_nothing_for_an_id_it_holds_no_transaction_for() {
    aHold();

    assertThat(provider.state(RUN, "999999")).isEmpty();
  }

  @Test
  void audits_the_settlement_as_an_inbound_request_and_an_outbound_response() {
    GrowTransaction hold = aHold();
    clock.moveTo(NOW.plusSeconds(30));

    settle(hold, HELD);

    List<AuditRecord> ofTheSettlement =
        audit.all().stream().filter(record -> SETTLE.equals(record.path())).toList();
    assertThat(ofTheSettlement)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    AuditRecord inbound = ofTheSettlement.get(0);
    assertThat(inbound.createdAt()).isEqualTo(NOW.plusSeconds(30));
    assertThat(inbound.sanitizedBody())
        .containsEntry(GrowFields.USER_ID, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.TRANSACTION_TOKEN, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.SUM, List.of(HELD));
    AuditRecord outbound = ofTheSettlement.get(1);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.httpStatus()).isEqualTo(200);
    assertThat(outbound.providerObjectId()).isEqualTo(hold.transactionId());
    assertThat(outbound.sanitizedBody())
        .containsEntry("data.transactionToken", List.of(Redaction.REDACTED))
        .containsEntry("data.transactionId", List.of(hold.transactionId()));
  }

  @Test
  void a_drawn_transaction_id_that_is_already_taken_is_a_simulator_fault() {
    configure(Map.of());
    String processId = createTheProcess(aHoldRequest(HELD));
    transactions.rejectingTheNext(1);

    WebhookScheduleRequest asking =
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> provider.schedule(asking))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transactionId");
    assertThat(processes.findByProcessId(RUN, processId).orElseThrow().outcome()).isNull();
  }

  @Test
  void a_matched_scenario_declines_the_settlement_and_leaves_the_ledger_untouched() {
    GrowTransaction hold = aHold();
    ScenarioRule rule = declineEverySettlement("814");

    GrowApiResponse response = settle(hold, HELD);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo("{\"status\":0,\"err\":{\"id\":814,\"message\":\"\"},\"data\":\"\"}");
    ProviderObjectState held = stateOf(hold);
    assertThat(held.nativeStatus()).isEqualTo("11");
    assertThat(held.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION);
    assertThat(balancesOf(held).capturedAmount().value()).isEqualTo("0");
    assertThat(audit.all().get(audit.all().size() - 1).scenarioId()).isEqualTo(rule.scenarioId());
  }

  @Test
  void a_scenario_never_stands_in_for_the_guards_on_a_released_hold() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    declineEverySettlement("814");

    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":808");
  }

  @Test
  void a_story_declines_one_settlement_and_releases_the_hold_on_the_next() {
    GrowTransaction hold = aHold();
    List<ScenarioRule> story = declineOneSettlementOf(hold.transactionId());

    GrowApiResponse declined = settle(hold, HELD);
    GrowApiResponse released = settle(hold, HELD);

    assertThat(declined.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(released.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo(HELD);
    assertThat(audit.all().stream().map(AuditRecord::scenarioId).filter(Objects::nonNull).toList())
        .containsExactly(story.get(0).scenarioId(), story.get(1).scenarioId());
  }

  @Test
  void a_story_naming_a_hold_is_not_moved_by_the_call_that_creates_one() {
    GrowTransaction hold = aHold();
    declineOneSettlementOf(hold.transactionId());

    createTheProcess(aHoldRequest(HELD));

    assertThat(settle(hold, HELD).outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(settle(hold, HELD).outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void a_scenario_naming_a_hold_declines_that_hold_and_leaves_every_other_one_alone() {
    GrowTransaction hold = aHold();
    GrowTransaction other = anotherHold();
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            matchingObject(other.transactionId()),
            new ScenarioAction.TerminalFailure("814"),
            null));

    assertThat(settle(hold, HELD).outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(settle(other, HELD))).contains("\"id\":814");
  }

  @Test
  void a_failure_the_scenario_did_not_choose_carries_no_scenario_id_into_audit() {
    GrowTransaction hold = aHold();
    declineOneSettlementOf(hold.transactionId());
    settle(hold, HELD);
    transactions.whileAppending(() -> transactions.clear(RUN));

    GrowApiResponse gone = settle(hold, HELD);

    assertThat(gone.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(gone)).contains("\"id\":813");
    assertThat(audit.all().get(audit.all().size() - 1).scenarioId()).isNull();
  }

  @Test
  void a_scenario_naming_an_amount_declines_only_the_settlement_that_asks_for_it() {
    GrowTransaction hold = aHold();
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(
                null, null, null, null, null, Amount.of("100.00"), null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("814"),
            null));

    assertThat(settle(hold, "100.00").outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(settle(hold, HELD).outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  private static ScenarioMatch matchingObject(String providerObjectId) {
    return new ScenarioMatch(
        null, null, providerObjectId, null, null, null, null, null, null, Map.of());
  }

  private List<ScenarioRule> declineOneSettlementOf(String providerObjectId) {
    return scenarioRules.addAll(
        List.of(
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                matchingObject(providerObjectId),
                new ScenarioAction.TerminalFailure("814"),
                new ScenarioStory(
                    "settlement-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")),
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                matchingObject(providerObjectId),
                new ScenarioAction.FixedSuccess(),
                new ScenarioStory("settlement-retries", StoryKeying.FLOW, "asked", null))));
  }

  private ScenarioRule declineEverySettlement(String providerCode) {
    return scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure(providerCode),
            null));
  }

  private GrowTransaction aHold() {
    return aHold(Map.of());
  }

  private GrowTransaction aHold(Map<String, String> settings) {
    return aHold(settings, HELD);
  }

  private GrowTransaction aHold(Map<String, String> settings, String sum) {
    return theTransactionOf(settings, aHoldRequest(sum));
  }

  private GrowTransaction aSavedCard() {
    Map<String, String> request = aHoldRequest("1");
    request.remove(GrowFields.PAYMENT_INTENT_FIELD);
    request.put(GrowFields.CHARGE_TYPE, GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    return theTransactionOf(Map.of(), request);
  }

  private GrowTransaction anotherHold() {
    String processId = createTheProcess(aHoldRequest(HELD));
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    return transactions.all().get(transactions.all().size() - 1);
  }

  private GrowTransaction theTransactionOf(
      Map<String, String> settings, Map<String, String> request) {
    configure(settings);
    String processId = createTheProcess(request);
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    assertThat(transactions.all()).hasSize(1);
    return transactions.all().get(0);
  }

  private GrowTransaction anAmountNoLongerReachableThroughTheApi(GrowTransaction held) {
    GrowTransaction unvalidated =
        new GrowTransaction(
            held.testRunId(),
            held.kind(),
            "500099",
            held.transactionToken(),
            held.merchantId(),
            held.processId(),
            held.refundedTransactionId(),
            held.asmachta(),
            held.paymentDate(),
            held.description(),
            held.payerFullName(),
            held.payerPhone(),
            held.payerEmail(),
            held.card(),
            Amount.of("twelve"),
            anAuthorizationOf(held, Amount.of("twelve")),
            held.origin(),
            held.createdAt(),
            held.updatedAt());
    transactions.holding(unvalidated);
    return unvalidated;
  }

  private static Ledger anAuthorizationOf(GrowTransaction held, Amount amount) {
    MonetaryOperation authorized = held.ledger().operations().get(0);
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                authorized.sequence(),
                authorized.kind(),
                amount,
                authorized.closesAuthorization(),
                authorized.createdObjectId(),
                authorized.nativeStatus(),
                authorized.normalizedStatus(),
                authorized.at()));
  }

  private String createTheProcess(Map<String, String> request) {
    GrowApiResponse created = api.createPaymentProcess(call(CREATE, request));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    return String.valueOf(created.providerObjectId());
  }

  private void configure(Map<String, String> settings) {
    accounts.with(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE, settings));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, NOTIFY_URL));
    testRuns.register(RUN);
  }

  private void reconfigure(Map<String, String> settings) {
    accounts.merge(
        RUN, List.of(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE, settings)));
  }

  private GrowApiResponse settle(GrowTransaction transaction, String sum) {
    return api.settleSuspendedTransaction(call(SETTLE, settlementFor(transaction, sum)));
  }

  private static Map<String, String> settlementFor(GrowTransaction transaction, String sum) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, USER_ID);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, transaction.transactionToken());
    fields.put(GrowFields.SUM, sum);
    return fields;
  }

  private static Map<String, String> aHoldRequest(String sum) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, "2");
    form.put(GrowFields.SUM, sum);
    form.put(GrowFields.SAVE_CARD_TOKEN, "1");
    form.put(GrowFields.DESCRIPTION, "A hold");
    form.put(GrowFields.SUCCESS_URL, "https://merchant.example/thanks");
    form.put(GrowFields.CANCEL_URL, "https://merchant.example/cancel");
    form.put(GrowFields.NOTIFY_URL, NOTIFY_URL);
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    form.put(GrowFields.EMAIL, "payer@merchant.example");
    form.put("cField1", "merchant-session-secret");
    form.put(GrowFields.PAYMENT_INTENT_FIELD, "intent-99");
    return form;
  }

  private ProviderObjectState stateOf(GrowTransaction transaction) {
    return provider.state(RUN, transaction.transactionId()).orElseThrow();
  }

  private static DerivedBalances balancesOf(ProviderObjectState state) {
    return state.derivedBalances().orElseThrow();
  }

  private static GrowApiCall call(String path, Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return new GrowApiCall(
        "POST", path, Map.of("Content-Type", List.of("multipart/form-data; boundary=x")), fields);
  }

  private static String bodyOf(GrowApiResponse response) {
    return GrowWireText.readable(new String(response.body(), UTF_8));
  }

  private static final class MovableClock implements SimulatorClock {

    private Instant now;

    private MovableClock(Instant now) {
      this.now = now;
    }

    void moveTo(Instant later) {
      this.now = later;
    }

    @Override
    public Instant now(TestRunId testRunId) {
      return now;
    }
  }
}
