package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.smoke.CannedTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TranzilaCompatibilityChecksTest {

  private static final String CREATE = "/v1/transaction/credit_card/create";

  private static final String CONFIG = "/__simulator/tranzila/config";

  private static final String HANDSHAKE = "/v2/handshake/create";

  private static final String PAGE = "/tranzila/directng/smoketerminal/iframenew.php";

  private static final String PROCESS = "/tranzila/hf/process/ajax_process_transaction";

  private static final Instant AT = Instant.parse("2026-10-13T12:00:00Z");

  private static final String A_DEBIT =
      "{\"error_code\":0,\"message\":\"success\",\"transaction_result\":{"
          + "\"processor_response_code\":\"000\",\"txn_type\":\"debit\",\"tranmode\":\"A\"}}";

  private static final String NOT_FOUND =
      "{\"error_code\":20112,\"message\":\"Original transaction not found\"}";

  private static final String REFUSED = "{\"error_code\":\"20000\",\"message\":\"x\"}";

  private static final String THE_TRAIL =
      "[{\"direction\":\"INBOUND_REQUEST\",\"outcome\":null},"
          + "{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]";

  private final List<ContractCheck> checks =
      new TranzilaCompatibilityChecks(
              new CheckJson(), new CountingIds(), TranzilaDoubles.clockAt(AT))
          .all();

  private final SmokeRunner runner = new SmokeRunner(Instant::now, provider -> List.of());

  @Test
  void every_value_a_check_expects_is_the_one_the_tranzila_samples_publish() {
    assertThat(expectedBy(checks.get(0)))
        .containsExactly(
            "httpStatus=200",
            "error_code=0",
            "transaction_result.processor_response_code=000",
            "transaction_result.txn_type=debit",
            "transaction_result.tranmode=A");
    assertThat(expectedBy(checks.get(1))).containsExactly("httpStatus=200", "error_code=20112");
    assertThat(expectedBy(checks.get(2))).containsExactly("httpStatus=401", "error_code=20000");
    assertThat(expectedBy(checks.get(3)))
        .containsExactly("audit.direction=INBOUND_REQUEST", "audit.outcome=SUCCESS");
    assertThat(expectedBy(checks.get(4)))
        .containsExactly("httpStatus=200", "error_code=0", "message=Success");
    assertThat(expectedBy(checks.get(5)))
        .containsExactly("httpStatus=401", "error_code=401", "message=Unauthorized");
    assertThat(expectedBy(checks.get(6)))
        .containsExactly("Response=000", "txn_type=debit", "tranmode=A");
    assertThat(expectedBy(checks.get(7)))
        .containsExactly(
            "transaction_response.success=true",
            "transaction_response.processor_response_code=000",
            "transaction_response.currency_code=1",
            "transaction_response.card_locality=domestic",
            "transaction_response.txn_type=debit",
            "transaction_response.tranmode=A",
            "transaction_response.payment_plan=1");
    assertThat(expectedBy(checks.get(8)))
        .containsExactly("code=10000", "message=MANDATORY KEY IS MISSING");
    assertThat(checks)
        .extracting(ContractCheck::operation)
        .containsExactly(
            TranzilaCompatibilityChecks.THE_API_CHARGES,
            TranzilaCompatibilityChecks.THE_API_FINDS_NO_ORIGINAL,
            TranzilaCompatibilityChecks.THE_API_REFUSES_A_TOKEN,
            TranzilaCompatibilityChecks.THE_TRAIL_RECORDS,
            TranzilaCompatibilityChecks.THE_HANDSHAKE_LOCKS_A_SUM,
            TranzilaCompatibilityChecks.THE_HANDSHAKE_REFUSES_A_TOKEN,
            TranzilaCompatibilityChecks.THE_PAGE_RETURNS_THE_PAYER,
            TranzilaCompatibilityChecks.THE_HOSTED_FIELDS_CHARGE,
            TranzilaCompatibilityChecks.THE_HOSTED_FIELDS_REFUSE_A_MISSING_KEY);
  }

  @Test
  void the_charge_holds_on_the_published_answer_and_fails_on_another_tranmode() {
    assertThat(run(0, prepared().answering("POST", CREATE, 200, A_DEBIT)).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    SmokeRun failed =
        run(0, prepared().answering("POST", CREATE, 200, A_DEBIT.replace("\"A\"", "\"V\"")));
    assertThat(((SmokeVerdict.Failed) failed.verdicts().get(0)).field())
        .isEqualTo("transaction_result.tranmode");
  }

  @Test
  void the_charge_fails_naming_each_field_its_answer_got_wrong() {
    assertThat(failedField(0, prepared().answering("POST", CREATE, 201, A_DEBIT)))
        .isEqualTo("httpStatus");
    assertThat(
            failedField(
                0,
                prepared()
                    .answering(
                        "POST",
                        CREATE,
                        200,
                        A_DEBIT.replace("\"error_code\":0", "\"error_code\":1"))))
        .isEqualTo("error_code");
    assertThat(
            failedField(
                0,
                prepared().answering("POST", CREATE, 200, A_DEBIT.replace("\"000\"", "\"777\""))))
        .isEqualTo("transaction_result.processor_response_code");
    assertThat(
            failedField(
                0,
                prepared()
                    .answering("POST", CREATE, 200, A_DEBIT.replace("\"debit\"", "\"verify\""))))
        .isEqualTo("transaction_result.txn_type");
  }

  @Test
  void the_missing_original_holds_on_20112_at_200_and_fails_on_another_status_or_code() {
    assertThat(run(1, prepared().answering("POST", CREATE, 200, NOT_FOUND)).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    assertThat(failedField(1, prepared().answering("POST", CREATE, 404, NOT_FOUND)))
        .isEqualTo("httpStatus");
    assertThat(
            failedField(
                1, prepared().answering("POST", CREATE, 200, NOT_FOUND.replace("20112", "20111"))))
        .isEqualTo("error_code");
  }

  @Test
  void the_refused_token_holds_on_401_20000_and_fails_on_another_status_or_code() {
    assertThat(run(2, prepared().answering("POST", CREATE, 401, REFUSED)).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    assertThat(failedField(2, prepared().answering("POST", CREATE, 403, REFUSED)))
        .isEqualTo("httpStatus");
    assertThat(
            failedField(
                2, prepared().answering("POST", CREATE, 401, REFUSED.replace("20000", "20002"))))
        .isEqualTo("error_code");
  }

  @Test
  void the_trail_holds_when_it_reads_the_request_and_then_the_answer() {
    CannedTransport transport =
        prepared()
            .answering("POST", CREATE, 200, A_DEBIT)
            .answering("GET", "/__simulator/tranzila/audit", 200, THE_TRAIL);

    assertThat(run(3, transport).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    assertThat(
            failedField(
                3,
                prepared()
                    .answering("POST", CREATE, 200, A_DEBIT)
                    .answering(
                        "GET",
                        "/__simulator/tranzila/audit",
                        200,
                        THE_TRAIL.replace("\"SUCCESS\"", "\"FAILURE\""))))
        .isEqualTo("audit.outcome");
    assertThat(
            failedField(
                3,
                prepared()
                    .answering("POST", CREATE, 200, A_DEBIT)
                    .answering(
                        "GET",
                        "/__simulator/tranzila/audit",
                        200,
                        THE_TRAIL.replace("\"INBOUND_REQUEST\"", "\"OUTBOUND_RESPONSE\""))))
        .isEqualTo("audit.direction");
  }

  @Test
  void the_charge_is_signed_with_a_token_the_configured_secret_recomputes_on_the_run_clock() {
    CannedTransport transport = prepared().answering("POST", CREATE, 200, A_DEBIT);
    run(0, transport);

    CheckRequest sent =
        transport.sent().stream()
            .filter(request -> CREATE.equals(request.path()))
            .findFirst()
            .orElseThrow();
    String appKey = theConfigured(transport, "appKey");
    String secret = theConfigured(transport, "appSecret");
    String requestTime = sent.headers().get(TranzilaFields.REQUEST_TIME);
    String nonce = sent.headers().get(TranzilaFields.NONCE);

    assertThat(requestTime).isEqualTo(Long.toString(AT.getEpochSecond()));
    assertThat(nonce).hasSize(80);
    assertThat(sent.headers().get(TranzilaFields.APP_KEY)).isEqualTo(appKey);
    assertThat(sent.headers().get(TranzilaFields.ACCESS_TOKEN))
        .isEqualTo(TranzilaAccessToken.hexOfTheHmac(secret + requestTime + nonce, appKey));
    assertThat(String.valueOf(sent.body()))
        .contains("\"terminal_name\":\"smoketerminal\"")
        .contains("\"unit_price\":10.53");
    assertThat(transport.sent().get(0).path()).isEqualTo("/__simulator/tranzila/reset");
  }

  @Test
  void the_refused_token_sends_the_configured_app_key_with_a_token_no_secret_produced() {
    CannedTransport transport = prepared().answering("POST", CREATE, 401, REFUSED);
    run(2, transport);

    CheckRequest sent =
        transport.sent().stream()
            .filter(request -> CREATE.equals(request.path()))
            .findFirst()
            .orElseThrow();
    assertThat(sent.headers().get(TranzilaFields.ACCESS_TOKEN)).isEqualTo("not-the-access-token");
    assertThat(sent.headers().get(TranzilaFields.APP_KEY))
        .isEqualTo(theConfigured(transport, "appKey"));
    assertThat(sent.headers().get(TranzilaFields.REQUEST_TIME))
        .isEqualTo(Long.toString(AT.getEpochSecond()));
  }

  @Test
  void the_missing_original_credits_a_reference_no_transaction_holds() {
    CannedTransport transport = prepared().answering("POST", CREATE, 200, NOT_FOUND);
    run(1, transport);

    assertThat(
            String.valueOf(
                transport.sent().stream()
                    .filter(request -> CREATE.equals(request.path()))
                    .findFirst()
                    .orElseThrow()
                    .body()))
        .contains("\"txn_type\":\"credit\"")
        .contains("\"reference_txn_id\":99999999");
  }

  @Test
  void the_handshake_holds_on_the_published_success_and_fails_naming_what_differs() {
    String created = "{\"error_code\":0,\"message\":\"Success\",\"thtk\":\"w1\"}";
    CannedTransport transport = prepared().answering("POST", HANDSHAKE, 200, created);

    assertThat(run(4, transport).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    assertThat(failedField(4, prepared().answering("POST", HANDSHAKE, 201, created)))
        .isEqualTo("httpStatus");
    assertThat(
            failedField(
                4, prepared().answering("POST", HANDSHAKE, 200, created.replace(":0", ":1"))))
        .isEqualTo("error_code");
    assertThat(
            failedField(
                4,
                prepared()
                    .answering("POST", HANDSHAKE, 200, created.replace("Success", "success"))))
        .isEqualTo("message");
    CheckRequest sent =
        transport.sent().stream()
            .filter(request -> HANDSHAKE.equals(request.path()))
            .findFirst()
            .orElseThrow();
    assertThat(String.valueOf(sent.body()))
        .isEqualTo("{\"terminal_name\":\"smoketerminal\",\"sum\":10.53}");
    assertThat(sent.headers().get(TranzilaFields.ACCESS_TOKEN))
        .isEqualTo(
            TranzilaAccessToken.hexOfTheHmac(
                theConfigured(transport, "appSecret")
                    + sent.headers().get(TranzilaFields.REQUEST_TIME)
                    + sent.headers().get(TranzilaFields.NONCE),
                theConfigured(transport, "appKey")));
  }

  @Test
  void the_refused_handshake_holds_on_the_published_401_and_fails_naming_what_differs() {
    String refused = "{\"error_code\":401,\"message\":\"Unauthorized\"}";
    CannedTransport transport = prepared().answering("POST", HANDSHAKE, 401, refused);

    assertThat(run(5, transport).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    assertThat(failedField(5, prepared().answering("POST", HANDSHAKE, 400, refused)))
        .isEqualTo("httpStatus");
    assertThat(
            failedField(
                5,
                prepared().answering("POST", HANDSHAKE, 401, refused.replace("Unauthorized", ""))))
        .isEqualTo("message");
    assertThat(
            transport.sent().stream()
                .filter(request -> HANDSHAKE.equals(request.path()))
                .findFirst()
                .orElseThrow()
                .headers()
                .get(TranzilaFields.ACCESS_TOKEN))
        .isEqualTo("not-the-access-token");
  }

  @Test
  void the_page_return_holds_when_the_payer_is_posted_back_with_the_published_values() {
    String page =
        "<form method=\"post\" action=\"/tranzila/directng/smoketerminal/payer/directng_1\">";
    String returned =
        "<input type=\"hidden\" name=\"Response\" value=\"000\">"
            + "<input type=\"hidden\" name=\"sum\" value=\"10.53\">"
            + "<input type=\"hidden\" name=\"txn_type\" value=\"debit\">"
            + "<input type=\"hidden\" name=\"tranmode\" value=\"A\">";
    CannedTransport transport =
        prepared()
            .answering("GET", PAGE, 200, page)
            .answering("POST", "/tranzila/directng/smoketerminal/payer/directng_1", 200, returned);

    assertThat(run(6, transport).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    CheckRequest paid = transport.sent().get(transport.sent().size() - 1);
    assertThat(paid.path()).isEqualTo("/tranzila/directng/smoketerminal/payer/directng_1");
    assertThat(String.valueOf(paid.body()))
        .contains("card=published-9022")
        .contains("outcome=PAID");
    assertThat(
            failedField(
                6,
                prepared()
                    .answering("GET", PAGE, 200, page)
                    .answering(
                        "POST",
                        "/tranzila/directng/smoketerminal/payer/directng_1",
                        200,
                        returned.replace("\"000\"", "\"004\""))))
        .isEqualTo("Response");
    assertThat(failedField(6, prepared().answering("GET", PAGE, 400, "refused")))
        .isEqualTo("Response");
  }

  @Test
  void the_hosted_fields_charge_holds_on_the_published_answer_and_fails_naming_what_differs() {
    String created = "{\"error_code\":0,\"message\":\"Success\",\"thtk\":\"w1\"}";
    String charged =
        "{\"errors\":null,\"transaction_response\":{\"success\":true,"
            + "\"processor_response_code\":\"000\",\"currency_code\":1,"
            + "\"card_locality\":\"domestic\",\"txn_type\":\"debit\",\"tranmode\":\"A\","
            + "\"payment_plan\":1},\"response_hash\":\"h\"}";
    CannedTransport transport =
        prepared()
            .answering("POST", HANDSHAKE, 200, created)
            .answering("POST", PROCESS, 200, charged);

    assertThat(run(7, transport).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    CheckRequest sent = transport.sent().get(transport.sent().size() - 1);
    assertThat(sent.path()).isEqualTo(PROCESS);
    assertThat(String.valueOf(sent.body()))
        .isEqualTo(
            "terminal_name=smoketerminal&thtk=w1&amount=10.53"
                + "&credit_card_number=4907639999909022&expiry_month=12&expiry_year=2030");
    assertThat(
            failedField(
                7,
                prepared()
                    .answering("POST", HANDSHAKE, 200, created)
                    .answering(
                        "POST",
                        PROCESS,
                        200,
                        charged.replace("\"currency_code\":1", "\"currency_code\":2"))))
        .isEqualTo("transaction_response.currency_code");
    assertThat(
            failedField(
                7,
                prepared()
                    .answering("POST", HANDSHAKE, 200, created)
                    .answering("POST", PROCESS, 200, charged.replace("true", "false"))))
        .isEqualTo("transaction_response.success");
  }

  @Test
  void the_hosted_fields_refusal_of_a_missing_thtk_holds_on_10000_and_fails_on_another_code() {
    String created = "{\"error_code\":0,\"message\":\"Success\",\"thtk\":\"w1\"}";
    String refused =
        "{\"errors\":[{\"code\":10000,\"param\":\"thtk\","
            + "\"message\":\"MANDATORY KEY IS MISSING\"}],\"transaction_response\":null}";
    CannedTransport transport =
        prepared()
            .answering("POST", HANDSHAKE, 200, created)
            .answering("POST", PROCESS, 200, refused);

    assertThat(run(8, transport).verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Passed.class);
    assertThat(String.valueOf(transport.sent().get(transport.sent().size() - 1).body()))
        .startsWith("terminal_name=smoketerminal&thtk=&amount=10.53");
    assertThat(
            failedField(
                8,
                prepared()
                    .answering("POST", HANDSHAKE, 200, created)
                    .answering("POST", PROCESS, 200, refused.replace("10000", "10006"))))
        .isEqualTo("code");
  }

  private SmokeRun run(int check, CannedTransport transport) {
    return runner.run(List.of(checks.get(check)), transport);
  }

  private String failedField(int check, CannedTransport transport) {
    SmokeRun run = run(check, transport);
    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Failed.class);
    return ((SmokeVerdict.Failed) run.verdicts().get(0)).field();
  }

  private static String theConfigured(CannedTransport transport, String property) {
    for (CheckRequest sent : transport.sent()) {
      if (CONFIG.equals(sent.path())) {
        Matcher found =
            Pattern.compile("\"" + property + "\":\"([^\"]+)\"")
                .matcher(String.valueOf(sent.body()));
        assertThat(found.find()).isTrue();
        return found.group(1);
      }
    }
    throw new AssertionError("no configuration was written");
  }

  private static List<String> expectedBy(ContractCheck check) {
    return check.expectations().stream()
        .map(expectation -> expectation.field() + "=" + expectation.published().value())
        .toList();
  }

  private static CannedTransport prepared() {
    return new CannedTransport()
        .answering("POST", "/__simulator/tranzila/reset", 200, "{}")
        .answering("PUT", CONFIG, 200, "{}");
  }
}
