package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowUpTest {

  private static final TestRunId RUN = TestRunId.of("run-follow-up");

  @Test
  void a_follow_up_named_as_a_path_segment_with_its_label_and_its_effect_is_taken() {
    FollowUp settle = new FollowUp("settle-2", "settle (payout)", "AUTHORIZED to SETTLED");

    assertThat(settle.name()).isEqualTo("settle-2");
  }

  @Test
  void a_name_that_could_not_be_a_single_lowercase_path_segment_is_refused() {
    for (String name : List.of("", "Settle", "2settle", "settle/now", "settle now", "-settle")) {
      assertThatCode(() -> new FollowUp(name, "settle", "moves it"))
          .as(name)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("path of the control route");
    }
  }

  @Test
  void a_follow_up_without_the_providers_words_or_without_its_effect_is_refused() {
    assertThatThrownBy(() -> new FollowUp("settle", " ", "moves it"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("words its provider uses");
    assertThatThrownBy(() -> new FollowUp("settle", "settle", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("what it moves");
  }

  @Test
  void a_request_strips_what_it_was_given_and_reads_a_missing_value_as_none() {
    FollowUpRequest asked = FollowUpRequest.of(RUN, " pix-1 ", " settle ", ObjectOrigin.UI);

    assertThat(asked).isEqualTo(new FollowUpRequest(RUN, "pix-1", "settle", ObjectOrigin.UI));
    assertThatThrownBy(() -> FollowUpRequest.of(RUN, null, "settle", ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names none");
    assertThatThrownBy(() -> FollowUpRequest.of(RUN, "pix-1", null, ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FollowUpRequest.A_FOLLOW_UP_IS_NAMED);
    assertThatThrownBy(() -> FollowUpRequest.of(RUN, "pix-1", "  ", ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(FollowUpRequest.A_FOLLOW_UP_IS_NAMED);
  }

  @Test
  void a_follow_up_is_asked_by_a_tester_and_never_by_a_client_route_or_the_delivery_queue() {
    assertThat(FollowUpRequest.of(RUN, "pix-1", "settle", ObjectOrigin.CONTROL_API).askedBy())
        .isEqualTo(ObjectOrigin.CONTROL_API);
    assertThatThrownBy(() -> FollowUpRequest.of(RUN, "pix-1", "settle", ObjectOrigin.PROVIDER_API))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider's own route");
    assertThatThrownBy(() -> FollowUpRequest.of(RUN, "pix-1", "settle", ObjectOrigin.SCHEDULED_JOB))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("delivery queue");
  }

  @Test
  void a_performed_follow_up_keeps_the_jobs_it_was_given_and_no_later_change_to_them() {
    List<String> jobs = new ArrayList<>(List.of("job-1"));
    FollowUpOutcome.Performed performed =
        new FollowUpOutcome.Performed(
            "settle",
            AddressedProviderObject.canonically(
                new ProviderDouble().holding("pix-1").itsObjects().get(0)),
            jobs);

    jobs.add("job-2");

    assertThat(performed.webhookJobIds()).containsExactly("job-1");
  }

  @Test
  void a_refusal_without_a_reason_is_itself_refused() {
    ActionVerdict.Refused verdict = ActionVerdict.refused(RefusalSubject.theObject("pix-1"));

    assertThatThrownBy(() -> new FollowUpOutcome.Refused(verdict, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty refusal");
  }
}
