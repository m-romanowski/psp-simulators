package dev.marcinromanowski.pspsim.kernel.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

  private static final String POST = "POST";

  private static final String CHARGE = "/v1/payment_intents";

  private static final FingerprintKey A_RUN =
      new FingerprintKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

  private static final FingerprintKey ANOTHER_RUN =
      new FingerprintKey("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");

  private static final Map<String, List<String>> A_CHARGE =
      Map.of("amount", List.of("1099"), "currency", List.of("usd"));

  @Test
  void a_fingerprint_is_the_hmac_sha256_of_the_request_under_the_key_of_its_test_run() {
    assertThat(RequestFingerprint.of(A_RUN, new FingerprintedRequest(POST, CHARGE, A_CHARGE)))
        .isEqualTo("7dc478d97f4af5a4ee0a6d95eb10f62af316100c1537c8864df728811e4a90fe");
  }

  @Test
  void the_same_request_fingerprints_differently_under_the_key_of_another_test_run() {
    assertThat(RequestFingerprint.of(ANOTHER_RUN, new FingerprintedRequest(POST, CHARGE, A_CHARGE)))
        .isEqualTo("4e75f229cb0349b2fee7f1b92301c56c70e9d45a79bdb2bbfc06c8705de34f78");
  }

  @Test
  void a_fingerprint_is_not_the_unkeyed_digest_a_reader_could_recompute_from_a_guess() {
    assertThat(RequestFingerprint.of(A_RUN, new FingerprintedRequest(POST, CHARGE, A_CHARGE)))
        .isNotEqualTo("8da55c04add0b4c0dbe9b719ca8ac84f7019fd799404a2f181e62d14307273af");
  }

  @Test
  void two_requests_that_differ_only_in_a_card_field_fingerprint_differently() {
    assertThat(
            RequestFingerprint.of(
                A_RUN,
                new FingerprintedRequest(
                    POST,
                    CHARGE,
                    Map.of("number", List.of("4111111111111111"), "cvv", List.of("123")))))
        .isEqualTo("78d892f2e92544575c80e3653972303dee23ae714171ebd7770669ce8ce4e3f9");
    assertThat(
            RequestFingerprint.of(
                A_RUN,
                new FingerprintedRequest(
                    POST,
                    CHARGE,
                    Map.of("number", List.of("4111111111111111"), "cvv", List.of("124")))))
        .isEqualTo("5c59f2cdd498b92802831c28120379a2a67a20ab5914665584ccedc00ba703d8");
  }

  @Test
  void a_retry_that_sends_the_same_parameters_in_another_order_is_the_same_request() {
    Map<String, List<String>> asSent = new LinkedHashMap<>();
    asSent.put("amount", List.of("1099"));
    asSent.put("currency", List.of("usd"));
    Map<String, List<String>> asRetried = new LinkedHashMap<>();
    asRetried.put("currency", List.of("usd"));
    asRetried.put("amount", List.of("1099"));

    assertThat(RequestFingerprint.of(A_RUN, new FingerprintedRequest(POST, CHARGE, asSent)))
        .isEqualTo(RequestFingerprint.of(A_RUN, new FingerprintedRequest(POST, CHARGE, asRetried)));
    assertThat(
            new FingerprintedRequest(POST, CHARGE, asSent)
                .isTheSameRequestAs(new FingerprintedRequest(POST, CHARGE, asRetried)))
        .isTrue();
  }

  @Test
  void a_retry_that_changes_one_parameter_is_a_different_request() {
    FingerprintedRequest sent =
        new FingerprintedRequest(POST, CHARGE, Map.of("amount", List.of("1099")));
    FingerprintedRequest changed =
        new FingerprintedRequest(POST, CHARGE, Map.of("amount", List.of("2000")));

    assertThat(RequestFingerprint.of(A_RUN, sent))
        .isNotEqualTo(RequestFingerprint.of(A_RUN, changed));
    assertThat(sent.isTheSameRequestAs(changed)).isFalse();
  }

  @Test
  void a_retry_that_sends_a_parameter_twice_is_a_different_request() {
    FingerprintedRequest once =
        new FingerprintedRequest(POST, CHARGE, Map.of("expand", List.of("a")));
    FingerprintedRequest twice =
        new FingerprintedRequest(POST, CHARGE, Map.of("expand", List.of("a", "b")));

    assertThat(RequestFingerprint.of(A_RUN, once))
        .isNotEqualTo(RequestFingerprint.of(A_RUN, twice));
    assertThat(once.comparedParameters()).containsExactly("expand=a");
    assertThat(twice.comparedParameters()).containsExactly("expand=a", "expand=b");
  }

  @Test
  void the_same_parameters_sent_to_another_endpoint_are_a_different_request() {
    Map<String, List<String>> form = Map.of("amount", List.of("1099"));
    FingerprintedRequest charged = new FingerprintedRequest(POST, CHARGE, form);
    FingerprintedRequest captured = new FingerprintedRequest(POST, CHARGE + "/pi_1/capture", form);
    FingerprintedRequest read = new FingerprintedRequest("GET", CHARGE, form);

    assertThat(RequestFingerprint.of(A_RUN, charged))
        .isNotEqualTo(RequestFingerprint.of(A_RUN, captured))
        .isNotEqualTo(RequestFingerprint.of(A_RUN, read));
    assertThat(charged.isTheSameRequestAs(captured)).isFalse();
    assertThat(charged.isTheSameRequestAs(read)).isFalse();
  }

  @Test
  void a_fingerprint_is_the_hexadecimal_digest_and_carries_nothing_a_client_sent() {
    String fingerprint =
        RequestFingerprint.of(
            A_RUN,
            new FingerprintedRequest(
                POST, CHARGE, Map.of("receipt_email", List.of("payer@example.test"))));

    assertThat(fingerprint).hasSize(64).matches("[0-9a-f]{64}");
    assertThat(fingerprint).doesNotContain("payer");
  }
}
