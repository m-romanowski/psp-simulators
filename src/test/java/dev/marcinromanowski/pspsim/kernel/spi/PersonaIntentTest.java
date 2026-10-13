package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonaIntentTest {

  @Test
  void every_intent_answers_to_the_name_a_request_spells_it_with() {
    for (PersonaIntent intent : PersonaIntent.values()) {
      assertThat(PersonaIntent.of(intent.publishedName())).contains(intent);
    }
  }

  @Test
  void the_published_names_are_the_hyphenated_ones_a_request_carries() {
    assertThat(PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS.publishedName())
        .isEqualTo("a-saved-card-that-succeeds");
    assertThat(PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED.publishedName())
        .isEqualTo("a-saved-card-that-is-declined");
    assertThat(PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST.publishedName())
        .isEqualTo("a-saved-card-whose-capture-answer-is-lost");
  }

  @Test
  void a_name_no_intent_answers_to_resolves_to_none() {
    assertThat(PersonaIntent.of("a-saved-card-that-succeeds ")).isEmpty();
    assertThat(PersonaIntent.of("A_SAVED_CARD_THAT_SUCCEEDS")).isEmpty();
    assertThat(PersonaIntent.of("")).isEmpty();
  }

  @Test
  void the_list_of_published_names_offers_every_intent_separated_for_a_diagnostic() {
    List<String> listed = List.of(PersonaIntent.thePublishedNames().split(", "));

    List<String> declared = new ArrayList<>();
    for (PersonaIntent intent : PersonaIntent.values()) {
      declared.add(intent.publishedName());
    }
    assertThat(listed).containsExactlyElementsOf(declared);
  }

  @Test
  void every_intent_says_what_a_tester_gets_in_words_that_name_no_provider_internals() {
    for (PersonaIntent intent : PersonaIntent.values()) {
      assertThat(intent.whatATesterGets()).isNotBlank();
      assertThat(intent.whatATesterGets()).doesNotContain("_");
    }
  }
}
