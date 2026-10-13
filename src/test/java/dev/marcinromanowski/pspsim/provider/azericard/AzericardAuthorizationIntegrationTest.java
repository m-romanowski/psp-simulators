package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.fault.AHeldResponse;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class AzericardAuthorizationIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-authorization-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "70000001";
  private static final String MERCHANT = "merchant-1";
  private static final String CGI_LINK = "/cgi-bin/cgi_link";

  @LocalServerPort private int port;

  @Autowired private DelayedDeliveries delays;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTerminal() {
    reset();
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

  @Test
  void authorizes_over_the_wire_and_answers_signed_xml_labelled_as_html() {
    HttpResponse<byte[]> response = post(signed(authorization("20260910120001")));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("content-type").orElseThrow()).startsWith("text/html");
    String body = new String(response.body(), UTF_8);
    assertThat(body)
        .startsWith("<response><desc>Approved</desc>")
        .contains("<terminal>" + TERMINAL_ID + "</terminal>")
        .contains("<action>0</action>")
        .contains("<rc>00</rc>")
        .contains("<currency>AZN</currency>");
    assertThat(
            AzericardKeyFixture.verifiedByTheGateway(
                AzericardMac.sourceOf(
                    "11.48",
                    TERMINAL_ID,
                    valueIn(body, "approval"),
                    valueIn(body, "rrn"),
                    valueIn(body, "intref")),
                valueIn(body, "p_sign")))
        .isTrue();
  }

  @Test
  void the_payment_it_stored_is_readable_through_the_control_api_by_its_internal_reference() {
    String body = new String(post(signed(authorization("20260910120002"))).body(), UTF_8);

    Map<String, Object> state = stateOf(valueIn(body, "intref"));

    assertThat(state)
        .containsEntry("providerObjectId", valueIn(body, "intref"))
        .containsEntry("nativeStatus", "0/00")
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE")
        .containsEntry("amount", "11.48")
        .containsEntry("currency", "AZN");
    assertThat(operationsOf(state))
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation).containsEntry("kind", "AUTHORIZATION");
              assertThat(operation).containsEntry("amount", "11.48");
            });
  }

  @Test
  void a_second_order_ending_in_the_digits_of_the_first_is_refused_as_a_duplicate() {
    post(signed(authorization("20260910120003")));

    HttpResponse<byte[]> repeated = post(signed(authorization("20270101120003")));

    assertThat(repeated.statusCode()).isEqualTo(200);
    assertThat(new String(repeated.body(), UTF_8))
        .contains("<action>1</action>")
        .contains("<intref></intref>");
  }

  @Test
  void a_request_whose_mac_does_not_verify_is_answered_signed_and_never_as_a_4xx() {
    Map<String, String> form = signed(authorization("20260910120004"));
    form.put(AzericardFields.P_SIGN, form.get(AzericardFields.P_SIGN).replace('A', 'B'));

    HttpResponse<byte[]> response = post(form);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(new String(response.body(), UTF_8))
        .contains("<desc>Access denied</desc>")
        .contains("<action>3</action>")
        .contains("<rc>-17</rc>")
        .contains("<p_sign>");
  }

  @Test
  void a_request_missing_a_field_is_answered_with_a_human_page_a_strict_parser_cannot_read() {
    Map<String, String> form = signed(authorization("20260910120005"));
    form.remove(AzericardFields.TERMINAL);

    HttpResponse<byte[]> response = post(form);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("content-type").orElseThrow()).startsWith("text/html");
    assertThat(new String(response.body(), UTF_8))
        .contains("Error message: Error in merchant terminal field")
        .doesNotContain("<action>")
        .doesNotContain("<p_sign>");
  }

  @Test
  void the_lost_response_preset_answers_the_first_authorization_without_running_it() {
    selectPreset("psp-5xx-then-success");

    HttpResponse<byte[]> lost = post(signed(authorization("20260910120006")));
    HttpResponse<byte[]> repeated = post(signed(authorization("20260910120006")));

    assertThat(lost.statusCode()).isEqualTo(502);
    assertThat(lost.headers().firstValue("Content-Type")).contains("text/plain;charset=utf-8");
    assertThat(new String(lost.body(), UTF_8))
        .startsWith("502 was answered in front of the provider.");
    assertThat(repeated.statusCode()).isEqualTo(200);
    assertThat(new String(repeated.body(), UTF_8)).contains("<action>0</action>");
  }

  @Test
  void a_delayed_answer_arrives_whole_no_sooner_than_the_scenario_said() {
    createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "action",
                Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 400)));

    long before = System.nanoTime();
    HttpResponse<byte[]> response = post(signed(authorization("20260910120007")));
    Duration waited = Duration.ofNanos(System.nanoTime() - before);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(new String(response.body(), UTF_8)).contains("<action>0</action>");
    assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(350));
  }

  @Test
  void resetting_a_test_run_answers_a_held_response_instead_of_delivering_stale_bytes()
      throws Exception {
    createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "action",
                Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 5000)));

    ExecutorService client = Executors.newSingleThreadExecutor();
    try {
      Future<HttpResponse<byte[]>> held =
          client.submit(() -> post(signed(authorization("20260910120008"))));
      AHeldResponse.isHeldBy(delays);
      reset();

      HttpResponse<byte[]> answer = held.get(15, TimeUnit.SECONDS);

      assertThat(answer.statusCode()).isEqualTo(503);
      assertThat(new String(answer.body(), UTF_8))
          .contains("held this response for a scenario delay")
          .contains("was reset before the delay elapsed");
    } finally {
      client.shutdownNow();
      configure();
    }
  }

  @Test
  void resetting_the_test_run_clears_the_payments_it_stored() {
    String body = new String(post(signed(authorization("20260910120009"))).body(), UTF_8);
    String intRef = valueIn(body, "intref");
    assertThat(stateStatusOf(intRef)).isEqualTo(200);

    reset();

    assertThat(stateStatusOf(intRef)).isEqualTo(404);
  }

  @Test
  void the_trail_holds_the_request_and_the_answer_of_every_call_it_answered() {
    String body = new String(post(signed(authorization("20260910120010"))).body(), UTF_8);

    List<Map<String, Object>> records = auditRecords();

    assertThat(records).hasSize(2);
    assertThat(records.get(0)).containsEntry("direction", "INBOUND_REQUEST");
    assertThat(records.get(1))
        .containsEntry("direction", "OUTBOUND_RESPONSE")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("providerObjectId", valueIn(body, "intref"))
        .containsEntry("status", 200);
  }

  private void configure() {
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
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of(
                            "terminalId",
                            TERMINAL_ID,
                            "apiKey",
                            AzericardKeyFixture.MERCHANT_PUBLIC,
                            "apiSecret",
                            AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8)))))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/azericard/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private void selectPreset(String preset) {
    http.post()
        .uri("/__simulator/azericard/scenarios/presets")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "preset", preset))
        .retrieve()
        .toBodilessEntity();
  }

  private void createScenario(Map<String, Object> declared) {
    http.post()
        .uri("/__simulator/azericard/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(declared)
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> stateOf(String intRef) {
    return http.get()
        .uri("/__simulator/azericard/state/" + intRef + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private int stateStatusOf(String intRef) {
    return http.get()
        .uri("/__simulator/azericard/state/" + intRef + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }

  private List<Map<String, Object>> auditRecords() {
    return http.get()
        .uri("/__simulator/azericard/audit?origin=PROVIDER_API&testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> operationsOf(Map<String, Object> state) {
    return (List<Map<String, Object>>) state.get("operations");
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
    if (opening < 0) {
      return "";
    }
    return xml.substring(
        opening + element.length() + 2, xml.indexOf("</" + element + ">", opening));
  }

  private static Map<String, String> authorization(String order) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "0");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.AMOUNT, "11.48");
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
    form.put(AzericardFields.MERCH_URL, "https://merchant.example");
    form.put(AzericardFields.DESC, "IT Books. Qty: 2");
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
}
