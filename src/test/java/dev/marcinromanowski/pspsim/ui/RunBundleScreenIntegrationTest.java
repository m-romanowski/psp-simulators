package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class RunBundleScreenIntegrationTest extends PostgresBackedTest {

  private static final String SCREEN = "/ui/run-bundle";

  private static final String RUN = "run-bundle-ui-it";

  private static final String INTO = "run-bundle-ui-it-2";

  private static final String THE_IMPORT_A_SUITE_CALLS = "/__simulator/stripe/run-bundle/import";

  private static final String A_TIME_NO_CLOCK_CAN_BE_PINNED_TO = "noon";

  private static final String THE_RUN_A_TESTER_LEAVES_UNNAMED = "";

  private static final String MERCHANT = "merchant-1";

  private static final String THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON = "sk_test_51UiExportedRunKey";

  private static final String THE_KEY_A_TESTER_TYPES_IN = "sk_test_51UiTypedIntoTheBox";

  private static final String THE_SIGNING_SECRET_A_TESTER_TYPES_IN = "whsec_uiTypedIntoTheBox";

  private static final Instant PINNED = Instant.parse("2026-09-24T12:00:00Z");

  private static final Pattern THE_DOCUMENT_BOX =
      Pattern.compile(
          "<textarea class=\"document\" readonly[^>]*>(.*?)</textarea>", Pattern.DOTALL);

  @LocalServerPort private int port;

  @Autowired private AuditTrail trail;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    clearBothRuns();
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    clearBothRuns();
  }

  @Test
  void a_bundle_turned_away_by_the_admission_leaves_the_same_verdict_whichever_surface_asked() {
    configure(RUN);

    String document = withAnUnreadableClock(theDocumentOn(pageOf(RUN)));

    Answer screen =
        paste(document, THE_RUN_A_TESTER_LEAVES_UNNAMED, theSecretsATesterTypes(), false);
    ResponseEntity<String> api =
        exchange(
            HttpMethod.POST,
            THE_IMPORT_A_SUITE_CALLS,
            "{\"testRunId\":\"" + RUN + "\",\"bundle\":" + document + "}");

    assertThat(screen.status()).isEqualTo(200);
    assertThat(theStatusOfTheOnlyRowAt(THE_IMPORT_A_SUITE_CALLS))
        .as("the answer a suite reads, which is the one the trail of that suite has to agree with")
        .isEqualTo(api.getStatusCode().value());
    assertThat(theStatusOfTheOnlyRowAt(SCREEN + "/import"))
        .as(
            "a tester clicking and a suite calling asked this provider to take back the same"
                + " document and were turned away by the same admission, so a trail read after"
                + " both cannot say the screen was refused what the suite was rejected")
        .isEqualTo(theStatusOfTheOnlyRowAt(THE_IMPORT_A_SUITE_CALLS));
  }

  private static String withAnUnreadableClock(String document) {
    String replaced = document.replace(PINNED.toString(), A_TIME_NO_CLOCK_CAN_BE_PINNED_TO);
    assertThat(replaced).isNotEqualTo(document);
    return replaced;
  }

  private int theStatusOfTheOnlyRowAt(String path) {
    List<AuditRecord> found = new ArrayList<>();
    for (AuditRecord row : trail.of(ProviderId.of("stripe"), TestRunId.of(RUN))) {
      if (path.equals(row.path())) {
        found.add(row);
      }
    }
    assertThat(found).as("the rows this test left at %s", path).hasSize(1);
    Integer status = found.getFirst().httpStatus();
    assertThat(status).as("the status the row at %s carries", path).isNotNull();
    return status;
  }

  @Test
  void the_screen_shows_the_run_as_a_bundle_and_prints_no_value_this_provider_declares_secret() {
    configure(RUN);

    String page = pageOf(RUN);

    assertThat(page).contains("This run as a bundle");
    assertThat(theDocumentOn(page)).contains("\"accountId\":\"" + MERCHANT + "\"");
    assertThat(page).doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
  }

  @Test
  void a_tester_who_clicks_gets_the_report_a_dry_run_answers_and_the_target_run_holds_nothing() {
    configure(RUN);
    writeADelayingRule(RUN);

    Answer reported = paste(theDocumentOn(pageOf(RUN)), INTO, theSecretsATesterTypes(), true);

    assertThat(reported.status()).isEqualTo(200);
    assertThat(reported.body()).contains("What an import would create, update and skip");
    assertThat(reported.body()).contains(RunBundleScreen.NOTHING_WAS_WRITTEN);
    assertThat(theRulesOf(INTO)).isEmpty();
  }

  @Test
  void
      a_tester_who_clicks_without_asking_for_a_dry_run_writes_the_bundle_into_the_run_they_named() {
    configure(RUN);
    writeADelayingRule(RUN);

    Answer applied = paste(theDocumentOn(pageOf(RUN)), INTO, theSecretsATesterTypes(), false);

    assertThat(applied.status()).isEqualTo(200);
    assertThat(applied.body()).contains("What this import created, updated and skipped");
    assertThat(theRulesOf(INTO)).hasSize(1);
  }

  @Test
  void the_report_a_screen_prints_names_the_secret_field_it_was_given_and_never_its_value() {
    configure(RUN);

    Answer applied = paste(theDocumentOn(pageOf(RUN)), INTO, theSecretsATesterTypes(), false);

    assertThat(applied.body()).contains(MERCHANT + ".secretKey");
    assertThat(applied.body())
        .doesNotContain(THE_KEY_A_TESTER_TYPES_IN)
        .doesNotContain(THE_SIGNING_SECRET_A_TESTER_TYPES_IN);
  }

  @Test
  void a_document_carrying_a_secret_is_refused_on_the_screen_by_its_property_and_not_its_value() {
    configure(RUN);
    String document =
        theDocumentOn(pageOf(RUN))
            .replace(
                "\"secretsItDoesNotCarry\"",
                "\"configuration\":{\"secretKey\":\""
                    + THE_KEY_A_TESTER_TYPES_IN
                    + "\"},\"ignored\"");

    Answer refused = paste(document, INTO, "", true);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("This bundle was not taken back");
    assertThat(refused.body()).contains("accounts[0].configuration.secretKey");
    assertThat(refused.body()).doesNotContain(THE_KEY_A_TESTER_TYPES_IN);
  }

  @Test
  void a_secrets_line_this_screen_cannot_read_is_refused_by_its_number_and_never_by_its_text() {
    configure(RUN);

    Answer refused =
        paste(theDocumentOn(pageOf(RUN)), INTO, "just " + THE_KEY_A_TESTER_TYPES_IN, true);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("secrets line 1");
    assertThat(refused.body()).doesNotContain(THE_KEY_A_TESTER_TYPES_IN);
  }

  @Test
  void a_box_holding_nothing_is_refused_and_writes_nothing_into_the_run_it_named() {
    configure(RUN);

    Answer refused = paste("", INTO, "", false);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains(RunBundleScreen.A_BUNDLE_IS_WHAT_THIS_BOX_TAKES);
    assertThat(theRulesOf(INTO)).isEmpty();
  }

  @Test
  void the_secrets_a_tester_typed_are_not_printed_back_into_the_box_on_a_refusal_or_on_a_report() {
    configure(RUN);
    String document = theDocumentOn(pageOf(RUN));

    Answer applied = paste(document, INTO, theSecretsATesterTypes(), false);
    Answer refused = paste("not a bundle", INTO, theSecretsATesterTypes(), false);

    assertThat(applied.body()).doesNotContain(THE_KEY_A_TESTER_TYPES_IN);
    assertThat(refused.body()).doesNotContain(THE_KEY_A_TESTER_TYPES_IN);
    assertThat(applied.body()).contains("<textarea id=\"secrets\"");
  }

  @Test
  void the_screen_says_which_header_stops_a_run_being_exported_and_never_prints_its_value() {
    configure(RUN);
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("testRunId", RUN);
    rule.put(
        "match",
        Map.of("header", Map.of("Authorization", "Bearer " + THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON)));
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    assertThat(post("/__simulator/stripe/scenarios", rule).getStatusCode().value()).isEqualTo(201);

    String page = pageOf(RUN);

    assertThat(page).contains("This run cannot be exported whole");
    assertThat(page).contains("scenarios.match.header.Authorization");
    assertThat(page).doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
  }

  @Test
  void the_bar_of_every_screen_carries_a_link_to_this_one() {
    String page = get("/ui/scenarios?provider=stripe&testRunId=" + RUN);

    assertThat(page).contains("/ui/run-bundle?provider=stripe");
    assertThat(page).contains("Run bundle");
  }

  private String theSecretsATesterTypes() {
    return MERCHANT
        + ".secretKey="
        + THE_KEY_A_TESTER_TYPES_IN
        + "\n"
        + MERCHANT
        + ".webhookSecret="
        + THE_SIGNING_SECRET_A_TESTER_TYPES_IN;
  }

  private String pageOf(String run) {
    return get(SCREEN + "?provider=stripe&testRunId=" + run);
  }

  private Answer paste(String document, String into, String secrets, boolean dryRun) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN);
    form.put("into", into);
    form.put("document", document);
    form.put("secrets", secrets);
    if (dryRun) {
      form.put("dryRun", "on");
    }
    return submit(SCREEN + "/import", form);
  }

  private static String theDocumentOn(String page) {
    Matcher box = THE_DOCUMENT_BOX.matcher(page);
    assertThat(box.find()).isTrue();
    return unescaped(box.group(1));
  }

  private void configure(String run) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put("secretKey", THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
    configuration.put("captureWindow", "P3D");
    ResponseEntity<String> applied =
        exchange(
            HttpMethod.PUT,
            "/__simulator/stripe/config",
            Map.of(
                "testRunId",
                run,
                "clock",
                Map.of("fixedNow", PINNED.toString()),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", "https://merchant.example/hooks"))));
    assertThat(applied.getStatusCode().value()).isEqualTo(200);
  }

  private void writeADelayingRule(String run) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("testRunId", run);
    rule.put("action", Map.of("type", "DELIVERY_DELAYED", "delayMs", 900_000L));
    assertThat(post("/__simulator/stripe/scenarios", rule).getStatusCode().value()).isEqualTo(201);
  }

  private List<?> theRulesOf(String run) {
    ResponseEntity<String> answered =
        http.get()
            .uri("/__simulator/stripe/scenarios?testRunId=" + run)
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);
    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    String body = String.valueOf(answered.getBody());
    List<String> ids = new ArrayList<>();
    Matcher found = Pattern.compile("\"scenarioId\":\"([^\"]+)\"").matcher(body);
    while (found.find()) {
      ids.add(found.group(1));
    }
    return ids;
  }

  private ResponseEntity<String> post(String path, Object body) {
    return exchange(HttpMethod.POST, path, body);
  }

  private ResponseEntity<String> exchange(HttpMethod method, String path, Object body) {
    return http.method(method)
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private void clearBothRuns() {
    forgetEverythingOf(RUN);
    forgetEverythingOf(INTO);
  }

  private void forgetEverythingOf(String run) {
    post("/__simulator/stripe/reset", Map.of("testRunId", run));
    Map<String, Object> released = new LinkedHashMap<>();
    released.put("testRunId", run);
    released.put("clock", Map.of());
    exchange(HttpMethod.PUT, "/__simulator/stripe/config", released);
    http.method(HttpMethod.DELETE)
        .uri("/__simulator/stripe/config/accounts/" + MERCHANT + "?testRunId=" + run)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toBodilessEntity();
  }

  private String get(String path) {
    HttpResponse<byte[]> answered =
        send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET());
    assertThat(answered.statusCode()).isEqualTo(200);
    return new String(answered.body(), UTF_8);
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> answered =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return new Answer(answered.statusCode(), new String(answered.body(), UTF_8));
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

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private static String unescaped(String markup) {
    return markup
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private record Answer(int status, String body) {}
}
