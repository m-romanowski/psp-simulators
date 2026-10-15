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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class TranzilaWalletIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-wallet-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-10-15T09:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String TERMINAL = "myterminal";

  private static final String APP_KEY = "tranzila-wallet-it-app-key";

  private static final String APP_SECRET = "tranzila-wallet-it-app-secret";

  private static final String SHOP_OK = "https://shop.example/paid";

  private static final String SHOP_FAIL = "https://shop.example/failed";

  private static final String THE_PUBLISHED_CARD = "published-9022";

  private static final String THE_PAGE = "/tranzila/directng/myterminal/iframenew.php";

  private static final AtomicInteger NONCES = new AtomicInteger();

  private static final Pattern A_HIDDEN_FIELD =
      Pattern.compile("<input type=\"hidden\" name=\"([^\"]*)\" value=\"([^\"]*)\">");

  private static final Pattern THE_PAYER_FORM =
      Pattern.compile("action=\"(/tranzila/directng/myterminal/payer/directng_[0-9a-f]{24})\"");

  private static final Pattern A_WALLET_BUTTON =
      Pattern.compile(
          "action=\"(/tranzila/directng/myterminal/payer/directng_[0-9a-f]{24}/wallet/[a-z_]+)\"");

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
  void an_apple_pay_payment_returns_the_payer_notifies_the_shop_and_the_lookups_name_its_broker()
      throws InterruptedException {
    HttpResponse<String> page =
        get(THE_PAGE, pageParameters("21.5", "A", "apple_pay", "1", "orderid", "SHOP-5"));
    List<String> wallets = all(A_WALLET_BUTTON, page.body());

    HttpResponse<String> sheet = get(wallets.get(0), Map.of());
    String payerForm = theOnly(THE_PAYER_FORM, sheet.body());
    HttpResponse<String> returned =
        post(
            payerForm,
            Map.of("wallet", "apple_pay", "card", THE_PUBLISHED_CARD, "outcome", "PAID"));
    Map<String, String> fields = theHiddenFieldsOf(returned.body());
    String transaction = fields.get("transaction_id");
    Map<String, String> notified = theNotifyNaming(transaction).fields();
    HttpResponse<String> byIndex =
        signed("/v1/transactions", aLookup(",\"transaction_index\":" + transaction));
    HttpResponse<String> reported =
        signed("/tranzila/report/v1/transaction", aLookup(",\"transaction_index\":" + transaction));

    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(wallets).singleElement().asString().endsWith("/wallet/apple_pay");
    assertThat(sheet.statusCode()).isEqualTo(200);
    assertThat(sheet.headers().firstValue("Content-Security-Policy").orElseThrow())
        .isEqualTo("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'");
    assertThat(sheet.body())
        .contains("<h1>Apple Pay</h1>")
        .contains("<option value=\"published-9022\">")
        .doesNotContain("<script");
    assertThat(returned.statusCode()).isEqualTo(200);
    assertThat(theOnly(THE_RETURN_FORM, returned.body())).isEqualTo(SHOP_OK);
    assertThat(fields)
        .containsEntry("Response", "000")
        .containsEntry("apple_pay", "1")
        .containsEntry("orderid", "SHOP-5")
        .containsEntry("ccno", "9022")
        .containsEntry("txn_type", "debit")
        .containsEntry("tranmode", "A")
        .doesNotContainKey("broker");
    assertThat(fields.get("TranzilaTK"))
        .matches("t[0-9a-f]{14}9022")
        .isNotEqualTo("t5d55d2922ca4029022");
    Map<String, String> withoutTheConfirmation = new LinkedHashMap<>(notified);
    assertThat(withoutTheConfirmation.remove("ConfirmationCode")).matches("[0-9]{7}");
    assertThat(withoutTheConfirmation).isEqualTo(fields);
    assertThat(byIndex.statusCode()).isEqualTo(200);
    assertThat(byIndex.body()).contains("\"broker\":\"apple_pay\",\"broker_product\":\"\"");
    assertThat(reported.statusCode()).isEqualTo(200);
    assertThat(reported.body()).contains("\"broker\":\"apple_pay\",\"broker_product\":\"\"");
    assertThat(audit()).doesNotContain(fields.get("TranzilaTK")).contains("apple_pay");
  }

  @Test
  void a_google_pay_j5_is_forced_on_the_api_with_the_token_the_page_returned()
      throws InterruptedException {
    HttpResponse<String> page = get(THE_PAGE, pageParameters("40", "V", "google_pay", "1"));
    String sheet = theOnly(A_WALLET_BUTTON, page.body());
    String payerForm = theOnly(THE_PAYER_FORM, get(sheet, Map.of()).body());
    Map<String, String> fields =
        theHiddenFieldsOf(
            post(
                    payerForm,
                    Map.of("wallet", "google_pay", "card", THE_PUBLISHED_CARD, "outcome", "PAID"))
                .body());
    String hold = fields.get("transaction_id");
    String authorization = theNotifyNaming(hold).fields().get("ConfirmationCode");
    HttpResponse<String> forced =
        signed(
            "/v1/transaction/credit_card/create",
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"force\",\"reference_txn_id\":"
                + hold
                + ",\"authorization_number\":\""
                + authorization
                + "\",\"card_number\":\""
                + fields.get("TranzilaTK")
                + "\",\"expire_month\":12,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":40}]}");
    String force = textIn(forced.body(), "processor_response_code");
    HttpResponse<String> looked =
        signed("/tranzila/report/v1/transaction", aLookup(",\"transaction_index\":" + hold));

    assertThat(sheet).endsWith("/wallet/google_pay");
    assertThat(fields)
        .containsEntry("Response", "777")
        .containsEntry("txn_type", "verify")
        .containsEntry("tranmode", "V");
    assertThat(forced.statusCode()).isEqualTo(200);
    assertThat(force).isEqualTo("000");
    assertThat(forced.body()).contains("\"txn_type\":\"force\"");
    assertThat(looked.body()).contains("\"broker\":\"GOOGLE_PAY\"");
  }

  @Test
  void an_apple_pay_token_is_refused_for_a_new_debit_on_the_api() throws InterruptedException {
    String sheet =
        theOnly(A_WALLET_BUTTON, get(THE_PAGE, pageParameters("7", "A", "apple_pay", "1")).body());
    Map<String, String> fields =
        theHiddenFieldsOf(
            post(
                    theOnly(THE_PAYER_FORM, get(sheet, Map.of()).body()),
                    Map.of("wallet", "apple_pay", "card", THE_PUBLISHED_CARD, "outcome", "PAID"))
                .body());
    theNotifyNaming(fields.get("transaction_id"));

    HttpResponse<String> debit =
        signed(
            "/v1/transaction/credit_card/create",
            "{\"terminal_name\":\"myterminal\",\"txn_type\":\"debit\",\"card_number\":\""
                + fields.get("TranzilaTK")
                + "\",\"expire_month\":12,\"expire_year\":2030,"
                + "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":7}]}");

    assertThat(fields).containsEntry("Response", "000");
    assertThat(debit.statusCode()).isEqualTo(200);
    assertThat(debit.body())
        .isEqualTo("{\"error_code\":20111,\"message\":\"Provided token check failure\"}");
  }

  @Test
  void a_tranmode_no_wallet_pays_shows_no_wallet_and_refuses_one_named_on_the_page() {
    HttpResponse<String> page = get(THE_PAGE, pageParameters("3", "N", "apple_pay", "1"));
    String payerForm = theOnly(THE_PAYER_FORM, page.body());

    HttpResponse<String> sheet = get(payerForm + "/wallet/apple_pay", Map.of());
    HttpResponse<String> paid =
        post(
            payerForm,
            Map.of("wallet", "apple_pay", "card", THE_PUBLISHED_CARD, "outcome", "PAID"));
    HttpResponse<String> shownAgain = get(payerForm, Map.of());

    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).doesNotContain("/wallet/");
    assertThat(sheet.statusCode()).isEqualTo(422);
    assertThat(sheet.body()).contains("Apple Pay is not offered on this payment page");
    assertThat(paid.statusCode()).isEqualTo(422);
    assertThat(paid.body()).contains("Apple Pay is not offered on this payment page");
    assertThat(shownAgain.statusCode()).isEqualTo(200);
    assertThat(shownAgain.body()).contains("<option value=\"published-9022\">");
  }

  private static String aLookup(String criteria) {
    return "{\"terminal_name\":\"" + TERMINAL + "\"" + criteria + "}";
  }

  private static List<String> all(Pattern pattern, String body) {
    List<String> found = new ArrayList<>();
    Matcher matched = pattern.matcher(body);
    while (matched.find()) {
      found.add(unescaped(matched.group(1)));
    }
    return found;
  }

  private static Map<String, String> pageParameters(String sum, String tranmode, String... extra) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("sum", sum);
    parameters.put("currency", "1");
    parameters.put("tranmode", tranmode);
    parameters.put("success_url_address", SHOP_OK);
    parameters.put("fail_url_address", SHOP_FAIL);
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

  private String audit() {
    return http.get()
        .uri("/__simulator/tranzila/audit?testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }
}
