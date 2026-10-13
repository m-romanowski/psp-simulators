package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RecordedRefusalsTest {

  private static final TestRunId RUN = TestRunId.of("a-run");

  private static final Instant AT = Instant.parse("2026-09-21T10:00:00Z");

  private final List<AuditRecord> written = new ArrayList<>();

  private final ProviderDouble module = new ProviderDouble();

  private final AuditTrail trail =
      new RecordedRefusals(collectingTrail(), () -> new ProviderRegistry(List.of(module)));

  @Test
  void a_refusal_that_names_an_object_this_run_holds_reaches_the_trail_under_that_object() {
    module.holding("734754");

    trail.record(aWireAnswer(ProviderOutcome.FAILURE, RefusalSubject.theObject("734754")));

    assertThat(written)
        .singleElement()
        .extracting(AuditRecord::providerObjectId)
        .isEqualTo("734754");
  }

  @Test
  void a_refused_wire_answer_that_names_no_subject_at_all_never_reaches_the_trail() {
    AuditRecord refusingNothing = aWireAnswer(ProviderOutcome.FAILURE, null);

    assertThatThrownBy(() -> trail.record(refusingNothing))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a payment nothing was ever refused on");

    assertThat(written).isEmpty();
  }

  @Test
  void
      a_refusal_naming_an_id_this_run_does_not_hold_is_refused_rather_than_planted_on_a_timeline() {
    AuditRecord namingTheObjectACallerGuessed =
        aWireAnswer(ProviderOutcome.FAILURE, RefusalSubject.theObject("734754"));

    assertThatThrownBy(() -> trail.record(namingTheObjectACallerGuessed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whichever object a caller guessed");

    assertThat(written).isEmpty();
  }

  @Test
  void a_refusal_under_a_provider_this_build_does_not_know_cannot_be_shown_to_name_anything() {
    module.holding("734754");

    AuditRecord ofAModuleThatIsNotLoaded =
        new AuditRecord(
            "aud_1",
            RUN,
            null,
            ProviderId.of("unloaded-module"),
            null,
            ObjectOrigin.PROVIDER_API,
            AuditDirection.OUTBOUND_RESPONSE,
            ProviderOutcome.FAILURE,
            "POST",
            "/api/refundTransaction",
            200,
            null,
            RefusalSubject.theObject("734754"),
            null,
            1,
            AT,
            Map.of(),
            Map.of(),
            null);

    assertThatThrownBy(() -> trail.record(ofAModuleThatIsNotLoaded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whichever object a caller guessed");

    assertThat(written).isEmpty();
  }

  @Test
  void a_refusal_that_says_why_it_names_none_needs_no_object_of_this_run_to_reach_the_trail() {
    trail.record(
        aWireAnswer(ProviderOutcome.FAILURE, RefusalSubject.noObjectAnsweredTo("transactionId")));

    assertThat(written).singleElement().extracting(AuditRecord::providerObjectId).isNull();
  }

  @Test
  void a_wire_answer_that_refused_nothing_is_recorded_without_a_subject() {
    trail.record(aWireAnswer(ProviderOutcome.SUCCESS, null));

    assertThat(written).hasSize(1);
  }

  @Test
  void a_webhook_attempt_that_failed_is_not_a_refused_operation_and_owes_no_subject() {
    trail.record(
        new AuditRecord(
            "aud_1",
            RUN,
            null,
            ProviderDouble.ID,
            null,
            ObjectOrigin.SCHEDULED_JOB,
            AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT,
            ProviderOutcome.FAILURE,
            "POST",
            "https://merchant.example/hook",
            500,
            "734754",
            null,
            null,
            1,
            AT,
            Map.of(),
            Map.of(),
            null));

    assertThat(written).hasSize(1);
  }

  @Test
  void the_reads_and_the_clears_a_run_asks_for_reach_the_trail_this_recorder_guards() {
    module.holding("734754");
    trail.record(aWireAnswer(ProviderOutcome.FAILURE, RefusalSubject.theObject("734754")));

    assertThat(trail.of(ProviderDouble.ID, RUN)).hasSize(1);
    assertThat(trail.clear(ProviderDouble.ID, RUN)).isEqualTo(1);
    assertThat(trail.clearAll(ProviderDouble.ID)).isEqualTo(2);
  }

  @Test
  void a_refused_action_of_a_control_surface_that_names_no_subject_never_reaches_the_trail() {
    AuditRecord refusedWithoutASubject = anAction(422, null);

    assertThatThrownBy(() -> trail.record(refusedWithoutASubject))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(written).isEmpty();
  }

  @Test
  void the_refusal_of_an_action_naming_no_subject_says_what_a_tester_would_be_shown_instead() {
    assertThatThrownBy(() -> trail.record(anAction(400, null)))
        .hasMessageContaining("refused the action PUT /__simulator/twokeys/config")
        .hasMessageContaining("a test run in which nothing was refused and nothing took effect");
  }

  @Test
  void an_action_of_a_screen_that_refuses_and_names_no_subject_never_reaches_the_trail_either() {
    AuditRecord refusedWithoutASubject = aScreenAction(404, null);

    assertThatThrownBy(() -> trail.record(refusedWithoutASubject))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(written).isEmpty();
  }

  @Test
  void an_action_recorded_under_a_status_that_took_effect_cannot_name_what_it_refused() {
    AuditRecord appliedYetCarryingARefusal =
        anAction(200, RefusalSubject.noObjectWasNamed("a configuration"));

    assertThatThrownBy(() -> trail.record(appliedYetCarryingARefusal))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(written).isEmpty();
  }

  @Test
  void the_refusal_of_an_applied_action_naming_a_subject_says_what_a_tester_would_be_shown() {
    assertThatThrownBy(
            () -> trail.record(anAction(200, RefusalSubject.noObjectWasNamed("a configuration"))))
        .hasMessageContaining("recorded the action PUT /__simulator/twokeys/config")
        .hasMessageContaining("applied and that it was turned away");
  }

  @Test
  void a_refused_action_that_names_the_rule_it_refused_over_reaches_the_trail() {
    trail.record(anAction(404, RefusalSubject.noObjectWasNamed("a scenario rule")));

    assertThat(written).singleElement().extracting(AuditRecord::providerObjectId).isNull();
  }

  @Test
  void a_refused_action_over_an_object_this_run_holds_reaches_the_trail_under_that_object() {
    module.holding("734754");

    trail.record(anAction(422, RefusalSubject.theObject("734754")));

    assertThat(written)
        .singleElement()
        .extracting(AuditRecord::providerObjectId)
        .isEqualTo("734754");
  }

  @Test
  void an_action_that_changed_something_is_recorded_without_a_subject() {
    trail.record(anAction(200, null));

    assertThat(written).hasSize(1);
  }

  @Test
  void an_action_row_carrying_no_status_at_all_refused_nothing_and_owes_no_subject() {
    trail.record(anActionOf(ObjectOrigin.CONTROL_API, null, null));

    assertThat(written).hasSize(1);
  }

  @Test
  void an_action_under_a_status_nothing_decided_reaches_the_trail_without_a_subject() {
    for (int nothingDecided : new int[] {500, 502, 503, 599}) {
      written.clear();

      trail.record(anAction(nothingDecided, null));

      assertThat(written)
          .as(
              "a failure nothing on the way to the operation answered refused nothing, so a row"
                  + " that names no subject is the only honest one and the trail is where a"
                  + " tester reads what the wire withheld")
          .hasSize(1);
    }
  }

  @Test
  void an_action_under_a_status_nothing_decided_cannot_name_what_it_refused() {
    AuditRecord failedYetCarryingARefusal =
        anAction(500, RefusalSubject.noObjectWasNamed("a configuration"));

    assertThatThrownBy(() -> trail.record(failedYetCarryingARefusal))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recorded the action PUT /__simulator/twokeys/config")
        .hasMessageContaining("under a status nothing decided while naming what it refused");

    assertThat(written).isEmpty();
  }

  private AuditRecord anAction(int httpStatus, @Nullable RefusalSubject refusal) {
    return anActionOf(ObjectOrigin.CONTROL_API, httpStatus, refusal);
  }

  private AuditRecord aScreenAction(int httpStatus, @Nullable RefusalSubject refusal) {
    return anActionOf(ObjectOrigin.UI, httpStatus, refusal);
  }

  private AuditRecord anActionOf(
      ObjectOrigin origin, @Nullable Integer httpStatus, @Nullable RefusalSubject refusal) {
    return new AuditRecord(
        "aud_1",
        RUN,
        null,
        ProviderDouble.ID,
        null,
        origin,
        AuditDirection.INBOUND_REQUEST,
        null,
        "PUT",
        "/__simulator/twokeys/config",
        httpStatus,
        null,
        refusal,
        null,
        1,
        AT,
        Map.of(),
        Map.of(),
        null);
  }

  private AuditRecord aWireAnswer(ProviderOutcome outcome, @Nullable RefusalSubject refusal) {
    return new AuditRecord(
        "aud_1",
        RUN,
        null,
        ProviderDouble.ID,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        outcome,
        "POST",
        "/api/refundTransaction",
        200,
        null,
        refusal,
        null,
        1,
        AT,
        Map.of(),
        Map.of(),
        null);
  }

  private AuditTrail collectingTrail() {
    return new AuditTrail() {
      @Override
      public void record(AuditRecord record) {
        written.add(record);
      }

      @Override
      public List<AuditRecord> of(ProviderId provider, TestRunId testRunId) {
        return List.copyOf(written);
      }

      @Override
      public int clear(ProviderId provider, TestRunId testRunId) {
        return written.size();
      }

      @Override
      public int clearAll(ProviderId provider) {
        return written.size() + 1;
      }
    };
  }
}
