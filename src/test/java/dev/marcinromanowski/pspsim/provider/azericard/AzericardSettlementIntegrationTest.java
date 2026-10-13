package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
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
import org.springframework.web.client.RestClient;

class AzericardSettlementIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-settlement-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "70000002";
  private static final String MERCHANT = "merchant-1";
  private static final String CGI_LINK = "/cgi-bin/cgi_link";
  private static final String HELD = "11.48";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(null);
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
  void completes_a_hold_over_the_wire_and_the_control_api_reports_the_capture_it_recorded() {
    Map<String, String> hold = heldPaymentOf("20260910130001");

    HttpResponse<byte[]> response = post(signed(settlement(hold, "21", HELD)));

    assertThat(response.statusCode()).isEqualTo(200);
    String body = new String(response.body(), UTF_8);
    assertThat(body).contains("<action>0</action>").contains("<rc>00</rc>");
    assertThat(valueIn(body, "intref")).isEqualTo(hold.get(AzericardFields.INT_REF));
    assertThat(valueIn(body, "rrn")).isEqualTo(hold.get(AzericardFields.RRN));

    Map<String, Object> state = stateOf(hold.get(AzericardFields.INT_REF));
    assertThat(state).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(operationsOf(state))
        .hasSize(2)
        .last()
        .satisfies(
            operation ->
                assertThat(operation)
                    .containsEntry("kind", "CAPTURE")
                    .containsEntry("amount", HELD));
  }

  @Test
  void reverses_a_completed_payment_over_the_wire_and_the_control_api_reports_it_refunded() {
    Map<String, String> hold = heldPaymentOf("20260910130002");
    post(signed(settlement(hold, "21", HELD)));

    HttpResponse<byte[]> response = post(signed(settlement(hold, "22", HELD)));

    assertThat(new String(response.body(), UTF_8)).contains("<action>0</action>");
    Map<String, Object> state = stateOf(hold.get(AzericardFields.INT_REF));
    assertThat(state).containsEntry("normalizedStatus", "REFUNDED");
    assertThat(operationsOf(state))
        .hasSize(3)
        .last()
        .satisfies(operation -> assertThat(operation).containsEntry("kind", "REFUND"));
  }

  @Test
  void releases_an_uncompleted_hold_over_the_wire_and_the_control_api_reports_it_cancelled() {
    Map<String, String> hold = heldPaymentOf("20260910130005");

    HttpResponse<byte[]> response = post(signed(settlement(hold, "22", HELD)));

    assertThat(new String(response.body(), UTF_8)).contains("<action>0</action>");
    Map<String, Object> state = stateOf(hold.get(AzericardFields.INT_REF));
    assertThat(state).containsEntry("normalizedStatus", "CANCELLED");
    assertThat(operationsOf(state))
        .singleElement()
        .satisfies(operation -> assertThat(operation).containsEntry("kind", "AUTHORIZATION"));
    assertThat(balancesOf(state))
        .containsEntry("capturableRemainder", "0")
        .containsEntry("refundableRemainder", "0");
  }

  @Test
  void refuses_a_completion_of_a_hold_a_reversal_released_over_the_wire() {
    Map<String, String> hold = heldPaymentOf("20260910130006");
    post(signed(settlement(hold, "24", HELD)));

    String body = new String(post(signed(settlement(hold, "21", HELD))).body(), UTF_8);

    assertThat(body)
        .contains("<action>3</action>")
        .contains("<rc>" + AzericardOutcomes.RC_NOT_ADMITTED + "</rc>");
    assertThat(stateOf(hold.get(AzericardFields.INT_REF)))
        .containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void refuses_a_completion_that_addresses_a_payment_this_gateway_never_answered() {
    Map<String, String> hold = heldPaymentOf("20260910130003");
    Map<String, String> unknown = new LinkedHashMap<>(hold);
    unknown.put(AzericardFields.INT_REF, "AAAAAAAAAAAAAAAA");

    String body = new String(post(signed(settlement(unknown, "21", HELD))).body(), UTF_8);

    assertThat(body)
        .contains("<action>3</action>")
        .contains("<rc>" + AzericardOutcomes.RC_NO_SUCH_TRANSACTION + "</rc>")
        .contains("<intref>AAAAAAAAAAAAAAAA</intref>");
    assertThat(stateOf(hold.get(AzericardFields.INT_REF)))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
  }

  @Test
  void a_terminal_configured_to_leave_the_remainder_reports_a_partly_captured_payment() {
    configure(AzericardPartialCompletion.LEAVES_THE_REMAINDER_CAPTURABLE.name());
    Map<String, String> hold = heldPaymentOf("20260910130004");

    post(signed(settlement(hold, "21", "5.00")));

    Map<String, Object> state = stateOf(hold.get(AzericardFields.INT_REF));
    assertThat(state).containsEntry("normalizedStatus", "PARTIALLY_CAPTURED");
    assertThat(balancesOf(state))
        .containsEntry("capturableRemainder", "6.48")
        .containsEntry("refundableRemainder", "5.00");
  }

  private Map<String, String> heldPaymentOf(String order) {
    String body = new String(post(signed(authorization(order))).body(), UTF_8);
    Map<String, String> addressed = new LinkedHashMap<>();
    addressed.put(AzericardFields.ORDER, order);
    addressed.put(AzericardFields.RRN, valueIn(body, "rrn"));
    addressed.put(AzericardFields.INT_REF, valueIn(body, "intref"));
    return addressed;
  }

  private void configure(String partialCompletion) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("terminalId", TERMINAL_ID);
    configuration.put("apiKey", AzericardKeyFixture.MERCHANT_PUBLIC);
    configuration.put("apiSecret", AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8);
    if (partialCompletion != null) {
      configuration.put(AzericardPartialCompletion.SETTING, partialCompletion);
    }
    Map<String, Object> account = Map.of("accountId", MERCHANT, "configuration", configuration);
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
                List.of(account)))
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

  private Map<String, Object> stateOf(String intRef) {
    return http.get()
        .uri("/__simulator/azericard/state/" + intRef + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> operationsOf(Map<String, Object> state) {
    return (List<Map<String, Object>>) state.get("operations");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> balancesOf(Map<String, Object> state) {
    return (Map<String, Object>) state.get("derivedBalances");
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
    form.put(AzericardFields.AMOUNT, HELD);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
    form.put(AzericardFields.MERCH_URL, "https://merchant.example");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> settlement(
      Map<String, String> addressed, String trtype, String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, addressed.get(AzericardFields.ORDER));
    form.put(AzericardFields.AMOUNT, amount);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.RRN, addressed.get(AzericardFields.RRN));
    form.put(AzericardFields.INT_REF, addressed.get(AzericardFields.INT_REF));
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
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
