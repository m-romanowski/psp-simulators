package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectActionOutcomeTest {

  @Test
  void an_applied_action_names_the_rule_that_let_it_through_and_none_when_no_rule_did() {
    assertThat(new ObjectActionOutcome.Applied("trx_1", null, "scn_1").theRuleThatDecidedIt())
        .isEqualTo("scn_1");
    assertThat(new ObjectActionOutcome.Applied("trx_1", "trx_2").theRuleThatDecidedIt()).isNull();
  }

  @Test
  void a_refusal_the_provider_answered_names_the_rule_that_decided_it_where_one_did() {
    assertThat(
            new ObjectActionOutcome.RefusedByTheProvider("808 released", "scn_2")
                .theRuleThatDecidedIt())
        .isEqualTo("scn_2");
    assertThat(new ObjectActionOutcome.RefusedByTheProvider("808 released").theRuleThatDecidedIt())
        .isNull();
  }

  @Test
  void an_answer_a_scenario_gave_in_place_of_the_operation_names_the_rule_that_gave_it() {
    assertThat(new ObjectActionOutcome.AnsweredByAScenarioInstead("scn_3").theRuleThatDecidedIt())
        .isEqualTo("scn_3");
  }

  @Test
  void an_action_this_provider_does_not_offer_and_an_object_it_does_not_hold_name_no_rule() {
    assertThat(new ObjectActionOutcome.NotOffered("no cancel").theRuleThatDecidedIt()).isNull();
    assertThat(new ObjectActionOutcome.NoSuchObject().theRuleThatDecidedIt()).isNull();
  }

  @Test
  void an_action_that_reports_the_object_it_acted_on_as_the_object_it_created_is_refused() {
    assertThatThrownBy(() -> new ObjectActionOutcome.Applied("trx_1", "trx_1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reports as new an object the caller was already looking at");
  }

  @Test
  void a_provider_that_refused_an_action_must_answer_in_words_of_its_own() {
    assertThatThrownBy(() -> new ObjectActionOutcome.RefusedByTheProvider(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tells a tester only that something failed");
  }

  @Test
  void an_action_a_provider_does_not_offer_says_in_its_own_words_what_it_has_instead() {
    ObjectActions declared =
        new ObjectActions(
            ProviderId.of("aprovider"), Map.of(ObjectAction.REFUND, "it returns money by hand"));

    assertThat(ObjectActionOutcome.notOffered(declared, ObjectAction.REFUND))
        .isEqualTo(new ObjectActionOutcome.NotOffered("it returns money by hand"));
  }
}
