package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class ClockMakesACallbackAndItsRetryDueIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("clock-callbacks-grow-it");

  private static final String PINNED = "2026-09-15T10:00:00Z";

  private static final String MERCHANT = "merchant-1";

  private static final String USER_ID = "clock-callbacks-user-must-never-be-read-back";

  private static final String PAGE_CODE = "clock-callbacks-page-must-never-be-read-back";

  private static final String AN_HOUR_BETWEEN_ATTEMPTS = "PT0S,PT1H,PT1H";

  private static final long AN_HOUR_IN_MILLISECONDS = 3_600_000L;

  private static final String WEBHOOKS = "/__simulator/grow/webhooks";

  private static final String DELIVER_DUE = WEBHOOKS + "/deliver-due";

  private static final String ADVANCE = "/__simulator/grow/clock/advance";

  @LocalServerPort private int port;

  private RestClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void standUpTheMerchantAndPinTheRun() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure();
  }

  @AfterEach
  void takeTheMerchantDown() {
    reset();
    merchant.close();
  }

  @Test
  void a_callback_scheduled_for_an_hour_away_is_posted_by_the_advance_that_reaches_that_hour() {
    String processId = aHoldNoPayerCompletes();
    scheduleInAnHour(processId);

    int beforeTheJump = deliverDue();
    advance("PT1H");
    int afterTheJump = deliverDue();

    assertThat(beforeTheJump).isZero();
    assertThat(afterTheJump).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
    assertThat(theDelivery()).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 1);
  }

  @Test
  void a_callback_scheduled_for_an_hour_away_is_left_alone_by_an_advance_that_stops_short() {
    String processId = aHoldNoPayerCompletes();
    scheduleInAnHour(processId);

    advance("PT30M");

    assertThat(deliverDue()).isZero();
    assertThat(merchant.received()).isEmpty();
    assertThat(theDelivery()).containsEntry("status", "PENDING").containsEntry("attemptsMade", 0);
  }

  @Test
  void a_retry_a_refusing_merchant_earned_is_attempted_by_the_advance_that_reaches_it() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));
    String processId = aHoldNoPayerCompletes();
    Map<String, Object> firstAttempt = sendNow(processId);

    int beforeTheJump = deliverDue();
    advance("PT1H");
    int afterTheJump = deliverDue();

    assertThat(firstAttempt).containsEntry("status", "PENDING").containsEntry("attemptsMade", 1);
    assertThat(beforeTheJump).isZero();
    assertThat(afterTheJump).isEqualTo(1);
    assertThat(merchant.received()).hasSize(2);
    assertThat(theDelivery()).containsEntry("attemptsMade", 2);
  }

  @Test
  void two_advances_of_an_hour_carry_a_refused_callback_through_the_whole_attempt_plan() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));
    String processId = aHoldNoPayerCompletes();
    sendNow(processId);

    advance("PT1H");
    deliverDue();
    advance("PT1H");
    deliverDue();

    assertThat(merchant.received()).hasSize(3);
    Map<String, Object> delivery = theDelivery();
    assertThat(delivery).containsEntry("status", "EXHAUSTED").containsEntry("attemptsMade", 3);
    assertThat(delivery.get("nextAttemptAt")).isNull();
  }

  private void advance(String advanceBy) {
    Map<String, Object> advanced =
        http.post()
            .uri(ADVANCE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "advanceBy", advanceBy))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(advanced).containsEntry("advancedBy", advanceBy);
  }

  private int deliverDue() {
    Map<String, Object> answered =
        http.post()
            .uri(DELIVER_DUE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value()))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return (Integer) answered.get("attempted");
  }

  private Map<String, Object> scheduleInAnHour(String processId) {
    return http.post()
        .uri(WEBHOOKS)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "providerObjectId",
                processId,
                "delayMs",
                AN_HOUR_IN_MILLISECONDS))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> sendNow(String processId) {
    return http.post()
        .uri(WEBHOOKS)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> theDelivery() {
    List<Map<String, Object>> deliveries =
        http.get()
            .uri(WEBHOOKS + "?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(deliveries).hasSize(1);
    return deliveries.get(0);
  }

  @SuppressWarnings("unchecked")
  private String aHoldNoPayerCompletes() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", "120.50");
    form.add("saveCardToken", "1");
    form.add("description", "A hold no payer completes");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", merchant.url());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    Map<String, Object> created =
        GrowWireText.asJson(
            http.post()
                .uri("/api/light/server/1.0/createPaymentProcess")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    assertThat(created).containsEntry("status", 1);
    return (String) ((Map<String, Object>) created.get("data")).get("processId");
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
                Map.of("fixedNow", PINNED),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", USER_ID, "pageCode", PAGE_CODE))),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url())),
                "settings",
                Map.of("webhook.retryPlan", AN_HOUR_BETWEEN_ATTEMPTS)))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }
}
