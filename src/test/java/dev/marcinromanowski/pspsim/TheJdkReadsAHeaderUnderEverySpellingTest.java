package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TheJdkReadsAHeaderUnderEverySpellingTest {

  private static final String REPLAYED = "Idempotent-Replayed";

  private static final String POLICY = "Content-Security-Policy";

  private static final String WHY_THIS_READER_IS_PINNED =
      "Every claim of this suite that a named header is absent from an answer read over the"
          + " JDK's own client reads that answer through firstValue or allValues and claims the"
          + " one spelling it names. "
          + TheSpellingsOfAHeaderName.ONE_HEADER_ANSWERS_TO_EVERY_SPELLING_OF_ITS_NAME
          + " Those claims are the claim they read as only because this reader answers about the"
          + " header rather than about the spelling, which is a property of the reader and not of"
          + " the claims, so it is pinned here: a reader that stopped folding the case of a name"
          + " would leave every one of those absences passing over an answer carrying the header"
          + " a client would read.";

  @Test
  void a_header_carried_under_another_spelling_is_still_the_first_value_this_reader_answers_with() {
    HttpHeaders answered = asAnAnswerCarrying(Map.of("idempotent-replayed", List.of("true")));

    assertThat(answered.map())
        .as(WHY_THIS_READER_IS_PINNED)
        .containsOnlyKeys("idempotent-replayed");
    assertThat(answered.firstValue(REPLAYED)).as(WHY_THIS_READER_IS_PINNED).hasValue("true");
    assertThat(answered.firstValue("IDEMPOTENT-REPLAYED")).hasValue("true");
    assertThat(answered.firstValue("idempotent-replayed")).hasValue("true");
  }

  @Test
  void a_header_carried_under_another_spelling_is_still_every_value_this_reader_answers_with() {
    HttpHeaders answered = asAnAnswerCarrying(Map.of("content-SECURITY-policy", List.of("a", "b")));

    assertThat(answered.allValues(POLICY)).as(WHY_THIS_READER_IS_PINNED).containsExactly("a", "b");
    assertThat(answered.allValues("CONTENT-SECURITY-POLICY")).containsExactly("a", "b");
  }

  @Test
  void a_header_no_spelling_of_which_was_carried_reads_as_nothing_through_this_reader() {
    HttpHeaders answered = asAnAnswerCarrying(Map.of("Content-Type", List.of("application/json")));

    assertThat(answered.firstValue(REPLAYED)).as(WHY_THIS_READER_IS_PINNED).isEmpty();
    assertThat(answered.allValues(POLICY)).as(WHY_THIS_READER_IS_PINNED).isEmpty();
  }

  private static HttpHeaders asAnAnswerCarrying(Map<String, List<String>> carried) {
    return HttpHeaders.of(carried, (name, value) -> true);
  }
}
