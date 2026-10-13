package dev.marcinromanowski.pspsim.kernel.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

  private static final String POST = "POST";

  private static final String CHARGE = "/v1/payment_intents";

  @Test
  void a_retry_that_sends_the_same_parameters_in_another_order_is_the_same_request() {
    Map<String, List<String>> asSent = new LinkedHashMap<>();
    asSent.put("amount", List.of("1099"));
    asSent.put("currency", List.of("usd"));
    Map<String, List<String>> asRetried = new LinkedHashMap<>();
    asRetried.put("currency", List.of("usd"));
    asRetried.put("amount", List.of("1099"));

    assertThat(RequestFingerprint.of(POST, CHARGE, asSent))
        .isEqualTo(RequestFingerprint.of(POST, CHARGE, asRetried));
  }

  @Test
  void a_retry_that_changes_one_parameter_is_a_different_request() {
    assertThat(RequestFingerprint.of(POST, CHARGE, Map.of("amount", List.of("1099"))))
        .isNotEqualTo(RequestFingerprint.of(POST, CHARGE, Map.of("amount", List.of("2000"))));
  }

  @Test
  void a_retry_that_sends_a_parameter_twice_is_a_different_request() {
    assertThat(RequestFingerprint.of(POST, CHARGE, Map.of("expand", List.of("a"))))
        .isNotEqualTo(RequestFingerprint.of(POST, CHARGE, Map.of("expand", List.of("a", "b"))));
  }

  @Test
  void the_same_parameters_sent_to_another_endpoint_are_a_different_request() {
    Map<String, List<String>> form = Map.of("amount", List.of("1099"));

    assertThat(RequestFingerprint.of(POST, CHARGE, form))
        .isNotEqualTo(RequestFingerprint.of(POST, CHARGE + "/pi_1/capture", form));
    assertThat(RequestFingerprint.of(POST, CHARGE, form))
        .isNotEqualTo(RequestFingerprint.of("GET", CHARGE, form));
  }

  @Test
  void a_fingerprint_is_the_hexadecimal_digest_and_carries_nothing_a_client_sent() {
    String fingerprint =
        RequestFingerprint.of(POST, CHARGE, Map.of("receipt_email", List.of("payer@example.test")));

    assertThat(fingerprint).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(fingerprint).doesNotContain("payer");
  }
}
