package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldlineJsonTest {

  @Test
  void an_amount_keeps_the_digits_the_client_wrote_rather_than_a_number_parsed_and_reprinted() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"amount\":500.00}").orElseThrow();

    assertThat(read.number(WorldlineFields.AMOUNT)).contains("500.00");
    assertThat(WorldlineJsonWriter.toText(read)).isEqualTo("{\"amount\":500.00}");
  }

  @Test
  void a_nested_object_is_read_under_its_own_name_and_a_string_member_under_that() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn(
                "{\"cardPaymentMethodSpecificInput\":{\"token\":\"token-abc\"}}")
            .orElseThrow();

    assertThat(
            read.object(WorldlineFields.CARD_PAYMENT_METHOD_SPECIFIC_INPUT)
                .orElseThrow()
                .text(WorldlineFields.TOKEN))
        .contains("token-abc");
  }

  @Test
  void a_body_that_is_not_a_json_object_is_read_as_nothing_rather_than_raising() {
    assertThat(WorldlineJsonReader.objectIn("[1,2]")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":1} trailing")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":tru}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\q\"}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\uZZZZ\"}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\u004"))
        .as(
            "the four digits of an escape are read as one, so a body that stops inside them is"
                + " unreadable and never a string with a character this reader invented")
        .isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\u0041")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":1;\"b\":2}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("[1;2]")).isEmpty();
  }

  @Test
  void every_json_value_worldline_can_send_survives_being_read_and_written_again() {
    String source =
        "{\"text\":\"a\\\"b\\\\c\\n\",\"flag\":true,\"off\":false,\"nothing\":null,"
            + "\"list\":[1,\"two\",{}],\"empty\":{},\"blank\":[]}";

    WorldlineJson.JsonObject read = WorldlineJsonReader.objectIn(source).orElseThrow();

    assertThat(WorldlineJsonWriter.toText(read)).isEqualTo(source);
  }

  @Test
  void a_character_outside_printable_ascii_is_written_as_an_escape_and_read_back_whole() {
    WorldlineJson.JsonObject written =
        WorldlineJson.object(Map.of("message", WorldlineJson.text("n\u00e3o \u0001")));

    String text = WorldlineJsonWriter.toText(written);

    assertThat(text).isEqualTo("{\"message\":\"n\\u00e3o \\u0001\"}");
    assertThat(WorldlineJsonReader.objectIn(text).orElseThrow().text("message"))
        .contains("n\u00e3o \u0001");
  }

  @Test
  void flattening_names_a_nested_member_by_its_path_and_an_array_item_by_the_arrays_own_path() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn(
                "{\"errors\":[{\"code\":\"X\",\"message\":\"y\"}],\"cardPaymentMethodSpecificInput\":{\"token\":\"s\"},"
                    + "\"n\":1,\"f\":false,\"z\":null}")
            .orElseThrow();

    Map<String, List<String>> flattened = read.flatten();

    assertThat(flattened)
        .containsEntry("errors.code", List.of("X"))
        .containsEntry("errors.message", List.of("y"))
        .containsEntry("cardPaymentMethodSpecificInput.token", List.of("s"))
        .containsEntry("n", List.of("1"))
        .containsEntry("f", List.of("false"))
        .containsEntry("z", List.of(""));
  }

  @Test
  void an_empty_array_is_flattened_to_a_named_key_with_no_value_under_it() {
    assertThat(WorldlineJsonReader.objectIn("{\"errors\":[]}").orElseThrow().flatten())
        .containsEntry(WorldlineFields.ERRORS, List.of());
  }

  @Test
  void a_member_read_under_the_wrong_json_type_is_absent_rather_than_coerced() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"amount\":\"500.00\",\"merchantReference\":12345}")
            .orElseThrow();

    assertThat(read.number(WorldlineFields.AMOUNT)).isEmpty();
    assertThat(read.text(WorldlineFields.MERCHANT_REFERENCE)).isEmpty();
    assertThat(read.object(WorldlineFields.AMOUNT)).isEmpty();
    assertThat(read.has(WorldlineFields.AMOUNT)).isTrue();
    assertThat(read.has(WorldlineFields.CARD)).isFalse();
  }

  @Test
  void an_amount_sent_as_a_string_is_still_the_lexical_form_a_caller_wrote() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"amount\":\"500.00\"}").orElseThrow();

    assertThat(read.text(WorldlineFields.AMOUNT)).contains("500.00");
  }

  @Test
  void
      a_negative_number_keeps_its_sign_and_every_other_number_form_worldline_could_send_reads_back() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn(
                "{\"a\":-500.00,\"b\":0,\"c\":1e3,\"d\":-0.1,\"e\":12.50,\"f\":1E-3}")
            .orElseThrow();

    assertThat(read.number("a")).contains("-500.00");
    assertThat(read.number("b")).contains("0");
    assertThat(read.number("c")).contains("1e3");
    assertThat(read.number("d")).contains("-0.1");
    assertThat(read.number("e")).contains("12.50");
    assertThat(read.number("f")).contains("1E-3");
  }

  @Test
  void a_minus_sign_with_no_digits_after_it_is_read_as_nothing_rather_than_as_a_number() {
    assertThat(WorldlineJsonReader.objectIn("{\"a\":-}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":x}")).isEmpty();
  }

  @Test
  void a_four_digit_escape_is_read_as_the_character_it_names_and_a_truncated_one_as_nothing() {
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\u0041\\u00e3\"}").orElseThrow().text("a"))
        .contains("A\u00e3");
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\u004")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\u00g1\"}")).isEmpty();
  }

  @Test
  void every_short_escape_json_publishes_is_read_as_the_character_it_names() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"a\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"}").orElseThrow();

    assertThat(read.text("a")).contains("\"\\/\b\f\n\r\t");
  }

  @Test
  void an_array_that_holds_nothing_but_blanks_is_read_as_an_array_of_no_elements() {
    assertThat(WorldlineJsonReader.objectIn("{\"a\":[ ]}").orElseThrow().flatten())
        .containsEntry("a", List.of());
    assertThat(WorldlineJsonReader.objectIn("{\"a\":[\n\t ]}").orElseThrow().flatten())
        .containsEntry("a", List.of());
  }

  @Test
  void an_object_that_holds_nothing_but_blanks_is_read_as_an_object_of_no_members() {
    assertThat(WorldlineJsonReader.objectIn("{ }").orElseThrow().members()).isEmpty();
  }

  @Test
  void an_array_of_several_elements_keeps_every_one_of_them_in_the_order_they_were_written() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"a\":[1,2,3],\"b\":[[4,5],{\"c\":6}]}").orElseThrow();

    assertThat(read.flatten())
        .containsEntry("a", List.of("1", "2", "3"))
        .containsEntry("b", List.of("4", "5"))
        .containsEntry("b.c", List.of("6"));
  }

  @Test
  void an_object_of_several_members_keeps_every_one_of_them_in_the_order_they_were_written() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"a\":1,\"b\":2,\"c\":{\"d\":3,\"e\":4}}").orElseThrow();

    assertThat(read.members().keySet()).containsExactly("a", "b", "c");
    assertThat(read.object("c").orElseThrow().members().keySet()).containsExactly("d", "e");
  }

  @Test
  void a_document_that_stops_before_its_closing_bracket_is_read_as_nothing_and_does_not_hang() {
    assertThat(WorldlineJsonReader.objectIn("{\"a\":[1,2")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":1,")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":[1,2,")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\"")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"unterminated")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"half an escape \\\\")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":\"\\\\n and then nothing")).isEmpty();
  }

  @Test
  void a_keyword_that_is_only_half_written_is_read_as_nothing_wherever_it_stands() {
    assertThat(WorldlineJsonReader.objectIn("{\"a\":tru}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":[fals]}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"a\":nul}")).isEmpty();
  }

  @Test
  void every_keyword_json_publishes_is_read_inside_an_array_as_well_as_beside_a_name() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"a\":[true,false,null],\"b\":true}").orElseThrow();

    assertThat(read.flatten()).containsEntry("a", List.of("true", "false", ""));
    assertThat(read.member("b")).isEqualTo(new WorldlineJson.JsonFlag(true));
  }

  @Test
  void
      the_last_printable_ascii_character_is_written_plainly_and_the_first_control_one_is_escaped() {
    assertThat(
            WorldlineJsonWriter.toText(
                WorldlineJson.object(Map.of("a", WorldlineJson.text("~ !")))))
        .isEqualTo("{\"a\":\"~ !\"}");
    assertThat(
            WorldlineJsonWriter.toText(
                WorldlineJson.object(Map.of("a", WorldlineJson.text("\u001f")))))
        .isEqualTo("{\"a\":\"\\u001f\"}");
    assertThat(
            WorldlineJsonWriter.toText(
                WorldlineJson.object(Map.of("a", WorldlineJson.text("\u007f")))))
        .isEqualTo("{\"a\":\"\\u007f\"}");
  }

  @Test
  void a_name_is_escaped_by_the_same_rule_as_a_value_when_it_is_written() {
    assertThat(
            WorldlineJsonWriter.toText(
                WorldlineJson.object(Map.of("a\"b", WorldlineJson.text("c")))))
        .isEqualTo("{\"a\\\"b\":\"c\"}");
  }

  @Test
  void whitespace_between_every_token_is_skipped_and_the_object_still_reads() {
    assertThat(
            WorldlineJsonReader.objectIn(" { \"a\" : [ 1 , 2 ] , \"b\" : { } } ")
                .orElseThrow()
                .flatten())
        .containsEntry("a", List.of("1", "2"));
  }

  @Test
  void a_value_that_is_not_a_json_number_is_refused_where_worldline_types_a_number() {
    assertThatThrownBy(() -> WorldlineJson.number("abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("abc");
    assertThatThrownBy(() -> WorldlineJson.number("+5"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number(".5"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number("5."))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number("01"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number("1e"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number("1.2.3"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number("-"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> WorldlineJson.number("5000 "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void every_number_form_json_publishes_is_accepted_where_worldline_types_a_number() {
    assertThat(WorldlineJson.number("0").lexical()).isEqualTo("0");
    assertThat(WorldlineJson.number("-0").lexical()).isEqualTo("-0");
    assertThat(WorldlineJson.number("5000").lexical()).isEqualTo("5000");
    assertThat(WorldlineJson.number("-5000").lexical()).isEqualTo("-5000");
    assertThat(WorldlineJson.number("1e3").lexical()).isEqualTo("1e3");
    assertThat(WorldlineJson.number("1E+3").lexical()).isEqualTo("1E+3");
    assertThat(WorldlineJson.number("1E-3").lexical()).isEqualTo("1E-3");
    assertThat(WorldlineJson.number("0.5").lexical()).isEqualTo("0.5");
  }

  @Test
  void a_value_this_simulator_did_not_produce_is_written_as_a_number_only_when_it_is_one() {
    assertThat(WorldlineJsonWriter.toText(WorldlineJson.numberOrText("5000"))).isEqualTo("5000");
    assertThat(WorldlineJsonWriter.toText(WorldlineJson.numberOrText("abc"))).isEqualTo("\"abc\"");
    assertThat(WorldlineJsonWriter.toText(WorldlineJson.numberOrText("+5"))).isEqualTo("\"+5\"");
    assertThat(WorldlineJsonWriter.toText(WorldlineJson.numberOrText(""))).isEqualTo("\"\"");
  }

  @Test
  void a_number_json_would_not_accept_makes_the_whole_body_unreadable_rather_than_a_bad_field() {
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":+5}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":.5}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":5.}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":1.2.3}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":01}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":1e}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":1e+}")).isEmpty();
    assertThat(WorldlineJsonReader.objectIn("{\"amount\":-.5}")).isEmpty();
  }

  @Test
  void a_scalar_is_read_whichever_of_the_two_json_shapes_a_client_wrote_it_in() {
    WorldlineJson.JsonObject read =
        WorldlineJsonReader.objectIn("{\"a\":5000,\"b\":\"abc\",\"c\":{},\"d\":null}")
            .orElseThrow();

    assertThat(read.scalar("a")).contains("5000");
    assertThat(read.scalar("b"))
        .as(
            "an amount a client sent as a string reaches the bounds check and is refused on the"
                + " property, where reading only the number shape left it absent and captured the"
                + " whole authorized amount instead")
        .contains("abc");
    assertThat(read.scalar("c")).isEmpty();
    assertThat(read.scalar("d")).isEmpty();
    assertThat(read.scalar("e")).isEmpty();
  }
}
