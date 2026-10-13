package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProviderObjectKindTest {

  private static final String WHAT_ONE_IS =
      "A Pix waiting for its payer to pay the payload behind its QR code, with no card in it.";

  @Test
  void a_kind_carries_the_provider_word_a_list_prints_and_the_sentence_a_screen_prints_under_it() {
    ProviderObjectKind kind = new ProviderObjectKind("a Pix", WHAT_ONE_IS);

    assertThat(kind.asThisProviderNamesIt()).isEqualTo("a Pix");
    assertThat(kind.whatItIsInItsOwnWords()).isEqualTo(WHAT_ONE_IS);
  }

  @Test
  void a_provider_that_names_no_word_for_a_kind_of_object_is_refused_rather_than_printed_blank() {
    assertThatThrownBy(() -> new ProviderObjectKind("   ", WHAT_ONE_IS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the column a tester reads before any other");
  }

  @Test
  void a_word_with_no_sentence_behind_it_is_refused_because_the_word_alone_has_to_be_recognised() {
    assertThatThrownBy(() -> new ProviderObjectKind("a Pix", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a word a tester either knows or does not");
  }

  @Test
  void prose_handed_in_as_the_word_a_list_prints_is_refused_and_the_refusal_quotes_it() {
    assertThatThrownBy(() -> new ProviderObjectKind(WHAT_ONE_IS, WHAT_ONE_IS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(WHAT_ONE_IS)
        .hasMessageContaining("inside one table cell");
  }

  @Test
  void a_word_carrying_a_comma_is_a_word_and_is_admitted() {
    assertThat(new ProviderObjectKind("a hold, captured only in full", WHAT_ONE_IS))
        .isEqualTo(new ProviderObjectKind("a hold, captured only in full", WHAT_ONE_IS));
  }
}
