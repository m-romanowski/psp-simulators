package dev.marcinromanowski.pspsim.provider.worldline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class ClockExpiresAHostedSessionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("clock-hosted-session-worldline-it");

  private static final String PINNED = "2026-09-15T10:00:00Z";

  private static final String ACCOUNT = "account-1";

  private static final String MERCHANT = "pspid-clock";

  private static final String API_KEY = "clock-worldline-key";

  private static final String API_SECRET = "clock-worldline-secret";

  private static final String ADVANCE = "/__simulator/worldline/clock/advance";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void pinTheRunAndConfigureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
  }

  @AfterEach
  void emptyTheRun() {
    reset();
    http.put()
        .uri("/__simulator/worldline/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "clock", Map.of(), "accounts", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void a_hosted_session_a_harness_advanced_past_its_life_span_answers_to_nobody_afterwards() {
    String sessionId = valueIn(bodyOf(openASession()), "hostedTokenizationId");
    HttpResponse<byte[]> whileItLived = readTheSession(sessionId);

    advance("PT4H");

    HttpResponse<byte[]> afterwards = readTheSession(sessionId);
    assertThat(whileItLived.statusCode()).isEqualTo(200);
    assertThat(afterwards.statusCode()).isEqualTo(404);
    assertThat(bodyOf(afterwards)).contains("past the three hours");
    assertThat(stateOf(sessionId)).containsEntry("nativeStatus", "EXPIRED");
  }

  @Test
  void the_state_read_calls_a_session_past_its_life_span_expired_before_the_wire_is_ever_asked() {
    String sessionId = valueIn(bodyOf(openASession()), "hostedTokenizationId");

    advance("PT4H");

    Map<String, Object> read = stateOf(sessionId);
    assertThat(read)
        .containsEntry("nativeStatus", "EXPIRED")
        .containsEntry("normalizedStatus", "CANCELLED");
    assertThat(readTheSession(sessionId).statusCode()).isEqualTo(404);
  }

  @Test
  void a_hosted_session_advanced_to_a_moment_short_of_its_life_span_is_still_awaiting_its_payer() {
    String sessionId = valueIn(bodyOf(openASession()), "hostedTokenizationId");

    advance("PT2H");

    assertThat(readTheSession(sessionId).statusCode()).isEqualTo(200);
    assertThat(stateOf(sessionId))
        .containsEntry("nativeStatus", "AWAITING_THE_PAYER")
        .containsEntry("normalizedStatus", "PROCESSING");
  }

  @Test
  void four_advances_of_an_hour_expire_the_session_the_third_of_them_carried_past_three_hours() {
    String sessionId = valueIn(bodyOf(openASession()), "hostedTokenizationId");

    advance("PT1H");
    advance("PT1H");
    int afterTwoHours = readTheSession(sessionId).statusCode();
    advance("PT1H");
    int afterThreeHours = readTheSession(sessionId).statusCode();

    assertThat(afterTwoHours).isEqualTo(200);
    assertThat(afterThreeHours).isEqualTo(404);
    assertThat(stateOf(sessionId)).containsEntry("nativeStatus", "EXPIRED");
  }

  private void advance(String advanceBy) {
    Map<String, Object> advanced =
        http.post()
            .uri(ADVANCE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "advanceBy", advanceBy))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(advanced).containsEntry("advancedBy", advanceBy);
  }

  private HttpResponse<byte[]> openASession() {
    return sent(
        signed(HttpRequest.newBuilder(at("/v2/" + MERCHANT + "/hostedtokenizations")))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build());
  }

  private HttpResponse<byte[]> readTheSession(String sessionId) {
    return sent(
        signed(HttpRequest.newBuilder(at("/v2/" + MERCHANT + "/hostedtokenizations/" + sessionId)))
            .GET()
            .build());
  }

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/worldline/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private static HttpRequest.Builder signed(HttpRequest.Builder request) {
    return request.header("Authorization", WorldlineSignature.SCHEME + API_KEY + ":unverified");
  }

  private URI at(String path) {
    return URI.create("http://localhost:" + port + path);
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
        .uri("/__simulator/worldline/config")
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
                        ACCOUNT,
                        "configuration",
                        Map.of(
                            "merchantId",
                            MERCHANT,
                            "apiKey",
                            API_KEY,
                            "apiSecret",
                            API_SECRET,
                            WorldlineAuthentication.SETTING,
                            WorldlineAuthentication.THE_SIGNATURE_IS_NOT_VERIFIED.name())))))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/worldline/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }
}
