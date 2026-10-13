package dev.marcinromanowski.pspsim.kernel.repro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class CarriedFieldOrderTest {

  private static final int THE_FIELDS_AN_EXCHANGE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES =
      12;

  private static final String
      A_BUNDLE_IS_READ_AND_DIFFED_FIELD_BY_FIELD_AND_TWELVE_FIELDS_LEAVE_479001600_ORDERS =
          "a tester reads the downloaded reproduction bundle field by field and diffs one capture"
              + " against another, so the fields come back in the order the run recorded them."
              + " Twelve fields leave 479001600 orders an unordered copy could draw from the"
              + " keys' hashes and the salt java.util.ImmutableCollections draws once per JVM, so"
              + " this assertion cannot pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " entries are in such a map there is no way left to tell it from an ordered one,"
              + " so the bundle is handed a map whose type carries an order on every run rather"
              + " than one that happened to print in the right order on this one";

  private static final String A_BUNDLE_IS_WHAT_THE_RUN_RECORDED_AND_NOT_WHAT_A_LATER_CALLER_ADDED =
      "the capture keeps this map for as long as the bundle is held, so the fields and the"
          + " order a reader sees are the ones the run recorded and not ones a later caller"
          + " added, which also fails if the unmodifiable wrapper around the ordered copy is"
          + " ever stripped";

  private static Map<String, List<String>> fieldsOfThatWidth() {
    Map<String, List<String>> recorded = new LinkedHashMap<>();
    for (int at = 1;
        at <= THE_FIELDS_AN_EXCHANGE_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_CARRIES;
        at++) {
      recorded.put("field_" + at, List.of("the value the run recorded " + at + "th"));
    }
    return recorded;
  }

  @Test
  void
      an_exchange_carries_its_headers_and_its_body_in_the_order_the_run_recorded_them_and_twelve_fields_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, List<String>> recorded = fieldsOfThatWidth();

    RecordedExchange exchange = anExchangeCarrying(recorded, recorded);

    assertThat(exchange.carriedHeaders().keySet())
        .as(A_BUNDLE_IS_READ_AND_DIFFED_FIELD_BY_FIELD_AND_TWELVE_FIELDS_LEAVE_479001600_ORDERS)
        .containsExactlyElementsOf(recorded.keySet());
    assertThat(exchange.carriedBody().keySet())
        .as(A_BUNDLE_IS_READ_AND_DIFFED_FIELD_BY_FIELD_AND_TWELVE_FIELDS_LEAVE_479001600_ORDERS)
        .containsExactlyElementsOf(recorded.keySet());
  }

  @Test
  void
      an_exchange_carries_its_headers_and_its_body_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    RecordedExchange exchange = anExchangeCarrying(fieldsOfThatWidth(), fieldsOfThatWidth());

    assertThat(exchange.carriedHeaders())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
    assertThat(exchange.carriedBody())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_headers_and_the_body_an_exchange_hands_a_reader_cannot_be_written_to() {
    RecordedExchange exchange = anExchangeCarrying(fieldsOfThatWidth(), fieldsOfThatWidth());

    assertThatThrownBy(
            () -> exchange.carriedHeaders().put("field_13", List.of("a field no run recorded")))
        .as(A_BUNDLE_IS_WHAT_THE_RUN_RECORDED_AND_NOT_WHAT_A_LATER_CALLER_ADDED)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () -> exchange.carriedBody().put("field_13", List.of("a field no run recorded")))
        .as(A_BUNDLE_IS_WHAT_THE_RUN_RECORDED_AND_NOT_WHAT_A_LATER_CALLER_ADDED)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void
      an_attempt_carries_the_headers_it_put_on_the_wire_in_the_order_it_sent_them_and_twelve_headers_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, List<String>> sent = fieldsOfThatWidth();

    RecordedAttempt attempt = anAttemptCarrying(sent);

    assertThat(attempt.carriedHeaders().keySet())
        .as(A_BUNDLE_IS_READ_AND_DIFFED_FIELD_BY_FIELD_AND_TWELVE_FIELDS_LEAVE_479001600_ORDERS)
        .containsExactlyElementsOf(sent.keySet());
  }

  @Test
  void
      an_attempt_carries_the_headers_it_put_on_the_wire_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    assertThat(anAttemptCarrying(fieldsOfThatWidth()).carriedHeaders())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_headers_an_attempt_hands_a_reader_cannot_be_written_to() {
    RecordedAttempt attempt = anAttemptCarrying(fieldsOfThatWidth());

    assertThatThrownBy(
            () -> attempt.carriedHeaders().put("field_13", List.of("a header no attempt sent")))
        .as(A_BUNDLE_IS_WHAT_THE_RUN_RECORDED_AND_NOT_WHAT_A_LATER_CALLER_ADDED)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void
      a_callback_carries_its_payload_in_the_order_the_run_rendered_it_and_twelve_fields_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, List<String>> rendered = fieldsOfThatWidth();

    RecordedCallback callback = aCallbackCarrying(rendered);

    assertThat(callback.carriedPayload().keySet())
        .as(A_BUNDLE_IS_READ_AND_DIFFED_FIELD_BY_FIELD_AND_TWELVE_FIELDS_LEAVE_479001600_ORDERS)
        .containsExactlyElementsOf(rendered.keySet());
  }

  @Test
  void
      a_callback_carries_its_payload_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    assertThat(aCallbackCarrying(fieldsOfThatWidth()).carriedPayload())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_payload_a_callback_hands_a_reader_cannot_be_written_to() {
    RecordedCallback callback = aCallbackCarrying(fieldsOfThatWidth());

    assertThatThrownBy(
            () -> callback.carriedPayload().put("field_13", List.of("a field no callback carried")))
        .as(A_BUNDLE_IS_WHAT_THE_RUN_RECORDED_AND_NOT_WHAT_A_LATER_CALLER_ADDED)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static RecordedExchange anExchangeCarrying(
      Map<String, List<String>> headers, Map<String, List<String>> body) {
    return new RecordedExchange(
        0,
        "aud_1",
        "PROVIDER_API",
        "INBOUND_REQUEST",
        "POST",
        "/payments",
        200,
        null,
        null,
        null,
        null,
        1,
        null,
        headers,
        body);
  }

  private static RecordedAttempt anAttemptCarrying(Map<String, List<String>> sent) {
    return new RecordedAttempt(1, null, null, "ACKNOWLEDGED", 200, null, null, sent);
  }

  private static RecordedCallback aCallbackCarrying(Map<String, List<String>> rendered) {
    return new RecordedCallback(
        "whd_1",
        "evt_1",
        null,
        null,
        "PENDING",
        null,
        0,
        3,
        null,
        null,
        null,
        "application/json",
        rendered,
        List.of());
  }
}
