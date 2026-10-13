package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class KeyInspectorIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("key-inspector-it");
  private static final TestRunId ELSEWHERE = TestRunId.of("key-inspector-it-elsewhere");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_keyInspectorIntegration";
  private static final String INTENTS = "/v1/payment_intents";
  private static final String INSPECTOR = "/__simulator/stripe/idempotency";
  private static final String KEY = "7c9a0c6e-1a1b-4c2d-9e3f-0a1b2c3d4e5f";
  private static final String ANOTHER_KEY = "0f1e2d3c-4b5a-4968-8778-6a5b4c3d2e1f";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
  }

  @Test
  void a_harness_that_replays_a_request_reads_one_first_request_and_one_replay_of_its_answer() {
    Map<String, Object> first = charge(KEY, "1099");
    Map<String, Object> replayed = charge(KEY, "1099");

    Map<String, Object> exchange = theOnlyExchange();
    assertThat(exchange).containsEntry("keyName", "Idempotency-Key");
    assertThat(exchange).containsEntry("keyValue", KEY);
    assertThat(exchange).containsEntry("role", "true idempotency key");
    assertThat(String.valueOf(exchange.get("roleMeans"))).contains("returns the original response");
    assertThat(exchange).containsEntry("accountId", MERCHANT);
    assertThat(exchange).containsEntry("stateChanged", true);
    assertThat(replayTimestampsOf(exchange)).hasSize(1);

    List<Map<String, Object>> requests = requestsOf(exchange);
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0)).containsEntry("verdict", "FIRST_REQUEST");
    assertThat(requests.get(1)).containsEntry("verdict", "TRUE_IDEMPOTENT_REPLAY");
    assertThat(requests.get(1).get("body")).isEqualTo(requests.get(0).get("body"));
    assertThat(requests.get(1)).containsEntry("stateChanged", false);
    assertThat(requests.get(1).get("conflictReason")).isNull();
    assertThat(first.get("id")).isEqualTo(replayed.get("id"));
  }

  @Test
  void a_replay_whose_payload_differs_reads_back_the_conflict_reason_and_no_second_answer() {
    charge(KEY, "1099");

    charge(KEY, "2000");

    Map<String, Object> exchange = theOnlyExchange();
    Map<String, Object> conflicting = requestsOf(exchange).get(1);
    assertThat(conflicting).containsEntry("verdict", "CONFLICTING_PAYLOAD");
    assertThat(String.valueOf(conflicting.get("conflictReason")))
        .contains("parameters the first request did not")
        .contains(String.valueOf(exchange.get("requestFingerprint")));
    assertThat(replayTimestampsOf(exchange)).isEmpty();
  }

  @Test
  void the_inspector_shows_only_the_exchanges_of_the_test_run_it_was_asked_about() {
    charge(KEY, "1099");

    assertThat(exchangesOf(ELSEWHERE.value(), "")).isEmpty();
    assertThat(exchangesOf(RUN.value(), "")).hasSize(1);
  }

  @Test
  void a_full_page_of_exchanges_hands_back_the_cursor_the_next_page_carries_on_from() {
    charge(KEY, "1099");
    charge(ANOTHER_KEY, "1099");

    Map<String, Object> firstPage = inspector(RUN.value(), "&limit=1");
    List<Map<String, Object>> onlyRow = exchangesOf(firstPage);
    String cursor = String.valueOf(firstPage.get("nextCursor"));
    List<Map<String, Object>> secondPage = exchangesOf(RUN.value(), "&limit=1&after=" + cursor);

    assertThat(onlyRow).hasSize(1);
    assertThat(secondPage).hasSize(1);
    assertThat(onlyRow.get(0).get("keyValue")).isNotEqualTo(secondPage.get(0).get("keyValue"));
  }

  @Test
  void a_page_shorter_than_the_limit_it_was_asked_for_hands_back_no_cursor_at_all() {
    charge(KEY, "1099");
    charge(ANOTHER_KEY, "1099");

    assertThat(inspector(RUN.value(), "&limit=10").get("nextCursor")).isNull();
  }

  @Test
  void resetting_a_test_run_leaves_the_inspector_with_nothing_of_it_to_show() {
    charge(KEY, "1099");

    reset();

    assertThat(exchangesOf(RUN.value(), "")).isEmpty();
  }

  @Test
  void a_provider_with_no_idempotency_key_says_so_where_the_key_row_would_be() {
    List<Map<String, Object>> absent = listOf(azericardInspector(), "withoutAMechanism");

    assertThat(absent).hasSize(1);
    assertThat(absent.get(0)).containsEntry("role", "true idempotency key");
    assertThat(String.valueOf(absent.get(0).get("instead")))
        .contains("publishes no idempotency header");
  }

  @Test
  void the_keys_that_provider_declares_are_the_ones_its_own_contract_names() {
    assertThat(listOf(azericardInspector(), "keys"))
        .extracting(key -> key.get("name"))
        .containsExactly("ORDER", "RRN", "INT_REF");
  }

  @Test
  void a_provider_this_run_sent_nothing_to_shows_none_of_the_exchanges_another_provider_holds() {
    charge(KEY, "1099");

    assertThat(exchangesOf(RUN.value(), "")).hasSize(1);
    assertThat(listOf(azericardInspector(), "exchanges")).isEmpty();
  }

  @Test
  void the_screen_shows_the_replay_and_names_the_control_read_it_stands_on() {
    charge(KEY, "1099");
    charge(KEY, "1099");

    String screen =
        String.valueOf(
            http.get()
                .uri("/ui/idempotency?provider=stripe&testRunId=" + RUN.value())
                .retrieve()
                .body(String.class));

    assertThat(screen).contains(KEY);
    assertThat(screen).contains("true idempotency key");
    assertThat(screen).contains("TRUE_IDEMPOTENT_REPLAY");
    assertThat(screen).contains("GET /__simulator/stripe/idempotency");
    assertThat(screen).doesNotContain(SECRET_KEY);
  }

  @Test
  void the_screen_of_a_provider_with_no_idempotency_key_prints_what_it_has_instead() {
    String screen =
        String.valueOf(
            http.get()
                .uri("/ui/idempotency?provider=grow&testRunId=" + RUN.value())
                .retrieve()
                .body(String.class));

    assertThat(screen).contains("publishes no idempotency header");
    assertThat(screen).contains("transactionUniqueIdentifier");
    assertThat(screen).contains("transactionGroupIdentifier");
    assertThat(screen).contains("refused and never replayed");
  }

  @Test
  void a_cursor_that_is_not_a_whole_number_is_refused_rather_than_read_as_the_newest_page() {
    assertThat(statusOf(INSPECTOR + "?testRunId=" + RUN.value() + "&after=nothing")).isEqualTo(400);
  }

  @Test
  void a_limit_that_is_not_a_whole_number_is_refused_rather_than_read_as_the_default_page_size() {
    assertThat(statusOf(INSPECTOR + "?testRunId=" + RUN.value() + "&limit=all")).isEqualTo(400);
  }

  @Test
  void the_inspector_of_a_provider_this_simulator_does_not_have_is_answered_as_not_found() {
    assertThat(statusOf("/__simulator/nosuchprovider/idempotency")).isEqualTo(404);
  }

  private Map<String, Object> azericardInspector() {
    return Objects.requireNonNull(
        http.get()
            .uri("/__simulator/azericard/idempotency?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
  }

  private Map<String, Object> charge(String key, String amount) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("amount", amount);
    form.add("currency", "pln");
    form.add("capture_method", "manual");
    return Objects.requireNonNull(
        http.post()
            .uri(INTENTS)
            .header("Authorization", "Bearer " + SECRET_KEY)
            .header("Idempotency-Key", key)
            .header(TestRunHeader.NAME, RUN.value())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
  }

  private Map<String, Object> theOnlyExchange() {
    List<Map<String, Object>> found = exchangesOf(RUN.value(), "");
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private List<Map<String, Object>> exchangesOf(String testRunId, String query) {
    return exchangesOf(inspector(testRunId, query));
  }

  private static List<Map<String, Object>> exchangesOf(Map<String, Object> answered) {
    return listOf(answered, "exchanges");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listOf(Map<String, Object> answered, String field) {
    return (List<Map<String, Object>>) Objects.requireNonNull(answered.get(field));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> requestsOf(Map<String, Object> exchange) {
    return (List<Map<String, Object>>) Objects.requireNonNull(exchange.get("requests"));
  }

  @SuppressWarnings("unchecked")
  private static List<String> replayTimestampsOf(Map<String, Object> exchange) {
    return (List<String>) Objects.requireNonNull(exchange.get("replayTimestamps"));
  }

  private Map<String, Object> inspector(String testRunId, String query) {
    return Objects.requireNonNull(
        http.get()
            .uri(INSPECTOR + "?testRunId=" + testRunId + query)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
  }

  private int statusOf(String uri) {
    ResponseEntity<Void> answered =
        http.get()
            .uri(uri)
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toBodilessEntity();
    return answered.getStatusCode().value();
  }

  private void configure() {
    String answered =
        http.put()
            .uri("/__simulator/stripe/config")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "testRunId",
                    RUN.value(),
                    "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            MERCHANT,
                            "configuration",
                            Map.of("secretKey", SECRET_KEY, "captureWindow", "P3D")))))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(String.class)
            .getBody();
    assertThat(answered).contains("configuredAccounts");
  }

  private void unconfigure() {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "accounts", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }
}
