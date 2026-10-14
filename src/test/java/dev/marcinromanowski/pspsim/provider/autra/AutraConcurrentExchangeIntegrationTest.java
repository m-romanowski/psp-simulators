package dev.marcinromanowski.pspsim.provider.autra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.AtOnce;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.TheRequestsUnderOneKey;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class AutraConcurrentExchangeIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("autra-concurrent-exchange-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "autra-client-id";
  private static final String CLIENT_SECRET = "autra-client-secret";
  private static final String ORDER = "pedido-concurrente-1";
  private static final String TABLE = "autra_keyed_exchange";
  private static final int CONCURRENT = 16;

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(List.of(anAccount()));
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
  void holds_sent_at_once_under_one_order_are_numbered_after_the_one_that_claimed_it()
      throws Exception {
    String token = anAccessToken();
    String session = aSessionToken(token);
    String hold = preAuthorization(session, "500.00", ORDER);

    List<Integer> statuses =
        AtOnce.sent(
            CONCURRENT,
            () -> post("/v1/acquiring/payments/pre-authorization", token, hold).statusCode());

    assertThat(statuses).hasSize(CONCURRENT).containsOnly(200, 409);
    assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheName(
            jdbc, TABLE, AutraKeyMechanisms.ORDER_ID, ORDER, RUN.value()),
        CONCURRENT,
        Set.of(
            ExchangeVerdict.DUPLICATE_KEY_REFUSAL, ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "autra", RUN.value());
  }

  @Test
  void captures_declined_at_once_are_each_answered_and_each_recorded_on_the_payment()
      throws Exception {
    String token = anAccessToken();
    String session = aSessionToken(token);
    String paymentId =
        valueIn(
            post(
                    "/v1/acquiring/payments/pre-authorization",
                    token,
                    preAuthorization(session, "500.00", null))
                .body(),
            AutraFields.PAYMENT_ID_UPPERCASE_D);
    declineEveryCapture();
    String capture =
        "{\"transactionalToken\":\""
            + session
            + "\",\"documentId\":\""
            + DOCUMENT
            + "\",\"amount\":480.00}";

    List<Integer> statuses =
        AtOnce.sent(
            CONCURRENT,
            () ->
                post("/v1/acquiring/payments/" + paymentId + "/capture", token, capture)
                    .statusCode());

    assertThat(statuses)
        .as("every capture is answered by the acquirer rather than failed by its own bookkeeping")
        .hasSize(CONCURRENT)
        .allMatch(status -> status < 500);
    assertThat(
            jdbc.sql(
                    "select count(*) from autra_payment_event"
                        + " where test_run_id = ? and payment_id = ?")
                .params(RUN.value(), paymentId)
                .query(Integer.class)
                .single())
        .as("the hold and every declined capture are on the payment's timeline")
        .isEqualTo(CONCURRENT + 1);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheName(
            jdbc, TABLE, AutraKeyMechanisms.PAYMENT_ID, paymentId, RUN.value()),
        CONCURRENT,
        Set.of(ExchangeVerdict.CORRELATED_RETRY));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "autra", RUN.value());
  }

  private void declineEveryCapture() {
    http.post()
        .uri("/__simulator/autra/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "priority",
                Integer.valueOf(10),
                "match",
                Map.of("operation", AutraOperations.CAPTURE),
                "action",
                Map.of("type", "TERMINAL_FAILURE", "providerCode", AutraErrors.INSUFFICIENT_FUNDS)))
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

  private String aSessionToken(String token) {
    return valueIn(
        post("/v1/acquiring/payments/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
            .body(),
        AutraFields.TRANSACTIONAL_TOKEN);
  }

  private static String preAuthorization(
      String transactionalToken, String amount, @Nullable String orderId) {
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

  private HttpResponse<String> post(String path, @Nullable String bearer, String body) {
    return post(path, bearer, body, null);
  }

  private HttpResponse<String> post(
      String path, @Nullable String bearer, String body, @Nullable String authorization) {
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
