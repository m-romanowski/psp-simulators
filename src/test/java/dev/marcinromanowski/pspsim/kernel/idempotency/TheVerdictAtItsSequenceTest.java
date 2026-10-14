package dev.marcinromanowski.pspsim.kernel.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

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
  void
      every_verdict_that_ran_the_operation_is_the_first_request_where_no_request_claimed_the_key() {
    for (ExchangeVerdict verdict : ExchangeVerdict.values()) {
      assertThat(verdict.recordedWhereNoRequestClaimedTheKey())
          .as(verdict.name())
          .isEqualTo(verdict.couldHaveChangedState() ? ExchangeVerdict.FIRST_REQUEST : verdict);
    }
  }

  @Test
  void a_verdict_that_ran_the_operation_after_the_claim_is_the_repeat_its_role_admits() {
    assertThat(
            ExchangeVerdict.FIRST_REQUEST.recordedAfterTheRequestThatClaimedTheKey(
                KeyRole.DUPLICATE_DETECTION_KEY))
        .isEqualTo(ExchangeVerdict.REPEAT_ADMITTED);
    assertThat(
            ExchangeVerdict.CORRELATED_RETRY.recordedAfterTheRequestThatClaimedTheKey(
                KeyRole.CORRELATION_KEY))
        .isEqualTo(ExchangeVerdict.CORRELATED_RETRY);
    assertThat(
            ExchangeVerdict.DUPLICATE_KEY_REFUSAL.recordedAfterTheRequestThatClaimedTheKey(
                KeyRole.DUPLICATE_DETECTION_KEY))
        .isEqualTo(ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
    assertThat(
            ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY.recordedAfterTheRequestThatClaimedTheKey(
                KeyRole.TRUE_IDEMPOTENCY_KEY))
        .isEqualTo(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY);
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
}
