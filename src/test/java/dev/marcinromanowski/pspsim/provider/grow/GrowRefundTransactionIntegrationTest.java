package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Duration;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowRefundTransactionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-refund-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-refund-user";
  private static final String PAGE_CODE = "grow-refund-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String HELD = "120.50";

  private static final Map<String, String> TWO_REFUNDS =
      Map.of(GrowRefundAllowance.SETTING, GrowRefundAllowance.TWO_REFUNDS.name());

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
    configure(FIXED_NOW);
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void releases_a_hold_over_http_and_leaves_the_transaction_refunded() {
    Held hold = aHold();
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "READY_FOR_CAPTURE");

    settle(hold, HELD);
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "SUCCEEDED");

    ResponseEntity<byte[]> released = refund(hold, HELD);

    assertThat(released.getStatusCode().value()).isEqualTo(200);
    Map<String, Object> refunded = state(hold.transactionId());
    assertThat(refunded)
        .containsEntry("normalizedStatus", "REFUNDED")
        .containsEntry("nativeStatus", "2");
    assertThat(refunded.get("derivedBalances"))
        .isEqualTo(
            Map.of(
                "capturedAmount",
                HELD,
                "refundedAmount",
                HELD,
                "capturableRemainder",
                "0",
                "refundableRemainder",
                "0.00"));
  }

  @Test
  void renders_the_refund_record_with_the_mixed_json_types_the_published_example_shows() {
    Held hold = aHold();
    settle(hold, HELD);

    ResponseEntity<byte[]> response = refund(hold, HELD);

    assertThat(bodyOf(response))
        .contains("\"status\":1")
        .contains("\"refundedTransactionId\":" + hold.transactionId() + ",")
        .contains("\"statusCode\":3")
        .contains("\"status\":\"תשלום שזוכה\"")
        .contains("\"sum\":\"120.50\"")
        .contains("\"paymentDate\":\"07/09/26\"")
        .doesNotContain("transactionToken")
        .doesNotContain("processId");
  }

  @Test
  void the_refund_row_survives_the_database_with_neither_half_of_the_rest_of_an_identity() {
    Held hold = aHold();
    settle(hold, HELD);
    String refundId = refundIdOf(refund(hold, HELD));

    GrowTransaction row = transactions.findByTransactionId(RUN, refundId).orElseThrow();
    assertThat(row.kind()).isEqualTo(GrowTransactionKind.REFUND);
    assertThat(row.transactionToken()).isNull();
    assertThat(row.processId()).isNull();
    assertThat(row.refundedTransactionId()).isEqualTo(hold.transactionId());
    assertThat(row.amount().value()).isEqualTo(HELD);
    assertThat(row.ledger().operations()).isEmpty();
    assertThat(transactions.find(RUN, MERCHANT, refundId, hold.transactionToken())).isEmpty();
  }

  @Test
  void the_refund_operation_survives_the_round_trip_on_the_transaction_it_refunded() {
    Held hold = aHold();
    settle(hold, HELD);
    String refundId = refundIdOf(refund(hold, HELD));

    GrowTransaction stored =
        transactions.findByTransactionId(RUN, hold.transactionId()).orElseThrow();
    assertThat(stored.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION, OperationKind.CAPTURE, OperationKind.REFUND);
    MonetaryOperation refund = stored.ledger().operations().get(2);
    assertThat(refund.amount().value()).isEqualTo(HELD);
    assertThat(refund.closesAuthorization()).isNull();
    assertThat(refund.createdObjectId()).isEqualTo(refundId);
  }

  @Test
  void the_state_route_reports_the_refund_row_and_the_link_back_to_what_it_refunded() {
    Held hold = aHold();
    settle(hold, HELD);
    String refundId = refundIdOf(refund(hold, HELD));

    Map<String, Object> row = state(refundId);

    assertThat(row)
        .containsEntry("providerObjectId", refundId)
        .containsEntry("nativeStatus", "3")
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", HELD);
    assertThat(row.get("operations")).isEqualTo(List.of());
    assertThat(row.get("relatedObjects"))
        .isEqualTo(Map.of("merchantId", MERCHANT, "refundedTransactionId", hold.transactionId()));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> operations =
        (List<Map<String, Object>>) state(hold.transactionId()).get("operations");
    assertThat(operations.get(2))
        .containsEntry("kind", "REFUND")
        .containsEntry("createdObjectId", refundId)
        .containsEntry("closesAuthorization", null)
        .containsEntry("nativeStatus", "3");
  }

  @Test
  void a_partial_refund_is_refused_on_the_day_of_the_charge_and_accepted_the_day_after() {
    Held hold = aHold();
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"id\":130");

    configure(FIXED_NOW.plus(Duration.ofDays(1)));

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"statusCode\":3");
    Map<String, Object> partly = state(hold.transactionId());
    assertThat(partly).containsEntry("normalizedStatus", "PARTIALLY_REFUNDED");
    assertThat(partly.get("derivedBalances"))
        .isEqualTo(
            Map.of(
                "capturedAmount", HELD,
                "refundedAmount", "40.00",
                "capturableRemainder", "0",
                "refundableRemainder", "80.50"));
  }

  @Test
  void a_refund_this_transaction_refused_is_on_the_timeline_of_the_transaction_it_refused() {
    Held hold = aHold();
    settle(hold, HELD);

    assertThat(bodyOf(refund(hold, "40.00"))).contains("\"id\":130");

    assertThat(theTimelineOf(hold.transactionId())).contains(REFUND).contains(SETTLE);
  }

  @Test
  void a_refund_naming_a_transaction_this_run_does_not_hold_is_on_no_transaction_timeline() {
    Held hold = aHold();
    settle(hold, HELD);

    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", "999999");
    form.add("transactionToken", hold.transactionToken());
    form.add("refundSum", "40.00");
    http.post()
        .uri(REFUND)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class);

    String timeline = theTimelineOf(hold.transactionId());
    assertThat(timeline).contains(SETTLE);
    assertThat(timeline).doesNotContain(REFUND);
  }

  private String theTimelineOf(String providerObjectId) {
    return new String(
        Objects.requireNonNull(
            http.get()
                .uri(
                    "/ui/timeline?provider=grow&testRunId="
                        + RUN.value()
                        + "&key="
                        + providerObjectId)
                .retrieve()
                .toEntity(byte[].class)
                .getBody()),
        UTF_8);
  }

  @Test
  void the_default_allowance_refuses_a_second_refund_before_the_database_is_asked_for_one() {
    Held hold = aHold();
    settle(hold, HELD);
    refund(hold, HELD);

    assertThat(bodyOf(refund(hold, HELD))).contains("\"id\":210");
    assertThat(
            transactions
                .findByTransactionId(RUN, hold.transactionId())
                .orElseThrow()
                .ledger()
                .operations())
        .hasSize(3);
  }

  @Test
  void the_database_refuses_the_second_of_two_refunds_the_allowance_admits_that_share_a_sequence() {
    configure(FIXED_NOW, TWO_REFUNDS);
    Held hold = aHold();
    settle(hold, HELD);
    configure(FIXED_NOW.plus(Duration.ofDays(1)), TWO_REFUNDS);
    String alreadyRefunded = refundIdOf(refund(hold, "40.00"));
    GrowTransaction charged =
        transactions.findByTransactionId(RUN, hold.transactionId()).orElseThrow();
    assertThat(charged.acceptsAnotherRefund(GrowRefundAllowance.TWO_REFUNDS)).isTrue();
    MonetaryOperation first = aRefundOperation(charged, "111111", "20.00");
    MonetaryOperation second = aRefundOperation(charged, "222222", "30.00");

    GrowTransactions.Refunded applied =
        transactions.refund(charged.with(first, FIXED_NOW), first, aRefundRow(charged, first));
    GrowTransactions.Refunded refused =
        transactions.refund(charged.with(second, FIXED_NOW), second, aRefundRow(charged, second));

    assertThat(applied).isEqualTo(GrowTransactions.Refunded.APPLIED);
    assertThat(refused).isEqualTo(GrowTransactions.Refunded.CLAIM_TAKEN);
    assertThat(
            transactions
                .findByTransactionId(RUN, hold.transactionId())
                .orElseThrow()
                .ledger()
                .operations())
        .extracting(MonetaryOperation::createdObjectId)
        .containsExactly(null, null, alreadyRefunded, "111111");
    assertThat(transactions.findByTransactionId(RUN, "222222")).isEmpty();
  }

  @Test
  void the_database_refuses_a_refund_whose_drawn_id_is_taken_and_writes_no_operation_for_it() {
    Held hold = aHold();
    settle(hold, HELD);
    Held anotherPayment = aHold();
    GrowTransaction charged =
        transactions.findByTransactionId(RUN, hold.transactionId()).orElseThrow();
    MonetaryOperation operation = aRefundOperation(charged, anotherPayment.transactionId(), HELD);

    GrowTransactions.Refunded refused =
        transactions.refund(
            charged.with(operation, FIXED_NOW), operation, aRefundRow(charged, operation));

    assertThat(refused).isEqualTo(GrowTransactions.Refunded.ID_TAKEN);
    assertThat(
            transactions
                .findByTransactionId(RUN, hold.transactionId())
                .orElseThrow()
                .ledger()
                .operations())
        .hasSize(2);
  }

  @Test
  void a_refund_that_drew_the_id_of_the_payment_it_refunds_is_refused_with_nothing_written() {
    Held hold = aHold();
    settle(hold, HELD);
    GrowTransaction charged =
        transactions.findByTransactionId(RUN, hold.transactionId()).orElseThrow();
    MonetaryOperation operation = aRefundOperation(charged, hold.transactionId(), HELD);

    GrowTransactions.Refunded refused =
        transactions.refund(
            charged.with(operation, FIXED_NOW), operation, aRefundRow(charged, operation));

    assertThat(refused).isEqualTo(GrowTransactions.Refunded.ID_TAKEN);
    assertThat(
            transactions
                .findByTransactionId(RUN, hold.transactionId())
                .orElseThrow()
                .ledger()
                .operations())
        .hasSize(2);
  }

  @Test
  void the_database_refuses_a_refund_of_a_transaction_a_reset_took_before_the_write() {
    Held hold = aHold();
    settle(hold, HELD);
    GrowTransaction charged =
        transactions.findByTransactionId(RUN, hold.transactionId()).orElseThrow();
    MonetaryOperation operation = aRefundOperation(charged, "333333", HELD);

    reset();

    assertThat(
            transactions.refund(
                charged.with(operation, FIXED_NOW), operation, aRefundRow(charged, operation)))
        .isEqualTo(GrowTransactions.Refunded.OBJECT_GONE);
    assertThat(transactions.findByTransactionId(RUN, "333333")).isEmpty();
  }

  @Test
  void a_reset_takes_the_refund_row_with_the_transaction_it_refunded() {
    Held hold = aHold();
    settle(hold, HELD);
    String refundId = refundIdOf(refund(hold, HELD));

    reset();

    assertThat(stateResponse(hold.transactionId()).getStatusCode().value()).isEqualTo(404);
    assertThat(stateResponse(refundId).getStatusCode().value()).isEqualTo(404);
    assertThat(transactions.findByTransactionId(RUN, refundId)).isEmpty();
  }

  private record Held(String processId, String transactionId, String transactionToken) {}

  private Held aHold() {
    String processId = createTheHold();
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
    GrowProcessOutcome outcome = processes.findByProcessId(RUN, processId).orElseThrow().outcome();
    assertThat(outcome).isNotNull();
    return new Held(
        processId,
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
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    return (String) data.get("processId");
  }

  private ResponseEntity<byte[]> settle(Held hold, String sum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    form.add("sum", sum);
    return http.post()
        .uri(SETTLE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private ResponseEntity<byte[]> refund(Held hold, String refundSum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", hold.transactionId());
    form.add("transactionToken", hold.transactionToken());
    form.add("refundSum", refundSum);
    return http.post()
        .uri(REFUND)
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
    return GrowWireText.asJson(
        http.get()
            .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
            .retrieve()
            .toEntity(byte[].class)
            .getBody());
  }

  private ResponseEntity<Void> stateResponse(String transactionId) {
    return http.get()
        .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toBodilessEntity();
  }

  private void configure(Instant fixedNow) {
    configure(fixedNow, Map.of());
  }

  private void configure(Instant fixedNow, Map<String, String> settings) {
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
                Map.of("fixedNow", fixedNow.toString()),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
        .retrieve()
        .toBodilessEntity();
  }

  private static MonetaryOperation aRefundOperation(
      GrowTransaction charged, String refundId, String sum) {
    return new MonetaryOperation(
        charged.ledger().nextSequence(),
        OperationKind.REFUND,
        Amount.of(sum),
        null,
        refundId,
        "3",
        NormalizedStatus.SUCCEEDED,
        FIXED_NOW);
  }

  private static GrowTransaction aRefundRow(GrowTransaction charged, MonetaryOperation refund) {
    return new GrowTransaction(
        RUN,
        GrowTransactionKind.REFUND,
        refund.createdObjectId(),
        null,
        MERCHANT,
        null,
        charged.transactionId(),
        "89474280",
        "07/09/26",
        charged.description(),
        charged.payerFullName(),
        charged.payerPhone(),
        charged.payerEmail(),
        charged.card(),
        refund.amount(),
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        FIXED_NOW,
        FIXED_NOW);
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
