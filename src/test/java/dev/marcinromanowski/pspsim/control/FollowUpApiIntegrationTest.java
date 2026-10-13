package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class FollowUpApiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "follow-up-api";
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "follow-up-client-id";
  private static final String CLIENT_SECRET = "follow-up-client-secret";
  private static final String PAYOUT = "acquiring.payout";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure(true);
  }

  @AfterEach
  void unconfigureTheMerchant() {
    reset();
    configure(false);
    merchant.close();
  }

  @Test
  void settling_a_paid_pix_answers_its_new_state_and_the_payout_it_queued() {
    String pixId = aPaidPix();

    ResponseEntity<Map<String, Object>> settled = followUp(pixId, "settle", RUN);

    assertThat(settled.getStatusCode().value()).isEqualTo(200);
    Map<String, Object> body = settled.getBody();
    assertThat(body)
        .containsEntry("testRunId", RUN)
        .containsEntry("provider", "autra")
        .containsEntry("followUp", "settle");
    Map<?, ?> state = mapIn(body.get("state"));
    assertThat(state.get("providerObjectId")).isEqualTo(pixId);
    assertThat(state.get("nativeStatus")).isEqualTo("SETTLED");
    assertThat(state.get("normalizedStatus")).isEqualTo("SUCCEEDED");
    List<?> jobIds = listIn(body.get("webhookJobIds"));
    assertThat(jobIds).hasSize(1);
    assertThat(deliveries())
        .filteredOn(delivery -> jobIds.contains(delivery.get("webhookJobId")))
        .extracting(delivery -> delivery.get("eventType"))
        .containsExactly(PAYOUT);
  }

  @Test
  void the_state_a_read_answers_and_the_payout_the_merchant_received_say_the_same_thing() {
    String pixId = aPaidPix();

    followUp(pixId, "settle", RUN);

    String nativeStatus = String.valueOf(stateOf(pixId).get("nativeStatus"));
    List<String> payouts =
        merchant.received().stream()
            .map(LocalMerchantServer.ReceivedCallback::body)
            .filter(received -> received.contains("\"eventType\":\"" + PAYOUT + "\""))
            .toList();
    assertThat(nativeStatus).isEqualTo("SETTLED");
    assertThat(payouts).hasSize(1);
    assertThat(payouts.get(0)).contains("\"status\":\"" + nativeStatus + "\"");
    assertThat(payoutStatusQueuedFor(pixId)).containsExactly(nativeStatus);
  }

  @Test
  void a_merchant_that_reads_the_pix_while_it_handles_the_payout_reads_it_settled() {
    String pixId = aPaidPix();
    List<Object> readWhileHandling = new CopyOnWriteArrayList<>();
    merchant.onceWhileHandling(() -> readWhileHandling.add(stateOf(pixId).get("nativeStatus")));

    ResponseEntity<Map<String, Object>> settled = followUp(pixId, "settle", RUN);

    assertThat(settled.getStatusCode().value()).isEqualTo(200);
    assertThat(readWhileHandling).containsExactly("SETTLED");
  }

  @Test
  void a_settled_pix_offers_no_second_settlement_and_is_refused_422_before_autra_is_asked() {
    String pixId = aPaidPix();
    followUp(pixId, "settle", RUN);

    ResponseEntity<Map<String, Object>> again = followUp(pixId, "settle", RUN);

    assertThat(again.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(again.getBody().get("message")))
        .contains("offers no follow-up named settle");
    assertThat(stateOf(pixId)).containsEntry("nativeStatus", "SETTLED");
  }

  @Test
  void a_pix_still_waiting_for_its_payer_is_refused_422_and_does_not_move() {
    String pixId = aQrCode();

    ResponseEntity<Map<String, Object>> refused = followUp(pixId, "settle", RUN);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .contains("offers no follow-up named settle");
    assertThat(stateOf(pixId)).containsEntry("nativeStatus", "PENDING");
  }

  @Test
  void an_object_the_run_does_not_hold_is_answered_404() {
    ResponseEntity<Map<String, Object>> missing = followUp("no-such-pix", "settle", RUN);

    assertThat(missing.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(missing.getBody().get("message")))
        .contains("holds no object of provider autra");
  }

  @Test
  void an_unreadable_test_run_id_in_the_body_is_answered_400_and_moves_nothing() {
    String pixId = aPaidPix();

    ResponseEntity<Map<String, Object>> unreadable = followUp(pixId, "settle", "not a run id");

    assertThat(unreadable.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(unreadable.getBody().get("message"))).contains("testRunId");
    assertThat(stateOf(pixId)).containsEntry("nativeStatus", "AUTHORIZED");
  }

  @Test
  void a_follow_up_named_by_nothing_but_blanks_is_answered_400_and_moves_nothing() {
    String pixId = aPaidPix();

    ResponseEntity<Map<String, Object>> unnamed = followUp(pixId, " ", RUN);

    assertThat(unnamed.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(unnamed.getBody().get("message")))
        .isEqualTo(FollowUpRequest.A_FOLLOW_UP_IS_NAMED);
    assertThat(stateOf(pixId)).containsEntry("nativeStatus", "AUTHORIZED");
  }

  @Test
  void a_performed_settlement_and_a_refused_one_both_leave_a_row_in_the_trail() {
    String pixId = aPaidPix();
    String path = "/__simulator/autra/objects/" + pixId + "/follow-ups/settle";

    followUp(pixId, "settle", RUN);
    followUp(pixId, "settle", RUN);

    List<Map<String, Object>> rows =
        audit().stream().filter(row -> path.equals(row.get("path"))).toList();
    assertThat(rows).extracting(row -> row.get("status")).containsExactlyInAnyOrder(200, 422);
    Map<String, Object> applied =
        rows.stream()
            .filter(row -> Integer.valueOf(200).equals(row.get("status")))
            .findFirst()
            .orElseThrow();
    assertThat(applied)
        .containsEntry("origin", "CONTROL_API")
        .containsEntry("method", "POST")
        .containsEntry("providerObjectId", pixId);
    assertThat(String.valueOf(applied.get("sanitizedBody"))).contains("followUp=[settle]");
    Map<String, Object> refused =
        rows.stream()
            .filter(row -> Integer.valueOf(422).equals(row.get("status")))
            .findFirst()
            .orElseThrow();
    assertThat(String.valueOf(refused.get("refusal"))).contains(pixId);
  }

  @Test
  void a_payout_sent_on_its_own_before_the_settlement_is_refused_and_resent_after_it() {
    String pixId = aPaidPix();

    ResponseEntity<Map<String, Object>> early = sendPayout(pixId);
    followUp(pixId, "settle", RUN);
    ResponseEntity<Map<String, Object>> resent = sendPayout(pixId);

    assertThat(early.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(early.getBody().get("message"))).contains("follow-ups/settle");
    assertThat(resent.getStatusCode().value()).isEqualTo(201);
    assertThat(payoutStatusQueuedFor(pixId)).containsExactly("SETTLED", "SETTLED");
  }

  private String aPaidPix() {
    String pixId = aQrCode();
    http.post()
        .uri("/__simulator/autra/hosted/" + pixId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN, "outcome", "PAID"))
        .retrieve()
        .toBodilessEntity();
    return pixId;
  }

  private String aQrCode() {
    String token = anAccessToken();
    String transactionalToken =
        valueIn(
            post("/v1/acquiring/pix/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            "transactionalToken");
    return valueIn(
        post(
                "/v1/acquiring/pix/checkout",
                token,
                "{\"transactionalToken\":\""
                    + transactionalToken
                    + "\",\"documentId\":\""
                    + DOCUMENT
                    + "\",\"amount\":99.90,\"currency\":\"BRL\",\"description\":\"Pedido\"}")
            .body(),
        "pixId");
  }

  private ResponseEntity<Map<String, Object>> followUp(
      String providerObjectId, String name, String testRunId) {
    return http.post()
        .uri("/__simulator/autra/objects/" + providerObjectId + "/follow-ups/" + name)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", testRunId))
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<Map<String, Object>> sendPayout(String pixId) {
    return http.post()
        .uri("/__simulator/autra/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN, "providerObjectId", pixId, "eventType", PAYOUT))
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<String> payoutStatusQueuedFor(String pixId) {
    return deliveries().stream()
        .filter(delivery -> PAYOUT.equals(delivery.get("eventType")))
        .filter(delivery -> pixId.equals(delivery.get("providerObjectId")))
        .map(delivery -> mapIn(delivery.get("payload")).get("operationData.payload.status"))
        .map(status -> String.valueOf(listIn(status).get(0)))
        .toList();
  }

  private static Map<?, ?> mapIn(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    throw new IllegalStateException("expected a JSON object and read " + value);
  }

  private static List<?> listIn(Object value) {
    if (value instanceof List<?> list) {
      return list;
    }
    throw new IllegalStateException("expected a JSON array and read " + value);
  }

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/autra/state/" + providerObjectId + "?testRunId=" + RUN)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> deliveries() {
    return http.get()
        .uri("/__simulator/autra/webhooks?testRunId=" + RUN)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> audit() {
    return http.get()
        .uri("/__simulator/autra/audit?testRunId=" + RUN + "&origin=CONTROL_API&limit=500")
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private String anAccessToken() {
    HttpResponse<String> minted =
        post(
            "/v1/oauth/token?grant_type=client_credentials",
            null,
            "",
            "Basic "
                + Base64.getEncoder()
                    .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(UTF_8)));
    assertThat(minted.statusCode()).isEqualTo(200);
    return valueIn(minted.body(), "accessToken");
  }

  private void configure(boolean withTheMerchant) {
    List<Map<String, Object>> accounts =
        withTheMerchant
            ? List.of(
                Map.of(
                    "accountId",
                    ACCOUNT,
                    "configuration",
                    Map.of(
                        "clientId",
                        CLIENT_ID,
                        "clientSecret",
                        CLIENT_SECRET,
                        "documentId",
                        DOCUMENT)))
            : List.of();
    http.put()
        .uri("/__simulator/autra/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN,
                "accounts",
                accounts,
                "webhooks",
                withTheMerchant
                    ? List.of(Map.of("accountId", ACCOUNT, "url", merchant.url()))
                    : List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/autra/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN))
        .retrieve()
        .toBodilessEntity();
  }

  private HttpResponse<String> post(String path, String bearer, String body) {
    return post(path, bearer, body, null);
  }

  private HttpResponse<String> post(String path, String bearer, String body, String authorization) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));
      if (bearer != null) {
        request = request.header("Authorization", "Bearer " + bearer);
      }
      if (authorization != null) {
        request = request.header("Authorization", authorization);
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
