package dev.marcinromanowski.pspsim.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import java.util.List;
import org.junit.jupiter.api.Test;

class ARefusedPropertyTest {

  @Test
  void a_bundle_and_a_rule_refuse_a_property_in_one_set_of_words() {
    assertThat(BundleRefusal.theWordsOf(List.of(new BundleRefusal("fixtures[0].alias", "taken"))))
        .as(
            "a bundle, a scenario rule and a payment-method fixture each refuse a property the"
                + " same way, and the sentence a tester reads is one sentence wherever it"
                + " reaches them from")
        .isEqualTo("fixtures[0].alias cannot be applied: taken")
        .isEqualTo(ARefusedProperty.theWordsOf("fixtures[0].alias", "taken"))
        .isEqualTo(
            ScenarioRefusal.theWordsOf(List.of(new ScenarioRefusal("fixtures[0].alias", "taken"))));
  }

  @Test
  void a_payment_method_fixture_refuses_a_property_in_those_same_words() {
    assertThat(
            new PaymentMethodFixtureAdmission.Verdict.Refused("fixtures[0].alias", "taken")
                .inItsOwnWords())
        .as(
            "a fixture refusal reaches a tester through a screen and a suite, and both read the"
                + " same sentence a rule and a bundle give for a property they cannot apply")
        .isEqualTo("fixtures[0].alias cannot be applied: taken")
        .isEqualTo(ARefusedProperty.theWordsOf("fixtures[0].alias", "taken"));
  }

  @Test
  void every_refused_property_is_named_one_beside_the_next() {
    assertThat(
            BundleRefusal.theWordsOf(
                List.of(new BundleRefusal("a", "one"), new BundleRefusal("b", "two"))))
        .isEqualTo("a cannot be applied: one; b cannot be applied: two");
  }

  @Test
  void a_refusal_naming_no_property_at_all_says_nothing_and_no_row_may_carry_that() {
    assertThat(BundleRefusal.theWordsOf(List.of())).isEmpty();
    assertThat(ScenarioRefusal.theWordsOf(List.of())).isEmpty();
  }
}
