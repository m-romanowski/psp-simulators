package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowApproveTransactionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-approve-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-approve-user";
  private static final String PAGE_CODE = "grow-approve-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String APPROVE = "/api/light/server/1.0/approveTransaction";

  private static final String DATA = "data[";

  @LocalServerPort private int port;

  private RestClient http;
  private LocalMerchantServer merchant;
  private String processId;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure(FIXED_NOW, "APPROVE_TRANSACTION");
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void a_delivered_callback_stays_unacknowledged_until_a_later_request_approves_it() {
    createTheHold();
    Map<String, Object> job = sendTheCallback();

    assertThat(job).containsEntry("status", "PENDING").containsEntry("attemptsMade", 1);
    assertThat(merchant.received()).hasSize(1);
    assertThat(attemptsOfTheOnlyDelivery())
        .singleElement()
        .satisfies(
            attempt ->
                assertThat(attempt)
                    .containsEntry("verdict", "AWAITING_SEPARATE_CALL")
                    .containsEntry("httpStatus", 200));

    configure(FIXED_NOW.plusSeconds(45), "APPROVE_TRANSACTION");
    ResponseEntity<byte[]> approved = approve(echoOfTheCallback());

    assertThat(approved.getStatusCode().value()).isEqualTo(200);
    assertThat(approved.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("text/html; charset=UTF-8"));
    assertThat(new String(approved.getBody(), UTF_8))
        .isEqualTo("{\"status\":1,\"err\":\"\",\"data\":\"\"}");

    Map<String, Object> delivery = theOnlyDelivery();
    assertThat(delivery).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 1);
    assertThat(delivery.get("nextAttemptAt")).isNull();
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void the_resends_are_visible_while_the_approval_is_missing_and_stop_when_it_arrives() {
    createTheHold();
    sendTheCallback();

    configure(FIXED_NOW.plusSeconds(1), "APPROVE_TRANSACTION");
    assertThat(deliverDue()).isEqualTo(1);
    assertThat(merchant.received()).hasSize(2);
    assertThat(attemptsOfTheOnlyDelivery())
        .extracting(attempt -> attempt.get("verdict"))
        .containsExactly("AWAITING_SEPARATE_CALL", "AWAITING_SEPARATE_CALL");

    approve(echoOfTheCallback());

    configure(FIXED_NOW.plusSeconds(600), "APPROVE_TRANSACTION");
    assertThat(deliverDue()).isZero();
    assertThat(merchant.received()).hasSize(2);
    assertThat(theOnlyDelivery()).containsEntry("status", "DELIVERED");
  }

  @Test
  void an_account_on_the_common_rule_is_acknowledged_without_any_approval() {
    configure(FIXED_NOW, "HTTP_2XX");
    createTheHold();

    assertThat(sendTheCallback()).containsEntry("status", "DELIVERED");
    assertThat(new String(approve(echoOfTheCallback()).getBody(), UTF_8))
        .isEqualTo("{\"status\":1,\"err\":\"\",\"data\":\"\"}");
    assertThat(attemptsOfTheOnlyDelivery()).hasSize(1);
  }

  @Test
  void a_callback_waiting_on_an_account_a_tester_removed_is_acknowledged_by_the_merchants_2xx() {
    createTheHold();

    assertThat(sendTheCallback()).containsEntry("status", "PENDING");
    assertThat(attemptsOfTheOnlyDelivery())
        .singleElement()
        .satisfies(
            attempt -> assertThat(attempt).containsEntry("verdict", "AWAITING_SEPARATE_CALL"));

    forgetTheAccount();
    moveTheClockTo(FIXED_NOW.plusSeconds(45));

    assertThat(deliverDue()).isEqualTo(1);
    assertThat(theOnlyDelivery()).containsEntry("status", "DELIVERED");
    assertThat(attemptsOfTheOnlyDelivery())
        .last()
        .satisfies(
            attempt ->
                assertThat(attempt)
                    .containsEntry("verdict", "ACKNOWLEDGED")
                    .containsEntry("httpStatus", 200));
  }

  @Test
  void an_account_removed_stops_authenticating_and_the_run_it_belonged_to_still_exists() {
    createTheHold();

    forgetTheAccount();

    assertThat(theConfiguredAccounts()).isEmpty();
    assertThat(new String(aHoldTheRemovedAccountWouldHaveServed().getBody(), UTF_8))
        .contains("\"id\":701");
  }

  @Test
  void an_account_no_configuration_of_this_run_named_is_not_found_rather_than_removed() {
    createTheHold();

    ResponseEntity<Map<String, Object>> answered =
        http.method(HttpMethod.DELETE)
            .uri("/__simulator/grow/config/accounts/merchant-9?testRunId=" + RUN.value())
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(answered.getStatusCode().value()).isEqualTo(404);
    assertThat(theConfiguredAccounts()).containsExactly(MERCHANT);
  }

  private void moveTheClockTo(Instant fixedNow) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "clock", Map.of("fixedNow", fixedNow.toString())))
        .retrieve()
        .toBodilessEntity();
  }

  private ResponseEntity<byte[]> aHoldTheRemovedAccountWouldHaveServed() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", "120.50");
    form.add("description", "A hold nothing authenticates any more");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return http.post()
        .uri(CREATE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(byte[].class);
  }

  private void forgetTheAccount() {
    ResponseEntity<Map<String, Object>> answered =
        http.method(HttpMethod.DELETE)
            .uri("/__simulator/grow/config/accounts/" + MERCHANT + "?testRunId=" + RUN.value())
            .retrieve()
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    assertThat(answered.getBody())
        .containsEntry("removedAccount", MERCHANT)
        .containsEntry("configuredAccounts", List.of());
  }

  @SuppressWarnings("unchecked")
  private List<String> theConfiguredAccounts() {
    Map<String, Object> answered =
        GrowWireText.asJson(
            http.put()
                .uri("/__simulator/grow/config")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("testRunId", RUN.value()))
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    return (List<String>) answered.get("configuredAccounts");
  }

  @Test
  void answers_180_over_http_200_for_a_transaction_this_business_has_no_record_of() {
    createTheHold();
    sendTheCallback();

    MultiValueMap<String, Object> echo = new LinkedMultiValueMap<>();
    echo.add("pageCode", PAGE_CODE);
    echo.add("transactionId", "999999");
    echo.add("transactionToken", "531bac1f99456a7140");
    ResponseEntity<byte[]> refused = approve(echo);

    assertThat(refused.getStatusCode().value()).isEqualTo(200);
    assertThat(GrowWireText.readable(new String(refused.getBody(), UTF_8)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":180,"
                + "\"message\":\"פעולת עידכון לא בוצעה, רשומה לא נמצאה\"},\"data\":\"\"}");
    assertThat(theOnlyDelivery()).containsEntry("status", "PENDING");
  }

  @Test
  void refuses_an_acknowledgement_mode_the_provider_does_not_recognise() {
    ResponseEntity<String> refused =
        http.put()
            .uri("/__simulator/grow/config")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "testRunId",
                    RUN.value(),
                    "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            MERCHANT,
                            "configuration",
                            Map.of(
                                "userId",
                                USER_ID,
                                "pageCode",
                                PAGE_CODE,
                                "callbackAcknowledgement",
                                "SOMETIMES")))))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(String.class);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refused.getBody())
        .contains("accounts[0].configuration.callbackAcknowledgement")
        .doesNotContain("SOMETIMES");
  }

  private MultiValueMap<String, Object> echoOfTheCallback() {
    MultiValueMap<String, Object> echo = new LinkedMultiValueMap<>();
    echo.add("pageCode", PAGE_CODE);
    merchant
        .received()
        .get(0)
        .fields()
        .forEach(
            (name, value) -> {
              if (name.startsWith(DATA) && name.indexOf('[', DATA.length()) < 0) {
                echo.add(name.substring(DATA.length(), name.length() - 1), value);
              }
            });
    return echo;
  }

  private void createTheHold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", "120.50");
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
    Map<String, Object> body =
        GrowWireText.asJson(
            http.post()
                .uri(CREATE)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    processId = (String) data.get("processId");
  }

  private ResponseEntity<byte[]> approve(MultiValueMap<String, Object> echo) {
    return http.post()
        .uri(APPROVE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(echo)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(byte[].class);
  }

  private Map<String, Object> sendTheCallback() {
    return GrowWireText.asJson(
        http.post()
            .uri("/__simulator/grow/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
            .retrieve()
            .toEntity(byte[].class)
            .getBody());
  }

  private int deliverDue() {
    Map<String, Object> answered =
        http.post()
            .uri("/__simulator/grow/webhooks/deliver-due")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value()))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return (Integer) answered.get("attempted");
  }

  private Map<String, Object> theOnlyDelivery() {
    List<Map<String, Object>> deliveries =
        http.get()
            .uri("/__simulator/grow/webhooks?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(deliveries).hasSize(1);
    return deliveries.get(0);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> attemptsOfTheOnlyDelivery() {
    return (List<Map<String, Object>>) theOnlyDelivery().get("attempts");
  }

  private void configure(Instant fixedNow, String acknowledgement) {
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
                        Map.of(
                            "userId",
                            USER_ID,
                            "pageCode",
                            PAGE_CODE,
                            "callbackAcknowledgement",
                            acknowledgement))),
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
}
