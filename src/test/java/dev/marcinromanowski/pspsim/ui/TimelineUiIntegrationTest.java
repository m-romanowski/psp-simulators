package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class TimelineUiIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-timeline-it");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "ui-timeline-user";
  private static final String PAGE_CODE = "ui-timeline-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String HELD = "120.50";
  private static final String PAYER = "Israel Israeli";
  private static final String PAYER_PHONE = "0505503144";
  private static final String SESSION = "session-must-not-reach-a-monitor";
  private static final String BEARER = "Bearer must-not-reach-a-monitor";
  private static final String TIMELINE = "/ui/timeline";

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;

  private RestClient http;
  private HttpClient wire;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
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
  void
      a_settled_payment_traced_by_its_transaction_id_shows_the_call_that_settled_it_and_its_answer() {
    Hold hold = aHold();
    settle(hold);

    String page = timelineOf(hold.transactionId());

    assertThat(page)
        .contains("provider-compatible request received")
        .contains("provider-compatible response sent")
        .contains(SETTLE)
        .contains(hold.transactionId());
  }

  @Test
  void a_settled_payment_reports_the_provider_native_status_and_the_normalized_status_together() {
    Hold hold = aHold();
    settle(hold);

    String page = timelineOf(hold.transactionId());

    assertThat(page).contains("provider-native status").contains("normalized status");
    assertThat(page).contains("SUCCEEDED");
  }

  @Test
  void a_payment_traced_by_the_callback_event_id_of_its_process_lands_on_that_process_timeline() {
    Hold hold = aHold();

    String eventId = theEventIdOfTheCallbackOf(hold.processId());
    String page = timelineOf(eventId);

    assertThat(page).contains(hold.processId());
    assertThat(page).contains("a callback event id");
  }

  @Test
  void
      a_key_no_object_callback_or_keyed_request_of_this_run_carries_says_so_rather_than_showing_nothing() {
    aHold();

    String page = timelineOf("txn_nothing_of_this_run_carries");

    assertThat(page).contains(TimelineScreen.NOTHING_OF_THIS_RUN_CARRIES_IT);
  }

  @Test
  void the_screen_names_the_facts_provider_grow_has_no_such_thing_for_in_grows_own_words() {
    Hold hold = aHold();

    String page = timelineOf(hold.transactionId());

    assertThat(page)
        .contains("match.orderId")
        .contains("provider grow publishes no order identifier on any endpoint of this contract");
    assertThat(page)
        .contains("true idempotency key")
        .contains("provider grow publishes no idempotency header");
  }

  @Test
  void the_screen_names_the_events_this_simulator_records_nowhere_instead_of_leaving_them_blank() {
    Hold hold = aHold();

    String page = timelineOf(hold.transactionId());

    assertThat(page)
        .contains("a callback that was scheduled")
        .contains("a state transition of its own")
        .contains("the delivery an attempt on the trail belongs to")
        .contains("a replay or a conflict under a key");
  }

  @Test
  void
      a_timeline_of_a_payment_puts_no_credential_and_no_payer_detail_on_the_page_until_it_is_asked() {
    Hold hold = aHold();
    settle(hold);

    String closed = timelineOf(hold.transactionId());

    assertThat(closed)
        .doesNotContain(BEARER)
        .doesNotContain(SESSION)
        .doesNotContain(PAYER)
        .doesNotContain(PAYER_PHONE);
  }

  @Test
  void a_value_redacted_where_it_was_stored_stays_off_the_timeline_when_a_tester_asks_for_values() {
    Hold hold = aHold();
    settle(hold);

    String shown = page(theOpenedExchangeOf(hold.transactionId()) + "&values=show");

    assertThat(shown).doesNotContain(BEARER);
    assertThat(shown).contains("redacted before it was stored");
  }

  private String theOpenedExchangeOf(String key) {
    String page = timelineOf(key);
    Matcher opened = Pattern.compile("href=\"([^\"]*open=aud_[^\"]*)\"").matcher(page);
    assertThat(opened.find()).isTrue();
    return opened.group(1).replace("&amp;", "&");
  }

  private String theEventIdOfTheCallbackOf(String providerObjectId) {
    String webhooks = page("/ui/webhooks?provider=grow&testRunId=" + RUN.value());
    Matcher event = Pattern.compile("(evt_[0-9a-f]+)").matcher(webhooks);
    assertThat(event.find()).as("a callback of %s was queued", providerObjectId).isTrue();
    return event.group(1);
  }

  private String timelineOf(String key) {
    return page(
        TIMELINE
            + "?provider=grow&testRunId="
            + RUN.value()
            + "&key="
            + URLEncoder.encode(key, UTF_8));
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private Hold aHold() {
    Map<String, Object> created = parse(post(CREATE, holdForm()));
    assertThat(created).containsEntry("status", 1);
    Map<String, Object> data = dataOf(created);
    String processId = String.valueOf(data.get("processId"));
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
    Map<String, Object> outcome = parse(post(PROCESS_INFO, lookupOf(processId, data)));
    Map<String, Object> transaction = firstTransactionOf(outcome);
    return new Hold(
        processId,
        String.valueOf(transaction.get("transactionId")),
        String.valueOf(transaction.get("transactionToken")));
  }

  private void settle(Hold hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("userId", USER_ID);
    form.put("transactionId", hold.transactionId());
    form.put("transactionToken", hold.transactionToken());
    form.put("sum", HELD);
    assertThat(parse(post(SETTLE, form))).containsEntry("status", 1);
  }

  private static Map<String, String> lookupOf(String processId, Map<String, Object> data) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("processId", processId);
    form.put("processToken", String.valueOf(data.get("processToken")));
    return form;
  }

  private Map<String, String> holdForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("userId", USER_ID);
    form.put("chargeType", "2");
    form.put("sum", HELD);
    form.put("saveCardToken", "1");
    form.put("description", "A hold");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("notifyUrl", merchant.url());
    form.put("pageField[fullName]", PAYER);
    form.put("pageField[phone]", PAYER_PHONE);
    form.put("cField1", SESSION);
    return form;
  }

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(
        new String(response.body(), UTF_8), new TypeReference<Map<String, Object>>() {});
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", BEARER)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
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
                Map.of("fixedNow", PINNED.toString()),
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
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstTransactionOf(Map<String, Object> body) {
    return ((List<Map<String, Object>>) dataOf(body).get("transactions")).get(0);
  }

  private record Hold(String processId, String transactionId, String transactionToken) {}
}
