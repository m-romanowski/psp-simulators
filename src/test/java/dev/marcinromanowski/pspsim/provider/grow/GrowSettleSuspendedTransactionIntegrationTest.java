package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Duration;
import java.time.Instant;
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

class GrowSettleSuspendedTransactionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-settle-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-settle-user";
  private static final String PAGE_CODE = "grow-settle-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String HELD = "120.50";

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
  void settles_a_hold_over_http_and_renders_every_scalar_as_a_json_string() {
    Held hold = aHold();

    ResponseEntity<byte[]> response = settle(hold, HELD);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyOf(response))
        .contains("\"status\":1")
        .contains("\"statusCode\":\"2\"")
        .contains("\"status\":\"שולם\"")
        .contains("\"sum\":\"120.50\"")
        .contains("\"transactionId\":\"" + hold.transactionId() + "\"")
        .contains("\"paymentDate\":\"07/09/26\"");
  }

  @Test
  void the_settlement_survives_the_round_trip_through_the_database() {
    Held hold = aHold();

    settle(hold, "40.00");

    GrowTransaction stored =
        transactions.findByTransactionId(RUN, hold.transactionId()).orElseThrow();
    assertThat(stored.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION, OperationKind.CAPTURE);
    assertThat(stored.ledger().operations())
        .extracting(operation -> operation.amount().value())
        .containsExactly(HELD, "40.00");
    assertThat(stored.ledger().operations().get(0).closesAuthorization()).isFalse();
    assertThat(stored.ledger().operations().get(1).closesAuthorization()).isTrue();
    assertThat(stored.amount().value()).isEqualTo(HELD);
    assertThat(stored.processId()).isEqualTo(hold.processId());
  }

  @Test
  void refuses_a_second_settlement_of_a_hold_the_database_already_holds_a_capture_for() {
    Held hold = aHold();
    settle(hold, "40.00");

    assertThat(bodyOf(settle(hold, "40.00"))).contains("\"id\":808");
    assertThat(
            transactions
                .findByTransactionId(RUN, hold.transactionId())
                .orElseThrow()
                .ledger()
                .operations())
        .hasSize(2);
  }

  @Test
  void the_state_route_reports_the_ledger_and_the_balances_derived_from_it() {
    Held hold = aHold();
    settle(hold, "40.00");

    Map<String, Object> state = state(hold.transactionId());

    assertThat(state)
        .containsEntry("providerObjectId", hold.transactionId())
        .containsEntry("nativeStatus", "2")
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", HELD)
        .containsEntry("currency", null);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> operations = (List<Map<String, Object>>) state.get("operations");
    assertThat(operations).hasSize(2);
    assertThat(operations.get(0))
        .containsEntry("sequence", 1)
        .containsEntry("kind", "AUTHORIZATION")
        .containsEntry("amount", HELD)
        .containsEntry("closesAuthorization", false)
        .containsEntry("createdObjectId", null)
        .containsEntry("nativeStatus", "11")
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
    assertThat(operations.get(1))
        .containsEntry("sequence", 2)
        .containsEntry("kind", "CAPTURE")
        .containsEntry("amount", "40.00")
        .containsEntry("closesAuthorization", true)
        .containsEntry("createdObjectId", null)
        .containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(state.get("derivedBalances"))
        .isEqualTo(
            Map.of(
                "capturedAmount", "40.00",
                "refundedAmount", "0",
                "capturableRemainder", "0",
                "refundableRemainder", "40.00"));
    assertThat(state.get("relatedObjects"))
        .isEqualTo(Map.of("processId", hold.processId(), "merchantId", MERCHANT));
    assertThat(bodyOfState(hold.transactionId())).doesNotContain(hold.transactionToken());
  }

  @Test
  void the_state_route_names_the_identifier_it_was_addressed_by_and_every_one_it_answers_to() {
    Held hold = aHold();

    Map<String, Object> state = state(hold.transactionId());

    assertThat(state)
        .containsEntry("providerObjectId", hold.transactionId())
        .containsEntry("addressedBy", hold.transactionId())
        .containsEntry("answersTo", List.of(hold.transactionId()));
  }

  @Test
  void a_hold_nobody_settled_is_cancelled_once_the_clock_passes_its_automatic_release() {
    Held hold = aHold();
    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "READY_FOR_CAPTURE");

    configure(FIXED_NOW.plus(Duration.ofDays(10)));

    assertThat(state(hold.transactionId())).containsEntry("normalizedStatus", "CANCELLED");
    assertThat(bodyOf(settle(hold, HELD))).contains("\"id\":803");
  }

  @Test
  void an_operation_whose_transaction_is_gone_is_reported_rather_than_raised() {
    Held hold = aHold();
    GrowTransaction read =
        transactions.findByTransactionId(RUN, hold.transactionId()).orElseThrow();
    MonetaryOperation capture =
        new MonetaryOperation(
            read.ledger().nextSequence(),
            OperationKind.CAPTURE,
            Amount.of(HELD),
            true,
            null,
            "2",
            NormalizedStatus.SUCCEEDED,
            FIXED_NOW);

    transactions.clear(RUN);

    assertThat(transactions.append(read.with(capture, FIXED_NOW), capture))
        .isEqualTo(LedgerClaim.OBJECT_GONE);
  }

  @Test
  void the_state_route_answers_404_for_an_id_it_holds_no_transaction_for() {
    assertThat(stateResponse("999999").getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void a_reset_takes_the_transaction_and_its_operations_with_it() {
    Held hold = aHold();
    settle(hold, "40.00");

    reset();

    assertThat(stateResponse(hold.transactionId()).getStatusCode().value()).isEqualTo(404);
    assertThat(transactions.findByTransactionId(RUN, hold.transactionId())).isEmpty();
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

  private Map<String, Object> state(String transactionId) {
    return GrowWireText.asJson(
        http.get()
            .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
            .retrieve()
            .toEntity(byte[].class)
            .getBody());
  }

  private String bodyOfState(String transactionId) {
    return bodyOf(
        http.get()
            .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
            .retrieve()
            .toEntity(byte[].class));
  }

  private ResponseEntity<Void> stateResponse(String transactionId) {
    return http.get()
        .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toBodilessEntity();
  }

  private void configure(Instant fixedNow) {
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
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", USER_ID, "pageCode", PAGE_CODE))),
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
