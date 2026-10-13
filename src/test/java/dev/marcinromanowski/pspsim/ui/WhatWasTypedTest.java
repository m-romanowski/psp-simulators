package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatWasTypedTest {

  @Test
  void a_field_that_was_filled_is_handed_back_the_way_it_was_filled() {
    WhatWasTyped typed = WhatWasTyped.theFieldsOf(Map.of("amount", "abc", "currency", "BRL"));

    assertThat(typed.valueOf("amount")).isEqualTo("abc");
    assertThat(typed.valueOf("currency")).isEqualTo("BRL");
  }

  @Test
  void a_field_nobody_filled_reads_back_as_empty_rather_than_as_the_word_null() {
    assertThat(WhatWasTyped.theFieldsOf(Map.of("amount", "abc")).valueOf("orderId"))
        .as("a screen that drew null into an input handed a tester that word to delete")
        .isEmpty();
    assertThat(WhatWasTyped.nothing().valueOf("amount")).isEmpty();
  }

  @Test
  void the_one_field_a_form_carries_is_handed_back_under_the_name_it_was_carried_as() {
    assertThat(WhatWasTyped.theFieldTyped("webhook.eventType", "charge.settled").fields())
        .containsExactly(Map.entry("webhook.eventType", "charge.settled"));
  }

  @Test
  void a_form_whose_one_field_was_left_out_altogether_carries_no_field_rather_than_a_null() {
    WhatWasTyped typed = WhatWasTyped.theFieldTyped("webhook.eventType", null);

    assertThat(typed.fields())
        .as("a field this record held as null would refuse to be copied at all")
        .isEmpty();
    assertThat(typed.valueOf("webhook.eventType")).isEmpty();
  }

  @Test
  void the_step_a_tester_picked_is_carried_back_to_the_object_it_was_picked_on() {
    WhatWasTyped typed = WhatWasTyped.thePayerStepChosenFor("pix_1", "PAID", null);

    assertThat(typed.theStepChosenFor("pix_1")).isEqualTo("PAID");
    assertThat(typed.theCardChosenFor("pix_1")).isNull();
  }

  @Test
  void the_step_picked_on_one_object_is_not_offered_back_as_picked_on_another() {
    WhatWasTyped typed = WhatWasTyped.thePayerStepChosenFor("pix_1", "PAID", "visa");

    assertThat(typed.theStepChosenFor("pix_2"))
        .as(
            "every object of the run draws a payer form of its own, so a choice carried onto all"
                + " of them would show a tester a step they never picked on a payment they never"
                + " touched")
        .isNull();
    assertThat(typed.theCardChosenFor("pix_2")).isNull();
  }

  @Test
  void a_card_a_tester_picked_is_carried_back_beside_the_step_it_was_picked_with() {
    WhatWasTyped typed = WhatWasTyped.thePayerStepChosenFor("session_1", "PAID", "visa");

    assertThat(typed.theCardChosenFor("session_1")).isEqualTo("visa");
  }

  @Test
  void a_form_that_named_no_object_carries_no_step_back_to_any_of_them() {
    WhatWasTyped typed = WhatWasTyped.thePayerStepChosenFor(null, "PAID", "visa");

    assertThat(typed.theStepChosenFor("pix_1")).isNull();
    assertThat(typed.theCardChosenFor("pix_1")).isNull();
  }

  @Test
  void nothing_typed_carries_no_step_and_no_card_at_all() {
    assertThat(WhatWasTyped.nothing().theStepChosenFor("pix_1")).isNull();
    assertThat(WhatWasTyped.nothing().theCardChosenFor("pix_1")).isNull();
    assertThat(WhatWasTyped.nothing().fields()).isEmpty();
  }
}
