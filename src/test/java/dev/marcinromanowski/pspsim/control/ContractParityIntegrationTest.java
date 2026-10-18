package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class ContractParityIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("contract-parity-it");

  private static final String MERCHANT = "contract-parity-merchant";

  private static final String THE_SECRET_NOBODY_MAY_READ =
      "contract-parity-page-code-must-never-be-answered";

  private static final List<String> EVERY_PROVIDER =
      List.of("grow", "autra", "azericard", "worldline", "tranzila", "stripe");

  private static final String A_TEST_RUN_NO_ID_CAN_BE = "a run id with spaces";

  private static final String THIS_PROJECTS_OWN_SENTENCE = "testRunId must match";

  private static final String THE_PATTERN_A_SUITE_AUTHOR_MATCHES_ON =
      "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";

  private static final String THE_SHAPE_SAID_IN_WORDS =
      "at most 64 characters, the first a letter or a digit and the rest a letter, a digit, a dot,"
          + " a hyphen or an underscore, for example checkout-smoke";

  private static final String THE_FRAMEWORKS_OWN_PAGE = "Bad Request";

  private static final String UNKNOWN_PROVIDER = "unknown provider";

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void configureTheGrowAccountOfTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    configureGrow(
        List.of(
            Map.of(
                "accountId",
                MERCHANT,
                "configuration",
                Map.of("userId", MERCHANT, "pageCode", THE_SECRET_NOBODY_MAY_READ))));
  }

  @AfterEach
  void unconfigureTheTestRun() {
    configureGrow(List.of());
  }

  @Test
  void the_base_url_a_harness_is_given_is_the_one_that_providers_pairing_screen_shows() {
    for (String provider : EVERY_PROVIDER) {
      Map<String, Object> address = address(provider);

      assertThat(pairingScreenOf(provider))
          .as(provider)
          .contains(String.valueOf(address.get("providerBaseUrl")));
    }
  }

  @Test
  void the_address_answers_the_public_base_url_the_path_and_the_header_that_carries_the_run() {
    Map<String, Object> address = address("grow");

    assertThat(address)
        .containsEntry("testRunId", RUN.value())
        .containsEntry("provider", "grow")
        .containsEntry("testRunHeader", TestRunHeader.NAME)
        .containsKey("howARequestNamesItsAccount");
    assertThat(address)
        .containsEntry("publicBaseUrl", "http://localhost:8080")
        .containsEntry("apiBasePath", "/api/light/server/1.0")
        .containsEntry("providerBaseUrl", "http://localhost:8080/api/light/server/1.0");
  }

  @Test
  void no_value_the_provider_declares_secret_reaches_the_address_a_harness_is_given() {
    String answered = body("/__simulator/grow/address?testRunId=" + RUN.value());

    assertThat(answered)
        .doesNotContain(THE_SECRET_NOBODY_MAY_READ)
        .doesNotContain(MERCHANT)
        .contains("providerBaseUrl");
  }

  @Test
  void the_operations_a_harness_reads_are_the_ones_a_rule_naming_another_is_refused_against() {
    List<String> named = operationNamesOf("grow");

    ResponseEntity<Map<String, Object>> refused = aRuleNaming("noSuchGrowOperation");

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message"))).contains(String.join(", ", named));
  }

  @Test
  void every_operation_carries_what_a_rule_may_match_it_on_and_what_a_story_of_it_keys_on() {
    List<Map<String, Object>> operations = operationsOf("grow");

    assertThat(operations).isNotEmpty();
    assertThat(operations)
        .allSatisfy(
            operation ->
                assertThat(operation)
                    .containsKeys(
                        "name",
                        "carries",
                        "storiesKeyedOn",
                        "itsFlowKeyMayBeReplaced",
                        "reportsAProcessingState"));
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_not_found_on_both_reads() {
    for (String path : List.of("/address", "/operations")) {
      ResponseEntity<Map<String, Object>> refused =
          read("/__simulator/no-such-psp" + path + "?testRunId=" + RUN.value());

      assertThat(refused.getStatusCode().value()).as(path).isEqualTo(404);
      assertThat(String.valueOf(refused.getBody().get("message")))
          .as(path)
          .contains(UNKNOWN_PROVIDER);
    }
  }

  @Test
  void a_test_run_no_id_can_be_is_refused_in_this_projects_own_words_on_both_reads() {
    for (String path : List.of("/address", "/operations")) {
      ResponseEntity<Map<String, Object>> refused =
          read(
              "/__simulator/grow"
                  + path
                  + "?testRunId="
                  + A_TEST_RUN_NO_ID_CAN_BE.replace(" ", "%20"));

      assertThat(refused.getStatusCode().value()).as(path).isEqualTo(400);
      assertThat(String.valueOf(refused.getBody().get("message")))
          .as(
              "%s answers a suite the pattern it matches on and, in the same sentence, the words"
                  + " a tester reads beside the box on a screen: one wording, two audiences",
              path)
          .contains(THIS_PROJECTS_OWN_SENTENCE)
          .contains(THE_PATTERN_A_SUITE_AUTHOR_MATCHES_ON)
          .contains(THE_SHAPE_SAID_IN_WORDS)
          .doesNotContain(THE_FRAMEWORKS_OWN_PAGE);
    }
  }

  @Test
  void a_read_naming_no_test_run_is_answered_for_the_reserved_default_rather_than_refused() {
    for (String path : List.of("/address", "/operations")) {
      ResponseEntity<Map<String, Object>> answered = read("/__simulator/grow" + path);

      assertThat(answered.getStatusCode().value()).as(path).isEqualTo(200);
      assertThat(answered.getBody()).as(path).containsEntry("testRunId", "default");
    }
  }

  private Map<String, Object> address(String provider) {
    ResponseEntity<Map<String, Object>> answered =
        read("/__simulator/" + provider + "/address?testRunId=" + RUN.value());
    assertThat(answered.getStatusCode().value()).as(provider).isEqualTo(200);
    return answered.getBody();
  }

  private List<Map<String, Object>> operationsOf(String provider) {
    ResponseEntity<Map<String, Object>> answered =
        read("/__simulator/" + provider + "/operations?testRunId=" + RUN.value());
    assertThat(answered.getStatusCode().value()).as(provider).isEqualTo(200);
    Object listed = answered.getBody().get("operations");
    List<Map<String, Object>> operations = new ArrayList<>();
    for (Object operation : (List<?>) listed) {
      operations.add(asMap(operation));
    }
    return List.copyOf(operations);
  }

  private List<String> operationNamesOf(String provider) {
    List<String> named = new ArrayList<>();
    for (Map<String, Object> operation : operationsOf(provider)) {
      named.add(String.valueOf(operation.get("name")));
    }
    return List.copyOf(named);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object operation) {
    return (Map<String, Object>) operation;
  }

  private ResponseEntity<Map<String, Object>> aRuleNaming(String operation) {
    return http.post()
        .uri("/__simulator/grow/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "priority",
                100,
                "match",
                Map.of("operation", operation),
                "action",
                Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")))
        .retrieve()
        .onStatus(status -> true, (request, whateverItAnswered) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<Map<String, Object>> read(String uri) {
    return http.get()
        .uri(uri)
        .retrieve()
        .onStatus(status -> true, (request, whateverItAnswered) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private String pairingScreenOf(String provider) {
    return body("/ui/pairing?provider=" + provider + "&testRunId=" + RUN.value());
  }

  private String body(String uri) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private void configureGrow(List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "accounts", accounts, "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }
}
