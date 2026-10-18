package dev.marcinromanowski.pspsim.kernel.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TheVerdictAtItsSequenceTest {

  @Test
  void a_repeat_that_ran_the_operation_is_named_by_the_role_of_its_key() {
    assertThat(KeyRole.TRUE_IDEMPOTENCY_KEY.aRepeatThatRanTheOperation())
        .isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(KeyRole.DUPLICATE_DETECTION_KEY.aRepeatThatRanTheOperation())
        .isEqualTo(ExchangeVerdict.REPEAT_ADMITTED);
    assertThat(KeyRole.CORRELATION_KEY.aRepeatThatRanTheOperation())
        .isEqualTo(ExchangeVerdict.CORRELATED_RETRY);
  }

  @Test
  void a_request_that_ran_the_operation_is_a_first_request_where_no_request_ran_before_it() {
    assertThat(KeyedOutcome.RAN_THE_OPERATION.recordedWhereNoRequestRanTheOperation())
        .isEqualTo(ExchangeVerdict.FIRST_REQUEST);
  }

  @Test
  void a_request_that_ran_after_another_is_the_repeat_its_role_admits() {
    for (KeyRole role : KeyRole.values()) {
      assertThat(KeyedOutcome.RAN_THE_OPERATION.recordedAfterTheRequestThatRanTheOperation(role))
          .as(role.name())
          .isEqualTo(role.aRepeatThatRanTheOperation());
    }
  }

  @Test
  void a_refusal_or_a_replay_is_recorded_as_itself_wherever_it_lands() {
    for (KeyRole role : KeyRole.values()) {
      assertThat(bothPlacesOf(KeyedOutcome.REFUSED_AS_A_DUPLICATE, role))
          .containsOnly(ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
      assertThat(bothPlacesOf(KeyedOutcome.REPLAYED, role))
          .containsOnly(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY);
      assertThat(bothPlacesOf(KeyedOutcome.REFUSED_AS_A_CONFLICT, role))
          .containsOnly(ExchangeVerdict.CONFLICTING_PAYLOAD);
      assertThat(bothPlacesOf(KeyedOutcome.REFUSED_WHILE_THE_KEY_WAS_HELD, role))
          .containsOnly(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD);
    }
  }

  @Test
  void the_verdicts_that_ran_the_operation_are_the_three_that_could_have_changed_state() {
    assertThat(ExchangeVerdict.THAT_RAN_THE_OPERATION)
        .containsExactly(
            ExchangeVerdict.FIRST_REQUEST,
            ExchangeVerdict.REPEAT_ADMITTED,
            ExchangeVerdict.CORRELATED_RETRY);
    assertThat(ExchangeVerdict.DUPLICATE_KEY_REFUSAL.couldHaveChangedState()).isFalse();
    assertThat(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY.couldHaveChangedState()).isFalse();
    assertThat(ExchangeVerdict.CONFLICTING_PAYLOAD.couldHaveChangedState()).isFalse();
    assertThat(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.couldHaveChangedState()).isFalse();
  }

  @Test
  void only_a_replay_and_a_conflict_answer_from_what_the_request_that_ran_stored() {
    for (ExchangeVerdict verdict : ExchangeVerdict.values()) {
      assertThat(verdict.answersFromWhatTheRequestThatRanStored())
          .as(verdict.name())
          .isEqualTo(
              verdict == ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY
                  || verdict == ExchangeVerdict.CONFLICTING_PAYLOAD);
    }
  }

  private static List<ExchangeVerdict> bothPlacesOf(KeyedOutcome outcome, KeyRole role) {
    return List.of(
        outcome.recordedWhereNoRequestRanTheOperation(),
        outcome.recordedAfterTheRequestThatRanTheOperation(role));
  }
}
