package dev.marcinromanowski.pspsim.kernel.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SettingGrammarTest {

  private static final String PROPERTY = "settings.webhook.retryPlan";

  @Test
  void a_harness_writes_the_schedule_it_wants_as_the_durations_it_waits() {
    assertThat(SettingGrammar.delaysOf(PROPERTY, "PT0S,PT1S,PT5S"))
        .containsExactly(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5));
  }

  @Test
  void a_schedule_written_with_spaces_around_its_delays_still_names_those_delays() {
    assertThat(SettingGrammar.delaysOf(PROPERTY, " PT0S , PT2M "))
        .containsExactly(Duration.ZERO, Duration.ofMinutes(2));
  }

  @Test
  void a_schedule_that_holds_the_first_attempt_back_is_refused_rather_than_never_becoming_due() {
    assertThatThrownBy(() -> SettingGrammar.delaysOf(PROPERTY, "PT10S,PT1M"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY + "[0] must be zero");
  }

  @Test
  void a_schedule_that_hurries_as_it_goes_is_refused_the_way_the_startup_file_refuses_it() {
    assertThatThrownBy(() -> SettingGrammar.delaysOf(PROPERTY, "PT0S,PT1M,PT5S"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(PROPERTY + "[2] is shorter than the delay before it");
  }

  @Test
  void a_delay_that_is_not_a_duration_is_refused_by_the_position_it_was_written_in() {
    assertThatThrownBy(() -> SettingGrammar.delaysOf(PROPERTY, "PT0S,ten minutes,PT5S"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(PROPERTY + "[1] must be an ISO-8601 duration");
  }

  @Test
  void a_delay_that_runs_the_clock_backwards_is_refused_by_the_position_it_was_written_in() {
    assertThatThrownBy(() -> SettingGrammar.delaysOf(PROPERTY, "PT0S,PT-1S"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(PROPERTY + "[1] must not be a negative duration");
  }

  @Test
  void a_missing_delay_is_refused_rather_than_read_as_no_wait_at_all() {
    assertThatThrownBy(() -> SettingGrammar.delaysOf(PROPERTY, "PT0S,,PT5S"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(PROPERTY + "[1] is required");
  }

  @Test
  void a_schedule_with_no_delay_in_it_is_refused_rather_than_read_as_a_plan_of_no_attempts() {
    assertThatThrownBy(() -> SettingGrammar.delaysOf(PROPERTY, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(PROPERTY + "[0] is required");
  }

  @Test
  void a_schedule_longer_than_one_delivery_may_attempt_is_refused_before_a_delay_is_read() {
    String tooMany = String.join(",", Collections.nCopies(21, "not even a duration"));

    assertThatThrownBy(() -> SettingGrammar.delaysOf(PROPERTY, tooMany))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            PROPERTY
                + " names more attempts than one delivery may make, which is at most "
                + SettingGrammar.MOST_ATTEMPTS_A_RETRY_PLAN_MAY_HOLD);
  }

  @Test
  void a_schedule_of_exactly_the_longest_plan_allowed_is_taken() {
    String longest = "PT0S," + String.join(",", Collections.nCopies(19, "PT1S"));

    assertThat(SettingGrammar.delaysOf(PROPERTY, longest))
        .hasSize(SettingGrammar.MOST_ATTEMPTS_A_RETRY_PLAN_MAY_HOLD);
  }

  @Test
  void the_core_refuses_a_value_for_the_key_it_was_written_under() {
    assertThatThrownBy(
            () ->
                SettingGrammar.rejectAValueTheSettingCannotCarry(
                    SettingKey.WEBHOOK_RETRY_PLAN, PROPERTY, "PT0S,nonsense"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(PROPERTY + "[1] must be an ISO-8601 duration");
  }

  @Test
  void a_key_a_harness_spelled_with_surrounding_space_still_names_the_setting_it_meant() {
    assertThat(SettingKey.named("  webhook.retryPlan ")).contains(SettingKey.WEBHOOK_RETRY_PLAN);
  }

  @Test
  void a_key_this_simulator_has_no_setting_for_names_nothing() {
    assertThat(SettingKey.named("webhook.retryBudget")).isEmpty();
    assertThat(SettingKey.named(null)).isEmpty();
  }

  @Test
  void the_key_space_a_refusal_prints_is_the_one_the_core_owns() {
    assertThat(SettingKey.keys()).containsExactly("webhook.retryPlan");
  }
}
