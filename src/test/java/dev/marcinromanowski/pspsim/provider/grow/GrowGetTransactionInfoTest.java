package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GrowGetTransactionInfoTest {

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String OTHER_PAGE_CODE = "another-grow-page";
  private static final String OTHER_MERCHANT = "merchant-2";
  private static final String NOTIFY_URL = "https://merchant.example/callbacks/grow";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String LOOK_UP = "/api/light/server/1.0/getTransactionInfo";

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
  void answers_the_published_transaction_record_with_the_process_that_created_it() {
    GrowTransaction hold = aHold();
    GrowPaymentProcess process = processes.all().get(0);

    GrowApiResponse response = lookUp(hold.transactionId(), hold.transactionToken());

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(hold.transactionId());
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":1,\"err\":\"\",\"data\":{"
                + "\"asmachta\":\""
                + hold.asmachta()
                + "\",\"cardSuffix\":\"4580\",\"cardType\":\"Foreign\",\"cardTypeCode\":2,"
                + "\"cardBrand\":\"Visa\",\"cardBrandCode\":3,\"cardExp\":\"0424\","
                + "\"firstPaymentSum\":0,\"periodicalPaymentSum\":0,"
                + "\"status\":\"עסקה מושהית\",\"statusCode\":11,"
                + "\"transactionTypeId\":\"1\",\"paymentType\":\"2\",\"sum\":\"120.50\","
                + "\"paymentsNum\":0,\"allPaymentsNum\":1,"
                + "\"paymentDate\":\"28/11/26\",\"description\":\"A hold\","
                + "\"fullName\":\"Israel Israeli\",\"payerPhone\":\"0505503144\","
                + "\"payerEmail\":\"payer@merchant.example\","
                + "\"transactionId\":"
                + hold.transactionId()
                + ",\"transactionToken\":\""
                + hold.transactionToken()
                + "\",\"processId\":\""
                + process.processId()
                + "\",\"processToken\":\""
                + process.processToken()
                + "\",\"customField\":{\"cField1\":\"merchant-session-secret\","
                + "\"cField2\":\"intent-99\"}}}");
  }

  @Test
  void reports_a_settled_transaction_as_paid_which_is_what_the_settlement_answered() {
    GrowTransaction hold = aHold();
    assertThat(bodyOf(settle(hold, HELD))).contains("\"statusCode\":\"2\"");

    String looked = bodyOf(lookUp(hold.transactionId(), hold.transactionToken()));

    assertThat(looked).contains("\"status\":\"שולם\",\"statusCode\":2,");
    assertThat(looked).contains("\"sum\":\"120.50\"");
  }

  @Test
  void reports_a_refunded_original_as_still_paid_and_never_as_refunded() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    refund(hold, HELD);

    String looked = bodyOf(lookUp(hold.transactionId(), hold.transactionToken()));

    assertThat(looked).contains("\"status\":\"שולם\",\"statusCode\":2,");
    assertThat(looked).doesNotContain("תשלום שזוכה");
    assertThat(looked).doesNotContain("\"statusCode\":3");
  }

  @Test
  void answers_716_for_a_refunds_own_id_whichever_token_a_client_could_offer_with_it() {
    GrowTransaction hold = aHold();
    settle(hold, HELD);
    String refundId = String.valueOf(refund(hold, HELD).providerObjectId());
    GrowTransaction row = transactions.findByTransactionId(RUN, refundId).orElseThrow();

    assertThat(bodyOf(lookUp(refundId, hold.transactionToken()))).isEqualTo(NOT_FOUND);
    assertThat(bodyOf(lookUp(refundId, String.valueOf(row.transactionToken()))))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void answers_716_for_an_unknown_id_a_mismatched_token_and_another_business_alike() {
    GrowTransaction hold = aHold();
    anotherBusiness();

    assertThat(bodyOf(lookUp("999999", hold.transactionToken()))).isEqualTo(NOT_FOUND);
    assertThat(bodyOf(lookUp(hold.transactionId(), "not-the-token"))).isEqualTo(NOT_FOUND);
    assertThat(bodyOf(lookUpAs(OTHER_PAGE_CODE, hold.transactionId(), hold.transactionToken())))
        .isEqualTo(NOT_FOUND);
    assertThat(bodyOf(lookUpAs("no-such-page", hold.transactionId(), hold.transactionToken())))
        .isEqualTo(NOT_FOUND);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {GrowFields.PAGE_CODE, GrowFields.TRANSACTION_ID, GrowFields.TRANSACTION_TOKEN})
  void names_the_missing_required_field_that_the_endpoint_page_lists_first(String absent) {
    aHold();
    Map<String, String> request = aLookUpOf(PAGE_CODE, "100002", "a-token");
    request.remove(absent);

    assertThat(bodyOf(api.getTransactionInfo(call(request))))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,\"message\":\"חסרים נתונים:"
                + absent
                + "\"},\"data\":\"\"}");
  }

  @Test
  void names_the_page_code_first_when_a_request_supplies_nothing_at_all() {
    assertThat(bodyOf(api.getTransactionInfo(call(new LinkedHashMap<>()))))
        .contains("\"id\":54")
        .contains("pageCode");
  }

  @Test
  void reports_an_empty_array_of_custom_fields_and_an_empty_payer_email() {
    GrowTransaction plain = aHoldWithNeitherCustomFieldsNorAnEmail();

    String looked = bodyOf(lookUp(plain.transactionId(), plain.transactionToken()));

    assertThat(looked).contains("\"customField\":[]");
    assertThat(looked).contains("\"payerEmail\":\"\"");
  }

  @Test
  void audits_the_lookup_and_redacts_every_token_it_was_given_and_returned() {
    GrowTransaction hold = aHold();
    clock.moveTo(NOW.plusSeconds(30));

    lookUp(hold.transactionId(), hold.transactionToken());

    List<AuditRecord> ofTheLookup =
        audit.all().stream().filter(record -> LOOK_UP.equals(record.path())).toList();
    assertThat(ofTheLookup)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    AuditRecord inbound = ofTheLookup.get(0);
    assertThat(inbound.createdAt()).isEqualTo(NOW.plusSeconds(30));
    assertThat(inbound.sanitizedBody())
        .containsEntry(GrowFields.PAGE_CODE, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.TRANSACTION_TOKEN, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.TRANSACTION_ID, List.of(hold.transactionId()));
    AuditRecord outbound = ofTheLookup.get(1);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.providerObjectId()).isEqualTo(hold.transactionId());
    assertThat(outbound.sanitizedBody())
        .containsEntry("data.transactionToken", List.of(Redaction.REDACTED))
        .containsEntry("data.processToken", List.of(Redaction.REDACTED))
        .containsEntry("data.customField.cField1", List.of(Redaction.REDACTED))
        .containsEntry("data.customField.cField2", List.of("intent-99"))
        .containsEntry("data.statusCode", List.of("11"));
  }

  private static final String NOT_FOUND =
      "{\"status\":0,\"err\":{\"id\":716,"
          + "\"message\":\"פרמטר קוד עסקה או תוקן אינו תקין\"},\"data\":\"\"}";

  private GrowTransaction aHold() {
    configure();
    return theTransactionOf(createTheProcess(aHoldRequest()));
  }

  private GrowTransaction aHoldWithNeitherCustomFieldsNorAnEmail() {
    configure();
    Map<String, String> request = aHoldRequest();
    request.remove(GrowFields.EMAIL);
    request.remove("cField1");
    request.remove(GrowFields.PAYMENT_INTENT_FIELD);
    return theTransactionOf(createTheProcess(request));
  }

  private GrowTransaction theTransactionOf(String processId) {
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    return transactions.all().get(transactions.all().size() - 1);
  }

  private String createTheProcess(Map<String, String> request) {
    GrowApiResponse created = api.createPaymentProcess(call(CREATE, request));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    return String.valueOf(created.providerObjectId());
  }

  private void configure() {
    accounts.with(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE, Map.of()));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, NOTIFY_URL));
    testRuns.register(RUN);
  }

  private void anotherBusiness() {
    accounts.with(
        SimulatorDoubles.account(
            RUN, OTHER_MERCHANT, "another-grow-user", OTHER_PAGE_CODE, Map.of()));
  }

  private GrowApiResponse lookUp(String transactionId, String transactionToken) {
    return lookUpAs(PAGE_CODE, transactionId, transactionToken);
  }

  private GrowApiResponse lookUpAs(String pageCode, String transactionId, String token) {
    return api.getTransactionInfo(call(aLookUpOf(pageCode, transactionId, token)));
  }

  private static Map<String, String> aLookUpOf(
      String pageCode, String transactionId, String transactionToken) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.PAGE_CODE, pageCode);
    fields.put(GrowFields.TRANSACTION_ID, transactionId);
    fields.put(GrowFields.TRANSACTION_TOKEN, transactionToken);
    return fields;
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

  private static Map<String, String> aHoldRequest() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, "2");
    form.put(GrowFields.SUM, HELD);
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

  private static GrowApiCall call(Map<String, String> form) {
    return call(LOOK_UP, form);
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
