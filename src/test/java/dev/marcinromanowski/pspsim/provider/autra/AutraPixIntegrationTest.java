package dev.marcinromanowski.pspsim.provider.autra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class AutraPixIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("autra-pix-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "autra-pix-client-id";
  private static final String CLIENT_SECRET = "autra-pix-client-secret";
  private static final String SECRET = "a-tenant-callback-secret-of-thirty-two";
  private static final String TARGET = "http://localhost:1/autra/acquiring";
  private static final String ORDER = "pedido-abc-123";

  @LocalServerPort private int port;

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
  void a_token_a_pix_session_a_qr_and_a_payer_who_paid_leave_a_pix_that_reads_back_as_paid() {
    String token = anAccessToken();

    HttpResponse<String> opened =
        post("/v1/acquiring/pix/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}");
    String transactionalToken = valueIn(opened.body(), AutraFields.TRANSACTIONAL_TOKEN);

    HttpResponse<String> created =
        post("/v1/acquiring/pix/checkout", token, checkout(transactionalToken, "99.90", ORDER));
    String pixId = valueIn(created.body(), AutraFields.PIX_ID);

    Map<String, Object> completion = completeAs("PAID", pixId);

    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(opened.body()).contains("\"code\":\"" + AutraStatus.ACCEPTED + "\"");
    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.body()).contains("\"status\":\"" + AutraStatus.PENDING + "\"");
    assertThat(valueIn(created.body(), AutraFields.QR_CODE))
        .startsWith(CreatePixCheckout.THE_PUBLISHED_QR_PREFIX);
    assertThat(completion).containsEntry("outcome", "PAID");
    assertThat(stateOf(pixId))
        .containsEntry("providerObjectId", pixId)
        .containsEntry("nativeStatus", AutraStatus.AUTHORIZED)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", "99.90")
        .containsEntry("currency", "BRL");
  }

  @Test
  void the_payer_step_of_a_paid_pix_queues_the_acquiring_event_the_merchant_would_receive() {
    String pixId = aQrCode();

    completeAs("PAID", pixId);

    List<Map<String, Object>> queued = deliveries();
    assertThat(queued).hasSize(1);
    assertThat(queued.get(0))
        .containsEntry("eventType", "acquiring.financial_transaction")
        .containsEntry("providerObjectId", pixId);
  }

  @Test
  void a_payer_who_left_the_qr_unpaid_leaves_a_pix_that_reads_back_as_cancelled() {
    String pixId = aQrCode();

    completeAs("ABANDONED", pixId);

    assertThat(stateOf(pixId))
        .containsEntry("nativeStatus", AutraStatus.EXPIRED)
        .containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void two_checkouts_under_one_order_hand_out_two_qr_codes_over_the_wire() {
    String token = anAccessToken();

    String first = valueIn(aCheckoutOf(token, ORDER).body(), AutraFields.PIX_ID);
    HttpResponse<String> repeat = aCheckoutOf(token, ORDER);

    assertThat(repeat.statusCode()).isEqualTo(200);
    assertThat(valueIn(repeat.body(), AutraFields.PIX_ID)).isNotEqualTo(first);
    assertThat(objects()).hasSize(2);
  }

  @Test
  void a_checkout_whose_session_token_expired_between_the_steps_is_answered_403() {
    HttpResponse<String> refused =
        post(
            "/v1/acquiring/pix/checkout",
            anAccessToken(),
            checkout("a-token-that-names-no-session", "99.90", null));

    assertThat(refused.statusCode()).isEqualTo(CreatePixCheckout.FORBIDDEN);
    assertThat(refused.body()).contains(AutraErrors.UNAUTHORIZED);
  }

  @Test
  void a_pix_and_a_pre_authorization_of_one_run_are_both_listed_newest_first() {
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
                    "{\"transactionalToken\":\""
                        + transactionalToken
                        + "\",\"documentId\":\""
                        + DOCUMENT
                        + "\",\"transactionType\":\"CREDIT\",\"currency\":\"BRL\",\"amount\":500.00,"
                        + "\"installments\":1,\"tokenData\":{\"slugToken\":\"slug-token-abc\"}}")
                .body(),
            AutraFields.PAYMENT_ID_UPPERCASE_D);

    String pixId = aQrCode();

    assertThat(objects())
        .extracting(listed -> listed.get("providerObjectId"))
        .containsExactly(pixId, paymentId);
  }

  private String aQrCode() {
    return valueIn(aCheckoutOf(anAccessToken(), ORDER).body(), AutraFields.PIX_ID);
  }

  private HttpResponse<String> aCheckoutOf(String token, String orderId) {
    String transactionalToken =
        valueIn(
            post("/v1/acquiring/pix/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            AutraFields.TRANSACTIONAL_TOKEN);
    return post(
        "/v1/acquiring/pix/checkout", token, checkout(transactionalToken, "99.90", orderId));
  }

  private Map<String, Object> completeAs(String outcome, String pixId) {
    return http.post()
        .uri("/__simulator/autra/hosted/" + pixId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "outcome", outcome))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private static String checkout(String transactionalToken, String amount, String orderId) {
    return "{\"transactionalToken\":\""
        + transactionalToken
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"amount\":"
        + amount
        + ",\"currency\":\"BRL\",\"description\":\"Pedido #1234\""
        + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
        + "}";
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
            DOCUMENT,
            AutraWebhookSecret.SETTING,
            SECRET));
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
                accounts.isEmpty()
                    ? List.of()
                    : List.of(Map.of("accountId", ACCOUNT, "url", TARGET))))
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

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/autra/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> objects() {
    return http.get()
        .uri("/__simulator/autra/objects?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> deliveries() {
    return http.get()
        .uri("/__simulator/autra/webhooks?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
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
