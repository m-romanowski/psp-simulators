package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionControl;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

class HostedCompletionUiIntegrationTest extends PostgresBackedTest {

  private static final String START = "/ui/objects/start";

  private static final String COMPLETE = "/ui/objects/complete";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";

  private static final String ACCOUNT = "merchant-1";

  private static final String TERMINAL = "77777777";

  private static final String CALLBACK = "https://merchant.example/callbacks";

  private static final String A_PUBLISHED_CARD = "sandbox-4580";

  private static final Pattern A_GROW_PROCESS_ID = Pattern.compile("started=([0-9]{6})");

  private static final Pattern AN_AZERICARD_INT_REF = Pattern.compile("started=([0-9A-F]{16})");

  private static final String A_PAYER_IS_STILL_AWAITED =
      "What a payer has still to finish on this one";

  private static final String THE_PAGE_THE_PAYER_WAS_SENT_TO =
      "The page this payment's payer was sent to";

  private static final Pattern A_RELATED_TRANSACTION =
      Pattern.compile("<dt>transactionId</dt>\\s*<dd[^>]*>\\s*<(?:a|span)[^>]*>([0-9]+)<");

  @LocalServerPort private int port;

  private HttpClient wire;
  private RestClient http;
  private final Set<String> runsThisTestTouched = new LinkedHashSet<>();

  @BeforeEach
  void connect() {
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    http = RestClient.create("http://localhost:" + port);
  }

  @AfterEach
  void clearWhatThisTestStarted() {
    for (String run : runsThisTestTouched) {
      discard("grow", run);
      discard("azericard", run);
    }
  }

  @Test
  void a_tester_finishes_a_grow_page_from_the_screen_and_the_payment_it_created_is_listed() {
    String run = aRunOfItsOwn("grow-finishing");
    String processId = aGrowProcessStartedFromTheWizard(run);

    HttpResponse<byte[]> finished = post(COMPLETE, aPayerWhoPaid(run, processId, A_PUBLISHED_CARD));

    assertThat(finished.statusCode()).isEqualTo(302);
    String detail = follow(finished);
    String transactionId = theIdIn(A_RELATED_TRANSACTION, detail);
    assertThat(detail).contains(processId);
    assertThat(object("grow", run, transactionId)).contains("READY_FOR_CAPTURE");
    assertThat(theObjectsThisOneIsTiedToOn(detail))
        .as(
            "the panel that names the object a tester now needs opens it, rather than printing an"
                + " identifier they have to find again further down the screen")
        .containsPattern(
            "<dt>transactionId</dt>\\s*<dd[^>]*>\\s*<a[^>]*providerObjectId="
                + transactionId
                + "[^>]*>\\s*"
                + transactionId
                + "</a>")
        .contains("READY_FOR_CAPTURE");
  }

  private static String theObjectsThisOneIsTiedToOn(String screen) {
    int opened = screen.indexOf("Objects this one is tied to");
    assertThat(opened).as("a payment's own screen names the objects it is tied to").isNotNegative();
    return screen.substring(opened, screen.indexOf("</section>", opened));
  }

  @Test
  void the_banner_says_the_step_the_tester_picked_and_never_the_one_they_did_not() {
    String run = aRunOfItsOwn("grow-abandoning");
    String processId = aGrowProcessStartedFromTheWizard(run);

    String detail = follow(post(COMPLETE, aPayerWho(run, processId, "ABANDONED", "")));

    assertThat(detail)
        .contains("This payer step is taken: the payer left the page without paying.")
        .doesNotContain("This payer step is taken: the payer paid on the page.");
  }

  @Test
  void a_tester_who_says_the_payer_met_a_decline_is_told_that_step_and_no_other() {
    String run = aRunOfItsOwn("grow-declining");
    String processId = aGrowProcessStartedFromTheWizard(run);

    String detail = follow(post(COMPLETE, aPayerWho(run, processId, "DECLINED", "")));

    assertThat(detail)
        .contains("This payer step is taken: the payer met a decline on the page.")
        .doesNotContain("This payer step is taken: the payer paid on the page.");
  }

  @Test
  void the_screen_offers_the_payer_step_on_a_page_nobody_has_finished_yet() {
    String run = aRunOfItsOwn("grow-offering");
    String processId = aGrowProcessStartedFromTheWizard(run);

    String detail = object("grow", run, processId);

    assertThat(detail)
        .contains("Take the payer's own step on this payment")
        .contains("/ui/objects/complete")
        .contains("Visa •••• 4580, 0424 (sandbox-4580)")
        .contains("the payer paid on the page");
    assertThat(detail)
        .as("a page whose payer still has to finish it is headed as one, and the step is below")
        .contains(A_PAYER_IS_STILL_AWAITED)
        .doesNotContain(THE_PAGE_THE_PAYER_WAS_SENT_TO);
  }

  @Test
  void the_screen_shows_no_card_number_of_the_cards_it_offers_a_payer() {
    String run = aRunOfItsOwn("grow-no-pan");
    String processId = aGrowProcessStartedFromTheWizard(run);

    assertThat(object("grow", run, processId)).doesNotContain("4580458045804580");
  }

  @Test
  void a_grow_page_a_tester_finished_is_in_the_trail_as_theirs_and_not_as_a_clients_call() {
    String run = aRunOfItsOwn("grow-completion-trail");
    String processId = aGrowProcessStartedFromTheWizard(run);

    HttpResponse<byte[]> answered = post(COMPLETE, aPayerWhoPaid(run, processId, A_PUBLISHED_CARD));

    assertThat(answered.statusCode())
        .as(
            "the status this screen files a completed page under on the trail is the status it"
                + " answers a tester with, and nothing else read that number, so a row could"
                + " have carried one no browser ever saw")
        .isEqualTo(HostedCompletionForm.THE_STATUS_THIS_SCREEN_ANSWERS_A_COMPLETED_PAGE_WITH);
    assertThat(theTrailOf("grow", run, "UI")).contains(COMPLETE).contains(processId);
    assertThat(theTrailOf("grow", run, "CONTROL_API")).doesNotContain(COMPLETE);
  }

  @Test
  void a_completion_nobody_confirmed_changes_nothing_and_says_what_confirming_would_do() {
    String run = aRunOfItsOwn("grow-unconfirmed");
    String processId = aGrowProcessStartedFromTheWizard(run);
    Map<String, String> unconfirmed = aPayerWhoPaid(run, processId, A_PUBLISHED_CARD);
    unconfirmed.remove("confirm");

    HttpResponse<byte[]> refused = post(COMPLETE, unconfirmed);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refused))
        .contains("tick confirm before taking this payer step")
        .contains("charges the card it names")
        .contains("none of it can be undone by leaving this screen");
    assertThat(object("grow", run, processId))
        .contains("Take the payer's own step on this payment");
  }

  @Test
  void a_card_this_run_does_not_offer_is_refused_naming_the_cards_a_payer_may_use_here() {
    String run = aRunOfItsOwn("grow-stray-card");
    String processId = aGrowProcessStartedFromTheWizard(run);

    HttpResponse<byte[]> refused = post(COMPLETE, aPayerWhoPaid(run, processId, "no-such-card"));

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refused))
        .contains("no-such-card")
        .contains("Visa •••• 4580, 0424 (sandbox-4580)");
  }

  @Test
  void a_page_a_payer_has_already_finished_offers_no_second_payer_step_and_says_why() {
    String run = aRunOfItsOwn("grow-finished-twice");
    String processId = aGrowProcessStartedFromTheWizard(run);
    assertThat(post(COMPLETE, aPayerWhoPaid(run, processId, A_PUBLISHED_CARD)).statusCode())
        .isEqualTo(302);

    String detail = object("grow", run, processId);

    assertThat(detail).contains("has already reached an outcome");
    assertThat(detail).doesNotContain("Take this payer step</button>");
    assertThat(detail)
        .as(
            "the panel of a page whose payer is finished with it must not be headed as one a"
                + " payer still has something to finish on")
        .contains(THE_PAGE_THE_PAYER_WAS_SENT_TO)
        .doesNotContain(A_PAYER_IS_STILL_AWAITED);
  }

  @Test
  void a_transaction_is_offered_no_payer_step_because_grow_leaves_one_on_a_process_only() {
    String run = aRunOfItsOwn("grow-transaction");
    String processId = aGrowProcessStartedFromTheWizard(run);
    HttpResponse<byte[]> finished = post(COMPLETE, aPayerWhoPaid(run, processId, A_PUBLISHED_CARD));
    String transactionId = theIdIn(A_RELATED_TRANSACTION, follow(finished));

    String detail = object("grow", run, transactionId);

    assertThat(detail).contains("leaves a payer step on a payment process and on nothing else");
    assertThat(detail).doesNotContain("Take this payer step</button>");
  }

  @Test
  void an_azericard_object_is_told_in_azericards_own_words_what_it_has_in_place_of_a_page() {
    String run = aRunOfItsOwn("azericard-no-page");
    configureAnAzericardTerminal(run);
    HttpResponse<byte[]> started = post(START, anAzericardHold(run, "20260920100011"));
    String intRef = theIdIn(AN_AZERICARD_INT_REF, locationOf(started));

    String detail = object("azericard", run, intRef);

    assertThat(detail)
        .contains("Take the payer's own step on this payment")
        .contains("leaves a payer step on a payment page and on nothing else")
        .contains("an INT_REF names the authorization a page or a server-to-server request");
    assertThat(detail).doesNotContain("Take this payer step</button>");
  }

  private String aGrowProcessStartedFromTheWizard(String run) {
    configureAGrowAccount(run);
    HttpResponse<byte[]> started = post(START, aGrowHold(run));
    assertThat(started.statusCode()).isEqualTo(302);
    return theIdIn(A_GROW_PROCESS_ID, locationOf(started));
  }

  @Test
  void a_payer_step_that_named_no_outcome_is_refused_by_name_rather_than_blocked_in_the_browser() {
    String run = aRunOfItsOwn("grow-no-outcome-picked");
    String processId = aGrowProcessStartedFromTheWizard(run);
    Map<String, String> form = aPayerWho(run, processId, "", A_PUBLISHED_CARD);

    HttpResponse<byte[]> refused = post(COMPLETE, form);

    assertThat(refused.statusCode())
        .as("a form posted with no screen to go back to is answered where it was posted")
        .isEqualTo(200);
    assertThat(bodyOf(refused))
        .as(
            "the radio group carried required, so a browser would not submit this and the"
                + " refusal of a completion that names no payer step was unreachable by clicking")
        .contains(HtmlUtils.htmlEscape(HostedCompletionControl.AN_OUTCOME_IS_REQUIRED));
  }

  private static Map<String, String> aPayerWhoPaid(String run, String processId, String card) {
    return aPayerWho(run, processId, "PAID", card);
  }

  private static Map<String, String> aPayerWho(
      String run, String processId, String outcome, String card) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", run);
    form.put("providerObjectId", processId);
    form.put("outcome", outcome);
    form.put("card", card);
    form.put("confirm", "yes");
    return form;
  }

  private String aRunOfItsOwn(String named) {
    String run = "hosted-completion-ui-" + named;
    runsThisTestTouched.add(run);
    return run;
  }

  private void configureAGrowAccount(String run) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", run);
    form.put("accountId", ACCOUNT);
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "userId", "a-grow-user-id");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "pageCode", "a-grow-page-code");
    form.put("callbackUrl", CALLBACK);
    assertThat(post(ACCOUNT_FORM, form).statusCode()).isEqualTo(302);
  }

  private void configureAnAzericardTerminal(String run) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "azericard");
    form.put("testRunId", run);
    form.put("accountId", ACCOUNT);
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "terminalId", TERMINAL);
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "apiKey", publicKeyPem());
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "apiSecret", privateKeyPem());
    form.put("callbackUrl", CALLBACK);
    assertThat(post(ACCOUNT_FORM, form).statusCode()).isEqualTo(302);
  }

  private static Map<String, String> aGrowHold(String run) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", run);
    form.put("accountId", ACCOUNT);
    form.put("startedAs", "createPaymentProcess:chargeType=2");
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "sum", "10.00");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "description", "one seat, row F");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "successUrl",
        "https://merchant.example/paid");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "cancelUrl",
        "https://merchant.example/gaveup");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "pageField[fullName]",
        "Dana Levi");
    form.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "pageField[phone]", "0501234567");
    return form;
  }

  private static Map<String, String> anAzericardHold(String run, String order) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "azericard");
    form.put("testRunId", run);
    form.put("accountId", ACCOUNT);
    form.put("startedAs", "TRTYPE=0");
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "ORDER", order);
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "AMOUNT", "25.00");
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "CURRENCY", "AZN");
    return form;
  }

  private String object(String provider, String run, String providerObjectId) {
    return get(
        "/ui/objects?provider="
            + provider
            + "&testRunId="
            + encoded(run)
            + "&providerObjectId="
            + encoded(providerObjectId));
  }

  private String theTrailOf(String provider, String run, String origin) {
    return get(
        "/ui/audit?provider=" + provider + "&testRunId=" + encoded(run) + "&origin=" + origin);
  }

  private static String theIdIn(Pattern shape, String source) {
    Matcher named = shape.matcher(source);
    assertThat(named.find()).isTrue();
    return named.group(1);
  }

  private static String locationOf(HttpResponse<byte[]> answer) {
    return answer.headers().firstValue("Location").orElseThrow();
  }

  private String follow(HttpResponse<byte[]> answer) {
    return read(URI.create(locationOf(answer)));
  }

  private String get(String path) {
    return read(URI.create("http://localhost:" + port + path));
  }

  private String read(URI address) {
    HttpResponse<byte[]> answer =
        send(HttpRequest.newBuilder(address).timeout(Duration.ofSeconds(20)).GET());
    assertThat(answer.statusCode()).isEqualTo(200);
    return bodyOf(answer);
  }

  private static String bodyOf(HttpResponse<byte[]> answer) {
    return new String(answer.body(), UTF_8);
  }

  private void discard(String provider, String run) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", run))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity();
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encodedForm(form))));
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String encodedForm(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach((name, value) -> pairs.add(encoded(name) + "=" + encoded(value)));
    return String.join("&", pairs);
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, UTF_8);
  }

  private static final KeyPair MERCHANT = generate();

  private static final KeyPair GATEWAY = generate();

  private static String publicKeyPem() {
    return pem("PUBLIC KEY", MERCHANT.getPublic().getEncoded());
  }

  private static String privateKeyPem() {
    return pem("PRIVATE KEY", GATEWAY.getPrivate().getEncoded());
  }

  private static String pem(String label, byte[] encoded) {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator keys = KeyPairGenerator.getInstance("RSA");
      keys.initialize(2048);
      return keys.generateKeyPair();
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException(refused);
    }
  }
}
