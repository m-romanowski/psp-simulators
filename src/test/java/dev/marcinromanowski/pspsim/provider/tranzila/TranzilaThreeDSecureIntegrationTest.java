package dev.marcinromanowski.pspsim.provider.tranzila;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class TranzilaThreeDSecureIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-3ds-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-10-14T09:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "secureterminal";

  private static final String APP_KEY = "tranzila-3ds-it-app-key";

  private static final String APP_SECRET = "tranzila-3ds-it-app-secret";

  private static final String FRICTIONLESS = "4907639999909022";

  private static final String FAILS = "4907639999990022";

  private static final String CHALLENGED = "4918914107195005";

  private static final String A_VISA = "4580210000004245";

  private static final String CREATE = "/v1/transaction/credit_card/create";

  private static final String COMPLETE = "/v1/transaction/credit_card/3ds/complete";

  private static final String PROCESS = "/tranzila/hf/process/ajax_process_transaction";

  private static final String A_SHOP = "https://shop.example/3ds/back";

  private static final AtomicInteger NONCES = new AtomicInteger();

  private static final Pattern THE_HASH =
      Pattern.compile(Pattern.quote(",\"response_hash\":\"") + "([0-9a-f]{64})\\W+$");

  @LocalServerPort private int port;

  private RestClient http;
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
  void the_frictionless_card_is_charged_with_its_3ds_statuses_and_the_trail_keeps_no_cryptogram() {
    HttpResponse<String> charged = signed(CREATE, aSecureDebit(FRICTIONLESS, ""));

    assertThat(charged.statusCode()).isEqualTo(200);
    assertThat(charged.body())
        .contains("\"error_code\":0,\"message\":\"success\"")
        .contains("\"processor_response_code\":\"000\"")
        .containsPattern(
            "\"t3ds_data\":\\{\"version\":\"1.0\",\"statusCode\":\"YA\","
                + "\"statusMessage\":\"Authenticated successfully.\",\"xid\":\"[A-Za-z0-9+/]{27}=\","
                + "\"cavv\":\"[A-Za-z0-9+/]{27}=\",\"eci\":\"05\"},\"original_request\":");
    String transaction = digitsIn(charged.body(), "transaction_id");
    assertThat(stateOf(transaction))
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("nativeStatus", "debit 000");
    String trail = audit();
    assertThat(trail)
        .contains("t3ds_data.cavv")
        .doesNotContain(textIn(charged.body(), "cavv"))
        .doesNotContain(textIn(charged.body(), "xid"))
        .doesNotContain(FRICTIONLESS);
  }

  @Test
  void the_failing_card_answers_900_and_records_a_failed_debit_unless_forced_on_failure() {
    HttpResponse<String> failed = signed(CREATE, aSecureDebit(FAILS, ""));
    HttpResponse<String> forced =
        signed(CREATE, aSecureDebit(FAILS, ",\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"Y\"}"));

    assertThat(failed.statusCode()).isEqualTo(200);
    assertThat(failed.body())
        .startsWith(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\","
                + "\"transaction_result\":{\"processor_response_code\":\"900\",");
    assertThat(stateOf(digitsIn(failed.body(), "transaction_id")))
        .containsEntry("normalizedStatus", "FAILED")
        .containsEntry("nativeStatus", "debit 900");
    assertThat(forced.body())
        .contains("\"processor_response_code\":\"000\"")
        .doesNotContain("t3ds_data");
  }

  @Test
  void a_payer_passes_the_challenge_is_posted_back_to_the_shop_and_the_shop_completes_it_once() {
    HttpResponse<String> created =
        signed(
            CREATE,
            aSecureDebit(
                CHALLENGED,
                ",\"cvv\":\"321\",\"auth_3ds_redirect\":{\"url\":\""
                    + A_SHOP
                    + "\",\"params\":[{\"key\":\"order\",\"value\":\"o-1\"}]}"));
    String trackId = textIn(created.body(), "track_id");
    String challengeUrl = textIn(created.body(), "challengeUrl");

    HttpResponse<String> shown = get(URI.create(challengeUrl).getPath());
    HttpResponse<String> passed = post(URI.create(challengeUrl).getPath(), Map.of("code", "555"));
    HttpResponse<String> unknown = signed(COMPLETE, aComplete("3ds_nothing"));
    Map<String, Object> beforeCompletion = stateOf(trackId);
    HttpResponse<String> completed = signed(COMPLETE, aComplete(trackId));
    HttpResponse<String> again = signed(COMPLETE, aComplete(trackId));

    assertThat(created.body())
        .matches(
            "\\{\"error_code\":0,\"message\":\"success\",\"challengeUrl\":\"http[^\"]*"
                + "/tranzila/3ds/challenge/3ds_[0-9a-f]{40}\",\"track_id\":\"3ds_[0-9a-f]{40}\"}");
    assertThat(shown.statusCode()).isEqualTo(200);
    assertThat(shown.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    assertThat(shown.body()).contains("Visa ending 5005").doesNotContain("491891");
    assertThat(passed.statusCode()).isEqualTo(200);
    assertThat(passed.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo(TranzilaReturnPage.WHAT_THIS_PAGE_PERMITS);
    assertThat(passed.body())
        .contains("<form method=\"post\" action=\"" + A_SHOP + "\">")
        .contains(
            "<input type=\"hidden\" name=\"order\" value=\"o-1\">"
                + "<input type=\"hidden\" name=\"track_id\" value=\""
                + trackId
                + "\">");
    assertThat(unknown.body()).contains("\"error_code\":900");
    assertThat(beforeCompletion)
        .containsEntry("normalizedStatus", "NOT_INITIALIZED")
        .containsEntry("nativeStatus", "challenge passed, not completed");
    assertThat(completed.statusCode()).isEqualTo(200);
    assertThat(completed.body())
        .startsWith("{\"error_code\":0,\"message\":\"Success\",\"transaction_result\":{")
        .containsPattern("\"transaction_id\":\"[1-9][0-9]{7}\"")
        .contains("\"card_mask\":\"491891******5005\"")
        .contains("\"t3ds_data\":{\"version\":\"1.0\"")
        .contains("\"card_number\":\"491891xxxxxx5005\"")
        .contains("\"cvv\":\"***\"")
        .contains("\"auth_3ds_redirect\":{\"url\":\"" + A_SHOP + "\"");
    assertThat(again.body())
        .isEqualTo(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\"}");
    String transaction = textIn(completed.body(), "transaction_id");
    assertThat(stateOf(trackId))
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("nativeStatus", "debit 000");
    assertThat(stateOf(transaction)).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(objects())
        .extracting(object -> object.get("providerObjectId"))
        .containsExactly(transaction, trackId);
    String trail = audit();
    assertThat(trail)
        .doesNotContain(challengeUrl)
        .doesNotContain(CHALLENGED)
        .doesNotContain("\"321\"")
        .doesNotContain(textIn(completed.body(), "cavv"));
  }

  @Test
  void a_payer_failing_the_challenge_with_no_redirect_reads_the_result_and_the_completion_fails() {
    HttpResponse<String> created =
        signed(
            CREATE,
            aSecureDebit(
                A_VISA,
                ",\"3ds_settings\":{\"force_challenge\":1,\"force_txn_on_3ds_fail\":\"N\","
                    + "\"auth_3ds_redirect\":[{\"key\":\"k\",\"value\":\"v\"}]}"));
    String trackId = textIn(created.body(), "track_id");
    String page = URI.create(textIn(created.body(), "challengeUrl")).getPath();

    HttpResponse<String> failed = post(page, Map.of("code", "123"));
    HttpResponse<String> twice = post(page, Map.of("code", "555"));
    HttpResponse<String> completed = signed(COMPLETE, aComplete(trackId));

    assertThat(failed.statusCode()).isEqualTo(200);
    assertThat(failed.body())
        .contains(TranzilaChallengePage.FAILED)
        .doesNotContain("<form")
        .doesNotContain("<script");
    assertThat(twice.statusCode()).isEqualTo(410);
    assertThat(completed.body())
        .startsWith(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\","
                + "\"transaction_result\":{\"processor_response_code\":\"900\",");
    assertThat(stateOf(trackId)).containsEntry("normalizedStatus", "FAILED");
  }

  @Test
  void a_completed_challenge_is_found_by_both_lookups_with_the_fields_and_items_its_create_sent() {
    HttpResponse<String> created =
        signed(
            CREATE,
            aSecureDebit(
                CHALLENGED,
                ",\"user_defined_fields\":[{\"name\":\"order\",\"value\":\"ORD-3DS\"}]"));
    String trackId = textIn(created.body(), "track_id");
    post(URI.create(textIn(created.body(), "challengeUrl")).getPath(), Map.of("code", "555"));
    String index = textIn(signed(COMPLETE, aComplete(trackId)).body(), "transaction_id");

    HttpResponse<String> found =
        signed(
            "/v1/transactions",
            "{\"terminal_name\":\"" + TERMINAL + "\",\"transaction_index\":" + index + "}");
    HttpResponse<String> reported =
        signed(
            "/tranzila/report/v1/transaction",
            "{\"terminal_name\":\""
                + TERMINAL
                + "\",\"transaction_index\":"
                + index
                + ",\"detailed\":\"Y\"}");

    assertThat(found.statusCode()).isEqualTo(200);
    assertThat(found.body())
        .contains("\"user_defined_1\":\"ORD-3DS\"")
        .contains("\"xid\":\"\",\"cavv\":\"\",\"eci\":\"\"");
    assertThat(reported.statusCode()).isEqualTo(200);
    assertThat(reported.body())
        .contains("\"user_defined_1\":\"ORD-3DS\"")
        .containsPattern("\"items\":\\[\\{[^]]*\"price\":1250");
  }

  @Test
  void two_completes_of_one_challenge_at_once_charge_once_and_refuse_the_other_900()
      throws Exception {
    HttpResponse<String> created = signed(CREATE, aSecureDebit(CHALLENGED, ""));
    String trackId = textIn(created.body(), "track_id");
    post(URI.create(textIn(created.body(), "challengeUrl")).getPath(), Map.of("code", "555"));
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<String> answered;
    try {
      List<Future<String>> racing = new ArrayList<>();
      for (int at = 0; at < 2; at++) {
        racing.add(
            pool.submit(
                () -> {
                  start.await();
                  return signed(COMPLETE, aComplete(trackId)).body();
                }));
      }
      start.countDown();
      answered = new ArrayList<>();
      for (Future<String> one : racing) {
        answered.add(one.get(30, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(answered).filteredOn(body -> body.contains("\"message\":\"Success\"")).hasSize(1);
    assertThat(answered)
        .filteredOn(
            body ->
                body.equals(
                    "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure"
                        + " validation\"}"))
        .hasSize(1);
    assertThat(objects()).hasSize(2);
  }

  @Test
  void a_pending_challenge_shows_no_identity_it_keeps_on_the_trail_the_inspector_or_its_state() {
    HttpResponse<String> created =
        signed(
            CREATE,
            aSecureDebit(
                CHALLENGED,
                ",\"card_holder_id\":\"012345678\",\"user_defined_fields\":["
                    + "{\"name\":\"Identity_Number\",\"value\":\"987654321\"},"
                    + "{\"name\":\"DCdisable\",\"value\":\"dc-3ds\"}]"));
    String trackId = textIn(created.body(), "track_id");
    String waiting =
        http.get()
            .uri("/__simulator/tranzila/state/" + trackId + "?testRunId=" + RUN.value())
            .retrieve()
            .body(String.class);
    post(URI.create(textIn(created.body(), "challengeUrl")).getPath(), Map.of("code", "555"));
    HttpResponse<String> completed = signed(COMPLETE, aComplete(trackId));

    assertThat(completed.body()).contains("987654321").contains("012345678");
    assertThat(List.of(waiting, audit(), inspector()))
        .allSatisfy(
            seen -> assertThat(seen).doesNotContain("987654321").doesNotContain("012345678"));
    assertThat(inspector()).contains("track_id").contains("dc-3ds");
  }

  @Test
  void the_complete_route_refuses_what_its_schema_does_not_publish_and_another_terminal() {
    HttpResponse<String> unknownField =
        signed(COMPLETE, "{\"track_id\":\"x\",\"terminal_name\":\"" + TERMINAL + "\",\"a\":1}");
    HttpResponse<String> anotherTerminal =
        signed(COMPLETE, "{\"track_id\":\"x\",\"terminal_name\":\"otherterminal\"}");
    HttpResponse<String> unsigned =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + COMPLETE))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(aComplete("x")))
                .build());

    assertThat(unknownField.statusCode()).isEqualTo(400);
    assertThat(unknownField.body()).startsWith("{\"error_code\":\"20004\"");
    assertThat(anotherTerminal.statusCode()).isEqualTo(401);
    assertThat(anotherTerminal.body()).startsWith("{\"error_code\":\"20002\"");
    assertThat(unsigned.statusCode()).isEqualTo(401);
    assertThat(unsigned.body()).startsWith("{\"error_code\":\"20000\"");
  }

  @Test
  void a_hosted_fields_challenge_is_framed_answered_and_continued_into_the_hashed_answer() {
    String thtk = aHandshakeLocking("25");

    HttpResponse<String> charged = post(PROCESS, aHostedCharge(thtk));
    String trackId = textIn(charged.body(), "track_id");
    String page = URI.create(textIn(charged.body(), "challengeUrl")).getPath();
    HttpResponse<String> shown = get(page);
    HttpResponse<String> answered = post(page, Map.of("code", "555"));
    HttpResponse<String> continued =
        post(PROCESS, Map.of("track_id", trackId, "supplier", TERMINAL));

    assertThat(charged.body())
        .matches(
            "\\{\"challengeUrl\":\"http[^\"]*/tranzila/3ds/challenge/3ds_[0-9a-f]{40}\","
                + "\"track_id\":\"3ds_[0-9a-f]{40}\"}");
    assertThat(shown.headers().firstValue("Content-Security-Policy").orElseThrow())
        .doesNotContain("frame-ancestors");
    assertThat(answered.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo(TranzilaChallengeAnsweredPage.WHAT_THIS_PAGE_PERMITS);
    assertThat(answered.body()).contains("data-track-id=\"" + trackId + "\"");
    assertThat(continued.body())
        .startsWith(
            "{\"errors\":null,\"transaction_response\":{\"success\":true,\"error\":null,"
                + "\"processor_response_code\":\"000\",")
        .contains("\"credit_card_last_4_digits\":\"5005\"");
    assertThat(theHashIsTheSecretsHmacOf(continued.body())).isTrue();
    assertThat(stateOf(trackId)).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(audit()).doesNotContain(CHALLENGED).doesNotContain("491891");
  }

  private boolean theHashIsTheSecretsHmacOf(String body) {
    Matcher hash = THE_HASH.matcher(body);
    if (!hash.find()) {
      throw new AssertionError("no response_hash closes " + body);
    }
    return hash.group(1).equals(hmac(APP_SECRET, THE_HASH.matcher(body).replaceFirst("}")));
  }

  private static String aSecureDebit(String card, String more) {
    return "{\"terminal_name\":\""
        + TERMINAL
        + "\",\"activate_3ds\":\"Y\",\"txn_type\":\"debit\",\"card_number\":\""
        + card
        + "\",\"expire_month\":7,\"expire_year\":2030,"
        + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":12.5}]"
        + more
        + "}";
  }

  private static String aComplete(String trackId) {
    return "{\"track_id\":\"" + trackId + "\",\"terminal_name\":\"" + TERMINAL + "\"}";
  }

  private static Map<String, String> aHostedCharge(String thtk) {
    Map<String, String> charge = new LinkedHashMap<>();
    charge.put("terminal_name", TERMINAL);
    charge.put("thtk", thtk);
    charge.put("amount", "25");
    charge.put("credit_card_number", CHALLENGED);
    charge.put("expiry_month", "12");
    charge.put("expiry_year", "2030");
    return charge;
  }

  private String aHandshakeLocking(String sum) {
    HttpResponse<String> created =
        signed(
            "/v2/handshake/create", "{\"terminal_name\":\"" + TERMINAL + "\",\"sum\":" + sum + "}");
    return textIn(created.body(), "thtk");
  }

  private static String textIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no " + field + " in " + json);
    }
    return found.group(1);
  }

  private static String digitsIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":([0-9]+)").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no " + field + " in " + json);
    }
    return found.group(1);
  }

  private static String hmac(String key, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private HttpResponse<String> signed(String path, String body) {
    String nonce = "3ds-nonce-" + NONCES.incrementAndGet();
    String requestTime = Long.toString(FIXED_NOW.getEpochSecond());
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-tranzila-api-app-key", APP_KEY)
            .header("X-tranzila-api-request-time", requestTime)
            .header("X-tranzila-api-nonce", nonce)
            .header("X-tranzila-api-access-token", hmac(APP_SECRET + requestTime + nonce, APP_KEY))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private HttpResponse<String> post(String path, Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)))
            .build());
  }

  private HttpResponse<String> get(String path) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
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
    http.put()
        .uri("/__simulator/tranzila/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
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

  private String inspector() {
    return http.get()
        .uri("/__simulator/tranzila/idempotency?testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }

  private String audit() {
    return http.get()
        .uri("/__simulator/tranzila/audit?testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }
}
