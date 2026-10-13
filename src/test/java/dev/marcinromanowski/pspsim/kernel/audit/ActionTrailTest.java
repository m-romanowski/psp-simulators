package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionTrailTest {

  private static final String THE_WORDS_IT_WAS_TURNED_AWAY_IN =
      "this test run configures no account under that identifier";

  private static Map<String, List<String>> theWordsItWasTurnedAwayIn() {
    return ActionFacts.none()
        .and(ActionFacts.REFUSED_BECAUSE, THE_WORDS_IT_WAS_TURNED_AWAY_IN)
        .asRecorded();
  }

  private static final ProviderId PROVIDER = ProviderId.of("aprovider");

  private static final TestRunId RUN = TestRunId.of("action-trail-test");

  @Test
  void an_action_a_tester_took_on_a_screen_is_written_as_the_request_that_arrived() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.applied(
                PROVIDER,
                RUN,
                ObjectOrigin.UI,
                "POST",
                "/ui/configuration/clock",
                ActionVerdict.applied(302),
                ActionFacts.none().and("clock.fixedNow", "2026-09-08T09:30:00Z").asRecorded()));

    assertThat(written.written())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.origin()).isEqualTo(ObjectOrigin.UI);
              assertThat(record.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
              assertThat(record.method()).isEqualTo("POST");
              assertThat(record.path()).isEqualTo("/ui/configuration/clock");
              assertThat(record.httpStatus()).isEqualTo(302);
              assertThat(record.testRunId()).isEqualTo(RUN);
              assertThat(record.provider()).isEqualTo(PROVIDER);
              assertThat(record.attempt()).isEqualTo(1);
              assertThat(record.auditId()).startsWith("aud_");
              assertThat(record.sanitizedBody())
                  .containsEntry("clock.fixedNow", List.of("2026-09-08T09:30:00Z"));
            });
  }

  @Test
  void an_action_names_no_provider_verdict_no_routing_rule_and_no_request_headers_it_never_read() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.applied(
                PROVIDER,
                RUN,
                ObjectOrigin.CONTROL_API,
                "POST",
                "/x/reset",
                ActionVerdict.applied(200),
                Map.of()));

    assertThat(written.written())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.outcome()).isNull();
              assertThat(record.resolutionRule()).isNull();
              assertThat(record.delivered()).isNull();
              assertThat(record.scenarioId()).isNull();
              assertThat(record.providerObjectId()).isNull();
              assertThat(record.sanitizedHeaders()).isEmpty();
            });
  }

  @Test
  void a_callback_a_harness_asked_for_names_the_object_it_was_about() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.applied(
                PROVIDER,
                RUN,
                ObjectOrigin.CONTROL_API,
                "POST",
                "/x/webhooks",
                ActionVerdict.applied(201),
                "pi_sim_1",
                Map.of()));

    assertThat(written.written().get(0).providerObjectId()).isEqualTo("pi_sim_1");
  }

  @Test
  void two_actions_of_one_run_are_told_apart_by_the_identifier_each_was_given() {
    RecordedActions written = new RecordedActions();
    ActionTrail trail = written.asTrail();

    trail.record(
        AuditedAction.applied(
            PROVIDER, RUN, ObjectOrigin.UI, "POST", "/ui/a", ActionVerdict.applied(302), Map.of()));
    trail.record(
        AuditedAction.applied(
            PROVIDER, RUN, ObjectOrigin.UI, "POST", "/ui/b", ActionVerdict.applied(302), Map.of()));

    assertThat(written.written()).extracting(AuditRecord::auditId).doesNotHaveDuplicates();
  }

  @Test
  void a_provider_request_is_the_exchange_it_is_and_is_refused_as_an_action_of_a_surface() {
    ActionVerdict.Applied applied = ActionVerdict.applied(200);
    Map<String, List<String>> noFacts = Map.of();

    assertThatThrownBy(
            () ->
                AuditedAction.applied(
                    PROVIDER,
                    RUN,
                    ObjectOrigin.PROVIDER_API,
                    "POST",
                    "/api/light",
                    applied,
                    noFacts))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not an action of a control surface");
  }

  @Test
  void a_scheduled_job_is_a_surface_the_trail_admits_because_the_queue_acts_without_one() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.applied(
                PROVIDER,
                RUN,
                ObjectOrigin.SCHEDULED_JOB,
                "POST",
                "/x/deliver",
                ActionVerdict.applied(200),
                Map.of()));

    assertThat(written.written().get(0).origin()).isEqualTo(ObjectOrigin.SCHEDULED_JOB);
  }

  @Test
  void an_action_a_scenario_rule_decided_names_that_rule_in_the_column_a_harness_filters_on() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.applied(
                    PROVIDER,
                    RUN,
                    ObjectOrigin.UI,
                    "POST",
                    "/ui/objects/act",
                    ActionVerdict.applied(302),
                    "trx_1",
                    ActionFacts.none().and("action", "CAPTURE").asRecorded())
                .decidedBy("scn_declines_the_capture"));

    assertThat(written.written())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.scenarioId()).isEqualTo("scn_declines_the_capture");
              assertThat(record.providerObjectId()).isEqualTo("trx_1");
              assertThat(record.httpStatus()).isEqualTo(302);
            });
  }

  @Test
  void naming_the_rule_that_decided_an_action_changes_nothing_else_the_row_reports() {
    AuditedAction asked =
        AuditedAction.applied(
            PROVIDER,
            RUN,
            ObjectOrigin.UI,
            "POST",
            "/ui/objects/act",
            ActionVerdict.applied(302),
            "trx_1",
            ActionFacts.none().and("action", "REFUND").asRecorded());

    AuditedAction decided = asked.decidedBy("scn_let_it_through");

    assertThat(decided.scenarioId()).isEqualTo("scn_let_it_through");
    assertThat(asked.scenarioId()).isNull();
    assertThat(decided.decidedBy(null)).isEqualTo(asked);
  }

  @Test
  void an_action_no_rule_decided_names_none_rather_than_the_rule_it_happened_to_mention() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.applied(
                    PROVIDER,
                    RUN,
                    ObjectOrigin.UI,
                    "POST",
                    "/ui/scenarios/write",
                    ActionVerdict.applied(302),
                    ActionFacts.none().andEach("scenarioIds", List.of("scn_1")).asRecorded())
                .decidedBy(null));

    assertThat(written.written().get(0).scenarioId()).isNull();
    assertThat(written.written().get(0).sanitizedBody())
        .containsEntry("scenarioIds", List.of("scn_1"));
  }

  @Test
  void the_facts_an_action_was_given_cannot_be_rewritten_by_whoever_handed_them_over() {
    ActionFacts facts = ActionFacts.none().and("scope", "TEST_RUN");
    AuditedAction action =
        AuditedAction.applied(
            PROVIDER,
            RUN,
            ObjectOrigin.CONTROL_API,
            "POST",
            "/x/reset",
            ActionVerdict.applied(200),
            facts.asRecorded());

    facts.and("scope", "ALL");

    assertThat(action.facts()).containsEntry("scope", List.of("TEST_RUN"));
  }

  @Test
  void a_refused_action_reaches_the_trail_under_the_status_of_the_verdict_that_refused_it() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.refusing(
                PROVIDER,
                RUN,
                ObjectOrigin.CONTROL_API,
                "PUT",
                "/__simulator/aprovider/config",
                ActionVerdict.refused(RefusalSubject.noObjectWasNamed("a configuration")),
                ActionFacts.none()
                    .and("accounts[0].accountId", "merchant-1")
                    .and(ActionFacts.REFUSED_BECAUSE, THE_WORDS_IT_WAS_TURNED_AWAY_IN)
                    .asRecorded()));

    assertThat(written.written())
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.httpStatus()).isEqualTo(422);
              assertThat(record.refusal())
                  .isEqualTo(RefusalSubject.noObjectWasNamed("a configuration"));
              assertThat(record.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
              assertThat(record.sanitizedBody())
                  .containsEntry("accounts[0].accountId", List.of("merchant-1"));
            });
  }

  @Test
  void a_refusal_over_an_object_this_run_holds_files_its_action_row_on_that_objects_timeline() {
    RecordedActions written = new RecordedActions();

    written
        .asTrail()
        .record(
            AuditedAction.refusing(
                PROVIDER,
                RUN,
                ObjectOrigin.CONTROL_API,
                "POST",
                "/__simulator/aprovider/hosted/pp_1/complete",
                ActionVerdict.refused(RefusalSubject.theObject("pp_1")),
                theWordsItWasTurnedAwayIn()));

    assertThat(written.written().get(0).providerObjectId()).isEqualTo("pp_1");
  }

  @Test
  void a_row_recording_a_call_this_simulator_turned_away_cannot_be_written_without_saying_why() {
    assertThatThrownBy(
            () ->
                AuditedAction.refusing(
                    PROVIDER,
                    RUN,
                    ObjectOrigin.CONTROL_API,
                    "DELETE",
                    "/__simulator/aprovider/scenarios/scn_9",
                    ActionVerdict.notFound(RefusalSubject.noObjectWasNamed("a scenario rule")),
                    Map.of()))
        .as(
            "the status and the subject tell a tester which thing was refused and never why, so a"
                + " surface that records a refusal without the words it answered leaves them a"
                + " row they cannot act on, and this is the one place every such row is built")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(AuditedAction.A_ROW_THAT_TURNED_A_CALLER_AWAY_SAYS_WHY)
        .hasMessageContaining("/__simulator/aprovider/scenarios/scn_9");
  }

  @Test
  void the_words_a_row_was_turned_away_in_stand_beside_the_facts_it_was_asked_with() {
    Map<String, List<String>> asked =
        ActionFacts.none().and("scope", "TEST_RUN").and("accountId", "merchant-1").asRecorded();

    Map<String, List<String>> recorded =
        ActionFacts.beside(asked, ActionFacts.REFUSED_BECAUSE, THE_WORDS_IT_WAS_TURNED_AWAY_IN);

    assertThat(recorded.keySet())
        .as(
            "a tester reads a row left to right, so the words it was turned away in come after"
                + " what it was asked with, and the facts a surface handed over are left as they"
                + " were")
        .containsExactly("scope", "accountId", ActionFacts.REFUSED_BECAUSE);
    assertThat(recorded)
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of(THE_WORDS_IT_WAS_TURNED_AWAY_IN));
    assertThat(asked).doesNotContainKey(ActionFacts.REFUSED_BECAUSE);
  }

  @Test
  void a_fact_already_beside_the_others_is_replaced_and_not_carried_twice() {
    assertThat(
            ActionFacts.beside(
                ActionFacts.none()
                    .and(ActionFacts.REFUSED_BECAUSE, "an older sentence")
                    .asRecorded(),
                ActionFacts.REFUSED_BECAUSE,
                THE_WORDS_IT_WAS_TURNED_AWAY_IN))
        .as(
            "the six screens that already carried their words hand the same sentence in, so"
                + " replacing rather than appending is what keeps those rows unchanged")
        .containsExactly(
            Map.entry(ActionFacts.REFUSED_BECAUSE, List.of(THE_WORDS_IT_WAS_TURNED_AWAY_IN)));
  }

  @Test
  void a_row_recording_a_call_that_took_effect_needs_no_such_words() {
    assertThatCode(
            () ->
                AuditedAction.applied(
                    PROVIDER,
                    RUN,
                    ObjectOrigin.CONTROL_API,
                    "POST",
                    "/__simulator/aprovider/reset",
                    ActionVerdict.applied(200),
                    Map.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void a_refusal_that_names_no_object_leaves_the_action_row_on_no_objects_timeline() {
    AuditedAction action =
        AuditedAction.refusing(
            PROVIDER,
            RUN,
            ObjectOrigin.CONTROL_API,
            "DELETE",
            "/__simulator/aprovider/scenarios/scn_9",
            ActionVerdict.notFound(RefusalSubject.noObjectWasNamed("a scenario rule")),
            theWordsItWasTurnedAwayIn());

    assertThat(action.providerObjectId()).isNull();
    assertThat(action.refusal()).isEqualTo(RefusalSubject.noObjectWasNamed("a scenario rule"));
  }

  @Test
  void an_action_that_changed_something_names_no_refusal_subject_at_all() {
    AuditedAction action =
        AuditedAction.applied(
            PROVIDER,
            RUN,
            ObjectOrigin.CONTROL_API,
            "POST",
            "/x/reset",
            ActionVerdict.applied(200),
            Map.of());

    assertThat(action.refusal()).isNull();
    assertThat(action.verdict()).isEqualTo(ActionVerdict.applied(200));
  }

  @Test
  void a_refused_action_keeps_its_verdict_when_a_rule_is_named_as_having_decided_it() {
    AuditedAction action =
        AuditedAction.refusing(
                PROVIDER,
                RUN,
                ObjectOrigin.UI,
                "POST",
                "/ui/objects/act",
                ActionVerdict.refused(RefusalSubject.theObject("pp_1")),
                theWordsItWasTurnedAwayIn())
            .decidedBy("scn_3");

    assertThat(action.scenarioId()).isEqualTo("scn_3");
    assertThat(action.refusal()).isEqualTo(RefusalSubject.theObject("pp_1"));
    assertThat(action.providerObjectId()).isEqualTo("pp_1");
  }
}
