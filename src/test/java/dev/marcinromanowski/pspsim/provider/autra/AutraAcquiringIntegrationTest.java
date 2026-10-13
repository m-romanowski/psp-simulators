package dev.marcinromanowski.pspsim.provider.autra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.fault.AHeldResponse;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class AutraAcquiringIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("autra-acquiring-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "autra-client-id";
  private static final String CLIENT_SECRET = "autra-client-secret";

  @LocalServerPort private int port;

  @Autowired private DelayedDeliveries delays;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(List.of(anAccount()));
    theExamplePairIsACardOfTheRun();
  }

  private void theExamplePairIsACardOfTheRun() {
    http.post()
        .uri("/__simulator/autra/fixtures/payment-methods")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "merchantId",
                ACCOUNT,
                "ownerId",
                "owner-1",
                "type",
                "card",
                "alias",
                "the-example-pair",
                "card",
                Map.of(
                    "brand",
                    "visa",
                    "last4",
                    "1111",
                    "expMonth",
                    12,
                    "expYear",
                    2030,
                    "funding",
                    "credit"),
                "providerIds",
                Map.of("token", "slug-token-abc")))
        .retrieve()
        .toBodilessEntity();
  }

  @AfterEach
  void unconfigureTheMerchant() {
    reset();
    configure(List.of());
  }

  @Test
  void a_token_a_session_a_hold_and_a_capture_over_the_wire_leave_a_payment_read_as_captured() {
    String token = anAccessToken();

    HttpResponse<String> opened =
        post("/v1/acquiring/payments/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}");
    String transactionalToken = valueIn(opened.body(), AutraFields.TRANSACTIONAL_TOKEN);

    HttpResponse<String> held =
        post(
            "/v1/acquiring/payments/pre-authorization",
            token,
            preAuthorization(transactionalToken, "500.00", null));
    String paymentId = valueIn(held.body(), AutraFields.PAYMENT_ID_UPPERCASE_D);

    HttpResponse<String> captured =
        post(
            "/v1/acquiring/payments/" + paymentId + "/capture",
            token,
            "{\"transactionalToken\":\""
                + transactionalToken
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"amount\":480.00}");

    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(opened.headers().firstValue("content-type").orElseThrow())
        .startsWith("application/json");
    assertThat(transactionalToken).isNotEmpty();
    assertThat(held.statusCode()).isEqualTo(200);
    assertThat(held.body()).contains("\"status\":\"ACCEPTED\"");
    assertThat(captured.statusCode()).isEqualTo(200);
    assertThat(captured.body()).contains("\"status\":\"" + AutraStatus.CAPTURED + "\"");
    assertThat(stateOf(paymentId))
        .containsEntry("providerObjectId", paymentId)
        .containsEntry("nativeStatus", AutraStatus.CAPTURED)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", "500.00")
        .containsEntry("currency", "BRL");
  }

  @Test
  void a_merchant_releases_a_hold_it_never_captured_and_the_payment_reads_cancelled() {
    String token = anAccessToken();
    String transactionalToken =
        valueIn(
            post(
                    "/v1/acquiring/payments/initialize",
                    token,
                    "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            AutraFields.TRANSACTIONAL_TOKEN);
    String paymentId =
        valueIn(
            post(
                    "/v1/acquiring/payments/pre-authorization",
                    token,
                    preAuthorization(transactionalToken, "500.00", null))
                .body(),
            AutraFields.PAYMENT_ID_UPPERCASE_D);

    HttpResponse<String> released =
        post(
            "/v1/acquiring/payments/" + paymentId + "/void",
            token,
            "{\"transactionalToken\":\""
                + transactionalToken
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"amount\":500.00,\"currency\":\"BRL\"}");

    assertThat(released.statusCode()).isEqualTo(200);
    assertThat(released.body())
        .contains("\"" + AutraFields.PAYMENT_ID_LOWERCASE_D + "\":\"" + paymentId + "\"")
        .contains("\"status\":\"" + AutraStatus.VOIDED + "\"")
        .contains("\"" + AutraFields.RRN + "\"")
        .contains("\"trackingNumber\"");
    assertThat(stateOf(paymentId)).containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void a_repeated_order_is_answered_409_over_the_wire_and_reserves_nothing_a_second_time() {
    String token = anAccessToken();
    String transactionalToken =
        valueIn(
            post(
                    "/v1/acquiring/payments/initialize",
                    token,
                    "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            AutraFields.TRANSACTIONAL_TOKEN);
    post(
        "/v1/acquiring/payments/pre-authorization",
        token,
        preAuthorization(transactionalToken, "500.00", "pedido-it-1"));

    HttpResponse<String> repeated =
        post(
            "/v1/acquiring/payments/pre-authorization",
            token,
            preAuthorization(transactionalToken, "500.00", "pedido-it-1"));

    assertThat(repeated.statusCode()).isEqualTo(409);
    assertThat(repeated.body()).contains(AutraErrors.ORDER_ALREADY_PREAUTHORIZED);
    assertThat(objects()).hasSize(1);
  }

  @Test
  void a_bearer_this_run_never_issued_is_answered_401_over_the_wire() {
    assertThat(
            post(
                    "/v1/acquiring/payments/initialize",
                    "never-issued",
                    "{\"documentId\":\"" + DOCUMENT + "\"}")
                .statusCode())
        .isEqualTo(401);
  }

  @Test
  void
      the_token_call_and_the_call_it_authenticated_are_in_the_trail_with_the_status_each_answered() {
    String token = anAccessToken();
    post("/v1/acquiring/payments/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}");

    List<Map<String, Object>> records = auditRecords();

    assertThat(records)
        .extracting(record -> record.get("path"))
        .contains("/v1/oauth/token", "/v1/acquiring/payments/initialize");
    assertThat(records)
        .filteredOn(record -> "OUTBOUND_RESPONSE".equals(record.get("direction")))
        .allSatisfy(record -> assertThat(record).containsEntry("status", 200));
  }

  @Test
  void a_delayed_answer_arrives_whole_no_sooner_than_the_scenario_said() {
    String token = anAccessToken();
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "action",
            Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 400)));

    long before = System.nanoTime();
    HttpResponse<String> opened =
        post("/v1/acquiring/payments/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}");
    Duration waited = Duration.ofNanos(System.nanoTime() - before);

    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(opened.body()).contains(AutraFields.TRANSACTIONAL_TOKEN);
    assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(350));
  }

  @Test
  void resetting_a_test_run_answers_a_held_response_instead_of_delivering_stale_bytes()
      throws Exception {
    String token = anAccessToken();
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "action",
            Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 5000)));

    ExecutorService client = Executors.newSingleThreadExecutor();
    try {
      Future<HttpResponse<String>> held =
          client.submit(
              () ->
                  post(
                      "/v1/acquiring/payments/initialize",
                      token,
                      "{\"documentId\":\"" + DOCUMENT + "\"}"));
      AHeldResponse.isHeldBy(delays);
      reset();

      HttpResponse<String> answer = held.get(15, TimeUnit.SECONDS);

      assertThat(answer.statusCode()).isEqualTo(503);
      assertThat(answer.body())
          .contains("held this response for a scenario delay")
          .contains("was reset before the delay elapsed");
    } finally {
      client.shutdownNow();
      configure(List.of(anAccount()));
    }
  }

  private void createScenario(Map<String, Object> declared) {
    http.post()
        .uri("/__simulator/autra/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(declared)
        .retrieve()
        .toBodilessEntity();
  }

  private String anAccessToken() {
    HttpResponse<String> minted =
        post(
            "/v1/oauth/token?grant_type=" + AutraFields.CLIENT_CREDENTIALS,
            null,
            "",
            AutraFields.BASIC
                + Base64.getEncoder()
                    .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(UTF_8)));
    assertThat(minted.statusCode()).isEqualTo(200);
    return valueIn(minted.body(), AutraFields.ACCESS_TOKEN);
  }

  private static String preAuthorization(String transactionalToken, String amount, String orderId) {
    return "{\"transactionalToken\":\""
        + transactionalToken
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"transactionType\":\"CREDIT\",\"currency\":\"BRL\",\"amount\":"
        + amount
        + ",\"installments\":1"
        + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
        + ",\"tokenData\":{\"slugToken\":\"slug-token-abc\"}}";
  }

  private static Map<String, Object> anAccount() {
    return Map.of(
        "accountId",
        ACCOUNT,
        "configuration",
        Map.of(
            AutraMerchant.CLIENT_ID_PROPERTY,
            CLIENT_ID,
            AutraMerchant.CLIENT_SECRET_PROPERTY,
            CLIENT_SECRET,
            AutraMerchant.DOCUMENT_ID_PROPERTY,
            DOCUMENT));
  }

  private void configure(List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/autra/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                accounts,
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/autra/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> stateOf(String paymentId) {
    return http.get()
        .uri("/__simulator/autra/state/" + paymentId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> objects() {
    return http.get()
        .uri("/__simulator/autra/objects?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> auditRecords() {
    return http.get()
        .uri("/__simulator/autra/audit?origin=PROVIDER_API&testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  @Test
  void a_payment_a_merchant_made_over_the_wire_is_read_back_over_the_wire_with_its_timeline() {
    String token = anAccessToken();
    String transactionalToken =
        valueIn(
            post(
                    "/v1/acquiring/payments/initialize",
                    token,
                    "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            AutraFields.TRANSACTIONAL_TOKEN);
    String paymentId =
        valueIn(
            post(
                    "/v1/acquiring/payments/pre-authorization",
                    token,
                    preAuthorization(transactionalToken, "500.00", null))
                .body(),
            AutraFields.PAYMENT_ID_UPPERCASE_D);
    post(
        "/v1/acquiring/payments/" + paymentId + "/capture",
        token,
        "{\"transactionalToken\":\""
            + transactionalToken
            + "\",\"documentId\":\""
            + DOCUMENT
            + "\",\"amount\":480.00}");

    HttpResponse<String> read = get("/v1/acquiring/payments/" + paymentId, token, DOCUMENT);

    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(read.headers().firstValue("content-type").orElseThrow())
        .startsWith("application/json");
    assertThat(read.body())
        .contains(textField(AutraFields.PAYMENT_ID_UPPERCASE_D, paymentId))
        .contains(textField(AutraFields.TYPE, AutraTimeline.PREAUTH))
        .contains(textField(AutraFields.STATUS, AutraStatus.CAPTURED))
        .contains("\"refundedAmount\":0")
        .contains(textField(AutraFields.TRANSACTION_METHOD, "TOKEN"))
        .contains(textField(AutraFields.CREATED_AT, "2026-09-22T12:00:00Z"))
        .contains(textField(AutraFields.TYPE, AutraTimeline.CAPTURE))
        .doesNotContain("\"maskedCard\"")
        .doesNotContain("\"brandReferenceId\"")
        .doesNotContain("\"nsu\"");
    assertThat(read.body())
        .as(
            "autra publishes voidRetryable false \"quando ja foi ... capturado\", so a capture"
                + " over the wire turns it off even though this simulator's void route would"
                + " still admit a reversal of what the capture took")
        .contains("\"voidRetryable\":false");
  }

  @Test
  void the_listing_answers_the_window_it_applied_and_the_payments_inside_it_over_the_wire() {
    String token = anAccessToken();
    String transactionalToken =
        valueIn(
            post(
                    "/v1/acquiring/payments/initialize",
                    token,
                    "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            AutraFields.TRANSACTIONAL_TOKEN);
    String paymentId =
        valueIn(
            post(
                    "/v1/acquiring/payments/pre-authorization",
                    token,
                    preAuthorization(transactionalToken, "500.00", null))
                .body(),
            AutraFields.PAYMENT_ID_UPPERCASE_D);

    HttpResponse<String> listed =
        get("/v1/acquiring/payments?documentId=" + DOCUMENT + "&type=PREAUTH&limit=200", token);

    assertThat(listed.statusCode()).isEqualTo(200);
    assertThat(listed.body())
        .contains("\"items\":[")
        .contains(textField(AutraFields.PAYMENT_ID_UPPERCASE_D, paymentId))
        .contains("\"page\":1")
        .contains("\"limit\":200")
        .contains("\"hasMore\":false")
        .contains(textField(AutraFields.FROM, "2026-09-15T12:00:01Z"))
        .contains(textField(AutraFields.TO, "2026-09-22T12:00:01Z"));
    assertThat(get("/v1/acquiring/payments?documentId=" + DOCUMENT + "&type=SALE", token).body())
        .contains("\"items\":[]");
  }

  @Test
  void the_published_refusals_of_the_two_reads_reach_a_client_as_the_vendor_envelope() {
    String token = anAccessToken();

    assertThat(get("/v1/acquiring/payments/not-a-uuid?documentId=" + DOCUMENT, token).body())
        .contains("\"errors\":[")
        .contains(textField(AutraFields.CODE, AutraErrors.INVALID_PAYMENT_ID));
    HttpResponse<String> windowTooWide =
        get(
            "/v1/acquiring/payments?documentId="
                + DOCUMENT
                + "&from=2026-01-01&to=2026-09-22T12:00:00Z",
            token);
    assertThat(windowTooWide.statusCode()).isEqualTo(400);
    assertThat(windowTooWide.body())
        .contains(textField(AutraFields.CODE, AutraErrors.RANGE_TOO_LARGE));
    HttpResponse<String> unnamedMerchant = get("/v1/acquiring/payments", token);
    assertThat(unnamedMerchant.statusCode()).isEqualTo(400);
    assertThat(unnamedMerchant.body())
        .contains(textField(AutraFields.CODE, AutraErrors.MISSING_DOCUMENT_ID));
  }

  private static String textField(String name, String value) {
    return '"' + name + "\":\"" + value + '"';
  }

  private HttpResponse<String> get(String path, String bearer) {
    return get(path, bearer, null);
  }

  private HttpResponse<String> get(String path, String bearer, String merchantDocument) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
      if (bearer != null) {
        request = request.header(AutraFields.AUTHORIZATION, AutraFields.BEARER + bearer);
      }
      if (merchantDocument != null) {
        request = request.header(AutraFields.MERCHANT_DOCUMENT_HEADER, merchantDocument);
      }
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private HttpResponse<String> post(String path, String bearer, String body) {
    return post(path, bearer, body, null);
  }

  private HttpResponse<String> post(String path, String bearer, String body, String authorization) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", AutraFields.JSON)
              .POST(HttpRequest.BodyPublishers.ofString(body));
      if (bearer != null) {
        request = request.header(AutraFields.AUTHORIZATION, AutraFields.BEARER + bearer);
      }
      if (authorization != null) {
        request = request.header(AutraFields.AUTHORIZATION, authorization);
      }
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String valueIn(String json, String field) {
    String opening = "\"" + field + "\":\"";
    int at = json.indexOf(opening);
    if (at < 0) {
      return "";
    }
    return json.substring(at + opening.length(), json.indexOf('"', at + opening.length()));
  }
}
