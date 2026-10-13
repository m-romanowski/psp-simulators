package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrowResponseBytesTest {

  @Test
  void renders_a_success_in_the_key_order_grow_publishes() {
    String body =
        render(
            GrowEnvelope.success(
                GrowJson.object(
                    "processId", GrowJson.text("734754"),
                    "processToken", GrowJson.text("2153287cd9af46457ae842337250b78d"),
                    "url", GrowJson.text("https://simulator.internal/far?l=89a71a")),
                GrowScalarType.INTEGER));

    assertThat(body)
        .isEqualTo(
            "{\"status\":1,\"err\":\"\",\"data\":{"
                + "\"processId\":\"734754\","
                + "\"processToken\":\"2153287cd9af46457ae842337250b78d\","
                + "\"url\":\"https://simulator.internal/far?l=89a71a\"}}");
  }

  @Test
  void renders_a_failure_with_an_empty_data_string() {
    String body =
        render(GrowEnvelope.failure(GrowErrors.invalidCredentials(), GrowScalarType.INTEGER));

    assertThat(body)
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":701,"
                + "\"message\":\"פרמטר קוד זיהוי אינו תקין userId\"},\"data\":\"\"}");
  }

  @Test
  void can_render_the_same_failure_with_a_string_status_and_a_string_error_id() {
    String body =
        render(
            GrowEnvelope.failure(
                GrowErrors.missingRequiredField("pageCode"), GrowScalarType.STRING));

    assertThat(body)
        .isEqualTo(
            "{\"status\":\"0\",\"err\":{\"id\":\"54\","
                + "\"message\":\"חסרים נתונים:pageCode\"},\"data\":\"\"}");
  }

  @Test
  void can_render_the_same_success_with_a_string_status() {
    String body = render(GrowEnvelope.success(GrowJson.object(), GrowScalarType.STRING));

    assertThat(body).isEqualTo("{\"status\":\"1\",\"err\":\"\",\"data\":{}}");
  }

  @Test
  void writes_hebrew_escaped_the_way_the_live_wire_writes_it() {
    byte[] bytes =
        GrowJsonWriter.toBytes(
            GrowJson.object("message", GrowJson.text(GrowNativeStatus.NOT_PAID)),
            GrowWireForm.LIVE_WIRE);

    assertThat(new String(bytes, UTF_8))
        .isEqualTo("{\"message\":\"\\u05dc\\u05d0 \\u05e9\\u05d5\\u05dc\\u05dd\"}");
  }

  @Test
  void writes_hebrew_as_utf8_for_a_client_built_against_the_published_examples() {
    byte[] bytes =
        GrowJsonWriter.toBytes(
            GrowJson.object("message", GrowJson.text(GrowNativeStatus.NOT_PAID)),
            GrowWireForm.PUBLISHED_EXAMPLE);

    assertThat(new String(bytes, UTF_8)).isEqualTo("{\"message\":\"לא שולם\"}");
    assertThat(new String(bytes, UTF_8)).doesNotContain("\\u");
  }

  @Test
  void leaves_the_last_printable_ascii_character_alone_and_escapes_the_one_above_it() {
    byte[] bytes =
        GrowJsonWriter.toBytes(
            GrowJson.object("edge", GrowJson.text("a~b\u007fc")), GrowWireForm.LIVE_WIRE);

    assertThat(new String(bytes, UTF_8)).isEqualTo("{\"edge\":\"a~b\\u007fc\"}");
  }

  @Test
  void escapes_only_what_json_requires() {
    String body =
        render(
            GrowJson.object(
                "quote", GrowJson.text("a \" b"),
                "backslash", GrowJson.text("a \\ b"),
                "controls", GrowJson.text("\b\f\n\r\t"),
                "other-control", GrowJson.text("\u0001"),
                "slash", GrowJson.text("https://a/b")));

    assertThat(body)
        .isEqualTo(
            "{\"quote\":\"a \\\" b\","
                + "\"backslash\":\"a \\\\ b\","
                + "\"controls\":\"\\b\\f\\n\\r\\t\","
                + "\"other-control\":\"\\u0001\","
                + "\"slash\":\"https://a/b\"}");
  }

  @Test
  void renders_a_number_without_quotes_or_a_decimal_point() {
    assertThat(render(GrowJson.object("n", GrowJson.number(0), "big", GrowJson.number(734754))))
        .isEqualTo("{\"n\":0,\"big\":734754}");
  }

  @Test
  void flattens_a_nested_body_onto_dotted_keys() {
    GrowJson body = GrowEnvelope.failure(GrowErrors.amountNotValid(), GrowScalarType.INTEGER);

    Map<String, List<String>> flattened = body.flatten();

    assertThat(flattened)
        .containsExactly(
            Map.entry("status", List.of("0")),
            Map.entry("err.id", List.of("707")),
            Map.entry("err.message", List.of("הסכום אינו תקין")),
            Map.entry("data", List.of("")));
  }

  @Test
  void renders_an_empty_array_the_way_a_lookup_reports_custom_fields_nobody_sent() {
    assertThat(render(GrowJson.object("customField", GrowJson.array(List.of()))))
        .isEqualTo("{\"customField\":[]}");
  }

  @Test
  void renders_an_array_of_rows_the_way_a_process_lookup_reports_its_transactions() {
    GrowJson body =
        GrowJson.object(
            "transactions",
            GrowJson.array(
                List.of(
                    GrowJson.object("transactionId", GrowJson.text("1")),
                    GrowJson.object("transactionId", GrowJson.text("2")))));

    assertThat(render(body))
        .isEqualTo("{\"transactions\":[{\"transactionId\":\"1\"},{\"transactionId\":\"2\"}]}");
  }

  @Test
  void flattens_every_row_of_an_array_onto_one_key_so_a_redaction_reaches_all_of_them() {
    GrowJson body =
        GrowJson.object(
            "customField",
            GrowJson.array(List.of()),
            "transactions",
            GrowJson.array(
                List.of(
                    GrowJson.object("transactionToken", GrowJson.text("first")),
                    GrowJson.object("transactionToken", GrowJson.text("second")))));

    assertThat(body.flatten())
        .containsExactly(
            Map.entry("customField", List.of()),
            Map.entry("transactions", List.of()),
            Map.entry("transactions.transactionToken", List.of("first", "second")));
  }

  @Test
  void refuses_an_object_literal_with_a_key_that_has_no_value() {
    assertThatThrownBy(() -> GrowJson.object("status"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void names_the_position_of_a_literal_whose_key_or_value_has_the_wrong_type() {
    GrowJson text = GrowJson.text("x");

    assertThatThrownBy(() -> GrowJson.object(1, text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("String key at position 0");
    assertThatThrownBy(() -> GrowJson.object("status", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GrowJson value at position 1");
  }

  @Test
  void keeps_an_object_immutable_once_built() {
    Map<String, GrowJson> members = new LinkedHashMap<>();
    members.put("a", GrowJson.number(1));
    GrowJson.JsonObject object = new GrowJson.JsonObject(members);

    members.put("b", GrowJson.number(2));

    assertThat(render(object)).isEqualTo("{\"a\":1}");
  }

  private static String render(GrowJson value) {
    return new String(GrowJsonWriter.toBytes(value, GrowWireForm.PUBLISHED_EXAMPLE), UTF_8);
  }
}
