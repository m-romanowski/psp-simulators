package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class ScenarioDiffApiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "scenario-diff-it";

  private static final String OTHER_RUN = "scenario-diff-it-2";

  private static final String GROW = "grow";

  private static final String AZERICARD = "azericard";

  private static final String SETTLE = "settleSuspendedTransaction";

  private static final String REFUND = "refundTransaction";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
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
  void two_rules_written_alike_are_compared_and_no_field_of_either_is_reported_as_differing() {
    String first = written(GROW, RUN, aSettlementDecliningWith("101"));
    String second = written(GROW, RUN, aSettlementDecliningWith("101"));

    Map<String, Object> compared = compared(GROW, RUN, first, second);

    assertThat(differencesOf(compared)).isEmpty();
    assertThat(alikeOf(compared)).contains("match.operation", "action.providerCode", "action.type");
  }

  @Test
  void
      a_header_both_rules_narrow_on_is_answered_as_alike_on_its_name_with_the_marker_that_says_so() {
    String one = written(GROW, RUN, aSettlementNarrowedByAHeader("one-secret"));
    String another = written(GROW, RUN, aSettlementNarrowedByAHeader("another-secret"));

    Map<String, Object> compared = compared(GROW, RUN, one, another);

    assertThat(differencesOf(compared)).isEmpty();
    assertThat(alikeOf(compared)).contains("match.header.X-Signature");
    assertThat(alikeOnTheirNameAloneOf(compared)).containsExactly("match.header.X-Signature");
    assertThat(String.valueOf(compared.get("whatAPropertySaidAlikeOnItsNameDoesNotCompare")))
        .contains("compares the header's name and never its value")
        .contains("match a request the other refuses");
    assertThat(String.valueOf(compared))
        .doesNotContain("one-secret")
        .doesNotContain("another-secret");
  }

  @Test
  void a_comparison_that_withheld_no_value_answers_with_no_marker_and_no_property_named_by_it() {
    String first = written(GROW, RUN, aSettlementNarrowedToAMerchant());
    String second = written(GROW, RUN, aSettlementNarrowedToAMerchant());

    Map<String, Object> compared = compared(GROW, RUN, first, second);

    assertThat(alikeOf(compared)).contains("match.merchantId");
    assertThat(alikeOnTheirNameAloneOf(compared)).isEmpty();
    assertThat(compared.get("whatAPropertySaidAlikeOnItsNameDoesNotCompare")).isNull();
  }

  @Test
  void one_changed_field_is_the_only_difference_and_each_side_answers_with_its_own_value() {
    String declining = written(GROW, RUN, aSettlementDecliningWith("101"));
    String declining_otherwise = written(GROW, RUN, aSettlementDecliningWith("102"));

    Map<String, Object> compared = compared(GROW, RUN, declining, declining_otherwise);

    assertThat(theDifferedProperties(compared)).containsExactly("action.providerCode");
    assertThat(valueOf(onlyDifferenceOf(compared), "onTheLeft")).isEqualTo("101");
    assertThat(valueOf(onlyDifferenceOf(compared), "onTheRight")).isEqualTo("102");
  }

  @Test
  void a_difference_in_the_operation_is_described_in_the_words_that_provider_publishes() {
    String settling = written(GROW, RUN, aSettlementDecliningWith("101"));
    String refunding = written(GROW, RUN, aRefundDecliningWith("101"));

    Map<String, Object> compared = compared(GROW, RUN, settling, refunding);

    assertThat(theDifferedProperties(compared)).containsExactly("match.operation");
    assertThat(String.valueOf(onlyDifferenceOf(compared).get("inTheProvidersWords")))
        .contains("provider grow exposes")
        .contains(SETTLE)
        .contains(REFUND);
  }

  @Test
  void every_comparison_names_the_dimensions_a_scenario_holds_no_field_for() {
    String first = written(GROW, RUN, aSettlementDecliningWith("101"));
    String second = written(GROW, RUN, aSettlementDecliningWith("102"));

    Map<String, Object> compared = compared(GROW, RUN, first, second);

    assertThat(dimensionsOf(compared))
        .contains("the state a payment comes to rest in", "the webhook plan");
  }

  @Test
  void a_side_this_run_does_not_hold_is_refused_as_not_found_and_the_refusal_names_that_rule() {
    String written = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> refused = compare(GROW, RUN, written, "scn_never_written", null, null);

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(bodyOf(refused).get("message"))).contains("scn_never_written");
  }

  @Test
  void a_comparison_of_two_providers_rules_is_refused_rather_than_answered_as_a_diff() {
    String onGrow = written(GROW, RUN, aSettlementDecliningWith("101"));
    String onAzericard = written(AZERICARD, RUN, anAzericardSuccess());

    ResponseEntity<Object> refused = compare(GROW, RUN, onGrow, onAzericard, AZERICARD, null);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(bodyOf(refused).get("message")))
        .contains("two scenarios of different providers are not compared");
    assertThat(bodyOf(refused).get("properties")).isEqualTo(List.of("provider"));
  }

  @Test
  void a_rule_of_another_test_run_of_the_same_provider_is_compared_and_each_run_is_named() {
    String here = written(GROW, RUN, aSettlementDecliningWith("101"));
    String there = written(GROW, OTHER_RUN, aSettlementDecliningWith("101"));

    Map<String, Object> compared = compared(GROW, RUN, here, there, null, OTHER_RUN);

    assertThat(sideOf(compared, "onTheLeft").get("testRunId")).isEqualTo(RUN);
    assertThat(sideOf(compared, "onTheRight").get("testRunId")).isEqualTo(OTHER_RUN);
    assertThat(differencesOf(compared)).isEmpty();
  }

  @Test
  void a_copy_taken_as_it_stands_is_a_new_rule_that_differs_from_its_source_in_no_field() {
    String source = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> copied = copy(GROW, RUN, source, null);

    assertThat(copied.getStatusCode().value()).isEqualTo(201);
    String copy = String.valueOf(bodyOf(copied).get("scenarioId"));
    assertThat(copy).isNotEqualTo(source);
    assertThat(differencesOf(compared(GROW, RUN, source, copy))).isEmpty();
    assertThat(theScenarioIdsOf(GROW, RUN)).containsExactlyInAnyOrder(source, copy);
  }

  @Test
  void a_copy_that_edits_one_field_differs_from_its_source_in_that_field_and_in_no_other() {
    String source = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> copied =
        copy(GROW, RUN, source, Map.of("edit", Map.of("action", Map.of("providerCode", "102"))));

    String copy = String.valueOf(bodyOf(copied).get("scenarioId"));
    Map<String, Object> compared = compared(GROW, RUN, source, copy);
    assertThat(theDifferedProperties(compared)).containsExactly("action.providerCode");
    assertThat(valueOf(onlyDifferenceOf(compared), "onTheLeft")).isEqualTo("101");
    assertThat(valueOf(onlyDifferenceOf(compared), "onTheRight")).isEqualTo("102");
  }

  @Test
  void a_copy_that_clears_one_narrowing_differs_from_its_source_in_that_narrowing_alone() {
    String source = written(GROW, RUN, aSettlementNarrowedToAMerchant());

    ResponseEntity<Object> copied =
        copy(GROW, RUN, source, Map.of("clear", List.of("match.merchantId")));

    String copy = String.valueOf(bodyOf(copied).get("scenarioId"));
    Map<String, Object> compared = compared(GROW, RUN, source, copy);
    assertThat(theDifferedProperties(compared)).containsExactly("match.merchantId");
    assertThat(namedBy(onlyDifferenceOf(compared), "onTheRight")).isFalse();
  }

  @Test
  void a_copy_naming_a_property_no_copy_clears_is_refused_and_writes_no_rule() {
    String source = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> refused =
        copy(GROW, RUN, source, Map.of("clear", List.of("action.type")));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(bodyOf(refused).get("properties")).isEqualTo(List.of("clear"));
    assertThat(theScenarioIdsOf(GROW, RUN)).containsExactly(source);
  }

  @Test
  void a_copy_of_a_rule_this_run_does_not_hold_is_refused_as_not_found_and_writes_no_rule() {
    ResponseEntity<Object> refused = copy(GROW, RUN, "scn_never_written", null);

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(bodyOf(refused).get("message"))).contains("scn_never_written");
    assertThat(theScenarioIdsOf(GROW, RUN)).isEmpty();
  }

  @Test
  void a_copy_the_provider_cannot_perform_is_refused_by_the_property_and_writes_no_rule() {
    String source = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> refused =
        copy(
            GROW,
            RUN,
            source,
            Map.of(
                "edit", Map.of("match", Map.of("operation", "chargeSomethingGrowNeverExposed"))));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(bodyOf(refused).get("properties")).isEqualTo(List.of("match.operation"));
    assertThat(theScenarioIdsOf(GROW, RUN)).containsExactly(source);
  }

  @Test
  void a_copy_that_was_written_is_recorded_in_the_trail_naming_the_rule_it_was_copied_from() {
    String source = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> copied = copy(GROW, RUN, source, null);

    Map<String, Object> row = theRowOfTheCopy(GROW, RUN, source);
    assertThat(row.get("status")).isEqualTo(201);
    assertThat(factsOf(row).get("copiedFrom")).isEqualTo(List.of(source));
    assertThat(factsOf(row).get("scenarioIds"))
        .isEqualTo(List.of(String.valueOf(bodyOf(copied).get("scenarioId"))));
  }

  @Test
  void a_copy_that_was_refused_is_recorded_in_the_trail_under_the_rule_it_could_not_copy() {
    copy(GROW, RUN, "scn_never_written", null);

    Map<String, Object> row = theRowOfTheCopy(GROW, RUN, "scn_never_written");
    assertThat(row.get("status")).isEqualTo(404);
    assertThat(refusalOf(row).get("subject")).isEqualTo("NO_OBJECT_WAS_NAMED");
    assertThat(refusalOf(row).get("whatItRefusedInstead"))
        .isEqualTo(ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY);
  }

  @Test
  void a_comparison_asked_of_a_provider_this_simulator_does_not_know_is_not_found() {
    ResponseEntity<Object> refused = compare("nosuchprovider", RUN, "scn_a", "scn_b", null, null);

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(bodyOf(refused).get("message"))).contains("unknown provider");
  }

  @Test
  void a_comparison_against_a_provider_this_simulator_does_not_know_is_not_found() {
    String source = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> refused = compare(GROW, RUN, source, source, "nosuchprovider", null);

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void a_comparison_naming_a_test_run_of_a_shape_this_simulator_refuses_is_a_bad_request() {
    ResponseEntity<Object> refused =
        compare(GROW, "a run named with a space", "scn_a", "scn_b", null, null);

    assertThat(refused.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void a_copy_asked_of_a_provider_this_simulator_does_not_know_is_not_found() {
    ResponseEntity<Object> refused = copy("nosuchprovider", RUN, "scn_a", null);

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void a_copy_naming_a_test_run_of_a_shape_this_simulator_refuses_is_rejected_and_writes_nothing() {
    ResponseEntity<Object> refused = copy(GROW, "a run named with a space", "scn_a", null);

    assertThat(refused.getStatusCode().value()).isEqualTo(400);
    assertThat(theScenarioIdsOf(GROW, RUN)).isEmpty();
  }

  @Test
  void a_copy_whose_edit_the_grammar_cannot_read_at_all_is_rejected_and_writes_no_rule() {
    String source = written(GROW, RUN, aSettlementDecliningWith("101"));

    ResponseEntity<Object> refused =
        copy(
            GROW,
            RUN,
            source,
            Map.of(
                "edit",
                Map.of("story", "a story of one state", "whenState", "Same", "thenState", "Same")));

    assertThat(refused.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(bodyOf(refused).get("message"))).contains("thenState");
    assertThat(theScenarioIdsOf(GROW, RUN)).containsExactly(source);
  }

  private static Map<String, Object> aSettlementDecliningWith(String providerCode) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", SETTLE));
    rule.put("action", Map.of("type", "TERMINAL_FAILURE", "providerCode", providerCode));
    return rule;
  }

  private static Map<String, Object> aRefundDecliningWith(String providerCode) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", REFUND));
    rule.put("action", Map.of("type", "TERMINAL_FAILURE", "providerCode", providerCode));
    return rule;
  }

  private static Map<String, Object> aSettlementNarrowedToAMerchant() {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", SETTLE, "merchantId", "merchant-1"));
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    return rule;
  }

  private static Map<String, Object> anAzericardSuccess() {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    return rule;
  }

  private String written(String provider, String testRunId, Map<String, Object> rule) {
    Map<String, Object> body = new LinkedHashMap<>(rule);
    body.put("testRunId", testRunId);
    ResponseEntity<Object> written = post(provider, "/scenarios", body);
    assertThat(written.getStatusCode().value()).isEqualTo(201);
    return String.valueOf(bodyOf(written).get("scenarioId"));
  }

  private Map<String, Object> compared(
      String provider, String testRunId, String left, String right) {
    return compared(provider, testRunId, left, right, null, null);
  }

  private Map<String, Object> compared(
      String provider,
      String testRunId,
      String left,
      String right,
      String againstProvider,
      String againstTestRunId) {
    ResponseEntity<Object> compared =
        compare(provider, testRunId, left, right, againstProvider, againstTestRunId);
    assertThat(compared.getStatusCode().value()).isEqualTo(200);
    return bodyOf(compared);
  }

  private ResponseEntity<Object> compare(
      String provider,
      String testRunId,
      String left,
      String right,
      String againstProvider,
      String againstTestRunId) {
    StringBuilder uri =
        new StringBuilder("/__simulator/" + provider + "/scenarios/" + left + "/diff")
            .append("?testRunId=")
            .append(testRunId)
            .append("&against=")
            .append(right);
    if (againstProvider != null) {
      uri.append("&againstProvider=").append(againstProvider);
    }
    if (againstTestRunId != null) {
      uri.append("&againstTestRunId=").append(againstTestRunId);
    }
    return http.get()
        .uri(uri.toString())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Object>() {});
  }

  private ResponseEntity<Object> copy(
      String provider, String testRunId, String scenarioId, Map<String, Object> asked) {
    return http.method(HttpMethod.POST)
        .uri(
            "/__simulator/"
                + provider
                + "/scenarios/"
                + scenarioId
                + "/copy?testRunId="
                + testRunId)
        .contentType(MediaType.APPLICATION_JSON)
        .body(asked == null ? Map.of() : asked)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Object>() {});
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> differencesOf(Map<String, Object> compared) {
    return (List<Map<String, Object>>) compared.get("differences");
  }

  private static Map<String, Object> aSettlementNarrowedByAHeader(String value) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("match", Map.of("operation", SETTLE, "header", Map.of("X-Signature", value)));
    rule.put("action", Map.of("type", "FIXED_SUCCESS"));
    return rule;
  }

  @SuppressWarnings("unchecked")
  private static List<String> alikeOnTheirNameAloneOf(Map<String, Object> compared) {
    return (List<String>) compared.get("thePropertiesSaidAlikeOnTheirNameAlone");
  }

  @SuppressWarnings("unchecked")
  private static List<String> alikeOf(Map<String, Object> compared) {
    return (List<String>) compared.get("thePropertiesBothSidesSayAlike");
  }

  private static List<String> dimensionsOf(Map<String, Object> compared) {
    List<String> named = new ArrayList<>();
    for (Map<String, Object> absent : theDimensionsOf(compared)) {
      named.add(String.valueOf(absent.get("dimension")));
    }
    return named;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> theDimensionsOf(Map<String, Object> compared) {
    return (List<Map<String, Object>>) compared.get("theDimensionsItCannotCompare");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> sideOf(Map<String, Object> compared, String side) {
    return (Map<String, Object>) compared.get(side);
  }

  private static List<String> theDifferedProperties(Map<String, Object> compared) {
    List<String> properties = new ArrayList<>();
    for (Map<String, Object> difference : differencesOf(compared)) {
      properties.add(String.valueOf(difference.get("property")));
    }
    return properties;
  }

  private static Map<String, Object> onlyDifferenceOf(Map<String, Object> compared) {
    assertThat(differencesOf(compared)).hasSize(1);
    return differencesOf(compared).getFirst();
  }

  @SuppressWarnings("unchecked")
  private static String valueOf(Map<String, Object> difference, String side) {
    return String.valueOf(((Map<String, Object>) difference.get(side)).get("value"));
  }

  @SuppressWarnings("unchecked")
  private static boolean namedBy(Map<String, Object> difference, String side) {
    return Boolean.TRUE.equals(((Map<String, Object>) difference.get(side)).get("named"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> bodyOf(ResponseEntity<Object> response) {
    return (Map<String, Object>) response.getBody();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> factsOf(Map<String, Object> row) {
    return (Map<String, Object>) row.get("sanitizedBody");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> refusalOf(Map<String, Object> row) {
    return (Map<String, Object>) row.get("refusal");
  }

  private Map<String, Object> theRowOfTheCopy(String provider, String testRunId, String source) {
    for (Map<String, Object> row : theTrailOf(provider, testRunId)) {
      if (String.valueOf(row.get("path")).endsWith("/scenarios/" + source + "/copy")) {
        return row;
      }
    }
    throw new AssertionError("no audit row records the copy of " + source);
  }

  private List<Map<String, Object>> theTrailOf(String provider, String testRunId) {
    return http.get()
        .uri("/__simulator/" + provider + "/audit?testRunId=" + testRunId)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<String> theScenarioIdsOf(String provider, String testRunId) {
    List<String> ids = new ArrayList<>();
    for (Map<String, Object> rule : theRulesOf(provider, testRunId)) {
      ids.add(String.valueOf(rule.get("scenarioId")));
    }
    return ids;
  }

  private List<Map<String, Object>> theRulesOf(String provider, String testRunId) {
    return http.get()
        .uri("/__simulator/" + provider + "/scenarios?testRunId=" + testRunId)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private ResponseEntity<Object> post(String provider, String path, Map<String, Object> body) {
    return http.method(HttpMethod.POST)
        .uri("/__simulator/" + provider + path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Object>() {});
  }

  private void reset(String provider, String testRunId) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", testRunId))
        .retrieve()
        .toBodilessEntity();
  }
}
