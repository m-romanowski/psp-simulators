package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class ClockExpiresAHoldAndAShortLivedKeyIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("clock-expiries-stripe-it");

  private static final String PINNED = "2026-09-11T12:00:00Z";

  private static final String MERCHANT = "merchant-1";

  private static final String SECRET_KEY = "sk_test_51ClockExpiriesSimulator";

  private static final String THREE_DAYS = "P3D";

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";

  private static final String CUSTOMERS = "/v1/customers";

  private static final String EPHEMERAL_KEYS = "/v1/ephemeral_keys";

  private static final String ADVANCE = "/__simulator/stripe/clock/advance";

  private static final String AN_EMAIL = "payer@example.test";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void pinTheRunAndConfigureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
  }

  @AfterEach
  void emptyTheRun() {
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
  void a_hold_a_harness_advanced_past_its_capture_window_reads_as_cancelled_and_refuses_capture() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"))), "id");
    Map<String, Object> held = stateOf(intentId);

    advance("P4D");

    HttpResponse<byte[]> captured = post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of());
    assertThat(held).containsEntry("nativeStatus", "requires_capture");
    assertThat(stateOf(intentId))
        .containsEntry("nativeStatus", "canceled")
        .containsEntry("normalizedStatus", "CANCELLED");
    assertThat(captured.statusCode()).isEqualTo(400);
    assertThat(bodyOf(captured)).contains("charge_expired_for_capture");
  }

  @Test
  void a_hold_advanced_to_a_moment_short_of_its_capture_window_is_still_captured() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"))), "id");

    advance("P2D");

    HttpResponse<byte[]> captured = post(PAYMENT_INTENTS + "/" + intentId + "/capture", Map.of());
    assertThat(captured.statusCode()).isEqualTo(200);
    assertThat(bodyOf(captured)).contains("\"status\":\"succeeded\"");
    assertThat(stateOf(intentId)).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void two_advances_that_cross_the_capture_window_between_them_expire_the_hold_the_first_left() {
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, hold("1099"))), "id");

    advance("P2D");
    Map<String, Object> halfway = stateOf(intentId);
    advance("P2D");

    assertThat(halfway).containsEntry("nativeStatus", "requires_capture");
    assertThat(stateOf(intentId)).containsEntry("nativeStatus", "canceled");
  }

  @Test
  void a_short_lived_key_a_harness_advanced_past_its_hour_authenticates_nothing_afterwards() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    String secret = valueIn(bodyOf(post(EPHEMERAL_KEYS, Map.of("customer", customerId))), "secret");
    HttpResponse<byte[]> whileItLived = getAs(secret, CUSTOMERS + "/" + customerId);

    advance("PT2H");

    HttpResponse<byte[]> afterwards = getAs(secret, CUSTOMERS + "/" + customerId);
    assertThat(whileItLived.statusCode()).isEqualTo(200);
    assertThat(afterwards.statusCode()).isEqualTo(401);
    assertThat(bodyOf(afterwards)).contains("expires moment has passed");
  }

  @Test
  void a_short_lived_key_advanced_to_a_moment_short_of_its_hour_still_reaches_its_customer() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    String secret = valueIn(bodyOf(post(EPHEMERAL_KEYS, Map.of("customer", customerId))), "secret");

    advance("PT30M");

    assertThat(getAs(secret, CUSTOMERS + "/" + customerId).statusCode()).isEqualTo(200);
  }

  private void advance(String advanceBy) {
    HttpResponse<byte[]> advanced =
        sent(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + ADVANCE))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"testRunId\":\""
                            + RUN.value()
                            + "\",\"advanceBy\":\""
                            + advanceBy
                            + "\"}"))
                .build());
    assertThat(advanced.statusCode()).isEqualTo(200);
  }

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/stripe/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY)
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<byte[]> getAs(String key, String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + key)
            .GET()
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

  private static Map<String, String> hold(String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", amount);
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    return form;
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
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

  private void configure() {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", PINNED),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("secretKey", SECRET_KEY, "captureWindow", THREE_DAYS)))))
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
}
