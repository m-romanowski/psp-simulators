package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowCallbackDeliveryIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-callback-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-callback-user";
  private static final String PAGE_CODE = "grow-callback-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  @LocalServerPort private int port;

  @Autowired private GrowPaymentProcesses processes;
  @Autowired private AuditTrail audit;

  private RestClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure(FIXED_NOW);
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void delivers_the_callback_to_the_notify_url_the_merchant_configured() {
    String processId = createTheHold();

    Map<String, Object> job = sendTheCallback(processId, null);

    assertThat(job).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 1);
    assertThat((String) job.get("webhookJobId")).startsWith("whjob_");
    assertThat((String) job.get("eventId")).startsWith("evt_");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> created = (List<Map<String, Object>>) job.get("deliveries");
    assertThat(created).hasSize(1);
    assertThat(created.get(0))
        .containsEntry("webhookJobId", job.get("webhookJobId"))
        .containsEntry("eventId", job.get("eventId"));

    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    assertThat(received.contentType()).isEqualTo("application/x-www-form-urlencoded");
    assertThat(received.fields())
        .containsEntry("status", "1")
        .containsEntry("data[processId]", processId)
        .containsEntry("data[statusCode]", "11")
        .containsEntry("data[sum]", "120.50")
        .containsEntry("data[customFields][cField1]", "session-abc")
        .containsEntry("data[paymentDate]", "07/09/26");
  }

  @Test
  void a_preset_that_duplicates_the_callback_posts_it_twice_under_one_event_id() {
    select("webhook-duplicate");
    String processId = createTheHold();

    Map<String, Object> job = sendTheCallback(processId, null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> created = (List<Map<String, Object>>) job.get("deliveries");
    assertThat(created).hasSize(2);
    assertThat(created)
        .extracting(delivery -> delivery.get("eventId"))
        .containsOnly(job.get("eventId"));
    assertThat(created.get(0).get("webhookJobId")).isNotEqualTo(created.get(1).get("webhookJobId"));
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body()).isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void the_trail_filtered_by_the_rule_a_tester_wrote_shows_both_deliveries_it_duplicated() {
    String duplicating = select("webhook-duplicate");
    String processId = createTheHold();

    sendTheCallback(processId, null);

    assertThat(trailOf(duplicating))
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row).containsEntry("direction", "OUTBOUND_WEBHOOK_ATTEMPT");
              assertThat(row).containsEntry("scenarioId", duplicating);
              assertThat(row).containsEntry("providerObjectId", processId);
            });
  }

  @Test
  void the_trail_filtered_by_the_rule_that_dropped_a_callback_shows_the_post_never_made() {
    String dropping = writeADroppedDelivery();
    String processId = createTheHold();

    sendTheCallback(processId, null);

    assertThat(trailOf(dropping))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).containsEntry("direction", "OUTBOUND_WEBHOOK_DROPPED");
              assertThat(row).containsEntry("scenarioId", dropping);
              assertThat(row).containsEntry("providerObjectId", processId);
              assertThat(row).containsEntry("attempt", 0);
              assertThat(row).containsEntry("status", null);
              assertThat(row).containsEntry("outcome", null);
            });
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void a_delivery_no_rule_shaped_names_no_rule_on_the_read_a_tester_gets_back() {
    String processId = createTheHold();

    sendTheCallback(processId, null);

    assertThat(listDeliveries().get(0)).containsEntry("shapedBy", null);
  }

  @Test
  void a_preset_that_holds_the_callback_back_keeps_the_merchant_waiting_for_the_clock() {
    select("webhook-delayed");
    String processId = createTheHold();

    Map<String, Object> job = sendTheCallback(processId, null);

    assertThat(job).containsEntry("status", "PENDING").containsEntry("attemptsMade", 0);
    assertThat(merchant.received()).isEmpty();

    configure(FIXED_NOW.plusSeconds(5));

    assertThat(deliverDue()).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_rule_that_drops_the_callback_keeps_the_record_and_posts_nothing_ever() {
    String dropping = writeADroppedDelivery();
    String processId = createTheHold();

    Map<String, Object> job = sendTheCallback(processId, null);

    assertThat(job).containsEntry("status", "DROPPED").containsEntry("attemptsMade", 0);
    assertThat(merchant.received()).isEmpty();

    configure(FIXED_NOW.plusSeconds(600));

    assertThat(deliverDue()).isZero();
    assertThat(merchant.received()).isEmpty();
    assertThat(listDeliveries()).hasSize(1);
    assertThat(listDeliveries().get(0))
        .containsEntry("status", "DROPPED")
        .containsEntry("shapedBy", dropping);
  }

  @Test
  void the_process_it_reported_on_is_left_at_its_outcome() {
    String processId = createTheHold();

    sendTheCallback(processId, null);

    GrowPaymentProcess process = processes.findByProcessId(RUN, processId).orElseThrow();
    assertThat(process.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(process.nativeStatusCode()).isEqualTo("11");
    assertThat(process.outcome()).isNotNull();
    assertThat(process.outcome().transactionId())
        .as("the outcome names a transaction of its own, not the process of the same six digits")
        .isNotEqualTo(processId)
        .matches("[1-9][0-9]{5}");
    assertThat(process.outcome().transactionToken()).matches("[0-9a-f]{18}");
  }

  @Test
  void lists_the_delivery_and_its_attempt_ledger() {
    String processId = createTheHold();
    Map<String, Object> job = sendTheCallback(processId, null);

    List<Map<String, Object>> deliveries = listDeliveries();

    assertThat(deliveries).hasSize(1);
    Map<String, Object> delivery = deliveries.get(0);
    assertThat(delivery)
        .containsEntry("webhookJobId", job.get("webhookJobId"))
        .containsEntry("eventId", job.get("eventId"))
        .containsEntry("eventType", "payment_process.completed")
        .containsEntry("providerObjectId", processId)
        .containsEntry("merchantId", MERCHANT)
        .containsEntry("targetUrl", merchant.url())
        .containsEntry("status", "DELIVERED")
        .containsEntry("attemptsMade", 1)
        .containsEntry("attemptCap", 6);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> attempts = (List<Map<String, Object>>) delivery.get("attempts");
    assertThat(attempts).hasSize(1);
    assertThat(attempts.get(0))
        .containsEntry("number", 1)
        .containsEntry("verdict", "ACKNOWLEDGED")
        .containsEntry("httpStatus", 200)
        .containsEntry("attemptedAt", FIXED_NOW.toString());
  }

  @Test
  void the_attempt_appears_in_audit_with_the_callback_body_sanitized() {
    String processId = createTheHold();

    sendTheCallback(processId, null);

    AuditRecord attempt = onlyCallbackAttemptInAudit();
    assertThat(attempt.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(attempt.httpStatus()).isEqualTo(200);
    assertThat(attempt.providerObjectId()).isEqualTo(processId);
    assertThat(attempt.attempt()).isEqualTo(1);
    assertThat(attempt.path()).isEqualTo(merchant.url());
    assertThat(attempt.resolutionRule()).isNull();
    assertThat(attempt.sanitizedBody())
        .containsEntry("data[customFields][cField1]", List.of(Redaction.REDACTED))
        .containsEntry("data[processToken]", List.of(Redaction.REDACTED))
        .containsEntry("data[statusCode]", List.of("11"));
    assertThat(merchant.received().get(0).fields())
        .containsEntry("data[customFields][cField1]", "session-abc");
  }

  @Test
  void the_read_previews_the_payload_and_the_headers_before_the_merchant_has_seen_them() {
    String processId = createTheHold();

    Map<String, Object> job = sendTheCallbackIn(processId, 60_000);

    assertThat(job).containsEntry("status", "PENDING").containsEntry("attemptsMade", 0);
    assertThat(merchant.received()).isEmpty();

    Map<String, Object> delivery = listDeliveries().get(0);
    assertThat(delivery).containsEntry("contentType", "application/x-www-form-urlencoded");
    assertThat(delivery.get("headers"))
        .isEqualTo(Map.of("Content-Type", List.of("application/x-www-form-urlencoded")));

    @SuppressWarnings("unchecked")
    Map<String, List<String>> payload = (Map<String, List<String>>) delivery.get("payload");
    assertThat(payload)
        .containsEntry("data[processId]", List.of(processId))
        .containsEntry("data[statusCode]", List.of("11"))
        .containsEntry("data[customFields][cField1]", List.of(Redaction.REDACTED))
        .containsEntry("data[processToken]", List.of(Redaction.REDACTED));
  }

  @Test
  void an_attempt_that_reached_nobody_reads_back_as_a_transport_failure() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.DropTheConnection());
    String processId = createTheHold();

    sendTheCallback(processId, null);

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> attempts =
        (List<Map<String, Object>>) listDeliveries().get(0).get("attempts");
    assertThat(attempts).hasSize(1);
    assertThat(attempts.get(0))
        .containsEntry("verdict", "REFUSED")
        .containsEntry("transportFailure", "CONNECTION_ERROR");
    assertThat(attempts.get(0).get("httpStatus")).isNull();
    assertThat(attempts.get(0).get("responsePreview")).isNull();
  }

  @Test
  void a_refusing_merchant_is_called_the_six_times_grow_publishes_and_then_visibly_gives_up() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));
    String processId = createTheHold();

    Map<String, Object> job = sendTheCallback(processId, null);
    assertThat(job).containsEntry("status", "PENDING").containsEntry("attemptsMade", 1);
    assertThat(job).containsEntry("scheduledAt", FIXED_NOW.plusSeconds(1).toString());

    for (long due : new long[] {1, 6, 36, 156, 456}) {
      configure(FIXED_NOW.plusSeconds(due));
      assertThat(deliverDue()).isEqualTo(1);
    }

    assertThat(merchant.received()).hasSize(6);
    Map<String, Object> delivery = listDeliveries().get(0);
    assertThat(delivery).containsEntry("status", "EXHAUSTED").containsEntry("attemptsMade", 6);
    assertThat(delivery.get("nextAttemptAt")).isNull();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> attempts = (List<Map<String, Object>>) delivery.get("attempts");
    assertThat(attempts)
        .extracting(a -> a.get("attemptedAt"))
        .containsExactly(
            FIXED_NOW.toString(),
            FIXED_NOW.plusSeconds(1).toString(),
            FIXED_NOW.plusSeconds(6).toString(),
            FIXED_NOW.plusSeconds(36).toString(),
            FIXED_NOW.plusSeconds(156).toString(),
            FIXED_NOW.plusSeconds(456).toString());
    assertThat(attempts).allSatisfy(a -> assertThat(a).containsEntry("verdict", "REFUSED"));
    assertThat(attempts.get(0)).containsEntry("responsePreview", "later");
  }

  @Test
  void nothing_is_attempted_before_the_simulator_clock_reaches_the_next_attempt() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));
    String processId = createTheHold();
    sendTheCallback(processId, null);

    configure(FIXED_NOW.plusMillis(999));

    assertThat(deliverDue()).isZero();
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void an_exhausted_delivery_can_be_retried_from_the_control_api() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));
    String processId = createTheHold();
    Map<String, Object> job = sendTheCallback(processId, null);
    for (long due : new long[] {1, 6, 36, 156, 456}) {
      configure(FIXED_NOW.plusSeconds(due));
      deliverDue();
    }
    assertThat(listDeliveries().get(0)).containsEntry("status", "EXHAUSTED");

    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(200, "OK"));
    Map<String, Object> retried =
        webhookRequest(
            Map.of(
                "testRunId",
                RUN.value(),
                "operation",
                "RETRY",
                "webhookJobId",
                job.get("webhookJobId")));

    assertThat(retried).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 7);
    assertThat(merchant.received()).hasSize(7);
  }

  @Test
  void refuses_to_deliver_to_a_notify_url_the_test_run_did_not_configure() {
    MultiValueMap<String, Object> form = hold();
    form.set("notifyUrl", "https://elsewhere.example/hook");
    String processId = createTheHold(form);

    ResponseEntity<String> response =
        http.post()
            .uri("/__simulator/grow/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).contains("not among the callback targets configured");
    assertThat(response.getBody()).doesNotContain("elsewhere.example");
    assertThat(listDeliveries()).isEmpty();
  }

  @Test
  void answers_404_for_a_process_this_test_run_does_not_hold() {
    ResponseEntity<String> response =
        http.post()
            .uri("/__simulator/grow/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "providerObjectId", "999999"))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void answers_400_when_no_object_is_named_to_send_a_callback_for() {
    ResponseEntity<String> response =
        http.post()
            .uri("/__simulator/grow/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value()))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).contains("providerObjectId is required");
  }

  @Test
  void a_reset_clears_the_deliveries_of_the_test_run() {
    String processId = createTheHold();
    sendTheCallback(processId, null);

    Map<String, Object> outcome = reset();

    assertThat((Integer) outcome.get("clearedObjects")).isEqualTo(3);
    assertThat(listDeliveries()).isEmpty();
  }

  @Test
  void a_process_reaches_its_outcome_once_however_many_callers_try() {
    String processId = createTheHold();
    GrowPaymentProcess read = processes.findByProcessId(RUN, processId).orElseThrow();

    boolean first =
        processes.reachOutcome(
            read.reaching(
                new GrowProcessOutcome(
                    GrowProcessOutcome.Kind.COMPLETED,
                    "111111",
                    "aaa",
                    "22222222",
                    null,
                    "07/09/26",
                    null),
                "11",
                "suspended",
                NormalizedStatus.READY_FOR_CAPTURE,
                FIXED_NOW));
    boolean second =
        processes.reachOutcome(
            read.reaching(
                new GrowProcessOutcome(
                    GrowProcessOutcome.Kind.COMPLETED,
                    "999999",
                    "bbb",
                    "88888888",
                    null,
                    "07/09/26",
                    null),
                "11",
                "suspended",
                NormalizedStatus.READY_FOR_CAPTURE,
                FIXED_NOW));

    assertThat(first).isTrue();
    assertThat(second).isFalse();
    GrowPaymentProcess stored = processes.findByProcessId(RUN, processId).orElseThrow();
    assertThat(stored.outcome()).isNotNull();
    assertThat(stored.outcome().transactionId()).isEqualTo("111111");
  }

  @Test
  void writing_back_a_process_that_does_not_exist_creates_nothing() {
    String processId = createTheHold();
    GrowPaymentProcess stored = processes.findByProcessId(RUN, processId).orElseThrow();
    GrowPaymentProcess elsewhere =
        inTestRun(
            stored.reaching(
                GrowProcessOutcome.failed(), "0", "unused", NormalizedStatus.FAILED, FIXED_NOW),
            TestRunId.of("no-such-run"));

    assertThat(processes.reachOutcome(elsewhere)).isFalse();
    assertThat(processes.findByProcessId(TestRunId.of("no-such-run"), processId)).isEmpty();
  }

  private static GrowPaymentProcess inTestRun(GrowPaymentProcess process, TestRunId testRunId) {
    return new GrowPaymentProcess(
        testRunId,
        process.processId(),
        process.processToken(),
        process.merchantId(),
        process.chargeType(),
        process.chargeSum(),
        process.description(),
        process.saveCardToken(),
        process.successUrl(),
        process.cancelUrl(),
        process.notifyUrl(),
        process.payerFullName(),
        process.payerPhone(),
        process.payerEmail(),
        process.customFields(),
        process.hostedLink(),
        process.nativeStatusCode(),
        process.nativeStatus(),
        process.normalizedStatus(),
        process.outcome(),
        process.origin(),
        process.createdAt(),
        process.updatedAt());
  }

  private String createTheHold() {
    return createTheHold(hold());
  }

  private String createTheHold(MultiValueMap<String, Object> form) {
    Map<String, Object> body =
        GrowWireText.asJson(
            http.post()
                .uri(CREATE)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    return (String) data.get("processId");
  }

  private String select(String preset) {
    List<Map<String, Object>> written =
        http.post()
            .uri("/__simulator/grow/scenarios/presets")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "preset", preset))
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(written).hasSize(1);
    return (String) written.get(0).get("scenarioId");
  }

  private List<Map<String, Object>> trailOf(String scenarioId) {
    return http.get()
        .uri("/__simulator/grow/audit?testRunId=" + RUN.value() + "&scenarioId=" + scenarioId)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private String writeADroppedDelivery() {
    Map<String, Object> written =
        http.post()
            .uri("/__simulator/grow/scenarios")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "action", Map.of("type", "DELIVERY_DROPPED")))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return (String) written.get("scenarioId");
  }

  private Map<String, Object> sendTheCallback(String processId, String eventType) {
    Map<String, Object> request =
        eventType == null
            ? Map.of("testRunId", RUN.value(), "providerObjectId", processId)
            : Map.of(
                "testRunId", RUN.value(), "providerObjectId", processId, "eventType", eventType);
    return webhookRequest(request);
  }

  private Map<String, Object> sendTheCallbackIn(String processId, long delayMs) {
    return webhookRequest(
        Map.of("testRunId", RUN.value(), "providerObjectId", processId, "delayMs", delayMs));
  }

  private Map<String, Object> webhookRequest(Map<String, Object> request) {
    return http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private int deliverDue() {
    Map<String, Object> answered =
        http.post()
            .uri("/__simulator/grow/webhooks/deliver-due")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value()))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return (Integer) answered.get("attempted");
  }

  private List<Map<String, Object>> listDeliveries() {
    return http.get()
        .uri("/__simulator/grow/webhooks?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private AuditRecord onlyCallbackAttemptInAudit() {
    List<AuditRecord> attempts =
        audit.of(GrowLightApi.GROW, RUN).stream()
            .filter(r -> r.direction() == AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT)
            .toList();
    assertThat(attempts).hasSize(1);
    return attempts.get(0);
  }

  private void configure(Instant fixedNow) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", fixedNow.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", USER_ID, "pageCode", PAGE_CODE))),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> reset() {
    return http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", "120.50");
    form.add("saveCardToken", "1");
    form.add("description", "A hold");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", merchant.url());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    form.add("pageField[email]", "payer@merchant.example");
    form.add("cField1", "session-abc");
    form.add("cField2", "intent-99");
    return form;
  }
}
