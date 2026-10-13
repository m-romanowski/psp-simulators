package dev.marcinromanowski.pspsim.provider.tranzila;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class TranzilaApiIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-api-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-10-13T12:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "myterminal";

  private static final String APP_KEY = "tranzila-it-app-key";

  private static final String APP_SECRET = "tranzila-it-app-secret";

  private static final String A_VISA = "4580210000004245";

  private static final AtomicInteger NONCES = new AtomicInteger();

  @LocalServerPort private int port;

  private RestClient http;
  private Instant runClock = FIXED_NOW;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(List.of(anAccount()));
  }

  @AfterEach
  void unconfigureTheTerminal() {
    reset();
    configure(List.of());
  }

  @Test
  void a_backend_signing_real_headers_holds_forces_part_credits_part_and_reads_it_back() {
    HttpResponse<String> held =
        signed(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"verify\",\"verify_mode\":5,"
                + "\"card_number\":\""
                + A_VISA
                + "\",\"expire_month\":7,\"expire_year\":2030,\"cvv\":\"123\","
                + "\"items\":[{\"name\":\"a\",\"units_number\":2,\"unit_price\":50}]}");
    String j5 = valueIn(held.body(), "transaction_id");
    String token = textIn(held.body(), "token");

    HttpResponse<String> forced =
        signed(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"force\",\"reference_txn_id\":"
                + j5
                + ",\"card_number\":\""
                + token
                + "\",\"expire_month\":7,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":60}]}");
    String force = valueIn(forced.body(), "transaction_id");

    HttpResponse<String> credited =
        signed(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"credit\",\"reference_txn_id\":"
                + force
                + ",\"authorization_number\":\""
                + textIn(forced.body(), "auth_number")
                + "\",\"card_number\":\""
                + token
                + "\",\"expire_month\":7,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":20}]}");
    String credit = valueIn(credited.body(), "transaction_id");

    assertThat(held.statusCode()).isEqualTo(200);
    assertThat(held.body())
        .contains("\"error_code\":0")
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"amount\":100")
        .contains("\"tranmode\":\"V\"")
        .contains("\"card_mask\":\"458021xxxxxx4245\"")
        .contains("\"cvv\":\"***\"");
    assertThat(token).startsWith("t").endsWith("4245").hasSize(19);
    assertThat(forced.body())
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"amount\":60")
        .contains("\"tranmode\":\"F\"");
    assertThat(credited.body())
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"tranmode\":\"C\"");
    assertThat(stateOf(j5))
        .containsEntry("providerObjectId", j5)
        .containsEntry("normalizedStatus", "PARTIALLY_REFUNDED")
        .containsEntry("nativeStatus", "verify 5 777")
        .containsEntry("amount", "100")
        .containsEntry("currency", "ILS");
    assertThat(stateOf(force))
        .containsEntry("providerObjectId", force)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("nativeStatus", "force 000")
        .containsEntry("amount", "60");
    assertThat(stateOf(credit))
        .containsEntry("providerObjectId", credit)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("nativeStatus", "credit 000")
        .containsEntry("amount", "20");
    assertThat(objects()).hasSize(3);
  }

  @Test
  void a_debit_a_backend_cancels_reads_back_as_cancelled() {
    HttpResponse<String> charged = signed(aDebit(""));
    String debit = valueIn(charged.body(), "transaction_id");

    HttpResponse<String> cancelled =
        signed(
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"cancel\",\"reference_txn_id\":"
                + debit
                + ",\"authorization_number\":\""
                + textIn(charged.body(), "auth_number")
                + "\"}");

    assertThat(charged.body()).contains("\"processor_response_code\":\"000\"");
    assertThat(cancelled.statusCode()).isEqualTo(200);
    assertThat(cancelled.body())
        .contains("\"error_code\":0")
        .contains("\"txn_type\":\"cancel\"")
        .contains("\"tranmode\":\"D\"");
    assertThat(stateOf(debit)).containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void
      a_debit_repeated_after_a_timeout_under_one_dc_disable_answers_the_original_and_charges_once() {
    String body =
        aDebit(",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"order-7\"}]");
    HttpResponse<String> first = signed(body);
    HttpResponse<String> repeat = signed(body);

    assertThat(repeat.statusCode()).isEqualTo(200);
    assertThat(repeat.body())
        .contains("\"error_code\":0")
        .contains("This payment has already been made, please contact the business")
        .contains("\"transaction_id\":" + valueIn(first.body(), "transaction_id"));
    assertThat(objects()).hasSize(1);
    String inspector =
        http.get()
            .uri("/__simulator/tranzila/idempotency?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class);
    assertThat(inspector)
        .contains("order-7")
        .contains("FIRST_REQUEST")
        .contains("DUPLICATE_KEY_REFUSAL")
        .contains("DCdisable");
  }

  @Test
  void two_debits_sent_at_once_under_one_dc_disable_charge_the_card_once() throws Exception {
    String body =
        aDebit(",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"race-1\"}]");

    List<HttpResponse<String>> answers = atOnce(List.of(body, body, body, body));

    assertThat(answers).allSatisfy(answer -> assertThat(answer.statusCode()).isEqualTo(200));
    assertThat(answers)
        .filteredOn(answer -> answer.body().contains("\"message\":\"success\""))
        .hasSize(1);
    assertThat(answers)
        .filteredOn(answer -> answer.body().contains("already been made"))
        .hasSize(3);
    assertThat(objects()).hasSize(1);
  }

  @Test
  void a_credit_and_a_cancel_of_one_debit_sent_at_once_settle_it_once() throws Exception {
    for (int round = 0; round < 5; round++) {
      HttpResponse<String> charged = signed(aDebit(""));
      long debit = Long.parseLong(valueIn(charged.body(), "transaction_id"));
      String auth = textIn(charged.body(), "auth_number");
      String credit =
          "{\"terminal_name\":\"myterminal\",\"txn_type\":\"credit\",\"reference_txn_id\":"
              + debit
              + ",\"authorization_number\":\""
              + auth
              + "\",\"card_number\":\""
              + A_VISA
              + "\",\"expire_month\":7,\"expire_year\":2030,"
              + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":1}]}";
      String cancel =
          "{\"terminal_name\":\"myterminal\",\"txn_type\":\"cancel\",\"reference_txn_id\":"
              + debit
              + ",\"authorization_number\":\""
              + auth
              + "\"}";

      List<HttpResponse<String>> answers = atOnce(List.of(credit, cancel));

      assertThat(answers)
          .as("round %s: %s", round, answers.stream().map(HttpResponse::body).toList())
          .filteredOn(answer -> answer.body().contains("\"processor_response_code\":\"000\""))
          .hasSize(1);
      assertThat(answers)
          .as("round %s: %s", round, answers.stream().map(HttpResponse::body).toList())
          .filteredOn(
              answer ->
                  answer.body().contains("\"error_code\":23001")
                      || answer.body().contains("\"error_code\":20112"))
          .hasSize(1);
    }
  }

  @Test
  void a_j2_a_j5_and_a_debit_under_one_dc_disable_are_no_duplicates_and_a_second_debit_is() {
    String underTheKey = ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"kinds\"}]";
    String j2 =
        aDebit(underTheKey)
            .replace("{\"terminal", "{\"txn_type\":\"verify\",\"verify_mode\":2,\"terminal");
    String j5 =
        aDebit(underTheKey)
            .replace("{\"terminal", "{\"txn_type\":\"verify\",\"verify_mode\":5,\"terminal");

    HttpResponse<String> checked = signed(j2);
    HttpResponse<String> held = signed(j5);
    HttpResponse<String> charged = signed(aDebit(underTheKey));
    HttpResponse<String> chargedAgain = signed(aDebit(underTheKey));

    assertThat(checked.body()).contains("\"message\":\"success\"").contains("\"tranmode\":\"N\"");
    assertThat(held.body()).contains("\"message\":\"success\"").contains("\"tranmode\":\"V\"");
    assertThat(charged.body()).contains("\"message\":\"success\"").contains("\"tranmode\":\"A\"");
    assertThat(chargedAgain.body())
        .contains("already been made")
        .contains("\"transaction_id\":" + valueIn(charged.body(), "transaction_id"));
    assertThat(objects()).hasSize(3);
  }

  @Test
  void a_declined_original_under_a_dc_disable_blocks_no_retry() {
    declinedOnceThenApproved();
    String underTheKey =
        ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"declined\"}]";

    HttpResponse<String> declined = signed(aDebit(underTheKey));
    HttpResponse<String> retried = signed(aDebit(underTheKey));

    assertThat(declined.body()).contains("\"error_code\":4");
    assertThat(retried.body()).contains("\"message\":\"success\"");
    assertThat(objects()).hasSize(2);
  }

  @Test
  void an_approval_24_hours_old_on_the_run_clock_blocks_no_repeat() {
    String underTheKey = ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"a-day\"}]";
    HttpResponse<String> first = signed(aDebit(underTheKey));
    configureAt(FIXED_NOW.plusSeconds(24 * 60 * 60 - 1), List.of(anAccount()));
    HttpResponse<String> justInside = signed(aDebit(underTheKey));
    configureAt(FIXED_NOW.plusSeconds(24 * 60 * 60), List.of(anAccount()));
    HttpResponse<String> aDayLater = signed(aDebit(underTheKey));

    assertThat(first.body()).contains("\"message\":\"success\"");
    assertThat(justInside.body()).contains("already been made");
    assertThat(aDayLater.body()).contains("\"message\":\"success\"");
    assertThat(objects()).hasSize(2);
  }

  @Test
  void a_declined_first_request_lets_the_concurrent_second_under_its_dc_disable_through()
      throws Exception {
    declinedOnceThenApproved();
    String body =
        aDebit(",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"race-2\"}]");

    List<HttpResponse<String>> answers = atOnce(List.of(body, body));

    assertThat(answers)
        .as("%s", answers.stream().map(HttpResponse::body).toList())
        .filteredOn(answer -> answer.body().contains("\"error_code\":4,"))
        .hasSize(1);
    assertThat(answers)
        .filteredOn(answer -> answer.body().contains("\"message\":\"success\""))
        .hasSize(1);
    assertThat(objects()).hasSize(2);
  }

  @Test
  void a_wrong_access_token_and_a_reused_nonce_are_answered_401_20000() {
    String nonce = "nonce-" + NONCES.incrementAndGet();
    String requestTime = Long.toString(FIXED_NOW.getEpochSecond());
    HttpResponse<String> first = send(aDebit(""), requestTime, nonce, tokenFor(requestTime, nonce));
    HttpResponse<String> reused =
        send(aDebit(""), requestTime, nonce, tokenFor(requestTime, nonce));
    HttpResponse<String> wrong = send(aDebit(""), requestTime, nonce + "x", "00");

    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(reused.statusCode()).isEqualTo(401);
    assertThat(reused.body()).contains("\"error_code\":\"20000\"");
    assertThat(wrong.statusCode()).isEqualTo(401);
    assertThat(wrong.body()).contains("\"error_code\":\"20000\"");
  }

  @Test
  void tranzila_answers_its_v1_route_and_the_neighbours_on_v1_and_v2_still_answer_theirs() {
    HttpResponse<String> tranzila = signed(aDebit(""));
    HttpResponse<String> unknown = post("/v1/transaction/credit_card/nothing", "{}", Map.of());
    HttpResponse<String> stripe = post("/v1/payment_intents", "", Map.of());
    HttpResponse<String> autra = post("/v1/oauth/token", "", Map.of());
    HttpResponse<String> worldline = post("/v2/pspid-1/payments", "{}", Map.of());

    assertThat(tranzila.body()).contains("\"txn_type\":\"debit\"");
    assertThat(unknown.statusCode()).isEqualTo(404);
    assertThat(unknown.body())
        .isEqualTo("{\"error_code\":\"404\",\"message\":\"The specified resource was not found\"}");
    assertThat(stripe.statusCode()).isEqualTo(401);
    assertThat(stripe.body()).contains("\"error\"").doesNotContain("error_code");
    assertThat(autra.statusCode()).isNotEqualTo(404);
    assertThat(autra.body()).doesNotContain("error_code");
    assertThat(worldline.statusCode()).isEqualTo(403);
    assertThat(worldline.body()).contains("ACCESS_TO_MERCHANT_NOT_ALLOWED");
  }

  private void declinedOnceThenApproved() {
    scenario(
        Map.of(
            "testRunId", RUN.value(),
            "story", "declined-once",
            "keyedOn", "TEST_RUN",
            "whenState", "Started",
            "thenState", "declined",
            "match", Map.of("operation", "debit"),
            "action", Map.of("type", "TERMINAL_FAILURE", "providerCode", "004")));
    scenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            "declined-once",
            "keyedOn",
            "TEST_RUN",
            "whenState",
            "declined",
            "match",
            Map.of("operation", "debit"),
            "action",
            Map.of("type", "FIXED_SUCCESS")));
  }

  private void scenario(Map<String, Object> declared) {
    http.post()
        .uri("/__simulator/tranzila/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(declared)
        .retrieve()
        .toBodilessEntity();
  }

  private List<HttpResponse<String>> atOnce(List<String> bodies) throws Exception {
    ExecutorService senders = Executors.newFixedThreadPool(bodies.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<HttpResponse<String>>> sent = new ArrayList<>();
      for (String body : bodies) {
        sent.add(
            senders.submit(
                () -> {
                  start.await();
                  return signed(body);
                }));
      }
      start.countDown();
      List<HttpResponse<String>> answers = new ArrayList<>();
      for (Future<HttpResponse<String>> answer : sent) {
        answers.add(answer.get(30, TimeUnit.SECONDS));
      }
      return answers;
    } finally {
      senders.shutdownNow();
    }
  }

  private static String aDebit(String extra) {
    return "{\"terminal_name\":\"myterminal\",\"card_number\":\""
        + A_VISA
        + "\",\"expire_month\":7,\"expire_year\":2030,"
        + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":10.53}]"
        + extra
        + "}";
  }

  private HttpResponse<String> signed(String body) {
    String nonce = "nonce-" + NONCES.incrementAndGet();
    String requestTime = Long.toString(runClock.getEpochSecond());
    return send(body, requestTime, nonce, tokenFor(requestTime, nonce));
  }

  private static String tokenFor(String requestTime, String nonce) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec((APP_SECRET + requestTime + nonce).getBytes(UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(APP_KEY.getBytes(UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private HttpResponse<String> send(
      String body, String requestTime, String nonce, String accessToken) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-tranzila-api-app-key", APP_KEY);
    headers.put("X-tranzila-api-request-time", requestTime);
    headers.put("X-tranzila-api-nonce", nonce);
    headers.put("X-tranzila-api-access-token", accessToken);
    return post("/v1/transaction/credit_card/create", body, headers);
  }

  private HttpResponse<String> post(String path, String body, Map<String, String> headers) {
    try {
      HttpRequest.Builder built =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));
      for (Map.Entry<String, String> header : headers.entrySet()) {
        built = built.header(header.getKey(), header.getValue());
      }
      return wire.send(built.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static Map<String, Object> anAccount() {
    return Map.of(
        "accountId",
        ACCOUNT,
        "configuration",
        Map.of(
            TranzilaTerminal.TERMINAL_NAME_PROPERTY, TERMINAL,
            TranzilaTerminal.APP_KEY_PROPERTY, APP_KEY,
            TranzilaTerminal.APP_SECRET_PROPERTY, APP_SECRET));
  }

  private void configure(List<Map<String, Object>> accounts) {
    configureAt(FIXED_NOW, accounts);
  }

  private void configureAt(Instant now, List<Map<String, Object>> accounts) {
    runClock = now;
    http.put()
        .uri("/__simulator/tranzila/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", now.toString()),
                "accounts",
                accounts,
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/tranzila/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/tranzila/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> objects() {
    return http.get()
        .uri("/__simulator/tranzila/objects?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private static String valueIn(String json, String field) {
    String opening = "\"" + field + "\":";
    int at = json.indexOf(opening);
    int from = at + opening.length();
    int to = from;
    while (to < json.length() && Character.isDigit(json.charAt(to))) {
      to++;
    }
    return json.substring(from, to);
  }

  private static String textIn(String json, String field) {
    String opening = "\"" + field + "\":\"";
    int at = json.indexOf(opening);
    return json.substring(at + opening.length(), json.indexOf('"', at + opening.length()));
  }
}
