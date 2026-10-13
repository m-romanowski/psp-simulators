package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeclaredFollowUpTest {

  @Test
  void a_body_that_names_a_run_hands_that_run_on() {
    assertThat(DeclaredFollowUp.theRunNamedIn(new DeclaredFollowUp("run-a"))).isEqualTo("run-a");
  }

  @Test
  void no_body_names_no_run() {
    assertThat(DeclaredFollowUp.theRunNamedIn(null)).isNull();
  }

  @Test
  void a_body_without_a_run_names_none() {
    assertThat(DeclaredFollowUp.theRunNamedIn(new DeclaredFollowUp(null))).isNull();
  }
}
