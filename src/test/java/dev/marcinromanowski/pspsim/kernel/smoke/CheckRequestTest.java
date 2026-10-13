package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class CheckRequestTest {

  private static final int THE_HEADERS_A_CHECK_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_NAMES = 12;

  private static final String
      A_CHECK_PUTS_THESE_HEADERS_ON_THE_WIRE_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS =
          "the local transport reads a request's headers in their iteration order and sets them"
              + " on the call it dials, so the order a check names them in is the order the"
              + " simulator receives them in and the order the inbound trail row a tester reads"
              + " lists them in. Twelve headers leave 479001600 orders an unordered copy could"
              + " draw from the keys' hashes and the salt java.util.ImmutableCollections draws"
              + " once per JVM, so this assertion cannot pass by coincidence on a build that"
              + " copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " headers are in such a map there is no way left to tell it from an ordered one,"
              + " so a check keeps a map whose type carries an order on every run rather than one"
              + " that happened to print in the right order on this one";

  @Test
  void a_form_request_carries_the_urlencoded_content_type_and_the_encoded_fields() {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("pageField[fullName]", "Israel Israeli");
    fields.put("sum", "120.50");

    CheckRequest request = CheckRequest.form("POST", "/api/x", fields);

    assertThat(request.headers())
        .containsEntry("Content-Type", "application/x-www-form-urlencoded");
    assertThat(request.body()).isEqualTo("pageField%5BfullName%5D=Israel+Israeli&sum=120.50");
  }

  @Test
  void a_json_request_carries_the_json_content_type_and_the_body_as_written() {
    CheckRequest request = CheckRequest.json("PUT", "/__simulator/grow/config", "{\"a\":1}");

    assertThat(request.headers()).containsEntry("Content-Type", "application/json");
    assertThat(request.body()).isEqualTo("{\"a\":1}");
  }

  @Test
  void a_get_request_carries_no_body_and_no_content_type() {
    CheckRequest request = CheckRequest.get("/v1/payment_intents/pi_sim_1");

    assertThat(request.body()).isNull();
    assertThat(request.headers()).isEmpty();
  }

  @Test
  void a_carried_header_is_added_without_losing_the_ones_already_there() {
    CheckRequest request =
        CheckRequest.json("POST", "/x", "{}").carrying("Authorization", "Bearer k");

    assertThat(request.headers())
        .containsEntry("Content-Type", "application/json")
        .containsEntry("Authorization", "Bearer k");
  }

  @Test
  void a_request_that_names_no_verb_or_no_route_a_client_could_dial_is_refused() {
    assertThatThrownBy(() -> new CheckRequest(" ", "/x", Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("method");
    assertThatThrownBy(() -> new CheckRequest("GET", "x", Map.of(), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("path");
  }

  private static Map<String, String> theHeadersOfThatWidth() {
    Map<String, String> named = new LinkedHashMap<>();
    for (int at = 1; at <= THE_HEADERS_A_CHECK_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_NAMES; at++) {
      named.put("X-Check-Header-" + at, "the value the check named " + at + "th");
    }
    return named;
  }

  @Test
  void
      a_request_names_its_headers_in_the_order_the_check_wrote_them_and_twelve_headers_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, String> named = theHeadersOfThatWidth();

    CheckRequest request = new CheckRequest("POST", "/v1/payment_intents", named, "{}");

    assertThat(List.copyOf(request.headers().keySet()))
        .as(A_CHECK_PUTS_THESE_HEADERS_ON_THE_WIRE_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(List.copyOf(named.keySet()));
  }

  @Test
  void a_carried_header_is_named_last_after_the_ones_the_check_had_already_named() {
    Map<String, String> named = theHeadersOfThatWidth();

    CheckRequest request =
        new CheckRequest("POST", "/v1/payment_intents", named, "{}")
            .carrying("X-Carried-Last", "the value a later hop added");

    List<String> inTheOrderTheyGoOnTheWire = new ArrayList<>(theHeadersOfThatWidth().keySet());
    inTheOrderTheyGoOnTheWire.add("X-Carried-Last");
    assertThat(List.copyOf(request.headers().keySet()))
        .as(A_CHECK_PUTS_THESE_HEADERS_ON_THE_WIRE_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(inTheOrderTheyGoOnTheWire);
  }

  @Test
  void
      a_request_keeps_its_headers_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    assertThat(new CheckRequest("GET", "/x", theHeadersOfThatWidth(), null).headers())
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void the_headers_a_request_hands_a_transport_cannot_be_written_to() {
    CheckRequest request = new CheckRequest("GET", "/x", theHeadersOfThatWidth(), null);

    assertThatThrownBy(
            () -> request.headers().put("X-Added-Later", "a header the check never named"))
        .as(
            "the transport, the namespace that stamps the run and the signature a provider check"
                + " computes all read the same map, so none of them may add to it or reorder it,"
                + " which also fails if the unmodifiable wrapper around the ordered copy is ever"
                + " stripped")
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
