package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class AzericardCallbackDeliveryIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-callback-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "70000005";
  private static final String MERCHANT = "merchant-1";
  private static final String CGI_LINK = "/cgi-bin/cgi_link";
  private static final String HELD = "11.48";

  @LocalServerPort private int port;

  private RestClient http;
  private LocalMerchantServer merchant;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure(Map.of());
  }

  @AfterEach
  void unconfigureTheTerminal() {
    reset();
    unconfigure();
    merchant.close();
  }

  @Test
  void posts_the_signed_authorization_result_to_the_backref_the_request_carried() {
    String intRef = anApprovedAuthorization("20260910160001", merchant.url());

    Map<String, Object> job = sendTheCallback(intRef);

    assertThat(job).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 1);
    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    assertThat(received.contentType()).isEqualTo("application/x-www-form-urlencoded");
    assertThat(received.fields())
        .containsEntry(AzericardFields.INT_REF, intRef)
        .containsEntry(AzericardFields.TERMINAL, TERMINAL_ID)
        .containsEntry(AzericardFields.AMOUNT, HELD)
        .containsEntry(AzericardFields.ACTION, "0")
        .containsEntry(AzericardFields.RC, "00")
        .containsEntry(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    received.fields().get(AzericardFields.AMOUNT),
                    received.fields().get(AzericardFields.TERMINAL),
                    received.fields().get(AzericardFields.APPROVAL),
                    received.fields().get(AzericardFields.RRN),
                    received.fields().get(AzericardFields.INT_REF)),
                received.fields().get(AzericardFields.P_SIGN)))
        .isTrue();
  }

  @Test
  void lists_the_delivery_with_no_event_name_because_the_vendor_publishes_none() {
    String intRef = anApprovedAuthorization("20260910160002", merchant.url());

    Map<String, Object> job = sendTheCallback(intRef);

    Map<String, Object> delivery = listDeliveries().get(0);
    assertThat(delivery)
        .containsEntry("webhookJobId", job.get("webhookJobId"))
        .containsEntry("providerObjectId", intRef)
        .containsEntry("merchantId", MERCHANT)
        .containsEntry("targetUrl", merchant.url())
        .containsEntry("status", "DELIVERED");
    assertThat(delivery.get("eventType")).isNull();
  }

  @Test
  void sends_a_signature_a_merchant_must_refuse_when_the_terminal_is_configured_to_corrupt_it() {
    configure(Map.of(AzericardCallbackSignature.SETTING, "CORRUPTED"));
    String intRef = anApprovedAuthorization("20260910160003", merchant.url());

    sendTheCallback(intRef);

    Map<String, String> fields = merchant.received().get(0).fields();
    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    fields.get(AzericardFields.AMOUNT),
                    fields.get(AzericardFields.TERMINAL),
                    fields.get(AzericardFields.APPROVAL),
                    fields.get(AzericardFields.RRN),
                    fields.get(AzericardFields.INT_REF)),
                fields.get(AzericardFields.P_SIGN)))
        .isFalse();
  }

  @Test
  void retries_a_callback_the_merchant_refused_and_sends_the_same_bytes_again() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(503, "later"));
    String intRef = anApprovedAuthorization("20260910160004", merchant.url());

    Map<String, Object> job = sendTheCallback(intRef);
    assertThat(job).containsEntry("status", "PENDING").containsEntry("attemptsMade", 1);

    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(200, "OK"));
    Map<String, Object> retried =
        webhookRequest(
            Map.of(
                "testRunId",
                RUN.value(),
                "operation",
                "RETRY",
                "webhookJobId",
                job.get("webhookJobId")));

    assertThat(retried).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 2);
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).fields()).isEqualTo(merchant.received().get(0).fields());
  }

  @Test
  void refuses_to_post_to_a_backref_this_test_run_never_declared() {
    String intRef = anApprovedAuthorization("20260910160005", "https://elsewhere.example/hook");

    ResponseEntity<String> response =
        http.post()
            .uri("/__simulator/azericard/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "providerObjectId", intRef))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).contains("not among the callback targets configured");
    assertThat(response.getBody()).doesNotContain("elsewhere.example");
    assertThat(listDeliveries()).isEmpty();
  }

  @Test
  void answers_404_for_an_authorization_this_test_run_never_made() {
    ResponseEntity<String> response =
        http.post()
            .uri("/__simulator/azericard/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "providerObjectId", "NOSUCHREFERENCE1"))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  private String anApprovedAuthorization(String order, String backref) {
    Map<String, String> form = authorization(order);
    form.put(AzericardFields.BACKREF, backref);
    String body = new String(post(signed(form)).body(), UTF_8);
    assertThat(body).contains("<action>0</action>");
    return valueIn(body, "intref");
  }

  private Map<String, Object> sendTheCallback(String intRef) {
    return webhookRequest(Map.of("testRunId", RUN.value(), "providerObjectId", intRef));
  }

  private Map<String, Object> webhookRequest(Map<String, Object> request) {
    return http.post()
        .uri("/__simulator/azericard/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> listDeliveries() {
    return http.get()
        .uri("/__simulator/azericard/webhooks?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private static Map<String, String> authorization(String order) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.AMOUNT, HELD);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    Map<String, List<String>> fields = new LinkedHashMap<>();
    signed.forEach((name, value) -> fields.put(name, List.of(value)));
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.CGI_LINK, fields).macSource()));
    return signed;
  }

  private HttpResponse<byte[]> post(Map<String, String> form) {
    try {
      return wire.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + CGI_LINK))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private static String valueIn(String xml, String element) {
    int opening = xml.indexOf("<" + element + ">");
    return xml.substring(
        opening + element.length() + 2, xml.indexOf("</" + element + ">", opening));
  }

  private void configure(Map<String, String> extraSettings) {
    Map<String, String> configuration = new LinkedHashMap<>(extraSettings);
    configuration.put(AzericardTerminal.TERMINAL_PROPERTY, TERMINAL_ID);
    configuration.put(AzericardTerminal.MERCHANT_KEY_PROPERTY, AzericardKeyFixture.MERCHANT_PUBLIC);
    configuration.put(
        AzericardTerminal.GATEWAY_KEY_PROPERTY, AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8);
    http.put()
        .uri("/__simulator/azericard/config")
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
        .uri("/__simulator/azericard/config")
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
        .uri("/__simulator/azericard/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }
}
