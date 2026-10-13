package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
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
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.kernel.webhook.internal.HttpMerchantEndpoint;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrowApproveTransactionTest {

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");
  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String APPROVE = "/api/light/server/1.0/approveTransaction";

  private static final String APPROVED = "{\"status\":1,\"err\":\"\",\"data\":\"\"}";

  private final LocalMerchantServer merchant = new LocalMerchantServer();
  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final MovableClock clock = new MovableClock(NOW);

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          deliveries,
          new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(5), 512),
          audit,
          clock,
          ids,
          new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1))),
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

  @AfterEach
  void stopTheMerchant() {
    merchant.close();
  }

  @Test
  void a_later_inbound_approval_acknowledges_a_callback_the_merchant_already_answered() {
    Transaction transaction = anUnacknowledgedCallback();

    assertThat(merchant.received()).hasSize(1);
    assertThat(lastVerdict()).isEqualTo(Acknowledgement.AWAITING_SEPARATE_CALL);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.PENDING);

    clock.moveTo(NOW.plusSeconds(45));
    GrowApiResponse response = approve(transaction.echo());

    assertThat(bodyOf(response)).isEqualTo(APPROVED);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(onlyDelivery().attemptsMade()).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void the_unacknowledged_update_is_resent_until_the_approval_arrives_and_then_stops() {
    Transaction transaction = anUnacknowledgedCallback();

    clock.moveTo(NOW.plusSeconds(1));
    assertThat(dispatcher.deliverDue(GrowLightApi.GROW, RUN)).isEqualTo(1);
    assertThat(merchant.received()).hasSize(2);

    approve(transaction.echo());

    clock.moveTo(NOW.plusSeconds(600));
    assertThat(dispatcher.deliverDue(GrowLightApi.GROW, RUN)).isZero();
    assertThat(merchant.received()).hasSize(2);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void approving_the_same_callback_twice_answers_and_changes_the_same_thing_twice() {
    Transaction transaction = anUnacknowledgedCallback();
    clock.moveTo(NOW.plusSeconds(10));
    approve(transaction.echo());

    clock.moveTo(NOW.plusSeconds(20));
    GrowApiResponse again = approve(transaction.echo());

    assertThat(bodyOf(again)).isEqualTo(APPROVED);
    WebhookDelivery delivery = onlyDelivery();
    assertThat(delivery.state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(delivery.updatedAt()).isEqualTo(NOW.plusSeconds(10));
    assertThat(dispatcher.attemptsOf(delivery.deliveryId())).hasSize(1);
  }

  @Test
  void acknowledging_a_callback_alters_nothing_about_the_transaction() {
    Transaction transaction = anUnacknowledgedCallback();
    GrowPaymentProcess before = onlyProcess();

    approve(transaction.echo());

    assertThat(onlyProcess()).isEqualTo(before);
    assertThat(onlyProcess().nativeStatusCode()).isEqualTo(GrowNativeStatus.SUSPENDED_CODE);
    assertThat(onlyProcess().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void an_account_on_the_common_rule_is_acknowledged_by_the_merchants_own_answer() {
    Transaction transaction = aDeliveredCallback(GrowAcknowledgementMode.HTTP_2XX);

    assertThat(lastVerdict()).isEqualTo(Acknowledgement.ACKNOWLEDGED);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);

    GrowApiResponse response = approve(transaction.echo());

    assertThat(bodyOf(response)).isEqualTo(APPROVED);
    assertThat(dispatcher.attemptsOf(onlyDelivery().deliveryId())).hasSize(1);
  }

  @Test
  void an_approval_for_a_callback_the_merchant_refused_settles_nothing_and_is_still_answered() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));
    Transaction transaction = aDeliveredCallback(GrowAcknowledgementMode.APPROVE_TRANSACTION);
    assertThat(lastVerdict()).isEqualTo(Acknowledgement.REFUSED);

    GrowApiResponse response = approve(transaction.echo());

    assertThat(bodyOf(response)).isEqualTo(APPROVED);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.PENDING);
    assertThat(onlyDelivery().nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
  }

  @Test
  void a_delivery_whose_account_this_run_configures_none_of_falls_back_on_the_common_rule() {
    anUnacknowledgedCallback();
    accounts.forget(RUN);

    clock.moveTo(NOW.plusSeconds(1));
    assertThat(dispatcher.deliverDue(GrowLightApi.GROW, RUN)).isEqualTo(1);

    assertThat(lastVerdict()).isEqualTo(Acknowledgement.ACKNOWLEDGED);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void approves_an_echo_built_from_the_callback_the_merchant_actually_received() {
    Transaction transaction = anUnacknowledgedCallback();
    Map<String, String> echo = new LinkedHashMap<>();
    echo.put(GrowFields.PAGE_CODE, PAGE_CODE);
    merchant
        .received()
        .get(0)
        .fields()
        .forEach(
            (name, value) -> {
              if (name.startsWith("data[") && !name.contains("[customFields]")) {
                echo.put(name.substring("data[".length(), name.length() - 1), value);
              }
            });
    assertThat(echo).containsKeys("transactionId", "transactionToken", "sum", "asmachta");
    assertThat(echo).doesNotContainKeys("transactionTypeId", "paymentType");

    assertThat(bodyOf(approve(echo))).isEqualTo(APPROVED);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(transaction.transactionId()).isEqualTo(echo.get("transactionId"));
  }

  @Test
  void approves_an_echo_that_carries_the_two_fields_as_well() {
    Transaction transaction = anUnacknowledgedCallback();
    Map<String, String> echo = transaction.echo();
    echo.put("transactionTypeId", "1");
    echo.put("paymentType", "2");

    assertThat(bodyOf(approve(echo))).isEqualTo(APPROVED);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void answers_180_for_a_transaction_it_holds_no_record_of() {
    anUnacknowledgedCallback();
    Map<String, String> echo = new LinkedHashMap<>();
    echo.put(GrowFields.PAGE_CODE, PAGE_CODE);
    echo.put(GrowFields.TRANSACTION_ID, "999999");
    echo.put(GrowFields.TRANSACTION_TOKEN, "not-a-token");

    GrowApiResponse response = approve(echo);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":180,"
                + "\"message\":\"פעולת עידכון לא בוצעה, רשומה לא נמצאה\"},\"data\":\"\"}");
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void answers_180_when_the_token_does_not_match_the_transaction_it_names() {
    Transaction transaction = anUnacknowledgedCallback();
    Map<String, String> echo = transaction.echo();
    echo.put(GrowFields.TRANSACTION_TOKEN, "a1b2c3d4e5f6a7b8c9");

    assertThat(bodyOf(approve(echo))).contains("\"id\":180");
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void answers_180_for_a_transaction_that_belongs_to_another_business() {
    Transaction transaction = anUnacknowledgedCallback();
    accounts.with(SimulatorDoubles.account(RUN, "merchant-2", "other-user", "other-page"));
    Map<String, String> echo = transaction.echo();
    echo.put(GrowFields.PAGE_CODE, "other-page");

    assertThat(bodyOf(approve(echo))).contains("\"id\":180");
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void answers_180_when_the_request_names_no_transaction_at_all() {
    anUnacknowledgedCallback();

    assertThat(bodyOf(approve(Map.of(GrowFields.PAGE_CODE, PAGE_CODE)))).contains("\"id\":180");
  }

  @Test
  void answers_54_naming_pageCode_when_no_credential_is_sent() {
    Transaction transaction = anUnacknowledgedCallback();
    Map<String, String> echo = transaction.echo();
    echo.remove(GrowFields.PAGE_CODE);

    assertThat(bodyOf(approve(echo)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,"
                + "\"message\":\"חסרים נתונים:pageCode\"},\"data\":\"\"}");
  }

  @Test
  void answers_180_for_a_pageCode_no_account_is_configured_for() {
    Transaction transaction = anUnacknowledgedCallback();
    Map<String, String> echo = transaction.echo();
    echo.put(GrowFields.PAGE_CODE, "not-a-configured-page");

    assertThat(bodyOf(approve(echo)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":180,"
                + "\"message\":\"פעולת עידכון לא בוצעה, רשומה לא נמצאה\"},\"data\":\"\"}");
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void answers_an_unrecognised_page_code_and_an_unknown_record_with_one_code() {
    Transaction transaction = anUnacknowledgedCallback();
    Map<String, String> echo = transaction.echo();
    echo.put(GrowFields.PAGE_CODE, "not-a-configured-page");
    echo.put(GrowFields.TRANSACTION_ID, "999999");

    assertThat(bodyOf(approve(echo))).contains("\"id\":180");
  }

  @Test
  void a_request_with_no_form_body_is_answered_by_grows_own_missing_field_error() {
    GrowApiResponse response =
        api.approveTransaction(new GrowApiCall("POST", APPROVE, Map.of(), Map.of()));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).contains("\"id\":54").contains("pageCode");
  }

  @Test
  void authenticates_on_the_pageCode_alone_and_ignores_a_userId_it_has_no_field_for() {
    Transaction transaction = anUnacknowledgedCallback();
    Map<String, String> echo = transaction.echo();
    echo.put(GrowFields.USER_ID, "not-this-businesss-user-id");

    assertThat(bodyOf(approve(echo))).isEqualTo(APPROVED);
    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void audits_the_approval_as_an_inbound_request_and_an_outbound_response() {
    Transaction transaction = anUnacknowledgedCallback();
    clock.moveTo(NOW.plusSeconds(30));

    approve(transaction.echo());

    List<AuditRecord> ofTheApproval =
        audit.all().stream().filter(record -> APPROVE.equals(record.path())).toList();
    assertThat(ofTheApproval)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    AuditRecord inbound = ofTheApproval.get(0);
    assertThat(inbound.createdAt()).isEqualTo(NOW.plusSeconds(30));
    assertThat(inbound.sanitizedBody())
        .containsEntry(GrowFields.PAGE_CODE, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.TRANSACTION_TOKEN, List.of(Redaction.REDACTED))
        .containsEntry(GrowFields.TRANSACTION_ID, List.of(transaction.transactionId()));
    AuditRecord outbound = ofTheApproval.get(1);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.httpStatus()).isEqualTo(200);
    assertThat(outbound.providerObjectId()).isEqualTo(onlyProcess().processId());
  }

  private record Transaction(String transactionId, String transactionToken) {

    Map<String, String> echo() {
      Map<String, String> fields = new LinkedHashMap<>();
      fields.put(GrowFields.PAGE_CODE, PAGE_CODE);
      fields.put(GrowFields.TRANSACTION_ID, transactionId);
      fields.put(GrowFields.TRANSACTION_TOKEN, transactionToken);
      return fields;
    }
  }

  private Transaction anUnacknowledgedCallback() {
    return aDeliveredCallback(GrowAcknowledgementMode.APPROVE_TRANSACTION);
  }

  private Transaction aDeliveredCallback(GrowAcknowledgementMode mode) {
    configure(mode);
    GrowApiResponse created = api.createPaymentProcess(call(CREATE, aHold()));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    provider.schedule(
        new WebhookScheduleRequest(
            RUN,
            String.valueOf(created.providerObjectId()),
            null,
            null,
            null,
            ObjectOrigin.CONTROL_API));
    GrowProcessOutcome outcome = onlyProcess().outcome();
    assertThat(outcome).isNotNull();
    return new Transaction(
        String.valueOf(outcome.transactionId()), String.valueOf(outcome.transactionToken()));
  }

  private void configure(GrowAcknowledgementMode mode) {
    accounts.with(
        SimulatorDoubles.account(
            RUN,
            MERCHANT,
            USER_ID,
            PAGE_CODE,
            Map.of(GrowAcknowledgementMode.SETTING, mode.name())));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, merchant.url()));
    testRuns.register(RUN);
  }

  private GrowApiResponse approve(Map<String, String> fields) {
    return api.approveTransaction(call(APPROVE, fields));
  }

  private Acknowledgement lastVerdict() {
    List<WebhookAttempt> attempts = dispatcher.attemptsOf(onlyDelivery().deliveryId());
    assertThat(attempts).isNotEmpty();
    return attempts.get(attempts.size() - 1).verdict();
  }

  private WebhookDelivery onlyDelivery() {
    List<WebhookDelivery> all = dispatcher.of(GrowLightApi.GROW, RUN);
    assertThat(all).hasSize(1);
    return all.get(0);
  }

  private GrowPaymentProcess onlyProcess() {
    assertThat(processes.all()).hasSize(1);
    return processes.all().get(0);
  }

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  private Map<String, String> aHold() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, "2");
    form.put(GrowFields.SUM, "120.50");
    form.put(GrowFields.SAVE_CARD_TOKEN, "1");
    form.put(GrowFields.DESCRIPTION, "A hold");
    form.put(GrowFields.SUCCESS_URL, "https://merchant.example/thanks");
    form.put(GrowFields.CANCEL_URL, "https://merchant.example/cancel");
    form.put(GrowFields.NOTIFY_URL, merchant.url());
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    form.put(GrowFields.EMAIL, "payer@merchant.example");
    form.put("cField1", "merchant-session-secret");
    form.put(GrowFields.PAYMENT_INTENT_FIELD, "intent-99");
    return form;
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

    MovableClock(Instant now) {
      this.now = now;
    }

    void moveTo(Instant instant) {
      this.now = instant;
    }

    @Override
    public Instant now(TestRunId testRunId) {
      return now;
    }
  }
}
