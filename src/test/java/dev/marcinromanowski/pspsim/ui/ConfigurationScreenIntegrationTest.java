package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class ConfigurationScreenIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-configuration-it");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String LATER = "2026-09-08T09:30:00Z";
  private static final String MERCHANT = "merchant-1";
  private static final String PATH = "/ui/configuration";

  private static final String ALIASED_URL =
      "https://screen.provider.example/v2/merchant-1/payments";

  private static final String GROW_CREATE = "/api/light/server/1.0/createPaymentProcess";

  private static final String GROW_USER = "grow-user-must-not-be-read-off-a-monitor";
  private static final String GROW_PAGE = "grow-page-must-not-be-read-off-a-monitor";
  private static final String TERMINAL = "az-terminal-1";
  private static final String STRIPE_SECRET_KEY = "sk_test_must_not_be_read_off_a_monitor";
  private static final String STRIPE_SIGNING_SECRET = "whsec_must_not_be_read_off_a_monitor";

  private static final String A_PLAN_SET_BY_CLICKING = "PT0S,PT7S,PT45S";

  private static final String THE_RUN_THAT_SETS_ONE = "ui-configuration-settings-set-it";

  private static final String THE_RUN_THAT_KEEPS_NOTHING = "ui-configuration-settings-refused-it";

  private static final String THE_RUN_THAT_TOUCHES_NOTHING =
      "ui-configuration-settings-untouched-it";

  private static final String A_PLAN_NO_PROVIDER_DELIVERS_ON = "PT5S,PT1S";

  private static final String THE_RETRY_PLAN = "webhook.retryPlan";

  private static final String THE_SUBMITTED_FIELD =
      SettingsForm.SUBMITTED_SETTING_PREFIX + THE_RETRY_PLAN;

  private static final KeyPair MERCHANT_KEYS = rsa();
  private static final KeyPair GATEWAY_KEYS = rsa();
  private static final String MERCHANT_PUBLIC_PEM =
      pem("PUBLIC KEY", MERCHANT_KEYS.getPublic().getEncoded());
  private static final String THE_PEM_BOUNDARY = "-----";
  private static final String GATEWAY_PRIVATE_PEM =
      pem("PRIVATE KEY", GATEWAY_KEYS.getPrivate().getEncoded());

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    configureGrow();
    configureAzericard();
    configureStripe();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure("grow");
    unconfigure("azericard");
    unconfigure("stripe");
  }

  @Test
  void each_provider_names_its_own_account_properties_and_the_screen_adds_none_of_its_own() {
    String grow = page(screen("grow", MERCHANT, null));
    String azericard = page(screen("azericard", MERCHANT, null));
    String stripe = page(screen("stripe", MERCHANT, null));

    assertThat(grow).contains("userId").contains("pageCode").contains("callbackAcknowledgement");
    assertThat(azericard).contains("terminalId").contains("apiKey").contains("apiSecret");
    assertThat(stripe).contains("secretKey").contains("captureWindow").contains("webhookSecret");
    assertThat(grow).doesNotContain("terminalId").doesNotContain("apiSecret");
    assertThat(azericard).doesNotContain("pageCode").doesNotContain("secretKey");
  }

  @Test
  void a_secret_is_named_on_the_screen_and_its_value_is_absent_even_when_values_are_asked_for() {
    String azericard = page(screen("azericard", MERCHANT, "show"));
    String stripe = page(screen("stripe", MERCHANT, "show"));
    String grow = page(screen("grow", MERCHANT, "show"));

    List<String> theKeyMaterial = theKeyMaterialLinesOf(GATEWAY_PRIVATE_PEM);
    assertThat(theKeyMaterial)
        .as(
            "a key this test read as no line at all would leave the assertion below forbidding"
                + " nothing, and one that read as a single line would not tell a check of every"
                + " line apart from a check of one")
        .hasSizeGreaterThan(1);
    assertThat(azericard).contains("apiSecret");
    assertThat(azericard)
        .as(
            "a private key is disclosed in base64 lines and every one of them carries key"
                + " material, so a screen showing any single line has published the key that far"
                + " and all of them are forbidden rather than one")
        .doesNotContain(theKeyMaterial.toArray(new String[0]));
    assertThat(stripe)
        .contains("secretKey")
        .doesNotContain(STRIPE_SECRET_KEY)
        .doesNotContain(STRIPE_SIGNING_SECRET);
    assertThat(grow).doesNotContain(GROW_USER).doesNotContain(GROW_PAGE);
  }

  @Test
  void a_configured_value_reaches_the_page_only_after_the_reader_asks_for_it_by_name() {
    String listed = page(screen("azericard", null, null));
    String opened = page(screen("azericard", MERCHANT, null));
    String asked = page(screen("azericard", MERCHANT, "show"));

    assertThat(listed).doesNotContain("terminalId").doesNotContain(TERMINAL);
    assertThat(opened).contains("terminalId").doesNotContain(TERMINAL);
    assertThat(asked).contains("terminalId").contains(TERMINAL);
  }

  @Test
  void the_screen_reports_what_the_simulator_was_started_with_and_the_header_that_routes() {
    String grow = page(screen("grow", null, null));

    assertThat(grow).contains(TestRunHeader.NAME);
    assertThat(grow).contains("http://localhost:8080");
    assertThat(grow).contains("permissive-auth modes");
  }

  @Test
  void the_screen_shows_the_callback_plan_the_provider_it_names_actually_delivers_on() {
    String grow = page(screen("grow", null, null));
    String azericard = page(screen("azericard", null, null));

    assertThat(thePlanOn(grow)).isEqualTo("PT0S,PT1S,PT5S,PT30S,PT2M,PT5M");
    assertThat(theAttemptCapOn(grow)).isEqualTo("6");
    assertThat(theAttemptPlanOn(azericard))
        .isEqualTo("0 seconds, 1 second, 5 seconds, 30 seconds, 2 minutes");
    assertThat(theAttemptCapOn(azericard)).isEqualTo("5");
  }

  @Test
  void the_plan_a_provider_can_change_is_printed_once_and_the_startup_row_is_not_repeated() {
    String grow = page(screen("grow", null, null));
    String azericard = page(screen("azericard", null, null));

    assertThat(grow)
        .as(
            "a provider whose test run can change the plan prints it in the settings table, and a"
                + " second spelling of the same plan beside it is one the two could disagree in")
        .doesNotContain("<dt>delay before each attempt</dt>");
    assertThat(azericard).contains("<dt>delay before each attempt</dt>");
  }

  @Test
  void a_plan_this_test_run_set_through_the_control_api_is_the_plan_the_screen_then_shows() {
    String before = thePlanOn(page(screen("grow", null, null)));

    theRetryPlanOfTheRunIsSetTo("PT0S,PT3S");

    String after = page(screen("grow", null, null));
    assertThat(before).isEqualTo("PT0S,PT1S,PT5S,PT30S,PT2M,PT5M");
    assertThat(thePlanOn(after)).isEqualTo("PT0S,PT3S");
    assertThat(theAttemptCapOn(after)).isEqualTo("2");
  }

  private void theRetryPlanOfTheRunIsSetTo(String plan) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "settings", Map.of("webhook.retryPlan", plan)))
        .retrieve()
        .toBodilessEntity();
  }

  private static String theAttemptPlanOn(String page) {
    return theValueUnder(page, "delay before each attempt");
  }

  private static String thePlanOn(String page) {
    Matcher value =
        Pattern.compile(
                "<td class=\"id\">"
                    + Pattern.quote(THE_RETRY_PLAN)
                    + "</td>\\s*<td class=\"wrap id\">([^<]*)</td>",
                Pattern.DOTALL)
            .matcher(page);
    assertThat(value.find()).isTrue();
    return value.group(1).strip();
  }

  private static String theAttemptCapOn(String page) {
    return theValueUnder(page, "callback attempts per delivery");
  }

  private static String theValueUnder(String page, String label) {
    Matcher value =
        Pattern.compile(
                "<dt>" + Pattern.quote(label) + "</dt>\\s*<dd[^>]*>([^<]*)</dd>", Pattern.DOTALL)
            .matcher(page);
    assertThat(value.find()).isTrue();
    return value.group(1).strip();
  }

  @Test
  void a_plan_set_by_clicking_is_the_plan_the_control_api_then_reports_for_the_run() {
    Answer set = setTheRetryPlanByClicking(THE_RUN_THAT_SETS_ONE, A_PLAN_SET_BY_CLICKING);

    String screen = page(set.location());
    assertThat(set.status()).isEqualTo(302);
    assertThat(theSettingsTheControlApiReports(THE_RUN_THAT_SETS_ONE))
        .containsEntry(THE_RETRY_PLAN, A_PLAN_SET_BY_CLICKING);
    assertThat(thePlanOn(screen)).isEqualTo("PT0S,PT7S,PT45S");
    assertThat(theAttemptCapOn(screen)).isEqualTo("3");
    assertThat(screen).contains("this test run set it");
  }

  @Test
  void a_field_emptied_rather_than_typed_into_is_refused_and_returns_no_setting_to_its_default() {
    Answer emptied = setTheRetryPlanByClicking(THE_RUN_THAT_KEEPS_NOTHING, "");

    assertThat(emptied.status()).isEqualTo(200);
    assertThat(emptied.body())
        .contains(theControlApiRefusalOf(THE_RUN_THAT_KEEPS_NOTHING, ""))
        .contains("is required");
    assertThat(emptied.body())
        .as("nothing may offer the provider's default as the value an empty field would send")
        .doesNotContain("placeholder=\"PT0S,PT1S,PT5S,PT30S,PT2M,PT5M\"");
  }

  @Test
  void a_form_handed_back_untouched_leaves_the_run_on_the_default_it_was_already_reading() {
    Answer resubmitted =
        setTheRetryPlanByClicking(THE_RUN_THAT_TOUCHES_NOTHING, "PT0S,PT1S,PT5S,PT30S,PT2M,PT5M");

    String screen = page(resubmitted.location());
    assertThat(resubmitted.status()).isEqualTo(302);
    assertThat(theSettingsTheControlApiReports(THE_RUN_THAT_TOUCHES_NOTHING))
        .as("submitting the value a field was handed pins nothing this test run did not choose")
        .doesNotContainKey(THE_RETRY_PLAN);
    assertThat(screen).contains("the default, until this test run sets one");
  }

  @Test
  void a_plan_the_screen_refuses_is_refused_in_the_words_the_control_api_refuses_it_in() {
    Answer refusedByTheScreen =
        setTheRetryPlanByClicking(THE_RUN_THAT_KEEPS_NOTHING, A_PLAN_NO_PROVIDER_DELIVERS_ON);

    String refusedByTheApi =
        theControlApiRefusalOf(THE_RUN_THAT_KEEPS_NOTHING, A_PLAN_NO_PROVIDER_DELIVERS_ON);
    assertThat(refusedByTheScreen.status()).isEqualTo(200);
    assertThat(refusedByTheScreen.body()).contains(refusedByTheApi);
    assertThat(refusedByTheScreen.body())
        .as("a value the screen refused is handed back to the field that carried it")
        .contains("value=\"" + A_PLAN_NO_PROVIDER_DELIVERS_ON + "\"");
    assertThat(thePlanOn(page(theScreenOf(THE_RUN_THAT_KEEPS_NOTHING))))
        .isEqualTo("PT0S,PT1S,PT5S,PT30S,PT2M,PT5M");
  }

  @Test
  void a_provider_that_declares_no_setting_offers_a_tester_no_field_to_set_one_with() {
    String stripe = page(screen("stripe", null, null));
    String grow = page(screen("grow", null, null));

    assertThat(stripe).contains("declares no setting a test run can change");
    assertThat(stripe).doesNotContain(THE_SUBMITTED_FIELD);
    assertThat(grow).contains(THE_SUBMITTED_FIELD);
    assertThat(grow).contains("the delay before each callback attempt");
  }

  private static String theScreenOf(String testRunId) {
    return PATH + "?provider=grow&testRunId=" + testRunId;
  }

  private Answer setTheRetryPlanByClicking(String testRunId, String plan) {
    String form =
        "provider=grow&testRunId="
            + URLEncoder.encode(testRunId, UTF_8)
            + "&"
            + THE_SUBMITTED_FIELD
            + "="
            + URLEncoder.encode(plan, UTF_8);
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH + "/settings"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private Map<String, String> theSettingsTheControlApiReports(String testRunId) {
    Map<?, ?> bundle =
        http.get()
            .uri("/__simulator/grow/run-bundle?testRunId=" + URLEncoder.encode(testRunId, UTF_8))
            .retrieve()
            .body(Map.class);
    Map<?, ?> settings = (Map<?, ?>) requireNonNull(bundle).get("settings");
    Map<String, String> reported = new LinkedHashMap<>();
    requireNonNull(settings)
        .forEach((key, value) -> reported.put(String.valueOf(key), String.valueOf(value)));
    return reported;
  }

  private String theControlApiRefusalOf(String testRunId, String plan) {
    Map<?, ?> refusal =
        http.put()
            .uri("/__simulator/grow/config")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", testRunId, "settings", Map.of(THE_RETRY_PLAN, plan)))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .body(Map.class);
    return String.valueOf(requireNonNull(refusal).get("message"));
  }

  @Test
  void a_url_bound_from_the_screen_is_listed_on_it_beside_the_route_that_answers_it() {
    Answer bound = bindAlias(ALIASED_URL, GROW_CREATE);

    String listed = page(bound.location());

    assertThat(bound.status()).isEqualTo(302);
    assertThat(listed).contains(ALIASED_URL).contains(GROW_CREATE);
  }

  @Test
  void a_url_the_screen_cannot_bind_is_refused_by_naming_the_field_and_binds_nothing() {
    Answer refused = bindAlias("/not-absolute", GROW_CREATE);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("routeAliases[0].from");
    assertThat(page(screen("grow", null, null))).doesNotContain("/not-absolute");
  }

  @Test
  void a_test_run_nothing_bound_a_url_for_says_so_rather_than_showing_an_empty_table() {
    assertThat(page(screen("azericard", null, null)))
        .contains("No route alias is bound to this test run");
  }

  @Test
  void a_url_released_from_the_screen_is_gone_from_it_and_the_run_answers_on_nothing() {
    bindAlias(ALIASED_URL, GROW_CREATE);

    Answer released = releaseAlias(ALIASED_URL, ConfigurationScreen.REMOVAL_IS_CONFIRMED);

    assertThat(released.status()).isEqualTo(302);
    assertThat(page(released.location()))
        .doesNotContain(ALIASED_URL)
        .contains("No route alias is bound to this test run");
  }

  @Test
  void a_release_the_screen_did_not_ask_twice_for_leaves_the_url_bound_and_says_why() {
    bindAlias(ALIASED_URL, GROW_CREATE);

    Answer unconfirmed = releaseAlias(ALIASED_URL, null);

    assertThat(unconfirmed.status()).isEqualTo(200);
    assertThat(unconfirmed.body()).contains("tick the box beside the URL");
    assertThat(page(screen("grow", null, null))).contains(ALIASED_URL);
  }

  private Answer releaseAlias(String from, String confirm) {
    String form =
        "provider=grow&testRunId="
            + URLEncoder.encode(RUN.value(), UTF_8)
            + "&from="
            + URLEncoder.encode(from, UTF_8)
            + (confirm == null ? "" : "&confirm=" + URLEncoder.encode(confirm, UTF_8));
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + PATH + "/route-alias/release"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private Answer bindAlias(String from, String to) {
    String form =
        "provider=grow&testRunId="
            + URLEncoder.encode(RUN.value(), UTF_8)
            + "&from="
            + URLEncoder.encode(from, UTF_8)
            + "&to="
            + URLEncoder.encode(to, UTF_8);
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH + "/route-alias"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  @Test
  void pinning_the_clock_from_the_screen_moves_the_test_run_and_leaves_its_accounts_in_place() {
    Answer pinned = pin(LATER);

    String moved = page(pinned.location());
    String account = page(theAccountLinkOn(moved, MERCHANT));

    assertThat(pinned.status()).isEqualTo(302);
    assertThat(moved).contains(LATER).contains(MERCHANT);
    assertThat(account).contains(LATER).contains("userId");
    assertThat(page(screen("azericard", MERCHANT, null))).contains(LATER).contains("terminalId");
  }

  @Test
  void releasing_the_clock_from_the_screen_hands_the_test_run_back_to_the_host_clock() {
    Answer released = pin("");

    String handedBack = page(released.location());

    assertThat(released.status()).isEqualTo(302);
    assertThat(handedBack).contains(RUN.value());
    assertThat(handedBack).contains("this test run reads the host clock");
  }

  @Test
  void a_time_that_is_not_an_instant_is_refused_by_naming_the_field_and_never_the_value() {
    Answer refused = pin("yesterday");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("fixedNow").doesNotContain("yesterday");
    assertThat(page(screen("grow", null, null))).contains(PINNED.toString());
  }

  private Answer pin(String fixedNow) {
    String form =
        "provider=grow&testRunId="
            + URLEncoder.encode(RUN.value(), UTF_8)
            + "&fixedNow="
            + URLEncoder.encode(fixedNow, UTF_8);
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + PATH + "/clock"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private static String theAccountLinkOn(String page, String accountId) {
    Matcher link =
        Pattern.compile("href=\"(/ui/configuration\\?[^\"]*open=" + accountId + ")\"")
            .matcher(page);
    assertThat(link.find()).isTrue();
    return link.group(1).replace("&amp;", "&");
  }

  private static String screen(String provider, String open, String values) {
    StringBuilder uri = new StringBuilder(PATH);
    uri.append("?provider=").append(provider).append("&testRunId=").append(RUN.value());
    if (open != null) {
      uri.append("&open=").append(open);
    }
    if (values != null) {
      uri.append("&values=").append(values);
    }
    return uri.toString();
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        send(HttpRequest.newBuilder(at(uri)).timeout(Duration.ofSeconds(20)).GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private URI at(String uri) {
    return URI.create(uri.startsWith("http") ? uri : "http://localhost:" + port + uri);
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

  private void configureGrow() {
    configure(
        "grow",
        Map.of("userId", GROW_USER, "pageCode", GROW_PAGE),
        List.of(Map.of("accountId", MERCHANT, "url", "https://merchant.test/callbacks")));
  }

  private void configureAzericard() {
    configure(
        "azericard",
        Map.of(
            "terminalId",
            TERMINAL,
            "apiKey",
            MERCHANT_PUBLIC_PEM,
            "apiSecret",
            GATEWAY_PRIVATE_PEM),
        List.of());
  }

  private void configureStripe() {
    configure(
        "stripe",
        Map.of(
            "secretKey",
            STRIPE_SECRET_KEY,
            "captureWindow",
            "P3D",
            "webhookSecret",
            STRIPE_SIGNING_SECRET),
        List.of());
  }

  private void configure(
      String provider, Map<String, String> configuration, List<Map<String, String>> webhooks) {
    http.put()
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", PINNED.toString()),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                webhooks))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure(String provider) {
    http.put()
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private static KeyPair rsa() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException(missing);
    }
  }

  private static String pem(String label, byte[] encoded) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
        + "\n-----END "
        + label
        + "-----\n";
  }

  private static List<String> theKeyMaterialLinesOf(String pem) {
    return pem.lines().filter(line -> !line.startsWith(THE_PEM_BOUNDARY)).toList();
  }

  private record Answer(int status, String body, String location) {}
}
