package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TranzilaApiTest {

  private static final String APP_KEY = "key-account-1";

  private static final String SECRET = "secret-account-1";

  private static final String SECONDS = Long.toString(TranzilaWiring.NOW.getEpochSecond());

  private static final String MILLIS = Long.toString(TranzilaWiring.NOW.toEpochMilli());

  private final TranzilaWiring wired = new TranzilaWiring().configuring(Map.of());

  @Test
  void the_authentication_pages_token_is_an_hmac_of_the_app_key_keyed_by_secret_time_and_nonce() {
    assertThat(
            TranzilaAccessToken.AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT.expectedFor(
                "app", "secret", "1700000000", "nonce"))
        .isEqualTo(hmac("secret1700000000nonce", "app"));
  }

  @Test
  void the_quickstart_token_is_an_hmac_of_app_key_time_and_nonce_keyed_by_the_secret() {
    assertThat(
            TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT.expectedFor(
                "app", "secret", "1700000000000", "nonce"))
        .isEqualTo(hmac("secret", "app1700000000000nonce"));
  }

  @Test
  void a_call_signed_as_the_authentication_pages_publish_it_is_answered() {
    TranzilaApiResponse answered = call(signedHeaders(SECONDS, "n-1"));

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(answered)).contains("\"processor_response_code\":\"000\"");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isEqualTo("10000001");
    assertThat(answered.testRunId()).isEqualTo(TranzilaWiring.RUN);
  }

  @Test
  void the_access_token_is_compared_without_regard_to_the_case_of_its_hex() {
    Map<String, List<String>> headers = signedHeaders(SECONDS, "n-2");
    headers.put(
        TranzilaFields.ACCESS_TOKEN,
        List.of(headers.get(TranzilaFields.ACCESS_TOKEN).get(0).toUpperCase(Locale.ROOT)));

    assertThat(call(headers).httpStatus()).isEqualTo(200);
  }

  @Test
  void
      a_call_signed_as_the_quickstart_signs_it_is_answered_for_an_account_naming_that_construction() {
    wired.configuring(
        Map.of(TranzilaAccessToken.SETTING, TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT.name()));
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(TranzilaFields.APP_KEY, List.of(APP_KEY));
    headers.put(TranzilaFields.REQUEST_TIME, List.of(MILLIS));
    headers.put(TranzilaFields.NONCE, List.of("n-3"));
    headers.put(TranzilaFields.ACCESS_TOKEN, List.of(hmac(SECRET, APP_KEY + MILLIS + "n-3")));

    assertThat(call(headers).httpStatus()).isEqualTo(200);
    assertThat(call(signedHeaders(SECONDS, "n-4")).httpStatus()).isEqualTo(401);
  }

  @Test
  void a_request_time_five_minutes_from_the_run_clock_is_answered_and_one_second_more_is_not() {
    long now = TranzilaWiring.NOW.getEpochSecond();

    assertThat(call(signedHeaders(Long.toString(now - 300), "n-5")).httpStatus()).isEqualTo(200);
    assertThat(call(signedHeaders(Long.toString(now + 300), "n-6")).httpStatus()).isEqualTo(200);
    TranzilaApiResponse late = call(signedHeaders(Long.toString(now - 301), "n-7"));
    TranzilaApiResponse early = call(signedHeaders(Long.toString(now + 301), "n-8"));

    assertThat(late.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(late))
        .isEqualTo(
            "{\"error_code\":\"20000\",\"message\":\"Authentication failed: "
                + TranzilaApi.THE_REQUEST_TIME_IS_OUT_OF_THE_WINDOW.replace("\"", "\\\"")
                + "\"}");
    assertThat(early.httpStatus()).isEqualTo(401);
  }

  @Test
  void a_request_time_in_seconds_is_refused_by_an_account_expecting_milliseconds() {
    wired.configuring(
        Map.of(TranzilaAccessToken.SETTING, TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT.name()));

    assertThat(bodyOf(call(signedHeaders(SECONDS, "n-9"))))
        .contains("X-tranzila-api-request-time is more than five minutes");
  }

  @Test
  void a_request_time_that_is_not_a_whole_number_is_refused_naming_the_unit_expected() {
    assertThat(bodyOf(call(signedHeaders("12:00", "n-10"))))
        .contains("X-tranzila-api-request-time is not a whole number of Unix seconds");
    wired.configuring(
        Map.of(TranzilaAccessToken.SETTING, TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT.name()));
    assertThat(bodyOf(call(signedHeaders("1.5", "n-11"))))
        .contains("X-tranzila-api-request-time is not a whole number of Unix milliseconds");
  }

  @Test
  void a_nonce_used_again_within_ten_minutes_is_refused_and_after_them_is_answered() {
    Map<String, List<String>> headers = signedHeaders(SECONDS, "n-12");
    TranzilaApiResponse first = call(headers);
    TranzilaApiResponse reused = call(headers);
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofMinutes(10)).plusSeconds(1));
    TranzilaApiResponse later =
        call(
            signedHeaders(
                Long.toString(TranzilaWiring.NOW.plus(Duration.ofMinutes(10)).getEpochSecond()),
                "n-12"));

    assertThat(first.httpStatus()).isEqualTo(200);
    assertThat(reused.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(reused))
        .isEqualTo(
            "{\"error_code\":\"20000\",\"message\":\"Authentication failed: "
                + TranzilaApi.THE_NONCE_WAS_USED.replace("\"", "\\\"")
                + "\"}");
    assertThat(later.httpStatus()).isEqualTo(200);
  }

  @Test
  void a_nonce_is_refused_again_for_as_long_as_the_request_time_it_came_with_is_accepted() {
    String lateButAccepted = Long.toString(TranzilaWiring.NOW.getEpochSecond() + 300);
    Map<String, List<String>> headers = signedHeaders(lateButAccepted, "n-replay");

    assertThat(call(headers).httpStatus()).isEqualTo(200);
    for (long after : List.of(301L, 450L, 600L)) {
      wired.clock.moveTo(TranzilaWiring.NOW.plusSeconds(after));
      TranzilaApiResponse replayed = call(headers);
      assertThat(replayed.httpStatus()).as("replayed at T+%s", after).isEqualTo(401);
      assertThat(bodyOf(replayed)).contains("already used by a call of this account");
    }
  }

  @Test
  void a_request_time_in_milliseconds_is_accepted_up_to_five_minutes_from_the_run_clock() {
    wired.configuring(
        Map.of(TranzilaAccessToken.SETTING, TranzilaAccessToken.AS_THE_QUICKSTART_SIGNS_IT.name()));
    long now = TranzilaWiring.NOW.toEpochMilli();

    assertThat(call(quickstart(Long.toString(now + 300_000), "q-1")).httpStatus()).isEqualTo(200);
    assertThat(call(quickstart(Long.toString(now - 300_000), "q-2")).httpStatus()).isEqualTo(200);
    assertThat(call(quickstart(Long.toString(now + 301_000), "q-3")).httpStatus()).isEqualTo(401);
    assertThat(call(quickstart(Long.toString(now - 301_000), "q-4")).httpStatus()).isEqualTo(401);
  }

  @Test
  void a_call_with_a_wrong_token_does_not_spend_its_nonce() {
    Map<String, List<String>> wrong = signedHeaders(SECONDS, "n-13");
    wrong.put(TranzilaFields.ACCESS_TOKEN, List.of("00"));

    TranzilaApiResponse refused = call(wrong);

    assertThat(bodyOf(refused))
        .contains(TranzilaApi.THE_ACCESS_TOKEN_DOES_NOT_MATCH + "tranzila.access-token");
    assertThat(call(signedHeaders(SECONDS, "n-13")).httpStatus()).isEqualTo(200);
  }

  @Test
  void each_of_the_three_signing_headers_is_required_and_named_when_missing() {
    for (String header :
        List.of(TranzilaFields.REQUEST_TIME, TranzilaFields.NONCE, TranzilaFields.ACCESS_TOKEN)) {
      Map<String, List<String>> headers = signedHeaders(SECONDS, "n-14-" + header);
      headers.remove(header);

      assertThat(bodyOf(call(headers)))
          .isEqualTo(
              "{\"error_code\":\"20000\",\"message\":\"Authentication failed: "
                  + header
                  + " is required on every call, as the authentication page's header table"
                  + " publishes it\"}");
    }
  }

  @Test
  void an_app_key_no_account_configures_is_refused_401_20000_and_the_call_names_no_account() {
    Map<String, List<String>> headers = signedHeaders(SECONDS, "n-15");
    headers.put(TranzilaFields.APP_KEY, List.of("nobody"));

    TranzilaApiResponse refused = call(headers);

    assertThat(refused.httpStatus()).isEqualTo(401);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error_code\":\"20000\",\"message\":\"Authentication failed: the"
                + " X-tranzila-api-app-key header names no app key this test run configures\"}");
    assertThat(wired.audit.all()).allSatisfy(record -> assertThat(record.accountId()).isNull());
    assertThat(refused.headers()).containsKey("Content-Type");
  }

  @Test
  void a_call_without_an_app_key_is_refused_401() {
    Map<String, List<String>> headers = signedHeaders(SECONDS, "n-16");
    headers.remove(TranzilaFields.APP_KEY);

    assertThat(call(headers).httpStatus()).isEqualTo(401);
  }

  @Test
  void an_account_that_does_not_verify_the_token_answers_a_call_carrying_only_its_app_key() {
    wired.configuring(
        Map.of(
            TranzilaAuthentication.SETTING,
            TranzilaAuthentication.THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name()));

    assertThat(call(Map.of(TranzilaFields.APP_KEY, List.of(APP_KEY))).httpStatus()).isEqualTo(200);
  }

  @Test
  void the_trail_keeps_the_request_and_the_answer_withholding_the_token_and_the_card() {
    call(signedHeaders(SECONDS, "n-17"));

    AuditRecord inbound = theRow(AuditDirection.INBOUND_REQUEST);
    AuditRecord outbound = theRow(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(inbound.provider()).isEqualTo(TranzilaApi.TRANZILA);
    assertThat(inbound.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(inbound.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(inbound.method()).isEqualTo("POST");
    assertThat(inbound.path()).isEqualTo("/v1/transaction/credit_card/create");
    assertThat(inbound.createdAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(inbound.sanitizedHeaders().get(TranzilaFields.ACCESS_TOKEN))
        .containsExactly("[redacted]");
    assertThat(inbound.sanitizedHeaders().get(TranzilaFields.APP_KEY)).containsExactly(APP_KEY);
    assertThat(inbound.sanitizedBody().get("card_number")).containsExactly("[redacted]");
    assertThat(inbound.sanitizedBody().get("terminal_name")).containsExactly("myterminal");
    assertThat(inbound.auditId()).startsWith("aud_").hasSize(28);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.httpStatus()).isEqualTo(200);
    assertThat(outbound.providerObjectId()).isEqualTo("10000001");
    assertThat(outbound.sanitizedBody().get("transaction_result.token"))
        .containsExactly("[redacted]");
    assertThat(outbound.sanitizedBody().get("original_request.card_number"))
        .containsExactly("[redacted]");
    assertThat(outbound.sanitizedBody().get("transaction_result.last_4")).containsExactly("4245");
  }

  @Test
  void a_body_that_is_no_json_leaves_the_inbound_row_without_fields() {
    TranzilaApiResponse refused =
        wired.api.create(
            TranzilaApiCall.of(
                "POST",
                TranzilaApiController.CREATE,
                Map.of(
                    TranzilaFields.APP_KEY, List.of(APP_KEY), "X-Simulator-Ignored", List.of(" ")),
                "not json"));

    assertThat(refused.httpStatus()).isEqualTo(401);
    assertThat(theRow(AuditDirection.INBOUND_REQUEST).sanitizedBody()).isEmpty();
  }

  @Test
  void an_answer_a_rule_puts_in_place_of_the_operation_is_delivered_as_the_rule_wrote_it() {
    wired.configuring(
        Map.of(
            TranzilaAuthentication.SETTING,
            TranzilaAuthentication.THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name()));
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));

    TranzilaApiResponse answered = call(Map.of(TranzilaFields.APP_KEY, List.of(APP_KEY)));

    assertThat(answered.httpStatus()).isEqualTo(502);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).scenarioId()).isNotNull();
  }

  @Test
  void an_answer_a_rule_disrupts_carries_the_fault_onto_the_wire() {
    wired.configuring(
        Map.of(
            TranzilaAuthentication.SETTING,
            TranzilaAuthentication.THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name()));
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(503)));

    TranzilaApiResponse answered = call(Map.of(TranzilaFields.APP_KEY, List.of(APP_KEY)));

    assertThat(answered.httpStatus()).isEqualTo(503);
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).delivered().transportFault()).isNotNull();
  }

  private AuditRecord theRow(AuditDirection direction) {
    return wired.audit.all().stream()
        .filter(record -> record.direction() == direction)
        .reduce((first, last) -> last)
        .orElseThrow();
  }

  private TranzilaApiResponse call(Map<String, List<String>> headers) {
    return wired.api.create(
        TranzilaApiCall.of("POST", TranzilaApiController.CREATE, headers, TranzilaWire.aDebit()));
  }

  private static Map<String, List<String>> quickstart(String requestTime, String nonce) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(TranzilaFields.APP_KEY, List.of(APP_KEY));
    headers.put(TranzilaFields.REQUEST_TIME, List.of(requestTime));
    headers.put(TranzilaFields.NONCE, List.of(nonce));
    headers.put(TranzilaFields.ACCESS_TOKEN, List.of(hmac(SECRET, APP_KEY + requestTime + nonce)));
    return headers;
  }

  private static Map<String, List<String>> signedHeaders(String requestTime, String nonce) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(TranzilaFields.APP_KEY, List.of(APP_KEY));
    headers.put(TranzilaFields.REQUEST_TIME, List.of(requestTime));
    headers.put(TranzilaFields.NONCE, List.of(nonce));
    headers.put(TranzilaFields.ACCESS_TOKEN, List.of(hmac(SECRET + requestTime + nonce, APP_KEY)));
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
