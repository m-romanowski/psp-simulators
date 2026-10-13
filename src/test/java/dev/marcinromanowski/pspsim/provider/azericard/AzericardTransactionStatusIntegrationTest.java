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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class AzericardTransactionStatusIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-status-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "70000004";
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
    configure(FIXED_NOW);
  }

  @AfterEach
  void unconfigureTheTerminal() {
    reset();
    unconfigure();
  }

  @Test
  void answers_a_status_inquiry_as_json_naming_the_payment_the_order_and_terminal_address() {
    String order = "20260910150001";
    String intRef = anApprovedHold(order);

    HttpResponse<byte[]> response = post(signed(statusInquiry(order, FIXED_NOW)));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(theMediaTypeOf(response)).isEqualTo("application/json;charset=UTF-8");
    String body = new String(response.body(), UTF_8);
    assertThat(body)
        .contains("\"intRef\":\"" + intRef + "\"")
        .contains("\"order\":\"" + order + "\"")
        .contains("\"amount\":\"" + HELD + "\"")
        .contains("\"actionCode\":\"0\"")
        .contains("\"tr_type\":\"0\"")
        .contains("\"signature\":\"");
  }

  @Test
  void refuses_an_inquiry_made_more_than_a_day_after_the_transaction_it_asks_about() {
    String order = "20260910150002";
    anApprovedHold(order);
    Instant aDayLater = FIXED_NOW.plus(AzericardTimestamp.STATUS_WINDOW).plusSeconds(60);
    configure(aDayLater);

    HttpResponse<byte[]> response = post(signed(statusInquiry(order, aDayLater)));

    assertThat(new String(response.body(), UTF_8))
        .contains("\"responseCode\":\"" + AzericardOutcomes.RC_OUTSIDE_THE_STATUS_WINDOW + "\"");
  }

  @Test
  void answers_an_inquiry_about_an_order_the_terminal_never_sent_as_a_context_mismatch() {
    HttpResponse<byte[]> response = post(signed(statusInquiry("20260910150003", FIXED_NOW)));

    assertThat(new String(response.body(), UTF_8))
        .contains("\"responseCode\":\"-24\"")
        .contains("\"statusMsg\":\"Transaction context mismatch\"")
        .doesNotContain("\"order\"");
  }

  @Test
  void answers_a_badly_signed_inquiry_in_json_rather_than_in_the_xml_the_other_calls_get() {
    Map<String, String> form = statusInquiry("20260910150004", FIXED_NOW);
    form.put(AzericardFields.P_SIGN, "00");

    HttpResponse<byte[]> response = post(form);

    assertThat(theMediaTypeOf(response)).isEqualTo("application/json;charset=UTF-8");
    assertThat(new String(response.body(), UTF_8))
        .contains("\"actionCode\":\"3\"")
        .contains("\"responseCode\":\"-17\"")
        .contains("\"statusMsg\":\"Access denied\"");
  }

  private static String theMediaTypeOf(HttpResponse<byte[]> response) {
    return response.headers().firstValue("Content-Type").orElseThrow().replace(" ", "");
  }

  private String anApprovedHold(String order) {
    String body = new String(post(signed(authorization(order))).body(), UTF_8);
    assertThat(body).contains("<action>0</action>");
    return valueIn(body, "intref");
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
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> statusInquiry(String order, Instant at) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "90");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.TRAN_TRTYPE, "0");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(at));
    form.put(AzericardFields.NONCE, "7CFB4C2512EEEC72");
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

  private void configure(Instant fixedNow) {
    http.put()
        .uri("/__simulator/azericard/config")
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
                            AzericardTerminal.TERMINAL_PROPERTY,
                            TERMINAL_ID,
                            AzericardTerminal.MERCHANT_KEY_PROPERTY,
                            AzericardKeyFixture.MERCHANT_PUBLIC,
                            AzericardTerminal.GATEWAY_KEY_PROPERTY,
                            AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8)))))
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
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }
}
