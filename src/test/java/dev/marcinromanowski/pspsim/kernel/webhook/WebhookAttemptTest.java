package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookAttemptTest {

  private static final Instant AT = Instant.parse("2026-09-25T12:00:00Z");

  private static final String TARGET = "https://merchant.example/hook";

  private static final String FORM = "application/x-www-form-urlencoded";

  @Test
  void an_attempt_that_recorded_the_wire_says_so_and_names_the_content_type_it_posted_under() {
    WebhookAttempt attempt = recording(Map.of("Content-Type", List.of(FORM)));

    assertThat(attempt.theWireWasRecorded()).isTrue();
    assertThat(attempt.contentTypeSent()).isEqualTo(FORM);
  }

  @Test
  void the_content_type_is_read_whatever_case_the_header_name_was_recorded_under() {
    assertThat(recording(Map.of("content-type", List.of(FORM))).contentTypeSent()).isEqualTo(FORM);
  }

  @Test
  void the_content_type_is_read_from_beside_the_other_headers_the_attempt_put_on_the_wire() {
    WebhookAttempt attempt =
        recording(
            Map.of(
                "Stripe-Signature",
                List.of("t=1,v1=abc"),
                "Content-Type",
                List.of("application/json")));

    assertThat(attempt.contentTypeSent()).isEqualTo("application/json");
  }

  @Test
  void an_attempt_that_recorded_no_wire_says_so_rather_than_naming_an_empty_set_of_headers() {
    WebhookAttempt attempt = recording(null);

    assertThat(attempt.theWireWasRecorded()).isFalse();
    assertThat(attempt.sentHeaders()).isNull();
    assertThat(attempt.contentTypeSent()).isNull();
  }

  @Test
  void a_recorded_wire_naming_no_content_type_is_refused_rather_than_stored() {
    assertThatThrownBy(() -> recording(Map.of("Stripe-Signature", List.of("t=1,v1=abc"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Stripe-Signature");
  }

  @Test
  void a_recorded_wire_carrying_no_header_at_all_is_refused_rather_than_stored() {
    assertThatThrownBy(() -> recording(Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("content type");
  }

  @Test
  void a_content_type_recorded_with_no_value_is_no_content_type_and_is_refused() {
    assertThatThrownBy(() -> recording(Map.of("Content-Type", List.of())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void the_recorded_wire_does_not_change_when_the_map_it_was_built_from_changes_afterwards() {
    Map<String, List<String>> onTheWire = new LinkedHashMap<>();
    onTheWire.put("Content-Type", List.of(FORM));
    WebhookAttempt attempt = recording(onTheWire);
    onTheWire.put("smuggled", List.of("later"));

    assertThat(attempt.sentHeaders()).containsOnlyKeys("Content-Type");
  }

  private static WebhookAttempt recording(Map<String, List<String>> onTheWire) {
    return new WebhookAttempt(
        "whjob_1", 1, AT, TARGET, onTheWire, Acknowledgement.ACKNOWLEDGED, 204, null, "");
  }
}
