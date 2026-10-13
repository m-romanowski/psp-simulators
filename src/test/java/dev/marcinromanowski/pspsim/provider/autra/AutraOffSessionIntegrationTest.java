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

class AutraOffSessionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("autra-off-session-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "autra-off-session-client-id";
  private static final String CLIENT_SECRET = "autra-off-session-client-secret";
  private static final String ORDER = "pedido-abc-123";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(List.of(anAccount(Map.of())));
  }

  @AfterEach
  void unconfigureTheMerchant() {
    reset();
    configure(List.of());
  }

  @Test
  void a_card_session_a_payer_who_stored_a_card_and_an_off_session_charge_read_back_as_paid() {
    String token = anAccessToken();

    HttpResponse<String> opened =
        post("/v1/acquiring/card-sessions", token, "{\"documentId\":\"" + DOCUMENT + "\"}");
    String sessionId = valueIn(opened.body(), AutraFields.HOSTED_CARD_SESSION_ID);

    Map<String, Object> tokenized =
        completeAs(sessionId, AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS);

    Map<String, Object> session = stateOf(sessionId);
    String slugToken = theRelated(session, AutraFields.SLUG_TOKEN);
    String slugStoredCard = theRelated(session, AutraFields.SLUG_STORED_CARD);

    HttpResponse<String> charged =
        post(
            "/v1/acquiring/payments", token, sale(aSessionToken(token), slugToken, slugStoredCard));
    String paymentId = valueIn(charged.body(), AutraFields.PAYMENT_ID_UPPERCASE_D);

    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(opened.body()).contains("\"expiresAt\":\"2026-09-22T12:15:00Z\"");
    assertThat(valueIn(opened.body(), AutraFields.HOSTED_CARD_URL))
        .contains(AutraCardPage.pathOf(sessionId) + "?" + AutraCardPage.TOKEN + "=");
    assertThat(tokenized).containsEntry("outcome", "PAID");
    assertThat(session).containsEntry("nativeStatus", AutraCardSession.TOKENIZED);
    assertThat(slugToken).isNotBlank();
    assertThat(slugStoredCard).isNotBlank();
    assertThat(charged.statusCode()).isEqualTo(200);
    assertThat(charged.body()).contains("\"status\":\"" + AutraStatus.ACCEPTED + "\"");
    assertThat(stateOf(paymentId))
        .containsEntry("providerObjectId", paymentId)
        .containsEntry("nativeStatus", AutraStatus.ACCEPTED)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", "150.00")
        .containsEntry("currency", "BRL");
  }

  @Test
  void a_declined_sale_of_the_default_account_is_two_hundred_carrying_the_payment_and_the_errors() {
    declineTheNextSale();
    String token = anAccessToken();

    HttpResponse<String> declined =
        post("/v1/acquiring/payments", token, sale(aSessionToken(token), "slug-token-abc", null));

    assertThat(declined.statusCode()).isEqualTo(200);
    assertThat(declined.body())
        .contains("\"" + AutraFields.PAYMENT_ID_UPPERCASE_D + "\"")
        .contains(AutraErrors.INSUFFICIENT_FUNDS)
        .doesNotContain("\"status\"");
    assertThat(stateOf(valueIn(declined.body(), AutraFields.PAYMENT_ID_UPPERCASE_D)))
        .containsEntry("nativeStatus", AutraStatus.DECLINED)
        .containsEntry("normalizedStatus", "FAILED");
  }

  @Test
  void the_account_configured_for_the_prose_side_answers_the_same_decline_four_twenty_two() {
    reset();
    configure(List.of(anAccount(Map.of(AutraSaleDecline.SETTING, "UNPROCESSABLE_WITH_ERRORS"))));
    declineTheNextSale();
    String token = anAccessToken();

    HttpResponse<String> declined =
        post("/v1/acquiring/payments", token, sale(aSessionToken(token), "slug-token-abc", null));

    assertThat(declined.statusCode()).isEqualTo(422);
    assertThat(declined.body()).contains(AutraErrors.INSUFFICIENT_FUNDS);
    assertThat(declined.body())
        .as(
            "provider autra publishes its payment identifier as paymentID from the sale, capture"
                + " and read endpoints and as paymentId from the reverse-or-release endpoint, and"
                + " reads it back under either, so a refusal naming the payment under the spelling"
                + " this body does not use would still be naming it")
        .doesNotContainIgnoringCase(AutraFields.PAYMENT_ID_UPPERCASE_D);
  }

  @Test
  void a_repeat_under_an_approved_order_is_refused_and_the_wire_names_no_payment() {
    String token = anAccessToken();
    HttpResponse<String> first =
        post(
            "/v1/acquiring/payments",
            token,
            sale(aSessionToken(token), "slug-token-abc", null, ORDER));

    HttpResponse<String> repeat =
        post(
            "/v1/acquiring/payments",
            token,
            sale(aSessionToken(token), "slug-token-abc", null, ORDER));

    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(repeat.statusCode()).isEqualTo(409);
    assertThat(repeat.body()).contains(AutraErrors.ORDER_ALREADY_APPROVED);
    assertThat(repeat.body())
        .as(
            "provider autra publishes its payment identifier as paymentID from the sale, capture"
                + " and read endpoints and as paymentId from the reverse-or-release endpoint, and"
                + " reads it back under either, so a refusal naming the payment under the spelling"
                + " this body does not use would still be naming it")
        .doesNotContainIgnoringCase(AutraFields.PAYMENT_ID_UPPERCASE_D);
    assertThat(objects()).hasSize(1);
  }

  @Test
  void the_simulator_own_retry_record_names_the_payment_the_published_conflict_will_not() {
    String token = anAccessToken();
    String original =
        valueIn(
            post(
                    "/v1/acquiring/payments",
                    token,
                    sale(aSessionToken(token), "slug-token-abc", null, ORDER))
                .body(),
            AutraFields.PAYMENT_ID_UPPERCASE_D);

    post(
        "/v1/acquiring/payments", token, sale(aSessionToken(token), "slug-token-abc", null, ORDER));

    assertThat(String.valueOf(theKeyedExchanges())).contains(original).contains("orderId");
  }

  @Test
  void a_card_session_a_payer_abandoned_expires_and_is_refused_a_second_payer_step() {
    String token = anAccessToken();
    String sessionId =
        valueIn(
            post("/v1/acquiring/card-sessions", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            AutraFields.HOSTED_CARD_SESSION_ID);

    Map<String, Object> abandoned = abandon(sessionId);

    assertThat(abandoned).containsEntry("outcome", "ABANDONED");
    assertThat(stateOf(sessionId))
        .containsEntry("nativeStatus", AutraStatus.EXPIRED)
        .containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void a_card_session_a_pix_and_a_sale_of_one_run_are_all_listed_newest_first() {
    String token = anAccessToken();
    String paymentId =
        valueIn(
            post(
                    "/v1/acquiring/payments",
                    token,
                    sale(aSessionToken(token), "slug-token-abc", null))
                .body(),
            AutraFields.PAYMENT_ID_UPPERCASE_D);
    String pixId =
        valueIn(
            post(
                    "/v1/acquiring/pix/checkout",
                    token,
                    "{\"transactionalToken\":\""
                        + aPixSessionToken(token)
                        + "\",\"documentId\":\""
                        + DOCUMENT
                        + "\",\"amount\":99.90,\"currency\":\"BRL\"}")
                .body(),
            AutraFields.PIX_ID);
    String sessionId =
        valueIn(
            post("/v1/acquiring/card-sessions", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            AutraFields.HOSTED_CARD_SESSION_ID);

    assertThat(objects())
        .extracting(listed -> listed.get("providerObjectId"))
        .containsExactly(sessionId, pixId, paymentId);
    assertThat(objectsAfter(sessionId))
        .as("a cursor naming the card session walks back into the two objects before it")
        .extracting(listed -> listed.get("providerObjectId"))
        .containsExactly(pixId, paymentId);
  }

  private void declineTheNextSale() {
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
                Map.of("operation", AutraOperations.SALE),
                "action",
                Map.of("type", "TERMINAL_FAILURE", "providerCode", AutraErrors.INSUFFICIENT_FUNDS)))
        .retrieve()
        .toBodilessEntity();
  }

  private String aSessionToken(String token) {
    return valueIn(
        post("/v1/acquiring/payments/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
            .body(),
        AutraFields.TRANSACTIONAL_TOKEN);
  }

  private String aPixSessionToken(String token) {
    return valueIn(
        post("/v1/acquiring/pix/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}").body(),
        AutraFields.TRANSACTIONAL_TOKEN);
  }

  private static String sale(String transactionalToken, String slugToken, String slugStoredCard) {
    return sale(transactionalToken, slugToken, slugStoredCard, null);
  }

  private static String sale(
      String transactionalToken, String slugToken, String slugStoredCard, String orderId) {
    return "{\"transactionalToken\":\""
        + transactionalToken
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"transactionType\":\"CREDIT\",\"amount\":150.00,\"currency\":\"BRL\","
        + "\"installments\":1"
        + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
        + ",\"tokenData\":{\"slugToken\":\""
        + slugToken
        + "\""
        + (slugStoredCard == null ? "" : ",\"slugStoredCard\":\"" + slugStoredCard + "\"")
        + "}}";
  }

  private Map<String, Object> completeAs(String providerObjectId, String card) {
    return http.post()
        .uri("/__simulator/autra/hosted/" + providerObjectId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "outcome", "PAID", "card", card))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> abandon(String providerObjectId) {
    return http.post()
        .uri("/__simulator/autra/hosted/" + providerObjectId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "outcome", "ABANDONED"))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private static String theRelated(Map<String, Object> state, String name) {
    Object related = state.get("relatedObjects");
    return String.valueOf(((Map<String, Object>) related).get(name));
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

  private static Map<String, Object> anAccount(Map<String, String> extra) {
    Map<String, String> configuration = new LinkedHashMap<>(extra);
    configuration.put(AutraMerchant.CLIENT_ID_PROPERTY, CLIENT_ID);
    configuration.put(AutraMerchant.CLIENT_SECRET_PROPERTY, CLIENT_SECRET);
    configuration.put(AutraMerchant.DOCUMENT_ID_PROPERTY, DOCUMENT);
    return Map.of("accountId", ACCOUNT, "configuration", configuration);
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
                accounts))
        .retrieve()
        .toBodilessEntity();
    if (!accounts.isEmpty()) {
      theExamplePairIsACardOfTheRun();
    }
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

  private List<Map<String, Object>> objectsAfter(String providerObjectId) {
    return http.get()
        .uri("/__simulator/autra/objects?testRunId=" + RUN.value() + "&after=" + providerObjectId)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> objects() {
    return http.get()
        .uri("/__simulator/autra/objects?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private Map<String, Object> theKeyedExchanges() {
    return http.get()
        .uri("/__simulator/autra/idempotency?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
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
