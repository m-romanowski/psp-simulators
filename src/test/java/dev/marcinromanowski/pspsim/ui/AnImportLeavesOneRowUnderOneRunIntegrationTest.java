package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class AnImportLeavesOneRowUnderOneRunIntegrationTest extends PostgresBackedTest {

  private static final ProviderId AZERICARD = ProviderId.of("azericard");

  private static final String PROVIDER = "azericard";

  private static final String RUN = "import-rows-it";

  private static final String INTO = "import-rows-it-2";

  private static final String A_TARGET_NO_NAMESPACE_CAN_HOLD = "not a run!";

  private static final String THE_TARGET_A_TESTER_LEAVES_UNNAMED = "";

  private static final String THE_RUN_BUNDLE_SCREEN = "/ui/run-bundle/import";

  private static final String THE_REPRODUCTION_SCREEN = "/ui/repro-bundle/import";

  private static final String THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS =
      "/__simulator/azericard/run-bundle/import";

  private static final String THE_REPRODUCTION_IMPORT_A_SUITE_CALLS =
      "/__simulator/azericard/repro-bundle/import";

  private static final String A_DOCUMENT_NO_BOX_CAN_READ = "";

  private static final String A_DOCUMENT_THE_ADMISSION_TURNS_AWAY = "{\"provider\":\"azericard\"}";

  private static final String A_REPRODUCTION_WHOSE_SETUP_THE_ADMISSION_TURNS_AWAY =
      "{\"setup\":{\"provider\":\"azericard\"}}";

  private static final String A_REMAPPING_LINE_NO_BOX_CAN_READ = "tok_nothing";

  private static final String THE_SECRET_FIELD_A_TESTER_NAMED = "merchant-1.apiSecret";

  private static final String A_SECRET_A_TESTER_TYPES_IN =
      THE_SECRET_FIELD_A_TESTER_NAMED + "=a-key-no-row-of-this-trail-carries";

  private static final String THE_APPLIED_OUTCOME = "applied";

  private static final String TURNED_AWAY_BY_THE_ADMISSION = "turned away by the admission";

  private static final String REJECTED_BY_THE_ADMISSION = "rejected by the admission";

  private static final String TURNED_AWAY_BY_A_BOX = "turned away by a box";

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
    forgetBothRuns();
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    forgetBothRuns();
  }

  @Test
  void every_outcome_of_an_import_on_the_run_bundle_screen_leaves_its_row_under_the_run_it_named() {
    String document = theRunAsABundle();

    Map<String, List<String>> took = new LinkedHashMap<>();
    took.put(
        THE_APPLIED_OUTCOME,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_SCREEN, () -> pasteIntoTheRunBundleScreen(document, INTO)));
    took.put(
        TURNED_AWAY_BY_THE_ADMISSION,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_SCREEN,
            () -> pasteIntoTheRunBundleScreen(A_DOCUMENT_THE_ADMISSION_TURNS_AWAY, INTO)));
    took.put(
        TURNED_AWAY_BY_A_BOX,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_SCREEN,
            () -> pasteIntoTheRunBundleScreen(A_DOCUMENT_NO_BOX_CAN_READ, INTO)));

    assertThat(took)
        .containsOnlyKeys(THE_APPLIED_OUTCOME, TURNED_AWAY_BY_THE_ADMISSION, TURNED_AWAY_BY_A_BOX)
        .allSatisfy(
            (outcome, runs) ->
                assertThat(runs)
                    .as(
                        "a tester asking afterwards what happened to an import they sent into"
                            + " another run reads one trail whatever the outcome was, and this"
                            + " import was %s",
                        outcome)
                    .containsExactly(INTO));
  }

  @Test
  void
      every_outcome_of_an_import_on_the_reproduction_screen_leaves_its_row_under_the_run_it_named() {
    String document = "{\"setup\":" + theRunAsABundle() + "}";

    Map<String, List<String>> took = new LinkedHashMap<>();
    took.put(
        THE_APPLIED_OUTCOME,
        theRunsThatTookANewRowAt(
            THE_REPRODUCTION_SCREEN, () -> pasteIntoTheReproductionScreen(document, INTO, "")));
    took.put(
        TURNED_AWAY_BY_THE_ADMISSION,
        theRunsThatTookANewRowAt(
            THE_REPRODUCTION_SCREEN,
            () ->
                pasteIntoTheReproductionScreen(
                    A_REPRODUCTION_WHOSE_SETUP_THE_ADMISSION_TURNS_AWAY, INTO, "")));
    took.put(
        REJECTED_BY_THE_ADMISSION,
        theRunsThatTookANewRowAt(
            THE_REPRODUCTION_SCREEN,
            () -> pasteIntoTheReproductionScreen(A_DOCUMENT_THE_ADMISSION_TURNS_AWAY, INTO, "")));
    took.put(
        TURNED_AWAY_BY_A_BOX,
        theRunsThatTookANewRowAt(
            THE_REPRODUCTION_SCREEN,
            () -> pasteIntoTheReproductionScreen(A_DOCUMENT_NO_BOX_CAN_READ, INTO, "")));

    assertThat(took)
        .containsOnlyKeys(
            THE_APPLIED_OUTCOME,
            TURNED_AWAY_BY_THE_ADMISSION,
            REJECTED_BY_THE_ADMISSION,
            TURNED_AWAY_BY_A_BOX)
        .allSatisfy(
            (outcome, runs) ->
                assertThat(runs)
                    .as(
                        "a reproduction import files its row the way a run import does, and this"
                            + " one was %s",
                        outcome)
                    .containsExactly(INTO));
  }

  @Test
  void every_outcome_of_an_import_a_suite_posts_leaves_its_row_under_the_run_its_body_named() {
    String document = theRunAsABundle();

    Map<String, List<String>> took = new LinkedHashMap<>();
    took.put(
        THE_APPLIED_OUTCOME,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
            () ->
                post(
                    THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
                    "{\"testRunId\":\"" + INTO + "\",\"bundle\":" + document + "}")));
    took.put(
        TURNED_AWAY_BY_THE_ADMISSION,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
            () ->
                post(
                    THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
                    "{\"testRunId\":\""
                        + INTO
                        + "\",\"bundle\":"
                        + A_DOCUMENT_THE_ADMISSION_TURNS_AWAY
                        + "}")));

    assertThat(took)
        .containsOnlyKeys(THE_APPLIED_OUTCOME, TURNED_AWAY_BY_THE_ADMISSION)
        .allSatisfy(
            (outcome, runs) ->
                assertThat(runs)
                    .as(
                        "the surface a suite drives files an import's row under the run the body"
                            + " names, and this import was %s",
                        outcome)
                    .containsExactly(INTO));
  }

  @Test
  void an_import_whose_target_no_namespace_can_hold_leaves_its_row_under_the_reserved_run() {
    Map<String, List<String>> took = new LinkedHashMap<>();
    took.put(
        THE_RUN_BUNDLE_SCREEN,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_SCREEN,
            () -> pasteIntoTheRunBundleScreen(theRunAsABundle(), A_TARGET_NO_NAMESPACE_CAN_HOLD)));
    took.put(
        THE_REPRODUCTION_SCREEN,
        theRunsThatTookANewRowAt(
            THE_REPRODUCTION_SCREEN,
            () ->
                pasteIntoTheReproductionScreen(
                    "{\"setup\":" + theRunAsABundle() + "}", A_TARGET_NO_NAMESPACE_CAN_HOLD, "")));
    took.put(
        THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
            () ->
                post(
                    THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
                    "{\"testRunId\":\""
                        + A_TARGET_NO_NAMESPACE_CAN_HOLD
                        + "\",\"bundle\":"
                        + theRunAsABundle()
                        + "}")));
    took.put(
        THE_REPRODUCTION_IMPORT_A_SUITE_CALLS,
        theRunsThatTookANewRowAt(
            THE_REPRODUCTION_IMPORT_A_SUITE_CALLS,
            () ->
                post(
                    THE_REPRODUCTION_IMPORT_A_SUITE_CALLS,
                    "{\"testRunId\":\""
                        + A_TARGET_NO_NAMESPACE_CAN_HOLD
                        + "\",\"bundle\":{\"setup\":"
                        + theRunAsABundle()
                        + "}}")));

    assertThat(took)
        .containsOnlyKeys(
            THE_RUN_BUNDLE_SCREEN,
            THE_REPRODUCTION_SCREEN,
            THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
            THE_REPRODUCTION_IMPORT_A_SUITE_CALLS)
        .allSatisfy(
            (surface, runs) ->
                assertThat(runs)
                    .as(
                        "the run this import named is a name no namespace can hold, so neither the"
                            + " run it names nor the run the request was made from holds its row,"
                            + " on %s",
                        surface)
                    .containsExactly(TestRunId.UNKNOWN.value()));
  }

  @Test
  void an_import_naming_no_target_leaves_its_row_under_the_run_the_screen_was_used_on() {
    Map<String, List<String>> took = new LinkedHashMap<>();
    took.put(
        THE_RUN_BUNDLE_SCREEN,
        theRunsThatTookANewRowAt(
            THE_RUN_BUNDLE_SCREEN,
            () ->
                pasteIntoTheRunBundleScreen(
                    A_DOCUMENT_THE_ADMISSION_TURNS_AWAY, THE_TARGET_A_TESTER_LEAVES_UNNAMED)));
    took.put(
        THE_REPRODUCTION_SCREEN,
        theRunsThatTookANewRowAt(
            THE_REPRODUCTION_SCREEN,
            () ->
                pasteIntoTheReproductionScreen(
                    A_REPRODUCTION_WHOSE_SETUP_THE_ADMISSION_TURNS_AWAY,
                    THE_TARGET_A_TESTER_LEAVES_UNNAMED,
                    "")));

    assertThat(took)
        .containsOnlyKeys(THE_RUN_BUNDLE_SCREEN, THE_REPRODUCTION_SCREEN)
        .allSatisfy(
            (surface, runs) ->
                assertThat(runs)
                    .as(
                        "an import naming no target writes into the run the screen is on, so that"
                            + " is the run its row belongs under, on %s",
                        surface)
                    .containsExactly(RUN));
  }

  @Test
  void a_row_the_reserved_run_holds_still_says_which_target_a_tester_typed() {
    List<AuditRecord> left =
        theNewRowsAt(
            THE_RUN_BUNDLE_SCREEN,
            TestRunId.UNKNOWN,
            () ->
                pasteIntoTheRunBundleScreen(
                    A_DOCUMENT_THE_ADMISSION_TURNS_AWAY, A_TARGET_NO_NAMESPACE_CAN_HOLD));

    assertThat(left)
        .hasSize(1)
        .allSatisfy(
            row ->
                assertThat(row.sanitizedBody())
                    .as(
                        "no namespace can hold the run this import named, so the run the row is"
                            + " filed under cannot say which target a tester typed and the row"
                            + " itself does")
                    .containsEntry(
                        ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO,
                        List.of(A_TARGET_NO_NAMESPACE_CAN_HOLD)));
  }

  @Test
  void a_row_the_reserved_run_holds_says_which_target_a_suite_named_too() {
    List<AuditRecord> left =
        theNewRowsAt(
            THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
            TestRunId.UNKNOWN,
            () ->
                post(
                    THE_RUN_BUNDLE_IMPORT_A_SUITE_CALLS,
                    "{\"testRunId\":\""
                        + A_TARGET_NO_NAMESPACE_CAN_HOLD
                        + "\",\"bundle\":"
                        + theRunAsABundle()
                        + "}"));

    assertThat(left)
        .hasSize(1)
        .allSatisfy(
            row ->
                assertThat(row.sanitizedBody())
                    .as(
                        "the reserved run holds the rows of every surface whose target no"
                            + " namespace could hold, so a suite reading them needs the target"
                            + " named on the row for the same reason a tester does")
                    .containsEntry(
                        ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO,
                        List.of(A_TARGET_NO_NAMESPACE_CAN_HOLD)));
  }

  @Test
  void
      every_refusal_of_an_import_records_the_target_the_dry_run_and_the_secret_names_it_was_given() {
    pasteIntoTheRunBundleScreen(A_DOCUMENT_NO_BOX_CAN_READ, INTO, A_SECRET_A_TESTER_TYPES_IN);
    pasteIntoTheRunBundleScreen(
        A_DOCUMENT_THE_ADMISSION_TURNS_AWAY, INTO, A_SECRET_A_TESTER_TYPES_IN);
    pasteIntoTheReproductionScreen(
        A_DOCUMENT_NO_BOX_CAN_READ, INTO, "", A_SECRET_A_TESTER_TYPES_IN);
    pasteIntoTheReproductionScreen(
        A_DOCUMENT_THE_ADMISSION_TURNS_AWAY, INTO, "", A_SECRET_A_TESTER_TYPES_IN);

    Map<String, List<AuditRecord>> refused = new LinkedHashMap<>();
    refused.put(THE_RUN_BUNDLE_SCREEN, theRowsAt(THE_RUN_BUNDLE_SCREEN, TestRunId.of(INTO)));
    refused.put(THE_REPRODUCTION_SCREEN, theRowsAt(THE_REPRODUCTION_SCREEN, TestRunId.of(INTO)));

    assertThat(refused)
        .containsOnlyKeys(THE_RUN_BUNDLE_SCREEN, THE_REPRODUCTION_SCREEN)
        .allSatisfy(
            (screen, rows) ->
                assertThat(rows)
                    .as("the import turned away by a box and the one turned away by the admission")
                    .hasSize(2)
                    .allSatisfy(
                        row ->
                            assertThat(row.sanitizedBody())
                                .as(
                                    "a reader of a refused import's row on %s is told what was"
                                        + " asked, and a box turning it away before the provider"
                                        + " saw it leaves the same reader the same questions",
                                    screen)
                                .containsEntry(
                                    ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO, List.of(INTO))
                                .containsEntry(
                                    ActionFacts.AN_IMPORT_WRITES_NOTHING,
                                    List.of(String.valueOf(false)))
                                .containsEntry(
                                    ActionFacts.THE_SECRETS_AN_IMPORT_WAS_GIVEN,
                                    List.of(THE_SECRET_FIELD_A_TESTER_NAMED))
                                .containsKey(ActionFacts.REFUSED_BECAUSE)));
  }

  @Test
  void a_remapping_line_no_box_could_read_is_refused_by_the_name_the_screen_draws_that_box_under() {
    pasteIntoTheReproductionScreen(
        A_REPRODUCTION_WHOSE_SETUP_THE_ADMISSION_TURNS_AWAY,
        INTO,
        A_REMAPPING_LINE_NO_BOX_CAN_READ,
        "");

    AuditRecord row = theOnlyRowAt(THE_REPRODUCTION_SCREEN, INTO);

    assertThat(String.join("", row.sanitizedBody().get(ActionFacts.REFUSED_BECAUSE)))
        .as(
            "the words a tester reads name a box drawn on the screen they typed into, and the"
                + " line of it this refusal is about")
        .startsWith(ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO + " line 1:");
  }

  private List<AuditRecord> theNewRowsAt(String path, TestRunId run, Runnable submitted) {
    List<String> before = new ArrayList<>();
    for (AuditRecord row : theRowsAt(path, run)) {
      before.add(row.auditId());
    }
    submitted.run();
    List<AuditRecord> left = new ArrayList<>();
    for (AuditRecord row : theRowsAt(path, run)) {
      if (!before.contains(row.auditId())) {
        left.add(row);
      }
    }
    return List.copyOf(left);
  }

  private List<String> theRunsThatTookANewRowAt(String path, Runnable submitted) {
    Map<String, Integer> before = theRowsEachRunHoldsAt(path);
    submitted.run();
    Map<String, Integer> after = theRowsEachRunHoldsAt(path);
    List<String> grown = new ArrayList<>();
    after.forEach(
        (run, rows) -> {
          if (rows > before.getOrDefault(run, 0)) {
            grown.add(run);
          }
        });
    return List.copyOf(grown);
  }

  private Map<String, Integer> theRowsEachRunHoldsAt(String path) {
    Map<String, Integer> held = new LinkedHashMap<>();
    for (TestRunId run : List.of(TestRunId.of(RUN), TestRunId.of(INTO), TestRunId.UNKNOWN)) {
      held.put(run.value(), theRowsAt(path, run).size());
    }
    return held;
  }

  private AuditRecord theOnlyRowAt(String path, String run) {
    List<AuditRecord> found = theRowsAt(path, TestRunId.of(run));
    assertThat(found).as("the rows this test left at %s of run %s", path, run).hasSize(1);
    return found.getFirst();
  }

  private List<AuditRecord> theRowsAt(String path, TestRunId run) {
    List<AuditRecord> found = new ArrayList<>();
    for (AuditRecord row : trail.of(AZERICARD, run)) {
      if (path.equals(row.path())) {
        found.add(row);
      }
    }
    return List.copyOf(found);
  }

  private String theRunAsABundle() {
    ResponseEntity<String> exported =
        http.get()
            .uri("/__simulator/" + PROVIDER + "/run-bundle?testRunId=" + RUN)
            .retrieve()
            .onStatus(status -> true, (request, response) -> {})
            .toEntity(String.class);
    assertThat(exported.getStatusCode().value()).as(exported.getBody()).isEqualTo(200);
    return String.valueOf(exported.getBody());
  }

  private void pasteIntoTheRunBundleScreen(String document, String into) {
    pasteIntoTheRunBundleScreen(document, into, "");
  }

  private void pasteIntoTheRunBundleScreen(String document, String into, String secrets) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", PROVIDER);
    form.put("testRunId", RUN);
    form.put("into", into);
    form.put("document", document);
    form.put("secrets", secrets);
    assertThat(submit(THE_RUN_BUNDLE_SCREEN, form))
        .as("the screen answered the tester rather than failing")
        .isEqualTo(200);
  }

  private void pasteIntoTheReproductionScreen(String document, String into, String remap) {
    pasteIntoTheReproductionScreen(document, into, remap, "");
  }

  private void pasteIntoTheReproductionScreen(
      String document, String into, String remap, String secrets) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", PROVIDER);
    form.put("testRunId", RUN);
    form.put("into", into);
    form.put("document", document);
    form.put("secrets", secrets);
    form.put("remap", remap);
    assertThat(submit(THE_REPRODUCTION_SCREEN, form))
        .as("the screen answered the tester rather than failing")
        .isEqualTo(200);
  }

  private void post(String path, String body) {
    http.method(HttpMethod.POST)
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toBodilessEntity();
  }

  private void forgetBothRuns() {
    for (String run : List.of(RUN, INTO)) {
      http.method(HttpMethod.POST)
          .uri("/__simulator/" + PROVIDER + "/reset")
          .contentType(MediaType.APPLICATION_JSON)
          .body("{\"testRunId\":\"" + run + "\"}")
          .retrieve()
          .onStatus(status -> true, (request, response) -> {})
          .toBodilessEntity();
    }
  }

  private int submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> answered =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return answered.statusCode();
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
}
