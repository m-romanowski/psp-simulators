package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class WebhookPayloadFieldOrderTest {

  private static final int THE_FIELDS_A_PAYLOAD_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES = 12;

  private static final String
      A_DELIVERY_SCREEN_LISTS_THESE_HEADERS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS =
          "the delivery screen lists a payload's wire headers one row per header, the control"
              + " answer for a delivery prints them in the same order, and the merchant endpoint"
              + " puts them on the wire in that order, so they are read back in the order the"
              + " payload was built in. Twelve headers leave 479001600 orders an unordered copy"
              + " could draw from the keys' hashes and the salt java.util.ImmutableCollections"
              + " draws once per JVM, so this assertion cannot pass by coincidence on a build"
              + " that copies them unordered";

  private static final String
      A_DELIVERY_SCREEN_LISTS_THESE_BODY_FIELDS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS =
          "the delivery screen lists a payload's sanitized body one row per field, the control"
              + " answer for a delivery prints it in the same order, and a stored delivery is"
              + " written as JSON in that order, so the fields are read back in the order the"
              + " payload was built in. Twelve fields leave 479001600 orders an unordered copy"
              + " could draw from the keys' hashes and the salt java.util.ImmutableCollections"
              + " draws once per JVM, so this assertion cannot pass by coincidence on a build"
              + " that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and"
              + " Collections.unmodifiableMap over an ordered copy keeps the order at runtime"
              + " while leaving no type a later caller or a later reviewer could read it off, so"
              + " the payload hands out maps whose type carries an order on every run rather than"
              + " ones that happened to print in the right order on this one. A payload is the"
              + " producer every webhook attempt, every stored delivery and every audited"
              + " webhook row copies its fields from";

  private static final String A_PAYLOAD_IS_READ_FOR_AS_LONG_AS_THE_DELIVERY_THAT_CARRIES_IT_LIVES =
      "a delivery hands the same payload to the merchant endpoint, to the attempt it records, to"
          + " the row the trail stores and to the screen that lists it, so a caller may not add a"
          + " field to it or reorder it after the fact, which also fails if the unmodifiable"
          + " wrapper around the ordered copy is ever stripped";

  private static WebhookPayload aPayloadOfThatWidth() {
    return new WebhookPayload(
        "application/json",
        headersOfThatWidth(),
        "{}".getBytes(StandardCharsets.UTF_8),
        bodyOfThatWidth());
  }

  private static Map<String, List<String>> headersOfThatWidth() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (int at = 1;
        at < THE_FIELDS_A_PAYLOAD_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES;
        at++) {
      headers.put("X-Provider-Header-" + at, List.of("the value the provider signed " + at + "th"));
    }
    return headers;
  }

  private static Map<String, List<String>> bodyOfThatWidth() {
    Map<String, List<String>> body = new LinkedHashMap<>();
    for (int at = 1;
        at <= THE_FIELDS_A_PAYLOAD_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES;
        at++) {
      body.put("data.field" + at, List.of("the value the provider sent " + at + "th"));
    }
    return body;
  }

  private static List<String> theWireHeadersExpected() {
    List<String> named = new ArrayList<>();
    named.add("Content-Type");
    named.addAll(headersOfThatWidth().keySet());
    return named;
  }

  @Test
  void
      a_payload_states_the_content_type_first_and_then_the_provider_headers_in_the_order_it_was_given_them_and_twelve_headers_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    assertThat(List.copyOf(aPayloadOfThatWidth().wireHeaders().keySet()))
        .as(A_DELIVERY_SCREEN_LISTS_THESE_HEADERS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(theWireHeadersExpected());
  }

  @Test
  void
      the_provider_headers_of_a_payload_are_the_wire_headers_without_the_content_type_in_the_same_order() {
    assertThat(List.copyOf(aPayloadOfThatWidth().providerHeaders().keySet()))
        .as(A_DELIVERY_SCREEN_LISTS_THESE_HEADERS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(headersOfThatWidth().keySet()));
  }

  @Test
  void
      a_payload_keeps_the_sanitized_body_in_the_order_the_provider_wrote_it_and_twelve_fields_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    assertThat(List.copyOf(aPayloadOfThatWidth().sanitizedBody().keySet()))
        .as(A_DELIVERY_SCREEN_LISTS_THESE_BODY_FIELDS_ONE_PER_ROW_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(bodyOfThatWidth().keySet()));
  }

  @Test
  void
      a_payload_hands_out_its_headers_and_its_body_in_maps_that_carry_an_order_rather_than_ones_drawn_from_a_per_jvm_salt() {
    WebhookPayload payload = aPayloadOfThatWidth();

    assertThat(payload.wireHeaders())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
    assertThat(payload.providerHeaders())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
    assertThat(payload.sanitizedBody())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_headers_and_the_body_a_payload_hands_a_reader_cannot_be_written_to() {
    WebhookPayload payload = aPayloadOfThatWidth();

    assertThatThrownBy(
            () -> payload.wireHeaders().put("X-Added-Later", List.of("a header nothing sent")))
        .as(A_PAYLOAD_IS_READ_FOR_AS_LONG_AS_THE_DELIVERY_THAT_CARRIES_IT_LIVES)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () -> payload.providerHeaders().put("X-Added-Later", List.of("a header nothing sent")))
        .as(A_PAYLOAD_IS_READ_FOR_AS_LONG_AS_THE_DELIVERY_THAT_CARRIES_IT_LIVES)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () -> payload.sanitizedBody().put("data.addedLater", List.of("a field nothing sent")))
        .as(A_PAYLOAD_IS_READ_FOR_AS_LONG_AS_THE_DELIVERY_THAT_CARRIES_IT_LIVES)
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
