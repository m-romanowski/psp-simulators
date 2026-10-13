package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.io.IOException;
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

class PaymentOriginationUiIntegrationTest extends PostgresBackedTest {

  private static final String WIZARD = "/ui/objects/new";

  private static final String START = "/ui/objects/start";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";

  private static final String ACCOUNT = "merchant-1";

  private static final String TERMINAL = "77777777";

  private static final String CALLBACK = "https://merchant.example/callbacks";

  private static final Pattern A_GROW_PROCESS_ID = Pattern.compile("started=([0-9]{6})");

  private static final Pattern AN_AZERICARD_INT_REF = Pattern.compile("started=([0-9A-F]{16})");

  private static final List<String> THE_PANELS_THE_WIZARD_HAS =
      List.of(
          "This payment exists now",
          "Configure an account before you start a payment",
          "Step 1 of 2",
          "Step 2 of 2");

  private static final String THE_CLASS_A_WIRE_VALUE_IS_PRINTED_IN = "class=\"id\"";

  private static final Pattern A_LABEL = Pattern.compile("(?s)<label [^>]*>(.*?)</label>");

  private static final Pattern A_WIRE_VALUE_SAID_TO_BE_SENT =
      Pattern.compile("(?s)is sent as\\s*<span class=\"id\">([^<]+)</span>");

  private static final Pattern AN_ORIGIN_CELL =
      Pattern.compile(">(PROVIDER_API|CONTROL_API|UI|SCHEDULED_JOB)</span>");

  @LocalServerPort private int port;

  private HttpClient wire;
  private RestClient http;
  private final Set<String> runsThisTestTouched = new LinkedHashSet<>();

  @BeforeEach
  void connect() {
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
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
  void a_tester_whose_run_configures_no_account_is_sent_to_the_screen_that_configures_one() {
    String run = aRunOfItsOwn("grow-without-an-account");

    String step = wizard("grow", run);

    assertThat(step)
        .contains("Configure an account on the Configuration screen")
        .contains("/ui/configuration/account");
    assertThat(step).doesNotContain("Step 1 of 2").doesNotContain("Step 2 of 2");
    assertThat(thePanelsOn(step))
        .containsExactly("Configure an account before you start a payment");
  }

  @Test
  void a_run_that_configures_no_account_is_shown_no_step_even_when_the_url_already_names_a_kind() {
    String run = aRunOfItsOwn("grow-without-an-account-but-with-a-kind");

    String step = step2("grow", run, "createPaymentProcess:chargeType=2");

    assertThat(step).doesNotContain("Step 2 of 2").doesNotContain("Start this payment");
    assertThat(thePanelsOn(step))
        .containsExactly("Configure an account before you start a payment");
  }

  @Test
  void exactly_one_panel_of_the_wizard_is_on_the_screen_in_every_state_the_wizard_can_be_in() {
    String unconfigured = aRunOfItsOwn("grow-one-panel-unconfigured");
    String run = aRunOfItsOwn("grow-one-panel");
    configureAGrowAccount(run);
    HttpResponse<byte[]> started = post(START, aGrowHold(run));

    assertThat(thePanelsOn(wizard("grow", unconfigured))).hasSize(1);
    assertThat(thePanelsOn(step2("grow", unconfigured, "createPaymentProcess:chargeType=2")))
        .hasSize(1);
    assertThat(thePanelsOn(wizard("grow", run))).hasSize(1);
    assertThat(thePanelsOn(step2("grow", run, "createPaymentProcess:chargeType=2"))).hasSize(1);
    assertThat(thePanelsOn(follow(started))).hasSize(1);
  }

  @Test
  void a_start_that_picked_no_way_at_all_is_refused_by_name_rather_than_blocked_in_the_browser() {
    String run = aRunOfItsOwn("grow-no-way-picked");
    configureAGrowAccount(run);
    Map<String, String> form = aGrowHold(run);
    form.remove("startedAs");

    HttpResponse<byte[]> refused = post(START, form);

    assertThat(refused.statusCode())
        .as("a form posted with no screen to go back to is answered where it was posted")
        .isEqualTo(200);
    assertThat(bodyOf(refused))
        .as(
            "the radio group carried required, so a browser would not submit this and the"
                + " refusal naming the ways this provider offers was unreachable by clicking")
        .contains("starts a payment one of these ways and no other")
        .contains("Go back a step and pick one of them");
  }

  private static List<String> thePanelsOn(String screen) {
    List<String> shown = new ArrayList<>();
    for (String heading : THE_PANELS_THE_WIZARD_HAS) {
      if (screen.contains(heading)) {
        shown.add(heading);
      }
    }
    return List.copyOf(shown);
  }

  @Test
  void a_tester_picking_how_a_grow_payment_starts_reads_what_each_choice_leaves_behind() {
    String run = aRunOfItsOwn("grow-choosing");
    configureAGrowAccount(run);

    String step = wizard("grow", run);

    assertThat(step)
        .contains("Hold the money on the payer&#39;s card and settle it later")
        .contains("grow holds the money when the payer picks a card and finishes that page")
        .contains("Save the payer&#39;s card and take no money at all")
        .contains("It moves no money ever");
  }

  @Test
  void the_first_step_keeps_the_wire_value_out_of_every_label_and_behind_the_choice_disclosure() {
    String run = aRunOfItsOwn("grow-wire-value-behind-a-disclosure");
    configureAGrowAccount(run);

    String step = wizard("grow", run);

    assertThat(theLabelsOf(step))
        .isNotEmpty()
        .allSatisfy(
            label -> assertThat(label).doesNotContain(THE_CLASS_A_WIRE_VALUE_IS_PRINTED_IN));
    assertThat(theWireValuesSaidToBeSentIn(step))
        .containsExactly(
            "createPaymentProcess:chargeType=2",
            "createPaymentProcess:chargeType=3",
            "createTransactionWithToken");
    assertThat(step).contains("This simulator serves the payer&#39;s page itself, at /far");
  }

  @Test
  void the_second_step_keeps_every_field_key_out_of_its_label_and_behind_one_disclosure() {
    String run = aRunOfItsOwn("grow-keys-behind-a-disclosure");
    configureAGrowAccount(run);

    String step = step2("grow", run, "createPaymentProcess:chargeType=2");

    assertThat(theLabelsOf(step))
        .isNotEmpty()
        .allSatisfy(
            label -> assertThat(label).doesNotContain(THE_CLASS_A_WIRE_VALUE_IS_PRINTED_IN));
    assertThat(step)
        .contains("The wire values this step sends, and the key each field is recorded under")
        .contains("<dd class=\"id\">sum</dd>")
        .contains("<dd class=\"id\">notifyUrl</dd>")
        .contains("The amount grow puts on the payer");
    assertThat(theWireValuesSaidToBeSentIn(step))
        .containsExactly("createPaymentProcess:chargeType=2");
  }

  private static List<String> theLabelsOf(String screen) {
    List<String> labels = new ArrayList<>();
    Matcher each = A_LABEL.matcher(screen);
    while (each.find()) {
      labels.add(each.group(1));
    }
    return List.copyOf(labels);
  }

  private static List<String> theWireValuesSaidToBeSentIn(String screen) {
    List<String> said = new ArrayList<>();
    Matcher each = A_WIRE_VALUE_SAID_TO_BE_SENT.matcher(screen);
    while (each.find()) {
      said.add(each.group(1));
    }
    return List.copyOf(said);
  }

  @Test
  void a_tester_starts_a_grow_payment_from_the_screens_and_grow_gives_it_a_process_id() {
    String run = aRunOfItsOwn("grow-starting");
    configureAGrowAccount(run);

    HttpResponse<byte[]> started = post(START, aGrowHold(run));

    assertThat(started.statusCode()).isEqualTo(302);
    String processId = theIdIn(A_GROW_PROCESS_ID, locationOf(started));
    assertThat(follow(started))
        .contains("This payment exists now")
        .contains(processId)
        .contains("grow holds the money when the payer picks a card and finishes that page");
  }

  @Test
  void a_grow_payment_a_tester_started_is_in_the_trail_as_theirs_and_not_as_a_clients_call() {
    String run = aRunOfItsOwn("grow-trail");
    configureAGrowAccount(run);

    HttpResponse<byte[]> started = post(START, aGrowHold(run));
    String processId = theIdIn(A_GROW_PROCESS_ID, locationOf(started));

    assertThat(theTrailOf("grow", run, "")).contains(processId);
    assertThat(theTrailOf("grow", run, "UI")).contains(START).contains(processId);
    assertThat(theTrailOf("grow", run, "PROVIDER_API")).doesNotContain(processId);
  }

  @Test
  void a_payment_a_tester_started_as_an_account_is_findable_in_the_trail_by_that_account() {
    String run = aRunOfItsOwn("grow-trail-by-account");
    configureAGrowAccount(run);

    HttpResponse<byte[]> started = post(START, aGrowHold(run));
    String processId = theIdIn(A_GROW_PROCESS_ID, locationOf(started));

    assertThat(theTrailNarrowedTo("grow", run, ACCOUNT)).contains(START).contains(processId);
    assertThat(theTrailNarrowedTo("grow", run, "merchant-nobody"))
        .contains("No row of this test run names the account")
        .doesNotContain(processId);
  }

  @Test
  void a_tester_starts_an_azericard_payment_from_the_screens_and_it_is_listed_as_their_own() {
    String run = aRunOfItsOwn("azericard-starting");
    configureAnAzericardTerminal(run);

    HttpResponse<byte[]> started = post(START, anAzericardHold(run, "20260915100011"));

    assertThat(started.statusCode()).isEqualTo(302);
    String intRef = theIdIn(AN_AZERICARD_INT_REF, locationOf(started));
    assertThat(payments("azericard", run)).contains(intRef);
    assertThat(theOnlyOriginCellOn(payments("azericard", run))).isEqualTo("UI");
  }

  @Test
  void an_azericard_payment_a_tester_started_is_in_the_trail_as_theirs_and_not_as_a_clients_call() {
    String run = aRunOfItsOwn("azericard-trail");
    configureAnAzericardTerminal(run);

    HttpResponse<byte[]> started = post(START, anAzericardHold(run, "20260915100021"));
    String intRef = theIdIn(AN_AZERICARD_INT_REF, locationOf(started));

    assertThat(theTrailOf("azericard", run, "")).contains(intRef);
    assertThat(theTrailOf("azericard", run, "UI")).contains(START).contains(intRef);
    assertThat(theTrailOf("azericard", run, "PROVIDER_API")).doesNotContain(intRef);
  }

  @Test
  void the_azericard_wizard_asks_for_no_credential_and_says_where_each_one_it_sends_comes_from() {
    String run = aRunOfItsOwn("azericard-credentials");
    configureAnAzericardTerminal(run);

    String step = step2("azericard", run, "TRTYPE=0");

    assertThat(step)
        .doesNotContain("name=\"field.TERMINAL\"")
        .doesNotContain("name=\"field.P_SIGN\"")
        .doesNotContain(TERMINAL);
    assertThat(step)
        .contains("What the simulator fills in for you")
        .contains("verifies such a MAC and cannot make one");
  }

  @Test
  void the_grow_wizard_asks_for_no_credential_and_says_where_each_one_it_sends_comes_from() {
    String run = aRunOfItsOwn("grow-credentials");
    configureAGrowAccount(run);

    String step = step2("grow", run, "createPaymentProcess:chargeType=2");

    assertThat(step)
        .doesNotContain("name=\"field.userId\"")
        .doesNotContain("name=\"field.pageCode\"")
        .doesNotContain("a-grow-user-id");
    assertThat(step)
        .contains("What the simulator fills in for you")
        .contains("never reads it back onto a screen");
  }

  @Test
  void a_required_field_left_empty_names_the_field_and_says_what_the_provider_does_with_it() {
    String run = aRunOfItsOwn("grow-missing-amount");
    configureAGrowAccount(run);
    Map<String, String> without = aGrowHold(run);
    without.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "sum", "");

    HttpResponse<byte[]> refused = post(START, without);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refused))
        .contains("cannot start without amount: The amount grow puts on the payer");
  }

  @Test
  void a_callback_target_this_run_does_not_configure_is_refused_with_the_ones_it_does() {
    String run = aRunOfItsOwn("grow-stray-callback");
    configureAGrowAccount(run);
    Map<String, String> stray = aGrowHold(run);
    stray.put(
        TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "notifyUrl",
        "https://elsewhere.example/x");

    HttpResponse<byte[]> refused = post(START, stray);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refused))
        .contains(
            "must be one of the callback targets this test run configures, and this test run"
                + " configures "
                + CALLBACK
                + ".");
  }

  @Test
  void an_order_azericard_cannot_read_comes_back_in_azericards_own_words() {
    String run = aRunOfItsOwn("azericard-short-order");
    configureAnAzericardTerminal(run);
    Map<String, String> tooShort = anAzericardHold(run, "12345");

    HttpResponse<byte[]> refused = post(START, tooShort);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refused)).contains("Error in merchant order field");
  }

  @Test
  void a_way_of_starting_a_payment_this_provider_never_declared_is_refused_by_name() {
    String run = aRunOfItsOwn("grow-unknown-way");
    configureAGrowAccount(run);
    Map<String, String> invented = aGrowHold(run);
    invented.put("startedAs", "createPaymentProcess:chargeType=9");

    HttpResponse<byte[]> refused = post(START, invented);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refused))
        .contains("starts a payment one of these ways and no other: Hold the money on the payer");
  }

  @Test
  void the_wizard_is_named_in_the_navigation_bar_the_screens_share() {
    String run = aRunOfItsOwn("grow-navigation");

    assertThat(payments("grow", run)).contains(">Start a payment</a");
  }

  private String aRunOfItsOwn(String named) {
    String run = "origination-ui-" + named;
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

  private String wizard(String provider, String run) {
    return get(WIZARD + "?provider=" + provider + "&testRunId=" + encoded(run));
  }

  private String step2(String provider, String run, String startedAs) {
    return get(
        WIZARD
            + "?provider="
            + provider
            + "&testRunId="
            + encoded(run)
            + "&accountId="
            + ACCOUNT
            + "&startedAs="
            + encoded(startedAs));
  }

  private String payments(String provider, String run) {
    return get("/ui/objects?provider=" + provider + "&testRunId=" + encoded(run));
  }

  private String theTrailNarrowedTo(String provider, String run, String accountId) {
    return get(
        "/ui/audit?provider="
            + provider
            + "&testRunId="
            + encoded(run)
            + "&accountId="
            + encoded(accountId));
  }

  private String theTrailOf(String provider, String run, String origin) {
    return get(
        "/ui/audit?provider=" + provider + "&testRunId=" + encoded(run) + "&origin=" + origin);
  }

  private static String theOnlyOriginCellOn(String listing) {
    Matcher cells = AN_ORIGIN_CELL.matcher(listing);
    List<String> found = new ArrayList<>();
    while (cells.find()) {
      found.add(cells.group(1));
    }
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private static String theIdIn(Pattern shape, String location) {
    Matcher named = shape.matcher(location);
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
