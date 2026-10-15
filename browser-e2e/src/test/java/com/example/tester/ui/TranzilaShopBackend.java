package com.example.tester.ui;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TranzilaShopBackend {

  public static final String TRANZILA = "tranzila";

  private static final String TEST_RUN_HEADER = "X-Simulator-Test-Run-Id";

  private static final String HMAC = "HmacSHA256";

  private static final int NONCE_BYTES = 40;

  private static final String RESPONSE_HASH = ",\"response_hash\":\"";

  private final String baseUrl;
  private final TheProviderApi providerApi;
  private final String terminal;
  private final String appKey;
  private final String appSecret;

  public TranzilaShopBackend(String baseUrl, TheProviderApi providerApi) {
    this.baseUrl = baseUrl;
    this.providerApi = providerApi;
    String drawn = HexFormat.of().toHexDigits(ThreadLocalRandom.current().nextLong());
    this.terminal = "wv" + drawn.substring(0, 12);
    this.appKey = "shop-app-key-" + drawn;
    this.appSecret = "shop-app-secret-" + drawn;
  }

  public String terminal() {
    return terminal;
  }

  public String appKey() {
    return appKey;
  }

  public String appSecret() {
    return appSecret;
  }

  public String thePaymentPage() {
    return baseUrl + "/tranzila/directng/" + terminal + "/iframenew.php";
  }

  public String theHostedFields() {
    return baseUrl + "/tranzila/hf";
  }

  public boolean verifies(String answered) {
    int hash = answered.lastIndexOf(RESPONSE_HASH);
    if (hash < 0 || !answered.endsWith("\"}")) {
      return false;
    }
    String hashed = answered.substring(0, hash) + "}";
    String presented = answered.substring(hash + RESPONSE_HASH.length(), answered.length() - 2);
    return hmac(appSecret, hashed).equals(presented);
  }

  public String aHandshakeLocking(String testRunId, String sum, String requestParams) {
    HttpResponse<String> answer =
        signed(
            testRunId,
            "/v2/handshake/create",
            "{\"terminal_name\":\""
                + terminal
                + "\",\"sum\":"
                + sum
                + ",\"request_params\":"
                + requestParams
                + "}");
    if (answer.statusCode() != 200) {
      throw new IllegalStateException(
          "tranzila answered " + answer.statusCode() + " to a handshake: " + answer.body());
    }
    return theTextIn(answer.body(), "thtk");
  }

  public String forces(
      String testRunId, String transactionId, String token, String sum, String expiry) {
    HttpResponse<String> answer =
        signed(
            testRunId,
            "/v1/transaction/credit_card/create",
            "{\"terminal_name\":\""
                + terminal
                + "\",\"txn_type\":\"force\",\"reference_txn_id\":"
                + transactionId
                + ",\"card_number\":\""
                + token
                + "\",\"expire_month\":"
                + expiry.substring(0, 2)
                + ",\"expire_year\":20"
                + expiry.substring(2)
                + ",\"items\":[{\"name\":\"two seats\",\"units_number\":1,\"unit_price\":"
                + sum
                + "}]}");
    if (answer.statusCode() != 200) {
      throw new IllegalStateException(
          "tranzila answered " + answer.statusCode() + " to a force: " + answer.body());
    }
    return answer.body();
  }

  public String looksUp(String testRunId, String transactionId) {
    HttpResponse<String> answer =
        signed(
            testRunId,
            "/v1/transactions",
            "{\"terminal_name\":\"" + terminal + "\",\"transaction_index\":" + transactionId + "}");
    if (answer.statusCode() != 200) {
      throw new IllegalStateException(
          "tranzila answered " + answer.statusCode() + " to a lookup: " + answer.body());
    }
    return answer.body();
  }

  public String aThreeDSecureDebit(
      String testRunId, String card, String sum, String redirectUrl, String order) {
    HttpResponse<String> answer =
        signed(
            testRunId,
            "/v1/transaction/credit_card/create",
            "{\"terminal_name\":\""
                + terminal
                + "\",\"txn_type\":\"debit\",\"activate_3ds\":\"Y\",\"card_number\":\""
                + card
                + "\",\"expire_month\":12,\"expire_year\":2030,\"cvv\":\"737\","
                + "\"items\":[{\"name\":\"two seats\",\"units_number\":1,\"unit_price\":"
                + sum
                + "}],\"3ds_settings\":{\"browser\":{\"language\":\"en-gb\","
                + "\"screen_width\":390,\"screen_height\":844}},"
                + "\"auth_3ds_redirect\":{\"url\":\""
                + redirectUrl
                + "\",\"params\":[{\"key\":\"order\",\"value\":\""
                + order
                + "\"}]}}");
    if (answer.statusCode() != 200) {
      throw new IllegalStateException(
          "tranzila answered " + answer.statusCode() + " to a 3-D Secure debit: " + answer.body());
    }
    return answer.body();
  }

  public String completes(String testRunId, String trackId) {
    HttpResponse<String> answer =
        signed(
            testRunId,
            "/v1/transaction/credit_card/3ds/complete",
            "{\"track_id\":\"" + trackId + "\",\"terminal_name\":\"" + terminal + "\"}");
    if (answer.statusCode() != 200) {
      throw new IllegalStateException(
          "tranzila answered "
              + answer.statusCode()
              + " to a 3-D Secure completion: "
              + answer.body());
    }
    return answer.body();
  }

  private HttpResponse<String> signed(String testRunId, String path, String body) {
    String requestTime = Long.toString(Instant.now().getEpochSecond());
    byte[] drawn = new byte[NONCE_BYTES];
    ThreadLocalRandom.current().nextBytes(drawn);
    String nonce = HexFormat.of().formatHex(drawn);
    return providerApi.send(
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .header(TEST_RUN_HEADER, testRunId)
            .header("X-tranzila-api-app-key", appKey)
            .header("X-tranzila-api-request-time", requestTime)
            .header("X-tranzila-api-nonce", nonce)
            .header("X-tranzila-api-access-token", hmac(appSecret + requestTime + nonce, appKey))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
  }

  private static String hmac(String key, String message) {
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  static String theTextIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    if (!found.find()) {
      throw new IllegalStateException("tranzila answered no " + field + ": " + json);
    }
    return found.group(1);
  }
}
