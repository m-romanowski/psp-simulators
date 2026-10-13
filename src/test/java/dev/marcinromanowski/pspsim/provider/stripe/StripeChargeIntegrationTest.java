package dev.marcinromanowski.pspsim.provider.stripe;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

class StripeChargeIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-charge-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-21T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51ChargeSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";
  private static final String CHARGES = "/v1/charges";
  private static final String REFUNDS = "/v1/refunds";
  private static final String CARD = "pm_card_visa";

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  private RestClient http;
  private final HttpClient wire =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @BeforeEach
  void configureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
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
  void a_hold_captured_and_refunded_in_full_is_walked_from_its_charge_over_http() {
    String intent = idIn(bodyOf(post(PAYMENT_INTENTS, aHoldOf("1000"))));
    String charge = latestChargeIn(bodyOf(get(PAYMENT_INTENTS + "/" + intent)));

    HttpResponse<byte[]> held = get(CHARGES + "/" + charge);
    post(PAYMENT_INTENTS + "/" + intent + "/capture", Map.of("amount_to_capture", "1000"));
    HttpResponse<byte[]> captured = get(CHARGES + "/" + charge);
    post(REFUNDS, Map.of("charge", charge, "amount", "400"));
    HttpResponse<byte[]> partly = get(CHARGES + "/" + charge);
    post(REFUNDS, Map.of("charge", charge, "amount", "600"));
    HttpResponse<byte[]> wholly = get(CHARGES + "/" + charge);

    assertThat(held.statusCode()).isEqualTo(200);
    assertThat(bodyOf(held))
        .contains("\"object\":\"charge\"")
        .contains("\"status\":\"succeeded\"")
        .contains("\"paid\":true")
        .contains("\"captured\":false")
        .contains("\"payment_intent\":\"" + intent + "\"");
    assertThat(bodyOf(captured)).contains("\"captured\":true").contains("\"amount_captured\":1000");
    assertThat(bodyOf(partly)).contains("\"amount_refunded\":400").contains("\"refunded\":false");
    assertThat(bodyOf(wholly)).contains("\"amount_refunded\":1000").contains("\"refunded\":true");
  }

  @Test
  void a_refund_taken_against_a_charge_names_that_charge_and_the_payment_it_belongs_to() {
    String intent = idIn(bodyOf(post(PAYMENT_INTENTS, aHoldOf("1000"))));
    String charge = latestChargeIn(bodyOf(get(PAYMENT_INTENTS + "/" + intent)));
    post(PAYMENT_INTENTS + "/" + intent + "/capture", Map.of("amount_to_capture", "1000"));

    HttpResponse<byte[]> refunded = post(REFUNDS, Map.of("charge", charge, "amount", "400"));

    assertThat(refunded.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refunded))
        .contains("\"charge\":\"" + charge + "\"")
        .contains("\"payment_intent\":\"" + intent + "\"");
    assertThat(theStoredChargeOfTheRefund(idIn(bodyOf(refunded)))).isEqualTo(charge);
  }

  @Test
  void answers_a_charge_this_simulator_never_minted_as_a_missing_resource() {
    HttpResponse<byte[]> missing = get(CHARGES + "/" + StripeChargeMinting.ID_PREFIX + "nothing");

    assertThat(missing.statusCode()).isEqualTo(404);
    assertThat(bodyOf(missing))
        .contains("\"code\":\"" + StripeErrors.RESOURCE_MISSING + "\"")
        .contains("\"param\":\"charge\"");
  }

  @Test
  void resetting_the_test_run_takes_the_charges_of_that_run_with_it() {
    String intent = idIn(bodyOf(post(PAYMENT_INTENTS, aHoldOf("1000"))));
    String charge = latestChargeIn(bodyOf(get(PAYMENT_INTENTS + "/" + intent)));
    assertThat(storedCharges()).contains(charge);

    reset();

    assertThat(storedCharges()).isEmpty();
    assertThat(claimedChargeIds()).isZero();
  }

  @Test
  void a_charge_carries_no_client_secret_of_the_payment_it_belongs_to() {
    String intent = idIn(bodyOf(post(PAYMENT_INTENTS, aHoldOf("1000"))));
    String charge = latestChargeIn(bodyOf(get(PAYMENT_INTENTS + "/" + intent)));

    HttpResponse<byte[]> retrieved = get(CHARGES + "/" + charge);

    assertThat(bodyOf(retrieved)).doesNotContain(StripeFields.CLIENT_SECRET);
    assertThat(theTrailOf(charge)).noneMatch(body -> body.contains(StripeFields.CLIENT_SECRET));
  }

  private List<String> storedCharges() {
    return jdbc.queryForList(
        "select charge_id from stripe_charge where test_run_id = ?", String.class, RUN.value());
  }

  private int claimedChargeIds() {
    Integer claimed =
        jdbc.queryForObject(
            "select count(*) from stripe_charge_id_claim where test_run_id = ?",
            Integer.class,
            RUN.value());
    return claimed == null ? 0 : claimed;
  }

  private String theStoredChargeOfTheRefund(String refundId) {
    return jdbc.queryForObject(
        "select charge_id from stripe_refund where test_run_id = ? and refund_id = ?",
        String.class,
        RUN.value(),
        refundId);
  }

  private List<String> theTrailOf(String providerObjectId) {
    return jdbc.queryForList(
        "select sanitized_body from simulator_audit_record where provider_object_id = ?",
        String.class,
        providerObjectId);
  }

  private static Map<String, String> aHoldOf(String amount) {
    return Map.of(
        "amount", amount,
        "currency", "pln",
        "capture_method", "manual",
        "payment_method", CARD,
        "confirm", "true");
  }

  private void configure() {
    http.put()
        .uri("/__simulator/stripe/config")
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

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<byte[]> get(String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + SECRET_KEY)
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

  private static String idIn(String body) {
    return between(body, "\"id\":\"", "\"");
  }

  private static String latestChargeIn(String body) {
    return between(body, "\"latest_charge\":\"", "\"");
  }

  private static String between(String body, String opening, String closing) {
    int from = body.indexOf(opening);
    assertThat(from).isNotNegative();
    int start = from + opening.length();
    return body.substring(start, body.indexOf(closing, start));
  }
}
