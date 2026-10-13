package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

class StripeEphemeralKeyIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-ephemeral-key-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final Instant TWO_HOURS_ON = FIXED_NOW.plus(Duration.ofHours(2));

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51EphemeralSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String CUSTOMERS = "/v1/customers";
  private static final String EPHEMERAL_KEYS = "/v1/ephemeral_keys";
  private static final String PAYMENT_INTENTS = "/v1/payment_intents";

  private static final String AN_EMAIL = "payer@example.test";

  private static final String IDEMPOTENCY_KEY = "8f1a1e2c-0f1a-4f2b-9c3d-5a6b7c8d9e01";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @BeforeEach
  void configureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configureTheClockAt(FIXED_NOW);
  }

  @AfterEach
  void unconfigureTheAccount() {
    reset();
    http.put()
        .uri("/__simulator/stripe/config")
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
  void a_mobile_client_mints_a_key_for_its_customer_and_reads_that_customer_back_with_it() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");

    HttpResponse<byte[]> minted = post(EPHEMERAL_KEYS, Map.of("customer", customerId));
    String secret = valueIn(bodyOf(minted), "secret");
    HttpResponse<byte[]> read = getAs(secret, CUSTOMERS + "/" + customerId);
    HttpResponse<byte[]> listed = getAs(secret, CUSTOMERS + "/" + customerId + "/payment_methods");

    assertThat(minted.statusCode()).isEqualTo(200);
    String keyId = valueIn(bodyOf(minted), "id");
    assertThat(keyId).matches("ephkey_sim_[0-9a-f]{16}");
    assertThat(secret)
        .as(
            "the key answers a secret of its own, the way stripe's ek_test_ sample carries no key id")
        .matches("ek_sim_[0-9a-f]{32}")
        .doesNotContain(keyId);
    assertThat(bodyOf(minted))
        .contains("\"object\":\"ephemeral_key\"")
        .contains("\"associated_objects\":[{\"type\":\"customer\",\"id\":\"" + customerId + "\"}]")
        .contains("\"created\":" + FIXED_NOW.getEpochSecond())
        .contains("\"expires\":" + FIXED_NOW.plus(Duration.ofHours(1)).getEpochSecond());
    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(bodyOf(read)).contains("\"email\":\"" + AN_EMAIL + "\"");
    assertThat(listed.statusCode()).isEqualTo(200);
    assertThat(bodyOf(listed)).contains("\"object\":\"list\"");
  }

  @Test
  void the_key_reaches_no_other_route_and_stops_reaching_its_own_once_it_has_expired() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    String secret = valueIn(bodyOf(post(EPHEMERAL_KEYS, Map.of("customer", customerId))), "secret");

    HttpResponse<byte[]> anotherRoute = postAs(secret, CUSTOMERS, Map.of("email", AN_EMAIL));
    configureTheClockAt(TWO_HOURS_ON);
    HttpResponse<byte[]> afterItExpired = getAs(secret, CUSTOMERS + "/" + customerId);

    assertThat(anotherRoute.statusCode()).isEqualTo(401);
    assertThat(bodyOf(anotherRoute)).contains("not one a short-lived key reaches in this build");
    assertThat(afterItExpired.statusCode()).isEqualTo(401);
    assertThat(bodyOf(afterItExpired)).contains("expires moment has passed");
  }

  @Test
  void the_secret_is_on_the_wire_and_on_no_screen_and_in_no_row_of_the_trail() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");

    String secret =
        valueIn(bodyOf(keyed(EPHEMERAL_KEYS, Map.of("customer", customerId))), "secret");
    String replayed =
        valueIn(bodyOf(keyed(EPHEMERAL_KEYS, Map.of("customer", customerId))), "secret");

    assertThat(secret).matches("ek_sim_[0-9a-f]{32}");
    assertThat(replayed).isEqualTo(secret);
    assertThat(screen("/ui/audit", theWholeOfTheAnswerRowOf(EPHEMERAL_KEYS)))
        .doesNotContain(secret)
        .contains("associated_objects.id")
        .contains("redacted before it was stored");
    assertThat(screen("/ui/idempotency", "")).doesNotContain(secret).contains(Redaction.REDACTED);
    assertThat(screen("/ui/objects", "")).doesNotContain(secret);
    assertThat(bodyOf(get("/__simulator/stripe/audit?testRunId=" + RUN.value())))
        .doesNotContain(secret);
  }

  @Test
  void resetting_the_test_run_leaves_the_key_it_minted_authenticating_nothing() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    String secret = valueIn(bodyOf(post(EPHEMERAL_KEYS, Map.of("customer", customerId))), "secret");

    reset();
    configureTheClockAt(FIXED_NOW);
    String again = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");

    HttpResponse<byte[]> read = getAs(secret, CUSTOMERS + "/" + again);
    assertThat(read.statusCode()).isEqualTo(401);
    assertThat(bodyOf(read)).contains("No valid API key provided.");
  }

  @Test
  void a_key_for_a_customer_this_run_holds_none_of_is_refused_and_never_names_the_id_it_was_sent() {
    String absent = CreateCustomer.ID_PREFIX + "0000000000000000";

    HttpResponse<byte[]> refused = post(EPHEMERAL_KEYS, Map.of("customer", absent));
    HttpResponse<byte[]> nameless = post(EPHEMERAL_KEYS, Map.of());

    assertThat(refused.statusCode()).isEqualTo(404);
    assertThat(bodyOf(refused)).contains("\"code\":\"resource_missing\"").doesNotContain(absent);
    assertThat(nameless.statusCode()).isEqualTo(400);
    assertThat(bodyOf(nameless))
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"customer\"");
  }

  @Test
  void the_key_is_no_object_of_the_read_surface_and_the_payment_routes_are_unchanged_by_it() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    String keyId = valueIn(bodyOf(post(EPHEMERAL_KEYS, Map.of("customer", customerId))), "id");

    HttpResponse<byte[]> hold = post(PAYMENT_INTENTS, aHold(customerId));

    assertThat(stateStatusOf(keyId)).isEqualTo(404);
    assertThat(screen("/ui/objects", "")).doesNotContain(keyId);
    assertThat(hold.statusCode()).isEqualTo(200);
    assertThat(bodyOf(hold)).contains("\"status\":\"requires_capture\"");
  }

  private String theWholeOfTheAnswerRowOf(String path) {
    List<Map<String, Object>> records =
        http.get()
            .uri("/__simulator/stripe/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    for (Map<String, Object> record : records) {
      if (path.equals(record.get("path")) && "OUTBOUND_RESPONSE".equals(record.get("direction"))) {
        return "&open=" + record.get("auditId") + "&values=show";
      }
    }
    throw new IllegalStateException("the trail holds no answer this simulator gave on " + path);
  }

  private static Map<String, String> aHold(String customerId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    form.put("customer", customerId);
    return form;
  }

  private void configureTheClockAt(Instant now) {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", now.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of(
                            StripeAccount.SECRET_KEY_PROPERTY,
                            SECRET_KEY,
                            StripeCaptureWindow.SETTING,
                            THREE_DAYS)))))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private int stateStatusOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/stripe/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }

  private String screen(String path, String alsoAsking) {
    HttpResponse<byte[]> answer =
        sent(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + path
                            + "?provider=stripe&testRunId="
                            + RUN.value()
                            + alsoAsking))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build());
    assertThat(answer.statusCode()).isEqualTo(200);
    return bodyOf(answer);
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return postAs(SECRET_KEY, path, form);
  }

  private HttpResponse<byte[]> postAs(String secretKey, String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + secretKey)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<byte[]> keyed(String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY)
            .header("Idempotency-Key", IDEMPOTENCY_KEY)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<byte[]> get(String path) {
    return getAs(SECRET_KEY, path);
  }

  private HttpResponse<byte[]> getAs(String secretKey, String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + secretKey)
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build());
  }

  private HttpResponse<byte[]> sent(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
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

  private static String bodyOf(HttpResponse<byte[]> response) {
    return new String(response.body(), UTF_8);
  }

  private static String valueIn(String json, String member) {
    String opening = "\"" + member + "\":\"";
    int start = json.indexOf(opening);
    if (start < 0) {
      return "";
    }
    int from = start + opening.length();
    return json.substring(from, json.indexOf('"', from));
  }
}
