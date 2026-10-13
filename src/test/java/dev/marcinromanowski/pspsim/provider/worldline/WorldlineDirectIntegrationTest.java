package dev.marcinromanowski.pspsim.provider.worldline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.fault.AHeldResponse;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class WorldlineDirectIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("worldline-direct-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-22T12:00:00Z");

  private static final String ACCOUNT = "account-1";

  private static final String MERCHANT = "pspid-1";

  private static final String API_KEY = "worldline-api-key";

  private static final String API_SECRET = "worldline-api-secret";

  @LocalServerPort private int port;

  @Autowired private DelayedDeliveries delays;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheMerchant() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(List.of(anAccount()));
  }

  @AfterEach
  void unconfigureTheMerchant() {
    reset();
    configure(List.of());
  }

  @Test
  void
      a_tokenization_a_token_a_hold_and_a_capture_over_the_wire_leave_a_payment_read_as_captured() {
    HttpResponse<String> opened = post(theHostedTokenizations(), "{}");
    String hostedTokenizationId = valueIn(opened.body(), WorldlineFields.HOSTED_TOKENIZATION_ID);

    completeAsThePayer(hostedTokenizationId);

    HttpResponse<String> read = get(theHostedTokenization(hostedTokenizationId));
    String token = valueIn(read.body(), WorldlineFields.ID);

    HttpResponse<String> held = post(thePayments(), aHoldOf(token));
    String paymentId = valueIn(held.body(), WorldlineFields.ID);

    HttpResponse<String> captured =
        post(thePayment(paymentId) + WorldlineApiController.CAPTURE, "{\"amount\":5000}");
    String minted = valueIn(captured.body(), WorldlineFields.ID);

    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(opened.headers().firstValue("content-type").orElseThrow())
        .startsWith(WorldlineFields.JSON);
    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(read.body()).contains(WorldlineStatus.TOKEN_CREATED);
    assertThat(token).isNotEmpty();
    assertThat(held.statusCode()).isEqualTo(201);
    assertThat(held.headers().firstValue(WorldlineFields.LOCATION)).contains(thePayment(paymentId));
    assertThat(held.body()).contains(WorldlineStatus.PENDING_CAPTURE);
    assertThat(captured.statusCode()).isEqualTo(201);
    assertThat(minted).isNotEqualTo(paymentId);
    assertThat(stateOf(paymentId))
        .containsEntry("providerObjectId", paymentId)
        .containsEntry("nativeStatus", WorldlineStatus.CAPTURED)
        .containsEntry("normalizedStatus", "SUCCEEDED")
        .containsEntry("amount", "5000")
        .containsEntry("currency", "PLN");
  }

  @Test
  void the_control_read_resolves_the_identifier_a_capture_minted_and_names_which_one_it_was() {
    String paymentId = aHold();
    String minted =
        valueIn(
            post(thePayment(paymentId) + WorldlineApiController.CAPTURE, "{\"amount\":2000}")
                .body(),
            WorldlineFields.ID);

    Map<String, Object> byTheMinted = stateOf(minted);
    Map<String, Object> byTheOriginal = stateOf(paymentId);

    assertThat(byTheMinted)
        .containsEntry("providerObjectId", paymentId)
        .containsEntry("addressedBy", minted);
    assertThat(byTheMinted.get("answersTo")).isEqualTo(List.of(paymentId, minted));
    assertThat(byTheOriginal).containsEntry("addressedBy", paymentId);
    assertThat(byTheOriginal.get("answersTo")).isEqualTo(List.of(paymentId, minted));
  }

  @Test
  void the_trail_of_one_payment_is_the_same_rows_whichever_identifier_it_is_asked_for_by() {
    String paymentId = aHold();
    String minted =
        valueIn(
            post(thePayment(paymentId) + WorldlineApiController.CAPTURE, "{\"amount\":5000}")
                .body(),
            WorldlineFields.ID);

    List<Map<String, Object>> byTheOriginal = auditOf(paymentId);
    List<Map<String, Object>> byTheMinted = auditOf(minted);

    assertThat(byTheOriginal).isNotEmpty().isEqualTo(byTheMinted);
    assertThat(byTheOriginal)
        .extracting(record -> record.get("providerObjectId"))
        .contains(paymentId, minted);
  }

  @Test
  void a_capture_addressed_by_the_identifier_an_earlier_capture_minted_reaches_the_same_payment() {
    String paymentId = aHold();
    String first =
        valueIn(
            post(thePayment(paymentId) + WorldlineApiController.CAPTURE, "{\"amount\":2000}")
                .body(),
            WorldlineFields.ID);
    HttpResponse<String> second =
        post(thePayment(first) + WorldlineApiController.CAPTURE, "{\"amount\":3000}");

    assertThat(second.statusCode()).isEqualTo(201);
    assertThat(stateOf(paymentId))
        .containsEntry("nativeStatus", WorldlineStatus.CAPTURED)
        .containsEntry("providerObjectId", paymentId);
    assertThat(stateOf(paymentId).get("answersTo"))
        .isEqualTo(List.of(paymentId, first, valueIn(second.body(), WorldlineFields.ID)));
  }

  @Test
  void a_hold_a_merchant_cancels_reads_back_as_cancelled_under_both_of_its_identifiers() {
    String paymentId = aHold();
    HttpResponse<String> cancelled =
        post(thePayment(paymentId) + WorldlineApiController.CANCEL, "{}");
    String minted = valueIn(cancelled.body(), WorldlineFields.ID);

    assertThat(cancelled.statusCode()).isEqualTo(200);
    assertThat(cancelled.body()).contains(WorldlineStatus.CANCELLED);
    assertThat(stateOf(minted)).containsEntry("normalizedStatus", "CANCELLED");
    assertThat(stateOf(paymentId)).containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void an_unsigned_call_of_an_account_that_verifies_the_signature_is_answered_403_and_not_401() {
    configure(List.of(anAccountThatVerifiesTheSignature()));
    try {
      HttpResponse<String> refused = post(theHostedTokenizations(), "{}");
      assertThat(refused.statusCode()).isEqualTo(403);
      assertThat(refused.body()).contains(WorldlineErrors.ACCESS_TO_MERCHANT_NOT_ALLOWED);
    } finally {
      configure(List.of(anAccount()));
    }
  }

  @Test
  void a_repeat_under_one_idempotence_key_replays_the_answer_and_creates_no_second_payment() {
    String body = aHoldOf(aStoredToken());
    Map<String, String> key =
        Map.of(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, "k-1");
    HttpResponse<String> first = post(thePayments(), body, key);
    HttpResponse<String> repeat = post(thePayments(), body, key);

    assertThat(repeat.statusCode()).isEqualTo(first.statusCode());
    assertThat(repeat.body()).isEqualTo(first.body());
    assertThat(repeat.headers().firstValue(WorldlineFields.IDEMPOTENCE_REQUEST_TIMESTAMP))
        .contains(Long.toString(FIXED_NOW.toEpochMilli()));
    assertThat(objects()).hasSize(2);
  }

  @Test
  void both_object_kinds_of_this_provider_are_listed_newest_first() {
    String paymentId = aHold();
    assertThat(objects())
        .extracting(object -> object.get("providerObjectId"))
        .startsWith(paymentId)
        .hasSize(2);
  }

  @Test
  void the_two_calls_of_a_hold_and_its_capture_are_in_the_trail_with_the_status_each_answered() {
    String paymentId = aHold();
    post(thePayment(paymentId) + WorldlineApiController.CAPTURE, "{\"amount\":5000}");
    List<Map<String, Object>> records = auditRecords();
    assertThat(records)
        .extracting(record -> record.get("path"))
        .contains(thePayments(), thePayment(paymentId) + WorldlineApiController.CAPTURE);
    assertThat(records)
        .filteredOn(record -> "OUTBOUND_RESPONSE".equals(record.get("direction")))
        .allSatisfy(record -> assertThat(record.get("status")).isNotNull());
  }

  @Test
  void a_rule_that_delays_the_answer_holds_the_whole_response_and_still_answers_it() {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "action",
            Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 400)));

    long before = System.nanoTime();
    HttpResponse<String> opened = post(theHostedTokenizations(), "{}");
    Duration waited = Duration.ofNanos(System.nanoTime() - before);

    assertThat(opened.statusCode()).isEqualTo(200);
    assertThat(opened.body()).contains(WorldlineFields.HOSTED_TOKENIZATION_ID);
    assertThat(waited).isGreaterThanOrEqualTo(Duration.ofMillis(300));
  }

  @Test
  void resetting_a_test_run_answers_a_held_response_instead_of_delivering_stale_bytes()
      throws Exception {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "action",
            Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE", "delayMs", 5000)));

    ExecutorService client = Executors.newSingleThreadExecutor();
    try {
      Future<HttpResponse<String>> held = client.submit(() -> post(theHostedTokenizations(), "{}"));
      AHeldResponse.isHeldBy(delays);
      reset();

      HttpResponse<String> answer = held.get(15, TimeUnit.SECONDS);

      assertThat(answer.statusCode()).isEqualTo(503);
      assertThat(answer.body())
          .contains("held this response for a scenario delay")
          .contains("was reset before the delay elapsed");
    } finally {
      client.shutdownNow();
      configure(List.of(anAccount()));
    }
  }

  @Test
  void the_inspector_reads_back_every_exchange_this_run_recorded_under_an_idempotence_key() {
    String body = aHoldOf(aStoredToken());
    Map<String, String> key =
        Map.of(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, "k-2");
    post(thePayments(), body, key);
    post(thePayments(), body, key);

    Map<String, Object> inspector =
        http.get()
            .uri("/__simulator/worldline/idempotency?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(String.valueOf(inspector))
        .contains("k-2")
        .contains("FIRST_REQUEST")
        .contains("TRUE_IDEMPOTENT_REPLAY");
  }

  @Test
  void a_key_a_request_never_finished_under_is_released_and_the_next_request_is_a_first_one() {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "match",
            Map.of("operation", WorldlineOperations.CREATE_PAYMENT),
            "action",
            Map.of(
                "type",
                "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
                "fault",
                "SERVER_ERROR",
                "httpStatus",
                502)));
    String body = aHoldOf(aStoredToken());
    Map<String, String> key =
        Map.of(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT, "k-3");

    HttpResponse<String> answeredInstead = post(thePayments(), body, key);
    reset();
    configure(List.of(anAccount()));
    HttpResponse<String> afterwards = post(thePayments(), aHoldOf(aStoredToken()), key);

    assertThat(answeredInstead.statusCode()).isEqualTo(502);
    assertThat(answeredInstead.headers().firstValue("Content-Type"))
        .contains("text/plain;charset=utf-8");
    assertThat(answeredInstead.body())
        .startsWith("502 was answered in front of the provider.")
        .doesNotContain("errors");
    assertThat(afterwards.statusCode()).isEqualTo(201);
  }

  private void createScenario(Map<String, Object> declared) {
    http.post()
        .uri("/__simulator/worldline/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(declared)
        .retrieve()
        .toBodilessEntity();
  }

  private String aHold() {
    return valueIn(post(thePayments(), aHoldOf(aStoredToken())).body(), WorldlineFields.ID);
  }

  private String aStoredToken() {
    String hostedTokenizationId =
        valueIn(
            post(theHostedTokenizations(), "{}").body(), WorldlineFields.HOSTED_TOKENIZATION_ID);
    completeAsThePayer(hostedTokenizationId);
    return valueIn(get(theHostedTokenization(hostedTokenizationId)).body(), WorldlineFields.ID);
  }

  private void completeAsThePayer(String hostedTokenizationId) {
    http.post()
        .uri("/__simulator/worldline/hosted/" + hostedTokenizationId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "outcome",
                "PAID",
                "card",
                WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS))
        .retrieve()
        .toBodilessEntity();
  }

  private static String aHoldOf(String token) {
    return "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"},"
        + "\"references\":{\"merchantReference\":\"order-123\"}},"
        + "\"cardPaymentMethodSpecificInput\":{\"token\":\""
        + token
        + "\",\"authorizationMode\":\"PRE_AUTHORIZATION\"}}";
  }

  private static String theHostedTokenizations() {
    return WorldlineApiController.theHostedTokenizationsOf(MERCHANT);
  }

  private static String theHostedTokenization(String hostedTokenizationId) {
    return WorldlineApiController.theHostedTokenizationOf(MERCHANT, hostedTokenizationId);
  }

  private static String thePayments() {
    return WorldlineApiController.thePaymentsOf(MERCHANT);
  }

  private static String thePayment(String paymentId) {
    return WorldlineApiController.thePaymentResourceOf(MERCHANT, paymentId);
  }

  private static Map<String, Object> anAccount() {
    return Map.of(
        "accountId",
        ACCOUNT,
        "configuration",
        Map.of(
            WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT,
            WorldlineMerchant.API_KEY_PROPERTY, API_KEY,
            WorldlineMerchant.API_SECRET_PROPERTY, API_SECRET,
            WorldlineAuthentication.SETTING,
                WorldlineAuthentication.THE_SIGNATURE_IS_NOT_VERIFIED.name()));
  }

  private static Map<String, Object> anAccountThatVerifiesTheSignature() {
    return Map.of(
        "accountId",
        ACCOUNT,
        "configuration",
        Map.of(
            WorldlineMerchant.MERCHANT_ID_PROPERTY, MERCHANT,
            WorldlineMerchant.API_KEY_PROPERTY, API_KEY,
            WorldlineMerchant.API_SECRET_PROPERTY, API_SECRET,
            WorldlineAuthentication.SETTING,
                WorldlineAuthentication.THE_SIGNATURE_IS_VERIFIED.name()));
  }

  private void configure(List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/worldline/config")
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
        .uri("/__simulator/worldline/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/worldline/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<Map<String, Object>> objects() {
    return http.get()
        .uri("/__simulator/worldline/objects?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> auditRecords() {
    return http.get()
        .uri("/__simulator/worldline/audit?origin=PROVIDER_API&testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> auditOf(String providerObjectId) {
    return http.get()
        .uri(
            "/__simulator/worldline/audit?testRunId="
                + RUN.value()
                + "&providerObjectId="
                + providerObjectId)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private HttpResponse<String> post(String path, String body) {
    return post(path, body, Map.of());
  }

  private HttpResponse<String> post(String path, String body, Map<String, String> headers) {
    Map<String, String> sent = new LinkedHashMap<>(headers);
    sent.put("Content-Type", WorldlineFields.JSON);
    sent.put(WorldlineFields.AUTHORIZATION, WorldlineSignature.SCHEME + API_KEY + ":unverified");
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .POST(HttpRequest.BodyPublishers.ofString(body)),
        sent);
  }

  private HttpResponse<String> get(String path) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET(),
        Map.of(WorldlineFields.AUTHORIZATION, WorldlineSignature.SCHEME + API_KEY + ":unverified"));
  }

  private HttpResponse<String> send(HttpRequest.Builder request, Map<String, String> headers) {
    try {
      HttpRequest.Builder built = request;
      for (Map.Entry<String, String> header : headers.entrySet()) {
        built = built.header(header.getKey(), header.getValue());
      }
      return wire.send(built.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String valueIn(String json, String field) {
    String opening = "\"" + field + "\":\"";
    int at = json.indexOf(opening);
    if (at < 0) {
      return "";
    }
    return json.substring(at + opening.length(), json.indexOf('"', at + opening.length()));
  }
}
