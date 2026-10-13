package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActionVerdictTest {

  private static final RefusalSubject SUBJECT =
      RefusalSubject.noObjectWasNamed("the configuration this request asked this provider to hold");

  @Test
  void the_four_ways_an_action_is_refused_carry_the_status_of_the_verdict_they_name() {
    assertThat(ActionVerdict.rejected(SUBJECT).httpStatus()).isEqualTo(400);
    assertThat(ActionVerdict.notFound(SUBJECT).httpStatus()).isEqualTo(404);
    assertThat(ActionVerdict.conflicting(SUBJECT).httpStatus()).isEqualTo(409);
    assertThat(ActionVerdict.refused(SUBJECT).httpStatus()).isEqualTo(422);
  }

  @Test
  void each_way_an_action_is_refused_names_the_vocabulary_entry_it_was_built_from() {
    assertThat(ActionVerdict.rejected(SUBJECT).status()).isEqualTo(RefusedActionStatus.REJECTED);
    assertThat(ActionVerdict.notFound(SUBJECT).status()).isEqualTo(RefusedActionStatus.NOT_FOUND);
    assertThat(ActionVerdict.conflicting(SUBJECT).status())
        .isEqualTo(RefusedActionStatus.CONFLICTING);
    assertThat(ActionVerdict.refused(SUBJECT).status()).isEqualTo(RefusedActionStatus.REFUSED);
  }

  @Test
  void a_refused_verdict_carries_the_subject_it_was_given() {
    assertThat(ActionVerdict.refused(SUBJECT).subject()).isEqualTo(SUBJECT);
  }

  @ParameterizedTest
  @EnumSource(RefusedActionStatus.class)
  void no_status_of_the_refusal_vocabulary_can_be_carried_by_an_action_recorded_as_applied(
      RefusedActionStatus refusing) {
    int refusedStatus = refusing.httpStatus();

    assertThatThrownBy(() -> ActionVerdict.applied(refusedStatus))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a call which took effect was turned away");
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 201, 302, 399})
  void a_status_that_reports_an_answer_below_the_refusing_range_is_recorded_as_applied(int status) {
    assertThat(ActionVerdict.applied(status).httpStatus()).isEqualTo(status);
  }

  @Test
  void the_refusing_range_this_simulator_reads_is_the_client_errors_and_stops_below_the_rest() {
    assertThat(RefusedActionStatus.refuses(399)).isFalse();
    assertThat(RefusedActionStatus.refuses(400)).isTrue();
    assertThat(RefusedActionStatus.refuses(499)).isTrue();
    assertThat(RefusedActionStatus.refuses(500))
        .as(
            "every status of the refusal vocabulary is a client error, and a failure nothing on"
                + " the way to the operation answered decided nothing and so refused nothing")
        .isFalse();
    assertThat(RefusedActionStatus.refuses(599)).isFalse();
  }

  @Test
  void a_status_nothing_decided_is_the_server_errors_and_nothing_a_decision_carries() {
    assertThat(RefusedActionStatus.nothingDecided(499)).isFalse();
    assertThat(RefusedActionStatus.nothingDecided(500)).isTrue();
    assertThat(RefusedActionStatus.nothingDecided(599)).isTrue();
    assertThat(RefusedActionStatus.nothingDecided(200)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503, 599})
  void no_status_nothing_decided_can_be_carried_by_an_action_recorded_as_applied(int status) {
    assertThatThrownBy(() -> ActionVerdict.applied(status))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a call this simulator failed on took effect");
  }
}
