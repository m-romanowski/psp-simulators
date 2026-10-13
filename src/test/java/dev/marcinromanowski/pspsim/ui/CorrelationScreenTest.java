package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class CorrelationScreenTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("run-correlation");
  private static final TestRunId DEFAULT = TestRunId.of("default");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String PATH = "/Api/Api/Payment/CreatePaymentProcess";

  @Test
  void every_rule_this_simulator_can_record_is_explained_in_words_naming_what_decided() {
    for (TestRunResolutionRule rule : TestRunResolutionRule.values()) {
      assertThat(CorrelationScreen.ruleMeans(rule)).isNotBlank();
    }
    assertThat(CorrelationScreen.ruleMeans(TestRunResolutionRule.TEST_HEADER_UNMATCHED))
        .contains("ends resolution");
    assertThat(CorrelationScreen.ruleMeans(TestRunResolutionRule.AMBIGUOUS))
        .contains("rather than choosing between equal candidates");
  }

  @Test
  void a_row_this_simulator_never_resolved_says_so_rather_than_reading_as_the_default_rule() {
    assertThat(CorrelationScreen.ruleMeans(null)).contains("records no rule");
  }

  @Test
  void a_manual_action_on_this_simulator_is_not_offered_as_a_provider_request_to_explain() {
    AuditRecord manual =
        record("aud_ui", ObjectOrigin.UI, AuditDirection.INBOUND_REQUEST, null, null, null);

    assertThat(CorrelationScreen.isAProviderCompatibleRequest(manual)).isFalse();
  }

  @Test
  void a_request_reads_the_scenario_and_object_off_the_answer_beside_it_and_says_it_paired_them() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), anAnswer("aud_2", "scn_1", "prc_1"));

    List<CorrelationScreen.Row> rows = CorrelationScreen.theRequestsOf(trail, null, 100);

    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.auditId()).isEqualTo("aud_1");
              assertThat(row.scenarioId()).isEqualTo("scn_1");
              assertThat(row.providerObjectId()).isEqualTo("prc_1");
            });
  }

  @Test
  void an_answer_already_claimed_by_an_earlier_request_is_not_read_as_a_later_request_answer() {
    List<AuditRecord> trail =
        List.of(
            aRequest("aud_1"),
            anAnswer("aud_2", "scn_1", "prc_1"),
            aRequest("aud_3"),
            anAnswer("aud_4", "scn_2", "prc_2"));

    List<CorrelationScreen.Row> rows = CorrelationScreen.theRequestsOf(trail, null, 100);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).scenarioId()).isEqualTo("scn_1");
    assertThat(rows.get(1).scenarioId()).isEqualTo("scn_2");
  }

  @Test
  void a_request_no_answer_of_this_namespace_follows_carries_no_scenario_read_off_another() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), aRequest("aud_3"));

    List<CorrelationScreen.Row> rows = CorrelationScreen.theRequestsOf(trail, null, 100);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).scenarioId()).isNull();
    assertThat(rows.get(1).scenarioId()).isNull();
  }

  @Test
  void a_namespace_holding_more_requests_than_one_screen_keeps_the_newest_of_them() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), aRequest("aud_2"), aRequest("aud_3"));

    List<CorrelationScreen.Row> rows = CorrelationScreen.theRequestsOf(trail, null, 2);

    assertThat(rows).extracting(CorrelationScreen.Row::auditId).containsExactly("aud_2", "aud_3");
  }

  @Test
  void a_resolution_that_named_no_run_is_flagged_ambiguous_on_the_row_a_tester_scans() {
    AuditRecord ambiguous =
        record(
            "aud_1",
            ObjectOrigin.PROVIDER_API,
            AuditDirection.INBOUND_REQUEST,
            TestRunResolutionRule.AMBIGUOUS,
            null,
            null);

    List<CorrelationScreen.Row> rows =
        CorrelationScreen.theRequestsOf(List.of(ambiguous), null, 100);

    assertThat(rows).singleElement().satisfies(row -> assertThat(row.ambiguous()).isTrue());
  }

  @Test
  void an_object_lookup_several_objects_answered_is_flagged_ambiguous_though_the_run_was_named() {
    List<AuditRecord> trail =
        List.of(
            aRequest("aud_1"),
            anAnswerRefusing("aud_2", RefusalSubject.severalObjectsAnsweredTo("ORDER", 2)));

    List<CorrelationScreen.Row> rows = CorrelationScreen.theRequestsOf(trail, null, 100);

    assertThat(rows).singleElement().satisfies(row -> assertThat(row.ambiguous()).isTrue());
  }

  @Test
  void a_field_several_objects_answered_to_is_reported_by_name_and_count_and_never_by_its_value() {
    AuditRecord answer =
        anAnswerRefusing("aud_2", RefusalSubject.severalObjectsAnsweredTo("ORDER", 3));

    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(aRequest("aud_1"), answer, null, List.of(), null, 0, 0, null);

    assertThat(facts).extracting(CorrelationScreen.Fact::value).contains("ORDER", "3");
  }

  @Test
  void a_field_that_named_no_object_of_this_run_is_the_one_unmatched_field_this_build_records() {
    AuditRecord answer =
        anAnswerRefusing("aud_2", RefusalSubject.noObjectAnsweredTo("transactionId"));

    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(aRequest("aud_1"), answer, null, List.of(), null, 0, 0, null);

    assertThat(facts)
        .filteredOn(fact -> fact.what().contains("named no object"))
        .singleElement()
        .satisfies(fact -> assertThat(fact.value()).isEqualTo("transactionId"));
  }

  @Test
  void the_objects_of_this_run_carrying_that_field_are_the_jump_an_ambiguity_offers() {
    List<ProviderObjectState> objects =
        List.of(
            anObject("txn_1", Map.of("ORDER", "order-42")),
            anObject("txn_2", Map.of("ORDER", "order-42")),
            anObject("txn_3", Map.of("cardToken", "tok_9")));

    List<CorrelationScreen.NamedObject> named =
        CorrelationScreen.theObjectsCarryingTheField("ORDER", objects);

    assertThat(named)
        .extracting(CorrelationScreen.NamedObject::providerObjectId)
        .containsExactly("txn_1", "txn_2");
  }

  @Test
  void a_request_no_ambiguity_was_recorded_against_offers_no_object_to_jump_to() {
    assertThat(CorrelationScreen.theFieldAnAmbiguityNamed(anAnswer("aud_2", "scn_1", "prc_1")))
        .isNull();
    assertThat(
            CorrelationScreen.theObjectsCarryingTheField(
                null, List.of(anObject("txn_1", Map.of("ORDER", "order-42")))))
        .isEmpty();
  }

  @Test
  void a_scenario_priority_is_marked_as_read_from_the_scenario_now_and_not_from_the_request() {
    ScenarioRule matched = aScenario("scn_1", 5, 3L);

    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(
            aRequest("aud_1"),
            anAnswer("aud_2", "scn_1", "prc_1"),
            null,
            List.of(),
            matched,
            1,
            2,
            null);

    assertThat(facts)
        .filteredOn(fact -> fact.what().equals("that scenario's priority"))
        .singleElement()
        .satisfies(
            fact -> {
              assertThat(fact.value()).isEqualTo("5");
              assertThat(fact.recorded()).isFalse();
            });
  }

  @Test
  void the_scenario_id_a_request_was_answered_by_is_read_off_the_trail_and_not_reconstructed() {
    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(
            aRequest("aud_1"),
            anAnswer("aud_2", "scn_1", "prc_1"),
            null,
            List.of(),
            null,
            0,
            0,
            null);

    assertThat(facts)
        .filteredOn(fact -> fact.what().equals("matched scenario id"))
        .singleElement()
        .satisfies(
            fact -> {
              assertThat(fact.value()).isEqualTo("scn_1");
              assertThat(fact.recorded()).isTrue();
            });
  }

  @Test
  void a_scenario_deleted_since_it_answered_leaves_the_id_on_the_trail_and_no_priority_beside_it() {
    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(
            aRequest("aud_1"),
            anAnswer("aud_2", "scn_1", "prc_1"),
            null,
            List.of(),
            null,
            0,
            0,
            null);

    assertThat(facts).noneMatch(fact -> fact.what().equals("that scenario's priority"));
    assertThat(facts).anyMatch(fact -> fact.value().equals("scn_1"));
  }

  @Test
  void a_scenario_rank_is_the_order_the_engine_consults_them_in_and_not_the_order_stored() {
    ScenarioRule low = aScenario("scn_low", 1, 9L);
    ScenarioRule high = aScenario("scn_high", 9, 1L);

    assertThat(CorrelationScreen.theRankOf(high, List.of(low, high))).isEqualTo(1);
    assertThat(CorrelationScreen.theRankOf(low, List.of(low, high))).isEqualTo(2);
  }

  @Test
  void the_alias_that_served_a_url_is_the_longest_binding_that_url_contains() {
    RouteAlias broad = alias("psp.test", "/");
    RouteAlias exact = alias("psp.test", "/Api/Api/Payment");

    RouteAlias served =
        CorrelationScreen.theAliasThatCouldHaveServed(
            "https://psp.test/Api/Api/Payment/CreatePaymentProcess", List.of(broad, exact));

    assertThat(served).isEqualTo(exact);
  }

  @Test
  void a_url_no_alias_of_this_run_binds_names_no_alias_rather_than_the_nearest_one() {
    assertThat(
            CorrelationScreen.theAliasThatCouldHaveServed(
                "https://other.test/Api", List.of(alias("psp.test", "/Api"))))
        .isNull();
  }

  @Test
  void a_run_holding_neither_an_account_nor_an_alias_is_not_offered_as_a_source_of_a_tie() {
    CorrelationScreen.Candidates candidates =
        CorrelationScreen.theRunsThatHoldABinding(
            List.of(RUN, DEFAULT),
            Map.of(RUN.value(), List.of("acct-1"), DEFAULT.value(), List.of()),
            Map.of(RUN.value(), List.of(), DEFAULT.value(), List.of()),
            20);

    assertThat(candidates.runs())
        .extracting(CorrelationScreen.Candidate::testRunId)
        .containsExactly(RUN.value());
  }

  @Test
  void a_simulator_holding_more_runs_than_this_screen_lists_says_how_many_it_left_out() {
    List<TestRunId> many =
        List.of(TestRunId.of("run-1"), TestRunId.of("run-2"), TestRunId.of("run-3"));

    CorrelationScreen.Candidates candidates =
        CorrelationScreen.theRunsThatHoldABinding(
            many,
            Map.of("run-1", List.of("a"), "run-2", List.of("a"), "run-3", List.of("a")),
            Map.of(),
            2);

    assertThat(candidates.runs()).hasSize(2);
    assertThat(candidates.notShown()).isEqualTo(1);
    assertThat(candidates.truncated())
        .isEqualTo(CorrelationScreen.MORE_RUNS_HOLD_A_BINDING_THAN_THIS_SCREEN_LISTS);
  }

  @Test
  void a_list_of_runs_this_screen_showed_whole_claims_nothing_was_left_out_of_it() {
    CorrelationScreen.Candidates candidates =
        CorrelationScreen.theRunsThatHoldABinding(
            List.of(RUN), Map.of(RUN.value(), List.of("acct-1")), Map.of(), 20);

    assertThat(candidates.notShown()).isZero();
    assertThat(candidates.truncated()).isNull();
  }

  @Test
  void a_run_offered_as_a_source_of_a_tie_says_what_it_holds_so_a_tester_can_unbind_one() {
    CorrelationScreen.Candidates candidates =
        CorrelationScreen.theRunsThatHoldABinding(
            List.of(RUN),
            Map.of(RUN.value(), List.of("acct-1", "acct-2")),
            Map.of(RUN.value(), List.of(alias("psp.test", "/Api"))),
            20);

    assertThat(candidates.runs())
        .singleElement()
        .satisfies(
            candidate ->
                assertThat(candidate.holds())
                    .isEqualTo("2 configured accounts and 1 bound route alias"));
  }

  @Test
  void a_provider_whose_contract_has_no_customer_says_so_in_its_own_words_rather_than_as_a_gap() {
    List<CorrelationScreen.Absent> absent =
        CorrelationScreen.theProviderHasNoSuchThing(aVocabularyCarryingNoCustomer());

    assertThat(absent)
        .singleElement()
        .satisfies(
            one -> {
              assertThat(one.what()).isEqualTo(MatchFact.CUSTOMER_ID.property());
              assertThat(one.why()).isEqualTo(NO_CUSTOMER_ON_THIS_CONTRACT);
            });
  }

  @Test
  void a_fact_the_provider_never_carries_is_not_also_reported_as_one_this_build_fails_to_record() {
    List<CorrelationScreen.Absent> absent =
        CorrelationScreen.thisBuildRecordsNoSuchThing(
            TestRunResolutionRule.ACCOUNT_CREDENTIALS, aVocabularyCarryingNoCustomer(), null);

    assertThat(absent).noneMatch(one -> one.why().equals(CorrelationScreen.NO_OWNER_IS_RECORDED));
  }

  @Test
  void a_rung_no_provider_of_this_build_offers_a_candidate_at_is_named_as_one_that_never_fires() {
    List<CorrelationScreen.Absent> absent =
        CorrelationScreen.thisBuildRecordsNoSuchThing(
            TestRunResolutionRule.DEFAULT, aVocabularyCarryingNoCustomer(), null);

    assertThat(absent)
        .anyMatch(
            one -> one.why().equals(CorrelationScreen.THIS_BUILD_RESOLVES_NO_REQUEST_BY_AN_OBJECT));
  }

  @Test
  void only_an_ambiguous_resolution_says_the_runs_that_tied_were_not_kept() {
    assertThat(
            CorrelationScreen.thisBuildRecordsNoSuchThing(
                TestRunResolutionRule.AMBIGUOUS, aVocabularyCarryingNoCustomer(), null))
        .anyMatch(one -> one.why().equals(CorrelationScreen.NO_CANDIDATE_IS_RECORDED));
    assertThat(
            CorrelationScreen.thisBuildRecordsNoSuchThing(
                TestRunResolutionRule.TEST_HEADER, aVocabularyCarryingNoCustomer(), null))
        .noneMatch(one -> one.why().equals(CorrelationScreen.NO_CANDIDATE_IS_RECORDED));
  }

  @Test
  void a_credential_that_resolved_an_account_is_withheld_even_when_the_tester_asks_for_values() {
    List<Disclosure.Field> fields =
        Disclosure.of(Map.of("userId", List.of(Redaction.REDACTED)), true);

    assertThat(fields)
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.visibility()).isEqualTo(Disclosure.Visibility.REDACTED_ON_WRITE);
              assertThat(field.values()).isEmpty();
            });
  }

  @Test
  void a_screen_nobody_opened_a_request_on_explains_none_rather_than_the_first_of_them() {
    assertThat(CorrelationScreen.theRequestOpened(List.of(aRequest("aud_1")), null)).isEqualTo(-1);
  }

  @Test
  void the_request_a_tester_opened_is_found_by_its_audit_id_among_the_rows_of_the_namespace() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), aRequest("aud_2"), aRequest("aud_3"));

    assertThat(CorrelationScreen.theRequestOpened(trail, "aud_3")).isEqualTo(2);
  }

  @Test
  void an_audit_id_naming_an_answer_opens_no_explanation_because_an_answer_assigned_nothing() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), anAnswer("aud_2", "scn_1", "prc_1"));

    assertThat(CorrelationScreen.theRequestOpened(trail, "aud_2")).isEqualTo(-1);
  }

  @Test
  void an_audit_id_no_row_of_this_namespace_carries_opens_no_explanation_at_all() {
    assertThat(CorrelationScreen.theRequestOpened(List.of(aRequest("aud_1")), "aud_9"))
        .isEqualTo(-1);
  }

  @Test
  void an_answer_of_this_namespace_is_told_from_a_request_of_it_so_neither_is_read_as_the_other() {
    assertThat(CorrelationScreen.answersAProviderCall(anAnswer("aud_2", null, null))).isTrue();
    assertThat(CorrelationScreen.answersAProviderCall(aRequest("aud_1"))).isFalse();
  }

  @Test
  void a_request_the_next_row_is_another_request_of_has_no_answer_read_off_that_later_one() {
    List<AuditRecord> trail =
        List.of(aRequest("aud_1"), aRequest("aud_2"), anAnswer("aud_3", "scn_1", "prc_1"));

    assertThat(CorrelationScreen.theAnswerTo(trail, 0, new boolean[trail.size()])).isNull();
  }

  @Test
  void a_request_at_the_end_of_a_namespace_has_no_answer_rather_than_the_first_row_of_it() {
    List<AuditRecord> trail = List.of(anAnswer("aud_1", "scn_1", "prc_1"), aRequest("aud_2"));

    assertThat(CorrelationScreen.theAnswerTo(trail, 1, new boolean[trail.size()])).isNull();
  }

  @Test
  void a_request_answered_by_the_row_after_it_names_that_row_as_its_answer() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), anAnswer("aud_2", "scn_1", "prc_1"));

    assertThat(CorrelationScreen.theAnswerTo(trail, 0, new boolean[trail.size()]))
        .isSameAs(trail.get(1));
  }

  @Test
  void a_namespace_holding_exactly_one_screen_of_requests_drops_none_of_them() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), aRequest("aud_2"));

    assertThat(CorrelationScreen.theRequestsOf(trail, null, 2))
        .extracting(CorrelationScreen.Row::auditId)
        .containsExactly("aud_1", "aud_2");
  }

  @Test
  void a_request_assigned_by_a_rule_and_answered_plainly_is_not_flagged_ambiguous_to_a_scanner() {
    List<AuditRecord> trail = List.of(aRequest("aud_1"), anAnswer("aud_2", "scn_1", "prc_1"));

    assertThat(CorrelationScreen.theRequestsOf(trail, null, 100))
        .singleElement()
        .satisfies(row -> assertThat(row.ambiguous()).isFalse());
  }

  @Test
  void a_row_this_simulator_recorded_no_rule_on_names_no_rule_rather_than_an_invented_one() {
    AuditRecord unresolved =
        record(
            "aud_1", ObjectOrigin.PROVIDER_API, AuditDirection.INBOUND_REQUEST, null, null, null);

    assertThat(CorrelationScreen.theRequestsOf(List.of(unresolved), null, 100))
        .singleElement()
        .satisfies(row -> assertThat(row.rule()).isNull());
  }

  @Test
  void a_rule_this_simulator_recorded_is_named_on_the_row_a_tester_scans() {
    assertThat(CorrelationScreen.theRequestsOf(List.of(aRequest("aud_1")), null, 100))
        .singleElement()
        .satisfies(row -> assertThat(row.rule()).isEqualTo("ACCOUNT_CREDENTIALS"));
  }

  @Test
  void a_request_this_simulator_recorded_no_rule_on_still_says_which_run_it_landed_in() {
    AuditRecord unresolved =
        record(
            "aud_1", ObjectOrigin.PROVIDER_API, AuditDirection.INBOUND_REQUEST, null, null, null);

    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(unresolved, null, null, List.of(), null, 0, 0, null);

    assertThat(facts)
        .filteredOn(fact -> fact.what().equals("resolution rule"))
        .singleElement()
        .satisfies(fact -> assertThat(fact.value()).isEqualTo("none recorded"));
  }

  @Test
  void a_request_an_alias_served_names_the_binding_as_read_from_the_aliases_the_run_holds_now() {
    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(
            aRequest("aud_1"), null, alias("psp.test", "/Api"), List.of(), null, 0, 0, null);

    assertThat(facts)
        .filteredOn(fact -> fact.what().equals("the route alias that binds that URL"))
        .singleElement()
        .satisfies(
            fact -> {
              assertThat(fact.value()).isEqualTo("psp.test/Api");
              assertThat(fact.recorded()).isFalse();
            });
  }

  @Test
  void a_request_that_reached_no_object_names_none_rather_than_an_empty_object_id() {
    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(
            aRequest("aud_1"), anAnswer("aud_2", "scn_1", null), null, List.of(), null, 0, 0, null);

    assertThat(facts)
        .noneMatch(fact -> fact.what().equals(CorrelationScreen.THE_ID_THE_PROVIDER_ANSWERED_WITH));
  }

  @Test
  void the_objects_the_matched_object_names_are_offered_as_read_from_that_object() {
    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(
            aRequest("aud_1"),
            anAnswer("aud_2", "scn_1", "prc_1"),
            null,
            List.of(),
            null,
            0,
            0,
            anObject("prc_1", Map.of("transactionId", "txn_9")));

    assertThat(facts)
        .filteredOn(fact -> fact.what().equals("an object it names by transactionId"))
        .singleElement()
        .satisfies(
            fact -> {
              assertThat(fact.value()).isEqualTo("txn_9");
              assertThat(fact.recorded()).isFalse();
            });
  }

  @Test
  void an_account_this_run_holds_is_offered_as_read_from_the_configuration_and_not_from_the_row() {
    List<CorrelationScreen.Fact> facts =
        CorrelationScreen.theFactsOf(
            aRequest("aud_1"), null, null, List.of("acct-1"), null, 0, 0, null);

    assertThat(facts)
        .filteredOn(fact -> fact.what().equals("an account this run holds"))
        .singleElement()
        .satisfies(
            fact -> {
              assertThat(fact.value()).isEqualTo("acct-1");
              assertThat(fact.recorded()).isFalse();
            });
  }

  @Test
  void a_request_no_answer_follows_offers_no_field_of_an_ambiguity_to_jump_from() {
    assertThat(CorrelationScreen.theFieldAnAmbiguityNamed(null)).isNull();
  }

  @Test
  void a_request_an_ambiguity_was_recorded_against_names_the_field_to_jump_from() {
    assertThat(
            CorrelationScreen.theFieldAnAmbiguityNamed(
                anAnswerRefusing("aud_2", RefusalSubject.severalObjectsAnsweredTo("ORDER", 2))))
        .isEqualTo("ORDER");
  }

  @Test
  void only_a_request_a_scenario_answered_says_its_priority_was_not_recorded_at_the_time() {
    assertThat(
            CorrelationScreen.thisBuildRecordsNoSuchThing(
                TestRunResolutionRule.TEST_HEADER,
                aVocabularyCarryingNoCustomer(),
                anAnswer("aud_2", "scn_1", "prc_1")))
        .anyMatch(one -> one.why().equals(CorrelationScreen.NO_PRIORITY_IS_RECORDED));
    assertThat(
            CorrelationScreen.thisBuildRecordsNoSuchThing(
                TestRunResolutionRule.TEST_HEADER,
                aVocabularyCarryingNoCustomer(),
                anAnswer("aud_2", null, "prc_1")))
        .noneMatch(one -> one.why().equals(CorrelationScreen.NO_PRIORITY_IS_RECORDED));
  }

  @Test
  void a_provider_that_does_carry_a_customer_is_told_this_build_records_none_against_a_request() {
    List<CorrelationScreen.Absent> absent =
        CorrelationScreen.thisBuildRecordsNoSuchThing(
            TestRunResolutionRule.TEST_HEADER, aVocabularyCarryingEveryFact(), null);

    assertThat(absent).anyMatch(one -> one.why().equals(CorrelationScreen.NO_OWNER_IS_RECORDED));
    assertThat(absent).anyMatch(one -> one.why().equals(CorrelationScreen.NO_ACCOUNT_IS_RECORDED));
  }

  @Test
  void a_provider_that_does_carry_a_merchant_id_has_no_absence_reported_in_its_own_words() {
    assertThat(CorrelationScreen.theProviderHasNoSuchThing(aVocabularyCarryingEveryFact()))
        .isEmpty();
  }

  @Test
  void a_run_holding_one_account_and_no_alias_counts_that_account_in_the_singular() {
    assertThat(theHoldingOf(List.of("acct-1"), List.of())).isEqualTo("1 configured account");
    assertThat(theHoldingOf(List.of("acct-1", "acct-2"), List.of()))
        .isEqualTo("2 configured accounts");
  }

  @Test
  void a_run_holding_aliases_and_no_account_counts_the_aliases_rather_than_the_accounts() {
    assertThat(theHoldingOf(List.of(), List.of(alias("psp.test", "/Api"))))
        .isEqualTo("1 bound route alias");
    assertThat(theHoldingOf(List.of(), List.of(alias("psp.test", "/Api"), alias("b.test", "/Api"))))
        .isEqualTo("2 bound route aliases");
  }

  @Test
  void a_simulator_holding_exactly_as_many_runs_as_this_screen_lists_leaves_none_of_them_out() {
    CorrelationScreen.Candidates candidates =
        CorrelationScreen.theRunsThatHoldABinding(
            List.of(TestRunId.of("run-1"), TestRunId.of("run-2")),
            Map.of("run-1", List.of("a"), "run-2", List.of("a")),
            Map.of(),
            2);

    assertThat(candidates.runs()).hasSize(2);
    assertThat(candidates.notShown()).isZero();
    assertThat(candidates.truncated()).isNull();
  }

  @Test
  void two_aliases_of_one_length_binding_a_url_leave_the_first_of_them_named_and_not_the_last() {
    RouteAlias first = alias("psp.test", "/Api");
    RouteAlias second = alias("psp.test", "/Api");

    assertThat(
            CorrelationScreen.theAliasThatCouldHaveServed(
                "https://psp.test/Api/one", List.of(first, second)))
        .isSameAs(first);
  }

  private static String theHoldingOf(List<String> accounts, List<RouteAlias> aliases) {
    return CorrelationScreen.theRunsThatHoldABinding(
            List.of(RUN), Map.of(RUN.value(), accounts), Map.of(RUN.value(), aliases), 20)
        .runs()
        .get(0)
        .holds();
  }

  private static ProviderVocabulary aVocabularyCarryingEveryFact() {
    return new ProviderVocabulary(
        PROVIDER,
        List.of(
            new ProviderOperation(
                "createPaymentProcess",
                Set.of(MatchFact.MERCHANT_ID, MatchFact.CUSTOMER_ID),
                "the payment process",
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
        Map.of(),
        new FailureCodes("[0-9]+", "a numeric err.id"),
        ProcessingStates.noneOfThem("provider grow publishes no processing status"));
  }

  private static final String NO_CUSTOMER_ON_THIS_CONTRACT =
      "provider grow publishes no customer or owner id on any endpoint of this contract";

  private static ProviderVocabulary aVocabularyCarryingNoCustomer() {
    return new ProviderVocabulary(
        PROVIDER,
        List.of(
            new ProviderOperation(
                "createPaymentProcess",
                Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID),
                "the payment process",
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
        Map.of(MatchFact.CUSTOMER_ID, NO_CUSTOMER_ON_THIS_CONTRACT),
        new FailureCodes("[0-9]+", "a numeric err.id"),
        ProcessingStates.noneOfThem("provider grow publishes no processing status"));
  }

  private static RouteAlias alias(String host, String path) {
    return new RouteAlias(
        PROVIDER, RUN, new RouteAliasKey(host, path), "https://" + host + path, path);
  }

  private static ScenarioRule aScenario(String scenarioId, int priority, long arrival) {
    return new ScenarioRule(
        scenarioId,
        PROVIDER,
        RUN,
        priority,
        arrival,
        new ScenarioMatch(null, null, null, null, null, null, null, null, null, Map.of()),
        new ScenarioAction.FixedSuccess(),
        null,
        PINNED);
  }

  private static ProviderObjectState anObject(String id, Map<String, String> related) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(id),
        AKindOfObjectATestNames.ONE,
        "0",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        related,
        ObjectOrigin.PROVIDER_API,
        PINNED);
  }

  private static AuditRecord aRequest(String auditId) {
    return record(
        auditId,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        TestRunResolutionRule.ACCOUNT_CREDENTIALS,
        null,
        null);
  }

  private static AuditRecord anAnswer(
      String auditId, @Nullable String scenarioId, @Nullable String providerObjectId) {
    return new AuditRecord(
        auditId,
        RUN,
        TestRunResolutionRule.ACCOUNT_CREDENTIALS,
        PROVIDER,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        "POST",
        PATH,
        200,
        providerObjectId,
        null,
        scenarioId,
        1,
        PINNED,
        Map.of(),
        Map.of(),
        null);
  }

  private static AuditRecord anAnswerRefusing(String auditId, RefusalSubject refusal) {
    return new AuditRecord(
        auditId,
        RUN,
        TestRunResolutionRule.ACCOUNT_CREDENTIALS,
        PROVIDER,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.FAILURE,
        "POST",
        PATH,
        200,
        null,
        refusal,
        null,
        1,
        PINNED,
        Map.of(),
        Map.of(),
        null);
  }

  private static AuditRecord record(
      String auditId,
      ObjectOrigin origin,
      AuditDirection direction,
      @Nullable TestRunResolutionRule rule,
      @Nullable String providerObjectId,
      @Nullable String scenarioId) {
    return new AuditRecord(
        auditId,
        RUN,
        rule,
        PROVIDER,
        null,
        origin,
        direction,
        null,
        "POST",
        PATH,
        null,
        providerObjectId,
        null,
        scenarioId,
        1,
        PINNED,
        Map.of("Content-Type", List.of("application/json")),
        Map.of("userId", List.of(Redaction.REDACTED)),
        null);
  }
}
