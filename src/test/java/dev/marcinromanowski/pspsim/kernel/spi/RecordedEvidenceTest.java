package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedAttempt;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedCallback;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedExchange;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedObject;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionEvidence;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RecordedEvidenceTest {

  private static final ProviderId PROVIDER = ProviderId.of("twokeys");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final Instant AT = Instant.parse("2026-09-24T12:00:00Z");

  private static final String CHARGE = "ch_1";

  private static final String RENAMED = "ch_renamed";

  private static final String A_SIGNATURE = "t=1,v1=deadbeef";

  @Test
  void only_the_rows_naming_an_identifier_this_object_answers_to_are_carried() {
    List<RecordedExchange> carried =
        RecordedEvidence.theTrailOf(
            List.of(CHARGE, RENAMED),
            List.of(
                aRow("aud_1", CHARGE),
                aRow("aud_2", "ch_someone_else"),
                aRow("aud_3", RENAMED),
                aRow("aud_4", null)));

    assertThat(carried).extracting(RecordedExchange::auditId).containsExactly("aud_1", "aud_3");
  }

  @Test
  void the_place_a_row_holds_in_the_bundle_counts_only_the_rows_the_bundle_carries() {
    List<RecordedExchange> carried =
        RecordedEvidence.theTrailOf(
            List.of(CHARGE),
            List.of(
                aRow("aud_1", "ch_someone_else"), aRow("aud_2", CHARGE), aRow("aud_3", CHARGE)));

    assertThat(carried).extracting(RecordedExchange::place).containsExactly(0, 1);
  }

  @Test
  void a_row_carries_the_surface_the_direction_the_call_and_the_sanitized_headers_and_body() {
    RecordedExchange carried =
        RecordedEvidence.theTrailOf(List.of(CHARGE), List.of(aRow("aud_1", CHARGE))).getFirst();

    assertThat(carried.surface()).isEqualTo(ObjectOrigin.PROVIDER_API.name());
    assertThat(carried.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST.name());
    assertThat(carried.method()).isEqualTo("POST");
    assertThat(carried.path()).isEqualTo("/v1/charges");
    assertThat(carried.httpStatus()).isEqualTo(201);
    assertThat(carried.outcome()).isEqualTo(ProviderOutcome.SUCCESS.name());
    assertThat(carried.scenarioId()).isEqualTo("scn_1");
    assertThat(carried.attempt()).isEqualTo(2);
    assertThat(carried.at()).isEqualTo(AT.toString());
    assertThat(carried.carriedHeaders())
        .containsExactlyInAnyOrderEntriesOf(Map.of("Content-Type", List.of("application/json")));
    assertThat(carried.carriedBody())
        .containsExactlyInAnyOrderEntriesOf(Map.of("amount", List.of("1000")));
    assertThat(carried.saysWhatWasOnTheWire()).isTrue();
  }

  @Test
  void a_row_carrying_neither_a_header_nor_a_field_says_nothing_of_what_was_on_the_wire() {
    RecordedExchange carried =
        RecordedEvidence.theTrailOf(
                List.of(CHARGE),
                List.of(
                    new AuditRecord(
                        "aud_1",
                        RUN,
                        null,
                        PROVIDER,
                        null,
                        ObjectOrigin.CONTROL_API,
                        AuditDirection.INBOUND_REQUEST,
                        null,
                        "GET",
                        "/state",
                        200,
                        CHARGE,
                        null,
                        null,
                        1,
                        AT,
                        Map.of(),
                        Map.of(),
                        null)))
            .getFirst();

    assertThat(carried.saysWhatWasOnTheWire()).isFalse();
    assertThat(carried.outcome()).isNull();
    assertThat(carried.refusedAs()).isNull();
    assertThat(carried.refusalSubject()).isNull();
  }

  @Test
  void a_refused_row_carries_the_arm_of_the_refusal_and_the_thing_that_arm_names() {
    List<RecordedExchange> carried =
        RecordedEvidence.theTrailOf(
            List.of(CHARGE), List.of(refusing(RefusalSubject.theObject(CHARGE))));

    assertThat(carried)
        .extracting(RecordedExchange::refusedAs, RecordedExchange::refusalSubject)
        .containsExactly(Tuple.tuple("TheObject", CHARGE));
  }

  @Test
  void a_refusal_whose_arm_names_no_object_is_on_no_object_trail_because_its_row_names_none() {
    assertThat(
            RecordedEvidence.theTrailOf(
                List.of(CHARGE), List.of(refusing(anObjectItWillNotName()))))
        .isEmpty();
  }

  @Test
  void every_arm_of_a_refusal_names_the_one_thing_that_arm_carries() {
    assertThat(RecordedEvidence.theSubjectOf(RefusalSubject.theObject(CHARGE))).isEqualTo(CHARGE);
    assertThat(RecordedEvidence.theSubjectOf(RefusalSubject.noObjectAnsweredTo("id")))
        .isEqualTo("id");
    assertThat(RecordedEvidence.theSubjectOf(RefusalSubject.severalObjectsAnsweredTo("id", 2)))
        .isEqualTo("id");
    assertThat(RecordedEvidence.theSubjectOf(anObjectItWillNotName()))
        .isEqualTo("its contract names none");
    assertThat(RecordedEvidence.theSubjectOf(RefusalSubject.noObjectWasNamed("the bundle")))
        .isEqualTo("the bundle");
  }

  @Test
  void only_the_callbacks_naming_an_identifier_this_object_answers_to_are_carried() {
    List<RecordedCallback> carried =
        RecordedEvidence.theCallbacksOf(
            List.of(CHARGE),
            List.of(
                aDelivery("whd_1", CHARGE),
                aDelivery("whd_2", "ch_someone_else"),
                aDelivery("whd_3", null)),
            Map.of());

    assertThat(carried).extracting(RecordedCallback::deliveryId).containsExactly("whd_1");
  }

  @Test
  void a_callback_carries_its_plan_its_target_its_payload_and_the_attempts_made_under_it() {
    RecordedCallback carried =
        RecordedEvidence.theCallbacksOf(
                List.of(CHARGE),
                List.of(aDelivery("whd_1", CHARGE)),
                Map.of("whd_1", List.of(anAttempt(1, 500, TransportFailure.READ_TIMEOUT))))
            .getFirst();

    assertThat(carried.eventId()).isEqualTo("evt_1");
    assertThat(carried.eventType()).isEqualTo("charge.succeeded");
    assertThat(carried.state()).isEqualTo(DeliveryState.PENDING.name());
    assertThat(carried.targetUrl()).isEqualTo("https://merchant.test/hook");
    assertThat(carried.attemptsMade()).isEqualTo(1);
    assertThat(carried.attemptCap()).isEqualTo(3);
    assertThat(carried.nextAttemptAt()).isEqualTo(AT.toString());
    assertThat(carried.contentType()).isEqualTo("application/json");
    assertThat(carried.carriedPayload())
        .containsExactlyInAnyOrderEntriesOf(Map.of("id", List.of("evt_1")));
    assertThat(carried.carriedAttempts()).hasSize(1);
  }

  @Test
  void a_callback_nothing_was_attempted_under_carries_no_attempt_rather_than_a_blank_one() {
    RecordedCallback carried =
        RecordedEvidence.theCallbacksOf(
                List.of(CHARGE), List.of(aDelivery("whd_1", CHARGE)), Map.of())
            .getFirst();

    assertThat(carried.carriedAttempts()).isEmpty();
  }

  @Test
  void an_attempt_carries_the_headers_it_was_signed_with_and_the_answer_it_was_given() {
    RecordedAttempt carried =
        RecordedEvidence.theAttemptsOf(List.of(anAttempt(2, 500, TransportFailure.CONNECT_TIMEOUT)))
            .getFirst();

    assertThat(carried.number()).isEqualTo(2);
    assertThat(carried.at()).isEqualTo(AT.toString());
    assertThat(carried.targetUrl()).isEqualTo("https://merchant.test/hook");
    assertThat(carried.verdict()).isEqualTo(Acknowledgement.REFUSED.name());
    assertThat(carried.httpStatus()).isEqualTo(500);
    assertThat(carried.transportFailure()).isEqualTo(TransportFailure.CONNECT_TIMEOUT.name());
    assertThat(carried.responsePreview()).isEqualTo("boom");
    assertThat(carried.carriedHeaders())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("Content-Type", List.of("application/json"), "Signature", List.of(A_SIGNATURE)));
    assertThat(carried.theWireWasRecorded()).isTrue();
  }

  @Test
  void an_attempt_no_transport_broke_carries_no_failure() {
    RecordedAttempt carried =
        RecordedEvidence.theAttemptsOf(List.of(anAttempt(1, 200, null))).getFirst();

    assertThat(carried.transportFailure()).isNull();
  }

  @Test
  void the_snapshot_carries_the_objects_it_names_in_the_order_the_provider_named_them() {
    Map<String, String> names = new LinkedHashMap<>();
    names.put("merchantId", "m1");
    names.put("payment_intent", "pi_1");
    names.put("transactionType", "CREDIT");
    names.put("orderId", "pedido-1");

    RecordedObject carried =
        RecordedEvidence.theObjectOf(AddressedProviderObject.canonically(anObjectNaming(names)));

    assertThat(carried.carriedRelatedObjects().keySet())
        .as(
            "a reproduction bundle is evidence a reader compares, and a key order the runtime"
                + " picks would make two bundles of one object differ textually between runs")
        .containsExactly("merchantId", "payment_intent", "transactionType", "orderId");
  }

  @Test
  void the_snapshot_carries_the_identity_the_statuses_the_money_and_the_ledger_of_the_object() {
    RecordedObject carried =
        RecordedEvidence.theObjectOf(AddressedProviderObject.canonically(anObject()));

    assertThat(carried.providerObjectId()).isEqualTo(CHARGE);
    assertThat(carried.carriedIdentifiers()).containsExactly(CHARGE, RENAMED);
    assertThat(carried.nativeStatus()).isEqualTo("succeeded");
    assertThat(carried.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED.name());
    assertThat(carried.amount()).isEqualTo("10.00");
    assertThat(carried.currency()).isEqualTo("USD");
    assertThat(carried.authorizationOpen()).isFalse();
    assertThat(carried.refundsAccepted()).isTrue();
    assertThat(carried.origin()).isEqualTo(ObjectOrigin.PROVIDER_API.name());
    assertThat(carried.updatedAt()).isEqualTo(AT.toString());
    assertThat(carried.carriedRelatedObjects())
        .containsExactlyInAnyOrderEntriesOf(Map.of("payment_intent", "pi_1"));
    assertThat(carried.carriedLedger())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.sequence()).isEqualTo(1);
              assertThat(operation.kind()).isEqualTo(OperationKind.CAPTURE.name());
              assertThat(operation.amount()).isEqualTo("10.00");
              assertThat(operation.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED.name());
              assertThat(operation.at()).isEqualTo(AT.toString());
            });
  }

  @Test
  void
      a_capture_addressed_by_the_id_the_object_answers_to_now_says_so_and_one_addressed_by_an_old_id_says_that() {
    ReproductionEvidence byCanonical =
        RecordedEvidence.of(
            CHARGE,
            AddressedProviderObject.canonically(anObject()),
            AT,
            List.of(),
            false,
            List.of(),
            Map.of());
    ReproductionEvidence byRenamed =
        RecordedEvidence.of(
            RENAMED,
            new AddressedProviderObject(RENAMED, anObject()),
            AT,
            List.of(),
            false,
            List.of(),
            Map.of());

    assertThat(byCanonical.foundBy())
        .isEqualTo(RecordedEvidence.ADDRESSED_BY_ITS_PROVIDER_OBJECT_ID);
    assertThat(byRenamed.foundBy())
        .isEqualTo(RecordedEvidence.ADDRESSED_BY_AN_IDENTIFIER_IT_ONCE_ANSWERED_TO);
    assertThat(byRenamed.key()).isEqualTo(RENAMED);
  }

  @Test
  void a_capture_carries_the_effective_clock_and_says_whether_the_trail_outran_the_read() {
    ReproductionEvidence read =
        RecordedEvidence.of(
            CHARGE,
            AddressedProviderObject.canonically(anObject()),
            AT,
            List.of(aRow("aud_1", CHARGE)),
            true,
            List.of(aDelivery("whd_1", CHARGE)),
            Map.of("whd_1", List.of(anAttempt(1, 200, null))));

    assertThat(read.effectiveClock()).isEqualTo(AT.toString());
    assertThat(read.trailTruncated()).isTrue();
    assertThat(read.carriedTrail()).hasSize(1);
    assertThat(read.carriedCallbacks()).hasSize(1);
    assertThat(read.theExchangesThatSayWhatWasOnTheWire()).isEqualTo(1);
    assertThat(read.theAttemptsItCarries()).isEqualTo(1);
    assertThat(read.theOperationsItCarries()).isEqualTo(1);
  }

  @Test
  void a_row_naming_nothing_is_not_taken_for_a_row_of_this_object() {
    assertThat(RecordedEvidence.namesOneOf(List.of(CHARGE), null)).isFalse();
    assertThat(RecordedEvidence.namesOneOf(List.of(CHARGE), CHARGE)).isTrue();
    assertThat(RecordedEvidence.namesOneOf(List.of(), CHARGE)).isFalse();
  }

  private static RefusalSubject anObjectItWillNotName() {
    return new RefusalSubject.AnObjectItWillNotName("its contract names none");
  }

  private static AuditRecord aRow(String auditId, @Nullable String providerObjectId) {
    return new AuditRecord(
        auditId,
        RUN,
        null,
        PROVIDER,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        ProviderOutcome.SUCCESS,
        "POST",
        "/v1/charges",
        201,
        providerObjectId,
        null,
        "scn_1",
        2,
        AT,
        Map.of("Content-Type", List.of("application/json")),
        Map.of("amount", List.of("1000")),
        null);
  }

  private static AuditRecord refusing(RefusalSubject refusal) {
    return new AuditRecord(
        "aud_refused",
        RUN,
        null,
        PROVIDER,
        null,
        ObjectOrigin.CONTROL_API,
        AuditDirection.INBOUND_REQUEST,
        null,
        "POST",
        "/v1/charges",
        422,
        CHARGE,
        refusal,
        null,
        1,
        AT,
        Map.of(),
        Map.of(),
        null);
  }

  private static WebhookDelivery aDelivery(String deliveryId, @Nullable String providerObjectId) {
    return new WebhookDelivery(
        deliveryId,
        PROVIDER,
        RUN,
        "m1",
        "evt_1",
        "charge.succeeded",
        providerObjectId,
        "https://merchant.test/hook",
        new WebhookPayload(
            "application/json",
            Map.of(),
            "{}".getBytes(StandardCharsets.UTF_8),
            Map.of("id", List.of("evt_1"))),
        new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))),
        DeliveryState.PENDING,
        false,
        1,
        AT,
        AT,
        AT,
        null,
        null);
  }

  private static WebhookAttempt anAttempt(
      int number, int httpStatus, @Nullable TransportFailure failure) {
    return new WebhookAttempt(
        "whd_1",
        number,
        AT,
        "https://merchant.test/hook",
        Map.of("Content-Type", List.of("application/json"), "Signature", List.of(A_SIGNATURE)),
        httpStatus == 200 ? Acknowledgement.ACKNOWLEDGED : Acknowledgement.REFUSED,
        httpStatus,
        failure,
        httpStatus == 200 ? null : "boom");
  }

  private static ProviderObjectState anObjectNaming(Map<String, String> related) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(CHARGE),
        AKindOfObjectATestNames.ONE,
        "succeeded",
        NormalizedStatus.SUCCEEDED,
        Amount.of("10.00"),
        "USD",
        Ledger.empty(),
        false,
        false,
        false,
        related,
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState anObject() {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(CHARGE).answeringAlsoTo(RENAMED),
        AKindOfObjectATestNames.ONE,
        "succeeded",
        NormalizedStatus.SUCCEEDED,
        Amount.of("10.00"),
        "USD",
        Ledger.empty()
            .with(
                new MonetaryOperation(
                    1,
                    OperationKind.CAPTURE,
                    Amount.of("10.00"),
                    null,
                    null,
                    "succeeded",
                    NormalizedStatus.SUCCEEDED,
                    AT)),
        false,
        false,
        true,
        Map.of("payment_intent", "pi_1"),
        ObjectOrigin.PROVIDER_API,
        AT);
  }
}
