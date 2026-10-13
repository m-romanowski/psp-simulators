package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer.Answer;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer.ReceivedCallback;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class StripeWebhookIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-webhook-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51WebhookSimulator";
  private static final String SIGNING_SECRET = "whsec_theEndpointSecretOfTheRun";
  private static final String THREE_DAYS = "P3D";

  private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

  private static final Duration SETTLES_AFTER = Duration.ofSeconds(5);

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();
  private final LocalMerchantServer merchant = new LocalMerchantServer();

  @BeforeEach
  void configureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(FIXED_NOW, Map.of(StripeWebhookSigning.SECRET_SETTING, SIGNING_SECRET));
  }

  @AfterEach
  void stopEverythingThisTestStarted() {
    reset();
    merchant.close();
  }

  @Test
  void delivers_a_signed_event_about_a_held_payment_intent_to_the_configured_endpoint() {
    String intentId = aHeldPaymentIntent();

    Map<String, Object> scheduled = sendWebhook(Map.of("providerObjectId", intentId));

    ReceivedCallback delivered = onlyCallback();
    assertThat(scheduled.get("status")).isEqualTo("DELIVERED");
    assertThat(delivered.contentType()).isEqualTo(StripeEvent.CONTENT_TYPE);
    assertThat(delivered.body())
        .contains("\"id\":\"" + scheduled.get("eventId") + "\"")
        .contains("\"type\":\"payment_intent.amount_capturable_updated\"")
        .contains("\"id\":\"" + intentId + "\"");
    assertThat(timestampOf(delivered)).isEqualTo(FIXED_NOW.getEpochSecond());
    assertThat(elementOf(delivered, "v1"))
        .isEqualTo(hmac(SIGNING_SECRET, timestampOf(delivered) + "." + delivered.body()));
  }

  @Test
  void a_retry_after_the_run_has_travelled_carries_a_fresh_signature_over_the_same_body() {
    merchant.alwaysAnswering(new Answer.Respond(500, "not now"));
    String intentId = aHeldPaymentIntent();
    Map<String, Object> scheduled = sendWebhook(Map.of("providerObjectId", intentId));

    configure(
        FIXED_NOW.plus(FIVE_MINUTES), Map.of(StripeWebhookSigning.SECRET_SETTING, SIGNING_SECRET));
    sendWebhook(
        Map.of(
            "operation", "RETRY", "webhookJobId", String.valueOf(scheduled.get("webhookJobId"))));

    List<ReceivedCallback> attempts = merchant.received();
    assertThat(attempts).hasSize(2);
    assertThat(attempts.get(1).body()).isEqualTo(attempts.get(0).body());
    assertThat(timestampOf(attempts.get(0))).isEqualTo(FIXED_NOW.getEpochSecond());
    assertThat(timestampOf(attempts.get(1)))
        .isEqualTo(FIXED_NOW.plus(FIVE_MINUTES).getEpochSecond());
    assertThat(elementOf(attempts.get(1), "v1"))
        .isNotEqualTo(elementOf(attempts.get(0), "v1"))
        .isEqualTo(
            hmac(SIGNING_SECRET, timestampOf(attempts.get(1)) + "." + attempts.get(1).body()));
  }

  @Test
  void an_account_configured_to_date_its_deliveries_back_emits_a_stale_t_and_still_delivers() {
    configure(
        FIXED_NOW,
        Map.of(
            StripeWebhookSigning.SECRET_SETTING,
            SIGNING_SECRET,
            StripeWebhookSigning.AGE_SETTING,
            FIVE_MINUTES.toString()));
    String intentId = aHeldPaymentIntent();

    Map<String, Object> scheduled = sendWebhook(Map.of("providerObjectId", intentId));

    assertThat(scheduled.get("status")).isEqualTo("DELIVERED");
    assertThat(timestampOf(onlyCallback()))
        .isEqualTo(FIXED_NOW.minus(FIVE_MINUTES).getEpochSecond());
  }

  @Test
  void resetting_the_test_run_clears_the_deliveries_it_made() {
    sendWebhook(Map.of("providerObjectId", aHeldPaymentIntent()));

    reset();

    assertThat(webhookJobs()).isEmpty();
  }

  @Test
  void a_payment_left_in_flight_is_not_paid_until_the_run_travels_and_the_event_arrives() {
    selectPreset("status-lookup-processing-then-success");

    HttpResponse<byte[]> created = post("/v1/payment_intents", anAsynchronousCharge());
    String intentId = valueIn(bodyOf(created), "id");
    String whileInFlight = bodyOf(get("/v1/payment_intents/" + intentId));
    Map<String, Object> pending = stateOf(intentId);
    List<ReceivedCallback> beforeTheRunTravelled = List.copyOf(merchant.received());

    configure(
        FIXED_NOW.plus(SETTLES_AFTER), Map.of(StripeWebhookSigning.SECRET_SETTING, SIGNING_SECRET));
    int attempted = deliverDue();
    String afterItSettled = bodyOf(get("/v1/payment_intents/" + intentId));

    assertThat(bodyOf(created)).contains("\"status\":\"processing\"", "\"amount_received\":0");
    assertThat(whileInFlight).contains("\"status\":\"processing\"");
    assertThat(pending).containsEntry("normalizedStatus", "PROCESSING");
    assertThat(beforeTheRunTravelled).isEmpty();

    assertThat(attempted).isEqualTo(1);
    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.succeeded\"")
        .contains("\"status\":\"succeeded\"");
    assertThat(afterItSettled).contains("\"status\":\"succeeded\"");
    assertThat(stateOf(intentId)).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_payment_left_in_flight_to_fail_lands_on_the_decline_and_reports_it_to_the_merchant() {
    selectPreset("status-lookup-processing-then-failed");

    String intentId = valueIn(bodyOf(post("/v1/payment_intents", anAsynchronousCharge())), "id");
    String whileInFlight = bodyOf(get("/v1/payment_intents/" + intentId));

    configure(
        FIXED_NOW.plus(SETTLES_AFTER), Map.of(StripeWebhookSigning.SECRET_SETTING, SIGNING_SECRET));
    deliverDue();

    assertThat(whileInFlight).contains("\"status\":\"processing\"");
    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.payment_failed\"")
        .contains("\"status\":\"requires_payment_method\"");
    assertThat(bodyOf(get("/v1/payment_intents/" + intentId)))
        .contains("\"status\":\"requires_payment_method\"")
        .contains("generic_decline");
  }

  private void selectPreset(String preset) {
    http.post()
        .uri("/__simulator/stripe/scenarios/presets")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "preset", preset))
        .retrieve()
        .toBodilessEntity();
  }

  private int deliverDue() {
    Map<String, Object> ran =
        http.post()
            .uri("/__simulator/stripe/webhooks/deliver-due")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value()))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return (Integer) ran.get("attempted");
  }

  private Map<String, Object> stateOf(String intentId) {
    return http.get()
        .uri("/__simulator/stripe/state/" + intentId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private HttpResponse<byte[]> get(String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + SECRET_KEY)
            .header("X-Test-Run-Id", RUN.value())
            .GET()
            .build());
  }

  private static Map<String, String> anAsynchronousCharge() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    return form;
  }

  private String aHeldPaymentIntent() {
    return valueIn(bodyOf(post("/v1/payment_intents", hold())), "id");
  }

  private ReceivedCallback onlyCallback() {
    assertThat(merchant.received()).hasSize(1);
    return merchant.received().get(0);
  }

  private static long timestampOf(ReceivedCallback delivered) {
    return Long.parseLong(elementOf(delivered, "t"));
  }

  private static String elementOf(ReceivedCallback delivered, String scheme) {
    List<String> header = delivered.header(StripeSignature.HEADER);
    assertThat(header).hasSize(1);
    for (String element : header.get(0).split(",", -1)) {
      if (element.startsWith(scheme + "=")) {
        return element.substring(scheme.length() + 1);
      }
    }
    throw new IllegalStateException("no " + scheme + " element in " + header.get(0));
  }

  private static String hmac(String secret, String material) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  private Map<String, Object> sendWebhook(Map<String, String> declared) {
    Map<String, Object> body = new LinkedHashMap<>(declared);
    body.put("testRunId", RUN.value());
    return http.post()
        .uri("/__simulator/stripe/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<?> webhookJobs() {
    return http.get()
        .uri("/__simulator/stripe/webhooks?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Object>>() {});
  }

  private void configure(Instant now, Map<String, String> signing) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put(StripeAccount.SECRET_KEY_PROPERTY, SECRET_KEY);
    configuration.put(StripeCaptureWindow.SETTING, THREE_DAYS);
    configuration.putAll(signing);
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
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
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

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY)
            .header("X-Test-Run-Id", RUN.value())
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<byte[]> sent(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private static Map<String, String> hold() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    return form;
  }

  private static String bodyOf(HttpResponse<byte[]> response) {
    return new String(response.body(), UTF_8);
  }

  private static String valueIn(String json, String member) {
    String opening = "\"" + member + "\":\"";
    int start = json.indexOf(opening);
    int from = start + opening.length();
    return json.substring(from, json.indexOf('"', from));
  }
}
