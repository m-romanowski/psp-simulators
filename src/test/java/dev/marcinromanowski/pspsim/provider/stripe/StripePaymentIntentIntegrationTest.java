package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.fault.AHeldResponse;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSample;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSamples;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

class StripePaymentIntentIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-payment-intent-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51IntegrationSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";

  private static final String REFUNDS = "/v1/refunds";

  @LocalServerPort private int port;

  @Autowired private DelayedDeliveries delays;

  @Autowired private TestRuns testRuns;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(FIXED_NOW);
  }

  @AfterEach
  void unconfigureTheAccount() {
    reset();
    http.put()
        .uri("/__simulator/stripe/config")
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
  void holds_confirms_and_captures_a_payment_intent_over_the_wire() {
    HttpResponse<byte[]> created = post(PAYMENT_INTENTS, hold("1099"), null);
    String intentId = valueIn(bodyOf(created), "id");

    HttpResponse<byte[]> captured =
        post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), null);

    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.headers().firstValue("content-type").orElseThrow())
        .startsWith("application/json");
    assertThat(bodyOf(created)).contains("\"status\":\"requires_capture\"");
    assertThat(captured.statusCode()).isEqualTo(200);
    assertThat(bodyOf(captured))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_received\":1099");
  }

  @Test
  void captures_a_payment_twice_over_the_wire_and_closes_it_with_the_second() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1000"), null)), "id");

    HttpResponse<byte[]> part =
        post(
            PAYMENT_INTENTS + "/" + intentId + "/capture",
            Map.of("amount_to_capture", "700", "final_capture", "false"),
            null);
    Map<String, Object> betweenTheTwo = stateOf(intentId);
    HttpResponse<byte[]> rest =
        post(
            PAYMENT_INTENTS + "/" + intentId + "/capture",
            Map.of("amount_to_capture", "100"),
            null);

    assertThat(bodyOf(part))
        .contains("\"status\":\"requires_capture\"")
        .contains("\"amount_capturable\":300")
        .contains("\"amount_received\":700");
    assertThat(betweenTheTwo).containsEntry("normalizedStatus", "PARTIALLY_CAPTURED");
    assertThat(stateOf(intentId)).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(bodyOf(rest))
        .contains("\"status\":\"succeeded\"")
        .contains("\"amount_capturable\":0")
        .contains("\"amount_received\":800");
  }

  @Test
  void refunds_a_captured_payment_over_the_wire_and_reports_both_objects() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1000"), null)), "id");
    post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), null);

    HttpResponse<byte[]> refunded =
        post(REFUNDS, Map.of("payment_intent", intentId, "amount", "400"), null);
    String refundId = valueIn(bodyOf(refunded), "id");

    assertThat(refunded.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refunded))
        .contains("\"object\":\"refund\"")
        .contains("\"amount\":400")
        .contains("\"payment_intent\":\"" + intentId + "\"");
    assertThat(stateOf(intentId)).containsEntry("normalizedStatus", "PARTIALLY_REFUNDED");
    assertThat(stateOf(refundId))
        .containsEntry("providerObjectId", refundId)
        .containsEntry("nativeStatus", "succeeded")
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", "400");
    assertThat(operationsOf(stateOf(intentId)))
        .last()
        .satisfies(
            operation -> {
              assertThat(operation).containsEntry("kind", "REFUND");
              assertThat(operation).containsEntry("createdObjectId", refundId);
            });
  }

  @Test
  void replays_a_refund_under_the_idempotency_key_that_created_it() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1000"), null)), "id");
    post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), null);
    String key = "a-key-of-one-refund";
    Map<String, String> form = Map.of("payment_intent", intentId, "amount", "400");

    HttpResponse<byte[]> first = post(REFUNDS, form, key);
    HttpResponse<byte[]> replay = post(REFUNDS, form, key);

    assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
    assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
    assertThat(operationsOf(stateOf(intentId))).hasSize(3);
  }

  @Test
  void refuses_over_the_wire_a_refund_of_a_payment_that_captured_nothing() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1000"), null)), "id");

    HttpResponse<byte[]> refused =
        post(REFUNDS, Map.of("payment_intent", intentId, "amount", "400"), null);

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("payment_intent_unexpected_state");
  }

  @Test
  void reports_the_payment_it_stored_through_the_control_state_route() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");

    Map<String, Object> state = stateOf(intentId);

    assertThat(state)
        .containsEntry("providerObjectId", intentId)
        .containsEntry("nativeStatus", "requires_capture")
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE")
        .containsEntry("amount", "1099")
        .containsEntry("currency", "pln");
    assertThat(operationsOf(state))
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation).containsEntry("kind", "AUTHORIZATION");
              assertThat(operation).containsEntry("amount", "1099");
            });
  }

  @Test
  void replays_the_stored_answer_of_a_repeated_request_and_says_that_it_did() {
    String key = "a-key-of-one-creation";

    HttpResponse<byte[]> first = post(PAYMENT_INTENTS, hold("1099"), key);
    HttpResponse<byte[]> replay = post(PAYMENT_INTENTS, hold("1099"), key);

    assertThat(replay.statusCode()).isEqualTo(first.statusCode());
    assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
    assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
    assertThat(replay.headers().firstValue("Idempotent-Replayed")).hasValue("true");
  }

  @Test
  void replays_a_repeated_request_of_a_run_forgotten_while_its_account_stays_configured() {
    testRuns.forget(RUN);
    String key = "a-key-of-a-forgotten-run";

    HttpResponse<byte[]> first = post(PAYMENT_INTENTS, hold("1099"), key);
    HttpResponse<byte[]> replay = post(PAYMENT_INTENTS, hold("1099"), key);

    assertThat(testRuns.exists(RUN)).isFalse();
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
    assertThat(replay.headers().firstValue("Idempotent-Replayed")).hasValue("true");
  }

  @Test
  void refuses_a_key_reused_with_other_parameters_over_the_wire() {
    String key = "a-key-of-two-different-creations";
    post(PAYMENT_INTENTS, hold("1099"), key);

    HttpResponse<byte[]> reused = post(PAYMENT_INTENTS, hold("2000"), key);

    assertThat(reused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(reused)).contains("\"type\":\"idempotency_error\"");
  }

  @Test
  void refuses_a_capture_once_the_clock_has_passed_the_configured_deadline() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");
    configure(FIXED_NOW.plus(Duration.ofDays(4)));

    HttpResponse<byte[]> captured =
        post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), null);

    assertThat(captured.statusCode()).isEqualTo(400);
    assertThat(bodyOf(captured)).contains("\"code\":\"charge_expired_for_capture\"");
    assertThat(stateOf(intentId)).containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void captures_beyond_day_seven_when_the_account_configured_a_window_that_reaches_it() {
    configureWith(FIXED_NOW, "P14D");
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");
    configureWith(FIXED_NOW.plus(Duration.ofDays(10)), "P14D");

    HttpResponse<byte[]> captured =
        post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), null);

    assertThat(captured.statusCode()).isEqualTo(200);
    assertThat(bodyOf(captured)).contains("\"status\":\"succeeded\"");
  }

  @Test
  void captures_within_a_window_shorter_than_the_millisecond_the_deadline_was_counted_in() {
    configureWith(FIXED_NOW, "PT0.0009S");
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");

    HttpResponse<byte[]> captured =
        post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), null);

    assertThat(captured.statusCode())
        .as(
            "the clock is pinned at the moment the intent was created, so a window of nine"
                + " hundred microseconds still has every one of them left to run, and the"
                + " deadline this account fixed has to sit after the moment the hold was taken"
                + " for any of it to be capturable at all")
        .isEqualTo(200);
    assertThat(bodyOf(captured)).contains("\"status\":\"succeeded\"");
  }

  @Test
  void answers_a_request_that_carries_no_api_key_on_the_wire_as_the_vendor_was_probed_answering() {
    VendorSample probed = VendorSamples.of("stripe").sample("no-api-key");

    HttpResponse<byte[]> response = postWithoutAKey(PAYMENT_INTENTS, hold("1099"));

    assertThat(String.valueOf(response.statusCode())).isEqualTo(probed.status().value());
    assertThat(response.headers().allValues("content-type"))
        .containsExactly(probed.header("Content-Type").value());
    assertThat(response.headers().allValues("www-authenticate"))
        .containsExactly(probed.header("WWW-Authenticate").value());
    assertThat(new CheckJson().at(bodyOf(response), "/error/message"))
        .isEqualTo(probed.json("/error/message").value());
  }

  @Test
  void cancels_a_hold_and_reports_the_release_through_the_control_state_route() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");

    HttpResponse<byte[]> cancelled =
        post(PAYMENT_INTENTS + "/" + intentId + "/cancel", Map.of(), null);

    assertThat(cancelled.statusCode()).isEqualTo(200);
    assertThat(bodyOf(cancelled)).contains("\"status\":\"canceled\"");
    assertThat(stateOf(intentId)).containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void the_lost_response_preset_answers_the_first_capture_without_running_it() {
    selectPreset("psp-5xx-then-success");
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");
    String key = "a-key-of-one-capture";

    HttpResponse<byte[]> lost = post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), key);
    HttpResponse<byte[]> repeated =
        post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), key);

    assertThat(lost.statusCode()).isEqualTo(502);
    assertThat(lost.headers().firstValue("Content-Type")).contains("text/plain;charset=utf-8");
    assertThat(new String(lost.body(), UTF_8))
        .startsWith("502 was answered in front of the provider.");
    assertThat(repeated.statusCode()).isEqualTo(200);
    assertThat(bodyOf(repeated)).contains("\"status\":\"succeeded\"");
    assertThat(repeated.headers().firstValue("Idempotent-Replayed")).isEmpty();
  }

  @Test
  void resetting_the_test_run_clears_the_payment_intents_it_stored() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");
    assertThat(stateStatusOf(intentId)).isEqualTo(200);

    reset();

    assertThat(stateStatusOf(intentId)).isEqualTo(404);
  }

  @Test
  void the_trail_holds_the_request_and_the_answer_of_every_call_it_answered() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");

    List<Map<String, Object>> records = auditRecords();

    assertThat(records).hasSize(2);
    assertThat(records.get(0)).containsEntry("direction", "INBOUND_REQUEST");
    assertThat(records.get(1))
        .containsEntry("direction", "OUTBOUND_RESPONSE")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("providerObjectId", intentId)
        .containsEntry("status", 200);
  }

  @Test
  @Timeout(value = 20, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void a_capture_whose_answer_was_lost_is_reconciled_by_retrieving_the_payment_intent() {
    selectPreset("capture-accepted-response-lost");
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"), null)), "id");

    Throwable lost =
        catchThrowable(() -> post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of(), null));

    assertThat(lost.getCause()).isInstanceOf(IOException.class);
    assertThat(lost.getCause()).hasMessageContaining("fixed content-length");
    assertThat(stateOf(intentId)).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(bodyOf(get(PAYMENT_INTENTS + "/" + intentId))).contains("\"status\":\"succeeded\"");
  }

  private HttpResponse<byte[]> get(String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + SECRET_KEY)
            .GET()
            .build());
  }

  @Test
  void confirms_a_payment_intent_over_the_wire_that_was_created_without_confirming() {
    Map<String, String> unconfirmed = new LinkedHashMap<>();
    unconfirmed.put("amount", "1099");
    unconfirmed.put("currency", "pln");
    unconfirmed.put("capture_method", "manual");
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, unconfirmed, null)), "id");

    HttpResponse<byte[]> confirmed =
        post(
            PAYMENT_INTENTS + "/" + intentId + "/confirm",
            Map.of("payment_method", "pm_card_visa"),
            null);

    assertThat(confirmed.statusCode()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"status\":\"requires_capture\"")
        .contains("\"payment_method\":\"pm_card_visa\"");
  }

  @Test
  void a_delayed_answer_arrives_whole_no_sooner_than_the_scenario_said() {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "action",
            Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 400)));

    long before = System.nanoTime();
    HttpResponse<byte[]> created = post(PAYMENT_INTENTS, hold("1099"), null);
    Duration waited = Duration.ofNanos(System.nanoTime() - before);

    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(bodyOf(created)).contains("\"status\":\"requires_capture\"");
    assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(350));
  }

  @Test
  void resetting_a_test_run_answers_a_held_response_instead_of_delivering_stale_bytes()
      throws Exception {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "action",
            Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 5000)));

    ExecutorService client = Executors.newSingleThreadExecutor();
    try {
      Future<HttpResponse<byte[]>> held =
          client.submit(() -> post(PAYMENT_INTENTS, hold("1099"), null));
      AHeldResponse.isHeldBy(delays);
      reset();

      HttpResponse<byte[]> answer = held.get(15, TimeUnit.SECONDS);

      assertThat(answer.statusCode()).isEqualTo(503);
      assertThat(answer.headers().firstValue("content-type").orElseThrow())
          .startsWith("text/plain");
      assertThat(bodyOf(answer))
          .contains("held this response for a scenario delay")
          .contains("was reset before the delay elapsed");
    } finally {
      client.shutdownNow();
      configure(FIXED_NOW);
    }
  }

  private void createScenario(Map<String, Object> declared) {
    http.post()
        .uri("/__simulator/stripe/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(declared)
        .retrieve()
        .toBodilessEntity();
  }

  private void configure(Instant now) {
    configureWith(now, THREE_DAYS);
  }

  private void configureWith(Instant now, String captureWindow) {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", now.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of(
                            StripeAccount.SECRET_KEY_PROPERTY,
                            SECRET_KEY,
                            StripeCaptureWindow.SETTING,
                            captureWindow)))))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private void selectPreset(String preset) {
    http.post()
        .uri("/__simulator/stripe/scenarios/presets")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "preset", preset))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> stateOf(String intentId) {
    return http.get()
        .uri("/__simulator/stripe/state/" + intentId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private int stateStatusOf(String intentId) {
    return http.get()
        .uri("/__simulator/stripe/state/" + intentId + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }

  private List<Map<String, Object>> auditRecords() {
    return http.get()
        .uri("/__simulator/stripe/audit?origin=PROVIDER_API&testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> operationsOf(Map<String, Object> state) {
    return (List<Map<String, Object>>) state.get("operations");
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form, String key) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY);
    if (key != null) {
      request = request.header("Idempotency-Key", key);
    }
    return sent(request.POST(HttpRequest.BodyPublishers.ofString(encoded(form))).build());
  }

  private HttpResponse<byte[]> postWithoutAKey(String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<byte[]> sent(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private static Map<String, String> hold(String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    return form;
  }

  private static String bodyOf(HttpResponse<byte[]> response) {
    return new String(response.body(), UTF_8);
  }

  private static String valueIn(String json, String member) {
    String opening = "\"" + member + "\":\"";
    int start = json.indexOf(opening);
    if (start < 0) {
      return "";
    }
    int from = start + opening.length();
    return json.substring(from, json.indexOf('"', from));
  }
}
