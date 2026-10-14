package dev.marcinromanowski.pspsim.provider.tranzila;

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
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

class TranzilaHostedFieldsIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-hf-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-10-14T09:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "hfterminal";

  private static final String APP_KEY = "tranzila-hf-it-app-key";

  private static final String APP_SECRET = "tranzila-hf-it-app-secret";

  private static final String THE_PUBLISHED_CARD = "4907639999909022";

  private static final String A_CVV = "737";

  private static final String PROCESS = "/tranzila/hf/process/ajax_process_transaction";

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
  void a_shop_charges_holds_and_checks_a_typed_card_and_forces_the_hold_with_its_token() {
    String thtk = aHandshakeLocking("42");

    HttpResponse<String> debit = post(PROCESS, aCharge(thtk, "42", "A", "contact", "Dana Levi"));
    HttpResponse<String> hold = post(PROCESS, aCharge(thtk, "42", "V", "tokenize", "true"));
    HttpResponse<String> check = post(PROCESS, aCharge(thtk, "42", "N"));
    String token = textIn(hold.body(), "token");
    String held = textIn(hold.body(), "transaction_id");
    HttpResponse<String> forced =
        signed(
            "/v1/transaction/credit_card/create",
            "{\"terminal_name\":\""
                + TERMINAL
                + "\",\"txn_type\":\"force\",\"reference_txn_id\":"
                + held
                + ",\"card_number\":\""
                + token
                + "\",\"expire_month\":12,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":42}]}");

    assertThat(debit.statusCode()).isEqualTo(200);
    assertThat(debit.headers().firstValue("Content-Type").orElseThrow())
        .isEqualTo("application/json");
    assertThat(debit.body())
        .matches(
            "\\{\"errors\":null,\"transaction_response\":\\{\"success\":true,\"error\":null,"
                + "\"processor_response_code\":\"000\",\"transaction_id\":\"[1-9][0-9]{7}\","
                + "\"auth_number\":\"[0-9]{7}\",\"amount\":\"42.00\",\"currency_code\":1,"
                + "\"credit_card_last_4_digits\":\"9022\",\"expiry_month\":\"12\","
                + "\"expiry_year\":\"30\",\"user_form_data\":\\{\"contact\":\"Dana Levi\"},"
                + "\"card_type\":\"2\",\"card_mask\":\"\\*{12}9022\","
                + "\"card_locality\":\"domestic\",\"txn_type\":\"debit\",\"tranmode\":\"A\","
                + "\"card_type_name\":\"Visa\",\"cvv_status\":\"1\",\"id_status\":\"0\","
                + "\"card_issuer\":\"1\",\"payment_plan\":1,\"total_installments_number\":null},"
                + "\"response_hash\":\"[0-9a-f]{64}\"}");
    assertThat(theHashIsTheSecretsHmacOf(debit.body())).isTrue();
    assertThat(theHashIsTheSecretsHmacOf(hold.body())).isTrue();
    assertThat(hold.body())
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"V\"");
    assertThat(token).matches("t[0-9a-f]{14}9022");
    assertThat(check.body())
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"N\"")
        .doesNotContain("\"token\"");
    assertThat(forced.statusCode()).isEqualTo(200);
    assertThat(forced.body())
        .contains("\"error_code\":0")
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"txn_type\":\"force\"");
    assertThat(stateOf(held)).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(stateOf(textIn(debit.body(), "transaction_id")))
        .containsEntry("normalizedStatus", "SUCCEEDED");
    String everything = debit.body() + hold.body() + check.body() + forced.body() + audit();
    assertThat(everything).doesNotContain(THE_PUBLISHED_CARD).doesNotContain("490763");
    assertThat(audit())
        .contains("/tranzila/hf/process/ajax_process_transaction")
        .contains("[redacted]")
        .doesNotContain("\"" + A_CVV + "\"")
        .doesNotContain(token);
  }

  @Test
  void a_charge_naming_its_notify_url_posts_the_transaction_data_there_as_the_page_does()
      throws InterruptedException {
    try (LocalMerchantServer merchant = new LocalMerchantServer()) {
      configure(List.of(anAccount()), List.of(Map.of("accountId", ACCOUNT, "url", merchant.url())));
      String thtk = aHandshakeLocking("42");

      HttpResponse<String> charged =
          post(
              PROCESS,
              aCharge(
                  thtk,
                  "42",
                  "V",
                  "notify_url",
                  merchant.url(),
                  "tokenize",
                  "true",
                  "identity_number",
                  "987654321"));
      String transaction = textIn(charged.body(), "transaction_id");
      LocalMerchantServer.ReceivedCallback notified = theNotifyNaming(merchant, transaction);

      assertThat(notified.contentType()).isEqualTo("application/x-www-form-urlencoded");
      assertThat(notified.fieldOrder())
          .containsExactly(
              "supplier",
              "sum",
              "currency",
              "Response",
              "notify_url",
              "identity_number",
              "transaction_id",
              "index",
              "ccno",
              "Responsecv",
              "Responseid",
              "cardtype",
              "cardissuer",
              "cardacquirer",
              "TranzilaTK",
              "txn_type",
              "tranmode",
              "ConfirmationCode");
      assertThat(notified.fields())
          .containsEntry("supplier", TERMINAL)
          .containsEntry("sum", "42")
          .containsEntry("Response", "777")
          .containsEntry("ccno", "9022")
          .containsEntry("TranzilaTK", textIn(charged.body(), "token"))
          .containsEntry("txn_type", "verify")
          .containsEntry("tranmode", "V");
      assertThat(notified.body()).doesNotContain(THE_PUBLISHED_CARD).doesNotContain(A_CVV + "&");
      String deliveries = deliveries();
      assertThat(deliveries)
          .contains("[redacted]")
          .doesNotContain("987654321")
          .doesNotContain(textIn(charged.body(), "token"));
    }
  }

  private LocalMerchantServer.ReceivedCallback theNotifyNaming(
      LocalMerchantServer merchant, String transactionId) throws InterruptedException {
    for (int attempt = 0; attempt < 100; attempt++) {
      for (LocalMerchantServer.ReceivedCallback received : merchant.received()) {
        if (transactionId.equals(received.fields().get("transaction_id"))) {
          return received;
        }
      }
      Thread.sleep(100);
    }
    throw new AssertionError("no notify named transaction " + transactionId);
  }

  private String deliveries() {
    return http.get()
        .uri("/__simulator/tranzila/webhooks?testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }

  @Test
  void a_charge_without_a_thtk_is_refused_and_charges_nothing() {
    Map<String, String> charge = aCharge("unused", "10", "A");
    charge.remove("thtk");

    HttpResponse<String> refused = post(PROCESS, charge);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(withoutTheHash(refused.body()))
        .isEqualTo(
            "{\"errors\":[{\"code\":10000,\"param\":\"thtk\",\"message\":\"MANDATORY KEY IS"
                + " MISSING\"}],\"transaction_response\":null}");
    assertThat(theHashIsTheSecretsHmacOf(refused.body())).isTrue();
    assertThat(objects()).isEmpty();
  }

  @Test
  void an_amount_the_thtk_does_not_lock_is_an_illegal_operation() {
    String thtk = aHandshakeLocking("10");

    HttpResponse<String> refused = post(PROCESS, aCharge(thtk, "10.01", "A"));

    assertThat(withoutTheHash(refused.body()))
        .isEqualTo(
            "{\"errors\":[{\"code\":912791,\"param\":\"thtk\",\"message\":\"Illegal Operation"
                + " 912791\"}],\"transaction_response\":null}");
    assertThat(objects()).isEmpty();
  }

  @Test
  void a_tran_mode_a_payment_plan_and_a_card_number_out_of_the_table_are_refused_together() {
    String thtk = aHandshakeLocking("10");
    Map<String, String> charge = aCharge(thtk, "10", "K", "payment_plan", "6");
    charge.put("credit_card_number", "4907639999909023");

    HttpResponse<String> refused = post(PROCESS, charge);

    assertThat(withoutTheHash(refused.body()))
        .isEqualTo(
            "{\"errors\":[{\"code\":10014,\"param\":\"tran_mode\",\"message\":\"INVALID TRAN"
                + " MODE\"},{\"code\":10007,\"param\":\"payment_plan\",\"message\":\"INVALID"
                + " PAYMENT PLAN\"},{\"code\":10002,\"param\":\"credit_card_number\","
                + "\"message\":\"INVALID CREDIT CARD NUMBER\"}],\"transaction_response\":null}");
    assertThat(objects()).isEmpty();
  }

  @Test
  void payment_plan_six_alone_is_refused_10007() {
    String thtk = aHandshakeLocking("10");

    HttpResponse<String> refused = post(PROCESS, aCharge(thtk, "10", "A", "payment_plan", "6"));

    assertThat(withoutTheHash(refused.body()))
        .isEqualTo(
            "{\"errors\":[{\"code\":10007,\"param\":\"payment_plan\",\"message\":\"INVALID"
                + " PAYMENT PLAN\"}],\"transaction_response\":null}");
    assertThat(objects()).isEmpty();
  }

  @Test
  void the_script_and_the_frames_are_served_with_their_own_headers() {
    HttpResponse<String> script = get("/tranzila/hf/assets/js/thostedf.js");
    HttpResponse<String> field =
        get(
            "/tranzila/hf/assets/js/hfields_depend/genfield.php?field_name=credit_card_number"
                + "&instance_identifier=abc");
    HttpResponse<String> control =
        get("/tranzila/hf/assets/js/hfields_depend/controlfields.html?instance_identifier=abc");
    HttpResponse<String> unknown = get("/tranzila/hf/assets/js/nothing.js");
    HttpResponse<String> wrongMethod = get(PROCESS);

    assertThat(script.statusCode()).isEqualTo(200);
    assertThat(script.headers().firstValue("Content-Type").orElseThrow())
        .isEqualTo("text/javascript;charset=utf-8");
    assertThat(script.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-cache");
    assertThat(script.headers().firstValue("X-Content-Type-Options").orElseThrow())
        .isEqualTo("nosniff");
    assertThat(script.body()).startsWith("var TzlaHostedFields = (function () {");
    assertThat(field.statusCode()).isEqualTo(200);
    assertThat(field.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo(TranzilaHostedFieldsPage.WHAT_A_FIELD_PERMITS);
    assertThat(field.headers().firstValue("X-Frame-Options")).isEmpty();
    assertThat(field.body()).isEqualTo(TranzilaHostedFieldsPage.THE_DOCUMENT);
    assertThat(control.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo(TranzilaHostedControlPage.WHAT_IT_PERMITS);
    assertThat(control.body()).isEqualTo(TranzilaHostedControlPage.THE_DOCUMENT);
    assertThat(unknown.statusCode()).isEqualTo(404);
    assertThat(unknown.body()).contains("hosted fields is served at this address");
    assertThat(wrongMethod.statusCode()).isEqualTo(405);
    assertThat(wrongMethod.body()).contains("does not answer this HTTP method");
  }

  private boolean theHashIsTheSecretsHmacOf(String body) {
    Matcher hash = THE_HASH.matcher(body);
    if (!hash.find()) {
      throw new AssertionError("no response_hash closes " + body);
    }
    return hash.group(1).equals(hmac(APP_SECRET, withoutTheHash(body)));
  }

  private static String withoutTheHash(String body) {
    return THE_HASH.matcher(body).replaceFirst("}");
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

  private String aHandshakeLocking(String sum) {
    HttpResponse<String> created =
        signed(
            "/v2/handshake/create", "{\"terminal_name\":\"" + TERMINAL + "\",\"sum\":" + sum + "}");
    return textIn(created.body(), "thtk");
  }

  private static Map<String, String> aCharge(
      String thtk, String amount, String tranMode, String... more) {
    Map<String, String> charge = new LinkedHashMap<>();
    charge.put("terminal_name", TERMINAL);
    charge.put("thtk", thtk);
    charge.put("amount", amount);
    charge.put("tran_mode", tranMode);
    charge.put("credit_card_number", THE_PUBLISHED_CARD);
    charge.put("cvv", A_CVV);
    charge.put("expiry_month", "12");
    charge.put("expiry_year", "2030");
    for (int at = 0; at < more.length; at += 2) {
      charge.put(more[at], more[at + 1]);
    }
    return charge;
  }

  private static String textIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no " + field + " in " + json);
    }
    return found.group(1);
  }

  private HttpResponse<String> signed(String path, String body) {
    String nonce = "hf-nonce-" + NONCES.incrementAndGet();
    String requestTime = Long.toString(FIXED_NOW.getEpochSecond());
    HttpRequest.Builder built =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-tranzila-api-app-key", APP_KEY)
            .header("X-tranzila-api-request-time", requestTime)
            .header("X-tranzila-api-nonce", nonce)
            .header("X-tranzila-api-access-token", hmac(APP_SECRET + requestTime + nonce, APP_KEY))
            .POST(HttpRequest.BodyPublishers.ofString(body));
    return send(built.build());
  }

  private HttpResponse<String> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
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

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
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
    configure(accounts, List.of());
  }

  private void configure(List<Map<String, Object>> accounts, List<Map<String, String>> webhooks) {
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
                webhooks))
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

  private String audit() {
    return http.get()
        .uri("/__simulator/tranzila/audit?testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }
}
