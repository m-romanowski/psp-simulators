package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioComparison;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class ScenarioDiffUiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "ui-scenario-diff-it";

  private static final String ANOTHER_RUN = "ui-scenario-diff-it-elsewhere";

  private static final String GROW = "grow";

  private static final String AZERICARD = "azericard";

  private static final String SCREEN = "/ui/scenarios/diff";

  private static final String SETTLE = "settleSuspendedTransaction";

  private static final Pattern A_DIFFERENCE =
      Pattern.compile(
          "<td class=\"id property\">([^<]*)</td>\\s*<td class=\"onTheLeft\">(.*?)</td>\\s*"
              + "<td class=\"onTheRight\">(.*?)</td>",
          Pattern.DOTALL);

  private static final Pattern A_FIELD =
      Pattern.compile("name=\"([A-Za-z]+)\"\\s+value=\"([^\"]*)\"");

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
    reset(GROW, ANOTHER_RUN);
    reset(AZERICARD, RUN);
  }

  @AfterEach
  void clearWhatThisTestWrote() {
    reset(GROW, RUN);
    reset(GROW, ANOTHER_RUN);
    reset(AZERICARD, RUN);
  }

  @Test
  void the_screen_names_the_one_field_that_differs_and_what_each_side_says_in_its_own_cell() {
    String declining = written(GROW, aSettlementDecliningWith("101"));
    String declining_otherwise = written(GROW, aSettlementDecliningWith("102"));

    Answer compared = compare(declining, declining_otherwise, null);

    assertThat(compared.status()).isEqualTo(200);
    assertThat(theDifferencesOn(compared.body()))
        .containsExactly(new Difference("action.providerCode", "101", "102"));
  }

  @Test
  void a_field_one_rule_narrows_on_and_the_other_does_not_is_shown_as_naming_none() {
    String narrowed = written(GROW, aSettlementNarrowedToAMerchant());
    String open = written(GROW, aSettlementSucceeding());

    Answer compared = compare(narrowed, open, null);

    assertThat(theDifferencesOn(compared.body()))
        .contains(new Difference("match.merchantId", "merchant-1", "names none"));
  }

  @Test
  void a_header_narrowing_is_shown_by_the_header_it_names_and_its_value_is_never_on_the_page() {
    String signed = written(GROW, aSettlementNarrowedByAHeader());
    String unsigned = written(GROW, aSettlementSucceeding());

    Answer compared = compare(signed, unsigned, null);

    assertThat(theDifferencesOn(compared.body()))
        .contains(new Difference("match.header.X-Signature", "withheld", "names none"));
    assertThat(compared.body()).doesNotContain("a-secret-a-tester-typed");
  }

  @Test
  void two_rules_written_alike_are_reported_as_differing_in_nothing_rather_than_shown_as_empty() {
    String first = written(GROW, aSettlementDecliningWith("101"));
    String second = written(GROW, aSettlementDecliningWith("101"));

    Answer compared = compare(first, second, null);

    assertThat(theDifferencesOn(compared.body())).isEmpty();
    assertThat(compared.body()).contains("say the same thing in every field a scenario holds");
  }

  @Test
  void
      a_rule_of_the_test_run_named_on_the_right_is_selectable_there_and_a_rule_of_this_run_is_not() {
    String ofThisRun = written(GROW, RUN, aSettlementDecliningWith("101"));
    String ofTheOtherRun = written(GROW, ANOTHER_RUN, aSettlementSucceeding());

    String page =
        get(SCREEN + "?provider=" + GROW + "&testRunId=" + RUN + "&rightTestRunId=" + ANOTHER_RUN)
            .body();

    assertThat(theOptionsOf("right", page)).contains(ofTheOtherRun).doesNotContain(ofThisRun);
    assertThat(theOptionsOf("left", page)).contains(ofThisRun).doesNotContain(ofTheOtherRun);
  }

  @Test
  void a_rule_of_the_run_named_on_the_right_is_compared_against_a_rule_of_this_one() {
    String ofThisRun = written(GROW, RUN, aSettlementDecliningWith("101"));
    String ofTheOtherRun = written(GROW, ANOTHER_RUN, aSettlementDecliningWith("102"));

    Answer compared =
        get(
            SCREEN
                + "?provider="
                + GROW
                + "&testRunId="
                + RUN
                + "&left="
                + ofThisRun
                + "&right="
                + ofTheOtherRun
                + "&rightTestRunId="
                + ANOTHER_RUN);

    assertThat(compared.status()).isEqualTo(200);
    assertThat(compared.body())
        .contains("<td class=\"id rightScenario\">" + ofTheOtherRun + "</td>");
    assertThat(theDifferencesOn(compared.body()))
        .containsExactly(new Difference("action.providerCode", "101", "102"));
  }

  @Test
  void two_rules_narrowing_on_one_header_with_different_values_do_not_claim_every_field_is_alike() {
    String one = written(GROW, RUN, aSettlementNarrowedByAHeader("one-secret"));
    String another = written(GROW, RUN, aSettlementNarrowedByAHeader("another-secret"));

    Answer compared = compare(one, another, null);

    assertThat(theDifferencesOn(compared.body())).isEmpty();
    assertThat(compared.body())
        .doesNotContain("say the same thing in every field a scenario holds")
        .contains("said alike on its name alone")
        .contains("Compared by name alone: match.header.X-Signature")
        .contains("and never its value")
        .doesNotContain("one-secret")
        .doesNotContain("another-secret");
  }

  @Test
  void the_screen_names_the_dimensions_it_cannot_compare_beside_every_comparison() {
    String first = written(GROW, aSettlementDecliningWith("101"));
    String second = written(GROW, aSettlementDecliningWith("102"));

    Answer compared = compare(first, second, null);

    assertThat(compared.body())
        .contains("the state a payment comes to rest in")
        .contains("the webhook plan")
        .contains("the state a story stands in right now");
  }

  @Test
  void a_rule_of_another_provider_is_refused_on_the_screen_and_no_difference_is_shown() {
    String onGrow = written(GROW, aSettlementDecliningWith("101"));
    String onAzericard = written(AZERICARD, aSuccessOfAnyRequest());

    Answer refused = compare(onGrow, onAzericard, AZERICARD);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body())
        .contains(
            ScenarioComparison.A_RULE_OF_ONE_PROVIDER_MEANS_NOTHING_TO_ANOTHER.substring(0, 60));
    assertThat(theDifferencesOn(refused.body())).isEmpty();
  }

  @Test
  void a_rule_the_run_no_longer_holds_is_refused_on_the_screen_and_no_difference_is_shown() {
    String written = written(GROW, aSettlementDecliningWith("101"));

    Answer refused = compare(written, "scn_never_written", null);

    assertThat(refused.body()).contains("scn_never_written").contains("on the right");
    assertThat(theDifferencesOn(refused.body())).isEmpty();
  }

  @Test
  void the_copy_form_of_a_side_arrives_holding_every_field_that_rule_was_written_with() {
    String source = written(GROW, aSettlementNarrowedToAMerchant());
    String other = written(GROW, aSettlementSucceeding());

    Map<String, String> held = theCopyFormOf(compare(source, other, null).body(), source);

    assertThat(held)
        .containsEntry("copiedFrom", source)
        .containsEntry("operation", SETTLE)
        .containsEntry("merchantId", "merchant-1")
        .containsEntry("actionType", "FIXED_SUCCESS")
        .containsEntry("priority", "0");
  }

  @Test
  void a_rule_that_narrows_on_a_header_is_offered_as_no_copy_and_the_screen_says_why() {
    String signed = written(GROW, aSettlementNarrowedByAHeader());
    String other = written(GROW, aSettlementSucceeding());

    String page = compare(signed, other, null).body();

    assertThat(page).contains("the copy form is not offered for it");
    assertThat(theCopyFormsOn(page)).hasSize(1);
    assertThat(theCopyFormsOn(page).getFirst()).containsEntry("copiedFrom", other);
  }

  @Test
  void a_copy_written_from_the_screen_is_a_new_rule_and_the_screen_comes_back_comparing_the_two() {
    String source = written(GROW, aSettlementNarrowedToAMerchant());
    String other = written(GROW, aSettlementSucceeding());
    Map<String, String> held = theCopyFormOf(compare(source, other, null).body(), source);

    Answer written = copy(held, "amount", "25.50");

    assertThat(written.status()).isEqualTo(302);
    assertThat(written.location()).contains("left=" + source).contains("right=scn_");
    assertThat(theScenarioIdsOf(GROW)).hasSize(3);
  }

  @Test
  void
      the_copy_the_screen_wrote_differs_from_the_rule_it_was_copied_from_in_the_one_field_changed() {
    String source = written(GROW, aSettlementNarrowedToAMerchant());
    String other = written(GROW, aSettlementSucceeding());
    Map<String, String> held = theCopyFormOf(compare(source, other, null).body(), source);

    Answer written = copy(held, "amount", "25.50");

    Answer compared = get(thePathOf(written.location()));
    assertThat(theDifferencesOn(compared.body()))
        .containsExactly(new Difference("match.amount", "names none", "25.50"));
  }

  @Test
  void a_copy_of_a_rule_the_run_no_longer_holds_is_refused_on_the_screen_and_writes_nothing() {
    String source = written(GROW, aSettlementNarrowedToAMerchant());
    String other = written(GROW, aSettlementSucceeding());
    Map<String, String> held = theCopyFormOf(compare(source, other, null).body(), source);
    held.put("copiedFrom", "scn_never_written");

    Answer refused = copy(held, "amount", "25.50");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("scn_never_written");
    assertThat(theScenarioIdsOf(GROW)).containsExactlyInAnyOrder(source, other);
  }

  @Test
  void a_copy_the_provider_cannot_perform_is_refused_by_its_property_and_writes_nothing() {
    String source = written(GROW, aSettlementNarrowedToAMerchant());
    String other = written(GROW, aSettlementSucceeding());
    Map<String, String> held = theCopyFormOf(compare(source, other, null).body(), source);

    Answer refused = copy(held, "operation", "chargeSomethingGrowNeverExposed");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("This copy was not written").contains("match.operation");
    assertThat(theScenarioIdsOf(GROW)).containsExactlyInAnyOrder(source, other);
  }

  @Test
  void a_copy_whose_priority_is_not_a_number_is_refused_by_that_property_and_writes_nothing() {
    String source = written(GROW, aSettlementNarrowedToAMerchant());
    String other = written(GROW, aSettlementSucceeding());
    Map<String, String> held = theCopyFormOf(compare(source, other, null).body(), source);

    Answer refused = copy(held, "priority", "soon");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("This copy was not written").contains("priority");
    assertThat(theScenarioIdsOf(GROW)).containsExactlyInAnyOrder(source, other);
  }

  @Test
  void the_screen_asks_which_provider_to_compare_on_when_the_request_named_none() {
    Answer asked = get(SCREEN);

    assertThat(asked.status()).isEqualTo(200);
    assertThat(asked.body()).contains("Choose a provider");
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_refused_on_the_screen_and_nothing_is_compared() {
    Answer refused = get(SCREEN + "?provider=nosuchprovider&testRunId=" + RUN);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("unknown provider");
    assertThat(theDifferencesOn(refused.body())).isEmpty();
  }

  @Test
  void a_side_named_on_a_provider_this_simulator_does_not_know_is_refused_rather_than_compared() {
    String source = written(GROW, aSettlementDecliningWith("101"));

    Answer refused = compare(source, source, "nosuchprovider");

    assertThat(refused.body()).contains("unknown provider");
    assertThat(theDifferencesOn(refused.body())).isEmpty();
  }

  @Test
  void a_test_run_of_a_shape_this_simulator_refuses_on_the_right_is_refused_rather_than_compared() {
    String source = written(GROW, aSettlementDecliningWith("101"));

    Answer refused =
        get(
            SCREEN
                + "?provider="
                + GROW
                + "&testRunId="
                + RUN
                + "&left="
                + source
                + "&right="
                + source
                + "&rightTestRunId=a%20run%20named%20with%20a%20space");

    assertThat(refused.body()).contains("testRunId");
    assertThat(theDifferencesOn(refused.body())).isEmpty();
  }

  @Test
  void a_copy_posted_before_a_provider_was_chosen_writes_nothing_and_asks_which_one() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "");
    form.put("testRunId", RUN);
    form.put("copiedFrom", "scn_never_written");

    Answer answered = submit(SCREEN + "/copy", form);

    assertThat(answered.status()).isEqualTo(200);
    assertThat(answered.body()).contains("Choose a provider");
    assertThat(theScenarioIdsOf(GROW)).isEmpty();
  }

  @Test
  void a_copy_whose_story_moves_nowhere_is_rejected_on_the_screen_and_writes_nothing() {
    String source = written(GROW, aSettlementNarrowedToAMerchant());
    String other = written(GROW, aSettlementSucceeding());
    Map<String, String> held = theCopyFormOf(compare(source, other, null).body(), source);
    held.put("story", "a story of one state");
    held.put("whenState", "Same");

    Answer refused = copy(held, "thenState", "Same");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("thenState");
    assertThat(theScenarioIdsOf(GROW)).containsExactlyInAnyOrder(source, other);
  }

  @Test
  void a_screen_asked_for_one_side_only_compares_nothing_and_says_two_rules_are_named_first() {
    String source = written(GROW, aSettlementDecliningWith("101"));

    Answer asked = get(SCREEN + "?provider=" + GROW + "&testRunId=" + RUN + "&left=" + source);

    assertThat(theDifferencesOn(asked.body())).isEmpty();
    assertThat(asked.body()).contains("it compares nothing");
  }

  private record Difference(String property, String onTheLeft, String onTheRight) {}

  private static List<Difference> theDifferencesOn(String page) {
    List<Difference> differences = new ArrayList<>();
    Matcher rows = A_DIFFERENCE.matcher(page);
    while (rows.find()) {
      differences.add(
          new Difference(rows.group(1).strip(), stripped(rows.group(2)), stripped(rows.group(3))));
    }
    return differences;
  }

  private static String stripped(String cell) {
    return cell.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
  }

  private static Map<String, String> theCopyFormOf(String page, String source) {
    for (Map<String, String> form : theCopyFormsOn(page)) {
      if (source.equals(form.get("copiedFrom"))) {
        return form;
      }
    }
    throw new AssertionError("no copy form on this page was taken from " + source);
  }

  private static List<Map<String, String>> theCopyFormsOn(String page) {
    List<Map<String, String>> forms = new ArrayList<>();
    Matcher blocks =
        Pattern.compile(
                "<form class=\"filters\" method=\"post\" action=\"/ui/scenarios/diff/copy[^\"]*\">(.*?)</form>",
                Pattern.DOTALL)
            .matcher(page);
    while (blocks.find()) {
      Map<String, String> fields = new LinkedHashMap<>();
      Matcher named = A_FIELD.matcher(blocks.group(1));
      while (named.find()) {
        fields.put(named.group(1), named.group(2));
      }
      forms.add(fields);
    }
    return forms;
  }

  private Answer copy(Map<String, String> held, String field, String value) {
    Map<String, String> form = new LinkedHashMap<>(held);
    form.put(field, value);
    return submit(SCREEN + "/copy?provider=" + GROW + "&testRunId=" + RUN, form);
  }

  private Answer compare(String left, String right, String rightProvider) {
    StringBuilder uri =
        new StringBuilder(SCREEN + "?provider=" + GROW + "&testRunId=" + RUN)
            .append("&left=")
            .append(left)
            .append("&right=")
            .append(right);
    if (rightProvider != null) {
      uri.append("&rightProvider=").append(rightProvider);
    }
    return get(uri.toString());
  }

  private static Map<String, Object> aSettlementDecliningWith(String providerCode) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", SETTLE));
    rule.put("action", Map.of("type", "TERMINAL_FAILURE", "providerCode", providerCode));
    return rule;
  }

  private static Map<String, Object> aSettlementSucceeding() {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", SETTLE));
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    return rule;
  }

  private static Map<String, Object> aSettlementNarrowedToAMerchant() {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", SETTLE, "merchantId", "merchant-1"));
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    return rule;
  }

  private static Map<String, Object> aSettlementNarrowedByAHeader() {
    return aSettlementNarrowedByAHeader("a-secret-a-tester-typed");
  }

  private static Map<String, Object> aSettlementNarrowedByAHeader(String value) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", SETTLE, "header", Map.of("X-Signature", value)));
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    return rule;
  }

  private static List<String> theOptionsOf(String select, String page) {
    Matcher block =
        Pattern.compile(
                "<select id=\"" + select + "\" name=\"[A-Za-z]+\"[^>]*>(.*?)</select>",
                Pattern.DOTALL)
            .matcher(page);
    assertThat(block.find()).isTrue();
    List<String> values = new ArrayList<>();
    Matcher option = Pattern.compile("<option\\s+value=\"([^\"]*)\"").matcher(block.group(1));
    while (option.find()) {
      if (!option.group(1).isEmpty()) {
        values.add(option.group(1));
      }
    }
    return values;
  }

  private static Map<String, Object> aSuccessOfAnyRequest() {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    return rule;
  }

  private String written(String provider, Map<String, Object> rule) {
    return written(provider, RUN, rule);
  }

  private String written(String provider, String testRunId, Map<String, Object> rule) {
    Map<String, Object> body = new LinkedHashMap<>(rule);
    body.put("testRunId", testRunId);
    Map<String, Object> answered =
        http.post()
            .uri("/__simulator/" + provider + "/scenarios")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return String.valueOf(answered.get("scenarioId"));
  }

  private List<String> theScenarioIdsOf(String provider) {
    List<String> ids = new ArrayList<>();
    for (Map<String, Object> rule :
        http.get()
            .uri("/__simulator/" + provider + "/scenarios?testRunId=" + RUN)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {})) {
      ids.add(String.valueOf(rule.get("scenarioId")));
    }
    return ids;
  }

  private void reset(String provider, String testRunId) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", testRunId))
        .retrieve()
        .toBodilessEntity();
  }

  private String thePathOf(String location) {
    return URI.create(location).getPath() + "?" + URI.create(location).getQuery();
  }

  private Answer get(String path) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET());
    return answerOf(response);
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return answerOf(response);
  }

  private static Answer answerOf(HttpResponse<byte[]> response) {
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
