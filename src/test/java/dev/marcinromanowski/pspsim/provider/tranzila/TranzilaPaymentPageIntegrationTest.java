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

class TranzilaPaymentPageIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-page-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-10-14T09:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "myterminal";

  private static final String APP_KEY = "tranzila-page-it-app-key";

  private static final String APP_SECRET = "tranzila-page-it-app-secret";

  private static final String SHOP_OK = "https://shop.example/paid";

  private static final String SHOP_FAIL = "https://shop.example/failed";

  private static final String THE_PUBLISHED_CARD = "published-9022";

  private static final AtomicInteger NONCES = new AtomicInteger();

  private static final Pattern A_HIDDEN_FIELD =
      Pattern.compile("<input type=\"hidden\" name=\"([^\"]*)\" value=\"([^\"]*)\">");

  private static final Pattern THE_PAYER_FORM =
      Pattern.compile("action=\"(/tranzila/directng/myterminal/payer/directng_[0-9a-f]{24})\"");

  private static final Pattern THE_RETURN_FORM = Pattern.compile("action=\"([^\"]*)\">");

  @LocalServerPort private int port;

  private RestClient http;
  private LocalMerchantServer merchant;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure(List.of(anAccount()), List.of(Map.of("accountId", ACCOUNT, "url", merchant.url())));
  }

  @AfterEach
  void unconfigureTheTerminal() {
    reset();
    configure(List.of(), List.of());
    merchant.close();
  }

  @Test
  void
      a_handshake_locks_the_sum_the_payer_holds_it_the_shop_is_returned_and_notified_and_forces_it()
          throws InterruptedException {
    HttpResponse<String> handshake =
        signed(
            "/v2/handshake/create",
            "{\"terminal_name\":\"myterminal\",\"sum\":10.5,"
                + "\"request_params\":{\"order_id\":\"ORD-9871\",\"user_id\":12}}");
    String thtk = textIn(handshake.body(), "thtk");

    HttpResponse<String> page =
        get(
            "/tranzila/directng/myterminal/iframenew.php",
            pageParameters("10.5", "VK", "thtk", thtk, "contact", "Dana Levi"));
    String payerForm = theOnly(THE_PAYER_FORM, page.body());
    HttpResponse<String> returned =
        post(payerForm, Map.of("card", THE_PUBLISHED_CARD, "outcome", "PAID"));
    Map<String, String> fields = theHiddenFieldsOf(returned.body());
    String transaction = fields.get("transaction_id");
    LocalMerchantServer.ReceivedCallback notified = theNotifyNaming(transaction);
    HttpResponse<String> forced =
        signed(
            "/v1/transaction/credit_card/create",
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"force\",\"reference_txn_id\":"
                + transaction
                + ",\"card_number\":\""
                + fields.get("TranzilaTK")
                + "\",\"expire_month\":12,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":10.5}]}");

    assertThat(handshake.statusCode()).isEqualTo(200);
    assertThat(handshake.body())
        .matches("\\{\"error_code\":0,\"message\":\"Success\",\"thtk\":\"w[0-9a-f]{41}\"}");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
    assertThat(page.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    assertThat(page.body()).contains("10.5 ILS").contains("value=\"published-9022\"");
    assertThat(returned.statusCode()).isEqualTo(200);
    assertThat(theOnly(THE_RETURN_FORM, returned.body())).isEqualTo(SHOP_OK);
    assertThat(returned.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo(
            "default-src 'none'; style-src 'unsafe-inline';"
                + " script-src 'sha256-8lDeP0UDwCO6/RhblgeH/ctdBzjVpJxrXizsnIk3cEQ='");
    assertThat(List.copyOf(fields.keySet()))
        .containsExactly(
            "supplier",
            "sum",
            "currency",
            "Response",
            "contact",
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
            "order_id",
            "user_id");
    assertThat(fields)
        .containsEntry("supplier", "myterminal")
        .containsEntry("sum", "10.5")
        .containsEntry("currency", "1")
        .containsEntry("Response", "777")
        .containsEntry("contact", "Dana Levi")
        .containsEntry("index", transaction)
        .containsEntry("ccno", "9022")
        .containsEntry("Responsecv", "0")
        .containsEntry("Responseid", "0")
        .containsEntry("cardtype", "2")
        .containsEntry("cardissuer", "1")
        .containsEntry("cardacquirer", "2")
        .containsEntry("TranzilaTK", "t5d55d2922ca4029022")
        .containsEntry("txn_type", "verify")
        .containsEntry("tranmode", "VK")
        .containsEntry("order_id", "ORD-9871")
        .containsEntry("user_id", "12");
    assertThat(notified.contentType()).isEqualTo("application/x-www-form-urlencoded");
    Map<String, String> notifyFields = notified.fields();
    assertThat(notifyFields.get("ConfirmationCode")).matches("[0-9]{7}");
    Map<String, String> withoutTheConfirmation = new LinkedHashMap<>(notifyFields);
    withoutTheConfirmation.remove("ConfirmationCode");
    assertThat(withoutTheConfirmation).isEqualTo(fields);
    assertThat(notified.header("X-tranzila-api-access-token")).isEmpty();
    assertThat(forced.statusCode()).isEqualTo(200);
    assertThat(forced.body())
        .contains("\"error_code\":0")
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"txn_type\":\"force\"");
    assertThat(stateOf(transaction)).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(page.body() + returned.body() + notified.body()).doesNotContain("4907639999909022");
  }

  @Test
  void a_declined_attempt_returns_the_payer_to_the_fail_address_and_still_notifies_the_shop()
      throws InterruptedException {
    scenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            "page-declined",
            "keyedOn",
            "TEST_RUN",
            "whenState",
            "Started",
            "match",
            Map.of("operation", "debit"),
            "action",
            Map.of("type", "TERMINAL_FAILURE", "providerCode", "004")));
    String payerForm =
        theOnly(
            THE_PAYER_FORM,
            get("/tranzila/directng/myterminal/iframenew.php", pageParameters("25", "A")).body());

    HttpResponse<String> returned =
        post(payerForm, Map.of("card", THE_PUBLISHED_CARD, "outcome", "PAID"));
    Map<String, String> fields = theHiddenFieldsOf(returned.body());
    Map<String, String> notified = theNotifyNaming(fields.get("transaction_id")).fields();

    assertThat(theOnly(THE_RETURN_FORM, returned.body())).isEqualTo(SHOP_FAIL);
    assertThat(fields)
        .containsEntry("Response", "004")
        .containsEntry("txn_type", "debit")
        .containsEntry("tranmode", "A")
        .doesNotContainKey("TranzilaTK");
    assertThat(notified)
        .containsEntry("Response", "004")
        .doesNotContainKey("ConfirmationCode")
        .doesNotContainKey("TranzilaTK");
    assertThat(stateOf(fields.get("transaction_id")))
        .containsEntry("normalizedStatus", "FAILED")
        .containsEntry("nativeStatus", "debit 004");
  }

  @Test
  void a_thtk_that_does_not_lock_the_sum_is_an_illegal_operation_and_opens_no_page() {
    String thtk =
        textIn(
            signed("/v2/handshake/create", "{\"terminal_name\":\"myterminal\",\"sum\":150}").body(),
            "thtk");

    HttpResponse<String> page =
        post(
            "/tranzila/directng/myterminal/iframenew.php",
            pageParameters("149.99", "A", "thtk", thtk));

    assertThat(page.statusCode()).isEqualTo(400);
    assertThat(page.body()).contains("Illegal Operation 912791").doesNotContain("payer/directng_");
    assertThat(objects()).isEmpty();
  }

  @Test
  void a_dc_disable_already_approved_on_the_page_charges_nothing_the_second_time()
      throws InterruptedException {
    String first =
        theOnly(
            THE_PAYER_FORM,
            post(
                    "/tranzila/directng/myterminal/iframenew.php",
                    pageParameters("30", "A", "DCdisable", "order-77-attempt"))
                .body());
    String second =
        theOnly(
            THE_PAYER_FORM,
            post(
                    "/tranzila/directng/myterminal/iframenew.php",
                    pageParameters("30", "A", "DCdisable", "order-77-attempt"))
                .body());

    HttpResponse<String> paid = post(first, Map.of("card", THE_PUBLISHED_CARD, "outcome", "PAID"));
    theNotifyNaming(theHiddenFieldsOf(paid.body()).get("transaction_id"));
    HttpResponse<String> again =
        post(second, Map.of("card", THE_PUBLISHED_CARD, "outcome", "PAID"));

    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(again.body())
        .contains("This payment has already been paid. Please contact customer service.")
        .doesNotContain("<input type=\"hidden\"");
    assertThat(
            objects().stream()
                .map(object -> object.get("providerObjectId") + " " + object.get("nativeStatus"))
                .filter(object -> !object.startsWith("directng_")))
        .hasSize(1)
        .allSatisfy(object -> assertThat(object).endsWith(" debit 000"));
    assertThat(stateOf(second.substring(second.lastIndexOf('/') + 1)))
        .containsEntry("nativeStatus", "awaiting the payer");
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_notify_the_shop_does_not_acknowledge_with_ok_is_retried_until_it_does()
      throws InterruptedException {
    merchant
        .thenAnswering(new LocalMerchantServer.Answer.Respond(200, "received"))
        .thenAnswering(new LocalMerchantServer.Answer.Respond(503, "OK"));
    String payerForm =
        theOnly(
            THE_PAYER_FORM,
            get("/tranzila/directng/myterminal/iframenew.php", pageParameters("12", "A")).body());
    String transaction =
        theHiddenFieldsOf(
                post(payerForm, Map.of("card", THE_PUBLISHED_CARD, "outcome", "PAID")).body())
            .get("transaction_id");
    theNotifyNaming(transaction);
    String job = String.valueOf(deliveries().get(0).get("webhookJobId"));

    Map<String, Object> secondAttempt = retry(job);
    Map<String, Object> thirdAttempt = retry(job);

    assertThat(secondAttempt).containsEntry("status", "PENDING").containsEntry("attemptsMade", 2);
    assertThat(thirdAttempt).containsEntry("status", "DELIVERED").containsEntry("attemptsMade", 3);
    assertThat(merchant.received()).hasSize(3);
    assertThat(merchant.received().get(2).fields()).isEqualTo(merchant.received().get(0).fields());
  }

  @Test
  void worldline_keeps_v2_while_the_handshake_namespace_answers_its_own_faults() {
    HttpResponse<String> worldlinePayment = post("/v2/pspid-1/payments", "{}", Map.of());
    HttpResponse<String> worldlineUnknown = post("/v2/pspid-1/nothing-here", "{}", Map.of());
    HttpResponse<String> worldlineWrongMethod = get("/v2/pspid-1/payments", Map.of());
    HttpResponse<String> handshakeUnknown = post("/v2/handshake/nothing-here", "{}", Map.of());
    HttpResponse<String> handshakeWrongMethod = get("/v2/handshake/create", Map.of());
    HttpResponse<String> handshakeUnsigned =
        post("/v2/handshake/create", "{\"terminal_name\":\"myterminal\",\"sum\":1}", Map.of());

    assertThat(worldlinePayment.statusCode()).isEqualTo(403);
    assertThat(worldlinePayment.body()).contains("ACCESS_TO_MERCHANT_NOT_ALLOWED");
    assertThat(worldlineUnknown.statusCode()).isEqualTo(404);
    assertThat(worldlineUnknown.body())
        .contains("no route of provider worldline answers this address")
        .doesNotContain("error_code");
    assertThat(worldlineWrongMethod.statusCode()).isEqualTo(405);
    assertThat(worldlineWrongMethod.body())
        .contains("provider worldline answers this address, and not with this HTTP method")
        .doesNotContain("error_code");
    assertThat(handshakeUnknown.statusCode()).isEqualTo(404);
    assertThat(handshakeUnknown.body())
        .isEqualTo("{\"error_code\":404,\"message\":\"The specified resource was not found\"}");
    assertThat(handshakeWrongMethod.statusCode()).isEqualTo(405);
    assertThat(handshakeWrongMethod.body())
        .isEqualTo(
            "{\"error_code\":405,\"message\":\"This address does not answer this HTTP method\"}");
    assertThat(handshakeUnsigned.statusCode()).isEqualTo(401);
    assertThat(handshakeUnsigned.body())
        .isEqualTo("{\"error_code\":401,\"message\":\"Unauthorized\"}");
  }

  private Map<String, String> pageParameters(String sum, String tranmode, String... extra) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("sum", sum);
    parameters.put("currency", "1");
    parameters.put("tranmode", tranmode);
    parameters.put("success_url_address", SHOP_OK);
    parameters.put("fail_url_address", SHOP_FAIL);
    parameters.put("notify_url_address", merchant.url());
    for (int at = 0; at < extra.length; at += 2) {
      parameters.put(extra[at], extra[at + 1]);
    }
    return parameters;
  }

  private LocalMerchantServer.ReceivedCallback theNotifyNaming(String transactionId)
      throws InterruptedException {
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

  private static Map<String, String> theHiddenFieldsOf(String html) {
    Map<String, String> fields = new LinkedHashMap<>();
    Matcher found = A_HIDDEN_FIELD.matcher(html);
    while (found.find()) {
      fields.put(unescaped(found.group(1)), unescaped(found.group(2)));
    }
    return fields;
  }

  private static String unescaped(String html) {
    return html.replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  private static String theOnly(Pattern pattern, String body) {
    Matcher found = pattern.matcher(body);
    if (!found.find()) {
      throw new AssertionError("nothing matches " + pattern + " in " + body);
    }
    return unescaped(found.group(1));
  }

  private static String textIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no " + field + " in " + json);
    }
    return found.group(1);
  }

  private HttpResponse<String> signed(String path, String body) {
    String nonce = "page-nonce-" + NONCES.incrementAndGet();
    String requestTime = Long.toString(FIXED_NOW.getEpochSecond());
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-tranzila-api-app-key", APP_KEY);
    headers.put("X-tranzila-api-request-time", requestTime);
    headers.put("X-tranzila-api-nonce", nonce);
    headers.put("X-tranzila-api-access-token", tokenFor(requestTime, nonce));
    return post(path, body, headers);
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

  private HttpResponse<String> post(String path, String body, Map<String, String> headers) {
    HttpRequest.Builder built =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    for (Map.Entry<String, String> header : headers.entrySet()) {
      built = built.header(header.getKey(), header.getValue());
    }
    return send(built.build());
  }

  private HttpResponse<String> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<String> get(String path, Map<String, String> query) {
    String suffix = query.isEmpty() ? "" : "?" + encoded(query);
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path + suffix))
            .GET()
            .build());
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

  private void scenario(Map<String, Object> declared) {
    http.post()
        .uri("/__simulator/tranzila/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(declared)
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

  private List<Map<String, Object>> deliveries() {
    return http.get()
        .uri("/__simulator/tranzila/webhooks?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private Map<String, Object> retry(String webhookJobId) {
    return http.post()
        .uri("/__simulator/tranzila/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "operation", "RETRY", "webhookJobId", webhookJobId))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }
}
