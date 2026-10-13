package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.JourneyClient.Kind;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class AzericardTesterJourneyIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "journey-azericard";
  private static final String ACCOUNT = "merchant-1";
  private static final String TERMINAL = "70000013";
  private static final String ORDER = "20260915100001";
  private static final String AMOUNT = "25.00";
  private static final String CURRENCY = "AZN";
  private static final String TIMESTAMP = "20260915100000";
  private static final String NONCE = "F2B2DD7E603A7ADA";

  @LocalServerPort private int port;

  private JourneyClient http;
  private LocalMerchantServer merchant;
  private AzericardMerchant keys;

  @BeforeEach
  void standUpTheMerchant() {
    http = new JourneyClient(port);
    merchant = new LocalMerchantServer();
    keys = new AzericardMerchant();
  }

  @AfterEach
  void takeTheMerchantDown() {
    http.discardTheTestRun("azericard", RUN);
    merchant.close();
  }

  @Test
  void a_tester_takes_an_azericard_hold_from_nothing_to_completed_and_reads_it_back() {
    assertThat(http.getList("the run holds nothing yet", objects())).isEmpty();

    Map<String, Object> configured =
        http.json(
            "configure the terminal, both keys and the callback target",
            Kind.WRITE,
            "PUT",
            "/__simulator/azericard/config",
            """
            {"testRunId":"%s",
             "clock":{"fixedNow":"2026-09-15T10:00:00Z"},
             "accounts":[{"accountId":"%s","configuration":{
               "terminalId":"%s","apiKey":"%s","apiSecret":"%s"}}],
             "webhooks":[{"accountId":"%s","url":"%s"}]}
            """
                .formatted(
                    RUN,
                    ACCOUNT,
                    TERMINAL,
                    AzericardMerchant.forJson(keys.merchantPublicKeyPem()),
                    AzericardMerchant.forJson(keys.gatewayPrivateKeyPem()),
                    ACCOUNT,
                    merchant.url()));
    assertThat(configured)
        .containsEntry("configuredAccounts", List.of(ACCOUNT))
        .containsEntry("configuredWebhookTargets", 1);

    Map<String, Object> rule =
        http.json(
            "pin the outcome with a scenario rule",
            Kind.WRITE,
            "POST",
            "/__simulator/azericard/scenarios",
            """
            {"testRunId":"%s","priority":100,
             "match":{"amount":"%s"},
             "action":{"type":"FIXED_SUCCESS"}}
            """
                .formatted(RUN, AMOUNT));
    String scenarioId = (String) rule.get("scenarioId");
    assertThat(scenarioId).startsWith("scn_");

    MultiValueMap<String, String> authorization = new LinkedMultiValueMap<>();
    authorization.add("TERMINAL", TERMINAL);
    authorization.add("TRTYPE", "0");
    authorization.add("ORDER", ORDER);
    authorization.add("AMOUNT", AMOUNT);
    authorization.add("CURRENCY", CURRENCY);
    authorization.add("TIMESTAMP", TIMESTAMP);
    authorization.add("NONCE", NONCE);
    authorization.add("DESC", "Journey preauthorization");
    authorization.add("MERCH_NAME", "Journey Merchant");
    authorization.add("MERCH_URL", merchant.url());
    authorization.add("BACKREF", merchant.url());
    authorization.add("EMAIL", "payer@merchant.example");
    authorization.add("CARD", "4127208104942601");
    authorization.add("EXP", "05");
    authorization.add("EXP_YEAR", "31");
    authorization.add("CVC2", "796");
    authorization.add(
        "P_SIGN", keys.sign(AMOUNT, CURRENCY, TERMINAL, "0", TIMESTAMP, NONCE, merchant.url()));

    ResponseEntity<String> held =
        http.form(
            "pre-authorize over the gateway's own route",
            Kind.WRITE,
            "/cgi-bin/cgi_link",
            authorization);
    assertThat(held.getHeaders().getContentType().toString()).startsWith("text/html");
    String xml = held.getBody();
    assertThat(AzericardMerchant.element("action", xml)).isEqualTo("0");
    assertThat(AzericardMerchant.element("rc", xml)).isEqualTo("00");
    assertThat(AzericardMerchant.element("desc", xml)).isEqualTo("Approved");
    assertThat(AzericardMerchant.element("order", xml)).isEqualTo(ORDER);
    String intRef = AzericardMerchant.element("intref", xml);
    String rrn = AzericardMerchant.element("rrn", xml);
    String approval = AzericardMerchant.element("approval", xml);
    assertThat(intRef).hasSize(16);
    assertThat(rrn).isNotBlank();
    assertThat(approval).isNotBlank();
    assertThat(
            keys.verifiesAsTheGateway(
                AzericardMerchant.element("p_sign", xml),
                AzericardMerchant.element("amount", xml),
                TERMINAL,
                approval,
                rrn,
                intRef))
        .isTrue();

    assertThat(merchant.received()).isEmpty();

    Map<String, Object> job =
        http.json(
            "post the authorization result to BACKREF, which the authorization itself does not",
            Kind.WRITE,
            "POST",
            "/__simulator/azericard/webhooks",
            """
            {"testRunId":"%s","providerObjectId":"%s"}
            """
                .formatted(RUN, intRef));
    assertThat(job).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 1);

    assertThat(merchant.received()).hasSize(1);
    LocalMerchantServer.ReceivedCallback callback = merchant.received().get(0);
    assertThat(callback.contentType()).isEqualTo("application/x-www-form-urlencoded");
    Map<String, String> fields = callback.fields();
    assertThat(fields)
        .containsEntry("TERMINAL", TERMINAL)
        .containsEntry("ORDER", ORDER)
        .containsEntry("AMOUNT", AMOUNT)
        .containsEntry("CURRENCY", CURRENCY)
        .containsEntry("ACTION", "0")
        .containsEntry("RC", "00")
        .containsEntry("INT_REF", intRef)
        .containsEntry("RRN", rrn)
        .containsEntry("APPROVAL", approval);
    assertThat(
            keys.verifiesAsTheGateway(
                fields.get("P_SIGN"),
                fields.get("AMOUNT"),
                fields.get("TERMINAL"),
                fields.get("APPROVAL"),
                fields.get("RRN"),
                fields.get("INT_REF")))
        .isTrue();

    MultiValueMap<String, String> completion = new LinkedMultiValueMap<>();
    completion.add("TERMINAL", TERMINAL);
    completion.add("TRTYPE", "21");
    completion.add("ORDER", ORDER);
    completion.add("AMOUNT", AMOUNT);
    completion.add("CURRENCY", CURRENCY);
    completion.add("RRN", rrn);
    completion.add("INT_REF", intRef);
    completion.add("TIMESTAMP", TIMESTAMP);
    completion.add("NONCE", NONCE);
    completion.add(
        "P_SIGN",
        keys.sign(AMOUNT, CURRENCY, ORDER, rrn, intRef, TERMINAL, "21", TIMESTAMP, NONCE));

    ResponseEntity<String> completed =
        http.form(
            "complete the sale over the gateway's own route",
            Kind.WRITE,
            "/cgi-bin/cgi_link",
            completion);
    assertThat(AzericardMerchant.element("action", completed.getBody())).isEqualTo("0");
    assertThat(AzericardMerchant.element("rc", completed.getBody())).isEqualTo("00");
    assertThat(AzericardMerchant.element("trtype", completed.getBody())).isEqualTo("21");

    Map<String, Object> state = http.get("read the object state", state(intRef));
    assertThat(state)
        .containsEntry("providerObjectId", intRef)
        .containsEntry("nativeStatus", "0/00")
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", AMOUNT)
        .containsEntry("currency", CURRENCY)
        .containsEntry("origin", "PROVIDER_API");
    List<Map<String, Object>> operations = operationsOf(state);
    assertThat(operations).hasSize(2);
    assertThat(operations.get(0))
        .containsEntry("kind", "AUTHORIZATION")
        .containsEntry("amount", AMOUNT)
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
    assertThat(operations.get(0).get("closesAuthorization")).isNull();
    assertThat(operations.get(1))
        .containsEntry("kind", "CAPTURE")
        .containsEntry("amount", AMOUNT)
        .containsEntry("closesAuthorization", true)
        .containsEntry("normalizedStatus", "SUCCEEDED");
    @SuppressWarnings("unchecked")
    Map<String, Object> balances = (Map<String, Object>) state.get("derivedBalances");
    assertThat(balances)
        .containsEntry("capturedAmount", AMOUNT)
        .containsEntry("capturableRemainder", "0")
        .containsEntry("refundableRemainder", AMOUNT);
    @SuppressWarnings("unchecked")
    Map<String, Object> related = (Map<String, Object>) state.get("relatedObjects");
    assertThat(related)
        .containsEntry("terminal", TERMINAL)
        .containsEntry("order", ORDER)
        .containsEntry("rrn", rrn)
        .containsEntry("approval", approval);

    MultiValueMap<String, String> inquiry = new LinkedMultiValueMap<>();
    inquiry.add("TERMINAL", TERMINAL);
    inquiry.add("TRTYPE", "90");
    inquiry.add("TRAN_TRTYPE", "0");
    inquiry.add("ORDER", ORDER);
    inquiry.add("TIMESTAMP", TIMESTAMP);
    inquiry.add("NONCE", NONCE);
    inquiry.add("P_SIGN", keys.sign(ORDER, TERMINAL, "90", TIMESTAMP, NONCE));

    ResponseEntity<String> status =
        http.form(
            "reconcile through the gateway's own status inquiry",
            Kind.READ,
            "/cgi-bin/cgi_link",
            inquiry);
    assertThat(status.getHeaders().getContentType().toString()).startsWith("application/json");
    assertThat(status.getBody())
        .contains("\"actionCode\":\"0\"")
        .contains("\"responseCode\":\"00\"")
        .contains("\"order\":\"" + ORDER + "\"")
        .contains("\"intRef\":\"" + intRef + "\"")
        .contains("\"amount\":\"" + AMOUNT + "\"");

    List<Map<String, Object>> deliveries = http.getList("read the callback ledger", webhooks());
    assertThat(deliveries).hasSize(1);
    assertThat(deliveries.get(0))
        .containsEntry("status", "DELIVERED")
        .containsEntry("attemptCap", 5);
    assertThat(deliveries.get(0).get("eventType")).isNull();
    assertThat(attemptsOf(deliveries.get(0)).get(0)).containsEntry("verdict", "ACKNOWLEDGED");

    List<Map<String, Object>> trail = http.getList("read the audit trail", audit());
    assertThat(trail).isNotEmpty();
    assertThat(trail)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "PROVIDER_API")
                    .containsEntry("direction", "OUTBOUND_RESPONSE")
                    .containsEntry("scenarioId", scenarioId)
                    .containsEntry("providerObjectId", intRef));
    assertThat(trail)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "SCHEDULED_JOB")
                    .containsEntry("direction", "OUTBOUND_WEBHOOK_ATTEMPT"));

    assertThat(http.counted(Kind.WRITE)).isEqualTo(5);
    assertThat(http.counted(Kind.READ)).isEqualTo(5);
  }

  private static String objects() {
    return "/__simulator/azericard/objects?testRunId=" + RUN;
  }

  private static String state(String providerObjectId) {
    return "/__simulator/azericard/state/" + providerObjectId + "?testRunId=" + RUN;
  }

  private static String webhooks() {
    return "/__simulator/azericard/webhooks?testRunId=" + RUN;
  }

  private static String audit() {
    return "/__simulator/azericard/audit?testRunId=" + RUN;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> operationsOf(Map<String, Object> state) {
    return (List<Map<String, Object>>) state.get("operations");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> attemptsOf(Map<String, Object> delivery) {
    return (List<Map<String, Object>>) delivery.get("attempts");
  }
}
