package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.repro.ProviderNativeIds;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionManifest;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionBundles;
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

class ReproductionScreenIntegrationTest extends PostgresBackedTest {

  private static final String SCREEN = "/ui/repro-bundle";

  private static final String RUN = "repro-ui-it";

  private static final String INTO = "repro-ui-it-2";

  private static final String MERCHANT = "merchant-1";

  private static final String THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON = "sk_test_51ReproUiExportedKey";

  private static final String THE_KEY_A_TESTER_TYPES_IN = "sk_test_51ReproUiTypedIntoTheBox";

  private static final String THE_CARD_THIS_FIXTURE_NAMES = "pm_card_visa";

  private static final String THE_CARD_A_TESTER_REMAPS_ONTO = "pm_card_typed_by_a_tester";

  private static final String THE_CARD_NO_INSTRUMENT_OF_THIS_BUNDLE_CARRIES = "pm_card_mastercard";

  private static final String FIXTURE_ALIAS = "visa-success";

  private static final Instant PINNED = Instant.parse("2026-09-24T12:00:00Z");

  private static final Pattern THE_DOCUMENT_BOX =
      Pattern.compile(
          "<textarea class=\"document\" readonly[^>]*>(.*?)</textarea>", Pattern.DOTALL);

  private static final Pattern THE_CELL_NAMING_WHAT_WAS_WRITTEN =
      Pattern.compile("id=\"written\"[^>]*>(.*?)</td>", Pattern.DOTALL);

  private static final Pattern THE_CELL_NAMING_WHAT_WAS_ONLY_READ =
      Pattern.compile("id=\"only-read\"[^>]*>(.*?)</td>", Pattern.DOTALL);

  private static final String IMPORT = SCREEN + "/import";

  private static final String THE_IMPORT_A_SUITE_CALLS = "/__simulator/stripe/repro-bundle/import";

  private static final String A_DOCUMENT_CARRYING_NO_SETUP = "{}";

  private static final ProviderId STRIPE = ProviderId.of("stripe");

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
  void the_screen_is_reachable_from_every_other_screen_and_asks_for_the_payment_before_anything() {
    String page = get(SCREEN + "?provider=stripe&testRunId=" + RUN);

    assertThat(page).contains("Reproduction bundle");
    assertThat(page).contains("Package one payment");
    assertThat(page).doesNotContain("This payment as a bundle");
  }

  @Test
  void the_screen_says_which_contents_an_import_writes_and_which_it_only_reads() {
    String intent = aPaymentOf(RUN);

    String page = unescaped(pageOf(RUN, intent));

    assertThat(page).contains("What this bundle carries, and which of it can be replayed");
    assertThat(page).contains("an import writes this");
    assertThat(page).contains("no import writes this");
    assertThat(page).contains(ReproductionManifest.AN_IMPORT_WRITES_IT);
    assertThat(page)
        .contains(ReproductionManifest.A_SIGNED_MESSAGE_VERIFIES_AGAINST_THE_KEY_THAT_MADE_IT);
    assertThat(page)
        .contains(ReproductionManifest.AN_OBJECT_IS_MINTED_BY_THE_EXCHANGE_THAT_CREATED_IT);
    assertThat(page).contains(ReproductionManifest.A_TRAIL_IS_WHAT_THIS_SIMULATOR_DID);
    assertThat(page).contains(ReproductionScreen.THE_EVIDENCE_IS_READ_AND_NEVER_WRITTEN);
    assertThat(page).contains(ReproductionScreen.A_SIGNATURE_TRAVELS_AS_EVIDENCE);
    assertThat(page).contains(ReproductionScreen.IDS_ARE_PRESERVED_UNLESS_A_REMAPPING_IS_TYPED);
  }

  @Test
  void
      the_screen_shows_the_payment_as_a_bundle_and_prints_no_value_this_provider_declares_secret() {
    String intent = aPaymentOf(RUN);

    String page = pageOf(RUN, intent);

    assertThat(page).contains("This payment as a bundle");
    assertThat(theDocumentOn(page)).contains("\"accountId\":\"" + MERCHANT + "\"");
    assertThat(theDocumentOn(page)).contains("\"providerObjectId\":\"" + intent + "\"");
    assertThat(page).doesNotContain(THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON);
  }

  @Test
  void
      a_payment_this_run_never_held_is_refused_on_the_screen_rather_than_shown_as_an_empty_bundle() {
    aPaymentOf(RUN);

    String page = unescaped(pageOf(RUN, "pi_nothing"));

    assertThat(page).contains(ReproductionBundles.NO_OBJECT_OF_THIS_RUN_ANSWERS_TO_IT);
    assertThat(page).doesNotContain("This payment as a bundle");
  }

  @Test
  void a_tester_who_asks_for_a_report_only_gets_one_and_the_target_run_holds_nothing_afterwards() {
    String intent = aPaymentOf(RUN);

    Answer reported = paste(theDocumentOn(pageOf(RUN, intent)), INTO, aSecret(), "", true);

    assertThat(reported.status()).isEqualTo(200);
    assertThat(reported.body()).contains("What an import would create, update and skip");
    assertThat(unescaped(reported.body())).contains(ReproductionScreen.NOTHING_WAS_WRITTEN);
    assertThat(theFixturesOf(INTO)).isEmpty();
    assertThat(theRulesOf(INTO)).isEmpty();
  }

  @Test
  void a_tester_who_confirms_writes_the_setup_and_the_report_names_what_it_only_read() {
    String intent = aPaymentOf(RUN);

    Answer applied = paste(theDocumentOn(pageOf(RUN, intent)), INTO, aSecret(), "", false);

    assertThat(applied.status()).isEqualTo(200);
    assertThat(applied.body()).contains("What this import created, updated and skipped");
    assertThat(theCellNamingWhatWasWritten(applied.body()))
        .contains(ReproductionManifest.FIXTURES, ReproductionManifest.ACCOUNTS);
    assertThat(theCellNamingWhatWasOnlyRead(applied.body()))
        .contains(ReproductionManifest.TRAIL, ReproductionManifest.CALLBACKS);
    assertThat(theFixturesOf(INTO)).hasSize(1);
  }

  @Test
  void a_tester_who_types_no_remapping_is_told_the_ids_were_preserved() {
    String intent = aPaymentOf(RUN);

    Answer applied = paste(theDocumentOn(pageOf(RUN, intent)), INTO, aSecret(), "", false);

    assertThat(applied.body()).contains("id=\"ids-preserved\"");
    assertThat(applied.body()).doesNotContain("id=\"ids-remapped\"");
    assertThat(theProviderIdOfTheOnlyFixtureOf(INTO)).isEqualTo(THE_CARD_THIS_FIXTURE_NAMES);
  }

  @Test
  void a_tester_who_types_a_remapping_is_told_which_key_changed_and_the_run_holds_the_new_id() {
    String intent = aPaymentOf(RUN);

    Answer applied =
        paste(
            theDocumentOn(pageOf(RUN, intent)),
            INTO,
            aSecret(),
            THE_CARD_THIS_FIXTURE_NAMES + "=" + THE_CARD_A_TESTER_REMAPS_ONTO,
            false);

    assertThat(applied.body()).contains("id=\"ids-remapped\"");
    assertThat(applied.body()).doesNotContain("id=\"ids-preserved\"");
    assertThat(theProviderIdOfTheOnlyFixtureOf(INTO)).isEqualTo(THE_CARD_A_TESTER_REMAPS_ONTO);
  }

  @Test
  void an_id_the_target_run_already_gave_away_is_refused_on_the_screen_without_naming_the_id() {
    String intent = aPaymentOf(RUN);
    String document = theDocumentOn(pageOf(RUN, intent));
    paste(document, INTO, aSecret(), "", false);

    Answer again = paste(renamed(document), INTO, aSecret(), "", false);

    assertThat(again.body()).contains("This bundle was not taken back");
    assertThat(again.body()).contains("setup.fixtures[0].providerIds.paymentMethodId");
    assertThat(again.body()).doesNotContain(THE_CARD_THIS_FIXTURE_NAMES);
    assertThat(theFixturesOf(INTO)).hasSize(1);
  }

  @Test
  void a_remapping_line_the_screen_cannot_read_is_refused_by_its_number_and_writes_nothing() {
    String intent = aPaymentOf(RUN);

    Answer refused =
        paste(theDocumentOn(pageOf(RUN, intent)), INTO, aSecret(), "pm_card_visa", false);

    assertThat(refused.body())
        .contains(ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO + " line 1");
    assertThat(theFixturesOf(INTO)).isEmpty();
  }

  @Test
  void a_box_that_holds_no_bundle_is_refused_and_writes_nothing() {
    Answer refused = paste("", INTO, aSecret(), "", false);

    assertThat(unescaped(refused.body()))
        .contains(ReproductionScreen.A_BUNDLE_IS_WHAT_THIS_BOX_TAKES);
    assertThat(theFixturesOf(INTO)).isEmpty();
  }

  @Test
  void a_secrets_line_the_screen_cannot_read_is_refused_in_a_row_saying_why_and_writes_nothing() {
    String intent = aPaymentOf(RUN);

    Answer refused =
        paste(theDocumentOn(pageOf(RUN, intent)), INTO, THE_KEY_A_TESTER_TYPES_IN, "", false);

    assertThat(unescaped(refused.body()))
        .contains(RunBundleScreen.A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE);
    assertThat(theWordsTheOnlyImportRowWasRefusedIn())
        .as(
            "a tester reading the trail afterwards is told which line of the secrets box the"
                + " import could not read, and the key typed on it is never carried into the row")
        .contains("secrets line 1")
        .contains(RunBundleScreen.A_LINE_NAMES_AN_ACCOUNT_A_FIELD_AND_A_VALUE)
        .doesNotContain(THE_KEY_A_TESTER_TYPES_IN);
    assertThat(theFixturesOf(INTO)).isEmpty();
  }

  @Test
  void a_document_that_reads_as_json_but_carries_no_setup_is_refused_in_a_row_saying_why() {
    Answer refused = paste(A_DOCUMENT_CARRYING_NO_SETUP, INTO, aSecret(), "", false);

    assertThat(unescaped(refused.body()))
        .contains(ReproductionAdmission.A_BUNDLE_CARRIES_THE_SETUP_IT_REPRODUCES);
    assertThat(theWordsTheOnlyImportRowWasRefusedIn())
        .as(
            "a document this simulator read and found nothing to write in is turned away by the"
                + " admission rather than by the box, and the row says that much")
        .isEqualTo(ReproductionAdmission.A_BUNDLE_CARRIES_THE_SETUP_IT_REPRODUCES);
    assertThat(theFixturesOf(INTO)).isEmpty();
  }

  @Test
  void a_remapping_of_an_id_this_bundle_never_carried_is_refused_in_a_row_saying_why() {
    String intent = aPaymentOf(RUN);

    Answer refused =
        paste(
            theDocumentOn(pageOf(RUN, intent)),
            INTO,
            aSecret(),
            THE_CARD_NO_INSTRUMENT_OF_THIS_BUNDLE_CARRIES + "=" + THE_CARD_A_TESTER_REMAPS_ONTO,
            false);

    assertThat(refused.body()).contains("This bundle was not taken back");
    assertThat(theWordsTheOnlyImportRowWasRefusedIn())
        .as(
            "a remapping that would be written nowhere is refused rather than silently preserving"
                + " the ids the bundle does carry, and the row names the line and says why")
        .contains(ProviderNativeIds.REMAP + "." + THE_CARD_NO_INSTRUMENT_OF_THIS_BUNDLE_CARRIES)
        .contains(ProviderNativeIds.NOTHING_THIS_BUNDLE_CARRIES_ANSWERS_TO_IT);
    assertThat(theRefusedPropertiesOfTheOnlyImportRow())
        .containsExactly(
            ProviderNativeIds.REMAP + "." + THE_CARD_NO_INSTRUMENT_OF_THIS_BUNDLE_CARRIES);
    assertThat(theFixturesOf(INTO)).isEmpty();
  }

  @Test
  void a_bundle_turned_away_by_the_admission_leaves_the_same_verdict_whichever_surface_asked() {
    Answer screen = paste(A_DOCUMENT_CARRYING_NO_SETUP, INTO, aSecret(), "", false);
    ResponseEntity<String> api =
        exchange(
            HttpMethod.POST,
            THE_IMPORT_A_SUITE_CALLS,
            Map.of("testRunId", RUN, "bundle", Map.of()));

    assertThat(theStatusOfTheOnlyRowAt(THE_IMPORT_A_SUITE_CALLS, RUN))
        .as("the answer a suite reads, which is the one the trail of that suite has to agree with")
        .isEqualTo(api.getStatusCode().value());
    assertThat(theStatusOfTheOnlyRowAt(IMPORT, INTO))
        .as(
            "a tester clicking and a suite calling asked this provider to take back the same"
                + " document and were turned away by the same admission, so a trail read after"
                + " both cannot say the screen was refused what the suite was rejected")
        .isEqualTo(theStatusOfTheOnlyRowAt(THE_IMPORT_A_SUITE_CALLS, RUN));
    assertThat(screen.body())
        .contains(ReproductionAdmission.A_BUNDLE_CARRIES_THE_SETUP_IT_REPRODUCES);
  }

  private int theStatusOfTheOnlyRowAt(String path, String run) {
    Integer status = theOnlyRowAt(path, run).httpStatus();
    assertThat(status).as("the status the row at %s carries", path).isNotNull();
    return status;
  }

  private String theWordsTheOnlyImportRowWasRefusedIn() {
    return String.join("", theFactOfTheOnlyImportRow(ActionFacts.REFUSED_BECAUSE));
  }

  private List<String> theRefusedPropertiesOfTheOnlyImportRow() {
    return theFactOfTheOnlyImportRow(ActionFacts.REFUSED_PROPERTIES);
  }

  private List<String> theFactOfTheOnlyImportRow(String fact) {
    List<String> said = theOnlyRowAt(IMPORT, INTO).sanitizedBody().get(fact);
    return said == null ? List.of() : said;
  }

  private AuditRecord theOnlyRowAt(String path, String run) {
    List<AuditRecord> found = new ArrayList<>();
    for (AuditRecord row : trail.of(STRIPE, TestRunId.of(run))) {
      if (path.equals(row.path())) {
        found.add(row);
      }
    }
    assertThat(found).as("the rows this test left at %s of run %s", path, run).hasSize(1);
    return found.getFirst();
  }

  private String aSecret() {
    return MERCHANT + ".secretKey=" + THE_KEY_A_TESTER_TYPES_IN;
  }

  private static String renamed(String document) {
    return document.replace(
        "\"alias\":\"" + FIXTURE_ALIAS + "\"", "\"alias\":\"" + FIXTURE_ALIAS + "-again\"");
  }

  private String pageOf(String run, String key) {
    return get(SCREEN + "?provider=stripe&testRunId=" + run + "&key=" + key);
  }

  private Answer paste(String document, String into, String secrets, String remap, boolean dryRun) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN);
    form.put("into", into);
    form.put("document", document);
    form.put("secrets", secrets);
    form.put("remap", remap);
    if (dryRun) {
      form.put("dryRun", "on");
    }
    return submit(IMPORT, form);
  }

  private static String theDocumentOn(String page) {
    Matcher box = THE_DOCUMENT_BOX.matcher(page);
    assertThat(box.find()).isTrue();
    return unescaped(box.group(1));
  }

  private static String theCellNamingWhatWasWritten(String page) {
    return theCellOn(page, THE_CELL_NAMING_WHAT_WAS_WRITTEN);
  }

  private static String theCellNamingWhatWasOnlyRead(String page) {
    return theCellOn(page, THE_CELL_NAMING_WHAT_WAS_ONLY_READ);
  }

  private static String theCellOn(String page, Pattern cell) {
    Matcher found = cell.matcher(page);
    assertThat(found.find()).isTrue();
    return unescaped(found.group(1));
  }

  private String aPaymentOf(String run) {
    configure(run);
    seedAFixture(run);
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", THE_CARD_THIS_FIXTURE_NAMES);
    form.put("confirm", "true");
    HttpResponse<byte[]> created =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/payment_intents"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + THE_KEY_THE_EXPORTED_RUN_ANSWERS_ON)
                .header("X-Simulator-Test-Run-Id", run)
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    String body = new String(created.body(), UTF_8);
    assertThat(created.statusCode()).as(body).isEqualTo(200);
    return valueIn(body, "id");
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
    assertThat(applied.getStatusCode().value()).as(applied.getBody()).isEqualTo(200);
  }

  private void seedAFixture(String run) {
    Map<String, Object> fixture = new LinkedHashMap<>();
    fixture.put("testRunId", run);
    fixture.put("merchantId", MERCHANT);
    fixture.put("ownerId", "owner-1");
    fixture.put("type", "card");
    fixture.put("alias", FIXTURE_ALIAS);
    fixture.put(
        "card",
        Map.of(
            "brand",
            "visa",
            "last4",
            "4242",
            "expMonth",
            12,
            "expYear",
            2031,
            "funding",
            "credit"));
    fixture.put("providerIds", Map.of("paymentMethodId", THE_CARD_THIS_FIXTURE_NAMES));
    assertThat(
            post("/__simulator/stripe/fixtures/payment-methods", fixture).getStatusCode().value())
        .isEqualTo(201);
  }

  private String theProviderIdOfTheOnlyFixtureOf(String run) {
    List<String> found =
        theValuesOn(
            theBodyOf("/__simulator/stripe/fixtures/payment-methods?testRunId=" + run),
            "paymentMethodId");
    assertThat(found).hasSize(1);
    return found.getFirst();
  }

  private List<String> theFixturesOf(String run) {
    return theValuesOn(
        theBodyOf("/__simulator/stripe/fixtures/payment-methods?testRunId=" + run), "fixtureId");
  }

  private List<String> theRulesOf(String run) {
    return theValuesOn(theBodyOf("/__simulator/stripe/scenarios?testRunId=" + run), "scenarioId");
  }

  private String theBodyOf(String path) {
    ResponseEntity<String> answered =
        http.get()
            .uri(path)
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);
    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    return String.valueOf(answered.getBody());
  }

  private static List<String> theValuesOn(String body, String member) {
    List<String> found = new ArrayList<>();
    Matcher matched = Pattern.compile("\"" + member + "\":\"([^\"]+)\"").matcher(body);
    while (matched.find()) {
      found.add(matched.group(1));
    }
    return found;
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

  private static String valueIn(String body, String member) {
    String opening = "\"" + member + "\":\"";
    int start = body.indexOf(opening);
    int from = start + opening.length();
    return body.substring(from, body.indexOf('"', from));
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
