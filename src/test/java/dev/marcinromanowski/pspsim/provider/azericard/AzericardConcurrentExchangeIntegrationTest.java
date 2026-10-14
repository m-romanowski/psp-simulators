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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class AzericardConcurrentExchangeIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-concurrent-exchange-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String TERMINAL_ID = "70000051";
  private static final String MERCHANT = "merchant-1";
  private static final String CGI_LINK = "/cgi-bin/cgi_link";
  private static final String HELD = "11.48";
  private static final String MORE_THAN_HELD = "99.00";
  private static final String ORDER = "20260910085100";
  private static final int CONCURRENT = 16;
  private static final int ROUNDS = 5;

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

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
  void settlements_sent_at_once_under_one_payment_are_each_answered_and_each_recorded()
      throws Exception {
    String body = new String(post(signed(authorization())).body(), UTF_8);
    String rrn = valueIn(body, "rrn");
    String intRef = valueIn(body, "intref");
    Map<String, String> refused = signed(settlement(MORE_THAN_HELD, rrn, intRef));

    List<Integer> statuses = new ArrayList<>();
    for (int round = 0; round < ROUNDS; round++) {
      statuses.addAll(sentAtOnce(refused));
    }

    assertThat(statuses)
        .as("every settlement is answered by the gateway rather than failed by its own bookkeeping")
        .hasSize(CONCURRENT * ROUNDS)
        .containsOnly(200);
    assertThat(recordedUnder(AzericardKeyMechanisms.RRN, rrn)).isEqualTo(CONCURRENT * ROUNDS);
    assertThat(recordedUnder(AzericardKeyMechanisms.INT_REF, intRef))
        .isEqualTo(CONCURRENT * ROUNDS);
  }

  private List<Integer> sentAtOnce(Map<String, String> form) throws Exception {
    ExecutorService clients = Executors.newFixedThreadPool(CONCURRENT);
    CountDownLatch ready = new CountDownLatch(CONCURRENT);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<Integer>> sent = new ArrayList<>();
      for (int client = 0; client < CONCURRENT; client++) {
        sent.add(
            clients.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return post(form).statusCode();
                }));
      }
      ready.await(10, TimeUnit.SECONDS);
      go.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> answer : sent) {
        statuses.add(answer.get(30, TimeUnit.SECONDS));
      }
      return statuses;
    } finally {
      clients.shutdownNow();
    }
  }

  private int recordedUnder(String keyName, String keyValue) {
    return jdbc.sql(
            """
            select count(*) from azericard_keyed_exchange
            where test_run_id = ? and key_name = ? and key_value = ?
            """)
        .params(RUN.value(), keyName, keyValue)
        .query(Integer.class)
        .single();
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
    assertThat(opening).as("the answer names its %s", element).isNotNegative();
    return xml.substring(
        opening + element.length() + 2, xml.indexOf("</" + element + ">", opening));
  }

  private static Map<String, String> authorization() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "0");
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, HELD);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
    form.put(AzericardFields.MERCH_URL, "https://merchant.example");
    form.put(AzericardFields.DESC, "IT Books. Qty: 2");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> settlement(String amount, String rrn, String intRef) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "21");
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
