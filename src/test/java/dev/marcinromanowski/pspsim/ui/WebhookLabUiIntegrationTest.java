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

class WebhookLabUiIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-webhook-lab-it");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "ui-webhook-lab-user";
  private static final String PAGE_CODE = "ui-webhook-lab-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String WEBHOOKS = "/ui/webhooks";
  private static final String A_TARGET_NO_TEST_DECLARED = "http://127.0.0.1:9/stolen";

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;

  private RestClient http;
  private HttpClient wire;
  private LocalMerchantServer merchant;
  private LocalMerchantServer second;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    merchant = new LocalMerchantServer();
    second = new LocalMerchantServer();
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
    second.close();
  }

  @Test
  void a_tester_sends_an_event_from_the_lab_and_the_merchant_receives_it() {
    String processId = aProcess();

    send(Map.of("providerObjectId", processId));

    assertThat(merchant.received()).hasSize(1);
    assertThat(queuePage()).contains(processId).contains("DELIVERED");
  }

  @Test
  void a_delivery_its_attempt_and_the_exchanges_behind_them_are_each_badged_in_their_own_tone() {
    String processId = aProcess();
    send(Map.of("providerObjectId", processId));
    String deliveryId = theDeliveryIdOnThePage();

    assertThat(openedPage(deliveryId))
        .containsPattern("class=\"tag success\"\\s*>DELIVERED</span>")
        .containsPattern("class=\"tag success\"\\s*>ACKNOWLEDGED</span>");
    assertThat(page("/ui/audit?provider=grow&testRunId=" + RUN.value()))
        .containsPattern("class=\"tag success\"\\s*>SUCCESS</span>");
    assertThat(page("/ui/timeline?provider=grow&testRunId=" + RUN.value() + "&key=" + processId))
        .containsPattern("class=\"tag success\"\\s*>SUCCESS</span>")
        .containsPattern("class=\"tag success\"\\s*>DELIVERED</span>");
  }

  @Test
  void an_event_scheduled_for_later_waits_for_this_run_s_clock_rather_than_being_attempted() {
    String processId = aProcess();

    send(Map.of("providerObjectId", processId, "delayMs", "60000"));

    assertThat(merchant.received()).isEmpty();
    assertThat(queuePage()).contains("PENDING");
  }

  @Test
  void a_delay_that_is_not_a_number_refuses_the_send_naming_the_field_and_not_what_was_typed() {
    String processId = aProcess();

    String page = send(Map.of("providerObjectId", processId, "delayMs", "soonish"));

    assertThat(theDiagnosticOn(page))
        .isEqualTo(DeliveryForm.A_DELAY_IS_A_WHOLE_NUMBER_OF_MILLISECONDS)
        .doesNotContain("soonish");
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void an_event_type_provider_grow_does_not_publish_is_refused_in_grow_s_own_words() {
    String processId = aProcess();

    String page =
        send(Map.of("providerObjectId", processId, "eventType", "payment_intent.succeeded"));

    assertThat(page).contains("payment_process.completed").contains("payment_process.failed");
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void replaying_an_event_id_the_run_already_carries_sends_the_merchant_that_same_event_again() {
    String processId = aProcess();
    send(Map.of("providerObjectId", processId));
    String eventId = theEventIdOnThePage();

    send(Map.of("providerObjectId", processId, "reuseEventId", eventId));

    assertThat(merchant.received()).hasSize(2);
    assertThat(theEventIdsOfTheDeliveriesOfThisRun()).containsExactly(eventId, eventId);
  }

  @Test
  void one_attempt_is_posted_to_another_configured_target_while_the_delivery_keeps_its_own() {
    String processId = aProcess();
    send(Map.of("providerObjectId", processId));
    String deliveryId = theDeliveryIdOnThePage();

    retry(deliveryId, second.url());

    assertThat(second.received()).hasSize(1);
    assertThat(openedPage(deliveryId))
        .contains("not the target it was scheduled for")
        .contains(second.url());
  }

  @Test
  void a_target_no_test_run_configured_is_refused_and_nothing_is_posted_to_it() {
    String processId = aProcess();
    send(Map.of("providerObjectId", processId));
    int before = merchant.received().size();

    String page = retry(theDeliveryIdOnThePage(), A_TARGET_NO_TEST_DECLARED);

    assertThat(theDiagnosticOn(page))
        .isEqualTo(DeliveryForm.THE_TARGET_IS_NOT_CONFIGURED_FOR_THIS_RUN)
        .doesNotContain(A_TARGET_NO_TEST_DECLARED);
    assertThat(merchant.received()).hasSize(before);
    assertThat(second.received()).isEmpty();
  }

  @Test
  void the_lab_offers_the_run_s_targets_and_draws_no_box_a_url_could_be_typed_into() {
    String processId = aProcess();
    send(Map.of("providerObjectId", processId));

    String opened = openedPage(theDeliveryIdOnThePage());

    assertThat(opened).contains("name=\"targetUrl\"").contains(second.url());
    assertThat(opened).doesNotContain("type=\"text\" name=\"targetUrl\"");
  }

  @Test
  void an_attempt_on_the_timeline_names_the_address_it_was_actually_posted_to() {
    String processId = aProcess();
    send(Map.of("providerObjectId", processId));
    retry(theDeliveryIdOnThePage(), second.url());

    String timeline =
        page("/ui/timeline?provider=grow&testRunId=" + RUN.value() + "&key=" + processId);

    assertThat(timeline).contains("callback delivery attempt").contains(second.url());
  }

  private String aProcess() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("userId", USER_ID);
    form.put("chargeType", "2");
    form.put("sum", "120.50");
    form.put("saveCardToken", "1");
    form.put("description", "A hold");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("notifyUrl", merchant.url());
    form.put("pageField[fullName]", "A Payer");
    form.put("pageField[phone]", "0505503144");
    Map<String, Object> created = parse(postForm(CREATE, form));
    assertThat(created).as("createPaymentProcess answered %s", created).containsEntry("status", 1);
    return String.valueOf(dataOf(created).get("processId"));
  }

  private String send(Map<String, String> fields) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.putAll(fields);
    return followed(postForm(WEBHOOKS + "/send", form));
  }

  private String retry(String deliveryId, String targetUrl) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("deliveryId", deliveryId);
    form.put("targetUrl", targetUrl);
    return followed(postForm(WEBHOOKS + "/retry", form));
  }

  private String followed(HttpResponse<byte[]> redirect) {
    assertThat(redirect.statusCode()).isEqualTo(302);
    URI location = URI.create(redirect.headers().firstValue("Location").orElseThrow());
    String query = location.getRawQuery();
    return page(location.getRawPath() + (query == null ? "" : "?" + query));
  }

  private String queuePage() {
    return page(WEBHOOKS + "?provider=grow&testRunId=" + RUN.value());
  }

  private String openedPage(String deliveryId) {
    return page(WEBHOOKS + "?provider=grow&testRunId=" + RUN.value() + "&open=" + deliveryId);
  }

  private String theEventIdOnThePage() {
    List<String> found = theEventIdsOfTheDeliveriesOfThisRun();
    assertThat(found).isNotEmpty();
    return found.get(0);
  }

  private List<String> theEventIdsOfTheDeliveriesOfThisRun() {
    List<String> found = new ArrayList<>();
    for (Map<String, Object> delivery : theDeliveriesOfThisRun()) {
      found.add(String.valueOf(delivery.get("eventId")));
    }
    return found;
  }

  private List<Map<String, Object>> theDeliveriesOfThisRun() {
    return json.readValue(
        http.get()
            .uri("/__simulator/grow/webhooks?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class),
        new TypeReference<List<Map<String, Object>>>() {});
  }

  private static String theDiagnosticOn(String page) {
    Matcher refusal =
        Pattern.compile("<div class=\"refusal\">(.*?)</div>", Pattern.DOTALL).matcher(page);
    assertThat(refusal.find()).as("the page carries a refusal").isTrue();
    return refusal
        .group(1)
        .strip()
        .replaceAll("\\s+", " ")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace("&amp;", "&");
  }

  private String theDeliveryIdOnThePage() {
    Matcher delivery = Pattern.compile("(whjob_[0-9a-f]+)").matcher(queuePage());
    assertThat(delivery.find()).isTrue();
    return delivery.group(1);
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        wireSend(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private HttpResponse<byte[]> postForm(String path, Map<String, String> form) {
    return wireSend(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
  }

  private HttpResponse<byte[]> wireSend(HttpRequest.Builder request) {
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

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(
        new String(response.body(), UTF_8), new TypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
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
                List.of(
                    Map.of("accountId", MERCHANT, "url", merchant.url()),
                    Map.of("accountId", MERCHANT, "url", second.url()))))
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
}
