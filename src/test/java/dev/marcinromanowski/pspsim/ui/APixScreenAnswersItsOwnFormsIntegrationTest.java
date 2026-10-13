package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class APixScreenAnswersItsOwnFormsIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "pix-screen-answers";

  private static final String ACCOUNT = "merchant-1";

  private static final String DOCUMENT = "25055662000190";

  private static final String CLIENT_ID = "pix-screen-client-id";

  private static final String CLIENT_SECRET = "pix-screen-client-secret";

  private static final String THE_PIX_SCREEN = "/ui/x/autra/pix";

  private static final String START = "/ui/objects/start";

  private static final String COMPLETE = "/ui/objects/complete";

  private static final String FOLLOW_UP = "/ui/objects/follow-up";

  private static final String ACT = "/ui/objects/act";

  private static final String WEBHOOK = "/ui/objects/webhook";

  private static final String THE_WIZARD_ONLY_DRAWS = "Step 2 of 2";

  private static final String THE_PAYMENTS_SCREEN_ONLY_DRAWS = "Payments of this test run";

  private static final String THE_TAB = "Pix";

  private static final int A_HUNDRED = 100;

  private static final Pattern THE_REFUSAL_A_READER_SEES =
      Pattern.compile("(?s)<div class=\"refusal\">(.*?)</div>");

  private static final Pattern THE_RADIOS_A_READER_FINDS_TICKED =
      Pattern.compile("<input[^>]*\\schecked[^>]*>");

  private static final Pattern THE_TAB_A_READER_IS_ON =
      Pattern.compile("(?s)<a\\s[^>]*aria-current=\"page\"[^>]*>(.*?)</a>");

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
  void a_start_this_simulator_cannot_read_is_answered_on_the_screen_that_asked_for_it() {
    HttpResponse<String> refused = post(START, aPixCheckoutOf("", "BRL"));

    assertThat(refused.statusCode()).isEqualTo(302);
    assertThat(where(refused)).contains(THE_PIX_SCREEN);
    String screen = follow(refused);
    assertThat(screen)
        .contains("<div class=\"refusal\">")
        .contains("amount")
        .doesNotContain(THE_WIZARD_ONLY_DRAWS);
    assertThat(theRefusalOn(screen))
        .as(
            "the refusal quotes the way of starting by its label, and the label named a QR code"
                + " button that is on no screen a tester can open")
        .contains("Hand out a Pix payload")
        .doesNotContain("QR code");
    assertThat(theTabThisScreenMarksAsItsOwn(screen)).isEqualTo(THE_TAB);
  }

  @Test
  void a_currency_nobody_filled_in_is_refused_by_name_rather_than_blocked_in_the_browser() {
    HttpResponse<String> refused = post(START, aPixCheckoutOf("99.90", ""));

    assertThat(refused.statusCode()).isEqualTo(302);
    String screen = follow(refused);
    assertThat(theRefusalOn(screen))
        .as(
            "both boxes carried required, so a browser would not submit either empty and the two"
                + " refusals behind them were unreachable by clicking")
        .contains("Hand out a Pix payload cannot start without")
        .contains("currency");
    assertThat(screen).contains("value=\"99.90\"");
  }

  @Test
  void a_refused_start_comes_back_with_everything_the_tester_had_typed_still_in_it() {
    Map<String, String> filledIn = aPixCheckoutOf("abc", "BRL");
    filledIn.put("field.description", "Pedido 7");
    filledIn.put("field.orderId", "pedido-abc-123");

    String screen = follow(post(START, filledIn));

    assertThat(screen)
        .as(
            "the refusal travelled and the form did not, so a tester read why the payment would"
                + " not start over four fields they had to fill in again")
        .contains("value=\"abc\"")
        .contains("value=\"BRL\"")
        .contains("value=\"Pedido 7\"")
        .contains("value=\"pedido-abc-123\"");
  }

  @Test
  void a_payer_step_nobody_confirmed_comes_back_with_the_outcome_the_tester_had_picked() {
    String pixId = aQrCode();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("outcome", "ABANDONED");
    form.put("returnTo", THE_PIX_SCREEN);

    String screen = follow(post(COMPLETE, form));

    assertThat(theRadiosTickedOn(screen))
        .as("the chosen outcome was dropped on the way back, so nothing was ticked at all")
        .singleElement()
        .satisfies(ticked -> assertThat(ticked).contains("value=\"ABANDONED\""));
  }

  @Test
  void a_start_no_screen_asked_to_come_back_to_is_still_drawn_where_the_form_posted_it() {
    Map<String, String> withoutTheWayBack = aPixCheckoutOf("", "BRL");
    withoutTheWayBack.remove("returnTo");

    HttpResponse<String> refused = post(START, withoutTheWayBack);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(refused.body()).contains(THE_WIZARD_ONLY_DRAWS);
  }

  @Test
  void a_started_pix_says_so_on_the_screen_it_was_started_from() {
    HttpResponse<String> started = post(START, aPixCheckoutOf("99.90", "BRL"));

    assertThat(started.statusCode()).isEqualTo(302);
    assertThat(where(started)).contains(THE_PIX_SCREEN);
    assertThat(follow(started))
        .contains("<div class=\"done\" role=\"status\">")
        .contains("This payment exists now");
  }

  @Test
  void a_payer_step_nobody_confirmed_is_answered_on_the_pix_screen_and_not_on_the_payments_one() {
    String pixId = aQrCode();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("outcome", "PAID");
    form.put("returnTo", THE_PIX_SCREEN);

    HttpResponse<String> refused = post(COMPLETE, form);

    assertThat(refused.statusCode()).isEqualTo(302);
    assertThat(where(refused)).contains(THE_PIX_SCREEN);
    String screen = follow(refused);
    assertThat(screen).contains("tick confirm").doesNotContain(THE_PAYMENTS_SCREEN_ONLY_DRAWS);
    assertThat(theTabThisScreenMarksAsItsOwn(screen)).isEqualTo(THE_TAB);
    assertThat(stateOf(pixId).get("nativeStatus")).isEqualTo("PENDING");
  }

  @Test
  void a_follow_up_nobody_confirmed_is_answered_on_the_pix_screen_and_not_on_the_payments_one() {
    String pixId = aPaidPix();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("followUp", "settle");
    form.put("returnTo", THE_PIX_SCREEN);

    HttpResponse<String> refused = post(FOLLOW_UP, form);

    assertThat(refused.statusCode()).isEqualTo(302);
    assertThat(where(refused)).contains(THE_PIX_SCREEN);
    String screen = follow(refused);
    assertThat(screen)
        .contains("tick confirm before a follow-up")
        .doesNotContain(THE_PAYMENTS_SCREEN_ONLY_DRAWS);
    assertThat(theTabThisScreenMarksAsItsOwn(screen)).isEqualTo(THE_TAB);
    assertThat(stateOf(pixId).get("nativeStatus")).isEqualTo("AUTHORIZED");
  }

  @Test
  void an_action_nobody_confirmed_is_answered_on_the_pix_screen_and_not_on_the_payments_one() {
    String pixId = aPaidPix();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("action", "REFUND");
    form.put("amount", "12.34");
    form.put("returnTo", THE_PIX_SCREEN);

    HttpResponse<String> refused = post(ACT, form);

    assertThat(refused.statusCode()).isEqualTo(302);
    assertThat(where(refused)).contains(THE_PIX_SCREEN);
    String screen = follow(refused);
    assertThat(screen)
        .contains("tick confirm before moving money")
        .doesNotContain(THE_PAYMENTS_SCREEN_ONLY_DRAWS);
    assertThat(theTabThisScreenMarksAsItsOwn(screen)).isEqualTo(THE_TAB);
    assertThat(stateOf(pixId).get("nativeStatus")).isEqualTo("AUTHORIZED");
  }

  @Test
  void a_callback_nobody_confirmed_is_answered_on_the_pix_screen_and_not_on_the_payments_one() {
    String pixId = aPaidPix();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("eventType", "charge.settled");
    form.put("returnTo", THE_PIX_SCREEN);

    HttpResponse<String> refused = post(WEBHOOK, form);

    assertThat(refused.statusCode()).isEqualTo(302);
    assertThat(where(refused)).contains(THE_PIX_SCREEN);
    String screen = follow(refused);
    assertThat(screen)
        .contains("tick confirm before sending a callback")
        .doesNotContain(THE_PAYMENTS_SCREEN_ONLY_DRAWS);
    assertThat(theTabThisScreenMarksAsItsOwn(screen)).isEqualTo(THE_TAB);
  }

  @Test
  void an_action_no_screen_asked_to_come_back_to_is_still_drawn_where_the_form_posted_it() {
    String pixId = aPaidPix();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("action", "REFUND");

    HttpResponse<String> refused = post(ACT, form);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(refused.body()).contains(THE_PAYMENTS_SCREEN_ONLY_DRAWS);
    assertThat(theRefusalOn(refused.body())).contains("tick confirm before moving money");
  }

  @Test
  void the_pix_screen_names_every_field_it_reads_back_and_says_when_the_payload_stops_working() {
    aQrCode();

    String screen = thePixScreen();

    assertThat(screen)
        .contains("when the payload stops working")
        .contains("what the payer sees in their bank app")
        .containsPattern("in \\d+ (second|minute)");
    assertThat(screen)
        .as("provider-native and normalized terms stand side by side on every screen")
        .contains("PENDING")
        .contains("PROCESSING");
  }

  @Test
  void a_pix_that_takes_no_payer_any_more_is_not_said_to_stop_working_later_on() {
    aPaidPix();

    String screen = thePixScreen();

    assertThat(screen)
        .as(
            "a Pix autra no longer takes a payer on is a payload that has stopped working, and"
                + " the row counted down to its expiresAt as though it still had one")
        .doesNotContain("when the payload stops working")
        .contains("the payload stopped working whether or not");
  }

  @Test
  void a_run_the_screen_could_not_read_to_the_end_of_says_so_over_the_pix_it_did_reach() {
    String lastHandedOut = "";
    for (int handed = 0; handed <= A_HUNDRED; handed++) {
      lastHandedOut = aQrCode();
    }

    String screen = thePixScreen();

    assertThat(screen)
        .as(
            "the confession was drawn only where the read found no Pix at all, so a short read"
                + " that did reach one printed a heading about the run over part of it")
        .contains("This screen read " + A_HUNDRED + " object(s)")
        .contains("did not reach the end of the run")
        .doesNotContain("The Pix of this test run")
        .contains(lastHandedOut);
  }

  @Test
  void a_payer_step_refused_where_no_screen_asked_to_come_back_to_keeps_the_outcome_ticked() {
    String pixId = aQrCode();
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("providerObjectId", pixId);
    form.put("outcome", "ABANDONED");

    HttpResponse<String> refused = post(COMPLETE, form);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(theRefusalOn(refused.body())).contains("tick confirm before taking this payer step");
    assertThat(theRadiosTickedOn(refused.body()))
        .as("a refusal drawn in place dropped the outcome the tester had already picked")
        .singleElement()
        .satisfies(ticked -> assertThat(ticked).contains("value=\"ABANDONED\""));
  }

  @Test
  void the_payments_screen_of_a_pix_promises_no_card_where_the_form_above_offers_none() {
    String pixId = aQrCode();

    String screen = thePaymentsScreenOf(pixId);

    assertThat(screen)
        .as(
            "a Pix collects no card, so a paragraph promising the cards offered contradicts both"
                + " autra's own sentence beside it and the form above it")
        .contains("a Pix collects no card, so none is named here and nothing is charged")
        .doesNotContain("The cards offered are the ones this provider publishes")
        .doesNotContain("which cards are offered")
        .contains("What this payer step runs");
  }

  @Test
  void the_pix_screen_offers_no_provider_to_choose_and_says_where_the_choice_is() {
    String screen = thePixScreen();

    assertThat(screen).doesNotContain("id=\"provider\"");
    assertThat(screen).contains("choose another provider");
  }

  private static String theRefusalOn(String markup) {
    Matcher drawn = THE_REFUSAL_A_READER_SEES.matcher(markup);
    return drawn.find() ? drawn.group(1).strip() : "";
  }

  private static List<String> theRadiosTickedOn(String markup) {
    List<String> ticked = new ArrayList<>();
    Matcher found = THE_RADIOS_A_READER_FINDS_TICKED.matcher(markup);
    while (found.find()) {
      ticked.add(found.group());
    }
    return List.copyOf(ticked);
  }

  private static String theTabThisScreenMarksAsItsOwn(String markup) {
    Matcher current = THE_TAB_A_READER_IS_ON.matcher(markup);
    return current.find() ? current.group(1).strip() : "";
  }

  private String thePaymentsScreenOf(String providerObjectId) {
    return send(HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/ui/objects?provider=autra&testRunId="
                        + RUN
                        + "&providerObjectId="
                        + providerObjectId))
            .GET()
            .build())
        .body();
  }

  private String thePixScreen() {
    return send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + THE_PIX_SCREEN + "?testRunId=" + RUN))
            .GET()
            .build())
        .body();
  }

  private Map<String, String> aPixCheckoutOf(String amount, String currency) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "autra");
    form.put("testRunId", RUN);
    form.put("accountId", ACCOUNT);
    form.put("startedAs", "POST /v1/acquiring/pix/checkout");
    form.put("returnTo", THE_PIX_SCREEN);
    form.put("field.amount", amount);
    form.put("field.currency", currency);
    return form;
  }

  private static String where(HttpResponse<String> answered) {
    return answered.headers().firstValue("location").orElseThrow();
  }

  private String follow(HttpResponse<String> answered) {
    return send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port).resolve(where(answered)))
            .GET()
            .build())
        .body();
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
    } catch (IOException unreachable) {
      throw new UncheckedIOException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String valueIn(String json, String field) {
    String marked = "\"" + field + "\":\"";
    int at = json.indexOf(marked);
    assertThat(at).as(field + " in " + json).isNotNegative();
    int from = at + marked.length();
    return json.substring(from, json.indexOf('"', from));
  }
}
