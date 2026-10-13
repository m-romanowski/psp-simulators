package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
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
import org.springframework.web.util.HtmlUtils;

class FollowUpUiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "follow-up-ui";
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "follow-up-ui-client-id";
  private static final String CLIENT_SECRET = "follow-up-ui-client-secret";
  private static final String FOLLOW_UP = "/ui/objects/follow-up";
  private static final String THE_NOTICE =
      "<div class=\"done\" role=\"status\">"
          + HtmlUtils.htmlEscape(Done.THE_FOLLOW_UP_IS_PERFORMED)
          + "</div>";
  private static final String NOTHING_APPLIES =
      "Nothing this provider offers applies to this payment";
  private static final String NO_STEP_IS_LEFT_EITHER = "payer step is left on it either";
  private static final String ITS_PAYER_HAS_NOT_FINISHED = "its payer has not finished with it";
  private static final String THE_SETTLE_BUTTON =
      "Ask <span>autra</span> to <span>settle (payout)</span>";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire =
      HttpClient.newBuilder()
          .cookieHandler(new CookieManager())
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(true);
  }

  @AfterEach
  void unconfigureTheMerchant() {
    reset();
    configure(false);
  }

  @Test
  void the_payment_screen_of_a_paid_pix_offers_the_settle_button_autra_names() {
    String pixId = aPaidPix();

    String screen = theScreenOf(pixId);

    assertThat(screen).contains(FOLLOW_UP).contains(THE_SETTLE_BUTTON);
    assertThat(screen).contains("moves this Pix from AUTHORIZED to SETTLED");
    assertThat(screen).doesNotContain(NOTHING_APPLIES);
  }

  @Test
  void the_payment_screen_of_a_pix_still_waiting_for_its_payer_offers_no_follow_up() {
    String pixId = aQrCode();

    String screen = theScreenOf(pixId);

    assertThat(screen).doesNotContain(FOLLOW_UP).doesNotContain(THE_SETTLE_BUTTON);
    assertThat(screen).contains(ITS_PAYER_HAS_NOT_FINISHED);
    assertThat(screen).doesNotContain(NO_STEP_IS_LEFT_EITHER);
    assertThat(screen).containsPattern("a step still moves it:\\s*<span>yes</span");
  }

  @Test
  void pressing_settle_settles_the_pix_and_the_screen_it_returns_to_offers_it_no_more() {
    String pixId = aPaidPix();

    HttpResponse<String> pressed = post(FOLLOW_UP, aSettlementOf(pixId, true));

    assertThat(pressed.statusCode()).isEqualTo(302);
    assertThat(stateOf(pixId).get("nativeStatus")).isEqualTo("SETTLED");
    String after = theScreenOf(pixId);
    assertThat(after)
        .contains("SETTLED")
        .doesNotContain(THE_SETTLE_BUTTON)
        .contains(NOTHING_APPLIES)
        .contains(NO_STEP_IS_LEFT_EITHER)
        .doesNotContain(ITS_PAYER_HAS_NOT_FINISHED);
    assertThat(theTrailOf("UI")).contains(FOLLOW_UP).contains(pixId);
  }

  @Test
  void pressing_settle_lands_on_a_page_that_says_so_once_and_not_again_on_reload() {
    String pixId = aPaidPix();

    HttpResponse<String> pressed = post(FOLLOW_UP, aSettlementOf(pixId, true));
    String location = pressed.headers().firstValue("location").orElseThrow();

    assertThat(pressed.statusCode()).isEqualTo(302);
    assertThat(page(location)).contains(THE_NOTICE);
    assertThat(page(location)).doesNotContain(THE_NOTICE);
  }

  private String page(String location) {
    return send(HttpRequest.newBuilder(URI.create("http://localhost:" + port).resolve(location))
            .GET()
            .build())
        .body();
  }

  @Test
  void a_settlement_nobody_confirmed_moves_nothing_and_says_what_confirming_would_do() {
    String pixId = aPaidPix();

    HttpResponse<String> refused = post(FOLLOW_UP, aSettlementOf(pixId, false));

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(refused.body()).contains("tick confirm before a follow-up");
    assertThat(stateOf(pixId).get("nativeStatus")).isEqualTo("AUTHORIZED");
  }

  @Test
  void a_second_settlement_from_a_stale_page_is_refused_as_no_longer_offered() {
    String pixId = aPaidPix();
    post(FOLLOW_UP, aSettlementOf(pixId, true));

    HttpResponse<String> again = post(FOLLOW_UP, aSettlementOf(pixId, true));

    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(again.body()).contains("offers no follow-up named settle");
  }

  private Map<String, String> aSettlementOf(String pixId, boolean confirmed) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("followUp", "settle");
    if (confirmed) {
      form.put("confirm", "yes");
    }
    return form;
  }

  private String theScreenOf(String pixId) {
    return http.get()
        .uri("/ui/objects?provider=autra&testRunId={run}&providerObjectId={id}", RUN, pixId)
        .retrieve()
        .body(String.class);
  }

  private String theTrailOf(String origin) {
    return http.get()
        .uri("/__simulator/autra/audit?testRunId={run}&origin={origin}", RUN, origin)
        .retrieve()
        .body(String.class);
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
            json("/v1/acquiring/pix/initialize", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            "transactionalToken");
    return valueIn(
        json(
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

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/autra/state/" + providerObjectId + "?testRunId=" + RUN)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private String anAccessToken() {
    HttpResponse<String> minted =
        send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + "/v1/oauth/token?grant_type=client_credentials"))
                .header(
                    "Authorization",
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(UTF_8)))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());
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
        .body(Map.of("testRunId", RUN, "accounts", accounts))
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

  private HttpResponse<String> post(String path, Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)))
            .build());
  }

  private HttpResponse<String> json(String path, String bearer, String body) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + bearer)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
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
