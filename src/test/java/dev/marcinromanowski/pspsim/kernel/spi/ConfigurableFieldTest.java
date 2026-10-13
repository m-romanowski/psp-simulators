package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurableFieldTest {

  private static final String A_SENTENCE = "What the provider does with the value.";

  @Test
  void a_field_carries_a_name_to_scan_and_a_sentence_to_read_as_two_separate_things() {
    ConfigurableField field =
        new ConfigurableField("clientId", "client id", A_SENTENCE, true, false);

    assertThat(field.label()).isEqualTo("client id");
    assertThat(field.whatItIsFor()).isEqualTo(A_SENTENCE);
  }

  @Test
  void a_label_longer_than_the_bound_a_tester_can_scan_is_refused() {
    String tooLong = "a".repeat(ConfigurableField.LONGEST_LABEL_A_TESTER_SCANS + 1);

    assertThatThrownBy(() -> new ConfigurableField("clientId", tooLong, A_SENTENCE, true, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId")
        .hasMessageContaining("whatItIsFor");
  }

  @Test
  void a_label_exactly_at_the_bound_is_taken_so_the_bound_is_a_ceiling_and_not_a_fence() {
    String asLongAsItGets = "a".repeat(ConfigurableField.LONGEST_LABEL_A_TESTER_SCANS);

    ConfigurableField field =
        new ConfigurableField("clientId", asLongAsItGets, A_SENTENCE, true, false);

    assertThat(field.label()).hasSize(ConfigurableField.LONGEST_LABEL_A_TESTER_SCANS);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "client id, the first half",
        "a document. digits only",
        "a name; and a clause",
        "a name: and a clause"
      })
  void a_label_written_as_a_sentence_is_refused_so_the_sentence_moves_to_the_explanation(
      String written) {
    assertThatThrownBy(() -> new ConfigurableField("clientId", written, A_SENTENCE, false, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId")
        .hasMessageContaining("whatItIsFor");
  }

  @Test
  void a_field_that_says_nothing_about_what_the_provider_does_with_the_value_is_refused() {
    assertThatThrownBy(() -> new ConfigurableField("clientId", "client id", "   ", false, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId")
        .hasMessageContaining("leaves that tester guessing");
  }

  @Test
  void a_field_whose_explanation_only_repeats_its_label_is_refused() {
    assertThatThrownBy(
            () -> new ConfigurableField("clientId", "client id", " client id ", false, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("clientId")
        .hasMessageContaining("learns nothing");
  }

  @Test
  void a_field_with_no_key_names_nothing_the_provider_reads() {
    assertThatThrownBy(() -> new ConfigurableField("  ", "client id", A_SENTENCE, false, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names nothing");
  }

  @Test
  void a_field_that_declares_no_value_accepts_whatever_a_tester_types() {
    ConfigurableField field =
        new ConfigurableField("clientId", "client id", A_SENTENCE, true, false);

    assertThat(field.acceptedValues()).isEmpty();
  }

  @Test
  void a_field_whose_values_are_a_closed_set_carries_a_sentence_for_every_one_of_them() {
    ConfigurableField field =
        new ConfigurableField(
            "wireForm",
            "which wire form answers carry",
            A_SENTENCE,
            false,
            false,
            List.of(
                new AcceptedValue("LIVE_WIRE", "answer text/html, as the wire does"),
                new AcceptedValue(
                    "PUBLISHED_EXAMPLE", "answer application/json, as the doc does")));

    assertThat(field.acceptedValues())
        .extracting(AcceptedValue::value)
        .containsExactly("LIVE_WIRE", "PUBLISHED_EXAMPLE");
    assertThat(field.acceptedValues())
        .extracting(AcceptedValue::whatItMeans)
        .containsExactly(
            "answer text/html, as the wire does", "answer application/json, as the doc does");
  }

  @Test
  void a_secret_that_declares_the_values_it_accepts_is_refused() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "apiSecret",
                    "api secret",
                    A_SENTENCE,
                    true,
                    true,
                    List.of(new AcceptedValue("whsec_one", "sign with the first issued secret"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("apiSecret")
        .hasMessageContaining("print a credential of this account on a screen");
  }

  @Test
  void a_value_declared_twice_is_refused_because_a_tester_could_not_tell_the_two_entries_apart() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "wireForm",
                    "which wire form answers carry",
                    A_SENTENCE,
                    false,
                    false,
                    List.of(
                        new AcceptedValue("LIVE_WIRE", "answer text/html, as the wire does"),
                        new AcceptedValue("LIVE_WIRE", "answer application/json instead"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wireForm")
        .hasMessageContaining("accepts the value LIVE_WIRE twice");
  }

  @Test
  void two_values_a_form_would_print_the_same_sentence_for_are_refused() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "wireForm",
                    "which wire form answers carry",
                    A_SENTENCE,
                    false,
                    false,
                    List.of(
                        new AcceptedValue("LIVE_WIRE", "answer the way the provider publishes"),
                        new AcceptedValue(
                            "PUBLISHED_EXAMPLE", "answer the way the provider publishes"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wireForm")
        .hasMessageContaining("the pick between them would be a coin flip");
  }

  @Test
  void a_field_that_takes_a_shape_rather_than_a_list_shows_that_shape_as_its_example() {
    ConfigurableField field =
        new ConfigurableField(
            "captureWindow", "how long a hold lasts", A_SENTENCE, false, false, List.of(), "P7D");

    assertThat(field.example()).isEqualTo("P7D");
  }

  @Test
  void a_field_that_declares_no_example_has_none_rather_than_an_empty_one() {
    ConfigurableField field =
        new ConfigurableField("clientId", "client id", A_SENTENCE, true, false);

    assertThat(field.example()).isNull();
  }

  @Test
  void a_blank_example_is_refused_because_a_box_hinting_nothing_reads_as_a_value() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "captureWindow",
                    "how long a hold lasts",
                    A_SENTENCE,
                    false,
                    false,
                    List.of(),
                    "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("captureWindow")
        .hasMessageContaining("carries a blank example");
  }

  @Test
  void an_example_beside_a_list_of_values_is_refused_because_no_box_would_ever_print_it() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "wireForm",
                    "which wire form answers carry",
                    A_SENTENCE,
                    false,
                    false,
                    List.of(new AcceptedValue("LIVE_WIRE", "answer text/html, as the wire does")),
                    "LIVE_WIRE"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wireForm")
        .hasMessageContaining("would reach no tester");
  }

  @Test
  void
      a_field_reads_back_the_sentence_it_wrote_for_a_value_it_accepts_and_none_for_one_it_does_not() {
    ConfigurableField field =
        new ConfigurableField(
            "wireForm",
            "which wire form answers carry",
            A_SENTENCE,
            false,
            false,
            List.of(
                new AcceptedValue("LIVE_WIRE", "answer text/html, as the wire does"),
                new AcceptedValue(
                    "PUBLISHED_EXAMPLE", "answer application/json, as the doc does")));

    assertThat(field.accepted("PUBLISHED_EXAMPLE"))
        .map(AcceptedValue::whatItMeans)
        .contains("answer application/json, as the doc does");
    assertThat(field.accepted("RETIRED_FORM")).isEmpty();
  }

  @Test
  void a_field_with_no_values_at_all_accepts_the_lookup_of_one_and_answers_nothing() {
    ConfigurableField field =
        new ConfigurableField("captureWindow", "how long a hold lasts", A_SENTENCE, false, false);

    assertThat(field.accepted("P7D")).isEmpty();
  }

  @Test
  void a_field_that_holds_a_duration_says_so_and_a_field_that_takes_free_text_does_not() {
    ConfigurableField span =
        ConfigurableField.aDuration(
            "captureWindow", "how long a hold lasts", A_SENTENCE, false, "P7D");

    assertThat(span.holdsADuration()).isTrue();
    assertThat(span.shape()).isEqualTo(FieldShape.A_DURATION);
    assertThat(span.example()).isEqualTo("P7D");
    assertThat(span.secret())
        .as("a span of time is never a credential the provider issued this account")
        .isFalse();
    assertThat(new ConfigurableField("clientId", "client id", A_SENTENCE, true, false).shape())
        .isEqualTo(FieldShape.FREE_TEXT);
    assertThat(
            new ConfigurableField("clientId", "client id", A_SENTENCE, true, false)
                .holdsADuration())
        .isFalse();
  }

  @Test
  void a_duration_that_also_declares_a_closed_set_of_values_is_refused() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "captureWindow",
                    "how long a hold lasts",
                    A_SENTENCE,
                    false,
                    false,
                    List.of(new AcceptedValue("P7D", "hold for a week")),
                    null,
                    FieldShape.A_DURATION,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("captureWindow")
        .hasMessageContaining("a duration is not a closed set");
  }

  @Test
  void a_duration_that_is_also_a_secret_is_refused() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "captureWindow",
                    "how long a hold lasts",
                    A_SENTENCE,
                    false,
                    true,
                    List.of(),
                    "P7D",
                    FieldShape.A_DURATION,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("captureWindow")
        .hasMessageContaining("never a span of time");
  }

  @Test
  void a_field_that_holds_a_block_of_many_lines_says_so_and_hints_the_line_it_begins_with() {
    ConfigurableField pasted =
        ConfigurableField.aBlockOfManyLines(
            "apiKey",
            "merchant public key (PEM)",
            A_SENTENCE,
            true,
            false,
            "-----BEGIN PUBLIC KEY-----",
            List.of("-----END PUBLIC KEY-----"));

    assertThat(pasted.holdsABlockOfManyLines()).isTrue();
    assertThat(pasted.shape()).isEqualTo(FieldShape.A_BLOCK_OF_MANY_LINES);
    assertThat(pasted.example())
        .as(
            "a whole block is too long for any box to hint, so what the box hints is the line"
                + " the block begins with and the shape of the rest is said beside it")
        .isEqualTo("-----BEGIN PUBLIC KEY-----");
    assertThat(pasted.holdsADuration()).isFalse();
    assertThat(
            new ConfigurableField("clientId", "client id", A_SENTENCE, true, false)
                .holdsABlockOfManyLines())
        .isFalse();
  }

  @Test
  void a_block_of_many_lines_that_also_declares_a_closed_set_of_values_is_refused() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "apiKey",
                    "merchant public key (PEM)",
                    A_SENTENCE,
                    true,
                    false,
                    List.of(new AcceptedValue("-----BEGIN PUBLIC KEY-----", "a public key")),
                    null,
                    FieldShape.A_BLOCK_OF_MANY_LINES,
                    List.of("-----END PUBLIC KEY-----")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("apiKey")
        .hasMessageContaining("a block of many lines is not a closed set");
  }

  @Test
  void a_block_of_many_lines_that_hints_more_than_one_line_of_itself_is_refused() {
    assertThatThrownBy(
            () ->
                ConfigurableField.aBlockOfManyLines(
                    "apiKey",
                    "merchant public key (PEM)",
                    A_SENTENCE,
                    true,
                    false,
                    "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkq\n-----END PUBLIC KEY-----",
                    List.of("-----END PUBLIC KEY-----")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("apiKey")
        .hasMessageContaining("hints more than one line");
  }

  @Test
  void a_field_with_no_label_leaves_a_form_printing_nothing_a_tester_could_read() {
    assertThatThrownBy(() -> new ConfigurableField("clientId", "  ", A_SENTENCE, false, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must carry a label");
  }
}
