package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.control.ControlPayloads.AuditRecordView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.KeyInspectorResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.KeyedExchangeView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.KeyedRequestView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.RefusalView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ScenarioResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.StateResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookAttemptView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookDeliveryView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookJobView;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.TestKeyClaims;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ControlViewsTest {

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");

  private static final Instant CREATED = Instant.parse("2026-09-15T10:00:00Z");

  private static final Instant EXPIRES = Instant.parse("2026-09-15T13:00:00Z");

  private static final String NO_PARTY_ON_A_TOKENIZATION =
      "provider worldline publishes no customer object on the hosted tokenization routes";

  private static final String NO_DEADLINE_ON_A_PROCESS =
      "provider grow publishes no deadline on a payment process at all";
  private static final TestRunId RUN = TestRunId.of("control-views");
  private static final ProviderId PROVIDER = ProviderId.of("grow");
  private static final String EMAIL = "payer@example.test";

  private static final String UNIQUE_IDENTIFIER = "transactionUniqueIdentifier";

  private static final String NO_REPLAY = "provider grow replays no answer anywhere";

  @Test
  void the_inspector_read_names_every_key_a_provider_declares_and_what_a_repeat_of_it_does() {
    KeyInspectorResponse answered =
        ControlViews.viewOf(RUN, aDuplicateCheckingProvider(), KeyedExchangePage.empty(), 50);

    assertThat(answered.testRunId()).isEqualTo(RUN.value());
    assertThat(answered.provider()).isEqualTo("grow");
    assertThat(answered.keys())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.name()).isEqualTo(UNIQUE_IDENTIFIER);
              assertThat(key.role()).isEqualTo("duplicate-detection key");
              assertThat(key.roleMeans()).contains("refused and never replayed");
              assertThat(key.carriedIn()).isEqualTo("sent as a field of the request itself");
              assertThat(key.canBeHeld()).isFalse();
              assertThat(key.whileItIsHeld()).isEqualTo(TestKeyClaims.NEVER_HELD.describedAs());
            });
    assertThat(answered.withoutAMechanism())
        .extracting(ControlPayloads.AbsentMechanismView::role)
        .containsExactly("true idempotency key", "correlation key");
    assertThat(answered.withoutAMechanism().get(0).instead()).isEqualTo(NO_REPLAY);
    assertThat(answered.exchanges()).isEmpty();
    assertThat(answered.nextCursor()).isNull();
  }

  @Test
  void the_inspector_read_reports_a_refused_repeat_as_a_refusal_and_never_as_a_replay() {
    KeyInspectorResponse answered =
        ControlViews.viewOf(
            RUN, aDuplicateCheckingProvider(), new KeyedExchangePage(List.of(aRefusedRepeat())), 1);

    KeyedExchangeView exchange = answered.exchanges().get(0);
    assertThat(exchange.cursor()).isEqualTo("9");
    assertThat(exchange.keyName()).isEqualTo(UNIQUE_IDENTIFIER);
    assertThat(exchange.role()).isEqualTo("duplicate-detection key");
    assertThat(exchange.keyValue()).isEqualTo("9001");
    assertThat(exchange.accountId()).isEqualTo("merchant-1");
    assertThat(exchange.firstRequestAt()).isEqualTo(AT);
    assertThat(exchange.replayTimestamps()).isEmpty();
    assertThat(exchange.stateChanged()).isTrue();
    assertThat(exchange.requestFingerprint()).isEqualTo("fingerprint-one");
    assertThat(answered.nextCursor()).isEqualTo("9");

    KeyedRequestView repeat = exchange.requests().get(1);
    assertThat(repeat.verdict()).isEqualTo(ExchangeVerdict.DUPLICATE_KEY_REFUSAL.name());
    assertThat(repeat.verdictMeans()).contains("no response replayed");
    assertThat(repeat.conflictReason()).isNull();
    assertThat(repeat.timeout()).isNull();
    assertThat(exchange.requests().get(0).timeout()).contains("already run and taken effect");
    assertThat(exchange.requests().get(0).providerObjectId()).isEqualTo("123456");
  }

  private static ProviderKeyMechanisms aDuplicateCheckingProvider() {
    return new ProviderKeyMechanisms(
        PROVIDER,
        List.of(
            new DeclaredKey(
                UNIQUE_IDENTIFIER,
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every submission of this business",
                TestKeyClaims.NEVER_HELD)),
        Map.of(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            NO_REPLAY,
            KeyRole.CORRELATION_KEY,
            "provider grow correlates nothing"),
        answered -> answered);
  }

  private static KeyedExchange aRefusedRepeat() {
    return new KeyedExchange(
        PROVIDER,
        RUN,
        "merchant-1",
        UNIQUE_IDENTIFIER,
        KeyRole.DUPLICATE_DETECTION_KEY,
        "9001",
        9L,
        List.of(
            new KeyedRequest(
                0,
                ExchangeVerdict.FIRST_REQUEST,
                "fingerprint-one",
                "createTransactionWithToken",
                200,
                "application/json",
                "{\"status\":1}",
                "123456",
                true,
                new AppliedFault(
                    TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION),
                AT),
            new KeyedRequest(
                1,
                ExchangeVerdict.DUPLICATE_KEY_REFUSAL,
                "fingerprint-one",
                "createTransactionWithToken",
                200,
                "application/json",
                "{\"status\":0}",
                null,
                false,
                null,
                AT.plus(Duration.ofMinutes(1)))));
  }

  @Test
  void a_seeded_card_is_answered_with_the_provider_native_ids_and_the_card_facts_it_holds() {
    ControlPayloads.FixtureResponse view =
        ControlViews.viewOf(
            new PaymentMethodFixture(
                "fx_1",
                ProviderId.of("stripe"),
                TestRunId.of("run-a"),
                "merchant-1",
                "customer-123",
                PaymentMethodKind.CARD,
                "visa-success",
                new FixtureCard("visa", "1111", 12, 2030, "credit"),
                Map.of("paymentMethodId", "pm_sim_123"),
                "scn_1",
                Instant.parse("2026-09-10T12:00:00Z")));

    assertThat(view.fixtureId()).isEqualTo("fx_1");
    assertThat(view.provider()).isEqualTo("stripe");
    assertThat(view.testRunId()).isEqualTo("run-a");
    assertThat(view.merchantId()).isEqualTo("merchant-1");
    assertThat(view.ownerId()).isEqualTo("customer-123");
    assertThat(view.type()).isEqualTo("card");
    assertThat(view.alias()).isEqualTo("visa-success");
    assertThat(view.defaultScenario()).isEqualTo("scn_1");
    assertThat(view.providerObjects()).containsExactly(Map.entry("paymentMethodId", "pm_sim_123"));
    assertThat(view.card())
        .isEqualTo(new ControlPayloads.FixtureCardView("visa", "1111", 12, 2030, "credit"));
    assertThat(view.createdAt()).isEqualTo(Instant.parse("2026-09-10T12:00:00Z"));
  }

  @Test
  void a_seeded_wallet_is_answered_with_no_card_rather_than_an_empty_one() {
    ControlPayloads.FixtureResponse view =
        ControlViews.viewOf(
            new PaymentMethodFixture(
                "fx_2",
                ProviderId.of("stripe"),
                TestRunId.of("run-a"),
                "merchant-1",
                "customer-123",
                PaymentMethodKind.WALLET,
                "a-wallet",
                null,
                Map.of(),
                null,
                Instant.parse("2026-09-10T12:00:00Z")));

    assertThat(view.card()).isNull();
    assertThat(view.type()).isEqualTo("wallet");
    assertThat(view.providerObjects()).isEmpty();
    assertThat(view.defaultScenario()).isNull();
  }

  @Test
  void
      an_exchange_recorded_before_the_provider_answered_reports_no_rule_no_verdict_and_nothing_delivered() {
    AuditRecordView view = ControlViews.viewOf(anArrivingRequest());

    assertThat(view.resolutionRule()).isNull();
    assertThat(view.outcome()).isNull();
    assertThat(view.delivered()).isNull();
    assertThat(view.status()).isNull();
    assertThat(view.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST.name());
  }

  @Test
  void
      an_answered_exchange_reports_the_rule_that_routed_it_to_a_test_run_and_the_verdict_it_reached() {
    AuditRecordView view = ControlViews.viewOf(anAnsweredExchange(null));

    assertThat(view.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER.name());
    assertThat(view.outcome()).isEqualTo(ProviderOutcome.SUCCESS.name());
    assertThat(view.testRunId()).isEqualTo(RUN.value());
    assertThat(view.provider()).isEqualTo(PROVIDER.value());
    assertThat(view.attempt()).isEqualTo(1);
    assertThat(view.method()).isEqualTo("POST");
    assertThat(view.path()).isEqualTo("/api/light/server/1.0/createPaymentProcess");
    assertThat(view.providerObjectId()).isEqualTo("proc_1");
    assertThat(view.scenarioId()).isEqualTo("scn_1");
    assertThat(view.createdAt()).isEqualTo(AT);
    assertThat(view.auditId()).isEqualTo("aud_1");
  }

  @Test
  void
      an_exchange_a_fault_damaged_reports_the_fault_where_it_was_placed_and_how_little_of_the_promised_body_reached_the_client() {
    AuditRecordView view =
        ControlViews.viewOf(
            anAnsweredExchange(
                new DeliveredResponse(
                    new AppliedFault(
                        TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION),
                    120L,
                    40L,
                    Duration.ofMillis(250))));

    assertThat(view.delivered()).isNotNull();
    assertThat(view.delivered().transportFault())
        .isEqualTo(TransportFaultKind.TRUNCATED_BODY.name());
    assertThat(view.delivered().faultPlacement())
        .isEqualTo(FaultPlacement.AFTER_THE_OPERATION.name());
    assertThat(view.delivered().declaredContentLength()).isEqualTo(120L);
    assertThat(view.delivered().writtenBodyBytes()).isEqualTo(40L);
  }

  @Test
  void
      an_exchange_only_held_back_names_no_fault_and_reports_how_long_the_caller_was_kept_waiting() {
    AuditRecordView view =
        ControlViews.viewOf(
            anAnsweredExchange(new DeliveredResponse(null, 120L, 120L, Duration.ofMillis(1500))));

    assertThat(view.delivered()).isNotNull();
    assertThat(view.delivered().transportFault()).isNull();
    assertThat(view.delivered().faultPlacement()).isNull();
    assertThat(view.delivered().heldForMillis()).isEqualTo(1500L);
    assertThat(view.delivered().writtenBodyBytes())
        .isEqualTo(view.delivered().declaredContentLength());
  }

  @Test
  void the_audit_trail_shows_the_fields_that_were_sent_with_the_secrets_already_redacted() {
    AuditRecordView view = ControlViews.viewOf(anAnsweredExchange(null));

    assertThat(view.sanitizedHeaders()).containsEntry("Authorization", List.of(Redaction.REDACTED));
    assertThat(view.sanitizedBody()).containsEntry("pageField[email]", List.of(EMAIL));
  }

  @Test
  void a_rule_that_is_not_a_step_in_a_story_reports_no_story_and_no_states() {
    ScenarioResponse view = ControlViews.viewOf("grow", aRuleWith(null));

    assertThat(view.story()).isNull();
    assertThat(view.keyedOn()).isNull();
    assertThat(view.whenState()).isNull();
    assertThat(view.thenState()).isNull();
    assertThat(view.scenarioId()).isEqualTo("scn_1");
    assertThat(view.provider()).isEqualTo("grow");
    assertThat(view.testRunId()).isEqualTo(RUN.value());
    assertThat(view.priority()).isEqualTo(10);
    assertThat(view.operation()).isEqualTo("createPaymentProcess");
    assertThat(view.createdAt()).isEqualTo(AT);
  }

  @Test
  void
      a_step_in_a_story_reports_the_state_it_waits_for_the_state_it_moves_to_and_what_the_story_is_keyed_on() {
    ScenarioResponse view =
        ControlViews.viewOf(
            "grow",
            aRuleWith(
                new ScenarioStory(
                    "declined-then-approved", StoryKeying.FLOW, "Started", "Declined")));

    assertThat(view.story()).isEqualTo("declined-then-approved");
    assertThat(view.keyedOn()).isEqualTo(StoryKeying.FLOW.name());
    assertThat(view.whenState()).isEqualTo("Started");
    assertThat(view.thenState()).isEqualTo("Declined");
  }

  @Test
  void an_object_the_provider_stated_no_amount_for_reports_none_rather_than_a_zero() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(anObjectWithNoAmount()), AT);

    assertThat(view.amount()).isNull();
    assertThat(view.currency()).isNull();
    assertThat(view.operations()).isEmpty();
    assertThat(view.relatedObjects()).isEmpty();
  }

  @Test
  void
      a_hold_the_provider_will_still_settle_reports_what_is_still_capturable_and_what_is_refundable() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aHoldOf("120.50")), AT);

    assertThat(view.amount()).isEqualTo("120.50");
    assertThat(view.currency()).isEqualTo("ILS");
    assertThat(view.derivedBalances()).isNotNull();
    assertThat(view.derivedBalances().capturedAmount()).isEqualTo("0");
    assertThat(view.derivedBalances().refundedAmount()).isEqualTo("0");
    assertThat(view.derivedBalances().capturableRemainder()).isEqualTo("120.50");
    assertThat(view.derivedBalances().refundableRemainder()).isEqualTo("0");
    assertThat(view.nativeStatus()).isEqualTo("2");
    assertThat(view.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE.name());
    assertThat(view.capturesAccepted()).isTrue();
    assertThat(view.refundsAccepted()).isFalse();
    assertThat(view.providerObjectId()).isEqualTo("trx_1");
    assertThat(view.testRunId()).isEqualTo(RUN.value());
    assertThat(view.provider()).isEqualTo("grow");
    assertThat(view.updatedAt()).isEqualTo(AT);
    assertThat(view.relatedObjects()).containsEntry("processId", "proc_1");
  }

  @Test
  void a_hold_no_capture_can_take_reports_the_remainder_it_holds_and_says_no_capture_is_taken() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aHoldNoCaptureCanTake("120.50")), AT);

    assertThat(view.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE.name());
    assertThat(view.derivedBalances().capturableRemainder()).isEqualTo("120.50");
    assertThat(view.capturesAccepted()).isFalse();
  }

  @Test
  void a_settlement_no_refund_can_return_reports_what_it_took_and_says_no_refund_is_returned() {
    StateResponse view =
        ControlViews.viewOf(
            RUN,
            "worldline",
            AddressedProviderObject.canonically(aSettlementNoRefundCanReturn("120.50")),
            AT);

    assertThat(view.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED.name());
    assertThat(view.derivedBalances().refundableRemainder()).isEqualTo("120.50");
    assertThat(view.refundsAccepted()).isFalse();
  }

  @Test
  void a_settlement_a_refund_would_still_return_reports_the_remainder_and_says_a_refund_is_taken() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aSettlementOf("120.50")), AT);

    assertThat(view.derivedBalances().refundableRemainder()).isEqualTo("120.50");
    assertThat(view.refundsAccepted()).isTrue();
  }

  @Test
  void
      an_object_whose_amounts_the_provider_never_validated_reports_no_balances_rather_than_zeros() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aHoldOf("not a number")), AT);

    assertThat(view.derivedBalances()).isNull();
    assertThat(view.operations()).hasSize(1);
  }

  @Test
  void
      every_operation_on_an_object_is_reported_in_the_order_it_happened_with_the_object_it_created() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aSettlementOf("120.50")), AT);

    assertThat(view.operations())
        .extracting(ControlPayloads.OperationView::kind)
        .containsExactly(OperationKind.AUTHORIZATION.name(), OperationKind.CAPTURE.name());
    assertThat(view.operations().get(0).sequence()).isEqualTo(1);
    assertThat(view.operations().get(0).createdObjectId()).isNull();
    assertThat(view.operations().get(0).closesAuthorization()).isNull();
    assertThat(view.operations().get(0).amount()).isEqualTo("120.50");
    assertThat(view.operations().get(0).nativeStatus()).isEqualTo("2");
    assertThat(view.operations().get(0).normalizedStatus())
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE.name());
    assertThat(view.operations().get(0).createdAt()).isEqualTo(AT);
    assertThat(view.operations().get(1).sequence()).isEqualTo(2);
    assertThat(view.operations().get(1).createdObjectId()).isEqualTo("cap_1");
    assertThat(view.operations().get(1).closesAuthorization()).isTrue();
  }

  @Test
  void
      a_webhook_job_reports_the_state_it_is_in_the_attempts_it_has_made_and_when_the_next_one_is_due() {
    WebhookJobView job =
        ControlViews.jobOf(aDelivery(DeliveryState.PENDING, 1, AT.plusSeconds(60)));

    assertThat(job.webhookJobId()).isEqualTo("whd_1");
    assertThat(job.eventId()).isEqualTo("evt_1");
    assertThat(job.status()).isEqualTo(DeliveryState.PENDING.name());
    assertThat(job.attemptsMade()).isEqualTo(1);
    assertThat(job.scheduledAt()).isEqualTo(AT.plusSeconds(60));
  }

  @Test
  void a_delivery_that_will_not_be_tried_again_reports_no_next_attempt() {
    WebhookJobView job = ControlViews.jobOf(aDelivery(DeliveryState.DELIVERED, 2, null));

    assertThat(job.status()).isEqualTo(DeliveryState.DELIVERED.name());
    assertThat(job.scheduledAt()).isNull();
  }

  @Test
  void a_delivery_reports_where_it_was_sent_what_it_carried_and_how_many_attempts_it_is_allowed() {
    WebhookDeliveryView view =
        ControlViews.viewOf(aDelivery(DeliveryState.PENDING, 1, AT.plusSeconds(60)), List.of());

    assertThat(view.webhookJobId()).isEqualTo("whd_1");
    assertThat(view.eventId()).isEqualTo("evt_1");
    assertThat(view.eventType()).isEqualTo("transaction.approved");
    assertThat(view.providerObjectId()).isEqualTo("trx_1");
    assertThat(view.merchantId()).isEqualTo("merchant-1");
    assertThat(view.targetUrl()).isEqualTo("https://merchant.example.test/hooks");
    assertThat(view.status()).isEqualTo(DeliveryState.PENDING.name());
    assertThat(view.shapedBy()).isNull();
    assertThat(view.attemptsMade()).isEqualTo(1);
    assertThat(view.attemptCap()).isEqualTo(3);
    assertThat(view.nextAttemptAt()).isEqualTo(AT.plusSeconds(60));
    assertThat(view.createdAt()).isEqualTo(AT);
    assertThat(view.contentType()).isEqualTo("application/json");
    assertThat(view.headers()).containsEntry("Content-Type", List.of("application/json"));
    assertThat(view.payload()).containsEntry("status", List.of("approved"));
    assertThat(view.attempts()).isEmpty();
  }

  @Test
  void a_delivery_a_rule_shaped_names_the_rule_so_a_reader_is_not_left_guessing_why() {
    WebhookDeliveryView view =
        ControlViews.viewOf(
            shapedBy(aDelivery(DeliveryState.DROPPED, 0, null), "scn_dropped"), List.of());

    assertThat(view.status()).isEqualTo(DeliveryState.DROPPED.name());
    assertThat(view.shapedBy()).isEqualTo("scn_dropped");
  }

  @Test
  void
      an_attempt_the_merchant_answered_reports_the_verdict_the_status_and_a_preview_of_what_came_back() {
    WebhookDeliveryView view =
        ControlViews.viewOf(
            aDelivery(DeliveryState.DELIVERED, 1, null), List.of(anAnsweredAttempt()));

    WebhookAttemptView attempt = view.attempts().get(0);
    assertThat(attempt.number()).isEqualTo(1);
    assertThat(attempt.attemptedAt()).isEqualTo(AT);
    assertThat(attempt.verdict()).isEqualTo(Acknowledgement.ACKNOWLEDGED.name());
    assertThat(attempt.httpStatus()).isEqualTo(200);
    assertThat(attempt.transportFailure()).isNull();
    assertThat(attempt.responsePreview()).isEqualTo("OK");
  }

  @Test
  void an_attempt_the_merchant_never_answered_names_the_transport_failure_and_reports_no_status() {
    WebhookDeliveryView view =
        ControlViews.viewOf(
            aDelivery(DeliveryState.PENDING, 1, AT.plusSeconds(60)), List.of(aTimedOutAttempt()));

    WebhookAttemptView attempt = view.attempts().get(0);
    assertThat(attempt.transportFailure()).isEqualTo(TransportFailure.READ_TIMEOUT.name());
    assertThat(attempt.httpStatus()).isNull();
    assertThat(attempt.responsePreview()).isNull();
    assertThat(attempt.verdict()).isEqualTo(Acknowledgement.REFUSED.name());
  }

  @Test
  void the_attempts_of_a_delivery_are_reported_in_the_order_they_were_made() {
    WebhookDeliveryView view =
        ControlViews.viewOf(
            aDelivery(DeliveryState.DELIVERED, 2, null),
            List.of(aTimedOutAttempt(), anAnsweredAttempt()));

    assertThat(view.attempts())
        .extracting(WebhookAttemptView::transportFailure)
        .containsExactly(TransportFailure.READ_TIMEOUT.name(), null);
  }

  private static AuditRecord anArrivingRequest() {
    return new AuditRecord(
        "aud_0",
        RUN,
        null,
        PROVIDER,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        null,
        "POST",
        "/api/light/server/1.0/createPaymentProcess",
        null,
        null,
        null,
        null,
        1,
        AT,
        Map.of("Authorization", List.of(Redaction.REDACTED)),
        Map.of("pageField[email]", List.of(EMAIL)),
        null);
  }

  private static AuditRecord anAnsweredExchange(@Nullable DeliveredResponse delivered) {
    return new AuditRecord(
        "aud_1",
        RUN,
        TestRunResolutionRule.TEST_HEADER,
        PROVIDER,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        "POST",
        "/api/light/server/1.0/createPaymentProcess",
        200,
        "proc_1",
        null,
        "scn_1",
        1,
        AT,
        Map.of("Authorization", List.of(Redaction.REDACTED)),
        Map.of("pageField[email]", List.of(EMAIL)),
        delivered);
  }

  private static ScenarioRule aRuleWith(@Nullable ScenarioStory story) {
    return new ScenarioRule(
        "scn_1",
        PROVIDER,
        RUN,
        10,
        1L,
        ScenarioMatch.anyRequest().naming("createPaymentProcess"),
        new ScenarioAction.FixedSuccess(),
        story,
        AT);
  }

  private static ProviderObjectState anObjectWithNoAmount() {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "11",
        NormalizedStatus.PROCESSING,
        null,
        null,
        Ledger.empty(),
        true,
        true,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  @Test
  void a_state_read_names_the_identifier_it_was_addressed_by_and_every_one_the_object_answers_to() {
    StateResponse view =
        ControlViews.viewOf(
            RUN,
            "grow",
            new AddressedProviderObject("txn_2", aHoldRenamedTo("txn_2", "120.50")),
            AT);

    assertThat(view.providerObjectId()).isEqualTo("trx_1");
    assertThat(view.addressedBy()).isEqualTo("txn_2");
    assertThat(view.answersTo()).containsExactly("trx_1", "txn_2");
  }

  @Test
  void a_state_read_of_an_object_that_kept_one_identifier_is_addressed_by_that_identifier() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aHoldOf("120.50")), AT);

    assertThat(view.providerObjectId()).isEqualTo("trx_1");
    assertThat(view.addressedBy()).isEqualTo("trx_1");
    assertThat(view.answersTo()).containsExactly("trx_1");
  }

  private static ProviderObjectState aHoldRenamedTo(String renamed, String amount) {
    ProviderObjectState kept = aHoldOf(amount);
    return new ProviderObjectState(
        ProviderObjectIdentity.of(kept.providerObjectId()).answeringAlsoTo(renamed),
        AKindOfObjectATestNames.ONE,
        kept.nativeStatus(),
        kept.normalizedStatus(),
        kept.amount(),
        kept.currency(),
        kept.ledger(),
        kept.authorizationOpen(),
        kept.capturesAccepted(),
        kept.refundsAccepted(),
        kept.relatedObjects(),
        kept.origin(),
        kept.updatedAt());
  }

  @Test
  void a_state_read_of_an_object_no_payer_finishes_publishes_no_hosted_session() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aHoldOf("120.50")), AT);

    assertThat(view.hostedSession()).isNull();
  }

  @Test
  void a_state_read_of_an_object_a_payer_finishes_publishes_the_facts_a_ci_run_reads() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aProcessNamingItsPayer()), AT);

    assertThat(view.hostedSession()).isNotNull();
    assertThat(view.hostedSession().account()).isEqualTo("merchant-1");
    assertThat(view.hostedSession().party()).isEqualTo("Dana Levi");
    assertThat(view.hostedSession().aPartyIsNamed()).isTrue();
    assertThat(view.hostedSession().operation()).isEqualTo("createPaymentProcess:chargeType=2");
    assertThat(view.hostedSession().createdAt()).isEqualTo(CREATED);
  }

  @Test
  void a_state_read_publishes_a_providers_own_words_where_it_publishes_no_deadline() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "grow", AddressedProviderObject.canonically(aProcessNamingItsPayer()), AT);

    assertThat(view.hostedSession()).isNotNull();
    assertThat(view.hostedSession().expires()).isEqualTo(NO_DEADLINE_ON_A_PROCESS);
    assertThat(view.hostedSession().itExpiresAtAKnownTime()).isFalse();
    assertThat(view.hostedSession().itsDeadlineHasPassed()).isFalse();
  }

  @Test
  void a_state_read_publishes_a_providers_own_words_where_it_names_no_party() {
    StateResponse view =
        ControlViews.viewOf(
            RUN, "worldline", AddressedProviderObject.canonically(aSessionNamingNoParty()), AT);

    assertThat(view.hostedSession()).isNotNull();
    assertThat(view.hostedSession().party()).isEqualTo(NO_PARTY_ON_A_TOKENIZATION);
    assertThat(view.hostedSession().aPartyIsNamed()).isFalse();
  }

  @Test
  void a_state_read_measures_a_deadline_against_the_clock_of_the_run_it_was_asked_of() {
    AddressedProviderObject session = AddressedProviderObject.canonically(aSessionNamingNoParty());

    assertThat(
            ControlViews.viewOf(RUN, "worldline", session, EXPIRES.minusSeconds(1))
                .hostedSession()
                .itsDeadlineHasPassed())
        .isFalse();
    assertThat(
            ControlViews.viewOf(RUN, "worldline", session, EXPIRES.plusSeconds(1))
                .hostedSession()
                .itsDeadlineHasPassed())
        .isTrue();
  }

  private static ProviderObjectState aProcessNamingItsPayer() {
    return anObjectFinishedBy(
        "proc_1",
        new HostedSessionFacts.AnObjectAPayerFinishes(
            "merchant-1",
            new HostedSessionFacts.Party.NamedBy("Dana Levi"),
            "createPaymentProcess:chargeType=2",
            CREATED,
            new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(
                NO_DEADLINE_ON_A_PROCESS)));
  }

  private static ProviderObjectState aSessionNamingNoParty() {
    return anObjectFinishedBy(
        "sess_1",
        new HostedSessionFacts.AnObjectAPayerFinishes(
            "merchant-1",
            new HostedSessionFacts.Party.NoPartyThisProviderPublishes(NO_PARTY_ON_A_TOKENIZATION),
            "POST /v2/{merchantId}/hostedtokenizations",
            CREATED,
            new HostedSessionFacts.Deadline.ItExpiresAt(EXPIRES)));
  }

  private static ProviderObjectState anObjectFinishedBy(String id, HostedSessionFacts facts) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(id),
        AKindOfObjectATestNames.ONE,
        "11",
        NormalizedStatus.PROCESSING,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT,
        facts);
  }

  private static ProviderObjectState aHoldOf(String amount) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of(amount),
        "ILS",
        new Ledger(List.of(anAuthorizationOf(amount))),
        true,
        true,
        false,
        Map.of("processId", "proc_1"),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aHoldNoCaptureCanTake(String amount) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of(amount),
        "ILS",
        new Ledger(List.of(anAuthorizationOf(amount))),
        true,
        false,
        false,
        Map.of("processId", "proc_1"),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aSettlementOf(String amount) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "3",
        NormalizedStatus.SUCCEEDED,
        Amount.of(amount),
        "ILS",
        new Ledger(
            List.of(
                anAuthorizationOf(amount),
                new MonetaryOperation(
                    2,
                    OperationKind.CAPTURE,
                    Amount.of(amount),
                    true,
                    "cap_1",
                    "3",
                    NormalizedStatus.SUCCEEDED,
                    AT))),
        false,
        false,
        true,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aSettlementNoRefundCanReturn(String amount) {
    ProviderObjectState settled = aSettlementOf(amount);
    return new ProviderObjectState(
        settled.identity(),
        AKindOfObjectATestNames.ONE,
        settled.nativeStatus(),
        settled.normalizedStatus(),
        settled.amount(),
        settled.currency(),
        settled.ledger(),
        settled.authorizationOpen(),
        settled.capturesAccepted(),
        false,
        settled.relatedObjects(),
        settled.origin(),
        settled.updatedAt());
  }

  private static MonetaryOperation anAuthorizationOf(String amount) {
    return new MonetaryOperation(
        1,
        OperationKind.AUTHORIZATION,
        Amount.of(amount),
        null,
        null,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        AT);
  }

  private static WebhookDelivery shapedBy(WebhookDelivery delivery, String scenarioId) {
    return new WebhookDelivery(
        delivery.deliveryId(),
        delivery.provider(),
        delivery.testRunId(),
        delivery.merchantId(),
        delivery.eventId(),
        delivery.eventType(),
        delivery.providerObjectId(),
        delivery.targetUrl(),
        delivery.payload(),
        delivery.retryPlan(),
        delivery.state(),
        delivery.acknowledgementOwed(),
        delivery.attemptsMade(),
        delivery.nextAttemptAt(),
        delivery.createdAt(),
        delivery.updatedAt(),
        scenarioId,
        delivery.notSentBecause());
  }

  private static WebhookDelivery aDelivery(
      DeliveryState state, int attemptsMade, @Nullable Instant nextAttemptAt) {
    return new WebhookDelivery(
        "whd_1",
        PROVIDER,
        RUN,
        "merchant-1",
        "evt_1",
        "transaction.approved",
        "trx_1",
        "https://merchant.example.test/hooks",
        new WebhookPayload(
            "application/json",
            Map.of(),
            "{\"status\":\"approved\"}".getBytes(StandardCharsets.UTF_8),
            Map.of("status", List.of("approved"))),
        new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(60), Duration.ofSeconds(300))),
        state,
        false,
        attemptsMade,
        nextAttemptAt,
        AT,
        AT,
        null,
        null);
  }

  private static WebhookAttempt anAnsweredAttempt() {
    return new WebhookAttempt(
        "whd_1",
        1,
        AT,
        "https://merchant.example/hook",
        Map.of("Content-Type", List.of("application/json")),
        Acknowledgement.ACKNOWLEDGED,
        200,
        null,
        "OK");
  }

  @Test
  void every_arm_of_a_refusal_subject_is_published_under_its_own_name_and_its_own_detail() {
    assertThat(ControlViews.viewOf((RefusalSubject) null)).isNull();
    assertThat(ControlViews.viewOf(RefusalSubject.theObject("pi_1")))
        .isEqualTo(new RefusalView(ControlViews.THE_OBJECT, "pi_1", null, null, null, null));
    assertThat(ControlViews.viewOf(RefusalSubject.noObjectAnsweredTo("providerObjectId")))
        .isEqualTo(
            new RefusalView(
                ControlViews.NO_OBJECT_ANSWERED_TO, null, "providerObjectId", null, null, null));
    assertThat(ControlViews.viewOf(RefusalSubject.severalObjectsAnsweredTo("orderId", 2)))
        .isEqualTo(
            new RefusalView(
                ControlViews.SEVERAL_OBJECTS_ANSWERED_TO, null, "orderId", 2, null, null));
    assertThat(ControlViews.viewOf(RefusalSubject.anObjectItWillNotName("it answers 200")))
        .isEqualTo(
            new RefusalView(
                ControlViews.AN_OBJECT_IT_WILL_NOT_NAME, null, null, null, "it answers 200", null));
    assertThat(ControlViews.viewOf(RefusalSubject.noObjectWasNamed("a scenario rule")))
        .isEqualTo(
            new RefusalView(
                ControlViews.NO_OBJECT_WAS_NAMED, null, null, null, null, "a scenario rule"));
  }

  private static WebhookAttempt aTimedOutAttempt() {
    return new WebhookAttempt(
        "whd_1",
        1,
        AT,
        "https://merchant.example/hook",
        Map.of("Content-Type", List.of("application/json")),
        Acknowledgement.REFUSED,
        null,
        TransportFailure.READ_TIMEOUT,
        null);
  }
}
