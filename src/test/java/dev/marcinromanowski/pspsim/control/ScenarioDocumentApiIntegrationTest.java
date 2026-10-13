package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class ScenarioDocumentApiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "scenario-document-it";

  private static final String OTHER_RUN = "scenario-document-it-2";

  private static final String GROW = "grow";

  private static final String AZERICARD = "azericard";

  private static final String CANCEL_HOLD = "cancel-hold-success";

  private static final String SETTLE = "settleSuspendedTransaction";

  private static final String REFUND = "refundTransaction";

  private static final String PREVIEW = "/scenarios/presets/preview";

  private static final String TAKE_BACK = "/scenarios/import";

  private static final String A_CREDENTIAL_A_CALLER_SUPPLIED = "sk-live-must-not-come-back";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    reset(GROW, RUN);
    reset(GROW, OTHER_RUN);
    reset(AZERICARD, RUN);
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    reset(GROW, RUN);
    reset(GROW, OTHER_RUN);
    reset(AZERICARD, RUN);
  }

  @Test
  void previewing_a_preset_answers_a_document_naming_the_run_the_provider_and_the_preset() {
    Map<String, Object> document = previewed(GROW, RUN, CANCEL_HOLD);

    assertThat(document).containsEntry("testRunId", RUN);
    assertThat(document).containsEntry("provider", GROW);
    assertThat(document).containsEntry("preset", CANCEL_HOLD);
    assertThat(rulesCarriedBy(document)).hasSize(2);
  }

  @Test
  void previewing_a_preset_writes_no_rule_of_its_own() {
    previewed(GROW, RUN, CANCEL_HOLD);

    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  @Test
  void a_preset_this_provider_cannot_express_is_refused_by_the_preview_naming_the_preset() {
    ResponseEntity<Object> refused =
        post(GROW, PREVIEW, Map.of("testRunId", RUN, "preset", "webhook-invalid-signature"));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(bodyOf(refused).get("properties")).isEqualTo(List.of("preset"));
  }

  @Test
  void the_document_a_preview_answered_writes_the_same_operations_when_it_is_posted_back() {
    Map<String, Object> document = previewed(GROW, RUN, CANCEL_HOLD);

    ResponseEntity<Object> written = postDocument(GROW, document);

    assertThat(written.getStatusCode().value()).isEqualTo(201);
    assertThat(theOperationsOf(GROW, RUN)).containsExactlyInAnyOrder(SETTLE, REFUND);
  }

  @Test
  void a_document_whose_run_a_caller_changed_writes_its_rules_into_the_run_it_now_names() {
    Map<String, Object> document = new LinkedHashMap<>(previewed(GROW, RUN, CANCEL_HOLD));
    document.put("testRunId", OTHER_RUN);

    postDocument(GROW, document);

    assertThat(theOperationsOf(GROW, OTHER_RUN)).containsExactlyInAnyOrder(SETTLE, REFUND);
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  @Test
  void a_document_another_provider_expanded_is_refused_and_writes_none_of_its_rules() {
    Map<String, Object> document = previewed(GROW, RUN, CANCEL_HOLD);

    ResponseEntity<Object> refused = postDocument(AZERICARD, document);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(bodyOf(refused).get("properties")).isEqualTo(List.of("provider"));
    assertThat(theRulesOf(AZERICARD, RUN)).isEmpty();
  }

  @Test
  void a_document_carrying_no_rule_is_refused_rather_than_answered_as_nothing_written() {
    ResponseEntity<Object> refused =
        postDocument(GROW, Map.of("testRunId", RUN, "provider", GROW, "rules", List.<Object>of()));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(bodyOf(refused).get("properties")).isEqualTo(List.of("rules"));
  }

  @Test
  void previewing_a_preset_this_simulator_does_not_publish_is_rejected_by_the_preset_property() {
    ResponseEntity<Object> rejected =
        post(GROW, PREVIEW, Map.of("testRunId", RUN, "preset", "cancel-hold-succes"));

    assertThat(rejected.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(bodyOf(rejected).get("message"))).startsWith("preset must be one of");
  }

  @Test
  void previewing_a_preset_of_a_provider_this_simulator_does_not_serve_is_not_found() {
    ResponseEntity<Object> unserved =
        post("no-such-psp", PREVIEW, Map.of("testRunId", RUN, "preset", CANCEL_HOLD));

    assertThat(unserved.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void a_document_posted_to_a_provider_this_simulator_does_not_serve_is_not_found() {
    ResponseEntity<Object> unserved =
        postDocument("no-such-psp", Map.of("testRunId", RUN, "provider", GROW));

    assertThat(unserved.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void a_document_naming_a_run_no_namespace_can_hold_is_rejected_and_writes_nothing() {
    Map<String, Object> document = new LinkedHashMap<>(previewed(GROW, RUN, CANCEL_HOLD));
    document.put("testRunId", "a run name no namespace can hold");

    ResponseEntity<Object> rejected = postDocument(GROW, document);

    assertThat(rejected.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(bodyOf(rejected).get("message"))).startsWith("testRunId");
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  @Test
  void a_preview_of_a_preset_narrowed_by_a_header_is_refused_by_the_header_it_narrows_on() {
    ResponseEntity<Object> refused = previewNarrowedByAHeader();

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(bodyOf(refused).get("properties")).isEqualTo(List.of("match.header.Authorization"));
  }

  @Test
  void the_value_of_that_header_is_in_no_part_of_the_answer_the_preview_refused_it_with() {
    ResponseEntity<Object> refused = previewNarrowedByAHeader();

    assertThat(String.valueOf(refused.getBody())).doesNotContain(A_CREDENTIAL_A_CALLER_SUPPLIED);
  }

  @Test
  void the_value_of_that_header_is_on_no_row_of_the_trail_the_refused_preview_left() {
    previewNarrowedByAHeader();

    assertThat(theTrailOf(GROW, RUN)).doesNotContain(A_CREDENTIAL_A_CALLER_SUPPLIED);
  }

  @Test
  void the_same_narrowing_still_expands_into_written_rules_where_no_document_is_asked_for() {
    ResponseEntity<Object> written =
        post(
            GROW,
            "/scenarios/presets",
            Map.of(
                "testRunId",
                RUN,
                "preset",
                CANCEL_HOLD,
                "match",
                Map.of("header", Map.of("Authorization", A_CREDENTIAL_A_CALLER_SUPPLIED))));

    assertThat(written.getStatusCode().value()).isEqualTo(201);
    assertThat(theOperationsOf(GROW, RUN)).containsExactlyInAnyOrder(SETTLE, REFUND);
  }

  @Test
  void the_value_of_that_header_is_in_no_answer_of_the_expansion_that_did_write_the_rules() {
    ResponseEntity<Object> written =
        post(
            GROW,
            "/scenarios/presets",
            Map.of(
                "testRunId",
                RUN,
                "preset",
                CANCEL_HOLD,
                "match",
                Map.of("header", Map.of("Authorization", A_CREDENTIAL_A_CALLER_SUPPLIED))));

    assertThat(String.valueOf(written.getBody())).doesNotContain(A_CREDENTIAL_A_CALLER_SUPPLIED);
    assertThat(String.valueOf(theRulesOf(GROW, RUN)))
        .doesNotContain(A_CREDENTIAL_A_CALLER_SUPPLIED);
    assertThat(theTrailOf(GROW, RUN)).doesNotContain(A_CREDENTIAL_A_CALLER_SUPPLIED);
  }

  private ResponseEntity<Object> previewNarrowedByAHeader() {
    return post(
        GROW,
        PREVIEW,
        Map.of(
            "testRunId",
            RUN,
            "preset",
            CANCEL_HOLD,
            "match",
            Map.of("header", Map.of("Authorization", A_CREDENTIAL_A_CALLER_SUPPLIED))));
  }

  private Map<String, Object> previewed(String provider, String testRunId, String preset) {
    ResponseEntity<Object> answered =
        post(provider, PREVIEW, Map.of("testRunId", testRunId, "preset", preset));
    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    return bodyOf(answered);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> rulesCarriedBy(Map<String, Object> document) {
    return (List<Object>) document.get("rules");
  }

  private ResponseEntity<Object> postDocument(String provider, Map<String, Object> document) {
    return post(provider, TAKE_BACK, document);
  }

  private ResponseEntity<Object> post(String provider, String path, Map<String, Object> body) {
    return http.method(HttpMethod.POST)
        .uri("/__simulator/" + provider + path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Object>() {});
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> bodyOf(ResponseEntity<Object> response) {
    return (Map<String, Object>) response.getBody();
  }

  private List<String> theOperationsOf(String provider, String testRunId) {
    List<String> operations = new ArrayList<>();
    for (Map<String, Object> rule : theRulesOf(provider, testRunId)) {
      operations.add(String.valueOf(rule.get("operation")));
    }
    return operations;
  }

  private List<Map<String, Object>> theRulesOf(String provider, String testRunId) {
    return http.get()
        .uri("/__simulator/" + provider + "/scenarios?testRunId=" + testRunId)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private String theTrailOf(String provider, String testRunId) {
    return String.valueOf(
        http.get()
            .uri("/__simulator/" + provider + "/audit?testRunId=" + testRunId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));
  }

  private void reset(String provider, String testRunId) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", testRunId))
        .retrieve()
        .toBodilessEntity();
  }
}
