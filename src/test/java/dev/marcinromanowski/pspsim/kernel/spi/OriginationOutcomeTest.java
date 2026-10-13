package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OriginationOutcomeTest {

  private static final String WHAT_EXISTS_NOW = "a hold the Payments screen lists";

  @Test
  void a_payment_that_started_without_an_id_leaves_a_tester_nothing_to_look_it_up_by() {
    assertThatThrownBy(() -> new OriginationOutcome.Originated("  ", WHAT_EXISTS_NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a blank one leaves a tester with nothing to look the payment up by");
  }

  @Test
  void a_provider_that_started_a_payment_and_says_nothing_of_what_exists_now_is_refused() {
    assertThatThrownBy(() -> new OriginationOutcome.Originated("770001", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say, in its own words, what now exists");
  }

  @Test
  void a_provider_that_refused_a_payment_without_saying_why_tells_a_tester_only_that_it_failed() {
    assertThatThrownBy(() -> new OriginationOutcome.RefusedByTheProvider("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("an empty refusal tells a tester only that something failed");
  }

  @Test
  void a_provider_that_starts_no_payment_must_say_what_its_contract_has_in_place_of_one() {
    assertThatThrownBy(() -> new OriginationOutcome.NotOffered("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reporting a decision this simulator did not make");
  }

  @Test
  void a_payment_a_rule_proceeded_under_names_that_rule_and_one_no_rule_touched_names_none() {
    assertThat(
            new OriginationOutcome.Originated("770001", WHAT_EXISTS_NOW, "sc_1")
                .theRuleThatDecidedIt())
        .isEqualTo("sc_1");
    assertThat(new OriginationOutcome.Originated("770001", WHAT_EXISTS_NOW).theRuleThatDecidedIt())
        .isNull();
  }

  @Test
  void a_refusal_a_rule_decided_names_that_rule_and_one_the_provider_decided_names_none() {
    assertThat(new OriginationOutcome.RefusedByTheProvider("803", "sc_2").theRuleThatDecidedIt())
        .isEqualTo("sc_2");
    assertThat(new OriginationOutcome.RefusedByTheProvider("803").theRuleThatDecidedIt()).isNull();
  }

  @Test
  void a_payment_a_rule_answered_instead_of_running_names_that_rule() {
    assertThat(new OriginationOutcome.AnsweredByAScenarioInstead("sc_3").theRuleThatDecidedIt())
        .isEqualTo("sc_3");
  }

  @Test
  void an_absence_and_a_missing_account_are_decided_by_no_rule_at_all() {
    assertThat(new OriginationOutcome.NotOffered("it has no such call").theRuleThatDecidedIt())
        .isNull();
    assertThat(new OriginationOutcome.NoSuchAccount().theRuleThatDecidedIt()).isNull();
  }
}
