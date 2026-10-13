package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class ScenarioRequestHeaderOrderTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");

  private static final String ONE_FLOW = StoryKey.inSpace("process", "one-flow");

  private static final int THE_HEADERS_A_CALL_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_SENDS = 12;

  private static final String
      A_DECISION_IS_TAKEN_ON_HEADERS_A_READER_MAY_YET_BE_SHOWN_AND_TWELVE_LEAVE_479001600_ORDERS =
          "no screen, bundle or control answer prints a scenario request's headers today: the"
              + " engine matches them by name through ScenarioMatch, so this order is the one the"
              + " provider handed over and the first reader to print them inherits it rather than"
              + " an order drawn from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM. Twelve headers leave"
              + " 479001600 orders an unordered copy could draw from, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " entries are in such a map there is no way left to tell it from an ordered one,"
              + " so the request keeps a map whose type carries an order on every run rather than"
              + " one that happened to print in the right order on this one";

  private static Map<String, List<String>> headersOfThatWidth() {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    for (int at = 1; at <= THE_HEADERS_A_CALL_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_SENDS; at++) {
      sent.put("X-Header-" + at, List.of("the value the caller sent " + at + "th"));
    }
    return sent;
  }

  @Test
  void
      a_request_keeps_the_headers_in_the_order_the_caller_sent_them_and_twelve_headers_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, List<String>> sent = headersOfThatWidth();

    ScenarioRequest request = carrying(sent);

    assertThat(request.headers().keySet())
        .as(
            A_DECISION_IS_TAKEN_ON_HEADERS_A_READER_MAY_YET_BE_SHOWN_AND_TWELVE_LEAVE_479001600_ORDERS)
        .containsExactlyElementsOf(sent.keySet());
  }

  @Test
  void
      a_request_keeps_the_headers_the_caller_sent_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    assertThat(carrying(headersOfThatWidth()).headers())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_headers_a_request_hands_the_engine_cannot_be_written_to() {
    ScenarioRequest request = carrying(headersOfThatWidth());

    assertThatThrownBy(
            () -> request.headers().put("X-Header-13", List.of("a header the caller never sent")))
        .as(
            "the engine takes its decision on these headers, so they are the ones the caller sent"
                + " and not ones a later caller added, which also fails if the unmodifiable"
                + " wrapper around the ordered copy is ever stripped")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static ScenarioRequest carrying(Map<String, List<String>> sent) {
    return new ScenarioRequest(
        GROW, RUN, ONE_FLOW, "merchant-1", null, null, null, null, null, null, null, sent);
  }
}
