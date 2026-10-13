package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeResponseBytesTest {

  @Test
  void escapes_a_merchant_value_that_would_otherwise_end_the_json_string_it_travels_in() {
    String written =
        StripeJsonWriter.toText(StripeJson.object("receipt_email", StripeJson.text("a\"b\\c")));

    assertThat(written).isEqualTo("{\"receipt_email\":\"a\\\"b\\\\c\"}");
  }

  @Test
  void escapes_every_control_character_a_merchant_value_can_carry() {
    String written =
        StripeJsonWriter.toText(
            StripeJson.object("receipt_email", StripeJson.text("\b\f\n\r\t" + (char) 1)));

    assertThat(written).isEqualTo("{\"receipt_email\":\"\\b\\f\\n\\r\\t\\u0001\"}");
  }

  @Test
  void leaves_a_merchant_value_that_needs_no_escaping_exactly_as_it_arrived() {
    String written =
        StripeJsonWriter.toText(StripeJson.object("receipt_email", StripeJson.text("a b~c")));

    assertThat(written).isEqualTo("{\"receipt_email\":\"a b~c\"}");
  }

  @Test
  void names_the_position_of_the_member_a_json_object_literal_left_without_a_value() {
    assertThatThrownBy(() -> StripeJson.object("id", "x"))
        .hasMessage("a JSON object literal needs a StripeJson value at position 1");
  }

  @Test
  void keeps_the_key_of_an_empty_array_so_the_trail_shows_the_field_was_answered() {
    assertThat(StripeJson.object("payment_method_types", StripeJson.array(List.of())).flatten())
        .containsEntry("payment_method_types", List.of());
    assertThat(
            StripeJson.object(
                    "payment_method_types",
                    StripeJson.array(List.of(StripeJson.text("card"), StripeJson.text("paypal"))))
                .flatten())
        .containsEntry("payment_method_types", List.of("card", "paypal"));
  }

  @Test
  void writes_an_amount_in_the_lexical_form_the_merchant_sent_it_in() {
    String written =
        StripeJsonWriter.toText(
            StripeJson.object(
                "amount",
                StripeJson.number("1099"),
                "livemode",
                StripeJson.bool(false),
                "customer",
                StripeJson.nothing(),
                "payment_method_types",
                StripeJson.array(List.of(StripeJson.text("card")))));

    assertThat(written)
        .isEqualTo(
            "{\"amount\":1099,\"livemode\":false,\"customer\":null,"
                + "\"payment_method_types\":[\"card\"]}");
  }

  @Test
  void flattens_an_answer_into_the_fields_the_audit_trail_keeps() {
    Map<String, List<String>> flattened =
        StripeJson.object(
                "id",
                StripeJson.text("pi_sim_1"),
                "amount",
                StripeJson.number("1099"),
                "livemode",
                StripeJson.bool(false),
                "customer",
                StripeJson.nothing(),
                "error",
                StripeJson.object("type", StripeJson.text("card_error")))
            .flatten();

    assertThat(flattened)
        .containsEntry("id", List.of("pi_sim_1"))
        .containsEntry("amount", List.of("1099"))
        .containsEntry("livemode", List.of("false"))
        .containsEntry("customer", List.of())
        .containsEntry("error.type", List.of("card_error"));
  }

  @Test
  void refuses_a_json_object_literal_whose_members_do_not_pair_up() {
    assertThatThrownBy(() -> StripeJson.object("id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a value for every key");
    StripeJson text = StripeJson.text("x");

    assertThatThrownBy(() -> StripeJson.object(1, text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("String key");
    assertThatThrownBy(() -> StripeJson.object("id", "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("StripeJson value");
  }

  @Test
  void reads_the_secret_key_a_client_sent_however_the_vendor_publishes_it_may_be_sent() {
    assertThat(
            StripeAuthentication.secretKeyIn(Map.of("Authorization", List.of("Bearer sk_test_1"))))
        .isEqualTo("sk_test_1");
    assertThat(
            StripeAuthentication.secretKeyIn(
                Map.of("authorization", List.of("Basic c2tfdGVzdF8xOg=="))))
        .isEqualTo("sk_test_1");
    assertThat(
            StripeAuthentication.secretKeyIn(
                Map.of("Authorization", List.of("Basic c2tfdGVzdF8x"))))
        .isEqualTo("sk_test_1");
  }

  @Test
  void reads_no_secret_key_from_a_header_it_cannot_make_one_out_of() {
    assertThat(StripeAuthentication.secretKeyIn(Map.of())).isNull();
    assertThat(StripeAuthentication.secretKeyIn(Map.of("Authorization", List.of("Digest abc"))))
        .isNull();
    assertThat(
            StripeAuthentication.secretKeyIn(Map.of("Authorization", List.of("Basic not!base64"))))
        .isNull();
    assertThat(StripeAuthentication.secretKeyIn(Map.of("Authorization", List.of("Bearer   "))))
        .isNull();
    assertThat(StripeAuthentication.secretKeyIn(Map.of("Authorization", List.of("Basic Og=="))))
        .isNull();
  }

  @Test
  void reads_no_idempotency_key_from_a_header_that_carries_none() {
    assertThat(StripeIdempotency.keyIn(Map.of())).isNull();
    assertThat(StripeIdempotency.keyIn(Map.of("Idempotency-Key", List.of("  ")))).isNull();
    assertThat(StripeIdempotency.keyIn(Map.of("Idempotency-Key", List.of("k")))).isEqualTo("k");
  }

  @Test
  void tells_two_requests_apart_by_the_endpoint_as_well_as_the_parameters() {
    Map<String, List<String>> form = Map.of("amount", List.of("1099"));

    assertThat(StripeIdempotency.fingerprintOf("POST", "/v1/payment_intents", form))
        .isNotEqualTo(
            StripeIdempotency.fingerprintOf("POST", "/v1/payment_intents/pi/capture", form))
        .isEqualTo(StripeIdempotency.fingerprintOf("POST", "/v1/payment_intents", form));
  }

  @Test
  void tells_two_requests_apart_by_a_repeated_parameter_the_forms_do_not_share() {
    assertThat(
            StripeIdempotency.fingerprintOf(
                "POST", "/v1/payment_intents", Map.of("expand", List.of("customer"))))
        .isNotEqualTo(
            StripeIdempotency.fingerprintOf(
                "POST",
                "/v1/payment_intents",
                Map.of("expand", List.of("customer", "payment_method"))));
  }
}
