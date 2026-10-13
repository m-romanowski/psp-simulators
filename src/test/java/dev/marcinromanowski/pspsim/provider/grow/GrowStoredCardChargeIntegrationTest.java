package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowStoredCardChargeIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-stored-card-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-stored-card-user";
  private static final String PAGE_CODE = "grow-stored-card-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String CHARGE = "/api/light/server/1.0/createTransactionWithToken";
  private static final String LOOK_UP = "/api/light/server/1.0/getTransactionInfo";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String CHARGED = "89.90";

  @LocalServerPort private int port;

  @Autowired private GrowPaymentProcesses processes;
  @Autowired private GrowTransactions transactions;
  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void registers_a_card_over_http_and_charges_it_without_a_hosted_process_of_its_own() {
    String cardToken = aRegisteredCard();

    ResponseEntity<byte[]> charged = charge(aChargeOf(cardToken, "1001"));

    assertThat(charged.getStatusCode().value()).isEqualTo(200);
    assertThat(bodyOf(charged))
        .contains("\"status\":1")
        .contains("\"statusCode\":2")
        .contains("\"status\":\"שולם\"")
        .contains("\"sum\":\"89.90\"");
    Map<String, Object> state = state(transactionIdOf(charged));
    assertThat(state)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("nativeStatus", "2");
    assertThat(state.get("relatedObjects")).isEqualTo(Map.of("merchantId", MERCHANT));
  }

  @Test
  void a_registered_card_is_reported_as_succeeded_and_holds_no_money() {
    String processId = createTheRegistration();
    completeTheProcess(processId);

    assertThat(processes.findByProcessId(RUN, processId).orElseThrow().normalizedStatus().name())
        .isEqualTo("SUCCEEDED");
    assertThat(theCardTokenOf(processId)).isNotBlank();
  }

  @Test
  void the_database_keeps_a_stored_card_charge_with_no_process_and_a_token_of_its_own() {
    String transactionId = transactionIdOf(charge(aChargeOf(aRegisteredCard(), "1001")));

    GrowTransaction stored = transactions.findByTransactionId(RUN, transactionId).orElseThrow();
    assertThat(stored.kind()).isEqualTo(GrowTransactionKind.TOKEN_PAYMENT);
    assertThat(stored.processId()).isNull();
    assertThat(stored.transactionToken())
        .as("a token of its own is eighteen hex digits, not the asmachta beside it")
        .matches("[0-9a-f]{18}");
    assertThat(stored.refundedTransactionId()).isNull();
  }

  @Test
  void the_database_refuses_a_repeat_of_a_unique_identifier_the_business_already_submitted() {
    String cardToken = aRegisteredCard();
    charge(aChargeOf(cardToken, "1001"));

    ResponseEntity<byte[]> repeated = charge(aChargeOf(cardToken, "1001"));

    assertThat(bodyOf(repeated)).contains("\"id\":712").contains("\"status\":0");
    assertThat(theStoredCardCharges()).hasSize(1);
  }

  @Test
  void the_database_refuses_a_group_identifier_a_charged_payment_already_carries() {
    String cardToken = aRegisteredCard();
    MultiValueMap<String, Object> first = aChargeOf(cardToken, "1001");
    first.add("transactionGroupIdentifier", "basket-7");
    charge(first);
    MultiValueMap<String, Object> second = aChargeOf(cardToken, "1002");
    second.add("transactionGroupIdentifier", "basket-7");

    assertThat(bodyOf(charge(second))).contains("\"id\":712");
    assertThat(theStoredCardCharges()).hasSize(1);
  }

  @Test
  void a_stored_card_charge_is_looked_up_with_its_own_custom_fields_and_no_hosted_process() {
    String cardToken = aRegisteredCard();
    MultiValueMap<String, Object> form = aChargeOf(cardToken, "1001");
    form.add("cField1", "merchant-session");
    String transactionId = transactionIdOf(charge(form));

    ResponseEntity<byte[]> looked = lookUp(transactionId);

    assertThat(bodyOf(looked))
        .contains("\"processId\":\"\",\"processToken\":\"\"")
        .contains("\"customField\":{\"cField1\":\"merchant-session\"}");
  }

  @Test
  void money_taken_from_a_stored_card_is_refundable_in_full_on_the_day_it_was_taken() {
    String transactionId = transactionIdOf(charge(aChargeOf(aRegisteredCard(), "1001")));
    GrowTransaction charged = transactions.findByTransactionId(RUN, transactionId).orElseThrow();

    ResponseEntity<byte[]> refunded = refund(charged, CHARGED);

    assertThat(bodyOf(refunded)).contains("\"status\":1");
    assertThat(state(transactionId)).containsEntry("normalizedStatus", "REFUNDED");
  }

  @Test
  void the_inspector_shows_the_refund_that_names_the_charge_it_reversed_and_is_keyed_by_nothing() {
    String transactionId = transactionIdOf(charge(aChargeOf(aRegisteredCard(), "1001")));
    GrowTransaction charged = transactions.findByTransactionId(RUN, transactionId).orElseThrow();
    refund(charged, CHARGED);
    String refundId = theOnlyRefundRow();

    String screen =
        String.valueOf(
            http.get()
                .uri("/ui/idempotency?provider=grow&testRunId=" + RUN.value())
                .retrieve()
                .body(String.class));

    assertThat(refundId).isNotEqualTo(transactionId);
    assertThat(screen).contains("named by refundedTransactionId from");
    assertThat(screen).contains(refundId);
  }

  @Test
  void a_reset_takes_the_submissions_with_the_charges_they_belong_to() {
    String cardToken = aRegisteredCard();
    charge(aChargeOf(cardToken, "1001"));

    reset();
    configure();
    String again = aRegisteredCard();

    assertThat(charge(aChargeOf(again, "1001")).getStatusCode().value()).isEqualTo(200);
    assertThat(bodyOf(charge(aChargeOf(again, "1002")))).contains("\"status\":1");
  }

  @Test
  void the_cards_offered_to_charge_are_the_ones_this_business_saved_in_this_test_run() {
    String cardToken = aRegisteredCard();
    String holdThatKeptNoCard = createTheHoldThatKeepsNoCard();
    completeTheProcess(holdThatKeptNoCard);

    assertThat(theCardTokensSavedBy(MERCHANT))
        .as("a hold that kept no card leaves nothing for a stored-card charge to spend")
        .containsExactly(cardToken);
    assertThat(theCardTokensSavedBy("merchant-nobody-configured"))
        .as("a token this business never saved is answered err.id 140, so it is offered to none")
        .isEmpty();
    assertThat(
            GrowOrigination.theSavedCardsThisRunHolds(
                processes.theProcessesThatSavedACard(
                    TestRunId.of("grow-stored-card-another-run"), MERCHANT)))
        .as("another test run's saved cards belong to that run alone")
        .isEmpty();
  }

  private List<String> theCardTokensSavedBy(String accountId) {
    return GrowOrigination.theSavedCardsThisRunHolds(
            processes.theProcessesThatSavedACard(RUN, accountId))
        .stream()
        .map(AValueAFieldOffers::value)
        .toList();
  }

  private String createTheHoldThatKeepsNoCard() {
    MultiValueMap<String, Object> form = registration();
    form.set("chargeType", "2");
    form.set("saveCardToken", "0");
    form.set("sum", "10.00");
    ResponseEntity<byte[]> created =
        http.post()
            .uri(CREATE)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(form)
            .retrieve()
            .toEntity(byte[].class);
    Matcher processId = PROCESS_ID.matcher(bodyOf(created));
    assertThat(processId.find()).isTrue();
    return processId.group(1);
  }

  private String theOnlyRefundRow() {
    List<String> refunds =
        jdbc.queryForList(
            "select transaction_id from grow_transaction"
                + " where test_run_id = ? and kind = 'REFUND'",
            String.class,
            RUN.value());
    assertThat(refunds).hasSize(1);
    return refunds.get(0);
  }

  private List<String> theStoredCardCharges() {
    return jdbc.queryForList(
        "select transaction_id from grow_transaction"
            + " where test_run_id = ? and kind = 'TOKEN_PAYMENT'",
        String.class,
        RUN.value());
  }

  private String aRegisteredCard() {
    String processId = createTheRegistration();
    completeTheProcess(processId);
    return theCardTokenOf(processId);
  }

  private String theCardTokenOf(String processId) {
    GrowProcessOutcome outcome = processes.findByProcessId(RUN, processId).orElseThrow().outcome();
    assertThat(outcome).isNotNull();
    return String.valueOf(outcome.cardToken());
  }

  private void completeTheProcess(String processId) {
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
  }

  private String createTheRegistration() {
    ResponseEntity<byte[]> created =
        http.post()
            .uri(CREATE)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(registration())
            .retrieve()
            .toEntity(byte[].class);
    Matcher processId = PROCESS_ID.matcher(bodyOf(created));
    assertThat(processId.find()).isTrue();
    return processId.group(1);
  }

  private static final Pattern PROCESS_ID = Pattern.compile("\"processId\":\"([0-9]+)\"");

  private MultiValueMap<String, Object> registration() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "3");
    form.add("sum", "1");
    form.add("saveCardToken", "1");
    form.add("description", "A card registration");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", merchant.url());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return form;
  }

  private static MultiValueMap<String, Object> aChargeOf(String cardToken, String uniqueId) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("cardToken", cardToken);
    form.add("userId", USER_ID);
    form.add("sum", CHARGED);
    form.add("description", "A stored-card charge");
    form.add("paymentType", "2");
    form.add("paymentNum", "1");
    form.add("transactionUniqueIdentifier", uniqueId);
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return form;
  }

  private ResponseEntity<byte[]> charge(MultiValueMap<String, Object> form) {
    return http.post()
        .uri(CHARGE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private ResponseEntity<byte[]> lookUp(String transactionId) {
    GrowTransaction charged = transactions.findByTransactionId(RUN, transactionId).orElseThrow();
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", transactionId);
    form.add("transactionToken", charged.transactionToken());
    return http.post()
        .uri(LOOK_UP)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private ResponseEntity<byte[]> refund(GrowTransaction charged, String refundSum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", charged.transactionId());
    form.add("transactionToken", charged.transactionToken());
    form.add("refundSum", refundSum);
    return http.post()
        .uri(REFUND)
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

  private static String transactionIdOf(ResponseEntity<byte[]> response) {
    String body = bodyOf(response);
    int at = body.indexOf("\"transactionId\":");
    return body.substring(at + 16, body.indexOf(',', at + 16));
  }

  private void configure() {
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

  private static String bodyOf(ResponseEntity<byte[]> response) {
    return GrowWireText.readable(new String(Objects.requireNonNull(response.getBody()), UTF_8));
  }
}
