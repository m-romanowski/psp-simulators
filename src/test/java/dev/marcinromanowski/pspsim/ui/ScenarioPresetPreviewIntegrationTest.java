package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class ScenarioPresetPreviewIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-preset-preview-it");

  private static final TestRunId OTHER_RUN = TestRunId.of("ui-preset-preview-it-2");

  private static final String SCREEN = "/ui/scenarios";

  private static final String GROW = "grow";

  private static final String AZERICARD = "azericard";

  private static final String WORLDLINE = "worldline";

  private static final String CANCEL_HOLD = "cancel-hold-success";

  private static final String SETTLE = "settleSuspendedTransaction";

  private static final String REFUND = "refundTransaction";

  private static final Pattern A_RULE_IN_WORDS = Pattern.compile("<li>([^<]*)</li>");

  private static final Pattern THE_DOCUMENT_BOX =
      Pattern.compile(
          "<textarea class=\"document\" readonly[^>]*>(.*?)</textarea>", Pattern.DOTALL);

  @LocalServerPort private int port;

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
    reset(GROW, RUN);
    reset(GROW, OTHER_RUN);
    reset(AZERICARD, RUN);
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    reset(GROW, RUN);
    reset(GROW, OTHER_RUN);
    reset(AZERICARD, RUN);
  }

  @Test
  void expanding_a_preset_without_confirming_it_shows_the_rules_and_writes_none_of_them() {
    Answer previewed = expand(GROW, RUN, CANCEL_HOLD, null);

    assertThat(previewed.status()).isEqualTo(200);
    assertThat(previewed.body()).contains("not yet written").contains(SETTLE).contains(REFUND);
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  @Test
  void confirming_the_expansion_writes_both_rules_the_preview_showed() {
    expand(GROW, RUN, CANCEL_HOLD, null);

    Answer written = expand(GROW, RUN, CANCEL_HOLD, "yes");

    assertThat(written.status()).isEqualTo(302);
    assertThat(theOperationsOf(GROW, RUN)).containsExactlyInAnyOrder(SETTLE, REFUND);
  }

  @Test
  void the_priority_a_preview_showed_is_the_priority_confirming_it_writes() {
    expand(GROW, RUN, CANCEL_HOLD, null, "7");

    expand(GROW, RUN, CANCEL_HOLD, "yes", "7");

    assertThat(theRulesOf(GROW, RUN))
        .isNotEmpty()
        .allSatisfy(rule -> assertThat(rule.get("priority")).isEqualTo(7));
  }

  @Test
  void the_screen_says_what_each_ready_made_flow_writes_before_one_is_chosen() {
    String page = theScreenOf(GROW);

    assertThat(page)
        .contains("cancel-hold-success · 2 rules")
        .contains("webhook-out-of-order · this provider cannot express it")
        .contains("<li>settleSuspendedTransaction succeeds</li>")
        .contains("<li>refundTransaction succeeds</li>")
        .contains("a grow payment process reaches one outcome and carries one callback");
  }

  @Test
  void a_flow_of_rules_that_wait_for_the_calls_they_narrow_on_claims_no_order_between_them() {
    String page = theScreenOf(AZERICARD);

    assertThat(page)
        .as(
            "a sentence joining alternative reversals with \"then\" tells a tester to send both in"
                + " that order, and the second is refused because the first reversed the whole"
                + " payment; one line per rule claims no order at all")
        .contains("<li>TRTYPE=22 succeeds</li>")
        .contains("<li>TRTYPE=24 succeeds</li>")
        .doesNotContain("TRTYPE=22 succeeds, then");
    assertThat(theRulesListedOn(page))
        .as("no line this screen reads a flow back in joins two rules into a sequence")
        .isNotEmpty()
        .allSatisfy(line -> assertThat(line).doesNotContain(", then "));
  }

  @Test
  void a_step_of_a_story_says_what_the_story_stands_on_so_two_providers_do_not_read_alike() {
    assertThat(theRulesListedOn(theScreenOf(GROW)))
        .anySatisfy(
            line ->
                assertThat(line)
                    .contains("kept per the flow of one call")
                    .contains("stands at " + ScenarioStory.INITIAL_STATE));
    assertThat(theRulesListedOn(theScreenOf(WORLDLINE)))
        .anySatisfy(line -> assertThat(line).contains("kept per the whole test run"));
  }

  private String theScreenOf(String provider) {
    return http.get()
        .uri(SCREEN + "?provider=" + provider + "&testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }

  private static List<String> theRulesListedOn(String page) {
    Matcher listed = A_RULE_IN_WORDS.matcher(page);
    List<String> read = new ArrayList<>();
    while (listed.find()) {
      read.add(unescaped(listed.group(1)));
    }
    return List.copyOf(read);
  }

  @Test
  void one_preset_previews_into_this_provider_s_operations_and_into_another_provider_s_own() {
    Answer onGrow = expand(GROW, RUN, CANCEL_HOLD, null);
    Answer onAzericard = expand(AZERICARD, RUN, CANCEL_HOLD, null);

    assertThat(theOperationsPreviewedOn(onGrow.body())).containsExactly(SETTLE, REFUND);
    assertThat(theOperationsPreviewedOn(onAzericard.body()))
        .isNotEqualTo(theOperationsPreviewedOn(onGrow.body()))
        .allSatisfy(operation -> assertThat(operation).startsWith("TRTYPE="));
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
    assertThat(theRulesOf(AZERICARD, RUN)).isEmpty();
  }

  @Test
  void the_document_a_preview_shows_writes_the_same_operations_when_it_is_pasted_back() {
    String document = theDocumentOn(expand(GROW, RUN, CANCEL_HOLD, null).body());

    Answer taken = paste(GROW, RUN, document);

    assertThat(taken.status()).isEqualTo(302);
    assertThat(theOperationsOf(GROW, RUN)).containsExactlyInAnyOrder(SETTLE, REFUND);
  }

  @Test
  void a_document_previewed_for_one_run_writes_its_rules_into_the_run_the_screen_is_on() {
    String document = theDocumentOn(expand(GROW, RUN, CANCEL_HOLD, null).body());

    paste(GROW, OTHER_RUN, document);

    assertThat(theOperationsOf(GROW, OTHER_RUN)).containsExactlyInAnyOrder(SETTLE, REFUND);
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  @Test
  void a_document_this_provider_did_not_expand_is_refused_on_the_screen_by_the_provider_property() {
    String document = theDocumentOn(expand(GROW, RUN, CANCEL_HOLD, null).body());

    Answer refused = paste(AZERICARD, RUN, document);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("provider");
    assertThat(theRulesOf(AZERICARD, RUN)).isEmpty();
  }

  @Test
  void a_document_posted_before_a_provider_was_chosen_writes_nothing_and_asks_which_one() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "");
    form.put("testRunId", RUN.value());
    form.put("document", "{}");

    Answer answered = submit(SCREEN + "/import", form);

    assertThat(answered.status()).isEqualTo(200);
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  @Test
  void an_empty_document_box_is_refused_by_the_property_and_writes_nothing() {
    Answer refused = paste(GROW, RUN, "   ");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains(ScenarioScreen.NOTHING_WAS_PASTED);
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  @Test
  void a_box_holding_something_other_than_a_document_is_refused_and_writes_nothing() {
    Answer refused = paste(GROW, RUN, "not a document at all");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains(ScenarioScreen.THE_DOCUMENT_IS_NOT_JSON);
    assertThat(theRulesOf(GROW, RUN)).isEmpty();
  }

  private Answer expand(
      String provider, TestRunId testRunId, String preset, @Nullable String confirm) {
    return expand(provider, testRunId, preset, confirm, "0");
  }

  private Answer expand(
      String provider,
      TestRunId testRunId,
      String preset,
      @Nullable String confirm,
      String priority) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", provider);
    form.put("testRunId", testRunId.value());
    form.put("preset", preset);
    form.put("priority", priority);
    if (confirm != null) {
      form.put("confirm", confirm);
    }
    return submit(SCREEN + "/preset", form);
  }

  private Answer paste(String provider, TestRunId testRunId, String document) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", provider);
    form.put("testRunId", testRunId.value());
    form.put("document", document);
    return submit(SCREEN + "/import", form);
  }

  private static List<String> theOperationsPreviewedOn(String page) {
    Matcher panel =
        Pattern.compile("not yet written(.*?)The same rules as JSON", Pattern.DOTALL).matcher(page);
    assertThat(panel.find()).isTrue();
    Matcher cells = Pattern.compile("<td>([^<]*)</td>").matcher(panel.group(1));
    List<String> operations = new ArrayList<>();
    while (cells.find()) {
      String cell = cells.group(1).strip();
      if (cell.equals(SETTLE) || cell.equals(REFUND) || cell.startsWith("TRTYPE=")) {
        operations.add(cell);
      }
    }
    return operations;
  }

  private static String theDocumentOn(String page) {
    Matcher box = THE_DOCUMENT_BOX.matcher(page);
    assertThat(box.find()).isTrue();
    return unescaped(box.group(1));
  }

  private static String unescaped(String markup) {
    return markup
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private List<String> theOperationsOf(String provider, TestRunId testRunId) {
    List<String> operations = new ArrayList<>();
    for (Map<String, Object> rule : theRulesOf(provider, testRunId)) {
      operations.add(String.valueOf(rule.get("operation")));
    }
    return operations;
  }

  private List<Map<String, Object>> theRulesOf(String provider, TestRunId testRunId) {
    return http.get()
        .uri("/__simulator/" + provider + "/scenarios?testRunId=" + testRunId.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private void reset(String provider, TestRunId testRunId) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", testRunId.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
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

  private record Answer(int status, String body, String location) {}
}
