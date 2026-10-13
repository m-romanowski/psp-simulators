package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrowRefundTransactionTest {

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final Duration A_DAY = Duration.ofDays(1);

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String NOTIFY_URL = "https://merchant.example/callbacks/grow";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";

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
  void releases_a_hold_by_settling_it_in_full_and_refunding_it_and_ends_refunded() {
    GrowTransaction hold = aHold();

    ProviderObjectState held = stateOf(hold.transactionId());
    assertThat(held.nativeStatus()).isEqualTo("11");
    assertThat(held.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(balancesOf(held)).isEqualTo(balances("0", "0", HELD, "0"));

    assertThat(bodyOf(settle(hold, HELD))).contains("\"statusCode\":\"2\"");

    ProviderObjectState settled = stateOf(hold.transactionId());
    assertThat(settled.nativeStatus()).isEqualTo("2");
    assertThat(settled.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(balancesOf(settled)).isEqualTo(balances(HELD, "0", "0", HELD));

    GrowApiResponse released = refund(hold, HELD);
    assertThat(released.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    String refundId = String.valueOf(released.providerObjectId());

    ProviderObjectState refunded = stateOf(hold.transactionId());
    assertThat(refunded.nativeStatus()).isEqualTo("2");
    assertThat(refunded.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(refunded.normalizedStatus()).isNotEqualTo(NormalizedStatus.CANCELLED);
    assertThat(balancesOf(refunded)).isEqualTo(balances(HELD, HELD, "0", "0.00"));
    assertThat(refunded.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION, OperationKind.CAPTURE, OperationKind.REFUND);
    assertThat(refunded.ledger().operations().get(2))
        .satisfies(
            operation -> {
              assertThat(operation.sequence()).isEqualTo(3);
              assertThat(operation.amount().value()).isEqualTo(HELD);
              assertThat(operation.closesAuthorization()).isNull();
              assertThat(operation.createdObjectId()).isEqualTo(refundId);
              assertThat(operation.nativeStatus()).isEqualTo("3");
              assertThat(operation.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
            });

    ProviderObjectState row = stateOf(refundId);
    assertThat(row.nativeStatus()).isEqualTo("3");
    assertThat(row.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(row.amount().value()).isEqualTo(HELD);
    assertThat(row.relatedObjects())
        .containsOnly(
            Map.entry("merchantId", MERCHANT),
            Map.entry("refundedTransactionId", hold.transactionId()));
  }

  @Test
  void the_release_path_is_two_ordinary_operations_and_neither_leg_repeats() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    refund(hold, HELD);

    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":808");
    assertThat(bodyOf(refund(hold, HELD))).contains("\"id\":210");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(3);
    assertThat(stateOf(hold.transactionId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void answers_the_published_refund_record_with_its_own_id_and_a_back_pointer() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    GrowApiResponse response = refund(hold, HELD);

    assertThat(response.httpStatus()).isEqualTo(200);
    String refundId = String.valueOf(response.providerObjectId());
    String asmachta = transactions.all().get(1).asmachta();
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":1,\"err\":\"\",\"data\":{"
                + "\"refundedTransactionId\":"
                + hold.transactionId()
                + ",\"asmachta\":\""
                + asmachta
                + "\",\"cardSuffix\":\"4580\",\"cardType\":\"Foreign\",\"cardTypeCode\":\"2\","
                + "\"cardBrand\":\"Visa\",\"cardBrandCode\":\"3\",\"cardExp\":\"0424\","
                + "\"status\":\"תשלום שזוכה\",\"statusCode\":3,"
                + "\"transactionTypeId\":\"1\",\"paymentType\":\"2\",\"sum\":\"120.50\","
                + "\"paymentDate\":\"28/11/26\",\"description\":\"A hold\","
                + "\"fullName\":\"Israel Israeli\",\"payerPhone\":\"0505503144\","
                + "\"payerEmail\":\"payer@merchant.example\",\"transactionId\":\""
                + refundId
                + "\"}}");
  }

  @Test
  void a_refund_row_is_refused_a_refund_of_its_own_with_716_and_a_settlement_with_813() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    String refundId = String.valueOf(refund(hold, HELD).providerObjectId());

    assertThat(stateOf(refundId).relatedObjects()).doesNotContainKey("processId");
    assertThat(transactions.all().get(1).transactionToken()).isNull();
    assertThat(transactions.all().get(1).processId()).isNull();

    Map<String, String> refundOfARefund = new LinkedHashMap<>();
    refundOfARefund.put(GrowFields.USER_ID, USER_ID);
    refundOfARefund.put(GrowFields.TRANSACTION_ID, refundId);
    refundOfARefund.put(GrowFields.TRANSACTION_TOKEN, hold.transactionToken());
    refundOfARefund.put(GrowFields.REFUND_SUM, HELD);
    assertThat(bodyOf(api.refundTransaction(call(REFUND, refundOfARefund)))).contains("\"id\":716");

    Map<String, String> settlementOfARefund = new LinkedHashMap<>();
    settlementOfARefund.put(GrowFields.USER_ID, USER_ID);
    settlementOfARefund.put(GrowFields.TRANSACTION_ID, refundId);
    settlementOfARefund.put(GrowFields.TRANSACTION_TOKEN, hold.transactionToken());
    settlementOfARefund.put(GrowFields.SUM, HELD);
    assertThat(bodyOf(api.settleSuspendedTransaction(call(SETTLE, settlementOfARefund))))
        .contains("\"id\":813");
  }

  @Test
  void a_hold_nothing_has_settled_accepts_no_refund_because_nothing_of_it_was_ever_charged() {
    GrowTransaction hold = aHold();

    ProviderObjectState held = stateOf(hold.transactionId());

    assertThat(held.refundsAccepted()).isFalse();
    assertThat(balancesOf(held).refundableRemainder().value()).isEqualTo("0");
    assertThat(bodyOf(refund(hold, HELD))).contains("\"id\":813");
  }

  @Test
  void a_transaction_whose_refund_allowance_is_spent_still_reports_what_it_never_returned() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    clock.moveTo(NOW.plus(A_DAY));
    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"statusCode\":3");

    ProviderObjectState spent = stateOf(hold.transactionId());

    assertThat(spent.refundsAccepted()).isFalse();
    assertThat(balancesOf(spent).refundableRemainder().value()).isEqualTo("80.50");
    assertThat(bodyOf(refund(hold, "80.50"))).contains("\"id\":210");
  }

  @Test
  void a_settled_transaction_the_allowance_still_admits_accepts_a_refund_of_what_it_took() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    ProviderObjectState settled = stateOf(hold.transactionId());

    assertThat(settled.refundsAccepted()).isTrue();
    assertThat(balancesOf(settled).refundableRemainder().value()).isEqualTo(HELD);
  }

  @Test
  void a_transaction_refunded_down_to_nothing_accepts_no_further_refund() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    refund(hold, HELD);

    ProviderObjectState returned = stateOf(hold.transactionId());

    assertThat(returned.refundsAccepted()).isFalse();
    assertThat(balancesOf(returned).refundableRemainder().value()).isEqualTo("0.00");
  }

  @Test
  void a_transaction_returned_in_full_accepts_no_refund_although_its_allowance_admits_another() {
    GrowTransaction hold =
        aHold(Map.of(GrowRefundAllowance.SETTING, GrowRefundAllowance.TWO_REFUNDS.name()));
    settle(hold, HELD);
    refund(hold, HELD);

    ProviderObjectState returned = stateOf(hold.transactionId());

    assertThat(returned.ledger().count(OperationKind.REFUND)).isOne();
    assertThat(returned.refundsAccepted()).isFalse();
    assertThat(balancesOf(returned).refundableRemainder().value()).isEqualTo("0.00");
    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"id\":105");
  }

  @Test
  void a_refund_row_moves_no_money_of_its_own_and_reports_that_it_accepts_no_refund() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    String refundId = String.valueOf(refund(hold, HELD).providerObjectId());

    ProviderObjectState row = stateOf(refundId);

    assertThat(row.ledger().operations()).isEmpty();
    assertThat(balancesOf(row)).isEqualTo(balances("0", "0", "0", "0"));
    assertThat(row.refundsAccepted()).isFalse();
    assertThat(balancesOf(stateOf(hold.transactionId())).refundedAmount().value()).isEqualTo(HELD);
  }

  @Test
  void refuses_a_partial_refund_of_a_transaction_charged_today_with_130() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "40.00")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":130,"
                + "\"message\":\"לא ניתן לבצע זיכוי חלקי על עסקה שבוצעה היום\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
    assertThat(stateOf(hold.transactionId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void permits_a_full_refund_on_the_day_the_transaction_was_charged() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, HELD))).contains("\"statusCode\":3");
  }

  @Test
  void the_same_day_rule_counts_the_day_of_the_charge_and_not_the_day_of_the_hold() {
    GrowTransaction hold = aHold();
    clock.moveTo(NOW.plus(A_DAY.multipliedBy(2)));
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"id\":130");

    clock.moveTo(NOW.plus(A_DAY.multipliedBy(3)));

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"statusCode\":3");
  }

  @Test
  void a_partial_refund_the_day_after_the_charge_leaves_the_payment_partially_refunded() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    clock.moveTo(NOW.plus(A_DAY));

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"sum\":\"40.00\"");

    ProviderObjectState partly = stateOf(hold.transactionId());
    assertThat(partly.normalizedStatus()).isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
    assertThat(partly.nativeStatus()).isEqualTo("2");
    assertThat(balancesOf(partly)).isEqualTo(balances(HELD, "40.00", "0", "80.50"));
    assertThat(bodyOf(refund(hold, "80.50"))).contains("\"id\":210");
  }

  @Test
  void refuses_a_refund_larger_than_what_was_actually_charged_with_105() {
    GrowTransaction hold = aHold();
    settle(hold, "40.00");

    assertThat(bodyOf(refund(hold, "50.00")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":105,"
                + "\"message\":\"לא ניתן לבצע זיכוי על סכום הגדול מהסכום המקורי\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"statusCode\":3");
  }

  @Test
  void an_account_configured_for_the_charge_wording_answers_190() {
    GrowTransaction hold =
        aHold(
            Map.of(
                GrowRefundExcessRejection.SETTING, GrowRefundExcessRejection.CHARGE_AMOUNT.name()));
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "120.51")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":190,"
                + "\"message\":\"לא ניתן לבצע זיכוי, סכום הזיכוי גדול מסכום החיוב\"},"
                + "\"data\":\"\"}");
  }

  @Test
  void an_account_configured_for_the_transaction_wording_answers_218() {
    GrowTransaction hold =
        aHold(
            Map.of(
                GrowRefundExcessRejection.SETTING,
                GrowRefundExcessRejection.TRANSACTION_AMOUNT.name()));
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "120.51")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":218,"
                + "\"message\":\"סכום הזיכוי גדול מסכום העסקה\"},\"data\":\"\"}");
  }

  @ParameterizedTest(name = "refuses a refund of {0}")
  @ValueSource(strings = {"0", "-1", "0.00", "not-a-number"})
  void refuses_a_refund_amount_that_is_not_a_usable_amount_with_215(String refundSum) {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, refundSum)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":215,"
                + "\"message\":\"הסכום לזיכוי אינו תקין\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
  }

  @Test
  void keeps_the_exact_characters_of_the_amount_it_was_refunded_for() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "120.5000"))).contains("\"sum\":\"120.5000\"");

    ProviderObjectState refunded = stateOf(hold.transactionId());
    assertThat(refunded.ledger().operations().get(2).amount().value()).isEqualTo("120.5000");
    assertThat(refunded.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(balancesOf(refunded).refundedAmount().value()).isEqualTo("120.5000");
  }

  @Test
  void refuses_to_refund_a_hold_nobody_settled_with_813_and_leaves_it_held() {
    GrowTransaction hold = aHold();

    assertThat(bodyOf(refund(hold, HELD)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":813,"
                + "\"message\":\"לא ניתן לבצע חיוב לעסקה שלא בוצעה\"},\"data\":\"\"}");
    ProviderObjectState held = stateOf(hold.transactionId());
    assertThat(held.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(held.ledger().operations()).hasSize(1);
    assertThat(balancesOf(held).capturableRemainder().value()).isEqualTo(HELD);
  }

  @Test
  void refuses_to_refund_a_saved_card_that_was_never_charged_with_813() {
    GrowTransaction registration = aSavedCard();

    assertThat(bodyOf(refund(registration, "1"))).contains("\"id\":813");
    assertThat(stateOf(registration.transactionId()).ledger().operations()).isEmpty();
  }

  @Test
  void an_account_configured_for_two_refunds_admits_the_second_and_refuses_the_third() {
    GrowTransaction hold =
        aHold(Map.of(GrowRefundAllowance.SETTING, GrowRefundAllowance.TWO_REFUNDS.name()));
    settle(hold, HELD);
    clock.moveTo(NOW.plus(A_DAY));

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"statusCode\":3");
    assertThat(balancesOf(stateOf(hold.transactionId())))
        .isEqualTo(balances(HELD, "40.00", "0", "80.50"));

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"statusCode\":3");
    assertThat(balancesOf(stateOf(hold.transactionId())))
        .isEqualTo(balances(HELD, "80.00", "0", "40.50"));

    assertThat(bodyOf(refund(hold, "40.50")))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":210,"
                + "\"message\":\"בוצע זיכוי לעסקה זו, לא ניתן לזכות בשנית.\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
  }

  @Test
  void a_second_refund_under_the_two_refund_allowance_returns_the_rest_and_ends_refunded() {
    GrowTransaction hold =
        aHold(Map.of(GrowRefundAllowance.SETTING, GrowRefundAllowance.TWO_REFUNDS.name()));
    settle(hold, HELD);
    clock.moveTo(NOW.plus(A_DAY));
    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"statusCode\":3");
    assertThat(stateOf(hold.transactionId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);

    assertThat(bodyOf(refund(hold, "80.50"))).contains("\"sum\":\"80.50\"");

    ProviderObjectState refunded = stateOf(hold.transactionId());
    assertThat(refunded.normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(refunded.nativeStatus()).isEqualTo("2");
    assertThat(balancesOf(refunded)).isEqualTo(balances(HELD, "120.50", "0", "0.00"));
    assertThat(refunded.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(
            OperationKind.AUTHORIZATION,
            OperationKind.CAPTURE,
            OperationKind.REFUND,
            OperationKind.REFUND);
    assertThat(transactions.all()).hasSize(3);
  }

  @Test
  void refuses_a_refund_of_an_account_with_no_balance_with_115() {
    GrowTransaction hold =
        aHold(Map.of(GrowMerchantBalance.SETTING, GrowMerchantBalance.EMPTY.name()));
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, HELD)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":115,\"message\":"
                + "\"אין אשראי מספק לביצוע הזיכוי. אנא פנה לשירות הלקוחות בטלפון 3144*.\"},"
                + "\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
  }

  @Test
  void answers_813_and_not_115_for_a_refund_of_an_uncharged_transaction_of_an_empty_account() {
    GrowTransaction hold =
        aHold(Map.of(GrowMerchantBalance.SETTING, GrowMerchantBalance.EMPTY.name()));

    assertThat(bodyOf(refund(hold, HELD))).contains("\"id\":813");
  }

  @Test
  void answers_215_and_not_115_for_an_unusable_amount_refunded_from_an_empty_account() {
    GrowTransaction hold =
        aHold(Map.of(GrowMerchantBalance.SETTING, GrowMerchantBalance.EMPTY.name()));
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "0"))).contains("\"id\":215");
  }

  @Test
  void answers_210_and_not_215_for_a_second_refund_of_an_amount_that_is_not_an_amount() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    refund(hold, HELD);

    assertThat(bodyOf(refund(hold, "not-a-number"))).contains("\"id\":210");
  }

  @Test
  void refunds_on_the_userId_alone_on_the_pageCode_alone_and_on_the_pair() {
    GrowTransaction first = aHold();
    settle(first, HELD);

    assertThat(bodyOf(refund(first, HELD))).contains("\"statusCode\":3");

    GrowTransaction second = anotherHold();
    settle(second, HELD);
    Map<String, String> onThePageCode = new LinkedHashMap<>();
    onThePageCode.put(GrowFields.PAGE_CODE, PAGE_CODE);
    onThePageCode.put(GrowFields.TRANSACTION_ID, second.transactionId());
    onThePageCode.put(GrowFields.TRANSACTION_TOKEN, second.transactionToken());
    onThePageCode.put(GrowFields.REFUND_SUM, HELD);

    assertThat(bodyOf(api.refundTransaction(call(REFUND, onThePageCode))))
        .contains("\"statusCode\":3");

    GrowTransaction third = anotherHold();
    settle(third, HELD);
    Map<String, String> onBoth = refundOf(third, HELD);
    onBoth.put(GrowFields.PAGE_CODE, PAGE_CODE);

    assertThat(bodyOf(api.refundTransaction(call(REFUND, onBoth)))).contains("\"statusCode\":3");
  }

  @Test
  void answers_724_for_a_request_that_carries_neither_credential() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    Map<String, String> anonymous = refundOf(hold, HELD);
    anonymous.remove(GrowFields.USER_ID);

    assertThat(bodyOf(api.refundTransaction(call(REFUND, anonymous))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":724,"
                + "\"message\":\"נדרש שדה userId או pageCode\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
  }

  @Test
  void answers_716_for_the_page_code_only_shape_the_official_postman_refund_sends() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    Map<String, String> postmanShaped = refundOf(hold, HELD);
    postmanShaped.remove(GrowFields.USER_ID);
    postmanShaped.put(GrowFields.PAGE_CODE, "not-a-configured-page");

    assertThat(bodyOf(api.refundTransaction(call(REFUND, postmanShaped))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":716,"
                + "\"message\":\"פרמטר קוד עסקה או תוקן אינו תקין\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
  }

  @Test
  void answers_801_for_a_user_id_that_matches_no_business() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    Map<String, String> unknownBusiness = refundOf(hold, HELD);
    unknownBusiness.put(GrowFields.USER_ID, "not-a-configured-user");

    assertThat(bodyOf(api.refundTransaction(call(REFUND, unknownBusiness))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":801,"
                + "\"message\":\"קוד זיהוי עסק אינו תקין\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
  }

  @Test
  void answers_716_alike_for_an_unknown_id_a_wrong_token_and_another_business() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    accounts.with(SimulatorDoubles.account(RUN, "merchant-2", "other-user", "other-page"));

    Map<String, String> unknownTransaction = refundOf(hold, HELD);
    unknownTransaction.put(GrowFields.TRANSACTION_ID, "999999");
    Map<String, String> wrongToken = refundOf(hold, HELD);
    wrongToken.put(GrowFields.TRANSACTION_TOKEN, "a1b2c3d4e5f6a7b8c9");
    Map<String, String> anotherBusiness = refundOf(hold, HELD);
    anotherBusiness.put(GrowFields.USER_ID, "other-user");

    assertThat(
            List.of(unknownTransaction, wrongToken, anotherBusiness).stream()
                .map(form -> bodyOf(api.refundTransaction(call(REFUND, form))))
                .distinct())
        .singleElement()
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":716,"
                + "\"message\":\"פרמטר קוד עסקה או תוקן אינו תקין\"},\"data\":\"\"}");
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
  }

  @Test
  void answers_801_for_a_credential_two_accounts_of_one_test_run_answer_to() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    accounts.with(SimulatorDoubles.account(RUN, "merchant-2", USER_ID, "another-page"));

    assertThat(bodyOf(refund(hold, HELD))).contains("\"id\":801");
  }

  @ParameterizedTest(name = "answers 54 naming the missing {0}")
  @ValueSource(strings = {"transactionId", "transactionToken", "refundSum"})
  void answers_54_naming_the_field_a_refund_left_out(String missing) {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    Map<String, String> incomplete = refundOf(hold, HELD);
    incomplete.remove(missing);

    assertThat(bodyOf(api.refundTransaction(call(REFUND, incomplete))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:"
                + missing
                + "\"},\"data\":\"\"}");
  }

  @Test
  void two_refunds_of_one_transaction_apply_once() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    List<GrowApiResponse> interleaved = new ArrayList<>();
    transactions.whileRefunding(() -> interleaved.add(refund(hold, HELD)));

    GrowApiResponse first = refund(hold, HELD);

    assertThat(interleaved).hasSize(1);
    assertThat(bodyOf(interleaved.get(0))).contains("\"statusCode\":3");
    assertThat(bodyOf(first)).contains("\"id\":210");
    ProviderObjectState refunded = stateOf(hold.transactionId());
    assertThat(refunded.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION, OperationKind.CAPTURE, OperationKind.REFUND);
    assertThat(balancesOf(refunded).refundedAmount().value()).isEqualTo(HELD);
    assertThat(transactions.all()).hasSize(2);
  }

  @Test
  void a_reset_landing_inside_a_refund_is_answered_716_in_grows_own_envelope() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    transactions.whileRefunding(() -> transactions.clear(RUN));

    GrowApiResponse response = refund(hold, HELD);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":716,"
                + "\"message\":\"פרמטר קוד עסקה או תוקן אינו תקין\"},\"data\":\"\"}");
    assertThat(provider.state(RUN, hold.transactionId())).isEmpty();
    assertThat(transactions.all()).isEmpty();
  }

  @Test
  void a_drawn_refund_id_that_is_already_taken_is_drawn_again() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    transactions.rejectingTheNext(1);

    GrowApiResponse response = refund(hold, HELD);

    assertThat(bodyOf(response)).contains("\"statusCode\":3");
    assertThat(transactions.all()).hasSize(2);
    assertThat(transactions.all().get(1).transactionId())
        .isEqualTo(String.valueOf(response.providerObjectId()));
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(3);
  }

  @Test
  void a_refund_id_space_that_yields_nothing_is_a_simulator_fault() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    transactions.rejectingTheNext(RefundTransaction.ID_ATTEMPTS);

    assertThatThrownBy(() -> refund(hold, HELD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transactionId");
    assertThat(transactions.all()).hasSize(1);
    assertThat(stateOf(hold.transactionId()).ledger().operations()).hasSize(2);
  }

  @Test
  void audits_the_refund_as_an_inbound_request_and_an_outbound_response() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    clock.moveTo(NOW.plusSeconds(30));

    GrowApiResponse response = refund(hold, HELD);

    List<AuditRecord> ofTheRefund =
        audit.all().stream().filter(record -> REFUND.equals(record.path())).toList();
    assertThat(ofTheRefund)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    AuditRecord inbound = ofTheRefund.get(0);
    assertThat(inbound.createdAt()).isEqualTo(NOW.plusSeconds(30));
    assertThat(inbound.sanitizedBody())
        .containsEntry(GrowFields.USER_ID, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.TRANSACTION_TOKEN, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.REFUND_SUM, List.of(HELD));
    AuditRecord outbound = ofTheRefund.get(1);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.httpStatus()).isEqualTo(200);
    assertThat(outbound.providerObjectId()).isEqualTo(response.providerObjectId());
    assertThat(outbound.sanitizedBody())
        .containsEntry("data.refundedTransactionId", List.of(hold.transactionId()))
        .doesNotContainKey("data.transactionToken");
  }

  @Test
  void a_refund_this_transaction_refused_answers_under_the_transaction_it_refused() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);

    GrowApiResponse refused = refund(hold, "40.00");

    assertThat(refused.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(refused.providerObjectId()).isEqualTo(hold.transactionId());
    assertThat(theOutboundRefusal().refusal())
        .isEqualTo(RefusalSubject.theObject(hold.transactionId()));
  }

  @Test
  void a_refund_naming_a_transaction_this_run_does_not_hold_answers_under_no_transaction() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    Map<String, String> unknown = refundOf(hold, HELD);
    unknown.put(GrowFields.TRANSACTION_ID, "999999");

    GrowApiResponse refused = api.refundTransaction(call(REFUND, unknown));

    assertThat(refused.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(refused.providerObjectId()).isNull();
    assertThat(theOutboundRefusal().refusal())
        .isEqualTo(RefusalSubject.noObjectAnsweredTo(GrowFields.TRANSACTION_ID));
  }

  private AuditRecord theOutboundRefusal() {
    List<AuditRecord> answered =
        audit.all().stream()
            .filter(record -> REFUND.equals(record.path()))
            .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
            .toList();
    return answered.get(answered.size() - 1);
  }

  private GrowTransaction aHold() {
    return aHold(Map.of());
  }

  private GrowTransaction aHold(Map<String, String> settings) {
    configure(settings);
    String processId = createTheProcess(aHoldRequest(HELD));
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    assertThat(transactions.all()).hasSize(1);
    return transactions.all().get(0);
  }

  private GrowTransaction anotherHold() {
    String processId = createTheProcess(aHoldRequest(HELD));
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    return transactions.all().get(transactions.all().size() - 1);
  }

  private GrowTransaction aSavedCard() {
    configure(Map.of());
    Map<String, String> request = aHoldRequest("1");
    request.remove(GrowFields.PAYMENT_INTENT_FIELD);
    request.put(GrowFields.CHARGE_TYPE, GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    String processId = createTheProcess(request);
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    return transactions.all().get(0);
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

  private GrowApiResponse settle(GrowTransaction transaction, String sum) {
    Map<String, String> settlement = new LinkedHashMap<>();
    settlement.put(GrowFields.USER_ID, USER_ID);
    settlement.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    settlement.put(GrowFields.TRANSACTION_TOKEN, transaction.transactionToken());
    settlement.put(GrowFields.SUM, sum);
    return api.settleSuspendedTransaction(call(SETTLE, settlement));
  }

  private GrowApiResponse refund(GrowTransaction transaction, String refundSum) {
    return api.refundTransaction(call(REFUND, refundOf(transaction, refundSum)));
  }

  private static Map<String, String> refundOf(GrowTransaction transaction, String refundSum) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, USER_ID);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, String.valueOf(transaction.transactionToken()));
    fields.put(GrowFields.REFUND_SUM, refundSum);
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

  private ProviderObjectState stateOf(String transactionId) {
    return provider.state(RUN, transactionId).orElseThrow();
  }

  private static DerivedBalances balancesOf(ProviderObjectState state) {
    return state.derivedBalances().orElseThrow();
  }

  private static DerivedBalances balances(
      String captured, String refunded, String capturable, String refundable) {
    return new DerivedBalances(
        Amount.of(captured), Amount.of(refunded), Amount.of(capturable), Amount.of(refundable));
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
