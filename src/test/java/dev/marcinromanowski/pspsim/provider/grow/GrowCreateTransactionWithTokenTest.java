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
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrowCreateTransactionWithTokenTest {

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String NOTIFY_URL = "https://merchant.example/callbacks/grow";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String CHARGE = "/api/light/server/1.0/createTransactionWithToken";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String LOOK_UP = "/api/light/server/1.0/getTransactionInfo";

  private static final String CHARGED = "89.90";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final SimulatorClock clock = SimulatorDoubles.clockAt(NOW);

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

  @BeforeEach
  void configureTheBusiness() {
    accounts.with(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE, Map.of()));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, NOTIFY_URL));
    testRuns.register(RUN);
  }

  @Test
  void charges_a_saved_card_and_answers_the_transaction_record_grow_publishes() {
    String cardToken = aSavedCard();

    GrowApiResponse response = charge(aChargeOf(cardToken));

    GrowTransaction charge = theOnlyStoredCardCharge();
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.providerObjectId()).isEqualTo(charge.transactionId());
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":1,\"err\":\"\",\"data\":{"
                + "\"asmachta\":\""
                + charge.asmachta()
                + "\",\"cardSuffix\":\"4580\",\"cardType\":\"Foreign\",\"cardTypeCode\":2,"
                + "\"cardBrand\":\"Visa\",\"cardBrandCode\":3,\"cardExp\":\"0424\","
                + "\"firstPaymentSum\":0,\"periodicalPaymentSum\":0,\"status\":\"שולם\","
                + "\"statusCode\":2,\"transactionTypeId\":\"1\",\"paymentType\":\"2\","
                + "\"sum\":\"89.90\",\"paymentsNum\":0,\"allPaymentsNum\":1,"
                + "\"paymentDate\":\"28/11/26\",\"description\":\"A stored-card charge\","
                + "\"fullName\":\"Israel Israeli\",\"payerPhone\":\"0505503144\","
                + "\"payerEmail\":\"payer@merchant.example\",\"transactionId\":"
                + charge.transactionId()
                + ",\"transactionToken\":\""
                + charge.transactionToken()
                + "\"}}");
  }

  @Test
  void a_charged_stored_card_is_money_taken_and_no_hold_of_any_kind() {
    charge(aChargeOf(aSavedCard()));

    GrowTransaction charge = theOnlyStoredCardCharge();
    assertThat(charge.holdsAnAuthorization()).isFalse();
    assertThat(charge.isReleased()).isTrue();
    assertThat(charge.ledger().count(OperationKind.CAPTURE)).isEqualTo(1);
    assertThat(charge.amount()).isEqualTo(Amount.of(CHARGED));
    assertThat(charge.processId()).isNull();
    assertThat(charge.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
  }

  @Test
  void a_charged_stored_card_is_reported_as_succeeded_with_grows_own_paid_status() {
    charge(aChargeOf(aSavedCard()));

    ProviderObjectState state = stateOf(theOnlyStoredCardCharge().transactionId());
    assertThat(state.nativeStatus()).isEqualTo(GrowNativeStatus.PAID_CODE);
    assertThat(state.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(state.relatedObjects()).containsEntry("merchantId", MERCHANT);
    assertThat(state.relatedObjects()).doesNotContainKey("processId");
  }

  @Test
  void money_taken_from_a_stored_card_can_be_refunded_in_full_the_same_day() {
    charge(aChargeOf(aSavedCard()));
    GrowTransaction charge = theOnlyStoredCardCharge();

    GrowApiResponse refunded = refund(charge, CHARGED);

    assertThat(refunded.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(stateOf(charge.transactionId()).normalizedStatus())
        .isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void a_stored_card_charge_holds_nothing_a_settlement_could_release() {
    charge(aChargeOf(aSavedCard()));

    GrowApiResponse settled = settle(theOnlyStoredCardCharge(), CHARGED);

    assertThat(bodyOf(settled)).contains("\"id\":810");
  }

  @Test
  void refuses_a_card_token_no_process_of_this_business_saved() {
    aSavedCard();

    GrowApiResponse response = charge(aChargeOf("a-token-this-simulator-never-issued"));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":140,"
                + "\"message\":\"לא ניתן לבצע תשלום עם טוקן, קוד מזהה טוקן אינו תקין\"},"
                + "\"data\":\"\"}");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void refuses_a_card_saved_by_another_business_as_a_token_it_does_not_know() {
    String cardToken = aSavedCard();
    accounts.with(
        SimulatorDoubles.account(RUN, "merchant-2", "another-grow-user", "another-page", Map.of()));

    GrowApiResponse response =
        charge(aChargeOf(cardToken, Map.of(GrowFields.USER_ID, "another-grow-user")));

    assertThat(bodyOf(response)).contains("\"id\":140");
  }

  @Test
  void reports_a_credential_failure_as_the_business_lookup_error_grow_was_probed_answering() {
    String cardToken = aSavedCard();

    GrowApiResponse response =
        charge(aChargeOf(cardToken, Map.of(GrowFields.USER_ID, "not-this-business")));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":880,"
                + "\"message\":\"לא נמצא עסק מתאים לUserId\"},\"data\":\"\"}");
  }

  @Test
  void refuses_a_stored_card_charge_that_counts_no_payments_the_way_grow_refuses_it() {
    String cardToken = aSavedCard();
    Map<String, String> withoutPaymentNum = aChargeOf(cardToken);
    withoutPaymentNum.remove(GrowFields.PAYMENT_NUM);

    GrowApiResponse response = charge(withoutPaymentNum);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:paymentNum\"},\"data\":\"\"}");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void refuses_a_stored_card_charge_with_no_payment_count_before_it_reads_the_credential() {
    String cardToken = aSavedCard();
    Map<String, String> form =
        aChargeOf(cardToken, Map.of(GrowFields.USER_ID, "not-this-business"));
    form.remove(GrowFields.PAYMENT_NUM);

    assertThat(bodyOf(charge(form))).contains("\"id\":54").doesNotContain("\"id\":880");
  }

  @ParameterizedTest(name = "refuses a harness identifier of {0} the way the live wire refuses it")
  @ValueSource(strings = {"order-123", "3f1b8c2e-5d64-4f2a-9a3e-1c7d0b6e5a42", "01", "1.5", "-1"})
  void refuses_a_unique_identifier_that_is_not_a_bare_number(String identifier) {
    String cardToken = aSavedCard();

    GrowApiResponse response =
        charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, identifier)));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":1013,\"message\":\"מזהה לא תקין."
                + " על המזהה להיות מספר שלם חיובי, ללא אפסים מובילים ושניתן לייצוג ב32 ביט"
                + " (כלומר, קטן מ 4,294,967,295)\"},\"data\":\"\"}");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void admits_the_widest_identifier_thirty_two_bits_hold_and_refuses_the_next_one() {
    String cardToken = aSavedCard();

    assertThat(
            charge(
                    aChargeOf(
                        cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "4294967295")))
                .outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(
            bodyOf(
                charge(
                    aChargeOf(
                        cardToken,
                        Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "4294967296")))))
        .contains("\"id\":1013");
  }

  @ParameterizedTest(name = "reads a unique identifier of {0} as one that was never sent")
  @ValueSource(strings = {"0", "   "})
  void reports_an_identifier_grow_reads_as_absent_as_a_user_that_does_not_exist(String identifier) {
    String cardToken = aSavedCard();

    GrowApiResponse response =
        charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, identifier)));

    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":104,"
                + "\"message\":\"משתמש זה אינו קיים במערכת\"},\"data\":\"\"}");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void judges_the_identifier_before_the_credential_the_way_the_live_wire_does() {
    String cardToken = aSavedCard();

    GrowApiResponse response =
        charge(
            aChargeOf(
                cardToken,
                Map.of(
                    GrowFields.USER_ID, "not-this-business",
                    GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "order-123")));

    assertThat(bodyOf(response)).contains("\"id\":1013").doesNotContain("\"id\":880");
  }

  @ParameterizedTest(name = "rejects a stored-card charge with no {0} as error 54 naming it")
  @ValueSource(
      strings = {
        "userId",
        "cardToken",
        "paymentType",
        "paymentNum",
        "description",
        "pageField[fullName]",
        "pageField[phone]"
      })
  void names_the_missing_required_field(String field) {
    String cardToken = aSavedCard();
    Map<String, String> withoutField = aChargeOf(cardToken);
    withoutField.remove(field);

    GrowApiResponse response = charge(withoutField);

    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:"
                + field
                + "\"},\"data\":\"\"}");
  }

  @Test
  void reports_a_missing_field_before_a_credential_failure() {
    String cardToken = aSavedCard();
    Map<String, String> form = aChargeOf(cardToken, Map.of(GrowFields.USER_ID, "not-this-one"));
    form.remove(GrowFields.DESCRIPTION);

    assertThat(bodyOf(charge(form))).contains("\"id\":54");
  }

  @ParameterizedTest(name = "refuses to charge {0}, which is no amount to take")
  @ValueSource(strings = {"0", "-1", "0.00", "not-a-number"})
  void refuses_an_amount_that_takes_no_money(String sum) {
    String cardToken = aSavedCard();

    GrowApiResponse response = charge(aChargeOf(cardToken, Map.of(GrowFields.SUM, sum)));

    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":707,\"message\":\"הסכום אינו תקין\"},\"data\":\"\"}");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void judges_the_amount_before_every_field_it_checks_for_presence() {
    String cardToken = aSavedCard();
    Map<String, String> form = aChargeOf(cardToken, Map.of(GrowFields.SUM, "0"));
    form.remove(GrowFields.PAYMENT_NUM);

    assertThat(bodyOf(charge(form))).contains("\"id\":707").doesNotContain("\"id\":54");
  }

  @Test
  void refuses_a_unique_identifier_a_charge_of_this_business_already_used() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    GrowApiResponse repeated = charge(aChargeOf(cardToken));

    assertThat(bodyOf(repeated))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":712,\"message\":\"העסקה כבר בוצעה\"},\"data\":\"\"}");
    assertThat(theStoredCardCharges()).hasSize(1);
  }

  @Test
  void refuses_a_unique_identifier_a_charge_that_was_declined_already_used() {
    String cardToken = aSavedCard();
    decline("809");
    assertThat(bodyOf(charge(aChargeOf(cardToken)))).contains("\"id\":809");
    scenarioRules.clear(GrowLightApi.GROW, RUN);

    GrowApiResponse repeated = charge(aChargeOf(cardToken));

    assertThat(bodyOf(repeated)).contains("\"id\":712");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void refuses_a_group_identifier_a_successful_payment_of_this_business_already_carries() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_GROUP_IDENTIFIER, "basket-7")));

    GrowApiResponse second =
        charge(
            aChargeOf(
                cardToken,
                Map.of(
                    GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "1002",
                    GrowFields.TRANSACTION_GROUP_IDENTIFIER, "basket-7")));

    assertThat(bodyOf(second)).contains("\"id\":712");
    assertThat(theStoredCardCharges()).hasSize(1);
  }

  @Test
  void a_repeat_under_a_held_unique_identifier_says_which_key_this_run_already_holds() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken));

    charge(aChargeOf(cardToken));

    assertThat(theLastRefusal().refusal())
        .isEqualTo(
            RefusalSubject.anObjectItWillNotName(
                GrowRefusals.THE_UNIQUE_IDENTIFIER_IS_ALREADY_HELD));
  }

  @Test
  void a_repeat_under_a_charged_group_identifier_says_which_key_this_run_already_holds() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_GROUP_IDENTIFIER, "basket-7")));

    charge(
        aChargeOf(
            cardToken,
            Map.of(
                GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "1002",
                GrowFields.TRANSACTION_GROUP_IDENTIFIER, "basket-7")));

    assertThat(theLastRefusal().refusal())
        .isEqualTo(RefusalSubject.anObjectItWillNotName(GrowRefusals.THE_GROUP_IS_ALREADY_CHARGED));
  }

  private AuditRecord theLastRefusal() {
    List<AuditRecord> refused =
        audit.all().stream()
            .filter(record -> record.outcome() == ProviderOutcome.FAILURE)
            .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
            .toList();
    return refused.get(refused.size() - 1);
  }

  @Test
  void admits_a_group_identifier_that_only_a_declined_charge_carries() {
    String cardToken = aSavedCard();
    decline("809");
    charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_GROUP_IDENTIFIER, "basket-7")));
    scenarioRules.clear(GrowLightApi.GROW, RUN);

    GrowApiResponse second =
        charge(
            aChargeOf(
                cardToken,
                Map.of(
                    GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "1002",
                    GrowFields.TRANSACTION_GROUP_IDENTIFIER, "basket-7")));

    assertThat(second.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(theStoredCardCharges()).hasSize(1);
  }

  @Test
  void a_declined_stored_card_charge_takes_no_money_and_answers_the_envelope_alone() {
    String cardToken = aSavedCard();
    decline("809");

    GrowApiResponse response = charge(aChargeOf(cardToken));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo("{\"status\":0,\"err\":{\"id\":809,\"message\":\"\"},\"data\":\"\"}");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void a_scenario_naming_a_stored_card_declines_charges_of_that_card_and_no_other() {
    String declined = aSavedCard();
    String accepted = aSecondSavedCard();
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            new ScenarioMatch(null, null, null, null, declined, null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("809"),
            null));

    assertThat(bodyOf(charge(aChargeOf(declined)))).contains("\"id\":809");
    assertThat(
            charge(aChargeOf(accepted, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "1003")))
                .outcome())
        .isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void a_stored_card_charge_is_looked_up_with_the_custom_fields_it_was_sent_and_no_process() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken, Map.of("cField1", "merchant-session", "cField3", "basket-7")));
    GrowTransaction charge = theOnlyStoredCardCharge();

    GrowApiResponse looked = lookUp(charge);

    assertThat(bodyOf(looked))
        .contains("\"processId\":\"\",\"processToken\":\"\",")
        .contains("\"customField\":{\"cField1\":\"merchant-session\",\"cField3\":\"basket-7\"}")
        .contains("\"statusCode\":2");
  }

  @Test
  void a_stored_card_charge_that_carried_no_custom_field_is_looked_up_with_grows_empty_array() {
    charge(aChargeOf(aSavedCard()));

    assertThat(bodyOf(lookUp(theOnlyStoredCardCharge()))).contains("\"customField\":[]");
  }

  @Test
  void keeps_the_card_token_out_of_the_audited_request_body() {
    String cardToken = aSavedCard();

    charge(aChargeOf(cardToken));

    assertThat(inboundBodyOf(CHARGE))
        .containsEntry(GrowFields.CARD_TOKEN, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, List.of("1001"));
  }

  @Test
  void refuses_to_charge_where_no_transaction_id_can_be_claimed_rather_than_reusing_one() {
    String cardToken = aSavedCard();
    transactions.withTheTransactionIdsAlreadyClaimed(
        "100003", "100004", "100005", "100006", "100007");
    Map<String, String> charging = aChargeOf(cardToken);

    assertThatThrownBy(() -> charge(charging))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no Grow transactionId could be claimed");
  }

  @Test
  void refuses_to_charge_where_the_claimed_transaction_id_was_taken_before_the_charge_was_stored() {
    String cardToken = aSavedCard();
    transactions.rejectingTheNext(1);
    Map<String, String> charging = aChargeOf(cardToken);

    assertThatThrownBy(() -> charge(charging))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("claimed by another request");
  }

  @Test
  void a_declined_stored_card_charge_carries_a_reason_a_merchant_could_act_on() {
    String cardToken = aSavedCard();
    decline("stolenCard");

    GrowApiResponse response = charge(aChargeOf(cardToken));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":103,"
                + "\"message\":\"כרטיס גנוב - עסקה לא מאושרת\"},\"data\":\"\"}");
    assertThat(theStoredCardCharges()).isEmpty();
  }

  @Test
  void tells_two_declines_that_share_one_error_id_apart_by_the_reason_grow_publishes() {
    String cardToken = aSavedCard();
    decline("expiredCard");
    assertThat(bodyOf(charge(aChargeOf(cardToken)))).contains("\"id\":103", "כרטיס פג תוקף.");
    scenarioRules.clear(GrowLightApi.GROW, RUN);
    decline("creditCompanyDidNotApprove");

    GrowApiResponse second =
        charge(aChargeOf(cardToken, Map.of(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "1004")));

    assertThat(bodyOf(second)).contains("\"id\":103", "חברת האשראי לא אשרה את העסקה");
  }

  @ParameterizedTest(name = "declines with the published reason {0}")
  @ValueSource(
      strings = {
        "blockedCard",
        "cardInactiveOrExpired",
        "cardBlockedByTheCreditCompany",
        "systemBlock"
      })
  void answers_every_published_reason_a_scenario_names(String reason) {
    String cardToken = aSavedCard();
    decline(reason);

    GrowApiResponse response = charge(aChargeOf(cardToken));

    assertThat(bodyOf(response)).doesNotContain("\"message\":\"\"");
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void still_declines_with_a_bare_number_for_a_code_grow_publishes_no_reason_for() {
    String cardToken = aSavedCard();
    decline("1013");

    assertThat(bodyOf(charge(aChargeOf(cardToken))))
        .isEqualTo("{\"status\":0,\"err\":{\"id\":1013,\"message\":\"\"},\"data\":\"\"}");
  }

  @Test
  void labels_a_response_the_way_grow_labels_it_and_still_sends_a_json_body() {
    String cardToken = aSavedCard();

    GrowApiResponse response = charge(aChargeOf(cardToken));

    assertThat(response.contentType()).isEqualTo("text/html; charset=UTF-8");
    assertThat(GrowWireText.asJson(response.body())).containsEntry("status", 1);
  }

  @Test
  void escapes_hebrew_the_way_the_live_wire_escapes_it_unless_the_account_asks_for_the_examples() {
    String cardToken = aSavedCard();
    decline("stolenCard");

    String escaped = new String(charge(aChargeOf(cardToken)).body(), UTF_8);

    assertThat(escaped).contains("\\u05db\\u05e8\\u05d8\\u05d9\\u05e1").doesNotContain("כרטיס");
    assertThat(GrowWireText.asJson(escaped.getBytes(UTF_8))).containsEntry("status", 0);
  }

  @Test
  void answers_an_account_built_against_the_published_examples_in_raw_utf8_json() {
    accounts.reconfigured(
        SimulatorDoubles.account(
            RUN,
            MERCHANT,
            USER_ID,
            PAGE_CODE,
            Map.of(GrowWireForm.SETTING, GrowWireForm.PUBLISHED_EXAMPLE.name())));
    String cardToken = aSavedCard();
    decline("stolenCard");

    GrowApiResponse response = charge(aChargeOf(cardToken));

    assertThat(response.contentType()).isEqualTo("application/json");
    assertThat(new String(response.body(), UTF_8))
        .contains("כרטיס גנוב - עסקה לא מאושרת")
        .doesNotContain("\\u");
  }

  private void decline(String providerCode) {
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(GrowOperations.CREATE_TRANSACTION_WITH_TOKEN),
            new ScenarioAction.TerminalFailure(providerCode),
            null));
  }

  private ProviderObjectState stateOf(String transactionId) {
    Optional<ProviderObjectState> state = provider.state(RUN, transactionId);
    assertThat(state).isPresent();
    return state.get();
  }

  private GrowTransaction theOnlyStoredCardCharge() {
    assertThat(theStoredCardCharges()).hasSize(1);
    return theStoredCardCharges().get(0);
  }

  private List<GrowTransaction> theStoredCardCharges() {
    return transactions.all().stream()
        .filter(t -> t.kind() == GrowTransactionKind.TOKEN_PAYMENT)
        .toList();
  }

  private String aSavedCard() {
    return theCardTokenOf(aTokenOnlyProcess(aTokenOnlyRequest()));
  }

  private String aSecondSavedCard() {
    Map<String, String> second = aTokenOnlyRequest();
    second.put(GrowFields.DESCRIPTION, "A second card");
    return theCardTokenOf(aTokenOnlyProcess(second));
  }

  private String theCardTokenOf(String processId) {
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    GrowPaymentProcess registered =
        processes.findByProcessId(RUN, processId).orElseThrow(AssertionError::new);
    GrowProcessOutcome outcome = registered.outcome();
    assertThat(outcome).isNotNull();
    assertThat(registered.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    return String.valueOf(outcome.cardToken());
  }

  private String aTokenOnlyProcess(Map<String, String> request) {
    GrowApiResponse created = api.createPaymentProcess(call(CREATE, request));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    return String.valueOf(created.providerObjectId());
  }

  private static Map<String, String> aTokenOnlyRequest() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    form.put(GrowFields.SUM, "1");
    form.put(GrowFields.SAVE_CARD_TOKEN, "1");
    form.put(GrowFields.DESCRIPTION, "A card registration");
    form.put(GrowFields.SUCCESS_URL, "https://merchant.example/thanks");
    form.put(GrowFields.CANCEL_URL, "https://merchant.example/cancel");
    form.put(GrowFields.NOTIFY_URL, NOTIFY_URL);
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    return form;
  }

  private static Map<String, String> aChargeOf(String cardToken) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.CARD_TOKEN, cardToken);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.SUM, CHARGED);
    form.put(GrowFields.DESCRIPTION, "A stored-card charge");
    form.put(GrowFields.PAYMENT_TYPE, "2");
    form.put(GrowFields.PAYMENT_NUM, "1");
    form.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "1001");
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    form.put(GrowFields.EMAIL, "payer@merchant.example");
    return form;
  }

  private static Map<String, String> aChargeOf(String cardToken, Map<String, String> overrides) {
    Map<String, String> form = aChargeOf(cardToken);
    form.putAll(overrides);
    return form;
  }

  private GrowApiResponse charge(Map<String, String> form) {
    return api.createTransactionWithToken(call(CHARGE, form));
  }

  private GrowApiResponse settle(GrowTransaction transaction, String sum) {
    Map<String, String> settlement = new LinkedHashMap<>();
    settlement.put(GrowFields.USER_ID, USER_ID);
    settlement.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    settlement.put(GrowFields.TRANSACTION_TOKEN, String.valueOf(transaction.transactionToken()));
    settlement.put(GrowFields.SUM, sum);
    return api.settleSuspendedTransaction(call(SETTLE, settlement));
  }

  private GrowApiResponse refund(GrowTransaction transaction, String refundSum) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.PAGE_CODE, PAGE_CODE);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, String.valueOf(transaction.transactionToken()));
    fields.put(GrowFields.REFUND_SUM, refundSum);
    return api.refundTransaction(call(REFUND, fields));
  }

  private GrowApiResponse lookUp(GrowTransaction transaction) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.PAGE_CODE, PAGE_CODE);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, String.valueOf(transaction.transactionToken()));
    return api.getTransactionInfo(call(LOOK_UP, fields));
  }

  private Map<String, List<String>> inboundBodyOf(String path) {
    return audit.all().stream()
        .filter(r -> r.direction() == AuditDirection.INBOUND_REQUEST && r.path().equals(path))
        .map(AuditRecord::sanitizedBody)
        .findFirst()
        .orElseThrow(AssertionError::new);
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
}
