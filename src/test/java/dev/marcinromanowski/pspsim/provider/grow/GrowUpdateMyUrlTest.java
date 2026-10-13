package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.kernel.webhook.internal.HttpMerchantEndpoint;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrowUpdateMyUrlTest {

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String UPDATE_MY_URL = "/api/light/server/1.0/updateMyUrl/";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private final LocalMerchantServer merchant = new LocalMerchantServer();
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
          new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(5), 512),
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
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, merchant.url()));
    testRuns.register(RUN);
  }

  @AfterEach
  void stopTheMerchant() {
    merchant.close();
  }

  @Test
  void a_tester_triggers_a_test_callback_through_the_vendors_own_endpoint() {
    aDeliveredCallback();

    GrowApiResponse response = updateMyUrl(merchant.url());

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).isEqualTo("{\"status\":1,\"err\":\"\",\"data\":\"\"}");
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body()).isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void the_test_callback_names_the_process_it_reports_on() {
    String processId = aDeliveredCallback();

    assertThat(updateMyUrl(merchant.url()).providerObjectId()).isEqualTo(processId);
  }

  @Test
  void resends_the_newest_callback_this_simulator_addressed_to_that_url() {
    aDeliveredCallback();
    String second = aDeliveredCallback();

    updateMyUrl(merchant.url());

    assertThat(merchant.received()).hasSize(3);
    assertThat(merchant.received().get(2).fields()).containsEntry("data[processId]", second);
  }

  @Test
  void answers_54_naming_the_url_a_tester_did_not_send() {
    GrowApiResponse response = api.updateMyUrl(get(UPDATE_MY_URL, Map.of()));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":54,\"message\":\"חסרים נתונים:url\"},\"data\":\"\"}");
  }

  @Test
  void refuses_a_url_this_simulator_has_addressed_no_callback_to() {
    aDeliveredCallback();

    assertThatThrownBy(() -> updateMyUrl("https://elsewhere.example/hook"))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("addressed no grow callback to that url");
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void audits_the_test_callback_as_an_inbound_request_and_an_outbound_response() {
    aDeliveredCallback();

    updateMyUrl(merchant.url());

    assertThat(audit.all().stream().filter(r -> r.path().equals(UPDATE_MY_URL)).toList())
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
  }

  private String aDeliveredCallback() {
    GrowApiResponse created = api.createPaymentProcess(post(CREATE, aHold()));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    String processId = String.valueOf(created.providerObjectId());
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    return processId;
  }

  private GrowApiResponse updateMyUrl(String url) {
    return api.updateMyUrl(get(UPDATE_MY_URL, Map.of(GrowFields.URL, url)));
  }

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
    form.put(GrowFields.PAYMENT_INTENT_FIELD, "intent-99");
    return form;
  }

  private static GrowApiCall post(String path, Map<String, String> form) {
    return new GrowApiCall(
        "POST",
        path,
        Map.of("Content-Type", List.of("multipart/form-data; boundary=x")),
        fieldsOf(form));
  }

  private static GrowApiCall get(String path, Map<String, String> query) {
    return new GrowApiCall(
        "GET", path, Map.of(TestRunHeader.NAME, List.of(RUN.value())), fieldsOf(query));
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static String bodyOf(GrowApiResponse response) {
    return GrowWireText.readable(new String(response.body(), UTF_8));
  }
}
