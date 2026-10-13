package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.kernel.webhook.ProviderRetryPlans;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
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

class GrowCallbackRetryPlanTest {

  private static final TestRunId MINE = TestRunId.of("e2e-run-123");

  private static final TestRunId SOMEBODY_ELSES = TestRunId.of("e2e-run-456");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String MERCHANT = "merchant-1";

  private static final Duration LONGER_THAN_GROWS_WHOLE_SCHEDULE = Duration.ofMinutes(10);

  private static final RetryPlan WHAT_THE_SIMULATOR_SHIPS =
      new RetryPlan(
          List.of(
              Duration.ZERO,
              Duration.ofSeconds(1),
              Duration.ofSeconds(5),
              Duration.ofSeconds(30),
              Duration.ofMinutes(2)));

  private final LocalMerchantServer merchant = new LocalMerchantServer();
  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(TestRunId.of("default"));
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final InMemoryTestRunSettings settings = new InMemoryTestRunSettings();
  private final MovableClock clock = new MovableClock(NOW);

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          deliveries,
          new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(5), 512),
          audit,
          clock,
          ids,
          new ProviderRetryPlans(
              new DeclaredProviderSettings(List.of(GrowSettings.DECLARED)),
              settings,
              WHAT_THE_SIMULATOR_SHIPS),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

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
  void a_merchant_refusing_every_attempt_is_called_the_six_times_grow_publishes() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(500, "no thanks"));

    sendTheCallbackOf(MINE, "grow-user-1", "grow-page-1");
    drainTheQueueOf(MINE);

    WebhookDelivery delivery = onlyDeliveryOf(MINE);
    assertThat(delivery.attemptsMade()).isEqualTo(6);
    assertThat(delivery.state()).isEqualTo(DeliveryState.EXHAUSTED);
    assertThat(merchant.received()).hasSize(6);
  }

  @Test
  void a_harness_setting_grows_retry_plan_for_its_own_run_leaves_another_run_on_grows_six() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(500, "no thanks"));
    settings.merge(
        GrowLightApi.GROW, MINE, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S,PT5S"));

    sendTheCallbackOf(MINE, "grow-user-1", "grow-page-1");
    sendTheCallbackOf(SOMEBODY_ELSES, "grow-user-2", "grow-page-2");
    drainTheQueueOf(MINE);
    drainTheQueueOf(SOMEBODY_ELSES);

    assertThat(onlyDeliveryOf(MINE).attemptsMade()).isEqualTo(3);
    assertThat(onlyDeliveryOf(SOMEBODY_ELSES).attemptsMade()).isEqualTo(6);
  }

  private void sendTheCallbackOf(TestRunId testRunId, String userId, String pageCode) {
    String processId = createTheProcess(testRunId, userId, pageCode);
    List<ScheduledWebhook> scheduled =
        provider.schedule(
            new WebhookScheduleRequest(
                testRunId, processId, null, null, null, ObjectOrigin.CONTROL_API));
    assertThat(scheduled).hasSize(1);
  }

  private void drainTheQueueOf(TestRunId testRunId) {
    for (long second = 0; second <= LONGER_THAN_GROWS_WHOLE_SCHEDULE.toSeconds(); second++) {
      clock.moveTo(NOW.plusSeconds(second));
      dispatcher.deliverDue(GrowLightApi.GROW, testRunId);
    }
    clock.moveTo(NOW);
  }

  private String createTheProcess(TestRunId testRunId, String userId, String pageCode) {
    accounts.with(SimulatorDoubles.account(testRunId, MERCHANT, userId, pageCode));
    accounts.withTarget(testRunId, new WebhookTarget(MERCHANT, merchant.url()));
    testRuns.register(testRunId);
    GrowApiResponse response = api().createPaymentProcess(call(aHold(userId, pageCode)));
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    return String.valueOf(response.providerObjectId());
  }

  private WebhookDelivery onlyDeliveryOf(TestRunId testRunId) {
    List<WebhookDelivery> all = dispatcher.of(GrowLightApi.GROW, testRunId);
    assertThat(all).hasSize(1);
    return all.get(0);
  }

  private GrowLightApi api() {
    return new GrowLightApi(
        accounts,
        testRuns,
        audit,
        clock,
        ids,
        TestRunId.of("default"),
        new CreatePaymentProcess(
            processes, ids, "https://simulator.internal", scenarioEngine, GrowScalarType.INTEGER),
        new CreateTransactionWithToken(
            processes, transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
        new ApproveTransaction(processes, dispatcher, scenarioEngine, GrowScalarType.INTEGER),
        new SettleSuspendedTransaction(transactions, scenarioEngine, GrowScalarType.INTEGER),
        new RefundTransaction(transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
        new GetTransactionInfo(transactions, processes, scenarioEngine, GrowScalarType.INTEGER),
        new GetPaymentProcessInfo(processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
        new GetTokenTransactionsByExternalIdentifiers(
            processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
        new CancelBitTransaction(GrowScalarType.INTEGER),
        new UpdateMyUrl(dispatcher, GrowScalarType.INTEGER));
  }

  private Map<String, String> aHold(String userId, String pageCode) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, pageCode);
    form.put(GrowFields.USER_ID, userId);
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
    form.put(GrowFields.PAYMENT_INTENT_FIELD, "intent-99");
    return form;
  }

  private static GrowApiCall call(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return new GrowApiCall(
        "POST",
        "/api/light/server/1.0/createPaymentProcess",
        Map.of("Content-Type", List.of("multipart/form-data; boundary=x")),
        fields);
  }

  private static final class MovableClock implements SimulatorClock {

    private volatile Instant now;

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
