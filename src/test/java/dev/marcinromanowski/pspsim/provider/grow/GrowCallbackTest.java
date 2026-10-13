package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
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

class GrowCallbackTest {

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");
  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String SESSION_TOKEN = "merchant-session-secret";

  private final LocalMerchantServer merchant = new LocalMerchantServer();
  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(TestRunId.of("default"));
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          deliveries,
          new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(5), 512),
          audit,
          SimulatorDoubles.clockAt(NOW),
          ids,
          new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1))),
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
          SimulatorDoubles.callbacks(
              processes, transactions, accounts, dispatcher, SimulatorDoubles.clockAt(NOW), ids),
          SimulatorDoubles.clockAt(NOW),
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
  void a_merchant_reads_the_bytes_grow_publishes_brackets_and_spaces_included() {
    sendTheCallbackFor(aHold());

    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    assertThat(received.contentType()).isEqualTo("application/x-www-form-urlencoded");
    assertThat(received.body())
        .startsWith(
            "err=&status=1&data%5Bstatus%5D="
                + "%D7%A2%D7%A1%D7%A7%D7%94%20%D7%9E%D7%95%D7%A9%D7%94%D7%99%D7%AA"
                + "&data%5BstatusCode%5D=11&")
        .contains("data%5BcustomFields%5D%5BcField1%5D=merchant-session-secret")
        .contains("data%5BpaymentDate%5D=28%2F11%2F26")
        .contains("data%5BfullName%5D=Israel%20Israeli");
    assertThat(received.fields())
        .containsEntry("data[customFields][cField1]", SESSION_TOKEN)
        .containsEntry("data[customFields][cField2]", "intent-99")
        .containsEntry("data[status]", "עסקה מושהית");
  }

  @Test
  void spells_no_bracket_literally_and_no_space_as_a_plus_anywhere_in_the_body() {
    sendTheCallbackFor(aHold());

    assertThat(merchant.received().get(0).body())
        .doesNotContain("[")
        .doesNotContain("]")
        .doesNotContain("+");
  }

  @Test
  void sends_no_signature_header_because_grow_publishes_none() {
    sendTheCallbackFor(aHold());

    assertThat(merchant.received().get(0).headers().keySet())
        .doesNotContain("X-Gcs-Signature", "X-Simulator-Event-Id");
    assertThat(onlyCallbackAttempt().sanitizedHeaders())
        .containsExactly(Map.entry("Content-Type", List.of("application/x-www-form-urlencoded")));
  }

  @Test
  void encodes_a_value_exactly_as_a_form_body_is_read_back() {
    String everyKind = "AZaz09-_.*~ &=+%[]/:@";

    sendTheCallbackFor(aHold(Map.of(GrowFields.DESCRIPTION, everyKind)));

    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    assertThat(received.body())
        .contains("data%5Bdescription%5D=AZaz09-_.%2A~%20%26%3D%2B%25%5B%5D%2F%3A%40");
    assertThat(received.fields()).containsEntry("data[description]", everyKind);
  }

  @Test
  void carries_the_published_callback_fields_in_the_published_order() {
    sendTheCallbackFor(aHold());

    assertThat(merchant.received().get(0).fieldOrder())
        .containsExactly(
            "err",
            "status",
            "data[status]",
            "data[statusCode]",
            "data[sum]",
            "data[firstPaymentSum]",
            "data[periodicalPaymentSum]",
            "data[paymentsNum]",
            "data[allPaymentsNum]",
            "data[paymentDate]",
            "data[asmachta]",
            "data[description]",
            "data[fullName]",
            "data[payerPhone]",
            "data[payerEmail]",
            "data[cardToken]",
            "data[cardSuffix]",
            "data[cardType]",
            "data[cardTypeCode]",
            "data[cardBrand]",
            "data[cardBrandCode]",
            "data[cardExp]",
            "data[payerBankAccountDetails][bankNum]",
            "data[payerBankAccountDetails][branchNum]",
            "data[payerBankAccountDetails][accountNum]",
            "data[payerBankAccountDetails][accountName]",
            "data[customFields][cField1]",
            "data[customFields][cField2]",
            "data[transactionId]",
            "data[transactionToken]",
            "data[processId]",
            "data[processToken]");
  }

  @Test
  void carries_an_empty_err_beside_a_successful_status() {
    sendTheCallbackFor(aHold());

    Map<String, String> fields = merchant.received().get(0).fields();
    assertThat(fields).containsEntry("err", "").containsEntry("status", "1");
    assertThat(fields)
        .containsEntry("data[payerBankAccountDetails][bankNum]", "")
        .containsEntry("data[payerBankAccountDetails][accountName]", "");
  }

  @Test
  void reports_the_native_status_pair_growns_own_example_returns() {
    sendTheCallbackFor(aHold());

    assertThat(merchant.received().get(0).fields())
        .containsEntry("data[statusCode]", "11")
        .containsEntry("data[status]", "עסקה מושהית");
  }

  @Test
  void echoes_the_amount_in_the_exact_lexical_form_the_merchant_sent() {
    sendTheCallbackFor(aHold(Map.of(GrowFields.SUM, "0009.90")));

    assertThat(merchant.received().get(0).fields()).containsEntry("data[sum]", "0009.90");
  }

  @Test
  void echoes_the_payer_details_the_payment_process_carries() {
    sendTheCallbackFor(aHold());

    assertThat(merchant.received().get(0).fields())
        .containsEntry("data[description]", "A hold")
        .containsEntry("data[fullName]", "Israel Israeli")
        .containsEntry("data[payerPhone]", "0505503144")
        .containsEntry("data[payerEmail]", "payer@merchant.example");
  }

  @Test
  void dates_the_payment_by_the_simulator_clock() {
    sendTheCallbackFor(aHold());

    assertThat(merchant.received().get(0).fields()).containsEntry("data[paymentDate]", "28/11/26");
  }

  @Test
  void carries_a_card_token_only_when_the_process_asked_to_save_one() {
    Map<String, String> withoutSaving = aHold();
    withoutSaving.remove(GrowFields.SAVE_CARD_TOKEN);

    sendTheCallbackFor(withoutSaving);

    assertThat(merchant.received().get(0).fields()).doesNotContainKey("data[cardToken]");
  }

  @Test
  void carries_the_saved_card_token_itself_when_the_process_asked_to_save_one() {
    sendTheCallbackFor(aHold());

    GrowPaymentProcess process = onlyProcess();
    String savedCardToken = process.outcome().cardToken();
    assertThat(savedCardToken)
        .as(
            "the saved card token is neither the process token nor the hosted link of the same shape")
        .isNotIn(process.processToken(), process.hostedLink());
    assertThat(merchant.received().get(0).fields())
        .containsEntry("data[cardToken]", savedCardToken);
  }

  @Test
  void echoes_only_the_custom_fields_the_merchant_actually_sent() {
    Map<String, String> withoutCustomFields = aHold();
    withoutCustomFields.remove("cField1");
    withoutCustomFields.remove(GrowFields.PAYMENT_INTENT_FIELD);

    sendTheCallbackFor(withoutCustomFields);

    assertThat(merchant.received().get(0).fields().keySet())
        .noneMatch(key -> key.startsWith("data[customFields]"));
  }

  @Test
  void a_completed_process_carrying_the_payment_intent_field_is_ready_for_capture() {
    sendTheCallbackFor(aHold());

    GrowPaymentProcess process = onlyProcess();
    assertThat(process.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(process.nativeStatusCode()).isEqualTo("11");
    assertThat(process.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void a_token_only_process_completes_into_a_registered_card_that_holds_nothing() {
    Map<String, String> registration = aHold();
    registration.remove(GrowFields.PAYMENT_INTENT_FIELD);
    registration.put(GrowFields.CHARGE_TYPE, GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    registration.put(GrowFields.SUM, "1");

    sendTheCallbackFor(registration);

    assertThat(onlyProcess().normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_failed_process_reports_growns_own_published_callback_failure() {
    String processId = createTheProcess(aHold());

    provider.schedule(
        new WebhookScheduleRequest(
            RUN, processId, GrowCallbacks.EVENT_FAILED, null, null, ObjectOrigin.CONTROL_API));

    assertThat(merchant.received().get(0).fields())
        .containsExactly(
            Map.entry("status", "0"),
            Map.entry("err[id]", "701"),
            Map.entry("err[message]", "פרמטר קוד זיהוי אינו תקין userId"),
            Map.entry("data", ""));
    assertThat(merchant.received().get(0).body())
        .isEqualTo(
            "status=0&err%5Bid%5D=701&err%5Bmessage%5D="
                + "%D7%A4%D7%A8%D7%9E%D7%98%D7%A8%20%D7%A7%D7%95%D7%93"
                + "%20%D7%96%D7%99%D7%94%D7%95%D7%99"
                + "%20%D7%90%D7%99%D7%A0%D7%95%20%D7%AA%D7%A7%D7%99%D7%9F%20userId&data=");
    GrowPaymentProcess process = onlyProcess();
    assertThat(process.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
    assertThat(process.nativeStatusCode()).isEqualTo("0");
  }

  @Test
  void names_a_completed_callback_after_the_outcome_it_reports() {
    send(createTheProcess(aHold()), GrowCallbacks.EVENT_COMPLETED, null);

    assertThat(onlyDelivery().eventType()).isEqualTo("payment_process.completed");
  }

  @Test
  void names_a_failed_callback_after_the_outcome_it_reports() {
    send(createTheProcess(aHold()), GrowCallbacks.EVENT_FAILED, null);

    assertThat(onlyDelivery().eventType()).isEqualTo("payment_process.failed");
  }

  @Test
  void a_second_delivery_of_a_failed_process_is_the_same_message_as_the_first() {
    String processId = createTheProcess(aHold());
    ScheduledWebhook first = send(processId, GrowCallbacks.EVENT_FAILED, null);

    ScheduledWebhook duplicate = send(processId, GrowCallbacks.EVENT_FAILED, first.eventId());

    assertThat(duplicate.eventId()).isEqualTo(first.eventId());
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body()).isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void a_second_delivery_of_one_process_is_the_same_message_as_the_first() {
    String processId = createTheProcess(aHold());
    ScheduledWebhook first = send(processId, null, null);

    ScheduledWebhook duplicate = send(processId, null, first.eventId());

    assertThat(duplicate.eventId()).isEqualTo(first.eventId());
    assertThat(duplicate.webhookJobId()).isNotEqualTo(first.webhookJobId());
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body()).isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void two_requests_reaching_one_outcome_at_once_report_the_same_transaction() {
    String processId = createTheProcess(aHold());
    processes.whileReachingAnOutcome(() -> send(processId, null, null));

    send(processId, null, null);

    assertThat(processes.all()).hasSize(1);
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body()).isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void a_process_reset_away_mid_request_is_unknown_rather_than_reported() {
    String processId = createTheProcess(aHold());
    processes.whileReachingAnOutcome(() -> processes.clear(RUN));

    assertThatThrownBy(() -> send(processId, null, null))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessageContaining("providerObjectId");
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void refuses_to_report_a_second_different_outcome_for_one_process() {
    String processId = createTheProcess(aHold());
    send(processId, null, null);

    assertThatThrownBy(() -> send(processId, GrowCallbacks.EVENT_FAILED, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("already reached a different outcome");
  }

  @Test
  void refuses_an_outcome_name_grow_has_no_callback_for() {
    String processId = createTheProcess(aHold());

    assertThatThrownBy(() -> send(processId, "payment_process.captured", null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("eventType");
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void refuses_to_send_a_callback_for_a_process_that_does_not_exist_in_this_test_run() {
    createTheProcess(aHold());

    assertThatThrownBy(() -> send("999999", null, null))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessageContaining("providerObjectId");
  }

  @Test
  void refuses_a_notify_url_the_test_run_did_not_configure() {
    String processId =
        createTheProcess(aHold(Map.of(GrowFields.NOTIFY_URL, "https://elsewhere.example/hook")));

    assertThatThrownBy(() -> send(processId, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("not among the callback targets configured");
    assertThat(merchant.received()).isEmpty();
    assertThat(dispatcher.of(GrowLightApi.GROW, RUN)).isEmpty();
  }

  @Test
  void a_callback_refused_for_want_of_a_target_leaves_the_process_completable_once_one_exists() {
    String processId = createTheProcess(aHold());
    accounts.forgetTargets(RUN);
    assertThatThrownBy(() -> send(processId, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class);
    assertThat(onlyProcess().outcome()).isNull();

    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, merchant.url()));
    send(processId, null, null);

    assertThat(merchant.received()).hasSize(1);
    assertThat(onlyProcess().outcome()).isNotNull();
  }

  @Test
  void accepts_a_notify_url_the_test_run_did_not_configure_when_the_process_is_created() {
    GrowApiResponse response =
        api()
            .createPaymentProcess(
                call(aHold(Map.of(GrowFields.NOTIFY_URL, "https://elsewhere.example/hook"))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  @Test
  void falls_back_to_the_single_target_configured_for_the_merchant() {
    Map<String, String> withoutNotifyUrl = aHold();
    withoutNotifyUrl.remove(GrowFields.NOTIFY_URL);

    sendTheCallbackFor(withoutNotifyUrl);

    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void does_not_fall_back_to_a_target_configured_for_another_merchant() {
    accounts.withTarget(
        RUN, new WebhookTarget("merchant-2", "https://another-business.example/hook"));
    Map<String, String> withoutNotifyUrl = aHold();
    withoutNotifyUrl.remove(GrowFields.NOTIFY_URL);

    sendTheCallbackFor(withoutNotifyUrl);

    assertThat(merchant.received()).hasSize(1);
    assertThat(onlyDelivery().targetUrl()).isEqualTo(merchant.url());
  }

  @Test
  void refuses_to_choose_between_two_configured_targets_for_a_process_with_no_notify_url() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, "https://second.example/hook"));
    Map<String, String> withoutNotifyUrl = aHold();
    withoutNotifyUrl.remove(GrowFields.NOTIFY_URL);
    String processId = createTheProcess(withoutNotifyUrl);

    assertThatThrownBy(() -> send(processId, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("no single configured callback target");
  }

  @Test
  void a_merchant_that_answers_2xx_acknowledges_the_callback() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(204, ""));

    sendTheCallbackFor(aHold());

    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void a_merchant_that_answers_just_outside_the_successful_range_has_not_acknowledged() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(300, "look elsewhere"));

    sendTheCallbackFor(aHold());

    assertThat(onlyDelivery().state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void a_merchant_that_refuses_leaves_the_callback_queued_for_another_attempt() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));

    sendTheCallbackFor(aHold());

    WebhookDelivery delivery = onlyDelivery();
    assertThat(delivery.state()).isEqualTo(DeliveryState.PENDING);
    assertThat(delivery.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
  }

  @Test
  void echoes_the_merchant_correlation_token_on_the_wire_and_redacts_it_in_audit() {
    sendTheCallbackFor(aHold());

    assertThat(merchant.received().get(0).fields())
        .containsEntry("data[customFields][cField1]", SESSION_TOKEN);

    AuditRecord attempt = onlyCallbackAttempt();
    assertThat(attempt.sanitizedBody())
        .containsEntry("data[customFields][cField1]", List.of(Redaction.REDACTED))
        .containsEntry("data[processToken]", List.of(Redaction.REDACTED))
        .containsEntry("data[transactionToken]", List.of(Redaction.REDACTED))
        .containsEntry("data[cardToken]", List.of(Redaction.REDACTED))
        .containsEntry("data[customFields][cField2]", List.of("intent-99"))
        .containsEntry("data[statusCode]", List.of("11"));
    assertThat(attempt.toString()).doesNotContain(SESSION_TOKEN);
  }

  @Test
  void audits_the_attempt_against_the_process_it_reports_on() {
    String processId = createTheProcess(aHold());
    send(processId, null, null);

    AuditRecord attempt = onlyCallbackAttempt();
    assertThat(attempt.providerObjectId()).isEqualTo(processId);
    assertThat(attempt.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(attempt.httpStatus()).isEqualTo(200);
    assertThat(attempt.attempt()).isEqualTo(1);
  }

  @Test
  void a_reset_clears_the_callbacks_of_the_test_run_it_names() {
    sendTheCallbackFor(aHold());

    assertThat(provider.reset(RUN, ResetScope.TEST_RUN)).isEqualTo(new ResetOutcome(3, 3));
    assertThat(dispatcher.of(GrowLightApi.GROW, RUN)).isEmpty();
  }

  @Test
  void a_reset_of_every_test_run_clears_the_callbacks_too() {
    sendTheCallbackFor(aHold());

    assertThat(provider.reset(RUN, ResetScope.ALL)).isEqualTo(new ResetOutcome(3, 3));
    assertThat(dispatcher.of(GrowLightApi.GROW, RUN)).isEmpty();
  }

  private void sendTheCallbackFor(Map<String, String> form) {
    send(createTheProcess(form), null, null);
  }

  private ScheduledWebhook send(String processId, String eventType, String reuseEventId) {
    List<ScheduledWebhook> scheduled =
        provider.schedule(
            new WebhookScheduleRequest(
                RUN, processId, eventType, reuseEventId, null, ObjectOrigin.CONTROL_API));
    assertThat(scheduled).hasSize(1);
    return scheduled.get(0);
  }

  private String createTheProcess(Map<String, String> form) {
    GrowApiResponse response = api().createPaymentProcess(call(form));
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    return String.valueOf(response.providerObjectId());
  }

  private GrowLightApi api() {
    accounts.with(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, merchant.url()));
    testRuns.register(RUN);
    return new GrowLightApi(
        accounts,
        testRuns,
        audit,
        SimulatorDoubles.clockAt(NOW),
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

  private GrowPaymentProcess onlyProcess() {
    assertThat(processes.all()).hasSize(1);
    return processes.all().get(0);
  }

  private WebhookDelivery onlyDelivery() {
    List<WebhookDelivery> all = dispatcher.of(GrowLightApi.GROW, RUN);
    assertThat(all).hasSize(1);
    return all.get(0);
  }

  private AuditRecord onlyCallbackAttempt() {
    List<AuditRecord> attempts =
        audit.of(GrowLightApi.GROW, RUN).stream()
            .filter(r -> r.direction() == AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT)
            .toList();
    assertThat(attempts).hasSize(1);
    return attempts.get(0);
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
    form.put(GrowFields.EMAIL, "payer@merchant.example");
    form.put("cField1", SESSION_TOKEN);
    form.put(GrowFields.PAYMENT_INTENT_FIELD, "intent-99");
    return form;
  }

  private Map<String, String> aHold(Map<String, String> overrides) {
    Map<String, String> form = aHold();
    form.putAll(overrides);
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
}
