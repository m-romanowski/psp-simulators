package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TranzilaHandshakeTest {

  private static final String APP_KEY = "key-account-1";

  private static final String SECRET = "secret-account-1";

  private static final String SECONDS = Long.toString(TranzilaWiring.NOW.getEpochSecond());

  private static final String THE_FIRST_THTK = "w" + "0".repeat(40) + "1";

  private static final String UNAUTHORIZED = "{\"error_code\":401,\"message\":\"Unauthorized\"}";

  private static final String BAD_REQUEST = "{\"error_code\":400,\"message\":\"\"}";

  private final TranzilaWiring wired = new TranzilaWiring().configuring(Map.of());

  @Test
  void a_signed_handshake_answers_the_published_success_and_locks_the_sum_for_twenty_minutes() {
    TranzilaApiResponse answered =
        handshake(
            signed("n-1"),
            "{\"terminal_name\":\"myterminal\",\"sum\":150,"
                + "\"request_params\":{\"order_id\":\"ORD-9871\",\"user_id\":12}}");

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(answered))
        .isEqualTo(
            "{\"error_code\":0,\"message\":\"Success\",\"thtk\":\"" + THE_FIRST_THTK + "\"}");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isNull();
    assertThat(wired.handshakes.all())
        .containsExactly(
            new TranzilaHandshake(
                THE_FIRST_THTK,
                TranzilaWiring.RUN,
                TranzilaWiring.ACCOUNT,
                TranzilaWiring.TERMINAL,
                "150",
                "{\"order_id\":\"ORD-9871\",\"user_id\":12}",
                Instant.parse("2026-10-13T12:00:00Z"),
                Instant.parse("2026-10-13T12:20:00Z")));
  }

  @Test
  void a_sum_with_trailing_zeros_is_locked_as_written_and_no_params_are_kept_for_none_sent() {
    handshake(signed("n-2"), "{\"terminal_name\":\"myterminal\",\"sum\":10.50}");

    assertThat(wired.handshakes.all()).extracting(TranzilaHandshake::sum).containsExactly("10.50");
    assertThat(wired.handshakes.all())
        .extracting(TranzilaHandshake::requestParams)
        .containsOnlyNulls();
  }

  @Test
  void every_body_the_schema_does_not_admit_is_answered_400_with_an_empty_message() {
    List<String> refused =
        List.of(
            "not json",
            "[]",
            "{\"sum\":1}",
            "{\"terminal_name\":7,\"sum\":1}",
            "{\"terminal_name\":\"myterminal\"}",
            "{\"terminal_name\":\"myterminal\",\"sum\":\"10\"}",
            "{\"terminal_name\":\"myterminal\",\"sum\":0}",
            "{\"terminal_name\":\"myterminal\",\"sum\":-1}",
            "{\"terminal_name\":\"myterminal\",\"sum\":1,\"request_params\":\"order\"}",
            "{\"terminal_name\":\"myterminal\",\"sum\":1,\"currency\":\"ILS\"}",
            "{\"terminal_name\":\"myterminal\",\"sum\":1.5e2}",
            "{\"terminal_name\":\"myterminal\",\"sum\":1e999999999}",
            "{\"terminal_name\":\"myterminal\",\"sum\":1E2}",
            "{\"terminal_name\":\"myterminal\",\"sum\":1234567890123456}",
            "{\"terminal_name\":\"myterminal\",\"sum\":1.1234567}");

    for (int at = 0; at < refused.size(); at++) {
      TranzilaApiResponse answered = handshake(signed("bad-" + at), refused.get(at));
      assertThat(answered.httpStatus()).as(refused.get(at)).isEqualTo(400);
      assertThat(bodyOf(answered)).as(refused.get(at)).isEqualTo(BAD_REQUEST);
      assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    }
    assertThat(wired.handshakes.all()).isEmpty();
  }

  @Test
  void a_handshake_for_another_terminal_is_unauthorized() {
    TranzilaApiResponse answered =
        handshake(signed("n-4"), "{\"terminal_name\":\"elsewhere\",\"sum\":1}");

    assertThat(answered.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(answered)).isEqualTo(UNAUTHORIZED);
    assertThat(wired.handshakes.all()).isEmpty();
  }

  @Test
  void a_wrong_token_and_an_unknown_app_key_are_both_answered_in_the_handshakes_own_401() {
    Map<String, List<String>> wrongToken = signed("n-5");
    wrongToken.put(TranzilaFields.ACCESS_TOKEN, List.of("00"));
    Map<String, List<String>> unknownKey = signed("n-6");
    unknownKey.put(TranzilaFields.APP_KEY, List.of("nobody"));

    TranzilaApiResponse refusedToken =
        handshake(wrongToken, "{\"terminal_name\":\"myterminal\",\"sum\":1}");
    TranzilaApiResponse refusedKey =
        handshake(unknownKey, "{\"terminal_name\":\"myterminal\",\"sum\":1}");

    assertThat(refusedToken.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(refusedToken)).isEqualTo(UNAUTHORIZED);
    assertThat(refusedKey.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(refusedKey)).isEqualTo(UNAUTHORIZED);
  }

  @Test
  void a_nonce_spent_on_a_handshake_cannot_sign_a_second_one() {
    handshake(signed("n-7"), "{\"terminal_name\":\"myterminal\",\"sum\":1}");

    TranzilaApiResponse replayed =
        handshake(signed("n-7"), "{\"terminal_name\":\"myterminal\",\"sum\":1}");

    assertThat(replayed.httpStatus()).isEqualTo(401);
    assertThat(wired.handshakes.all()).hasSize(1);
  }

  @Test
  void
      a_thtk_already_taken_is_drawn_again_and_five_taken_in_a_row_is_a_failure_of_this_simulator() {
    wired.handshakes.theNextThtksAreTaken(2);
    TranzilaApiResponse drawnAgain =
        handshake(signed("n-8"), "{\"terminal_name\":\"myterminal\",\"sum\":1}");
    wired.handshakes.theNextThtksAreTaken(5);

    assertThat(bodyOf(drawnAgain)).contains("\"thtk\":\"w" + "0".repeat(40) + "3\"");
    assertThatThrownBy(
            () -> handshake(signed("n-9"), "{\"terminal_name\":\"myterminal\",\"sum\":1}"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("no Tranzila thtk could be claimed in 5 attempts");
  }

  @Test
  void the_trail_keeps_the_handshake_and_its_answer_with_the_access_token_withheld() {
    handshake(signed("n-10"), "{\"terminal_name\":\"myterminal\",\"sum\":1}");

    AuditRecord inbound = theRow(AuditDirection.INBOUND_REQUEST);
    AuditRecord outbound = theRow(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(inbound.path()).isEqualTo("/v2/handshake/create");
    assertThat(inbound.sanitizedHeaders().get(TranzilaFields.ACCESS_TOKEN))
        .containsExactly("[redacted]");
    assertThat(outbound.httpStatus()).isEqualTo(Integer.valueOf(200));
    assertThat(outbound.sanitizedBody()).containsEntry("thtk", List.of(THE_FIRST_THTK));
  }

  @Test
  void a_handshake_locks_the_terminal_and_the_sum_it_was_made_for_until_it_expires() {
    TranzilaHandshake held =
        new TranzilaHandshake(
            THE_FIRST_THTK,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            "150",
            null,
            TranzilaWiring.NOW,
            Instant.parse("2026-10-13T12:20:00Z"));

    assertThat(held.locks("myterminal", new BigDecimal("150.00"))).isTrue();
    assertThat(held.locks("myterminal", new BigDecimal("149.99"))).isFalse();
    assertThat(held.locks("another", new BigDecimal("150"))).isFalse();
    assertThat(held.isLiveAt(Instant.parse("2026-10-13T12:19:59Z"))).isTrue();
    assertThat(held.isLiveAt(Instant.parse("2026-10-13T12:20:00Z"))).isFalse();
  }

  private AuditRecord theRow(AuditDirection direction) {
    return wired.audit.all().stream()
        .filter(record -> record.direction() == direction)
        .reduce((first, last) -> last)
        .orElseThrow();
  }

  private TranzilaApiResponse handshake(Map<String, List<String>> headers, String body) {
    return wired.api.handshake(
        TranzilaApiCall.of("POST", TranzilaApiController.CREATE_HANDSHAKE, headers, body));
  }

  private static Map<String, List<String>> signed(String nonce) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(TranzilaFields.APP_KEY, List.of(APP_KEY));
    headers.put(TranzilaFields.REQUEST_TIME, List.of(SECONDS));
    headers.put(TranzilaFields.NONCE, List.of(nonce));
    headers.put(TranzilaFields.ACCESS_TOKEN, List.of(hmac(SECRET + SECONDS + nonce, APP_KEY)));
    return headers;
  }

  private static String hmac(String key, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new AssertionError(unavailable);
    }
  }

  private static String bodyOf(TranzilaApiResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
