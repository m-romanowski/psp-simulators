package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RailGroupTest {

  @Test
  void a_group_is_found_by_the_token_a_contributed_screen_writes() {
    assertThat(RailGroup.named("daily-work")).contains(RailGroup.DAILY_WORK);
    assertThat(RailGroup.named("take-it-elsewhere")).contains(RailGroup.TAKE_IT_ELSEWHERE);
  }

  @Test
  void a_token_no_group_of_this_rail_carries_finds_none() {
    assertThat(RailGroup.named("brazil")).isEmpty();
    assertThat(RailGroup.named("Daily work")).isEmpty();
    assertThat(RailGroup.named("")).isEmpty();
  }

  @Test
  void the_tokens_a_refusal_lists_are_every_group_this_rail_has() {
    assertThat(RailGroup.theOnesThisRailHas())
        .isEqualTo("daily-work, set-up, evidence, take-it-elsewhere");
  }

  @Test
  void every_group_reads_on_a_screen_as_a_name_a_tester_recognises() {
    assertThat(RailGroup.DAILY_WORK.label()).isEqualTo("Daily work");
    assertThat(RailGroup.SET_UP.label()).isEqualTo("Set up");
    assertThat(RailGroup.EVIDENCE.label()).isEqualTo("Evidence");
    assertThat(RailGroup.TAKE_IT_ELSEWHERE.label()).isEqualTo("Take it elsewhere");
    assertThat(RailGroup.DAILY_WORK.token()).isEqualTo("daily-work");
  }
}
