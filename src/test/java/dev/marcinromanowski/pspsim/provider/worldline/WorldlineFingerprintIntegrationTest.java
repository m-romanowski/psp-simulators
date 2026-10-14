package dev.marcinromanowski.pspsim.provider.worldline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class WorldlineFingerprintIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("worldline-fingerprint-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String MERCHANT = "pspid-1";

  private static final String API_KEY = "worldline-api-key";

  private static final String API_SECRET = "worldline-api-secret";

  private static final String A_PAN = "4111111111111111";

  private static final String ANOTHER_PAN = "4012888888881881";

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
  void two_payments_differing_only_in_their_card_fingerprint_apart_and_no_surface_shows_a_key()
      throws Exception {
    String token = aStoredToken();
    String withACard = withTheCard(aHoldOf(token), A_PAN, "123");
    String withAnotherCard = withTheCard(aHoldOf(token), ANOTHER_PAN, "456");
    post(thePayments(), withACard, keyed("k-a-card"));
    post(thePayments(), withAnotherCard, keyed("k-another-card"));

    List<String> fingerprints =
        jdbc.sql(
                "select fingerprint from worldline_keyed_exchange where test_run_id = ?"
                    + " order by key_value")
            .param(RUN.value())
            .query(String.class)
            .list();
    String secret =
        jdbc.sql("select fingerprint_key from simulator_test_run where test_run_id = ?")
            .param(RUN.value())
            .query(String.class)
            .single();

    assertThat(fingerprints).hasSize(2).doesNotHaveDuplicates();
    assertThat(secret).matches("[0-9a-f]{64}");
    List<String> anythingThatCouldBeChecked =
        List.of(
            secret,
            theDigestAReaderCouldRecompute(withACard),
            theDigestAReaderCouldRecompute(withAnotherCard),
            A_PAN,
            ANOTHER_PAN);
    for (String shown :
        List.of(
            read("/__simulator/worldline/idempotency?testRunId=" + RUN.value()),
            read("/ui/idempotency?provider=worldline&testRunId=" + RUN.value()),
            read("/__simulator/worldline/run-bundle?testRunId=" + RUN.value()),
            read("/ui/run-bundle?provider=worldline&testRunId=" + RUN.value()),
            read("/__simulator/worldline/repro-bundle?testRunId=" + RUN.value() + "&key=k-a-card"),
            read("/__simulator/worldline/audit?testRunId=" + RUN.value()))) {
      assertThat(shown).isNotBlank();
      for (String secretOrInput : anythingThatCouldBeChecked) {
        assertThat(shown).doesNotContain(secretOrInput);
      }
    }
    assertThat(read("/__simulator/worldline/idempotency?testRunId=" + RUN.value()))
        .contains(fingerprints.get(0))
        .contains(fingerprints.get(1));
  }

  private static Map<String, String> keyed(String key) {
    return Map.of(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, key);
  }

  private static String withTheCard(String hold, String pan, String cvv) {
    return hold.replace(
        "\"cardPaymentMethodSpecificInput\":{",
        "\"cardPaymentMethodSpecificInput\":{\"card\":{\"cardNumber\":\""
            + pan
            + "\",\"cvv\":\""
            + cvv
            + "\",\"expiryDate\":\"1230\"},");
  }

  private static String theDigestAReaderCouldRecompute(String body) throws Exception {
    StringBuilder compared = new StringBuilder("POST\n" + WorldlineOperations.CREATE_PAYMENT);
    for (String parameter :
        new FingerprintedRequest(
                "POST",
                WorldlineOperations.CREATE_PAYMENT,
                WorldlineJsonReader.objectIn(body).orElseThrow().flatten())
            .comparedParameters()) {
      compared.append('\n').append(parameter);
    }
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(compared.toString().getBytes(StandardCharsets.UTF_8)));
  }

  private String read(String path) {
    return http.get()
        .uri(path)
        .exchange(
            (request, response) ->
                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
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
