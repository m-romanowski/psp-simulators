package dev.marcinromanowski.pspsim.provider.grow;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrowTransportFaultTest {

  private static final String GATEWAY_CONTENT_TYPE = "text/plain; charset=utf-8";

  private static final String GATEWAY_502 =
      "502 was answered in front of the provider. These bytes are the gateway's and not the"
          + " provider's, and whether the provider processed the request cannot be read from this"
          + " answer.";

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String NOTIFY_URL = "https://merchant.example/callbacks/grow";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String TRANSACTION_INFO = "/api/light/server/1.0/getTransactionInfo";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String APPROVE = "/api/light/server/1.0/approveTransaction";

  private static final String HELD = "120.50";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final PinnedClock clock = new PinnedClock();

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
  void configureTheMerchantAccount() {
    accounts.with(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE, Map.of()));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, NOTIFY_URL));
    testRuns.register(RUN);
  }

  @Test
  void a_capture_the_merchant_never_learns_of_is_still_a_capture_in_the_ledger() {
    GrowTransaction hold = aHold();
    ScenarioRule rule = faulting(new TransportFault.ServerError(502));

    GrowApiResponse response = settle(hold);

    assertThat(response.httpStatus()).isEqualTo(502);
    assertThat(response.contentType()).isEqualTo(GATEWAY_CONTENT_TYPE);
    assertThat(new String(response.body(), UTF_8)).isEqualTo(GATEWAY_502);
    assertThat(response.declaredContentLength()).isEqualTo(GATEWAY_502.getBytes(UTF_8).length);
    assertThat(response.completesTheBody()).isTrue();
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);

    ProviderObjectState captured = stateOf(hold);
    assertThat(captured.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION, OperationKind.CAPTURE);
    assertThat(balancesOf(captured).capturedAmount().value()).isEqualTo(HELD);

    AuditRecord outbound = lastAudit();
    assertThat(outbound.httpStatus()).isEqualTo(502);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.scenarioId()).isEqualTo(rule.scenarioId());
    assertThat(outbound.sanitizedHeaders())
        .containsExactly(Map.entry("Content-Type", List.of(GATEWAY_CONTENT_TYPE)));
    assertThat(outbound.sanitizedBody()).containsEntry("data.statusCode", List.of("2"));
  }

  @Test
  void a_capture_answered_instead_of_being_run_leaves_the_hold_where_the_payer_left_it() {
    GrowTransaction hold = aHold();
    ScenarioRule rule = answering(502);

    GrowApiResponse response = settle(hold);

    assertThat(response.httpStatus()).isEqualTo(502);
    assertThat(response.contentType()).isEqualTo(GATEWAY_CONTENT_TYPE);
    assertThat(new String(response.body(), UTF_8)).isEqualTo(GATEWAY_502);
    assertThat(response.declaredContentLength()).isEqualTo(GATEWAY_502.getBytes(UTF_8).length);
    assertThat(response.completesTheBody()).isTrue();
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);

    ProviderObjectState untouched = stateOf(hold);
    assertThat(untouched.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION);
    assertThat(balancesOf(untouched).capturedAmount().value()).isEqualTo("0");
    assertThat(untouched.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(lastAudit().scenarioId()).isEqualTo(rule.scenarioId());
  }

  @Test
  void a_repeated_capture_the_simulator_never_ran_is_released_rather_than_refused_as_released() {
    GrowTransaction hold = aHold();
    ScenarioRule unreached = answering(502);

    settle(hold);
    scenarioRules.remove(GrowLightApi.GROW, RUN, unreached.scenarioId());
    GrowApiResponse repeated = settle(hold);

    assertThat(repeated.httpStatus()).isEqualTo(200);
    assertThat(repeated.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(repeated)).contains("\"status\":1").doesNotContain("808");
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo(HELD);
  }

  @Test
  void an_audit_reader_tells_a_lost_response_from_a_request_the_operation_never_saw() {
    GrowTransaction damaged = aHold();
    faulting(new TransportFault.ServerError(502));
    GrowApiResponse lost = settle(damaged);
    AuditRecord afterTheOperation = lastAudit();

    GrowTransaction untouched = aHold();
    scenarioRules.clear(GrowLightApi.GROW, RUN);
    answering(502);
    GrowApiResponse unreached = settle(untouched);
    AuditRecord insteadOfTheOperation = lastAudit();

    assertThat(unreached.httpStatus()).isEqualTo(lost.httpStatus());
    assertThat(unreached.contentType()).isEqualTo(lost.contentType());
    assertThat(unreached.body()).isEqualTo(lost.body());
    assertThat(unreached.declaredContentLength()).isEqualTo(lost.declaredContentLength());
    assertThat(unreached.delay()).isEqualTo(lost.delay());
    assertThat(balancesOf(stateOf(damaged)).capturedAmount().value()).isEqualTo(HELD);
    assertThat(balancesOf(stateOf(untouched)).capturedAmount().value()).isEqualTo("0");
    assertThat(afterTheOperation.delivered().transportFault())
        .isEqualTo(
            new AppliedFault(TransportFaultKind.SERVER_ERROR, FaultPlacement.AFTER_THE_OPERATION));
    assertThat(insteadOfTheOperation.delivered().transportFault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.SERVER_ERROR, FaultPlacement.INSTEAD_OF_THE_OPERATION));
    assertThat(afterTheOperation.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(insteadOfTheOperation.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(afterTheOperation.sanitizedBody()).containsEntry("data.statusCode", List.of("2"));
    assertThat(insteadOfTheOperation.sanitizedBody()).isEmpty();
    assertThat(insteadOfTheOperation.providerObjectId()).isNull();
  }

  @Test
  void a_client_tells_a_capture_the_provider_refused_from_one_it_never_reached() {
    GrowTransaction hold = aHold();
    declining();
    GrowApiResponse refused = settle(hold);
    AuditRecord refusedRow = lastAudit();

    scenarioRules.clear(GrowLightApi.GROW, RUN);
    answering(502);
    GrowApiResponse unreached = settle(hold);
    AuditRecord unreachedRow = lastAudit();

    assertThat(refused.httpStatus()).isEqualTo(200);
    assertThat(unreached.httpStatus()).isEqualTo(502);
    assertThat(refused.contentType()).isEqualTo("text/html; charset=UTF-8");
    assertThat(unreached.contentType()).isEqualTo(GATEWAY_CONTENT_TYPE);
    assertThat(bodyOf(refused)).contains("\"status\":0").contains("\"id\":803");
    assertThat(new String(unreached.body(), UTF_8)).isEqualTo(GATEWAY_502);
    assertThat(new String(unreached.body(), UTF_8)).doesNotContain("\"status\"");
    assertThat(refused.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(unreached.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(refusedRow.sanitizedBody()).containsEntry("err.id", List.of("803"));
    assertThat(unreachedRow.sanitizedBody()).isEmpty();
    assertThat(refusedRow.delivered().transportFault()).isNull();
    assertThat(unreachedRow.delivered().transportFault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.SERVER_ERROR, FaultPlacement.INSTEAD_OF_THE_OPERATION));
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo("0");
  }

  @Test
  void a_step_that_answers_instead_of_the_operation_stands_aside_once_it_has_moved_the_story_on() {
    GrowTransaction hold = aHold();
    scenarioRules.addAll(
        List.of(
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                new ScenarioStory(
                    "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "unreached")),
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                new ScenarioStory("capture-retries", StoryKeying.FLOW, "unreached", null))));

    GrowApiResponse unreached = settle(hold);
    GrowApiResponse released = settle(hold);

    assertThat(unreached.httpStatus()).isEqualTo(502);
    assertThat(released.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(released)).contains("\"status\":1");
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo(HELD);
  }

  @Test
  void an_answer_in_place_of_the_operation_is_no_reason_for_the_provider_to_refuse_a_scenario() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        GrowLightApi.GROW,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
                        new ScenarioAction.TransportFaultInsteadOfOperation(
                            new TransportFault.ServerError(502)),
                        null)))
        .isEmpty();
  }

  @Test
  void a_truncated_capture_response_carries_the_opening_bytes_of_the_envelope_and_no_more() {
    GrowTransaction hold = aHold();
    GrowApiResponse whole = settle(aHold());
    faulting(new TransportFault.TruncatedBody());

    GrowApiResponse response = settle(hold);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.contentType()).isEqualTo("text/html; charset=UTF-8");
    assertThat(bodyOf(response)).startsWith("{\"status\":1,\"err\":\"\",\"data\":{\"asmachta\"");
    assertThat(bodyOf(response)).doesNotEndWith("}");
    assertThat(response.body().length).isEqualTo(whole.body().length / 2);
    assertThat(response.declaredContentLength()).isEqualTo(response.body().length);
    assertThat(response.completesTheBody()).isTrue();
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo(HELD);
    assertThat(lastAudit().httpStatus()).isEqualTo(200);
  }

  @Test
  void a_corrupted_capture_response_is_as_long_as_the_rendered_one_and_no_longer_closes() {
    GrowApiResponse valid = settle(aHold());

    GrowTransaction hold = aHold();
    faulting(new TransportFault.CorruptedBody());
    GrowApiResponse corrupted = settle(hold);

    assertThat(bodyOf(valid)).endsWith("}");
    assertThat(bodyOf(corrupted)).doesNotEndWith("}");
    assertThat(corrupted.body()).hasSameSizeAs(valid.body());
    assertThat(corrupted.declaredContentLength()).isEqualTo(corrupted.body().length);
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo(HELD);
  }

  @Test
  void a_dropped_capture_response_promises_more_bytes_than_it_writes() {
    GrowTransaction hold = aHold();
    faulting(new TransportFault.DroppedConnection());

    GrowApiResponse response = settle(hold);

    assertThat(response.completesTheBody()).isFalse();
    assertThat(response.declaredContentLength()).isGreaterThan(response.body().length);
    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo(HELD);
  }

  @Test
  void a_dropped_capture_response_ends_the_connection_on_the_wire_and_says_so_in_the_audit() {
    GrowTransaction hold = aHold();
    faulting(new TransportFault.DroppedConnection());

    GrowApiResponse response = settle(hold);

    assertThat(response.headers())
        .containsEntry(DeliveredHeaders.CONNECTION, List.of(DeliveredHeaders.CLOSE));
    assertThat(lastAudit().sanitizedHeaders())
        .containsEntry(DeliveredHeaders.CONNECTION, List.of(DeliveredHeaders.CLOSE));
  }

  @Test
  void a_whole_capture_response_ends_no_connection_on_the_wire_and_says_so_in_the_audit() {
    GrowTransaction hold = aHold();

    GrowApiResponse response = settle(hold);

    assertThat(response.completesTheBody()).isTrue();
    assertThat(response.headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(DeliveredHeaders.CONNECTION));
    assertThat(lastAudit().sanitizedHeaders())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(DeliveredHeaders.CONNECTION));
  }

  @Test
  void a_delayed_capture_response_is_the_whole_rendered_answer_and_names_how_long_it_waits() {
    GrowTransaction hold = aHold();
    faulting(new TransportFault.DelayedResponse(Duration.ofSeconds(3)));

    GrowApiResponse response = settle(hold);

    assertThat(response.delay()).isEqualTo(Duration.ofSeconds(3));
    assertThat(response.completesTheBody()).isTrue();
    assertThat(bodyOf(response)).startsWith("{\"status\":1").endsWith("}");
    assertThat(balancesOf(stateOf(hold)).capturedAmount().value()).isEqualTo(HELD);
  }

  @Test
  void a_response_no_fault_touches_is_delivered_whole_and_at_once() {
    GrowApiResponse response = settle(aHold());

    assertThat(response.delay()).isEqualTo(Duration.ZERO);
    assertThat(response.completesTheBody()).isTrue();
    assertThat(response.contentType()).isEqualTo("text/html; charset=UTF-8");
    assertThat(lastAudit().sanitizedHeaders())
        .containsEntry("Content-Type", List.of("text/html; charset=UTF-8"));
  }

  @Test
  void a_faulting_step_of_a_story_stands_aside_once_it_has_moved_the_story_on() {
    scenarioRules.addAll(
        List.of(
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest().naming(GrowOperations.GET_TRANSACTION_INFO),
                new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(503)),
                new ScenarioStory(
                    "lookup-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "lost")),
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest().naming(GrowOperations.GET_TRANSACTION_INFO),
                new ScenarioAction.FixedSuccess(),
                new ScenarioStory("lookup-retries", StoryKeying.FLOW, "lost", null))));

    GrowTransaction hold = aHold();
    GrowApiResponse lost = lookUpTheTransaction(hold);
    GrowApiResponse answered = lookUpTheTransaction(hold);

    assertThat(lost.httpStatus()).isEqualTo(503);
    assertThat(new String(lost.body(), UTF_8))
        .startsWith("503 was answered in front of the provider.");
    assertThat(lost.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(answered)).contains("\"transactionId\"");
    assertThat(answered.providerObjectId()).isEqualTo(hold.transactionId());
  }

  @Test
  void a_transport_fault_is_no_reason_for_the_provider_to_refuse_a_scenario() {
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        GrowLightApi.GROW,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.TransportFaultAlways(
                            new TransportFault.ServerError(504)),
                        null)))
        .isEmpty();
    assertThat(
            provider
                .vocabulary()
                .refusals(
                    new NewScenarioRule(
                        GrowLightApi.GROW,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.TransportFaultAlways(new TransportFault.TruncatedBody()),
                        new ScenarioStory(
                            "hold-retries",
                            StoryKeying.FLOW,
                            ScenarioStory.INITIAL_STATE,
                            "lost"))))
        .isEmpty();
  }

  @Test
  void a_refund_is_subject_to_a_rule_and_a_fault_damages_only_its_delivery() {
    GrowTransaction hold = aHold();
    settle(hold);
    ScenarioRule rule = faulting(new TransportFault.ServerError(504));

    GrowApiResponse response = refund(hold);

    assertThat(response.httpStatus()).isEqualTo(504);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(lastAudit().scenarioId()).isEqualTo(rule.scenarioId());
    assertThat(balancesOf(stateOf(hold)).refundedAmount().value()).isEqualTo(HELD);
  }

  @Test
  void a_refund_a_rule_declines_never_reaches_the_ledger() {
    GrowTransaction hold = aHold();
    settle(hold);
    declining();

    GrowApiResponse response = refund(hold);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).contains("\"id\":803");
    assertThat(balancesOf(stateOf(hold)).refundedAmount().value()).isEqualTo("0");
  }

  @Test
  void a_transaction_lookup_is_subject_to_a_rule_and_a_fault_damages_only_its_delivery() {
    GrowTransaction hold = aHold();
    ScenarioRule rule = faulting(new TransportFault.TruncatedBody());

    GrowApiResponse response = lookUpTheTransaction(hold);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).doesNotEndWith("}");
    assertThat(lastAudit().scenarioId()).isEqualTo(rule.scenarioId());
  }

  @Test
  void a_transaction_lookup_a_rule_declines_answers_the_declined_code() {
    GrowTransaction hold = aHold();
    declining();

    GrowApiResponse response = lookUpTheTransaction(hold);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).contains("\"id\":803");
  }

  @Test
  void a_process_lookup_is_subject_to_a_rule_and_a_fault_damages_only_its_delivery() {
    aHold();
    GrowPaymentProcess process = processes.all().get(0);
    ScenarioRule rule = faulting(new TransportFault.CorruptedBody());

    GrowApiResponse response = lookUpTheProcess(process);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response)).doesNotEndWith("}");
    assertThat(lastAudit().scenarioId()).isEqualTo(rule.scenarioId());
  }

  @Test
  void a_process_lookup_a_rule_declines_answers_the_declined_code() {
    aHold();
    GrowPaymentProcess process = processes.all().get(0);
    declining();

    GrowApiResponse response = lookUpTheProcess(process);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).contains("\"id\":803");
  }

  @Test
  void an_acknowledgement_is_subject_to_a_rule_and_a_fault_damages_only_its_delivery() {
    GrowTransaction hold = aHold();
    ScenarioRule rule = faulting(new TransportFault.DroppedConnection());

    GrowApiResponse response = approve(hold);

    assertThat(response.completesTheBody()).isFalse();
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(lastAudit().scenarioId()).isEqualTo(rule.scenarioId());
  }

  @Test
  void an_acknowledgement_a_rule_declines_answers_the_declined_code() {
    GrowTransaction hold = aHold();
    declining();

    GrowApiResponse response = approve(hold);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).contains("\"id\":803");
  }

  @Test
  void a_fault_that_damaged_a_failure_the_provider_owns_still_names_the_scenario_that_damaged_it() {
    GrowTransaction hold = aHold();
    ScenarioRule rule = faulting(new TransportFault.TruncatedBody());
    transactions.whileAppending(() -> transactions.clear(RUN));

    GrowApiResponse response = settle(hold);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).doesNotEndWith("}");
    assertThat(lastAudit().scenarioId()).isEqualTo(rule.scenarioId());
    assertThat(lastAudit().delivered().transportFault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION));
  }

  @Test
  void a_provider_failure_under_a_rule_that_only_proceeded_still_names_no_scenario() {
    GrowTransaction hold = aHold();
    scenarioRules.addAll(
        List.of(
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.TransportFaultAlways(new TransportFault.TruncatedBody()),
                new ScenarioStory(
                    "settlement-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "lost")),
            new NewScenarioRule(
                GrowLightApi.GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                new ScenarioStory("settlement-retries", StoryKeying.FLOW, "lost", null))));
    settle(hold);
    transactions.whileAppending(() -> transactions.clear(RUN));

    GrowApiResponse response = settle(hold);

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response)).endsWith("}");
    assertThat(lastAudit().scenarioId()).isNull();
  }

  @Test
  void the_outbound_row_describes_the_delivery_without_the_rule_that_named_the_fault() {
    GrowTransaction hold = aHold();
    ScenarioRule rule = faulting(new TransportFault.DroppedConnection());

    GrowApiResponse response = settle(hold);
    scenarioRules.remove(GrowLightApi.GROW, RUN, rule.scenarioId());

    AuditRecord outbound = lastAudit();
    assertThat(outbound.delivered().transportFault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.DROPPED_CONNECTION, FaultPlacement.AFTER_THE_OPERATION));
    assertThat(outbound.delivered().declaredContentLength())
        .isEqualTo(response.declaredContentLength());
    assertThat(outbound.delivered().writtenBodyBytes()).isEqualTo(response.body().length);
    assertThat(outbound.delivered().writtenBodyBytes())
        .isLessThan(outbound.delivered().declaredContentLength());
    assertThat(outbound.delivered().heldFor()).isEqualTo(Duration.ZERO);
    assertThat(scenarioRules.find(GrowLightApi.GROW, RUN, rule.scenarioId())).isEmpty();
  }

  @Test
  void an_undamaged_delivery_says_so_on_its_own_row_rather_than_leaving_the_reader_to_infer_it() {
    AuditRecord outbound = auditOf(settle(aHold()));

    assertThat(outbound.delivered().transportFault()).isNull();
    assertThat(outbound.delivered().declaredContentLength())
        .isEqualTo(outbound.delivered().writtenBodyBytes());
    assertThat(outbound.delivered().heldFor()).isEqualTo(Duration.ZERO);
  }

  @Test
  void a_held_response_names_the_test_run_whose_reset_may_take_it_away() {
    GrowTransaction hold = aHold();
    faulting(new TransportFault.DelayedResponse(Duration.ofMillis(750)));

    GrowApiResponse response = settle(hold);

    assertThat(response.testRunId()).isEqualTo(RUN);
    assertThat(api.createPaymentProcess(call(CREATE, aHoldRequest())).testRunId()).isEqualTo(RUN);
  }

  @Test
  void a_held_delivery_records_how_long_it_was_held_for() {
    GrowTransaction hold = aHold();
    faulting(new TransportFault.DelayedResponse(Duration.ofMillis(750)));

    settle(hold);

    assertThat(lastAudit().delivered().heldFor()).isEqualTo(Duration.ofMillis(750));
  }

  private AuditRecord auditOf(GrowApiResponse response) {
    assertThat(response.httpStatus()).isEqualTo(200);
    return lastAudit();
  }

  private void declining() {
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("803"),
            null));
  }

  private GrowApiResponse refund(GrowTransaction transaction) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, USER_ID);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, transaction.transactionToken());
    fields.put(GrowFields.REFUND_SUM, HELD);
    return api.refundTransaction(call(REFUND, fields));
  }

  private GrowApiResponse lookUpTheTransaction(GrowTransaction transaction) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.PAGE_CODE, PAGE_CODE);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, transaction.transactionToken());
    return api.getTransactionInfo(call(TRANSACTION_INFO, fields));
  }

  private GrowApiResponse lookUpTheProcess(GrowPaymentProcess process) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.PAGE_CODE, PAGE_CODE);
    fields.put(GrowFields.PROCESS_ID, process.processId());
    fields.put(GrowFields.PROCESS_TOKEN, process.processToken());
    return api.getPaymentProcessInfo(call(PROCESS_INFO, fields));
  }

  private GrowApiResponse approve(GrowTransaction transaction) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.PAGE_CODE, PAGE_CODE);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, transaction.transactionToken());
    return api.approveTransaction(call(APPROVE, fields));
  }

  private ScenarioRule answering(int httpStatus) {
    return scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(httpStatus)),
            null));
  }

  private ScenarioRule faulting(TransportFault fault) {
    return scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TransportFaultAlways(fault),
            null));
  }

  private GrowTransaction aHold() {
    String processId = createTheProcess();
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    return transactions.all().get(transactions.all().size() - 1);
  }

  private String createTheProcess() {
    GrowApiResponse created = api.createPaymentProcess(call(CREATE, aHoldRequest()));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    return String.valueOf(created.providerObjectId());
  }

  private GrowApiResponse settle(GrowTransaction transaction) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.USER_ID, USER_ID);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, transaction.transactionToken());
    fields.put(GrowFields.SUM, HELD);
    return api.settleSuspendedTransaction(call(SETTLE, fields));
  }

  private static Map<String, String> aHoldRequest() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, "2");
    form.put(GrowFields.SUM, HELD);
    form.put(GrowFields.DESCRIPTION, "A hold");
    form.put(GrowFields.SUCCESS_URL, "https://merchant.example/thanks");
    form.put(GrowFields.CANCEL_URL, "https://merchant.example/cancel");
    form.put(GrowFields.NOTIFY_URL, NOTIFY_URL);
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    form.put(GrowFields.PAYMENT_INTENT_FIELD, "intent-99");
    return form;
  }

  private ProviderObjectState stateOf(GrowTransaction transaction) {
    return provider.state(RUN, transaction.transactionId()).orElseThrow();
  }

  private static DerivedBalances balancesOf(ProviderObjectState state) {
    return state.derivedBalances().orElseThrow();
  }

  private AuditRecord lastAudit() {
    return audit.all().get(audit.all().size() - 1);
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

  private static final class PinnedClock implements SimulatorClock {

    @Override
    public Instant now(TestRunId testRunId) {
      return NOW;
    }
  }
}
