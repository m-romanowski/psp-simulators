package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowFaultInsteadOfOperationIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-unreached-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-unreached-user";
  private static final String PAGE_CODE = "grow-unreached-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String HELD = "120.50";
  private static final String STORY = "capture-retries";
  private static final String UNREACHED = "never-reached";

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
    configure();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void a_capture_the_simulator_never_ran_writes_no_provider_state_and_the_repeat_releases_it() {
    Held hold = aHold();
    theCaptureRetriesStory();

    ResponseEntity<byte[]> unreached = settle(hold);

    assertThat(unreached.getStatusCode().value()).isEqualTo(502);
    assertThat(unreached.getHeaders().getContentType()).hasToString("text/plain;charset=utf-8");
    assertThat(new String(unreached.getBody(), UTF_8))
        .startsWith("502 was answered in front of the provider.");
    assertThat(theHoldOf(hold))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE")
        .containsEntry("nativeStatus", "11");
    assertThat(operationsOf(hold)).extracting("kind").containsExactly("AUTHORIZATION");
    assertThat(balancesOf(hold)).containsEntry("capturedAmount", "0");

    ResponseEntity<byte[]> released = settle(hold);

    assertThat(released.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyOf(released)).contains("\"status\":1").doesNotContain("\"id\":808");
    assertThat(operationsOf(hold)).extracting("kind").containsExactly("AUTHORIZATION", "CAPTURE");
    assertThat(balancesOf(hold)).containsEntry("capturedAmount", HELD);
  }

  @Test
  void the_audit_row_of_a_capture_that_never_ran_names_where_the_fault_was_applied() {
    Held hold = aHold();
    theCaptureRetriesStory();

    settle(hold);
    settle(hold);

    List<AuditRecord> answered =
        audit.of(GrowLightApi.GROW, RUN).stream()
            .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
            .filter(record -> SETTLE.equals(record.path()))
            .toList();
    assertThat(answered).hasSize(2);
    assertThat(answered.get(0).delivered().transportFault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.SERVER_ERROR, FaultPlacement.INSTEAD_OF_THE_OPERATION));
    assertThat(answered.get(0).outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(answered.get(0).scenarioId()).isNotNull();
    assertThat(answered.get(1).delivered().transportFault()).isNull();
    assertThat(answered.get(1).outcome()).isEqualTo(ProviderOutcome.SUCCESS);
  }

  private void theCaptureRetriesStory() {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            STORY,
            "whenState",
            ScenarioStory.INITIAL_STATE,
            "thenState",
            UNREACHED,
            "match",
            Map.of("operation", "settleSuspendedTransaction"),
            "action",
            Map.of(
                "type", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
                "fault", "SERVER_ERROR",
                "httpStatus", 502)));
    createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "story", STORY,
            "whenState", UNREACHED,
            "match", Map.of("operation", "settleSuspendedTransaction"),
            "action", Map.of("type", "FIXED_SUCCESS")));
  }

  private void createScenario(Map<String, Object> body) {
    assertThat(
            http.post()
                .uri("/__simulator/grow/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, ignored) -> {})
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(201);
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
    GrowProcessOutcome outcome = processes.findByProcessId(RUN, processId).orElseThrow().outcome();
    assertThat(outcome).isNotNull();
    return new Held(
        processId,
        String.valueOf(outcome.transactionId()),
        String.valueOf(outcome.transactionToken()));
  }

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
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    return (String) data.get("processId");
  }

  private ResponseEntity<byte[]> settle(Held hold) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    form.add("sum", HELD);
    return http.post()
        .uri(SETTLE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(byte[].class);
  }

  private Map<String, Object> theHoldOf(Held hold) {
    return GrowWireText.asJson(
        http.get()
            .uri("/__simulator/grow/state/" + hold.transactionId() + "?testRunId=" + RUN.value())
            .retrieve()
            .toEntity(byte[].class)
            .getBody());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> operationsOf(Held hold) {
    return (List<Map<String, Object>>) theHoldOf(hold).get("operations");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> balancesOf(Held hold) {
    return (Map<String, Object>) theHoldOf(hold).get("derivedBalances");
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

  private static String bodyOf(ResponseEntity<byte[]> response) {
    return GrowWireText.readable(new String(Objects.requireNonNull(response.getBody()), UTF_8));
  }
}
