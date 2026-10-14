package dev.marcinromanowski.pspsim.kernel.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class KeyedRequestAnswerTest {

  private static final Instant AT = Instant.parse("2026-10-14T10:00:00Z");

  @Test
  void a_request_whose_answer_is_kept_shows_that_answer_and_no_reason_for_its_absence() {
    assertThat(request(200, "application/json", "{}", true).whyNoAnswerIsShown()).isNull();
  }

  @Test
  void a_request_answered_with_nothing_says_nothing_was_answered() {
    assertThat(request(null, null, null, true).whyNoAnswerIsShown())
        .isEqualTo(KeyedRequest.NOTHING_WAS_ANSWERED);
  }

  @Test
  void a_request_whose_answer_was_not_kept_says_so_and_not_that_nothing_was_answered() {
    assertThat(request(null, null, null, false).whyNoAnswerIsShown())
        .isEqualTo(KeyedRequest.ITS_ANSWER_WAS_NOT_KEPT);
  }

  @Test
  void a_request_recorded_as_keeping_no_answer_carries_no_part_of_one() {
    assertThatThrownBy(() -> request(200, null, null, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> request(null, "application/json", null, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> request(null, null, "{}", false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_request_read_back_keeps_whether_its_answer_was_kept() {
    RecordedKeyedRequest notKept =
        new RecordedKeyedRequest(
            "a",
            "k",
            "v",
            1L,
            0,
            ExchangeVerdict.FIRST_REQUEST,
            "fp",
            "op",
            null,
            null,
            null,
            null,
            false,
            null,
            AT,
            false);

    assertThat(notKept.asRequest().answerKept()).isFalse();
    assertThat(
            new RecordedKeyedRequest(
                    "a",
                    "k",
                    "v",
                    1L,
                    0,
                    ExchangeVerdict.FIRST_REQUEST,
                    "fp",
                    "op",
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    AT)
                .answerKept())
        .isTrue();
  }

  private static KeyedRequest request(
      @Nullable Integer status, @Nullable String contentType, @Nullable String body, boolean kept) {
    return new KeyedRequest(
        0,
        ExchangeVerdict.FIRST_REQUEST,
        "fp",
        "op",
        status,
        contentType,
        body,
        null,
        false,
        null,
        AT,
        kept);
  }
}
