package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GrowScenarioIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-scenario-it");
  private static final String USER_ID = "grow-scenario-user";
  private static final String PAGE_CODE = "grow-scenario-page";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  @LocalServerPort private int port;

  @Autowired private AuditTrail audit;
  @Autowired private ObjectMapper json;

  private RestClient http;

  @BeforeEach
  void configureTheMerchantAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            "merchant-1",
                            "configuration",
                            Map.of("userId", USER_ID, "pageCode", PAGE_CODE)))))
        .retrieve()
        .toBodilessEntity();
  }

  @AfterEach
  void unconfigureTheMerchantAccount() {
    reset();
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

  @Test
  void a_scenario_configured_through_the_control_api_declines_a_hold_on_the_wire() {
    String scenarioId = createScenario(declineEveryRequestWith("813"));

    ResponseEntity<byte[]> response = post(hold());

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("text/html; charset=UTF-8"));
    Map<String, Object> body = parse(response);
    assertThat(body).containsEntry("status", 0).containsEntry("data", "");

    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) body.get("err");
    assertThat(error).containsEntry("id", 813).containsEntry("message", "");
    assertThat(outboundAudit().providerObjectId()).isNull();
    assertThat(outboundAudit().scenarioId()).isEqualTo(scenarioId);
    assertThat(outboundAudit().outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void the_decline_is_chosen_by_the_scenario_and_not_by_the_credentials() {
    Map<String, Object> body = parse(post(hold()));
    assertThat(body).containsEntry("status", 1);

    createScenario(declineEveryRequestWith("813"));

    assertThat(parse(post(hold()))).containsEntry("status", 0);
  }

  @Test
  void every_hold_over_the_wire_begins_its_own_story_so_the_first_step_meets_each_of_them() {
    String declines =
        createScenario(
            Map.of(
                "testRunId", RUN.value(),
                "story", "hold-retries",
                "whenState", "Started",
                "thenState", "asked",
                "action", Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")));
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            "hold-retries",
            "whenState",
            "asked",
            "action",
            Map.of("type", "FIXED_SUCCESS")));

    assertThat(parse(post(hold()))).containsEntry("status", 0);
    assertThat(outboundAudit().scenarioId()).isEqualTo(declines);
    assertThat(parse(post(hold()))).containsEntry("status", 0);
    assertThat(outboundAudit().scenarioId()).isEqualTo(declines);
  }

  @Test
  void a_story_keyed_on_the_test_run_declines_the_first_hold_of_the_run_and_takes_the_next() {
    String declines =
        createScenario(
            Map.of(
                "testRunId", RUN.value(),
                "story", "hold-retries",
                "keyedOn", "TEST_RUN",
                "whenState", "Started",
                "thenState", "asked",
                "match", Map.of("operation", "createPaymentProcess"),
                "action", Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")));
    String lets =
        createScenario(
            Map.of(
                "testRunId",
                RUN.value(),
                "story",
                "hold-retries",
                "keyedOn",
                "TEST_RUN",
                "whenState",
                "asked",
                "match",
                Map.of("operation", "createPaymentProcess"),
                "action",
                Map.of("type", "FIXED_SUCCESS")));

    assertThat(parse(post(hold()))).containsEntry("status", 0);
    assertThat(outboundAudit().scenarioId()).isEqualTo(declines);
    assertThat(parse(post(hold()))).containsEntry("status", 1);
    assertThat(outboundAudit().scenarioId()).isEqualTo(lets);
    assertThat(parse(post(hold()))).containsEntry("status", 1);
    assertThat(outboundAudit().scenarioId()).isEqualTo(lets);
  }

  @Test
  void two_steps_of_one_story_that_declare_different_keys_are_refused_naming_the_key() {
    createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "story", "hold-retries",
            "keyedOn", "TEST_RUN",
            "whenState", "Started",
            "thenState", "asked",
            "match", Map.of("operation", "createPaymentProcess"),
            "action", Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")));

    ResponseEntity<Map<String, Object>> refused =
        whateverAnswersAScenario(
            Map.of(
                "testRunId",
                RUN.value(),
                "story",
                "hold-retries",
                "keyedOn",
                "FLOW",
                "whenState",
                "asked",
                "match",
                Map.of("operation", "createPaymentProcess"),
                "action",
                Map.of("type", "FIXED_SUCCESS")));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refused.getBody().get("properties")).isEqualTo(List.of("keyedOn"));
    assertThat(String.valueOf(refused.getBody().get("message")))
        .contains("hold-retries", "FLOW", "TEST_RUN");
    assertThat(parse(post(hold()))).containsEntry("status", 0);
  }

  @Test
  void a_scenario_selected_by_the_explicit_header_declines_only_the_call_that_carries_it() {
    createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "match", Map.of("header", Map.of("X-Simulator-Scenario", "card-add-failed")),
            "action", Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")));

    assertThat(parse(post(hold()))).containsEntry("status", 1);
    assertThat(parse(postCarrying("card-add-failed"))).containsEntry("status", 0);
  }

  @Test
  void deleting_a_scenario_gives_the_operation_back_to_the_provider() {
    String scenarioId = createScenario(declineEveryRequestWith("813"));
    assertThat(parse(post(hold()))).containsEntry("status", 0);

    ResponseEntity<Map<String, Object>> deleted =
        http.delete()
            .uri("/__simulator/grow/scenarios/" + scenarioId + "?testRunId=" + RUN.value())
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(deleted.getStatusCode().value()).isEqualTo(200);
    assertThat(parse(post(hold()))).containsEntry("status", 1);
  }

  @Test
  void resetting_a_test_run_clears_its_scenarios() {
    createScenario(declineEveryRequestWith("813"));
    assertThat(parse(post(hold()))).containsEntry("status", 0);

    reset();

    assertThat(parse(post(hold()))).containsEntry("status", 1);
  }

  private static Map<String, Object> declineEveryRequestWith(String providerCode) {
    return Map.of(
        "testRunId", RUN.value(),
        "priority", 100,
        "action", Map.of("type", "TERMINAL_FAILURE", "providerCode", providerCode));
  }

  private String createScenario(Map<String, Object> body) {
    ResponseEntity<Map<String, Object>> created = whateverAnswersAScenario(body);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    return String.valueOf(created.getBody().get("scenarioId"));
  }

  private ResponseEntity<Map<String, Object>> whateverAnswersAScenario(Map<String, Object> body) {
    return http.post()
        .uri("/__simulator/grow/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }

  private AuditRecord outboundAudit() {
    List<AuditRecord> records = audit.of(GrowLightApi.GROW, RUN);
    return records.stream()
        .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .reduce((first, second) -> second)
        .orElseThrow();
  }

  private ResponseEntity<byte[]> post(MultiValueMap<String, Object> form) {
    return http.post()
        .uri(CREATE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .header(HttpHeaders.AUTHORIZATION, "Bearer squirrel")
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private ResponseEntity<byte[]> postCarrying(String scenarioHeader) {
    return http.post()
        .uri(CREATE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .header("X-Simulator-Scenario", scenarioHeader)
        .body(hold())
        .retrieve()
        .toEntity(byte[].class);
  }

  private Map<String, Object> parse(ResponseEntity<byte[]> response) {
    return json.readValue(
        GrowWireText.readable(new String(response.getBody(), UTF_8)),
        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  private static MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", "120.50");
    form.add("description", "A hold");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return form;
  }
}
