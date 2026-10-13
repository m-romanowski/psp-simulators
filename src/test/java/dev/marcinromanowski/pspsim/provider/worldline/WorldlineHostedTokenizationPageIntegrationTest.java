package dev.marcinromanowski.pspsim.provider.worldline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class WorldlineHostedTokenizationPageIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("worldline-page-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");

  private static final String ACCOUNT = "page-account";

  private static final String MERCHANT = "pspid-page";

  private static final String API_KEY = "worldline-page-api-key";

  private static final String CARD = WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS;

  @LocalServerPort private int port;

  @Value("${simulator.public-base-url}")
  private String publicBaseUrl;

  @Autowired private WorldlineTokenizations sessions;

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
  void the_url_a_shop_is_handed_is_the_page_this_simulator_serves_under_its_public_base_url() {
    String opened = open().body();
    String id = valueIn(opened, WorldlineFields.HOSTED_TOKENIZATION_ID);

    assertThat(valueIn(opened, WorldlineFields.HOSTED_TOKENIZATION_URL))
        .isEqualTo(publicBaseUrl + "/hostedtokenization/tokenization/form/" + id);
    assertThat(valueIn(opened, WorldlineFields.PARTIAL_REDIRECT_URL))
        .isEqualTo(
            URI.create(publicBaseUrl).getRawAuthority()
                + "//hostedtokenization/tokenization/form/"
                + id);
  }

  @Test
  void a_payer_opens_the_page_with_no_run_header_picks_a_card_and_the_shop_reads_the_token() {
    String id = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);

    HttpResponse<String> page = asThePayer(HttpRequest.newBuilder(thePage(id)).GET());
    HttpResponse<String> stored = submit(id, "outcome=PAID&card=" + CARD);
    HttpResponse<String> read = get(theHostedTokenization(id));

    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.headers().firstValue("Content-Type")).contains("text/html;charset=utf-8");
    assertThat(page.body())
        .contains("<option value=\"" + CARD + "\">")
        .contains("value=\"PAID\"")
        .doesNotContain(API_KEY);
    assertThat(stored.statusCode()).isEqualTo(200);
    assertThat(read.statusCode()).isEqualTo(200);
    String token = String.valueOf(sessions.findById(RUN, id).orElseThrow().token());
    assertThat(read.body())
        .contains("\"id\":\"" + token + "\"")
        .contains("\"tokenStatus\":\"" + WorldlineStatus.TOKEN_CREATED + "\"")
        .contains("************1111");
    assertThat(stored.body()).doesNotContain(token);
  }

  @Test
  void nothing_on_the_page_answer_forbids_a_shop_from_framing_it() {
    String id = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);

    HttpResponse<String> page = asThePayer(HttpRequest.newBuilder(thePage(id)).GET());

    assertThat(page.headers().firstValue("X-Frame-Options")).isEmpty();
    assertThat(page.headers().firstValue("Content-Security-Policy"))
        .contains("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    assertThat(page.headers().allValues("Content-Security-Policy"))
        .noneMatch(policy -> policy.contains("frame-ancestors"));
    assertThat(page.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(page.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(page.headers().firstValue("Referrer-Policy")).contains("no-referrer");
  }

  @Test
  void the_page_and_the_control_route_leave_two_sessions_the_published_read_answers_alike() {
    String byThePage = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);
    String byTheControlRoute = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);

    submit(byThePage, "outcome=PAID&card=" + CARD);
    completeThroughTheControlRoute(byTheControlRoute);

    String pageRead = get(theHostedTokenization(byThePage)).body();
    String controlRead = get(theHostedTokenization(byTheControlRoute)).body();
    assertThat(withoutTheToken(pageRead, byThePage))
        .isEqualTo(withoutTheToken(controlRead, byTheControlRoute));
    assertThat(stateOf(byThePage))
        .containsEntry("nativeStatus", WorldlineStatus.TOKEN_CREATED)
        .containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void
      the_state_of_a_session_names_the_id_both_urls_are_derived_from_and_neither_url_a_screen_would_print() {
    HttpResponse<String> opened = open();
    String id = valueIn(opened.body(), WorldlineFields.HOSTED_TOKENIZATION_ID);
    Map<String, Object> state = stateOf(id);

    assertThat(state.get("relatedObjects"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .as(
            "the control answer, the object screen and the timeline read this one map, and every"
                + " entry of it is printed verbatim, so an address here hands a tester the payer"
                + " link ThePayerStep says is never printed on a screen")
        .doesNotContainKeys(
            WorldlineFields.HOSTED_TOKENIZATION_URL, WorldlineFields.PARTIAL_REDIRECT_URL);
    String named = String.valueOf(state.get("providerObjectId"));
    assertThat(valueIn(opened.body(), WorldlineFields.HOSTED_TOKENIZATION_URL))
        .as(
            "the address the create response answered is the public base url and the page path of"
                + " this very id, so the id the state names is what both addresses are derived"
                + " from and the state withholds nothing they carry")
        .isEqualTo(publicBaseUrl + WorldlineHostedTokenizationPage.pathOf(named));
    assertThat(valueIn(opened.body(), WorldlineFields.PARTIAL_REDIRECT_URL))
        .as("the second address worldline publishes is the same id behind the same path")
        .endsWith(WorldlineHostedTokenizationPage.pathOf(named));
    assertThat(asThePayer(HttpRequest.newBuilder(thePage(named)).GET()).statusCode())
        .as(
            "and the page the id resolves to is served, so a tester who reads only the state can"
                + " still reach the payer page and loses nothing by its two addresses being gone")
        .isEqualTo(200);
  }

  @Test
  void a_declined_outcome_posted_to_the_page_is_refused_with_the_pinned_status_422() {
    String id = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);

    HttpResponse<String> declined = submit(id, "outcome=DECLINED&card=" + CARD);

    assertThat(declined.statusCode()).isEqualTo(422);
  }

  @Test
  void a_finished_session_and_an_unknown_one_are_refused_on_both_verbs() {
    String id = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);
    submit(id, "outcome=PAID&card=" + CARD);
    String unknown = "0123456789abcdef0123456789abcdef";

    assertThat(asThePayer(HttpRequest.newBuilder(thePage(id)).GET()).statusCode()).isEqualTo(410);
    assertThat(submit(id, "outcome=ABANDONED").statusCode()).isEqualTo(410);
    assertThat(asThePayer(HttpRequest.newBuilder(thePage(unknown)).GET()).statusCode())
        .isEqualTo(404);
    assertThat(submit(unknown, "outcome=PAID&card=" + CARD).statusCode()).isEqualTo(404);
  }

  @Test
  void the_submission_is_in_the_trail_of_the_run_that_opened_the_session() {
    String id = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);

    submit(id, "outcome=PAID&card=" + CARD);

    assertThat(auditOf(id))
        .anySatisfy(
            row -> {
              assertThat(row).containsEntry("path", WorldlineHostedTokenizationPage.pathOf(id));
              assertThat(row).containsEntry("direction", "OUTBOUND_RESPONSE");
              assertThat(row).containsEntry("status", 200);
            });
  }

  @Test
  void the_store_answers_a_second_run_claiming_a_held_id_as_taken_instead_of_failing() {
    String id = valueIn(open().body(), WorldlineFields.HOSTED_TOKENIZATION_ID);
    WorldlineTokenization held = sessions.findById(RUN, id).orElseThrow();

    boolean inserted =
        sessions.insertIfAbsent(
            new WorldlineTokenization(
                TestRunId.of("worldline-page-it-other"),
                id,
                held.accountId(),
                held.merchantId(),
                WorldlineStatus.AWAITING_THE_PAYER,
                null,
                null,
                null,
                held.expiresAt(),
                ObjectOrigin.PROVIDER_API,
                held.createdAt(),
                held.updatedAt()));

    assertThat(inserted).isFalse();
    assertThat(sessions.findByIdInAnyRun(id).orElseThrow().testRunId()).isEqualTo(RUN);
    assertThat(sessions.findByIdInAnyRun("f".repeat(32))).isEmpty();
  }

  private static String withoutTheToken(String read, String hostedTokenizationId) {
    return read.replaceAll("\"id\":\"[0-9a-f]{32}\"", "\"id\":\"<token>\"")
        .replace(hostedTokenizationId, "<session>");
  }

  private HttpResponse<String> open() {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + theHostedTokenizations()))
            .header("Content-Type", WorldlineFields.JSON)
            .header(WorldlineFields.AUTHORIZATION, authorization())
            .POST(HttpRequest.BodyPublishers.ofString("{}")));
  }

  private HttpResponse<String> get(String path) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header(WorldlineFields.AUTHORIZATION, authorization())
            .GET());
  }

  private HttpResponse<String> submit(String hostedTokenizationId, String form) {
    return asThePayer(
        HttpRequest.newBuilder(thePage(hostedTokenizationId))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form, UTF_8)));
  }

  private HttpResponse<String> asThePayer(HttpRequest.Builder request) {
    return send(request);
  }

  private URI thePage(String hostedTokenizationId) {
    return URI.create(
        "http://localhost:" + port + WorldlineHostedTokenizationPage.pathOf(hostedTokenizationId));
  }

  private static String authorization() {
    return WorldlineSignature.SCHEME + API_KEY + ":unverified";
  }

  private static String theHostedTokenizations() {
    return WorldlineApiController.theHostedTokenizationsOf(MERCHANT);
  }

  private static String theHostedTokenization(String hostedTokenizationId) {
    return WorldlineApiController.theHostedTokenizationOf(MERCHANT, hostedTokenizationId);
  }

  private void completeThroughTheControlRoute(String hostedTokenizationId) {
    http.post()
        .uri("/__simulator/worldline/hosted/" + hostedTokenizationId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "outcome", "PAID", "card", CARD))
        .retrieve()
        .toBodilessEntity();
  }

  private static Map<String, Object> anAccount() {
    return Map.of(
        "accountId",
        ACCOUNT,
        "configuration",
        Map.of(
            WorldlineMerchant.MERCHANT_ID_PROPERTY,
            MERCHANT,
            WorldlineMerchant.API_KEY_PROPERTY,
            API_KEY,
            WorldlineMerchant.API_SECRET_PROPERTY,
            "worldline-page-api-secret",
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

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/worldline/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> auditOf(String providerObjectId) {
    return http.get()
        .uri(
            "/__simulator/worldline/audit?testRunId="
                + RUN.value()
                + "&providerObjectId="
                + providerObjectId)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String valueIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
    return found.find() ? found.group(1) : "";
  }
}
