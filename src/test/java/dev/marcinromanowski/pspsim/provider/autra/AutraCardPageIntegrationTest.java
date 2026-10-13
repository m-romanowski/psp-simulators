package dev.marcinromanowski.pspsim.provider.autra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class AutraCardPageIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("autra-card-page-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "autra-card-page-client-id";
  private static final String CLIENT_SECRET = "autra-card-page-client-secret";
  private static final Pattern A_RESULT_FIELD =
      Pattern.compile("&quot;([A-Za-z0-9]+)&quot;:&quot;([^&]*)&quot;");

  @LocalServerPort private int port;

  @Value("${simulator.public-base-url}")
  private String publicBaseUrl;

  private RestClient http;
  private final HttpClient payer =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

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
  void a_payer_saves_a_card_on_the_served_page_and_the_merchant_charges_the_slug_it_handed_back() {
    String token = anAccessToken();
    HttpResponse<String> opened =
        post("/v1/acquiring/card-sessions", token, "{\"documentId\":\"" + DOCUMENT + "\"}");
    String sessionId = valueIn(opened.body(), AutraFields.HOSTED_CARD_SESSION_ID);
    URI hostedCardUrl = URI.create(valueIn(opened.body(), AutraFields.HOSTED_CARD_URL));

    HttpResponse<String> page = send(HttpRequest.newBuilder(onThisServer(hostedCardUrl)).GET());
    HttpResponse<String> saved =
        send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + hostedCardUrl.getRawPath()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        hostedCardUrl.getQuery()
                            + "&outcome=PAID&card="
                            + URLEncoder.encode(
                                AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS, UTF_8))));
    Map<String, String> result = theResultIn(saved.body());
    HttpResponse<String> charged =
        post(
            "/v1/acquiring/payments",
            token,
            sale(aSessionToken(token), result.get(AutraFields.SLUG_TOKEN)));
    HttpResponse<String> forged =
        post(
            "/v1/acquiring/payments",
            token,
            sale(aSessionToken(token), result.get(AutraFields.SLUG_TOKEN) + "0"));
    String pageToken = hostedCardUrl.getRawQuery().substring(AutraCardPage.TOKEN.length() + 1);

    assertThat(hostedCardUrl.toString())
        .startsWith(publicBaseUrl + AutraCardPage.pathOf(sessionId) + "?");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
    assertThat(page.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(page.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(page.headers().firstValue("Referrer-Policy")).contains("no-referrer");
    assertThat(page.headers().firstValue("Content-Security-Policy"))
        .contains("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    assertThat(page.body())
        .contains(AutraPayerCards.THE_ONE_CARD_AUTRA_PRINTS)
        .doesNotContain(CLIENT_SECRET);
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(saved.headers().firstValue("Location")).isEmpty();
    assertThat(stateOf(sessionId)).containsEntry("nativeStatus", AutraCardSession.TOKENIZED);
    assertThat(result).containsEntry(AutraFields.LAST4, "1111");
    assertThat(charged.statusCode()).isEqualTo(200);
    assertThat(charged.body()).contains("\"status\":\"" + AutraStatus.ACCEPTED + "\"");
    assertThat(forged.statusCode()).isEqualTo(422);
    assertThat(forged.body()).contains(AutraErrors.TOKEN_NOT_AVAILABLE);
    assertThat(String.valueOf(stateOf(sessionId))).doesNotContain(pageToken);
    assertThat(theObjectScreenOf(sessionId)).contains(sessionId).doesNotContain(pageToken);
  }

  private String theObjectScreenOf(String sessionId) {
    return String.valueOf(
        http.get()
            .uri(
                "/ui/objects?provider=autra&testRunId="
                    + RUN.value()
                    + "&providerObjectId="
                    + sessionId)
            .retrieve()
            .body(String.class));
  }

  @Test
  void a_link_whose_token_no_session_was_minted_with_is_not_found_and_changes_nothing() {
    String token = anAccessToken();
    HttpResponse<String> opened =
        post("/v1/acquiring/card-sessions", token, "{\"documentId\":\"" + DOCUMENT + "\"}");
    String sessionId = valueIn(opened.body(), AutraFields.HOSTED_CARD_SESSION_ID);
    URI thePage = URI.create("http://localhost:" + port + AutraCardPage.pathOf(sessionId));
    String forged = "token=" + "0".repeat(24);

    HttpResponse<String> page =
        send(HttpRequest.newBuilder(URI.create(thePage + "?" + forged)).GET());
    HttpResponse<String> saved =
        send(
            HttpRequest.newBuilder(thePage)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        forged + "&outcome=PAID&card=published-4111")));

    assertThat(page.statusCode()).isEqualTo(404);
    assertThat(saved.statusCode()).isEqualTo(404);
    assertThat(stateOf(sessionId)).containsEntry("nativeStatus", AutraStatus.PENDING);
  }

  private URI onThisServer(URI published) {
    return URI.create(
        "http://localhost:" + port + published.getRawPath() + "?" + published.getRawQuery());
  }

  private static Map<String, String> theResultIn(String page) {
    Map<String, String> fields = new LinkedHashMap<>();
    Matcher field = A_RESULT_FIELD.matcher(page);
    while (field.find()) {
      fields.put(field.group(1), field.group(2));
    }
    return fields;
  }

  private String aSessionToken(String token) {
    return valueIn(
        post("/v1/acquiring/payments/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
            .body(),
        AutraFields.TRANSACTIONAL_TOKEN);
  }

  private static String sale(String transactionalToken, String slugToken) {
    return "{\"transactionalToken\":\""
        + transactionalToken
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"transactionType\":\"CREDIT\",\"amount\":150.00,\"currency\":\"BRL\","
        + "\"installments\":1,\"tokenData\":{\"slugToken\":\""
        + slugToken
        + "\"}}";
  }

  private String anAccessToken() {
    HttpResponse<String> minted =
        send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + "/v1/oauth/token?grant_type="
                            + AutraFields.CLIENT_CREDENTIALS))
                .header(
                    AutraFields.AUTHORIZATION,
                    AutraFields.BASIC
                        + Base64.getEncoder()
                            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(UTF_8)))
                .POST(HttpRequest.BodyPublishers.noBody()));
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
                accounts))
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

  private HttpResponse<String> post(String path, String bearer, String body) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", AutraFields.JSON)
            .header(AutraFields.AUTHORIZATION, AutraFields.BEARER + bearer)
            .POST(HttpRequest.BodyPublishers.ofString(body)));
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return payer.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (IOException unreachable) {
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
