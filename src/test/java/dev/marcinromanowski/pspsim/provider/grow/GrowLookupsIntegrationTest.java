package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowLookupsIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-lookups-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-lookups-user";
  private static final String PAGE_CODE = "grow-lookups-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String TRANSACTION_INFO = "/api/light/server/1.0/getTransactionInfo";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String HELD = "120.50";

  private static final Map<String, String> FOLLOWING_THE_ERROR_TABLE =
      Map.of(GrowProcessLookupRejection.SETTING, GrowProcessLookupRejection.PUBLISHED_TABLE.name());

  @LocalServerPort private int port;

  @Autowired private GrowPaymentProcesses processes;
  @Autowired private GrowTransactions transactions;

  private RestClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure(Map.of());
  }

  @Test
  void the_bit_cancellation_grow_publishes_has_a_route_that_answers_over_http() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("processId", "734754");
    form.add("processToken", "2153287cd9af46457ae842337250b78d");

    ResponseEntity<byte[]> response = post(CANCEL_BIT, form);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("text/html; charset=UTF-8"));
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":730,"
                + "\"message\":\"פרמטר קוד תהליך או תוקן אינו תקין\"},\"data\":\"\"}");
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void reconciles_a_process_the_payer_abandoned_as_a_zeroed_row_rather_than_an_empty_list() {
    String processId = createTheHold();

    String body = bodyOf(processInfo(PAGE_CODE, processId, processTokenOf(processId)));

    assertThat(body)
        .contains("\"status\":1")
        .contains("\"transactions\":[{")
        .contains("\"status\":\"לא שולם\",\"statusCode\":\"0\"")
        .contains("\"transactionId\":\"0\",\"transactionToken\":\"\"")
        .contains("\"sum\":\"120.50\"")
        .contains("\"customField\":{\"cField1\":\"session-abc\",\"cField2\":\"intent-99\"}");
    assertThat(transactions.findByTransactionId(RUN, "0")).isEmpty();
  }

  @Test
  void reconciles_a_hold_over_http_through_the_settlement_and_the_refund_that_release_it() {
    Held hold = aHold();

    assertThat(bodyOf(transactionInfo(hold))).contains("\"statusCode\":11");
    assertThat(bodyOf(processInfo(hold))).contains("\"statusCode\":\"11\"");

    settle(hold, HELD);

    assertThat(bodyOf(transactionInfo(hold))).contains("\"statusCode\":2");
    assertThat(bodyOf(processInfo(hold))).contains("\"statusCode\":\"2\"");

    refund(hold, HELD);

    assertThat(bodyOf(transactionInfo(hold)))
        .contains("\"status\":\"שולם\",\"statusCode\":2")
        .doesNotContain("\"statusCode\":3");
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "REFUNDED");
  }

  @Test
  void renders_the_transaction_record_with_the_mixed_json_types_the_published_example_shows() {
    Held hold = aHold();

    String body = bodyOf(transactionInfo(hold));

    assertThat(body)
        .contains("\"cardTypeCode\":2")
        .contains("\"cardBrandCode\":3")
        .contains("\"firstPaymentSum\":0,\"periodicalPaymentSum\":0")
        .contains("\"paymentsNum\":0,\"allPaymentsNum\":1")
        .contains("\"transactionId\":" + hold.transactionId() + ",")
        .contains("\"processId\":\"" + hold.processId() + "\"")
        .contains("\"paymentDate\":\"07/09/26\"");
  }

  @Test
  void the_refund_exists_but_the_transaction_lookup_denies_it_and_its_process_omits_it() {
    Held hold = aHold();
    settle(hold, HELD);
    String refundId = refundIdOf(refund(hold, HELD));
    GrowTransaction row = transactions.findByTransactionId(RUN, refundId).orElseThrow();

    assertThat(state(refundId)).containsEntry("nativeStatus", "3");
    assertThat(bodyOf(transactionInfo(refundId, hold.transactionToken()))).contains("\"id\":716");
    assertThat(bodyOf(transactionInfo(refundId, String.valueOf(row.transactionToken()))))
        .contains("\"id\":716");
    assertThat(bodyOf(processInfo(hold))).doesNotContain(refundId);
  }

  @Test
  void answers_the_code_the_error_table_assigns_a_process_lookup_when_the_account_asks_for_it() {
    configure(FOLLOWING_THE_ERROR_TABLE);
    Held hold = aHold();

    assertThat(bodyOf(processInfo(PAGE_CODE, "999999", hold.processToken())))
        .contains("\"id\":730")
        .contains("פרמטר קוד תהליך או תוקן אינו תקין");
    assertThat(bodyOf(transactionInfo(hold.transactionId(), "not-the-token")))
        .contains("\"id\":716");
  }

  @Test
  void reads_the_credentials_out_of_a_url_encoded_body_as_well_as_a_multipart_one() {
    Held hold = aHold();

    ResponseEntity<byte[]> response =
        http.post()
            .uri(TRANSACTION_INFO)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                "pageCode="
                    + PAGE_CODE
                    + "&transactionId="
                    + hold.transactionId()
                    + "&transactionToken="
                    + hold.transactionToken())
            .retrieve()
            .toEntity(byte[].class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyOf(response)).contains("\"status\":1").contains("\"statusCode\":11");
  }

  @Test
  void answers_a_credential_in_the_query_string_as_a_request_that_carried_none() {
    Held hold = aHold();

    ResponseEntity<byte[]> response =
        http.post()
            .uri(
                TRANSACTION_INFO
                    + "?pageCode="
                    + PAGE_CODE
                    + "&transactionId="
                    + hold.transactionId()
                    + "&transactionToken="
                    + hold.transactionToken())
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(new LinkedMultiValueMap<String, Object>())
            .retrieve()
            .toEntity(byte[].class);

    assertThat(bodyOf(response)).contains("\"id\":54").contains("pageCode");
  }

  private record Held(
      String processId, String processToken, String transactionId, String transactionToken) {}

  private Held aHold() {
    String processId = createTheHold();
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
    GrowPaymentProcess process = processes.findByProcessId(RUN, processId).orElseThrow();
    GrowProcessOutcome outcome = process.outcome();
    assertThat(outcome).isNotNull();
    return new Held(
        processId,
        process.processToken(),
        String.valueOf(outcome.transactionId()),
        String.valueOf(outcome.transactionToken()));
  }

  private String createTheHold() {
    Map<String, Object> body =
        GrowWireText.asJson(
            http.post()
                .uri(CREATE)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(hold())
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) Objects.requireNonNull(body).get("data");
    return (String) data.get("processId");
  }

  private String processTokenOf(String processId) {
    return processes.findByProcessId(RUN, processId).orElseThrow().processToken();
  }

  private static final String CANCEL_BIT = "/api/light/server/1.0/cancelBitTransaction";

  private ResponseEntity<byte[]> transactionInfo(Held hold) {
    return transactionInfo(hold.transactionId(), hold.transactionToken());
  }

  private ResponseEntity<byte[]> transactionInfo(String transactionId, String transactionToken) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", transactionId);
    form.add("transactionToken", transactionToken);
    return post(TRANSACTION_INFO, form);
  }

  private ResponseEntity<byte[]> processInfo(Held hold) {
    return processInfo(PAGE_CODE, hold.processId(), hold.processToken());
  }

  private ResponseEntity<byte[]> processInfo(
      String pageCode, String processId, String processToken) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", pageCode);
    form.add("processId", processId);
    form.add("processToken", processToken);
    return post(PROCESS_INFO, form);
  }

  private ResponseEntity<byte[]> settle(Held hold, String sum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    form.add("sum", sum);
    return post(SETTLE, form);
  }

  private ResponseEntity<byte[]> refund(Held hold, String refundSum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    form.add("refundSum", refundSum);
    return post(REFUND, form);
  }

  private ResponseEntity<byte[]> post(String path, MultiValueMap<String, Object> form) {
    return http.post()
        .uri(path)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private static String refundIdOf(ResponseEntity<byte[]> response) {
    String body = bodyOf(response);
    int at = body.indexOf("\"transactionId\":\"");
    return body.substring(at + 17, body.indexOf('"', at + 17));
  }

  private Map<String, Object> state(String transactionId) {
    return http.get()
        .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private void configure(Map<String, String> settings) {
    Map<String, String> configuration = new LinkedHashMap<>(settings);
    configuration.put("userId", USER_ID);
    configuration.put("pageCode", PAGE_CODE);
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }

  private MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", HELD);
    form.add("saveCardToken", "1");
    form.add("description", "A hold");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", merchant.url());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    form.add("pageField[email]", "payer@merchant.example");
    form.add("cField1", "session-abc");
    form.add("cField2", "intent-99");
    return form;
  }

  private static String bodyOf(ResponseEntity<byte[]> response) {
    return GrowWireText.readable(new String(Objects.requireNonNull(response.getBody()), UTF_8));
  }
}
