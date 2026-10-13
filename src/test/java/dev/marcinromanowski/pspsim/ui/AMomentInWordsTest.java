package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AMomentInWordsTest {

  private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

  private static final AMomentInWords AS_A_TESTER_READS_IT = new AMomentInWords(NOW);

  private static final String THE_WORDS_A_TESTER_READS_WHERE_NO_MOMENT_COULD_BE_READ =
      "this screen was handed no moment it could read here";

  @Test
  void a_deadline_still_ahead_is_said_to_be_ahead_and_by_how_far() {
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T10:05:00Z"))
        .isEqualTo("in 5 minutes");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T10:01:00Z")).isEqualTo("in 1 minute");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T10:00:01Z")).isEqualTo("in 1 second");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T10:00:45Z"))
        .isEqualTo("in 45 seconds");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T11:00:00Z")).isEqualTo("in 1 hour");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T13:30:00Z")).isEqualTo("in 3 hours");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-10-01T10:00:00Z")).isEqualTo("in 1 day");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-10-03T10:00:00Z")).isEqualTo("in 3 days");
  }

  @Test
  void a_deadline_already_behind_is_said_to_be_behind_and_by_how_far() {
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T09:55:00Z"))
        .isEqualTo("5 minutes ago");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T09:59:59Z"))
        .isEqualTo("1 second ago");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T08:00:00Z")).isEqualTo("2 hours ago");
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-28T10:00:00Z")).isEqualTo("2 days ago");
  }

  @Test
  void the_moment_this_run_is_being_read_at_is_neither_ahead_nor_behind() {
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("2026-09-30T10:00:00Z"))
        .as(
            "a reading taken from the constant this class prints would pass on an empty one, and"
                + " an empty cell beside a deadline is a screen saying nothing")
        .isEqualTo("now");
  }

  @Test
  void a_span_is_named_in_the_largest_unit_it_fills_and_never_in_two() {
    assertThat(AMomentInWords.theSpanOf(59)).isEqualTo("59 seconds");
    assertThat(AMomentInWords.theSpanOf(60)).isEqualTo("1 minute");
    assertThat(AMomentInWords.theSpanOf(3599)).isEqualTo("59 minutes");
    assertThat(AMomentInWords.theSpanOf(3600)).isEqualTo("1 hour");
    assertThat(AMomentInWords.theSpanOf(86399)).isEqualTo("23 hours");
    assertThat(AMomentInWords.theSpanOf(86400)).isEqualTo("1 day");
  }

  @Test
  void a_value_that_is_no_moment_says_so_rather_than_reading_as_a_deadline() {
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("whenever"))
        .as(
            "a reading taken from the constant this class prints would pass on an empty one, and"
                + " an empty cell beside a deadline reads as a deadline nobody set")
        .isEqualTo(THE_WORDS_A_TESTER_READS_WHERE_NO_MOMENT_COULD_BE_READ);
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow(null))
        .isEqualTo(THE_WORDS_A_TESTER_READS_WHERE_NO_MOMENT_COULD_BE_READ);
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow(""))
        .isEqualTo(THE_WORDS_A_TESTER_READS_WHERE_NO_MOMENT_COULD_BE_READ);
  }

  @Test
  void a_moment_a_provider_wrote_with_spaces_around_it_is_still_read_as_that_moment() {
    assertThat(AS_A_TESTER_READS_IT.howFarFromNow("  2026-09-30T10:05:00Z  "))
        .isEqualTo("in 5 minutes");
  }
}
