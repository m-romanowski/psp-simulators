package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.AtOnce;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.TheRequestsUnderOneKey;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowConcurrentExchangeIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-concurrent-exchange-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-concurrent-user";
  private static final String PAGE_CODE = "grow-concurrent-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String CHARGE = "/api/light/server/1.0/createTransactionWithToken";
  private static final String LOOK_UP = "/api/light/server/1.0/getTransactionInfo";
  private static final String TABLE = "grow_keyed_exchange";
  private static final Pattern PROCESS_ID = Pattern.compile("\"processId\":\"([0-9]+)\"");
  private static final int CONCURRENT = 16;

  @LocalServerPort private int port;

  @Autowired private GrowPaymentProcesses processes;
  @Autowired private GrowTransactions transactions;
  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure(
        List.of(
            Map.of(
                "accountId",
                MERCHANT,
                "configuration",
                Map.of("userId", USER_ID, "pageCode", PAGE_CODE))));
  }

  @AfterEach
  void unconfigureTheTestRun() {
    configure(List.of());
    reset();
    merchant.close();
  }

  @Test
  void lookups_sent_at_once_are_each_recorded_as_a_retry_of_the_one_recorded_first()
      throws Exception {
    String transactionId = transactionIdOf(charge(aChargeOf(aRegisteredCard(), "1001")));

    List<Integer> statuses =
        AtOnce.sent(CONCURRENT, () -> lookUp(transactionId).getStatusCode().value());

    assertThat(statuses).hasSize(CONCURRENT).containsOnly(200);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheName(
            jdbc, TABLE, GrowKeyMechanisms.TRANSACTION_ID, transactionId, RUN.value()),
        CONCURRENT,
        Set.of(ExchangeVerdict.CORRELATED_RETRY));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "grow", RUN.value());
  }

  @Test
  void charges_sent_at_once_under_one_unique_identifier_are_numbered_after_the_one_that_charged()
      throws Exception {
    MultiValueMap<String, Object> charge = aChargeOf(aRegisteredCard(), "1001");

    List<Integer> statuses = AtOnce.sent(CONCURRENT, () -> charge(charge).getStatusCode().value());

    assertThat(statuses).hasSize(CONCURRENT).containsOnly(200);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheName(
            jdbc, TABLE, GrowKeyMechanisms.UNIQUE_IDENTIFIER, "1001", RUN.value()),
        CONCURRENT,
        Set.of(ExchangeVerdict.DUPLICATE_KEY_REFUSAL));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "grow", RUN.value());
  }

  @Test
  void charges_sent_at_once_under_one_group_are_numbered_after_the_one_that_charged()
      throws Exception {
    String card = aRegisteredCard();
    AtomicInteger unique = new AtomicInteger(2000);

    List<Integer> statuses =
        AtOnce.sent(
            CONCURRENT,
            () -> {
              MultiValueMap<String, Object> charge =
                  aChargeOf(card, String.valueOf(unique.incrementAndGet()));
              charge.add("transactionGroupIdentifier", "basket-7");
              return charge(charge).getStatusCode().value();
            });

    assertThat(statuses).hasSize(CONCURRENT).containsOnly(200);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheName(
            jdbc, TABLE, GrowKeyMechanisms.GROUP_IDENTIFIER, "basket-7", RUN.value()),
        CONCURRENT,
        Set.of(ExchangeVerdict.DUPLICATE_KEY_REFUSAL, ExchangeVerdict.REPEAT_ADMITTED));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "grow", RUN.value());
  }

  @Test
  void a_lookup_is_kept_without_the_token_and_the_custom_field_its_trail_withholds() {
    MultiValueMap<String, Object> charge = aChargeOf(aRegisteredCard(), "1002");
    charge.add("cField1", "merchant-session-secret");
    String transactionId = transactionIdOf(charge(charge));
    String token =
        transactions.findByTransactionId(RUN, transactionId).orElseThrow().transactionToken();

    String answered = bodyOf(lookUp(transactionId));

    String kept =
        jdbc.sql(
                "select body from grow_keyed_exchange where test_run_id = ? and key_name = ?"
                    + " and key_value = ?")
            .params(RUN.value(), GrowKeyMechanisms.TRANSACTION_ID, transactionId)
            .query(String.class)
            .single();
    String screen =
        String.valueOf(
            http.get()
                .uri("/ui/idempotency?provider=grow&testRunId=" + RUN.value())
                .retrieve()
                .body(String.class));
    assertThat(answered).contains(token).contains("merchant-session-secret");
    assertThat(kept)
        .contains("\"data.transactionToken\":[\"" + Redaction.REDACTED + "\"]")
        .contains("\"data.customField.cField1\":[\"" + Redaction.REDACTED + "\"]")
        .doesNotContain(token)
        .doesNotContain("merchant-session-secret");
    assertThat(screen)
        .contains(transactionId)
        .doesNotContain(token)
        .doesNotContain("merchant-session-secret");
  }

  private String aRegisteredCard() {
    ResponseEntity<byte[]> created =
        http.post()
            .uri(CREATE)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(registration(merchant.url()))
            .retrieve()
            .toEntity(byte[].class);
    Matcher processId = PROCESS_ID.matcher(bodyOf(created));
    assertThat(processId.find()).isTrue();
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId.group(1)))
        .retrieve()
        .toBodilessEntity();
    GrowProcessOutcome outcome =
        processes.findByProcessId(RUN, processId.group(1)).orElseThrow().outcome();
    assertThat(outcome).isNotNull();
    return String.valueOf(outcome.cardToken());
  }

  private static MultiValueMap<String, Object> registration(String notifyUrl) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "3");
    form.add("sum", "1");
    form.add("saveCardToken", "1");
    form.add("description", "A card registration");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", notifyUrl);
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return form;
  }

  private static MultiValueMap<String, Object> aChargeOf(String cardToken, String uniqueId) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("cardToken", cardToken);
    form.add("userId", USER_ID);
    form.add("sum", "89.90");
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
        .exchange(
            (request, response) ->
                ResponseEntity.status(response.getStatusCode())
                    .body(response.getBody().readAllBytes()));
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
        .exchange(
            (request, response) ->
                ResponseEntity.status(response.getStatusCode())
                    .body(response.getBody().readAllBytes()));
  }

  private static String transactionIdOf(ResponseEntity<byte[]> response) {
    String body = bodyOf(response);
    int at = body.indexOf("\"transactionId\":");
    return body.substring(at + 16, body.indexOf(',', at + 16));
  }

  private void configure(List<Map<String, Object>> accounts) {
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
                accounts,
                "webhooks",
                accounts.isEmpty()
                    ? List.of()
                    : List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
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
