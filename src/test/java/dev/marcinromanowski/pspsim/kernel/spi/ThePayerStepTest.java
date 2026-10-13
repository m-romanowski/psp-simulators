package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ThePayerStepTest {

  private static final String A_ROUTE = "/tokenize/sessions/{hostedCardSessionId}";

  @Test
  void a_page_this_build_serves_that_names_no_route_is_refused_because_nothing_could_check_it() {
    assertThatThrownBy(() -> new ThePayerStep.APageThisSimulatorServes("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must name the route it is served at")
        .hasMessageContaining("a page named in prose alone");
  }

  @Test
  void a_payer_page_declared_as_a_vendor_host_rather_than_a_path_of_this_build_is_refused() {
    assertThatThrownBy(
            () -> new ThePayerStep.APageThisSimulatorServes("https://cards.autra.io/tokenize"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("https://cards.autra.io/tokenize")
        .hasMessageContaining("is not a path this build can answer on")
        .hasMessageContaining("NoPageThisSimulatorServes");
  }

  @Test
  void a_page_this_build_serves_tells_a_tester_the_route_and_that_the_vendor_host_is_not_used() {
    ThePayerStep step = new ThePayerStep.APageThisSimulatorServes(A_ROUTE);

    assertThat(step.asATesterReadsIt())
        .contains("This simulator serves the payer's page itself, at " + A_ROUTE)
        .contains("never on the vendor's own host")
        .contains("the payer step on the object's own screen");
  }

  @Test
  void a_way_that_leaves_no_page_this_build_serves_and_says_nothing_instead_is_refused() {
    assertThatThrownBy(() -> new ThePayerStep.NoPageThisSimulatorServes(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say")
        .hasMessageContaining("in this provider's own words what stands in its place");
  }

  @Test
  void a_way_that_leaves_no_page_reads_as_the_providers_own_words_and_nothing_this_build_added() {
    String inTheProvidersWords = "Autra publishes no page for a Pix.";

    assertThat(new ThePayerStep.NoPageThisSimulatorServes(inTheProvidersWords).asATesterReadsIt())
        .isEqualTo(inTheProvidersWords);
  }
}
