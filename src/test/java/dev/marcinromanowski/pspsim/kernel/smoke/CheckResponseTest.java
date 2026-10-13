package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class CheckResponseTest {

  private static final int THE_HEADERS_AN_ANSWER_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES = 12;

  private static final String
      THE_FIRST_HEADER_OF_A_NAME_WINS_AND_TWELVE_HEADERS_LEAVE_479001600_ORDERS =
          "a header is read by walking the answer's headers in order and taking the first whose"
              + " name matches whatever case it was written in, so the order the route answered"
              + " in decides which of two headers of the same name in two cases a check compares"
              + " against a vendor sample. Twelve headers leave 479001600 orders an unordered"
              + " copy could draw from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " headers are in such a map there is no way left to tell it from an ordered one,"
              + " so an answer keeps a map whose type carries an order on every run rather than"
              + " one that happened to print in the right order on this one";

  @Test
  void a_header_is_found_whatever_case_it_was_written_in() {
    CheckResponse answer =
        new CheckResponse(200, Map.of("idempotent-replayed", List.of("true")), "{}");

    assertThat(answer.header("Idempotent-Replayed")).isEqualTo("true");
  }

  @Test
  void a_header_the_answer_did_not_carry_reads_as_nothing() {
    assertThat(new CheckResponse(200, Map.of(), "{}").header("Stripe-Signature")).isNull();
  }

  @Test
  void a_header_present_with_no_value_reads_as_nothing_rather_than_as_an_empty_one() {
    CheckResponse answer = new CheckResponse(200, Map.of("Stripe-Signature", List.of()), "{}");

    assertThat(answer.header("Stripe-Signature")).isNull();
  }

  @Test
  void the_first_value_is_read_when_a_header_travelled_more_than_once() {
    CheckResponse answer =
        new CheckResponse(200, Map.of("Set-Cookie", List.of("first", "second")), "{}");

    assertThat(answer.header("Set-Cookie")).isEqualTo("first");
  }

  @Test
  void an_answer_whose_body_was_never_read_is_refused() {
    assertThatThrownBy(() -> new CheckResponse(200, Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("body");
  }

  private static Map<String, List<String>> theHeadersOfThatWidth() {
    Map<String, List<String>> answered = new LinkedHashMap<>();
    for (int at = 1;
        at <= THE_HEADERS_AN_ANSWER_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES;
        at++) {
      answered.put("X-Answered-Header-" + at, List.of("the value the route answered " + at + "th"));
    }
    return answered;
  }

  @Test
  void
      an_answer_keeps_its_headers_in_the_order_the_route_answered_with_and_twelve_headers_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, List<String>> answered = theHeadersOfThatWidth();

    CheckResponse answer = new CheckResponse(200, answered, "{}");

    assertThat(List.copyOf(answer.headers().keySet()))
        .as(THE_FIRST_HEADER_OF_A_NAME_WINS_AND_TWELVE_HEADERS_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(answered.keySet()));
  }

  @Test
  void the_first_of_two_headers_of_one_name_in_two_cases_is_the_one_a_check_reads() {
    Map<String, List<String>> answered = new LinkedHashMap<>();
    answered.put("Idempotent-Replayed", List.of("the value answered first"));
    answered.put("idempotent-replayed", List.of("the value answered second"));

    CheckResponse answer = new CheckResponse(200, answered, "{}");

    assertThat(answer.header("Idempotent-Replayed"))
        .as(THE_FIRST_HEADER_OF_A_NAME_WINS_AND_TWELVE_HEADERS_LEAVE_479001600_ORDERS)
        .isEqualTo("the value answered first");
  }

  @Test
  void
      an_answer_keeps_its_headers_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    assertThat(new CheckResponse(200, theHeadersOfThatWidth(), "{}").headers())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_headers_an_answer_hands_a_check_cannot_be_written_to() {
    CheckResponse answer = new CheckResponse(200, theHeadersOfThatWidth(), "{}");

    assertThatThrownBy(
            () -> answer.headers().put("X-Added-Later", List.of("a header the route never sent")))
        .as(
            "a check reads the same answer for every expectation it compares against a vendor"
                + " sample, so no expectation may add to it or reorder it, which also fails if"
                + " the unmodifiable wrapper around the ordered copy is ever stripped")
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
