package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CheckJsonTest {

  private final CheckJson json = new CheckJson();

  @Test
  void a_member_of_an_object_is_read_by_its_name() {
    assertThat(json.at("{\"status\":\"requires_capture\"}", "/status"))
        .isEqualTo("requires_capture");
  }

  @Test
  void a_nested_member_is_read_by_the_path_that_reaches_it() {
    assertThat(
            json.at("{\"error\":{\"decline_code\":\"insufficient_funds\"}}", "/error/decline_code"))
        .isEqualTo("insufficient_funds");
  }

  @Test
  void a_member_of_an_array_is_read_by_its_position() {
    assertThat(
            json.at(
                "[{\"direction\":\"INBOUND_REQUEST\"},{\"outcome\":\"SUCCESS\"}]", "/1/outcome"))
        .isEqualTo("SUCCESS");
  }

  @Test
  void a_whole_number_and_the_same_number_quoted_read_as_one_value() {
    assertThat(json.at("{\"status\":1}", "/status")).isEqualTo("1");
    assertThat(json.at("{\"status\":\"1\"}", "/status")).isEqualTo("1");
  }

  @Test
  void a_negative_and_a_fractional_number_keep_their_lexical_form() {
    assertThat(json.at("{\"rc\":-17}", "/rc")).isEqualTo("-17");
    assertThat(json.at("{\"sum\":120.50}", "/sum")).isEqualTo("120.50");
  }

  @Test
  void a_boolean_reads_as_the_word_the_wire_carried() {
    assertThat(json.at("{\"livemode\":false}", "/livemode")).isEqualTo("false");
    assertThat(json.at("{\"ok\":true}", "/ok")).isEqualTo("true");
  }

  @Test
  void a_member_that_is_absent_and_a_member_that_is_null_both_read_as_nothing() {
    assertThat(json.at("{\"status\":1}", "/err/id")).isNull();
    assertThat(json.at("{\"outcome\":null}", "/outcome")).isNull();
  }

  @Test
  void a_path_that_indexes_an_object_or_names_an_array_reads_as_nothing() {
    assertThat(json.at("{\"data\":{\"processId\":\"1\"}}", "/data/0")).isNull();
    assertThat(json.at("[{\"a\":\"b\"}]", "/direction")).isNull();
    assertThat(json.at("[{\"a\":\"b\"}]", "/9/a")).isNull();
  }

  @Test
  void a_path_that_stops_on_an_object_reads_as_nothing_rather_than_as_its_rendering() {
    assertThat(json.at("{\"data\":{\"processId\":\"1\"}}", "/data")).isNull();
  }

  @Test
  void a_header_name_carrying_a_hyphen_is_a_member_name_like_any_other() {
    assertThat(
            json.at(
                "{\"sentHeaders\":{\"Stripe-Signature\":[\"t=1,v1=a\"]}}",
                "/sentHeaders/Stripe-Signature/0"))
        .isEqualTo("t=1,v1=a");
  }

  @Test
  void an_escape_the_wire_uses_is_read_back_as_the_character_it_names() {
    assertThat(json.at("{\"message\":\"\\u05e4\\u05e8\"}", "/message")).isEqualTo("\u05e4\u05e8");
    assertThat(json.at("{\"message\":\"a\\nb\\tc\\\"d\\\\e\\r\\b\\f\"}", "/message"))
        .isEqualTo("a\nb\tc\"d\\e\r\b\f");
  }

  @Test
  void an_empty_object_and_an_empty_array_are_read_without_members() {
    assertThat(json.at("{}", "/status")).isNull();
    assertThat(json.at("[]", "/0")).isNull();
  }

  @Test
  void whitespace_between_tokens_is_not_part_of_any_value() {
    assertThat(json.at("{ \"a\" : { \"b\" : \"c\" } }", "/a/b")).isEqualTo("c");
  }

  @Test
  void an_empty_pointer_reads_the_document_itself_and_an_object_is_not_a_value() {
    assertThat(json.at("{\"a\":\"b\"}", "")).isNull();
    assertThat(json.at("\"bare\"", "")).isEqualTo("bare");
  }

  @Test
  void a_body_that_is_not_the_json_the_route_publishes_is_refused_rather_than_guessed() {
    assertThatThrownBy(() -> json.at("not json at all", "/status"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(CheckJson.NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
    assertThatThrownBy(() -> json.at("{\"a\":\"b\"} trailing", "/a"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> json.at("{\"a\":}", "/a")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> json.at("{\"a\" \"b\"}", "/a"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> json.at("[1,", "/0")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> json.at("tru", "")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> json.at("{\"a\":\"\\u05\"}", "/a"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> json.at("", "")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void a_step_that_is_not_a_whole_number_reaches_no_member_of_an_array() {
    assertThat(json.at("[\"first\"]", "/!")).isNull();
    assertThat(json.at("[\"first\"]", "/:")).isNull();
    assertThat(json.at("[\"first\"]", "/a")).isNull();
    assertThat(json.at("[\"first\"]", "/1234567890")).isNull();
    assertThat(json.at("[\"first\"]", "/0")).isEqualTo("first");
  }

  @Test
  void a_position_past_the_first_ten_is_read_as_the_whole_number_its_digits_spell() {
    String twelve = "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\",\"l\"]";

    assertThat(json.at(twelve, "/10")).isEqualTo("k");
    assertThat(json.at(twelve, "/11")).isEqualTo("l");
    assertThat(json.at(twelve, "/12")).isNull();
  }

  @Test
  void whitespace_after_the_document_and_around_every_separator_is_not_part_of_any_value() {
    assertThat(json.at("{\"a\":\"b\"} ", "/a")).isEqualTo("b");
    assertThat(json.at("{\"a\":\"b\" , \"c\":\"d\" }", "/c")).isEqualTo("d");
    assertThat(json.at("[\"a\" , \"b\" ]", "/1")).isEqualTo("b");
    assertThat(json.at("{ }", "/a")).isNull();
    assertThat(json.at("[ ]", "/0")).isNull();
  }

  @Test
  void an_escape_the_body_ends_in_the_middle_of_is_refused_in_the_words_every_other_refusal_uses() {
    assertThatThrownBy(() -> json.at("{\"a\":\"\\u00", "/a"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(CheckJson.NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
    assertThatThrownBy(() -> json.at("{\"a\":\"\\u", "/a"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(CheckJson.NOT_THE_JSON_THIS_ROUTE_PUBLISHES);
  }

  @Test
  void an_array_that_carries_a_null_keeps_the_positions_of_the_members_after_it() {
    assertThat(json.at("[null,\"second\"]", "/1")).isEqualTo("second");
  }
}
