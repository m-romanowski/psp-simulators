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

class GrowTesterJourneyIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "journey-grow";
  private static final String ACCOUNT = "merchant-1";
  private static final String USER_ID = "journey-grow-user";
  private static final String PAGE_CODE = "journey-grow-page";
  private static final String HELD = "120.50";
  private static final String CHOSEN_CARD = "sandbox-4580";
  private static final String CONFIG = "/__simulator/grow/config";
  private static final String SCENARIOS = "/__simulator/grow/scenarios";

  @LocalServerPort private int port;

  private JourneyClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void standUpTheMerchant() {
    http = new JourneyClient(port);
    merchant = new LocalMerchantServer();
  }

  @AfterEach
  void takeTheMerchantDown() {
    http.discardTheTestRun("grow", RUN);
    merchant.close();
  }

  @Test
  void a_payer_pays_the_grow_hold_the_quickstart_creates_and_the_tester_reads_it_back_settled() {
    assertThat(http.getList("the run holds nothing yet", objects())).isEmpty();

    Map<String, Object> configured =
        http.json(
            "configure the account and the callback target",
            Kind.WRITE,
            "PUT",
            CONFIG,
            """
            {"testRunId":"%s",
             "clock":{"fixedNow":"2026-09-15T10:00:00Z"},
             "accounts":[{"accountId":"%s","configuration":{
               "userId":"%s","pageCode":"%s","callbackAcknowledgement":"APPROVE_TRANSACTION"}}],
             "webhooks":[{"accountId":"%s","url":"%s"}]}
            """
                .formatted(RUN, ACCOUNT, USER_ID, PAGE_CODE, ACCOUNT, merchant.url()));
    assertThat(configured)
        .containsEntry("configuredAccounts", List.of(ACCOUNT))
        .containsEntry("configuredWebhookTargets", 1)
        .containsEntry("updatedAt", "2026-09-15T10:00:00Z");

    Map<String, Object> rule =
        http.json(
            "pin the outcome with a scenario rule",
            Kind.WRITE,
            "POST",
            SCENARIOS,
            """
            {"testRunId":"%s","priority":100,
             "match":{"amount":"%s"},
             "action":{"type":"FIXED_SUCCESS"}}
            """
                .formatted(RUN, HELD));
    String scenarioId = (String) rule.get("scenarioId");
    assertThat(scenarioId).startsWith("scn_");

    Map<String, Object> created =
        http.multipart(
            "create the hosted payment process the payer will be sent to",
            Kind.WRITE,
            "/api/light/server/1.0/createPaymentProcess",
            "pageCode",
            PAGE_CODE,
            "userId",
            USER_ID,
            "chargeType",
            "2",
            "sum",
            HELD,
            "description",
            "Journey hold",
            "saveCardToken",
            "1",
            "successUrl",
            "https://merchant.example/thanks",
            "cancelUrl",
            "https://merchant.example/cancel",
            "notifyUrl",
            merchant.url(),
            "pageField[fullName]",
            "Israel Israeli",
            "pageField[phone]",
            "0505503144",
            "cField1",
            "journey-session",
            "cField2",
            "journey-intent");
    assertThat(http.answeredAs()).startsWith("text/html");
    assertThat(created).containsEntry("status", 1);
    @SuppressWarnings("unchecked")
    Map<String, Object> process = (Map<String, Object>) created.get("data");
    String processId = (String) process.get("processId");
    String processToken = (String) process.get("processToken");
    assertThat(processId).isNotBlank();
    assertThat(processToken).isNotBlank();
    String hostedUrl = (String) process.get("url");
    assertThat(hostedUrl).contains("/far?l=");
    String link = hostedUrl.substring(hostedUrl.indexOf("?l=") + "?l=".length());

    ResponseEntity<String> offered =
        http.page("open the page the payer was sent to", "/far?l=" + link);
    assertThat(offered.getStatusCode().value()).isEqualTo(200);
    assertThat(String.valueOf(offered.getHeaders().getContentType())).startsWith("text/html");
    assertThat(offered.getBody())
        .contains("Journey hold")
        .contains(HELD)
        .contains("Israel Israeli")
        .contains(CHOSEN_CARD)
        .contains("sandbox-0000")
        .contains("sandbox-1121")
        .contains("PAID")
        .contains("DECLINED")
        .contains("ABANDONED");

    ResponseEntity<String> paid =
        http.form(
            "pay it the way the payer's browser does",
            Kind.WRITE,
            "/far",
            submitted(link, "PAID", CHOSEN_CARD));
    assertThat(paid.getStatusCode().value()).isEqualTo(303);
    assertThat(paid.getHeaders().getFirst("Location"))
        .isEqualTo(
            "https://merchant.example/thanks?cField1=journey-session&cField2=journey-intent");

    assertThat(merchant.received()).hasSize(1);
    LocalMerchantServer.ReceivedCallback callback = merchant.received().get(0);
    assertThat(callback.contentType()).isEqualTo("application/x-www-form-urlencoded");
    Map<String, String> fields = callback.fields();
    assertThat(fields)
        .containsEntry("status", "1")
        .containsEntry("err", "")
        .containsEntry("data[processId]", processId)
        .containsEntry("data[processToken]", processToken)
        .containsEntry("data[statusCode]", "11")
        .containsEntry("data[status]", "עסקה מושהית")
        .containsEntry("data[sum]", HELD)
        .containsEntry("data[paymentDate]", "15/09/26")
        .containsEntry("data[customFields][cField1]", "journey-session")
        .containsEntry("data[customFields][cField2]", "journey-intent");
    String transactionId = fields.get("data[transactionId]");
    String transactionToken = fields.get("data[transactionToken]");
    assertThat(transactionId).isNotBlank();
    assertThat(transactionToken).isNotBlank();

    List<Map<String, Object>> owed =
        http.getList("read the callback ledger before the merchant acknowledges", webhooks());
    assertThat(owed).hasSize(1);
    assertThat(owed.get(0))
        .containsEntry("status", "PENDING")
        .containsEntry("eventType", "payment_process.completed")
        .containsEntry("attemptCap", 6);
    assertThat(attemptsOf(owed.get(0)).get(0)).containsEntry("verdict", "AWAITING_SEPARATE_CALL");

    Map<String, Object> approved =
        http.multipart(
            "acknowledge the callback the way this account's contract asks a merchant to",
            Kind.WRITE,
            "/api/light/server/1.0/approveTransaction",
            "pageCode",
            PAGE_CODE,
            "transactionId",
            transactionId,
            "transactionToken",
            transactionToken);
    assertThat(approved)
        .containsEntry("status", 1)
        .containsEntry("err", "")
        .containsEntry("data", "");

    Map<String, Object> settled =
        http.multipart(
            "settle the hold in full",
            Kind.WRITE,
            "/api/light/server/1.0/settleSuspendedTransaction",
            "userId",
            USER_ID,
            "transactionId",
            transactionId,
            "transactionToken",
            transactionToken,
            "sum",
            HELD);
    assertThat(settled).containsEntry("status", 1);
    @SuppressWarnings("unchecked")
    Map<String, Object> settlement = (Map<String, Object>) settled.get("data");
    assertThat(settlement)
        .containsEntry("statusCode", "2")
        .containsEntry("status", "שולם")
        .containsEntry("sum", HELD)
        .containsEntry("transactionId", transactionId);

    Map<String, Object> state = http.get("read the object state", state(transactionId));
    assertThat(state)
        .containsEntry("providerObjectId", transactionId)
        .containsEntry("nativeStatus", "2")
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", HELD)
        .containsEntry("origin", "PROVIDER_API");
    List<Map<String, Object>> operations = operationsOf(state);
    assertThat(operations).hasSize(2);
    assertThat(operations.get(0))
        .containsEntry("kind", "AUTHORIZATION")
        .containsEntry("amount", HELD)
        .containsEntry("closesAuthorization", false)
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
    assertThat(operations.get(1))
        .containsEntry("kind", "CAPTURE")
        .containsEntry("amount", HELD)
        .containsEntry("closesAuthorization", true)
        .containsEntry("normalizedStatus", "SUCCEEDED");
    @SuppressWarnings("unchecked")
    Map<String, Object> balances = (Map<String, Object>) state.get("derivedBalances");
    assertThat(balances)
        .containsEntry("capturedAmount", HELD)
        .containsEntry("refundedAmount", "0")
        .containsEntry("capturableRemainder", "0")
        .containsEntry("refundableRemainder", HELD);
    @SuppressWarnings("unchecked")
    Map<String, Object> related = (Map<String, Object>) state.get("relatedObjects");
    assertThat(related).containsEntry("processId", processId).containsEntry("merchantId", ACCOUNT);

    Map<String, Object> lookedUp =
        http.multipart(
            "reconcile through Grow's own transaction lookup",
            Kind.READ,
            "/api/light/server/1.0/getTransactionInfo",
            "pageCode",
            PAGE_CODE,
            "transactionId",
            transactionId,
            "transactionToken",
            transactionToken);
    assertThat(lookedUp).containsEntry("status", 1);
    @SuppressWarnings("unchecked")
    Map<String, Object> record = (Map<String, Object>) lookedUp.get("data");
    assertThat(record)
        .containsEntry("statusCode", 2)
        .containsEntry("processId", processId)
        .containsEntry("sum", HELD);

    Map<String, Object> reconciled =
        http.multipart(
            "reconcile the process the payer completed through Grow's own process lookup",
            Kind.READ,
            "/api/light/server/1.0/getPaymentProcessInfo",
            "pageCode",
            PAGE_CODE,
            "processId",
            processId,
            "processToken",
            processToken);
    assertThat(reconciled).containsEntry("status", 1);
    @SuppressWarnings("unchecked")
    Map<String, Object> processRecord = (Map<String, Object>) reconciled.get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) processRecord.get("transactions");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("statusCode", "2")
        .containsEntry("transactionId", transactionId);

    List<Map<String, Object>> acknowledged =
        http.getList("read the callback ledger again", webhooks());
    assertThat(acknowledged.get(0)).containsEntry("status", "DELIVERED");

    List<Map<String, Object>> trail = http.getList("read the audit trail", audit());
    assertThat(trail).isNotEmpty();
    assertThat(trail)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "CONTROL_API")
                    .containsEntry("path", CONFIG));
    assertThat(trail)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "PROVIDER_API")
                    .containsEntry("direction", "OUTBOUND_RESPONSE")
                    .containsEntry("scenarioId", scenarioId)
                    .containsEntry("providerObjectId", processId));
    assertThat(trail)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "PROVIDER_API")
                    .containsEntry("path", "/far")
                    .containsEntry("direction", "OUTBOUND_RESPONSE")
                    .containsEntry("providerObjectId", processId));
    assertThat(trail)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "SCHEDULED_JOB")
                    .containsEntry("direction", "OUTBOUND_WEBHOOK_ATTEMPT"));
    assertThat(trail)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("path", "/api/light/server/1.0/settleSuspendedTransaction")
                    .containsEntry("direction", "OUTBOUND_RESPONSE")
                    .containsEntry("outcome", "SUCCESS"));

    List<String> controlApiWrites = theControlApiWritesOf(http.steps());
    assertThat(controlApiWrites).containsExactly(CONFIG, SCENARIOS);
    assertThat(http.counted(Kind.WRITE)).isEqualTo(6);
    assertThat(http.counted(Kind.READ)).isEqualTo(8);
  }

  private static MultiValueMap<String, String> submitted(String link, String outcome, String card) {
    MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
    fields.add("l", link);
    fields.add("outcome", outcome);
    fields.add("card", card);
    return fields;
  }

  private static List<String> theControlApiWritesOf(List<JourneyClient.Step> steps) {
    return steps.stream()
        .filter(step -> step.kind() == Kind.WRITE)
        .map(JourneyClient.Step::path)
        .filter(path -> path.startsWith("/__simulator/"))
        .toList();
  }

  private static String objects() {
    return "/__simulator/grow/objects?testRunId=" + RUN;
  }

  private static String state(String providerObjectId) {
    return "/__simulator/grow/state/" + providerObjectId + "?testRunId=" + RUN;
  }

  private static String webhooks() {
    return "/__simulator/grow/webhooks?testRunId=" + RUN;
  }

  private static String audit() {
    return "/__simulator/grow/audit?testRunId=" + RUN;
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
