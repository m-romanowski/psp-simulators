package dev.marcinromanowski.pspsim.provider.worldline;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class WorldlineConcurrentExchangeIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("worldline-concurrent-exchange-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String MERCHANT = "pspid-1";

  private static final String API_KEY = "worldline-api-key";

  private static final String API_SECRET = "worldline-api-secret";

  private static final String KEY = "k-concurrent";

  private static final int CONCURRENT = 16;

  private static final int ROUNDS = 3;

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(List.of(anAccount()));
  }

  @AfterEach
  void unconfigureTheMerchant() {
    reset();
    configure(List.of());
  }

  @Test
  void payments_sent_at_once_under_one_key_are_numbered_after_the_one_that_claimed_it()
      throws Exception {
    String body = aHoldOf(aStoredToken());
    Map<String, String> key =
        Map.of(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, KEY);

    List<Integer> statuses = new ArrayList<>();
    for (int round = 0; round < ROUNDS; round++) {
      statuses.addAll(AtOnce.sent(CONCURRENT, () -> post(thePayments(), body, key).statusCode()));
    }

    assertThat(statuses).hasSize(CONCURRENT * ROUNDS).containsOnly(201, 409);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheName(
            jdbc,
            "worldline_keyed_exchange",
            WorldlineKeyMechanisms.IDEMPOTENCE_KEY,
            KEY,
            RUN.value()),
        CONCURRENT * ROUNDS,
        Set.of(
            ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY,
            ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "worldline", RUN.value());
  }

  private String aStoredToken() {
    String hostedTokenizationId =
        valueIn(
            post(theHostedTokenizations(), "{}").body(), WorldlineFields.HOSTED_TOKENIZATION_ID);
    completeAsThePayer(hostedTokenizationId);
    return valueIn(get(theHostedTokenization(hostedTokenizationId)).body(), WorldlineFields.ID);
  }

  private void completeAsThePayer(String hostedTokenizationId) {
    http.post()
        .uri("/__simulator/worldline/hosted/" + hostedTokenizationId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "outcome",
                "PAID",
                "card",
                WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS))
        .retrieve()
        .toBodilessEntity();
  }

  private static String aHoldOf(String token) {
    return "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"},"
        + "\"references\":{\"merchantReference\":\"order-123\"}},"
        + "\"cardPaymentMethodSpecificInput\":{\"token\":\""
        + token
        + "\",\"authorizationMode\":\"PRE_AUTHORIZATION\"}}";
  }

  private static String theHostedTokenizations() {
    return WorldlineApiController.theHostedTokenizationsOf(MERCHANT);
  }

  private static String theHostedTokenization(String hostedTokenizationId) {
    return WorldlineApiController.theHostedTokenizationOf(MERCHANT, hostedTokenizationId);
  }

  private static String thePayments() {
    return WorldlineApiController.thePaymentsOf(MERCHANT);
  }

  private static Map<String, Object> anAccount() {
    return Map.of(
        "accountId",
        ACCOUNT,
        "configuration",
        Map.of(
            WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT,
            WorldlineMerchant.API_KEY_PROPERTY, API_KEY,
            WorldlineMerchant.API_SECRET_PROPERTY, API_SECRET,
            WorldlineAuthentication.SETTING,
                WorldlineAuthentication.THE_SIGNATURE_IS_NOT_VERIFIED.name()));
  }

  private void configure(List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/worldline/config")
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
        .uri("/__simulator/worldline/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private HttpResponse<String> post(String path, String body) {
    return post(path, body, Map.of());
  }

  private HttpResponse<String> post(String path, String body, Map<String, String> headers) {
    Map<String, String> sent = new LinkedHashMap<>(headers);
    sent.put("Content-Type", WorldlineFields.JSON);
    sent.put(WorldlineFields.AUTHORIZATION, WorldlineSignature.SCHEME + API_KEY + ":unverified");
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .POST(HttpRequest.BodyPublishers.ofString(body)),
        sent);
  }

  private HttpResponse<String> get(String path) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET(),
        Map.of(WorldlineFields.AUTHORIZATION, WorldlineSignature.SCHEME + API_KEY + ":unverified"));
  }

  private HttpResponse<String> send(HttpRequest.Builder request, Map<String, String> headers) {
    try {
      HttpRequest.Builder built = request;
      for (Map.Entry<String, String> header : headers.entrySet()) {
        built = built.header(header.getKey(), header.getValue());
      }
      return wire.send(built.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
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
