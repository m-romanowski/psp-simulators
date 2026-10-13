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

class AzericardTokenRouteIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-token-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "70000003";
  private static final String MERCHANT = "merchant-1";
  private static final String TOKEN_LINK = "/token/cgi_link";
  private static final String MINIMAL = "0.01";

  @LocalServerPort private int port;

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
    unconfigure();
  }

  @Test
  void stores_a_card_over_the_wire_and_the_control_api_reads_the_token_as_an_object_of_its_own() {
    Map<String, String> storage = cardStorage("20260910140001");

    HttpResponse<byte[]> response = post(signed(storage));

    assertThat(response.statusCode()).isEqualTo(200);
    String body = new String(response.body(), UTF_8);
    assertThat(body).contains("<action>0</action>").contains("<rc>00</rc>");

    String intRef = valueIn(body, "intref");
    Map<String, Object> payment = stateOf(intRef);
    String token = (String) relatedOf(payment).get("token");
    assertThat(token).hasSize(AzericardStoredCard.TOKEN_LENGTH);
    assertThat(operationsOf(payment))
        .singleElement()
        .satisfies(operation -> assertThat(operation).containsEntry("createdObjectId", token));

    Map<String, Object> card = stateOf(token);
    assertThat(card)
        .containsEntry("providerObjectId", token)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("nativeStatus", "0/00");
    assertThat(relatedOf(card)).containsEntry("storedBy", intRef);
  }

  @Test
  void pays_with_the_stored_card_over_the_wire_and_records_the_token_it_paid_on() {
    String token = aStoredCardFor("20260910140002");

    HttpResponse<byte[]> response = post(signed(storedCardPayment("20260910140003", token)));

    String body = new String(response.body(), UTF_8);
    assertThat(body).contains("<action>0</action>");
    assertThat(relatedOf(stateOf(valueIn(body, "intref")))).containsEntry("token", token);
  }

  @Test
  void refuses_a_payment_on_a_token_this_test_run_never_stored() {
    HttpResponse<byte[]> response =
        post(signed(storedCardPayment("20260910140004", "A".repeat(28))));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(new String(response.body(), UTF_8))
        .contains("<action>3</action>")
        .contains("<rc>" + AzericardOutcomes.RC_NO_SUCH_TOKEN + "</rc>");
  }

  @Test
  void a_reset_clears_the_cards_the_test_run_stored() {
    String token = aStoredCardFor("20260910140005");

    reset();

    assertThat(missingStateOf(token)).isEqualTo(404);
  }

  private String aStoredCardFor(String order) {
    String body = new String(post(signed(cardStorage(order))).body(), UTF_8);
    return (String) relatedOf(stateOf(valueIn(body, "intref"))).get("token");
  }

  private static Map<String, String> authorization(String order) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.AMOUNT, MINIMAL);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> cardStorage(String order) {
    Map<String, String> form = authorization(order);
    form.put(AzericardFields.TOKEN_ACTION, AzericardTokenFlow.REGISTER);
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.STORING_A_CARD);
    return form;
  }

  private static Map<String, String> storedCardPayment(String order, String token) {
    Map<String, String> form = authorization(order);
    form.put(AzericardFields.TOKEN, token);
    form.keySet().removeAll(AzericardFields.CARD_DATA);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    Map<String, List<String>> fields = new LinkedHashMap<>();
    signed.forEach((name, value) -> fields.put(name, List.of(value)));
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.TOKEN_CGI_LINK, fields).macSource()));
    return signed;
  }

  private HttpResponse<byte[]> post(Map<String, String> form) {
    try {
      return wire.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + TOKEN_LINK))
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

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/azericard/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private int missingStateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/azericard/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> relatedOf(Map<String, Object> state) {
    return (Map<String, Object>) state.get("relatedObjects");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> operationsOf(Map<String, Object> state) {
    return (List<Map<String, Object>>) state.get("operations");
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
