package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.PaymentMethodForm;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class StripeCustomerIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-customer-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51CustomerSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String CUSTOMERS = "/v1/customers";
  private static final String PAYMENT_INTENTS = "/v1/payment_intents";

  private static final String AN_EMAIL = "payer@example.test";
  private static final String ANOTHER_EMAIL = "second-payer@example.test";

  @LocalServerPort private int port;

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
  void creates_retrieves_updates_and_deletes_a_customer_over_the_wire() {
    HttpResponse<byte[]> created = post(CUSTOMERS, Map.of("email", AN_EMAIL));
    String customerId = valueIn(bodyOf(created), "id");

    HttpResponse<byte[]> retrieved = get(CUSTOMERS + "/" + customerId);
    HttpResponse<byte[]> updated =
        post(CUSTOMERS + "/" + customerId, Map.of("email", ANOTHER_EMAIL));
    HttpResponse<byte[]> deleted = delete(CUSTOMERS + "/" + customerId);

    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.headers().firstValue("content-type").orElseThrow())
        .startsWith("application/json");
    assertThat(customerId).startsWith(CreateCustomer.ID_PREFIX);
    assertThat(bodyOf(created))
        .contains("\"object\":\"customer\"")
        .contains("\"email\":\"" + AN_EMAIL + "\"");
    assertThat(bodyOf(retrieved)).contains("\"email\":\"" + AN_EMAIL + "\"");
    assertThat(bodyOf(updated)).contains("\"email\":\"" + ANOTHER_EMAIL + "\"");
    assertThat(deleted.statusCode()).isEqualTo(200);
    assertThat(bodyOf(deleted))
        .isEqualTo("{\"id\":\"" + customerId + "\",\"object\":\"customer\",\"deleted\":true}");
  }

  @Test
  void a_deleted_customer_is_still_retrievable_and_refuses_every_further_operation() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    delete(CUSTOMERS + "/" + customerId);

    HttpResponse<byte[]> retrieved = get(CUSTOMERS + "/" + customerId);
    HttpResponse<byte[]> updated =
        post(CUSTOMERS + "/" + customerId, Map.of("email", ANOTHER_EMAIL));
    HttpResponse<byte[]> deletedAgain = delete(CUSTOMERS + "/" + customerId);

    assertThat(retrieved.statusCode()).isEqualTo(200);
    assertThat(bodyOf(retrieved)).contains("\"deleted\":true");
    assertThat(updated.statusCode()).isEqualTo(400);
    assertThat(bodyOf(updated)).contains("prevents any further operations");
    assertThat(deletedAgain.statusCode()).isEqualTo(400);
    assertThat(bodyOf(deletedAgain)).contains("prevents any further operations");
  }

  @Test
  void a_customer_id_this_run_holds_nothing_under_is_refused_on_every_route_that_takes_one() {
    String absent = CreateCustomer.ID_PREFIX + "0000000000000000";

    assertThat(get(CUSTOMERS + "/" + absent).statusCode()).isEqualTo(404);
    assertThat(post(CUSTOMERS + "/" + absent, Map.of("email", AN_EMAIL)).statusCode())
        .isEqualTo(404);
    assertThat(delete(CUSTOMERS + "/" + absent).statusCode()).isEqualTo(404);
    assertThat(bodyOf(get(CUSTOMERS + "/" + absent)))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"customer\"");
  }

  @Test
  void the_control_read_surface_lists_a_customer_beside_the_payments_of_its_run() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    String intentId = valueIn(bodyOf(post(PAYMENT_INTENTS, aHold(customerId))), "id");

    assertThat(idsOn(objects())).contains(customerId, intentId);
    assertThat(stateOf(customerId))
        .containsEntry("providerObjectId", customerId)
        .containsEntry("nativeStatus", StripeCustomerObject.A_CUSTOMER_STRIPE_STILL_HOLDS)
        .containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(stateOf(customerId).get("relatedObjects"))
        .isEqualTo(Map.of(StripeCustomerObject.ACCOUNT, MERCHANT));
  }

  @Test
  void a_deleted_customer_is_still_on_the_control_read_surface_and_says_it_was_deleted() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");
    delete(CUSTOMERS + "/" + customerId);

    assertThat(stateOf(customerId))
        .containsEntry("nativeStatus", StripeCustomerObject.A_CUSTOMER_STRIPE_HAS_DELETED)
        .containsEntry("normalizedStatus", "CANCELLED");
    assertThat(idsOn(objects())).contains(customerId);
  }

  @Test
  void a_tester_reads_a_customer_back_on_the_payments_screen_without_a_single_call() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");

    String listing = screen("/ui/objects", "");
    String opened = screen("/ui/objects", "&providerObjectId=" + customerId);

    assertThat(listing).contains(customerId);
    assertThat(opened)
        .contains(customerId)
        .contains(StripeCustomerObject.A_CUSTOMER_STRIPE_STILL_HOLDS)
        .contains("SUCCEEDED");
  }

  @Test
  void a_tester_who_seeds_a_payment_method_for_a_customer_creates_that_customer_from_a_screen() {
    HttpResponse<byte[]> seeded =
        post("/ui/payment-methods/seed", aCardOwnedBy("cus_from_a_screen"));

    HttpResponse<byte[]> retrieved = get(CUSTOMERS + "/cus_from_a_screen");

    assertThat(seeded.statusCode()).isEqualTo(302);
    assertThat(screen("/ui/objects", "")).contains("cus_from_a_screen");
    assertThat(idsOn(objects())).contains("cus_from_a_screen");
    assertThat(retrieved.statusCode()).isEqualTo(200);
    assertThat(bodyOf(retrieved))
        .contains("\"id\":\"cus_from_a_screen\"")
        .contains("\"object\":\"customer\"");
  }

  @Test
  void a_customer_seeded_under_an_account_this_run_configures_none_of_is_refused_not_stored() {
    Map<String, Object> underAGhost = new LinkedHashMap<>();
    underAGhost.put("testRunId", RUN.value());
    underAGhost.put("merchantId", "ghost-account");
    underAGhost.put("ownerId", "owner-1");
    underAGhost.put("type", "card");
    underAGhost.put("alias", "a-card-of-a-ghost");
    underAGhost.put(
        "card",
        Map.of(
            "brand",
            "visa",
            "last4",
            "1111",
            "expMonth",
            12,
            "expYear",
            2030,
            "funding",
            "credit"));
    underAGhost.put(
        "providerIds",
        Map.of("paymentMethodId", "pm_of_a_ghost", "customerId", "cus_ghost_unreachable"));

    ResponseEntity<Map<String, Object>> refused =
        http.post()
            .uri("/__simulator/stripe/fixtures/payment-methods")
            .contentType(MediaType.APPLICATION_JSON)
            .body(underAGhost)
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .contains(StripeFixtureCards.CUSTOMER_PROPERTY)
        .doesNotContain("cus_ghost_unreachable");
    assertThat(refused.getBody().get("properties"))
        .isEqualTo(List.of(StripeFixtureCards.CUSTOMER_PROPERTY));
    assertThat(idsOn(objects())).isEmpty();
    assertThat(get(CUSTOMERS + "/cus_ghost_unreachable").statusCode()).isEqualTo(404);
  }

  @Test
  void a_customer_seeded_under_the_configured_account_is_both_listed_and_retrievable_by_its_key() {
    post("/ui/payment-methods/seed", aCardOwnedBy("cus_the_key_reaches"));

    HttpResponse<byte[]> retrieved = get(CUSTOMERS + "/cus_the_key_reaches");

    assertThat(idsOn(objects())).containsExactlyInAnyOrder("cus_the_key_reaches", "pm_seeded");
    assertThat(stateOf("cus_the_key_reaches").get("relatedObjects"))
        .isEqualTo(Map.of(StripeCustomerObject.ACCOUNT, MERCHANT));
    assertThat(retrieved.statusCode()).isEqualTo(200);
    assertThat(bodyOf(retrieved)).contains("\"id\":\"cus_the_key_reaches\"");
  }

  @Test
  void a_customer_seeded_from_a_screen_is_deleted_by_the_published_route_and_stays_readable() {
    post("/ui/payment-methods/seed", aCardOwnedBy("cus_from_a_screen"));

    HttpResponse<byte[]> deleted = delete(CUSTOMERS + "/cus_from_a_screen");

    assertThat(deleted.statusCode()).isEqualTo(200);
    assertThat(stateOf("cus_from_a_screen"))
        .containsEntry("nativeStatus", StripeCustomerObject.A_CUSTOMER_STRIPE_HAS_DELETED);
    assertThat(screen("/ui/objects", "")).contains("cus_from_a_screen");
  }

  @Test
  void a_customer_holds_the_second_payment_method_a_screen_seeds_for_it_as_well_as_the_first() {
    post("/ui/payment-methods/seed", aCardOwnedBy("cus_from_a_screen"));

    Map<String, String> second = aCardOwnedBy("cus_from_a_screen");
    second.put("alias", "a-second-card");
    second.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", "pm_the_second");

    HttpResponse<byte[]> seeded = post("/ui/payment-methods/seed", second);

    assertThat(seeded.statusCode()).isEqualTo(302);
    assertThat(idsOn(objects())).contains("pm_seeded", "pm_the_second", "cus_from_a_screen");
  }

  @Test
  void a_second_card_under_one_payment_method_id_is_still_refused_as_already_claimed() {
    post("/ui/payment-methods/seed", aCardOwnedBy("cus_from_a_screen"));

    Map<String, String> second = aCardOwnedBy("cus_from_a_screen");
    second.put("alias", "a-second-card");

    HttpResponse<byte[]> refused = post("/ui/payment-methods/seed", second);

    assertThat(refused.statusCode()).isEqualTo(200);
    assertThat(bodyOf(refused)).contains("already claimed");
  }

  @Test
  void resetting_the_test_run_takes_its_customers_off_every_read_surface() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");

    reset();

    assertThat(get(CUSTOMERS + "/" + customerId).statusCode()).isEqualTo(404);
    assertThat(stateStatusOf(customerId)).isEqualTo(404);
  }

  @Test
  void the_trail_holds_the_request_and_the_answer_of_a_customer_call() {
    String customerId = valueIn(bodyOf(post(CUSTOMERS, Map.of("email", AN_EMAIL))), "id");

    List<Map<String, Object>> records = auditRecords();

    assertThat(records).hasSize(2);
    assertThat(records.get(0))
        .containsEntry("direction", "INBOUND_REQUEST")
        .containsEntry("path", CUSTOMERS);
    assertThat(records.get(1))
        .containsEntry("direction", "OUTBOUND_RESPONSE")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("providerObjectId", customerId)
        .containsEntry("status", 200);
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

  private static Map<String, String> aCardOwnedBy(String customerId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN.value());
    form.put("merchantId", MERCHANT);
    form.put("ownerId", "owner-1");
    form.put("type", "card");
    form.put("alias", "a-card-of-this-customer");
    form.put("brand", "visa");
    form.put("last4", "1111");
    form.put("expMonth", "12");
    form.put("expYear", "2030");
    form.put("funding", "credit");
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", "pm_seeded");
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "customerId", customerId);
    return form;
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

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/stripe/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
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

  private List<Map<String, Object>> objects() {
    return http.get()
        .uri("/__simulator/stripe/objects?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> auditRecords() {
    return http.get()
        .uri("/__simulator/stripe/audit?origin=PROVIDER_API&testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private static List<String> idsOn(List<Map<String, Object>> objects) {
    List<String> ids = new ArrayList<>();
    for (Map<String, Object> object : objects) {
      ids.add(String.valueOf(object.get("providerObjectId")));
    }
    return ids;
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
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20));
    if (path.startsWith("/v1")) {
      request = request.header("Authorization", "Bearer " + SECRET_KEY);
    }
    return sent(request.POST(HttpRequest.BodyPublishers.ofString(encoded(form))).build());
  }

  private HttpResponse<byte[]> get(String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + SECRET_KEY)
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build());
  }

  private HttpResponse<byte[]> delete(String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + SECRET_KEY)
            .timeout(Duration.ofSeconds(20))
            .DELETE()
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
