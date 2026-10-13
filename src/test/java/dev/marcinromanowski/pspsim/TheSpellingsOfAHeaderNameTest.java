package dev.marcinromanowski.pspsim;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TheSpellingsOfAHeaderNameTest {

  private static final String REPLAYED = "Idempotent-Replayed";

  private static final String CONTENT_TYPE = "Content-Type";

  private static final String JSON = "application/json";

  @Test
  void a_map_carrying_no_spelling_of_the_name_carries_no_header_of_that_name() {
    Map<String, List<String>> headers =
        Map.of(CONTENT_TYPE, List.of(JSON), "Request-Id", List.of("req_1"));

    assertThat(headers)
        .as("no spelling of %s is a key of this map, so the absence holds", REPLAYED)
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED))
        .containsEntry(CONTENT_TYPE, List.of(JSON));
  }

  @Test
  void a_header_carried_under_the_spelling_the_claim_names_fails_the_claim() {
    Map<String, List<String>> headers = Map.of(REPLAYED, List.of("true"));

    assertThatCode(
            () -> assertThat(headers).doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED)))
        .as("the spelling the claim names is the one case every form of this claim catches")
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void a_header_carried_under_a_spelling_the_claim_does_not_name_fails_the_claim_the_same_way() {
    for (String spelling :
        List.of("idempotent-replayed", "IDEMPOTENT-REPLAYED", "Idempotent-replayed")) {
      Map<String, List<String>> headers = Map.of(spelling, List.of("true"));

      assertThat(headers)
          .as("%s is the one spelling of this header this map carries", spelling)
          .containsOnlyKeys(spelling);
      assertThatCode(
              () -> assertThat(headers).doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED)))
          .as(
              "a client reading %s reads the header %s, so the absence of %s is false of this"
                  + " map and the claim fails",
              spelling, REPLAYED, REPLAYED)
          .isInstanceOf(AssertionError.class);
    }
  }

  @Test
  void the_failure_names_the_header_and_says_every_spelling_of_it_was_looked_for() {
    Map<String, List<String>> headers = Map.of("idempotent-replayed", List.of("true"));

    assertThatCode(
            () -> assertThat(headers).doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED)))
        .as("the author of a failing absence reads why one spelling was not the claim")
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(REPLAYED)
        .hasMessageContaining("under any spelling of that name")
        .hasMessageContaining("case-insensitive");
  }

  @Test
  void a_header_map_that_carries_one_value_a_name_is_read_the_same_way_as_one_that_carries_many() {
    Map<String, String> oneValueEach = Map.of("idempotent-replayed", "true");

    assertThatCode(
            () -> assertThat(oneValueEach).doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED)))
        .as("a map of one value a name carries the same header names as a map of many")
        .isInstanceOf(AssertionError.class);
    assertThat(Map.of(CONTENT_TYPE, JSON)).doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED));
  }

  @Test
  void a_map_this_claim_reads_may_carry_a_name_nothing_was_asked_about() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(CONTENT_TYPE, List.of(JSON));
    headers.put("CONNECTION", List.of("close"));

    assertThat(headers)
        .as("the claim is about one name and says nothing about the rest of the map")
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED))
        .has(aHeaderOfThisNameUnderAnySpelling("Connection"));
  }

  @Test
  void an_empty_map_carries_no_header_of_any_name() {
    assertThat(Map.<String, List<String>>of())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(REPLAYED))
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(CONTENT_TYPE));
  }
}
