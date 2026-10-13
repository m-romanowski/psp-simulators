package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraJsonTest {

  @Test
  void an_amount_keeps_the_digits_the_client_wrote_rather_than_a_number_parsed_and_reprinted() {
    AutraJson.JsonObject read = AutraJsonReader.objectIn("{\"amount\":500.00}").orElseThrow();

    assertThat(read.number(AutraFields.AMOUNT)).contains("500.00");
    assertThat(AutraJsonWriter.toText(read)).isEqualTo("{\"amount\":500.00}");
  }

  @Test
  void a_nested_object_is_read_under_its_own_name_and_a_string_member_under_that() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn("{\"tokenData\":{\"slugToken\":\"slug-abc\"}}").orElseThrow();

    assertThat(read.object(AutraFields.TOKEN_DATA).orElseThrow().text(AutraFields.SLUG_TOKEN))
        .contains("slug-abc");
  }

  @Test
  void a_body_that_is_not_a_json_object_is_read_as_nothing_rather_than_raising() {
    assertThat(AutraJsonReader.objectIn("[1,2]")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{")).isEmpty();
    assertThat(AutraJsonReader.objectIn("")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":1} trailing")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":tru}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\q\"}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\uZZZZ\"}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":1;\"b\":2}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("[1;2]")).isEmpty();
  }

  @Test
  void every_json_value_autra_can_send_survives_being_read_and_written_again() {
    String source =
        "{\"text\":\"a\\\"b\\\\c\\n\",\"flag\":true,\"off\":false,\"nothing\":null,"
            + "\"list\":[1,\"two\",{}],\"empty\":{},\"blank\":[]}";

    AutraJson.JsonObject read = AutraJsonReader.objectIn(source).orElseThrow();

    assertThat(AutraJsonWriter.toText(read)).isEqualTo(source);
  }

  @Test
  void a_character_outside_printable_ascii_is_written_as_an_escape_and_read_back_whole() {
    AutraJson.JsonObject written =
        AutraJson.object(Map.of("msg", AutraJson.text("n\u00e3o \u0001")));

    String text = AutraJsonWriter.toText(written);

    assertThat(text).isEqualTo("{\"msg\":\"n\\u00e3o \\u0001\"}");
    assertThat(AutraJsonReader.objectIn(text).orElseThrow().text("msg"))
        .contains("n\u00e3o \u0001");
  }

  @Test
  void flattening_names_a_nested_member_by_its_path_and_an_array_item_by_the_arrays_own_path() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn(
                "{\"errors\":[{\"code\":\"X\",\"msg\":\"y\"}],\"tokenData\":{\"slugToken\":\"s\"},"
                    + "\"n\":1,\"f\":false,\"z\":null}")
            .orElseThrow();

    Map<String, List<String>> flattened = read.flatten();

    assertThat(flattened)
        .containsEntry("errors.code", List.of("X"))
        .containsEntry("errors.msg", List.of("y"))
        .containsEntry("tokenData.slugToken", List.of("s"))
        .containsEntry("n", List.of("1"))
        .containsEntry("f", List.of("false"))
        .containsEntry("z", List.of(""));
  }

  @Test
  void an_empty_array_is_flattened_to_a_named_key_with_no_value_under_it() {
    assertThat(AutraJsonReader.objectIn("{\"errors\":[]}").orElseThrow().flatten())
        .containsEntry(AutraFields.ERRORS, List.of());
  }

  @Test
  void a_member_read_under_the_wrong_json_type_is_absent_rather_than_coerced() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn("{\"amount\":\"500.00\",\"documentId\":12345}").orElseThrow();

    assertThat(read.number(AutraFields.AMOUNT)).isEmpty();
    assertThat(read.text(AutraFields.DOCUMENT_ID)).isEmpty();
    assertThat(read.object(AutraFields.AMOUNT)).isEmpty();
    assertThat(read.has(AutraFields.AMOUNT)).isTrue();
    assertThat(read.has(AutraFields.CARD)).isFalse();
  }

  @Test
  void an_amount_sent_as_a_string_is_still_the_lexical_form_a_caller_wrote() {
    AutraJson.JsonObject read = AutraJsonReader.objectIn("{\"amount\":\"500.00\"}").orElseThrow();

    assertThat(AutraAmounts.lexicalIn(read, AutraFields.AMOUNT)).contains("500.00");
  }

  @Test
  void a_negative_number_keeps_its_sign_and_every_other_number_form_autra_could_send_reads_back() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn(
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
    assertThat(AutraJsonReader.objectIn("{\"a\":-}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":x}")).isEmpty();
  }

  @Test
  void a_four_digit_escape_is_read_as_the_character_it_names_and_a_truncated_one_as_nothing() {
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\u0041\\u00e3\"}").orElseThrow().text("a"))
        .contains("A\u00e3");
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\u004")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\u00g1\"}")).isEmpty();
  }

  @Test
  void every_short_escape_json_publishes_is_read_as_the_character_it_names() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn("{\"a\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\"}").orElseThrow();

    assertThat(read.text("a")).contains("\"\\/\b\f\n\r\t");
  }

  @Test
  void an_array_that_holds_nothing_but_blanks_is_read_as_an_array_of_no_elements() {
    assertThat(AutraJsonReader.objectIn("{\"a\":[ ]}").orElseThrow().flatten())
        .containsEntry("a", List.of());
    assertThat(AutraJsonReader.objectIn("{\"a\":[\n\t ]}").orElseThrow().flatten())
        .containsEntry("a", List.of());
  }

  @Test
  void an_object_that_holds_nothing_but_blanks_is_read_as_an_object_of_no_members() {
    assertThat(AutraJsonReader.objectIn("{ }").orElseThrow().members()).isEmpty();
  }

  @Test
  void an_array_of_several_elements_keeps_every_one_of_them_in_the_order_they_were_written() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn("{\"a\":[1,2,3],\"b\":[[4,5],{\"c\":6}]}").orElseThrow();

    assertThat(read.flatten())
        .containsEntry("a", List.of("1", "2", "3"))
        .containsEntry("b", List.of("4", "5"))
        .containsEntry("b.c", List.of("6"));
  }

  @Test
  void an_object_of_several_members_keeps_every_one_of_them_in_the_order_they_were_written() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn("{\"a\":1,\"b\":2,\"c\":{\"d\":3,\"e\":4}}").orElseThrow();

    assertThat(read.members().keySet()).containsExactly("a", "b", "c");
    assertThat(read.object("c").orElseThrow().members().keySet()).containsExactly("d", "e");
  }

  @Test
  void a_document_that_stops_before_its_closing_bracket_is_read_as_nothing_and_does_not_hang() {
    assertThat(AutraJsonReader.objectIn("{\"a\":[1,2")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":1,")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":[1,2,")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\"")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"unterminated")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"half an escape \\\\")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\\\n and then nothing")).isEmpty();
  }

  @Test
  void a_keyword_that_is_only_half_written_is_read_as_nothing_wherever_it_stands() {
    assertThat(AutraJsonReader.objectIn("{\"a\":tru}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":[fals]}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":nul}")).isEmpty();
  }

  @Test
  void every_keyword_json_publishes_is_read_inside_an_array_as_well_as_beside_a_name() {
    AutraJson.JsonObject read =
        AutraJsonReader.objectIn("{\"a\":[true,false,null],\"b\":true}").orElseThrow();

    assertThat(read.flatten()).containsEntry("a", List.of("true", "false", ""));
    assertThat(read.member("b")).isEqualTo(new AutraJson.JsonFlag(true));
  }

  @Test
  void
      the_last_printable_ascii_character_is_written_plainly_and_the_first_control_one_is_escaped() {
    assertThat(AutraJsonWriter.toText(AutraJson.object(Map.of("a", AutraJson.text("~ !")))))
        .isEqualTo("{\"a\":\"~ !\"}");
    assertThat(AutraJsonWriter.toText(AutraJson.object(Map.of("a", AutraJson.text("\u001f")))))
        .isEqualTo("{\"a\":\"\\u001f\"}");
    assertThat(AutraJsonWriter.toText(AutraJson.object(Map.of("a", AutraJson.text("\u007f")))))
        .isEqualTo("{\"a\":\"\\u007f\"}");
  }

  @Test
  void a_name_is_escaped_by_the_same_rule_as_a_value_when_it_is_written() {
    assertThat(AutraJsonWriter.toText(AutraJson.object(Map.of("a\"b", AutraJson.text("c")))))
        .isEqualTo("{\"a\\\"b\":\"c\"}");
  }

  @Test
  void whitespace_between_every_token_is_skipped_and_the_object_still_reads() {
    assertThat(
            AutraJsonReader.objectIn(" { \"a\" : [ 1 , 2 ] , \"b\" : { } } ")
                .orElseThrow()
                .flatten())
        .containsEntry("a", List.of("1", "2"));
  }

  @Test
  void a_value_that_is_not_a_json_number_is_refused_where_autra_types_a_number() {
    assertThatThrownBy(() -> AutraJson.number("abc"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("abc");
    assertThatThrownBy(() -> AutraJson.number("+5")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AutraJson.number(".5")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AutraJson.number("5.")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AutraJson.number("01")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AutraJson.number("1e")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AutraJson.number("1.2.3"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AutraJson.number("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AutraJson.number("-")).isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> AutraJson.number("1 "))
        .as("a lexical with anything after the number would be written beside it unquoted")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void every_number_form_json_publishes_is_accepted_where_autra_types_a_number() {
    assertThat(AutraJson.number("0").lexical()).isEqualTo("0");
    assertThat(AutraJson.number("-0").lexical()).isEqualTo("-0");
    assertThat(AutraJson.number("500.00").lexical()).isEqualTo("500.00");
    assertThat(AutraJson.number("-500.00").lexical()).isEqualTo("-500.00");
    assertThat(AutraJson.number("1e3").lexical()).isEqualTo("1e3");
    assertThat(AutraJson.number("1E+3").lexical()).isEqualTo("1E+3");
    assertThat(AutraJson.number("1E-3").lexical()).isEqualTo("1E-3");
    assertThat(AutraJson.number("0.5").lexical()).isEqualTo("0.5");
  }

  @Test
  void a_value_this_simulator_did_not_produce_is_written_as_a_number_only_when_it_is_one() {
    assertThat(AutraJsonWriter.toText(AutraJson.numberOrText("99.90"))).isEqualTo("99.90");
    assertThat(AutraJsonWriter.toText(AutraJson.numberOrText("abc"))).isEqualTo("\"abc\"");
    assertThat(AutraJsonWriter.toText(AutraJson.numberOrText("+5"))).isEqualTo("\"+5\"");
    assertThat(AutraJsonWriter.toText(AutraJson.numberOrText(""))).isEqualTo("\"\"");
  }

  @Test
  void a_unicode_escape_the_body_ends_in_the_middle_of_is_unreadable_rather_than_half_read() {
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\u004"))
        .as(
            "the four digits of an escape are read as one, so a body that stops inside them is"
                + " unreadable and never a string with a character this reader invented")
        .isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\u0041"))
        .as("a body that stops at the end of the four digits is unreadable as well")
        .isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"a\":\"\\u0041\"}"))
        .as("the same four digits inside a body that closes are the character they spell")
        .isNotEmpty();
  }

  @Test
  void a_number_json_would_not_accept_makes_the_whole_body_unreadable_rather_than_a_bad_field() {
    assertThat(AutraJsonReader.objectIn("{\"amount\":+5}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"amount\":.5}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"amount\":5.}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"amount\":1.2.3}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"amount\":01}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"amount\":1e}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"amount\":1e+}")).isEmpty();
    assertThat(AutraJsonReader.objectIn("{\"amount\":-.5}")).isEmpty();
  }

  @Test
  void an_amount_autra_would_echo_is_one_it_can_write_back_into_a_number_position() {
    assertThat(AutraAmounts.isAboveZero("99.90")).isTrue();
    assertThat(AutraAmounts.isAboveZero("0")).isFalse();
    assertThat(AutraAmounts.isAboveZero("-1")).isFalse();
    assertThat(AutraAmounts.isAboveZero("abc")).isFalse();
    assertThat(AutraAmounts.isAboveZero("+5.00"))
        .as(
            "BigDecimal reads a leading plus and JSON does not, so an accepted +5.00 would be"
                + " echoed into a number position as +5.00 and the answer autra gave would not"
                + " parse in the client at all")
        .isFalse();
    assertThat(AutraAmounts.isAboveZero(".5")).isFalse();
    assertThat(ChargeStoredCard.isZeroOrAbove("0")).isTrue();
    assertThat(ChargeStoredCard.isZeroOrAbove("+5.00")).isFalse();
    assertThat(ChargeStoredCard.isZeroOrAbove("abc")).isFalse();
    assertThat(ChargeStoredCard.isZeroOrAbove("-1")).isFalse();
  }
}
