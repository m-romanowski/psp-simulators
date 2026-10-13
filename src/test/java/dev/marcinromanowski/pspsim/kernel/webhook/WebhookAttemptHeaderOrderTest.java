package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class WebhookAttemptHeaderOrderTest {

  private static final Instant AT = Instant.parse("2026-09-25T12:00:00Z");

  private static final String TARGET = "https://merchant.example/hook";

  private static final int THE_HEADERS_A_WIRE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_RECORDS = 12;

  private static final String
      A_DELIVERY_SCREEN_LISTS_THESE_HEADERS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS =
          "the delivery screen lists an attempt's headers one row per header and the control"
              + " answer prints them in the same order, so they come back in the order the"
              + " attempt put them on the wire. Twelve headers leave 479001600 orders an"
              + " unordered copy could draw from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " entries are in such a map there is no way left to tell it from an ordered one,"
              + " so the attempt keeps a map whose type carries an order on every run rather than"
              + " one that happened to print in the right order on this one";

  private static Map<String, List<String>> aWireOfThatWidth() {
    Map<String, List<String>> onTheWire = new LinkedHashMap<>();
    onTheWire.put("Content-Type", List.of("application/json"));
    for (int at = 2; at <= THE_HEADERS_A_WIRE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_RECORDS; at++) {
      onTheWire.put("X-Header-" + at, List.of("the value the attempt sent " + at + "th"));
    }
    return onTheWire;
  }

  @Test
  void
      an_attempt_keeps_the_headers_in_the_order_it_put_them_on_the_wire_and_twelve_headers_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, List<String>> onTheWire = aWireOfThatWidth();

    WebhookAttempt attempt = recording(onTheWire);

    assertThat(attempt.sentHeaders())
        .as(A_DELIVERY_SCREEN_LISTS_THESE_HEADERS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isNotNull();
    assertThat(attempt.sentHeaders().keySet())
        .as(A_DELIVERY_SCREEN_LISTS_THESE_HEADERS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS)
        .containsExactlyElementsOf(onTheWire.keySet());
  }

  @Test
  void
      an_attempt_keeps_the_headers_it_put_on_the_wire_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    assertThat(recording(aWireOfThatWidth()).sentHeaders())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_headers_an_attempt_hands_a_screen_cannot_be_written_to() {
    WebhookAttempt attempt = recording(aWireOfThatWidth());

    assertThatThrownBy(
            () ->
                attempt
                    .sentHeaders()
                    .put("X-Header-13", List.of("a header the attempt never sent")))
        .as(
            "the attempt keeps this map for as long as the delivery keeps the attempt, so the"
                + " headers and the order a screen shows are the ones that went on the wire and"
                + " not ones a later caller added, which also fails if the unmodifiable wrapper"
                + " around the ordered copy is ever stripped")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static WebhookAttempt recording(Map<String, List<String>> onTheWire) {
    return new WebhookAttempt(
        "whjob_1", 1, AT, TARGET, onTheWire, Acknowledgement.ACKNOWLEDGED, 204, null, "");
  }
}
