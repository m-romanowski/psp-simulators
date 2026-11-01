package dev.marcinromanowski.pspsim.journey;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

final class StripeJourneyClient implements AutoCloseable {

  static final String RUN = "stripe-acceptance-journey";
  static final String OTHER_RUN = "stripe-acceptance-other-run";
  static final String SECRET = "sk_test_StripeAcceptance";
  static final String PUBLIC = "pk_test_StripeAcceptance";
  static final String OTHER_SECRET = "sk_test_StripeAcceptanceOtherAccount";
  static final String OTHER_PUBLIC = "pk_test_StripeAcceptanceOtherAccount";
  static final String OTHER_RUN_SECRET = "sk_test_StripeAcceptanceAnotherRun";
  static final String OTHER_RUN_PUBLIC = "pk_test_StripeAcceptanceAnotherRun";
  static final String NOW = "2026-11-01T09:00:00Z";
  static final String ENDIVE = "2026-10-28.endive";
  static final String DAHLIA = "2026-08-26.dahlia";
  static final String SIGNING_SECRET = "whsec_StripeAcceptanceEndpoint";
  static final String PAYMENTS = "/v1/payment_intents";
  static final String SETUPS = "/v1/setup_intents";

  record Reply(int status, Map<String, List<String>> headers, String body) {
    JsonObject json() {
      return JsonParser.parseString(body).getAsJsonObject();
    }

    String at(String path) {
      return value(json(), path);
    }

    @Nullable String header(String name) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(name))
          .map(entry -> entry.getValue().getFirst())
          .findFirst()
          .orElse(null);
    }
  }

  private final int port;
  private final HttpClient wire = HttpClient.newHttpClient();
  private final RestClient control;
  final LocalMerchantServer endpoint = new LocalMerchantServer();

  StripeJourneyClient(int port) {
    this.port = port;
    control = RestClient.create(base());
    configure(
        RUN,
        List.of(
            account("merchant-1", SECRET, PUBLIC),
            account("merchant-2", OTHER_SECRET, OTHER_PUBLIC)));
    configure(OTHER_RUN, List.of(account("merchant-1", OTHER_RUN_SECRET, OTHER_RUN_PUBLIC)));
    assertThat(ok("GET", "/v1/payment_methods", SECRET, Map.of()).json().getAsJsonArray("data"))
        .isEmpty();
  }

  String base() {
    return "http://localhost:" + port;
  }

  private static Map<String, Object> account(String id, String secret, String publishable) {
    return Map.of(
        "accountId",
        id,
        "configuration",
        Map.of(
            "secretKey",
            secret,
            "publishableKey",
            publishable,
            "captureWindow",
            "PT2H",
            "apiVersion",
            DAHLIA,
            "webhookSecret",
            SIGNING_SECRET));
  }

  private void configure(String run, List<Map<String, Object>> accounts) {
    control(
        "PUT",
        "/config",
        Map.of(
            "testRunId",
            run,
            "clock",
            Map.of("fixedNow", NOW),
            "accounts",
            accounts,
            "webhooks",
            List.of(Map.of("accountId", "merchant-1", "url", endpoint.url()))));
  }

  com.google.gson.JsonElement control(String method, String path, Map<String, ?> body) {
    String result =
        control
            .method(org.springframework.http.HttpMethod.valueOf(method))
            .uri("/__simulator/stripe" + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
    return JsonParser.parseString(result);
  }

  JsonObject ledger(String id) {
    String result =
        control
            .get()
            .uri("/__simulator/stripe/state/" + id + "?testRunId=" + RUN)
            .retrieve()
            .body(String.class);
    JsonObject ledger = new JsonObject();
    ledger.add("operations", JsonParser.parseString(result).getAsJsonObject().get("operations"));
    return ledger;
  }

  void rule(String operation, String amount, String code) {
    control(
        "POST",
        "/scenarios",
        Map.of(
            "testRunId",
            RUN,
            "priority",
            100,
            "match",
            Map.of("operation", operation, "amount", amount, "merchantId", "merchant-1"),
            "action",
            Map.of("type", "TERMINAL_FAILURE", "providerCode", code)));
  }

  void loseTheCaptureResponse() {
    control(
        "POST",
        "/scenarios/presets",
        Map.of("testRunId", RUN, "preset", "capture-accepted-response-lost"));
  }

  void advance(String duration) {
    control("POST", "/clock/advance", Map.of("testRunId", RUN, "advanceBy", duration));
  }

  Reply ok(String method, String path, String key, Map<String, String> form) {
    return ok(method, path, key, form, Map.of());
  }

  Reply ok(
      String method,
      String path,
      String key,
      Map<String, String> form,
      Map<String, String> headers) {
    Reply reply = request(method, path, key, form, headers);
    assertThat(reply.status()).as(reply.body()).isEqualTo(200);
    assertThat(reply.header("Content-Type")).startsWith("application/json");
    return reply;
  }

  Reply request(
      String method,
      String path,
      String key,
      Map<String, String> form,
      Map<String, String> headers) {
    String encoded = encoded(form);
    String address =
        base() + path + ("GET".equals(method) && !encoded.isEmpty() ? "?" + encoded : "");
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(address)).timeout(Duration.ofSeconds(10));
    if (!key.isBlank()) {
      request.header("Authorization", "Bearer " + key);
    }
    headers.forEach(request::header);
    if ("GET".equals(method)) {
      request.GET();
    } else {
      request
          .header("Content-Type", "application/x-www-form-urlencoded")
          .method(method, HttpRequest.BodyPublishers.ofString(encoded));
    }
    try {
      HttpResponse<String> response =
          wire.send(request.build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.body()).doesNotContain("4242424242424242", "4000000000009995", "\"cvc\"");
      return new Reply(response.statusCode(), response.headers().map(), response.body());
    } catch (IOException lost) {
      throw new UncheckedIOException(lost);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  static Map<String, String> payment(String amount) {
    return Map.of("amount", amount, "currency", "usd", "allowed_payment_method_types[]", "card");
  }

  static Map<String, String> with(Map<String, String> form, String... fields) {
    Map<String, String> result = new LinkedHashMap<>(form);
    for (int i = 0; i < fields.length; i += 2) {
      result.put(fields[i], fields[i + 1]);
    }
    return result;
  }

  static String value(JsonObject object, String path) {
    com.google.gson.JsonElement cursor = object;
    for (String member : path.split("\\.", -1)) {
      cursor = cursor.getAsJsonObject().get(member);
    }
    return cursor.getAsString();
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  @Override
  public void close() {
    try {
      for (String run : List.of(RUN, OTHER_RUN)) {
        control("POST", "/reset", Map.of("testRunId", run));
      }
    } finally {
      endpoint.close();
    }
  }
}
