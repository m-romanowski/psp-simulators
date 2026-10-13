package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderAccountFieldsTest {

  private static final ProviderId PROVIDER = ProviderId.of("aprovider");

  @Test
  void a_form_is_drawn_in_the_order_the_provider_declared_its_fields_in() {
    ProviderAccountFields declared =
        new ProviderAccountFields(
            PROVIDER,
            List.of(
                new ConfigurableField(
                    "userId",
                    "The user id",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "captureWindow",
                    "How long a hold lasts",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read")));

    assertThat(declared.keys()).containsExactly("userId", "captureWindow");
  }

  @Test
  void a_tester_reads_the_label_and_the_form_sends_the_key() {
    ProviderAccountFields declared =
        new ProviderAccountFields(
            PROVIDER,
            List.of(
                new ConfigurableField(
                    "apiSecret",
                    "Gateway private key",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read")));

    assertThat(declared.field("apiSecret"))
        .get()
        .satisfies(field -> assertThat(field.label()).isEqualTo("Gateway private key"));
    assertThat(declared.field("apiKey")).isEmpty();
  }

  @Test
  void only_the_fields_the_provider_calls_secret_are_answered_as_secret() {
    ProviderAccountFields declared =
        new ProviderAccountFields(
            PROVIDER,
            List.of(
                new ConfigurableField(
                    "terminalId",
                    "Terminal id",
                    "a sentence a test does not read",
                    true,
                    false,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "apiSecret",
                    "Gateway private key",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read")));

    assertThat(declared.secretKeys()).containsExactly("apiSecret");
  }

  @Test
  void a_provider_that_declares_no_field_at_all_is_refused_rather_than_drawing_an_empty_form() {
    assertThatThrownBy(() -> new ProviderAccountFields(PROVIDER, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nothing to ask a tester for");
  }

  @Test
  void one_key_declared_twice_is_refused_rather_than_drawing_two_inputs_writing_one_value() {
    List<ConfigurableField> twice =
        List.of(
            new ConfigurableField(
                "userId",
                "The user id",
                "a sentence a test does not read",
                true,
                true,
                List.of(),
                "a value a test does not read"),
            new ConfigurableField(
                "userId",
                "The user id again",
                "a sentence a test does not read",
                false,
                false,
                List.of(),
                "a value a test does not read"));

    assertThatThrownBy(() -> new ProviderAccountFields(PROVIDER, twice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId")
        .hasMessageContaining("declared twice");
  }

  @Test
  void a_field_with_no_label_is_refused_because_a_form_prints_the_label_and_not_the_key() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "userId", "  ", "a sentence a test does not read", true, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId")
        .hasMessageContaining("label");
  }

  @Test
  void a_field_with_no_key_is_refused_because_its_answer_would_reach_no_provider() {
    assertThatThrownBy(
            () ->
                new ConfigurableField(
                    "  ", "A label", "a sentence a test does not read", true, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names nothing the provider reads");
  }

  @Test
  void the_declared_field_list_cannot_be_changed_after_the_provider_published_it() {
    ProviderAccountFields declared =
        new ProviderAccountFields(
            PROVIDER,
            List.of(
                new ConfigurableField(
                    "userId",
                    "The user id",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read")));

    List<ConfigurableField> published = declared.fields();
    ConfigurableField smuggled =
        new ConfigurableField(
            "smuggled", "Not declared", "a sentence a test does not read", false, false);

    assertThatThrownBy(() -> published.add(smuggled))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void
      a_box_a_tester_types_into_and_that_declares_no_example_is_refused_rather_than_hinting_its_key() {
    assertThatThrownBy(
            () ->
                new ProviderAccountFields(
                    PROVIDER,
                    List.of(
                        new ConfigurableField(
                            "terminalId",
                            "terminal id",
                            "a sentence a test does not read",
                            true,
                            false))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("terminalId")
        .hasMessageContaining("declares no example for that box to hint");
  }

  @Test
  void a_field_offered_as_a_list_needs_no_example_because_no_box_is_drawn_for_it_to_hint() {
    ProviderAccountFields declared =
        new ProviderAccountFields(
            PROVIDER,
            List.of(
                new ConfigurableField(
                    "wireForm",
                    "which wire form answers carry",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(new AcceptedValue("LIVE_WIRE", "answer as the live wire does")))));

    assertThat(declared.field("wireForm").orElseThrow().example())
        .as("a list prints no hint into a box it never draws")
        .isNull();
  }

  @Test
  void a_field_is_shown_in_plain_only_when_this_provider_declares_it_and_does_not_call_it_secret() {
    ProviderAccountFields fields =
        new ProviderAccountFields(
            ProviderId.of("worldline"),
            List.of(
                new ConfigurableField(
                    "merchantId",
                    "The merchant id",
                    "a sentence a test does not read",
                    true,
                    false,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "apiSecret",
                    "The signing secret",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read")));

    assertThat(fields.isShownInPlain("merchantId")).isTrue();
    assertThat(fields.isShownInPlain("MERCHANTID")).isTrue();
    assertThat(fields.isShownInPlain("apiSecret")).isFalse();
    assertThat(fields.isShownInPlain("APISECRET")).isFalse();
    assertThat(fields.isShownInPlain("aFieldThisProviderNeverDeclared")).isFalse();
  }
}
