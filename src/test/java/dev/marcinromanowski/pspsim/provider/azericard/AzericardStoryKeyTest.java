package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import org.junit.jupiter.api.Test;

class AzericardStoryKeyTest {

  @Test
  void names_the_payment_a_terminal_and_an_order_stand_for() {
    assertThat(AzericardStoryKey.ofPayment("77777777", "20220628084800"))
        .isEqualTo("payment:8-7777777720220628084800");
  }

  @Test
  void two_payments_whose_terminals_and_orders_run_together_alike_keep_two_keys() {
    assertThat(AzericardStoryKey.ofPayment("7777", "77720220628084800"))
        .isNotEqualTo(AzericardStoryKey.ofPayment("7777777", "20220628084800"));
  }

  @Test
  void two_payments_of_one_terminal_ordered_alike_but_for_a_separator_keep_two_keys() {
    assertThat(AzericardStoryKey.ofPayment("77777777", "-20220628084800"))
        .isNotEqualTo(AzericardStoryKey.ofPayment("77777777-", "20220628084800"));
  }

  @Test
  void a_key_names_the_space_the_kernel_makes_a_story_stand_in() {
    assertThat(
            new StoryKey(
                    StoryKeying.FLOW, AzericardStoryKey.ofPayment("77777777", "20220628084800"))
                .value())
        .startsWith("payment" + StoryKey.NAMES_THE_SPACE);
  }

  @Test
  void the_key_a_terminal_is_measured_by_is_the_longest_any_order_the_gateway_answers_spells() {
    String measured = AzericardStoryKey.theLongestPaymentKeyOf("77777777");

    assertThat(measured)
        .isEqualTo(
            AzericardStoryKey.ofPayment("77777777", "9".repeat(AzericardOrderTrace.LONGEST_ORDER)))
        .hasSizeGreaterThan(
            AzericardStoryKey.ofPayment("77777777", "9".repeat(AzericardOrderTrace.SHORTEST_ORDER))
                .length());
  }

  @Test
  void a_key_carries_nothing_a_text_column_cannot_hold() {
    assertThat(AzericardStoryKey.ofPayment("77777777", "20220628084800")).matches("[\\x20-\\x7e]+");
  }
}
