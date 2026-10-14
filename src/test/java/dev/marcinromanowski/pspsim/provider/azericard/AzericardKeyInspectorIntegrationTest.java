package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.InspectorScreen;
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
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class AzericardKeyInspectorIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-key-inspector-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "70000012";
  private static final String MERCHANT = "merchant-1";
  private static final String CGI_LINK = "/cgi-bin/cgi_link";
  private static final String INSPECTOR = "/__simulator/azericard/idempotency";
  private static final String HELD = "11.48";

  private static final String ORDER = "20260910084800";
  private static final String ANOTHER_ORDER_ENDING_THE_SAME = "20270101084800";
  private static final String TRACE = "084800";

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
  void a_duplicate_driven_over_the_wire_reads_back_as_a_refusal_the_control_api_can_be_asked_for() {
    post(signed(authorization(ORDER)));

    post(signed(authorization(ANOTHER_ORDER_ENDING_THE_SAME)));

    Map<String, Object> exchange = theOnlyExchange();
    assertThat(exchange).containsEntry("keyName", AzericardKeyMechanisms.ORDER);
    assertThat(exchange).containsEntry("keyValue", TRACE);
    assertThat(exchange).containsEntry("role", "duplicate-detection key");
    assertThat(exchange).containsEntry("accountId", MERCHANT);
    List<Map<String, Object>> requests = requestsOf(exchange);
    assertThat(requests).hasSize(2);
    assertThat(requests.get(0)).containsEntry("verdict", "FIRST_REQUEST");
    assertThat(requests.get(1)).containsEntry("verdict", "DUPLICATE_KEY_REFUSAL");
    assertThat(String.valueOf(requests.get(1).get("body"))).contains("\"action\":[\"1\"]");
    assertThat(replayTimestampsOf(exchange)).isEmpty();
  }

  @Test
  void the_screen_a_tester_opens_shows_that_duplicate_rather_than_an_empty_exchange_list() {
    post(signed(authorization(ORDER)));
    post(signed(authorization(ANOTHER_ORDER_ENDING_THE_SAME)));

    String screen = screen();

    assertThat(screen)
        .as("the exchange row names the key and the value the duplicate was refused under")
        .containsPattern(
            "<span class=\"id\">"
                + AzericardKeyMechanisms.ORDER
                + "</span>\\s*<span class=\"id\">"
                + TRACE
                + "</span>");
    assertThat(screen).contains("DUPLICATE_KEY_REFUSAL");
    assertThat(screen).doesNotContain(InspectorScreen.NOTHING_HAS_BEEN_SENT_UNDER_A_KEY);
  }

  @Test
  void the_screen_says_this_key_replayed_no_answer_where_a_replay_response_would_be() {
    post(signed(authorization(ORDER)));
    post(signed(authorization(ANOTHER_ORDER_ENDING_THE_SAME)));

    String screen = screen();

    assertThat(screen).contains("original response");
    assertThat(screen).contains("replay response");
    assertThat(screen).contains(InspectorScreen.NO_REPLAY_WAS_ANSWERED_UNDER_THIS_KEY);
  }

  @Test
  void the_screen_links_the_first_request_to_the_payment_that_request_created() {
    String body = new String(post(signed(authorization(ORDER))).body(), UTF_8);

    String screen = screen();

    assertThat(screen).contains(valueIn(body, "intref"));
    assertThat(screen).contains("it names by rrn");
    assertThat(screen).contains(valueIn(body, "rrn"));
  }

  @Test
  void a_settlement_of_that_payment_reads_back_under_both_correlation_keys_the_provider_declares() {
    String body = new String(post(signed(authorization(ORDER))).body(), UTF_8);
    String rrn = valueIn(body, "rrn");
    String intRef = valueIn(body, "intref");

    post(signed(settlement("21", HELD, rrn, intRef)));

    assertThat(exchangeUnder(AzericardKeyMechanisms.RRN, rrn))
        .containsEntry("role", "correlation key");
    assertThat(exchangeUnder(AzericardKeyMechanisms.INT_REF, intRef))
        .containsEntry("role", "correlation key");
  }

  @Test
  void resetting_the_test_run_leaves_the_control_read_with_no_exchange_of_it_to_answer_with() {
    post(signed(authorization(ORDER)));
    assertThat(exchanges()).hasSize(1);

    reset();

    assertThat(exchanges()).isEmpty();
  }

  private String screen() {
    return String.valueOf(
        http.get()
            .uri("/ui/idempotency?provider=azericard&testRunId=" + RUN.value())
            .retrieve()
            .body(String.class));
  }

  private Map<String, Object> theOnlyExchange() {
    List<Map<String, Object>> found = exchanges();
    assertThat(found).hasSize(1);
    return found.get(0);
  }

  private Map<String, Object> exchangeUnder(String keyName, String keyValue) {
    return exchanges().stream()
        .filter(row -> keyName.equals(row.get("keyName")) && keyValue.equals(row.get("keyValue")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no exchange under " + keyName + " " + keyValue));
  }

  private List<Map<String, Object>> exchanges() {
    Map<String, Object> answered =
        Objects.requireNonNull(
            http.get()
                .uri(INSPECTOR + "?testRunId=" + RUN.value())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
    return listOf(answered, "exchanges");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listOf(Map<String, Object> answered, String field) {
    return (List<Map<String, Object>>) Objects.requireNonNull(answered.get(field));
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> requestsOf(Map<String, Object> exchange) {
    return (List<Map<String, Object>>) Objects.requireNonNull(exchange.get("requests"));
  }

  @SuppressWarnings("unchecked")
  private static List<String> replayTimestampsOf(Map<String, Object> exchange) {
    return (List<String>) Objects.requireNonNull(exchange.get("replayTimestamps"));
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
    form.put(AzericardFields.DESC, "IT Books. Qty: 2");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> settlement(
      String trtype, String amount, String rrn, String intRef) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, amount);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.RRN, rrn);
    form.put(AzericardFields.INT_REF, intRef);
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
