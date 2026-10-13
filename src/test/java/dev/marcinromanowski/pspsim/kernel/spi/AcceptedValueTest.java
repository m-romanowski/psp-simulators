package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AcceptedValueTest {

  private static final String A_SENTENCE =
      "answer as the live wire does: content type text/html, and every non-ASCII character escaped";

  @Test
  void an_accepted_value_carries_the_wire_value_and_the_sentence_as_two_separate_things() {
    AcceptedValue accepted = new AcceptedValue("LIVE_WIRE", A_SENTENCE);

    assertThat(accepted.value()).isEqualTo("LIVE_WIRE");
    assertThat(accepted.whatItMeans()).isEqualTo(A_SENTENCE);
  }

  @Test
  void a_value_with_nothing_to_send_is_refused_because_picking_it_would_configure_nothing() {
    assertThatThrownBy(() -> new AcceptedValue("   ", A_SENTENCE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sends nothing to the provider");
  }

  @Test
  void a_value_that_says_nothing_about_what_picking_it_does_is_refused() {
    assertThatThrownBy(() -> new AcceptedValue("LIVE_WIRE", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LIVE_WIRE")
        .hasMessageContaining("must say what picking it does");
  }

  @ParameterizedTest
  @ValueSource(strings = {"LIVE_WIRE", "live_wire", "live wire", "Live Wire", " LIVE-WIRE "})
  void a_value_offered_under_its_own_wire_spelling_is_refused_however_it_is_dressed_up(
      String dressedUp) {
    assertThatThrownBy(() -> new AcceptedValue("LIVE_WIRE", dressedUp))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LIVE_WIRE")
        .hasMessageContaining("same jargon in a list");
  }

  @Test
  void a_sentence_that_happens_to_name_the_wire_value_inside_it_is_taken() {
    AcceptedValue accepted =
        new AcceptedValue("LIVE_WIRE", "LIVE_WIRE answers text/html, as the wire itself does");

    assertThat(accepted.whatItMeans())
        .as(
            "the rule refuses a sentence that is nothing but the token, not one that quotes the"
                + " token on its way to saying what picking it does")
        .startsWith("LIVE_WIRE answers");
  }
}
