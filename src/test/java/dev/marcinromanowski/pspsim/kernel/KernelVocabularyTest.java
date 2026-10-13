package dev.marcinromanowski.pspsim.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunCandidates;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolution;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class KernelVocabularyTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId RUN_A = TestRunId.of("run-a");
  private static final TestRunId RUN_B = TestRunId.of("run-b");

  @ParameterizedTest(name = "accepts the test run id {0}")
  @ValueSource(strings = {"default", "e2e-run-123", "manual-marcin-2026-09-07", "a"})
  void accepts_test_run_ids_that_need_no_escaping_anywhere(String value) {
    assertThat(TestRunId.of(value).value()).isEqualTo(value);
  }

  @ParameterizedTest(name = "rejects the test run id {0}")
  @ValueSource(strings = {"", "run 1", "-leading-dash", "run/1", "run?x=1", "run#1"})
  void rejects_test_run_ids_that_would_need_escaping(String value) {
    assertThatThrownBy(() -> TestRunId.of(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refuses_to_name_a_test_run_the_id_reserved_for_a_failed_resolution() {
    String reserved = TestRunId.UNKNOWN.value();

    assertThatThrownBy(() -> TestRunId.of(reserved))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
  }

  @Test
  void a_row_about_a_request_naming_a_run_no_namespace_can_hold_is_filed_under_the_reserved_run() {
    assertThat(TestRunId.theRunARowAboutThisRequestBelongsTo("run 1", DEFAULT_RUN))
        .as(
            "a control route that turned a request away still records what it was asked, and a"
                + " row filed under no run at all would hide the call a suite made from the"
                + " tester looking for it")
        .isEqualTo(TestRunId.UNKNOWN);
    assertThat(TestRunId.theRunARowAboutThisRequestBelongsTo("UNKNOWN", DEFAULT_RUN))
        .isEqualTo(TestRunId.UNKNOWN);
    assertThat(TestRunId.theRunARowAboutThisRequestBelongsTo("run-a", DEFAULT_RUN))
        .isEqualTo(RUN_A);
    assertThat(TestRunId.theRunARowAboutThisRequestBelongsTo(null, DEFAULT_RUN))
        .isEqualTo(DEFAULT_RUN);
    assertThat(TestRunId.theRunARowAboutThisRequestBelongsTo(" ", DEFAULT_RUN))
        .isEqualTo(DEFAULT_RUN);
  }

  @Test
  void reads_a_stored_namespace_back_including_the_one_a_failed_resolution_wrote() {
    assertThat(TestRunId.storedNamespace("UNKNOWN")).isEqualTo(TestRunId.UNKNOWN);
    assertThat(TestRunId.storedNamespace("run-a")).isEqualTo(RUN_A);
    assertThatThrownBy(() -> TestRunId.storedNamespace("run 1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_control_request_that_names_no_test_run_lands_in_the_default_namespace() {
    assertThat(TestRunId.declaredOr(null, DEFAULT_RUN)).isEqualTo(DEFAULT_RUN);
    assertThat(TestRunId.declaredOr(" ", DEFAULT_RUN)).isEqualTo(DEFAULT_RUN);
    assertThat(TestRunId.declaredOr("run-a", DEFAULT_RUN)).isEqualTo(RUN_A);
  }

  @Test
  void
      a_control_request_naming_a_test_run_no_namespace_can_hold_names_the_property_not_the_value() {
    assertThatThrownBy(() -> TestRunId.declaredOr("sk-live-0123456789 leaked", DEFAULT_RUN))
        .as(
            "the property is named once: a reader met testRunId: testRunId must match for as long"
                + " as this call prefixed a message that already opened with the property")
        .hasMessageStartingWith("testRunId must match")
        .hasMessageNotContaining("sk-live-0123456789");
    assertThatThrownBy(() -> TestRunId.declaredOr("UNKNOWN", DEFAULT_RUN))
        .hasMessageStartingWith("testRunId must not be")
        .hasMessageContaining("reserved");
  }

  @Test
  void a_reset_that_names_no_scope_clears_the_test_run_it_names_and_never_every_test_run() {
    assertThat(ResetScope.declared(null)).isEqualTo(ResetScope.TEST_RUN);
    assertThat(ResetScope.declared(" ")).isEqualTo(ResetScope.TEST_RUN);
    assertThat(ResetScope.declared("TEST_RUN")).isEqualTo(ResetScope.TEST_RUN);
    assertThat(ResetScope.declared("ALL")).isEqualTo(ResetScope.ALL);
  }

  @ParameterizedTest(name = "the reset scope {0} is answered with the scopes that exist")
  @ValueSource(strings = {"EVERYTHING", "all", "TEST-RUN"})
  void a_reset_scope_this_control_api_does_not_publish_is_answered_with_the_ones_it_does(
      String named) {
    assertThatThrownBy(() -> ResetScope.declared(named))
        .hasMessage("scope must be one of TEST_RUN, ALL");
  }

  @Test
  void refuses_to_store_a_row_under_the_namespace_reserved_for_a_failed_resolution() {
    assertThatThrownBy(() -> TestRunId.storableNamespace(TestRunId.UNKNOWN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
    assertThat(TestRunId.storableNamespace(RUN_A)).isEqualTo(RUN_A);
  }

  @Test
  void an_identifier_rejection_names_the_rule_and_not_the_value() {
    assertThatThrownBy(() -> TestRunId.of("sk-live-0123456789 with a space"))
        .hasMessageContaining("testRunId must match")
        .hasMessageNotContaining("sk-live-0123456789");
    assertThatThrownBy(() -> ProviderId.of("Grow Payments"))
        .hasMessageContaining("provider id must match")
        .hasMessageNotContaining("Grow Payments");
  }

  @ParameterizedTest(name = "rejects the provider id {0}")
  @ValueSource(strings = {"", "Grow", "1grow", "grow/1", "grow_1"})
  void rejects_provider_ids_that_would_need_escaping(String value) {
    assertThatThrownBy(() -> ProviderId.of(value)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_provider_id_reads_as_its_name() {
    assertThat(ProviderId.of("grow")).hasToString("grow");
    assertThat(TestRunId.of("run-a")).hasToString("run-a");
  }

  @Test
  void a_rule_that_matched_nothing_reports_that_and_keeps_its_own_name() {
    TestRunCandidates candidates =
        TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of());

    assertThat(candidates.evaluated()).isTrue();
    assertThat(candidates.matchedNothing()).isTrue();
    assertThat(candidates.isAmbiguous()).isFalse();
    assertThat(candidates.rule()).isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
  }

  @ParameterizedTest(name = "{0} can report that it was not evaluated at all")
  @EnumSource(
      value = TestRunResolutionRule.class,
      names = {"TEST_HEADER", "ROUTE_ALIAS", "ACCOUNT_CREDENTIALS", "OBJECT_CORRELATION"})
  void a_rule_that_was_not_evaluated_is_not_a_rule_that_matched_nothing(
      TestRunResolutionRule rule) {
    TestRunCandidates candidates = TestRunCandidates.notEvaluated(rule);

    assertThat(candidates.evaluated()).isFalse();
    assertThat(candidates.matchedNothing()).isFalse();
    assertThat(candidates.testRunIds()).isEmpty();
    assertThat(candidates.rule()).isEqualTo(rule);
  }

  @Test
  void refuses_candidates_for_a_rule_that_was_not_evaluated() {
    Set<TestRunId> oneCandidate = Set.of(RUN_A);

    assertThatThrownBy(
            () -> new TestRunCandidates(TestRunResolutionRule.TEST_HEADER, false, oneCandidate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a rule that was not evaluated has no candidates");
  }

  @Test
  void a_rule_that_was_not_evaluated_leaves_the_decision_to_the_next_rule() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.notEvaluated(TestRunResolutionRule.TEST_HEADER),
                TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_B))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(RUN_B);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
  }

  @Test
  void a_rule_that_matched_one_test_run_reports_exactly_that() {
    TestRunCandidates candidates =
        TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of(RUN_A));

    assertThat(candidates.matchedNothing()).isFalse();
    assertThat(candidates.isAmbiguous()).isFalse();
    assertThat(candidates.testRunIds()).containsExactly(RUN_A);
  }

  @Test
  void treats_repeated_matches_on_one_namespace_as_a_single_candidate() {
    TestRunCandidates candidates =
        TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of(RUN_A, RUN_A));

    assertThat(candidates.isAmbiguous()).isFalse();
    assertThat(candidates.testRunIds()).containsExactly(RUN_A);
  }

  @Test
  void a_rule_that_matched_two_test_runs_is_ambiguous() {
    assertThat(
            TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_A, RUN_B))
                .isAmbiguous())
        .isTrue();
  }

  @ParameterizedTest(name = "refuses to build a candidate set for {0}")
  @EnumSource(
      value = TestRunResolutionRule.class,
      names = {"DEFAULT", "AMBIGUOUS", "TEST_HEADER_UNMATCHED"})
  void refuses_a_candidate_set_for_a_rule_that_looks_for_nothing(TestRunResolutionRule rule) {
    List<TestRunId> oneCandidate = List.of(RUN_A);

    assertThatThrownBy(() -> TestRunCandidates.of(rule, oneCandidate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("candidate-producing rules");
  }

  @Test
  void falls_back_to_the_default_namespace_when_no_rule_matched_anything() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of()),
                TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of())),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(DEFAULT_RUN);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.DEFAULT);
  }

  @Test
  void reports_the_rule_that_matched_a_single_candidate() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_A))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(RUN_A);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
  }

  @Test
  void applies_the_published_precedence_and_not_the_order_the_caller_listed() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(
                    TestRunResolutionRule.OBJECT_CORRELATION, List.of(TestRunId.of("run-d"))),
                TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_B)),
                TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of(RUN_A))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(RUN_A);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
  }

  @Test
  void a_stronger_rule_that_matched_nothing_lets_the_next_rule_decide() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of()),
                TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_B))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(RUN_B);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
  }

  @Test
  void flags_two_equal_candidates_as_unknown_rather_than_choosing() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of(RUN_A, RUN_B)),
                TestRunCandidates.of(
                    TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(DEFAULT_RUN))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.AMBIGUOUS);
  }

  @Test
  void refuses_to_evaluate_one_rule_twice_in_a_request() {
    List<TestRunCandidates> twice =
        List.of(
            TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_A)),
            TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_B)));

    assertThatThrownBy(() -> TestRunResolution.of(twice, DEFAULT_RUN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "does not evaluate the header rule for {0}")
  @MethodSource("requestsCarryingNoTestRunHeader")
  void a_request_carrying_no_test_run_header_does_not_evaluate_the_header_rule(
      String description, Map<String, List<String>> headers) {
    TestRunCandidates candidates = TestRunHeader.in(headers, existing(RUN_A));

    assertThat(candidates.evaluated()).isFalse();
    assertThat(candidates.matchedNothing()).isFalse();
  }

  static List<Arguments> requestsCarryingNoTestRunHeader() {
    return List.of(
        Arguments.of("no such header", Map.of("Content-Type", List.of("application/json"))),
        Arguments.of(
            "the header key carrying no value at all", Map.of(TestRunHeader.NAME, List.of())));
  }

  @Test
  void a_header_naming_an_existing_test_run_is_that_rules_single_candidate() {
    TestRunCandidates candidates = header(RUN_A.value(), RUN_A, RUN_B);

    assertThat(candidates.rule()).isEqualTo(TestRunResolutionRule.TEST_HEADER);
    assertThat(candidates.testRunIds()).containsExactly(RUN_A);
  }

  @ParameterizedTest(name = "reads the test-run header spelled {0}")
  @ValueSource(
      strings = {"X-Simulator-Test-Run-Id", "x-simulator-test-run-id", "X-SIMULATOR-TEST-RUN-ID"})
  void reads_the_test_run_header_whatever_case_it_arrived_in(String spelling) {
    Map<String, List<String>> headers = Map.of(spelling, List.of(RUN_A.value()));

    assertThat(TestRunHeader.in(headers, existing(RUN_A)).testRunIds()).containsExactly(RUN_A);
  }

  @ParameterizedTest(name = "matches nothing for the header value {0}")
  @ValueSource(strings = {"run-c", "", "run 1", "UNKNOWN"})
  void a_header_that_names_no_existing_test_run_matches_nothing(String value) {
    assertThat(header(value, RUN_A, RUN_B).matchedNothing()).isTrue();
  }

  @Test
  void a_header_naming_two_configured_test_runs_is_ambiguous() {
    Map<String, List<String>> headers =
        Map.of(TestRunHeader.NAME, List.of(RUN_A.value(), RUN_B.value()));

    assertThat(TestRunHeader.in(headers, existing(RUN_A, RUN_B)).isAmbiguous()).isTrue();
  }

  @Test
  void a_header_sent_twice_with_one_value_is_a_single_candidate() {
    Map<String, List<String>> headers =
        Map.of(TestRunHeader.NAME, List.of(RUN_A.value(), RUN_A.value()));

    assertThat(TestRunHeader.in(headers, existing(RUN_A)).testRunIds()).containsExactly(RUN_A);
  }

  @Test
  void a_header_naming_one_existing_test_run_and_one_that_is_not_matches_nothing() {
    Map<String, List<String>> headers = Map.of(TestRunHeader.NAME, List.of(RUN_A.value(), "run-c"));

    assertThat(TestRunHeader.in(headers, existing(RUN_A, RUN_B)).matchedNothing()).isTrue();
  }

  @Test
  void the_header_outranks_the_credentials_a_request_carries() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_B)),
                TestRunCandidates.of(TestRunResolutionRule.TEST_HEADER, List.of(RUN_A))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(RUN_A);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.TEST_HEADER);
  }

  @Test
  void a_header_that_named_nothing_is_terminal_rather_than_a_miss() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(TestRunResolutionRule.TEST_HEADER, List.of()),
                TestRunCandidates.of(TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_B))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.TEST_HEADER_UNMATCHED);
  }

  @Test
  void a_header_that_named_nothing_is_terminal_although_a_route_alias_would_have_matched() {
    TestRunResolution resolution =
        TestRunResolution.of(
            List.of(
                TestRunCandidates.of(TestRunResolutionRule.TEST_HEADER, List.of()),
                TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of(RUN_A))),
            DEFAULT_RUN);

    assertThat(resolution.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(resolution.rule()).isEqualTo(TestRunResolutionRule.TEST_HEADER_UNMATCHED);
  }

  @Test
  void a_verdict_that_named_no_test_run_resolves_to_no_namespace_at_all() {
    TestRunResolution ambiguous =
        TestRunResolution.of(
            List.of(TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, List.of(RUN_A, RUN_B))),
            DEFAULT_RUN);
    TestRunResolution unmatchedHeader =
        TestRunResolution.of(
            List.of(TestRunCandidates.of(TestRunResolutionRule.TEST_HEADER, List.of())),
            DEFAULT_RUN);

    assertThat(ambiguous.resolvedTestRun()).isEmpty();
    assertThat(unmatchedHeader.resolvedTestRun()).isEmpty();
    assertThat(ambiguous.testRunId()).isEqualTo(TestRunId.UNKNOWN);
  }

  @Test
  void every_rule_that_named_a_test_run_resolves_to_it_the_default_namespace_included() {
    assertThat(TestRunResolution.of(List.of(), DEFAULT_RUN).resolvedTestRun())
        .contains(DEFAULT_RUN);
    assertThat(
            TestRunResolution.of(
                    List.of(
                        TestRunCandidates.of(
                            TestRunResolutionRule.ACCOUNT_CREDENTIALS, List.of(RUN_A))),
                    DEFAULT_RUN)
                .resolvedTestRun())
        .contains(RUN_A);
  }

  @Test
  void a_resolution_cannot_pair_a_namespace_with_a_rule_that_contradicts_it() {
    assertThatThrownBy(
            () ->
                new TestRunResolution(TestRunId.UNKNOWN, TestRunResolutionRule.ACCOUNT_CREDENTIALS))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TestRunResolution(RUN_A, TestRunResolutionRule.AMBIGUOUS))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "{0} says for itself what matching nothing means")
  @EnumSource(
      value = TestRunResolutionRule.class,
      names = {"TEST_HEADER", "ROUTE_ALIAS", "ACCOUNT_CREDENTIALS", "OBJECT_CORRELATION"})
  void each_rule_says_for_itself_what_matching_nothing_means(TestRunResolutionRule rule) {
    assertThat(rule.whenNothingMatched())
        .isEqualTo(
            rule == TestRunResolutionRule.TEST_HEADER
                ? TestRunResolutionRule.WhenNothingMatched.resolutionFailsAs(
                    TestRunResolutionRule.TEST_HEADER_UNMATCHED)
                : TestRunResolutionRule.WhenNothingMatched.theNextRuleDecides());
  }

  @ParameterizedTest(name = "{0} is a verdict and is never asked what matching nothing means")
  @EnumSource(
      value = TestRunResolutionRule.class,
      names = {"DEFAULT", "AMBIGUOUS", "TEST_HEADER_UNMATCHED"})
  void a_verdict_is_never_asked_what_matching_nothing_means(TestRunResolutionRule verdict) {
    assertThatThrownBy(verdict::whenNothingMatched)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(verdict.name());
  }

  @ParameterizedTest(name = "{0} names a test run")
  @EnumSource(
      value = TestRunResolutionRule.class,
      names = {
        "TEST_HEADER",
        "ROUTE_ALIAS",
        "ACCOUNT_CREDENTIALS",
        "OBJECT_CORRELATION",
        "DEFAULT"
      })
  void every_rule_and_the_default_namespace_name_a_test_run(TestRunResolutionRule rule) {
    assertThat(rule.namesATestRun()).isTrue();
  }

  @ParameterizedTest(name = "{0} names no test run")
  @EnumSource(
      value = TestRunResolutionRule.class,
      names = {"AMBIGUOUS", "TEST_HEADER_UNMATCHED"})
  void the_verdicts_that_resolved_nothing_name_no_test_run(TestRunResolutionRule verdict) {
    assertThat(verdict.namesATestRun()).isFalse();
  }

  @Test
  void the_published_precedence_lists_the_candidate_producing_rules_only() {
    assertThat(TestRunResolutionRule.byPrecedence())
        .containsExactly(
            TestRunResolutionRule.TEST_HEADER,
            TestRunResolutionRule.ROUTE_ALIAS,
            TestRunResolutionRule.ACCOUNT_CREDENTIALS,
            TestRunResolutionRule.OBJECT_CORRELATION);
    assertThat(TestRunResolutionRule.TEST_HEADER_UNMATCHED.producesCandidates()).isFalse();
  }

  private static TestRunCandidates header(String value, TestRunId... existing) {
    return TestRunHeader.in(Map.of(TestRunHeader.NAME, List.of(value)), existing(existing));
  }

  private static Predicate<TestRunId> existing(TestRunId... testRuns) {
    return Set.of(testRuns)::contains;
  }

  @Test
  void replaces_a_sensitive_value_and_leaves_the_rest_alone() {
    Map<String, List<String>> redacted =
        Redaction.redact(ordered("userId", "grow-user", "sum", "120.50"), Set.of("userId"));

    assertThat(redacted)
        .containsExactly(
            Map.entry("userId", List.of(Redaction.REDACTED)), Map.entry("sum", List.of("120.50")));
  }

  @Test
  void keeps_the_key_of_a_redacted_field() {
    assertThat(Redaction.redact(ordered("pageCode", "grow-page"), Set.of("pageCode")))
        .containsOnlyKeys("pageCode");
  }

  @Test
  void keeps_every_value_of_a_field_that_was_sent_twice() {
    Map<String, List<String>> values = new LinkedHashMap<>();
    values.put("sum", List.of("120.50", "1.00"));
    values.put("pageCode", List.of("page-a", "page-b"));

    Map<String, List<String>> redacted = Redaction.redact(values, Set.of("pageCode"));

    assertThat(redacted.get("sum")).containsExactly("120.50", "1.00");
    assertThat(redacted.get("pageCode"))
        .containsExactly(Redaction.REDACTED, Redaction.REDACTED)
        .hasSize(2);
  }

  @Test
  void matches_a_sensitive_name_whatever_case_it_arrived_in() {
    Map<String, List<String>> redacted =
        Redaction.redact(ordered("AUTHORIZATION", "Bearer squirrel"), Set.of("authorization"));

    assertThat(redacted).containsEntry("AUTHORIZATION", List.of(Redaction.REDACTED));
  }

  @Test
  void redacts_nothing_when_no_name_is_sensitive() {
    Map<String, List<String>> values = ordered("sum", "120.50", "description", "A hold");

    assertThat(Redaction.redact(values, Set.of())).isEqualTo(values);
  }

  @Test
  void writes_the_same_marker_whatever_it_replaced() {
    Map<String, List<String>> redacted =
        Redaction.redact(ordered("a", "x", "b", "a much longer secret"), Set.of("a", "b"));

    assertThat(redacted.get("a")).isEqualTo(redacted.get("b"));
  }

  private static Map<String, List<String>> ordered(String... keysAndValues) {
    Map<String, List<String>> values = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      values.put(keysAndValues[i], List.of(keysAndValues[i + 1]));
    }
    return values;
  }
}
