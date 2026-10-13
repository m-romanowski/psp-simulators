package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.fault.AHeldResponse;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class GrowTransportFaultIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-transport-fault-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-fault-user";
  private static final String PAGE_CODE = "grow-fault-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";

  private static final String TRANSACTION_INFO = "/api/light/server/1.0/getTransactionInfo";
  private static final String HELD = "120.50";

  @LocalServerPort private int port;

  @Autowired private DelayedDeliveries delays;

  @Autowired private GrowPaymentProcesses processes;
  @Autowired private ObjectMapper json;

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
    wire.close();
  }

  @Test
  void a_capture_whose_response_is_replaced_by_a_5xx_is_still_in_the_ledger() {
    Held hold = aHold();
    faulting(hold.transactionId(), Map.of("fault", "SERVER_ERROR", "httpStatus", 504));

    HttpResponse<byte[]> response = post(SETTLE, settlement(hold));

    assertThat(response.statusCode()).isEqualTo(504);
    assertThat(response.headers().firstValue("Content-Type")).contains("text/plain;charset=utf-8");
    assertThat(new String(response.body(), UTF_8))
        .startsWith("504 was answered in front of the provider.");
    assertThat(balancesOf(hold.transactionId())).containsEntry("capturedAmount", HELD);
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_truncated_capture_response_is_shorter_than_the_rendered_one_and_does_not_parse() {
    Held faulted = aHold();
    Held untouched = aHold();
    faulting(faulted.transactionId(), Map.of("fault", "TRUNCATED_BODY"));

    HttpResponse<byte[]> truncated = post(SETTLE, settlement(faulted));
    HttpResponse<byte[]> whole = post(SETTLE, settlement(untouched));

    assertThat(truncated.statusCode()).isEqualTo(200);
    assertThat(contentLengthOf(truncated)).isEqualTo(truncated.body().length);
    assertThat(truncated.body().length).isEqualTo(whole.body().length / 2);
    assertThat(new String(truncated.body(), UTF_8))
        .startsWith("{\"status\":1,\"err\":\"\",\"data\"");
    assertThatThrownBy(() -> parse(truncated)).isInstanceOf(RuntimeException.class);
    assertThat(parse(whole)).containsEntry("status", 1);
    assertThat(balancesOf(faulted.transactionId())).containsEntry("capturedAmount", HELD);
  }

  @Test
  void a_corrupted_capture_response_is_as_long_as_the_rendered_one_and_does_not_parse() {
    Held faulted = aHold();
    Held untouched = aHold();
    faulting(faulted.transactionId(), Map.of("fault", "CORRUPTED_BODY"));

    HttpResponse<byte[]> corrupted = post(SETTLE, settlement(faulted));
    HttpResponse<byte[]> whole = post(SETTLE, settlement(untouched));

    assertThat(corrupted.statusCode()).isEqualTo(200);
    assertThat(corrupted.body()).hasSameSizeAs(whole.body());
    assertThat(contentLengthOf(corrupted)).isEqualTo(corrupted.body().length);
    assertThatThrownBy(() -> parse(corrupted)).isInstanceOf(RuntimeException.class);
    assertThat(balancesOf(faulted.transactionId())).containsEntry("capturedAmount", HELD);
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void a_dropped_capture_response_ends_before_the_content_length_it_promised_the_client() {
    Held hold = aHold();
    faulting(hold.transactionId(), Map.of("fault", "DROPPED_CONNECTION"));

    IOException reset =
        (IOException) catchThrowable(() -> post(SETTLE, settlement(hold))).getCause();

    assertThat(reset).hasMessageContaining("fixed content-length");
    long promised = numberAfter(reset.getMessage(), "fixed content-length: ");
    long received = numberAfter(reset.getMessage(), "bytes received: ");
    assertThat(received).isPositive();
    assertThat(received).isLessThan(promised);
    assertThat(received).isEqualTo(promised / 2);

    assertThat(balancesOf(hold.transactionId())).containsEntry("capturedAmount", HELD);
  }

  private static long numberAfter(String message, String label) {
    int start = message.indexOf(label) + label.length();
    int end = start;
    while (end < message.length() && Character.isDigit(message.charAt(end))) {
      end++;
    }
    return Long.parseLong(message.substring(start, end));
  }

  @Test
  void resetting_a_test_run_answers_a_held_response_instead_of_delivering_stale_bytes()
      throws Exception {
    Held hold = aHold();
    faulting(hold.transactionId(), Map.of("fault", "DELAYED_RESPONSE", "delayMs", 5000));

    ExecutorService client = Executors.newSingleThreadExecutor();
    try {
      Future<HttpResponse<byte[]>> held = client.submit(() -> post(SETTLE, settlement(hold)));
      AHeldResponse.isHeldBy(delays);
      reset();

      HttpResponse<byte[]> answer = held.get(15, TimeUnit.SECONDS);

      assertThat(answer.statusCode()).isEqualTo(503);
      assertThat(answer.headers().firstValue("content-type").orElseThrow())
          .startsWith("text/plain");
      assertThat(new String(answer.body(), UTF_8))
          .contains("held this response for a scenario delay")
          .contains("was reset before the delay elapsed");
      assertThat(stateStatusOf(hold.transactionId())).isEqualTo(404);
    } finally {
      client.shutdownNow();
      configure();
    }
  }

  private int stateStatusOf(String transactionId) {
    return http.get()
        .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }

  @Test
  void a_delayed_capture_response_arrives_whole_no_sooner_than_the_scenario_said() {
    Held hold = aHold();
    faulting(hold.transactionId(), Map.of("fault", "DELAYED_RESPONSE", "delayMs", 400));

    long before = System.nanoTime();
    HttpResponse<byte[]> response = post(SETTLE, settlement(hold));
    Duration waited = Duration.ofNanos(System.nanoTime() - before);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(parse(response)).containsEntry("status", 1);
    assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(350));
    assertThat(balancesOf(hold.transactionId())).containsEntry("capturedAmount", HELD);
  }

  @Test
  void a_faulting_first_hold_of_a_test_run_loses_one_response_and_the_next_hold_answers_normally() {
    aStoryOfTheWholeTestRun(
        "hold-retries",
        GrowOperations.CREATE_PAYMENT_PROCESS,
        Map.of("type", "TRANSPORT_FAULT", "fault", "SERVER_ERROR", "httpStatus", 502));

    HttpResponse<byte[]> lost = post(CREATE, hold());
    HttpResponse<byte[]> answered = post(CREATE, hold());

    assertThat(lost.statusCode()).isEqualTo(502);
    assertThat(lost.headers().firstValue("Content-Type")).contains("text/plain;charset=utf-8");
    assertThat(new String(lost.body(), UTF_8))
        .startsWith("502 was answered in front of the provider.");
    assertThat(answered.statusCode()).isEqualTo(200);
    assertThat(parse(answered)).containsEntry("status", 1);
    assertThat(dataOf(parse(answered)).get("processId")).isNotNull();
  }

  @Test
  void a_faulting_first_lookup_of_a_payment_loses_one_response_and_the_next_lookup_answers() {
    aStoryOfOnePayment(
        "lookup-retries",
        GrowOperations.GET_TRANSACTION_INFO,
        Map.of("type", "TRANSPORT_FAULT", "fault", "SERVER_ERROR", "httpStatus", 502));
    Held hold = aHold();

    HttpResponse<byte[]> lost = post(TRANSACTION_INFO, lookup(hold));
    HttpResponse<byte[]> answered = post(TRANSACTION_INFO, lookup(hold));

    assertThat(lost.statusCode()).isEqualTo(502);
    assertThat(lost.headers().firstValue("Content-Type")).contains("text/plain;charset=utf-8");
    assertThat(new String(lost.body(), UTF_8))
        .startsWith("502 was answered in front of the provider.");
    assertThat(answered.statusCode()).isEqualTo(200);
    assertThat(String.valueOf(dataOf(parse(answered)).get("transactionId")))
        .isEqualTo(hold.transactionId());
  }

  private void aStoryOfTheWholeTestRun(
      String story, String operation, Map<String, Object> firstStep) {
    aTwoStepStory(story, "TEST_RUN", operation, firstStep);
  }

  private void aStoryOfOnePayment(String story, String operation, Map<String, Object> firstStep) {
    aTwoStepStory(story, "FLOW", operation, firstStep);
  }

  private void aTwoStepStory(
      String story, String keyedOn, String operation, Map<String, Object> firstStep) {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "priority",
            100,
            "story",
            story,
            "keyedOn",
            keyedOn,
            "whenState",
            "Started",
            "thenState",
            "lost",
            "match",
            Map.of("operation", operation),
            "action",
            firstStep));
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "priority",
            100,
            "story",
            story,
            "keyedOn",
            keyedOn,
            "whenState",
            "lost",
            "match",
            Map.of("operation", operation),
            "action",
            Map.of("type", "FIXED_SUCCESS")));
  }

  private Map<String, String> lookup(Held hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("transactionId", hold.transactionId());
    form.put("transactionToken", hold.transactionToken());
    return form;
  }

  @Test
  void resetting_the_test_run_takes_the_transport_fault_away_with_the_rest_of_the_scenarios() {
    Held hold = aHold();
    faulting(hold.transactionId(), Map.of("fault", "SERVER_ERROR", "httpStatus", 502));
    assertThat(post(SETTLE, settlement(hold)).statusCode()).isEqualTo(502);

    reset();
    configure();

    Held afterReset = aHold();
    assertThat(post(SETTLE, settlement(afterReset)).statusCode()).isEqualTo(200);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(
        new String(response.body(), UTF_8), new TypeReference<Map<String, Object>>() {});
  }

  private static long contentLengthOf(HttpResponse<byte[]> response) {
    return response.headers().firstValueAsLong("content-length").orElseThrow();
  }

  private void faulting(String transactionId, Map<String, Object> fault) {
    Map<String, Object> action = new HashMap<>(fault);
    action.put("type", "TRANSPORT_FAULT");
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "priority",
            100,
            "match",
            Map.of("providerObjectId", transactionId),
            "action",
            action));
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

  private Held aHold() {
    HttpResponse<byte[]> created = post(CREATE, hold());
    assertThat(created.statusCode()).isEqualTo(200);
    String processId = String.valueOf(dataOf(parse(created)).get("processId"));
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
    GrowProcessOutcome outcome = processes.findByProcessId(RUN, processId).orElseThrow().outcome();
    assertThat(outcome).isNotNull();
    return new Held(
        String.valueOf(outcome.transactionId()), String.valueOf(outcome.transactionToken()));
  }

  private Map<String, String> settlement(Held hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("userId", USER_ID);
    form.put("transactionId", hold.transactionId());
    form.put("transactionToken", hold.transactionToken());
    form.put("sum", HELD);
    return form;
  }

  private Map<String, String> hold() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("userId", USER_ID);
    form.put("chargeType", "2");
    form.put("sum", HELD);
    form.put("saveCardToken", "1");
    form.put("description", "A hold");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("notifyUrl", merchant.url());
    form.put("pageField[fullName]", "Israel Israeli");
    form.put("pageField[phone]", "0505503144");
    form.put("cField1", "session-abc");
    form.put("cField2", "intent-99");
    return form;
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build();
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private Map<String, Object> state(String transactionId) {
    return http.get()
        .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> balancesOf(String transactionId) {
    return (Map<String, Object>) state(transactionId).get("derivedBalances");
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

  private record Held(String transactionId, String transactionToken) {}
}
