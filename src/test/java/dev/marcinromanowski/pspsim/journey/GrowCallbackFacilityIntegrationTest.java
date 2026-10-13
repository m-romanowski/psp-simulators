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

class GrowCallbackFacilityIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "journey-grow-callbacks";
  private static final String ACCOUNT = "merchant-1";
  private static final String USER_ID = "journey-callbacks-user";
  private static final String PAGE_CODE = "journey-callbacks-page";
  private static final String HELD = "120.50";
  private static final String WEBHOOKS = "/__simulator/grow/webhooks";

  @LocalServerPort private int port;

  private JourneyClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void standUpTheMerchant() {
    http = new JourneyClient(port);
    merchant = new LocalMerchantServer();
    configure();
  }

  @AfterEach
  void takeTheMerchantDown() {
    http.discardTheTestRun("grow", RUN);
    merchant.close();
  }

  @Test
  void a_harness_with_no_payer_still_carries_a_process_to_its_outcome_and_the_merchant_is_told() {
    String processId = theProcessId(create());

    Map<String, Object> job = sendTheCallbackFor(processId, "the completed callback with no payer");

    assertThat(job).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 1);
    assertThat(merchant.received()).hasSize(1);
    Map<String, String> fields = merchant.received().get(0).fields();
    assertThat(fields)
        .containsEntry("status", "1")
        .containsEntry("data[processId]", processId)
        .containsEntry("data[statusCode]", "11");
    assertThat(fields.get("data[transactionId]"))
        .as("the callback names a transaction of its own, not the process of the same six digits")
        .isNotBlank()
        .isNotEqualTo(processId);
    assertThat(fields.get("data[transactionToken]")).isNotBlank();
  }

  @Test
  void the_failure_callback_a_payer_never_caused_is_the_one_the_control_route_sends_on_request() {
    String processId = theProcessId(create());

    http.json(
        "ask for the failure callback of a process that reached nothing",
        Kind.WRITE,
        "POST",
        WEBHOOKS,
        """
        {"testRunId":"%s","providerObjectId":"%s","eventType":"payment_process.failed"}
        """
            .formatted(RUN, processId));

    Map<String, String> fields = merchant.received().get(0).fields();
    assertThat(fields).containsEntry("status", "0").containsEntry("data", "");
    assertThat(fields).doesNotContainKey("data[statusCode]");
    assertThat(fields.get("err[id]")).isNotBlank();
    assertThat(http.getList("read the callback ledger", webhooks()).get(0))
        .containsEntry("eventType", "payment_process.failed");
  }

  @Test
  void a_duplicate_asked_for_under_the_event_id_already_sent_arrives_byte_identical() {
    String processId = theProcessId(create());
    Map<String, Object> first = sendTheCallbackFor(processId, "the first callback");
    String eventId = (String) first.get("eventId");
    assertThat(eventId).isNotBlank();

    http.json(
        "ask the callback facility to repeat what the merchant was already told",
        Kind.WRITE,
        "POST",
        WEBHOOKS,
        """
        {"testRunId":"%s","providerObjectId":"%s",
         "eventType":"payment_process.completed","reuseEventId":"%s"}
        """
            .formatted(RUN, processId, eventId));

    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body()).isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void a_second_request_naming_a_different_outcome_for_a_reached_process_is_refused() {
    String processId = theProcessId(create());
    sendTheCallbackFor(processId, "the completed callback with no payer");

    http.json(
        "ask for the other outcome of a process the merchant was already told about",
        Kind.WRITE,
        "POST",
        WEBHOOKS,
        """
        {"testRunId":"%s","providerObjectId":"%s","eventType":"payment_process.failed"}
        """
            .formatted(RUN, processId));

    assertThat(theLastStatus()).isEqualTo(422);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void naming_the_transaction_instead_of_the_process_is_not_found_rather_than_a_callback() {
    String processId = theProcessId(create());
    sendTheCallbackFor(processId, "the completed callback with no payer");
    String transactionId = merchant.received().get(0).fields().get("data[transactionId]");
    assertThat(transactionId).isNotBlank();

    http.json(
        "name the transaction the completion created instead of the process",
        Kind.WRITE,
        "POST",
        WEBHOOKS,
        """
        {"testRunId":"%s","providerObjectId":"%s","eventType":"payment_process.completed"}
        """
            .formatted(RUN, transactionId));

    assertThat(theLastStatus()).isEqualTo(404);
    assertThat(merchant.received()).hasSize(1);
  }

  private static String webhooks() {
    return WEBHOOKS + "?testRunId=" + RUN;
  }

  private Map<String, Object> sendTheCallbackFor(String processId, String what) {
    return http.json(
        what,
        Kind.WRITE,
        "POST",
        WEBHOOKS,
        """
        {"testRunId":"%s","providerObjectId":"%s"}
        """
            .formatted(RUN, processId));
  }

  private int theLastStatus() {
    List<JourneyClient.Step> steps = http.steps();
    return steps.get(steps.size() - 1).status();
  }

  private Map<String, Object> create() {
    return http.multipart(
        "create a hosted payment process no payer will open",
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
        "A hold no payer completes",
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
  }

  @SuppressWarnings("unchecked")
  private static String theProcessId(Map<String, Object> created) {
    assertThat(created).containsEntry("status", 1);
    return (String) ((Map<String, Object>) created.get("data")).get("processId");
  }

  private void configure() {
    http.json(
        "configure the account and the callback target",
        Kind.WRITE,
        "PUT",
        "/__simulator/grow/config",
        """
        {"testRunId":"%s",
         "clock":{"fixedNow":"2026-09-15T10:00:00Z"},
         "accounts":[{"accountId":"%s","configuration":{
           "userId":"%s","pageCode":"%s"}}],
         "webhooks":[{"accountId":"%s","url":"%s"}]}
        """
            .formatted(RUN, ACCOUNT, USER_ID, PAGE_CODE, ACCOUNT, merchant.url()));
  }
}
