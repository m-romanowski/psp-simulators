package dev.marcinromanowski.pspsim.kernel.smoke;

import static dev.marcinromanowski.pspsim.kernel.smoke.Samples.A_PUBLISHED_EXAMPLE;
import static dev.marcinromanowski.pspsim.kernel.smoke.Samples.published;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ContractExpectationTest {

  private static final Published STATUS_ONE = published("1", A_PUBLISHED_EXAMPLE);

  @Test
  void a_field_carrying_the_value_the_sample_gives_meets_the_expectation() {
    assertThat(new ContractExpectation("status", STATUS_ONE).isMetBy("1")).isTrue();
  }

  @Test
  void a_field_carrying_another_value_or_nothing_does_not_meet_it() {
    ContractExpectation expectation = new ContractExpectation("status", STATUS_ONE);

    assertThat(expectation.isMetBy("0")).isFalse();
    assertThat(expectation.isMetBy(null)).isFalse();
  }

  @Test
  void an_expectation_that_names_no_field_or_no_sampled_value_is_refused() {
    assertThatThrownBy(() -> new ContractExpectation(" ", STATUS_ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field");
    assertThatThrownBy(() -> new ContractExpectation(null, STATUS_ONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("field");
    assertThatThrownBy(() -> new ContractExpectation("status", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("vendor sample");
  }
}
