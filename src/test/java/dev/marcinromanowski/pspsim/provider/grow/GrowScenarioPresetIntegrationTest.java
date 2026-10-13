package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowScenarioPresetIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-preset-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-preset-user";
  private static final String PAGE_CODE = "grow-preset-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String LOOKUP = "/api/light/server/1.0/getTransactionInfo";
  private static final String HELD = "120.50";

  @LocalServerPort private int port;

  @Autowired private GrowPaymentProcesses processes;
  @Autowired private AuditTrail audit;

  private RestClient http;
  private HttpClient wire;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    merchant = new LocalMerchantServer();
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void a_hold_cancellation_becomes_a_settlement_and_a_refund_a_harness_can_read_back() {
    List<Map<String, Object>> created = select("cancel-hold-success", Map.of());

    assertThat(created)
        .extracting(rule -> rule.get("operation"))
        .containsExactly("settleSuspendedTransaction", "refundTransaction");
    assertThat(scenarios())
        .extracting(rule -> rule.get("operation"))
        .containsExactlyInAnyOrder("settleSuspendedTransaction", "refundTransaction");

    Held hold = aHold();
    assertThat(bodyOf(settle(hold, HELD))).contains("\"status\":1");
    assertThat(lastOutboundAudit().scenarioId()).isEqualTo(created.get(0).get("scenarioId"));
    assertThat(bodyOf(refund(hold, HELD))).contains("\"status\":1");
    assertThat(lastOutboundAudit().scenarioId()).isEqualTo(created.get(1).get("scenarioId"));

    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "REFUNDED");
  }

  @Test
  void a_hold_cancellation_settles_the_full_held_amount_and_refunds_that_same_amount() {
    select("cancel-hold-success", Map.of());
    Held hold = aHold();

    settle(hold, HELD);
    refund(hold, HELD);

    assertThat(balancesOf(hold))
        .containsEntry("capturedAmount", HELD)
        .containsEntry("refundedAmount", HELD)
        .containsEntry("refundableRemainder", "0.00");
  }

  @Test
  void a_failed_card_add_refuses_the_registration_the_payer_would_have_been_sent_to() {
    List<Map<String, Object>> created = select("card-add-failed", Map.of());

    assertThat(created)
        .extracting(rule -> rule.get("operation"))
        .containsExactly("createPaymentProcess");

    ResponseEntity<byte[]> refused =
        http.post()
            .uri(CREATE)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(registration())
            .retrieve()
            .toEntity(byte[].class);

    assertThat(refused.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyOf(refused))
        .contains("\"status\":0")
        .contains("\"id\":809")
        .doesNotContain("processId");
    assertThat(lastOutboundAudit().scenarioId()).isEqualTo(created.get(0).get("scenarioId"));
  }

  @Test
  void a_successful_card_add_names_the_call_the_registration_is_made_with() {
    List<Map<String, Object>> created = select("card-add-success", Map.of());

    assertThat(created)
        .extracting(rule -> rule.get("operation"))
        .containsExactly("createPaymentProcess");

    ResponseEntity<byte[]> registered =
        http.post()
            .uri(CREATE)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(registration())
            .retrieve()
            .toEntity(byte[].class);

    assertThat(bodyOf(registered)).contains("\"status\":1");
    assertThat(lastOutboundAudit().scenarioId()).isEqualTo(created.get(0).get("scenarioId"));
  }

  @Test
  void a_capture_decline_declines_the_capture_and_leaves_the_pre_authorization_alone() {
    declineEverythingWith813();
    select("preauth-success-capture-decline", Map.of());

    Held hold = aHold();
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "READY_FOR_CAPTURE");

    String declined = bodyOf(settle(hold, HELD));

    assertThat(declined)
        .contains("\"status\":0")
        .contains("\"id\":809")
        .doesNotContain("statusCode");
    assertThat(state(hold.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE")
        .containsEntry("nativeStatus", "11");
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void a_capture_whose_response_was_lost_still_released_the_hold_and_a_lookup_says_so() {
    select("capture-accepted-response-lost", Map.of());
    Held hold = aHold();

    IOException lost = (IOException) catchThrowable(() -> settleOnTheWire(hold)).getCause();

    assertThat(lost).hasMessageContaining("fixed content-length");
    assertThat(numberAfter(lost.getMessage(), "bytes received: "))
        .isLessThan(numberAfter(lost.getMessage(), "fixed content-length: "));
    assertThat(bodyOf(lookup(hold))).contains("\"statusCode\":2");
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_successful_pre_authorization_and_capture_shield_the_two_calls_they_name_and_no_other() {
    declineEverythingWith813();

    List<Map<String, Object>> created = select("preauth-success-capture-success", Map.of());

    Held hold = aHold();
    assertThat(bodyOf(settle(hold, HELD)))
        .contains("\"status\":1")
        .contains("\"statusCode\":\"2\"");
    assertThat(lastOutboundAudit().scenarioId()).isEqualTo(created.get(1).get("scenarioId"));
    assertThat(lastOutboundAudit().outcome()).isEqualTo(ProviderOutcome.SUCCESS);

    assertThat(bodyOf(lookup(hold))).contains("\"status\":0").contains("\"id\":813");
  }

  private AuditRecord lastOutboundAudit() {
    return audit.of(GrowLightApi.GROW, RUN).stream()
        .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .reduce((first, second) -> second)
        .orElseThrow();
  }

  @Test
  void a_full_refund_names_the_refund_alone_and_leaves_the_capture_to_the_provider() {
    List<Map<String, Object>> created = select("refund-full-success", Map.of());

    assertThat(created)
        .extracting(rule -> rule.get("operation"))
        .containsExactly("refundTransaction");

    Held hold = aHold();
    settle(hold, HELD);
    assertThat(lastOutboundAudit().scenarioId()).isNull();

    assertThat(bodyOf(refund(hold, HELD))).contains("\"status\":1");
    assertThat(lastOutboundAudit().scenarioId()).isEqualTo(created.get(0).get("scenarioId"));
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "REFUNDED");
  }

  @Test
  void a_preset_narrowed_to_one_hold_shields_that_hold_and_leaves_every_other_declined() {
    Held shielded = aHold();
    Held untouched = aHold();
    declineEverythingWith813();

    select(
        "cancel-hold-success",
        Map.of("match", Map.of("providerObjectId", shielded.transactionId())));

    assertThat(bodyOf(settle(shielded, HELD))).contains("\"statusCode\":\"2\"");
    assertThat(bodyOf(refund(shielded, HELD))).contains("\"status\":1");
    assertThat(state(shielded.transactionId())).containsEntry("normalizedStatus", "REFUNDED");

    assertThat(bodyOf(settle(untouched, HELD))).contains("\"id\":813");
    assertThat(state(untouched.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
  }

  @Test
  void a_preset_whose_flow_creates_the_object_refuses_a_narrowing_to_an_object_id() {
    Held hold = aHold();

    ResponseEntity<Object> refused =
        selectResponse(
            "preauth-success-capture-decline",
            Map.of("match", Map.of("providerObjectId", hold.transactionId())));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refusalOf(refused)).containsEntry("properties", List.of("match.providerObjectId"));
    assertThat(String.valueOf(refusalOf(refused).get("message")))
        .contains("createPaymentProcess", "can never fire");
    assertThat(scenarios()).isEmpty();
  }

  @Test
  void a_preset_narrowed_by_a_predicate_grow_cannot_supply_is_refused_and_creates_no_rule() {
    ResponseEntity<Object> refused =
        selectResponse("cancel-hold-success", Map.of("match", Map.of("currency", "PLN")));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refusalOf(refused)).containsEntry("properties", List.of("match.currency"));
    assertThat(scenarios()).isEmpty();
  }

  @ParameterizedTest(name = "the preset {0} is refused rather than expanded into a silent rule")
  @ValueSource(
      strings = {
        "preauth-success-capture-timeout-then-success",
        "refund-partial-then-full",
        "webhook-out-of-order",
        "webhook-invalid-signature",
        "status-lookup-processing-then-success",
        "status-lookup-processing-then-failed"
      })
  void a_preset_grow_cannot_express_is_refused_and_creates_no_rule(String preset) {
    ResponseEntity<Object> refused = selectResponse(preset, Map.of());

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refusalOf(refused)).containsEntry("properties", List.of("preset"));
    assertThat(scenarios()).isEmpty();
  }

  @Test
  void a_gateway_failure_preset_answers_the_first_capture_without_running_it_and_the_repeat_wins() {
    List<Map<String, Object>> created = select("psp-5xx-then-success", Map.of());
    Held hold = aHold();

    ResponseEntity<byte[]> unreached = settle(hold, HELD);

    assertThat(unreached.getStatusCode().value()).isEqualTo(502);
    assertThat(unreached.getHeaders().getContentType()).hasToString("text/plain;charset=utf-8");
    assertThat(new String(unreached.getBody(), UTF_8))
        .startsWith("502 was answered in front of the provider.");
    assertThat(state(hold.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE")
        .containsEntry("nativeStatus", "11");
    assertThat(operationsOf(hold)).extracting("kind").containsExactly("AUTHORIZATION");
    assertThat(balancesOf(hold)).containsEntry("capturedAmount", "0");

    ResponseEntity<byte[]> released = settle(hold, HELD);

    assertThat(released.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyOf(released)).contains("\"status\":1").doesNotContain("\"id\":808");
    assertThat(operationsOf(hold)).extracting("kind").containsExactly("AUTHORIZATION", "CAPTURE");
    assertThat(balancesOf(hold)).containsEntry("capturedAmount", HELD);
    assertThat(lastOutboundAudit().scenarioId()).isEqualTo(created.get(1).get("scenarioId"));
  }

  @Test
  void a_gateway_failure_preset_answers_the_first_capture_of_every_payment_it_was_selected_for() {
    select("psp-5xx-then-success", Map.of());
    Held first = aHold();
    Held second = aHold();

    assertThat(settle(first, HELD).getStatusCode().value()).isEqualTo(502);
    assertThat(settle(second, HELD).getStatusCode().value()).isEqualTo(502);

    assertThat(bodyOf(settle(first, HELD))).contains("\"status\":1");
    assertThat(bodyOf(settle(second, HELD))).contains("\"status\":1");
    assertThat(state(first.transactionId())).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(state(second.transactionId())).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_gateway_failure_preset_shields_its_repeat_from_a_flat_rule_that_declines_the_same_call() {
    Held hold = aHold();
    select("psp-5xx-then-success", Map.of());
    declineEverythingWith813();

    assertThat(settle(hold, HELD).getStatusCode().value()).isEqualTo(502);

    assertThat(bodyOf(settle(hold, HELD))).contains("\"status\":1").doesNotContain("\"id\":813");
  }

  @Test
  void a_preset_whose_story_a_stored_step_keys_on_the_whole_run_is_refused_and_writes_no_rule() {
    aStepOfTheGatewayFailureStoryKeyedOnTheWholeRun();

    ResponseEntity<Object> refused = selectResponse("psp-5xx-then-success", Map.of());

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refusalOf(refused)).containsEntry("properties", List.of("keyedOn"));
    assertThat(String.valueOf(refusalOf(refused).get("message")))
        .contains("psp-5xx-then-success", "FLOW", "TEST_RUN", "could never see");
    assertThat(scenarios()).hasSize(1);
  }

  private void aStepOfTheGatewayFailureStoryKeyedOnTheWholeRun() {
    http.post()
        .uri("/__simulator/grow/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "story", "psp-5xx-then-success",
                "keyedOn", "TEST_RUN",
                "whenState", "Started",
                "thenState", "counted",
                "match", Map.of("operation", "settleSuspendedTransaction"),
                "action", Map.of("type", "FIXED_SUCCESS")))
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> operationsOf(Held hold) {
    return (List<Map<String, Object>>) state(hold.transactionId()).get("operations");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> balancesOf(Held hold) {
    return (Map<String, Object>) state(hold.transactionId()).get("derivedBalances");
  }

  private void declineEverythingWith813() {
    http.post()
        .uri("/__simulator/grow/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "action",
                Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")))
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> select(String preset, Map<String, Object> narrowing) {
    ResponseEntity<Object> response = selectResponse(preset, narrowing);
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    return (List<Map<String, Object>>) Objects.requireNonNull(response.getBody());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> refusalOf(ResponseEntity<Object> response) {
    return (Map<String, Object>) Objects.requireNonNull(response.getBody());
  }

  private ResponseEntity<Object> selectResponse(String preset, Map<String, Object> narrowing) {
    Map<String, Object> body = new HashMap<>(narrowing);
    body.put("testRunId", RUN.value());
    body.put("preset", preset);
    return http.post()
        .uri("/__simulator/grow/scenarios/presets")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Object>() {});
  }

  private List<Map<String, Object>> scenarios() {
    return http.get()
        .uri("/__simulator/grow/scenarios?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private record Held(String processId, String transactionId, String transactionToken) {}

  private Held aHold() {
    String processId = createTheHold();
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
    GrowProcessOutcome outcome =
        Objects.requireNonNull(processes.findByProcessId(RUN, processId).orElseThrow().outcome());
    return new Held(
        processId,
        String.valueOf(outcome.transactionId()),
        String.valueOf(outcome.transactionToken()));
  }

  @SuppressWarnings("unchecked")
  private String createTheHold() {
    Map<String, Object> body =
        GrowWireText.asJson(
            http.post()
                .uri(CREATE)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(hold())
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    assertThat(body).containsEntry("status", 1);
    return (String) ((Map<String, Object>) body.get("data")).get("processId");
  }

  private ResponseEntity<byte[]> settle(Held hold, String sum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    form.add("sum", sum);
    return post(SETTLE, form);
  }

  private ResponseEntity<byte[]> refund(Held hold, String refundSum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    form.add("refundSum", refundSum);
    return post(REFUND, form);
  }

  private ResponseEntity<byte[]> lookup(Held hold) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    return post(LOOKUP, form);
  }

  private void settleOnTheWire(Held hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("userId", USER_ID);
    form.put("transactionId", hold.transactionId());
    form.put("transactionToken", hold.transactionToken());
    form.put("sum", HELD);
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + SETTLE))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)))
            .build();
    try {
      wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (IOException lost) {
      throw new IllegalStateException(lost);
    }
  }

  private static long numberAfter(String message, String label) {
    int start = message.indexOf(label) + label.length();
    int end = start;
    while (end < message.length() && Character.isDigit(message.charAt(end))) {
      end++;
    }
    return Long.parseLong(message.substring(start, end));
  }

  private ResponseEntity<byte[]> post(String path, MultiValueMap<String, Object> form) {
    return http.post()
        .uri(path)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(byte[].class);
  }

  private Map<String, Object> state(String transactionId) {
    return http.get()
        .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private void configure() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
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

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }

  private MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", HELD);
    form.add("saveCardToken", "1");
    form.add("description", "A hold");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", merchant.url());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    form.add("cField2", "intent-99");
    return form;
  }

  private MultiValueMap<String, Object> registration() {
    MultiValueMap<String, Object> form = hold();
    form.set("chargeType", "3");
    form.set("sum", "1");
    form.remove("cField2");
    return form;
  }

  private static String bodyOf(ResponseEntity<byte[]> response) {
    return GrowWireText.readable(new String(Objects.requireNonNull(response.getBody()), UTF_8));
  }
}
