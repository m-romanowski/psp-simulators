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
import org.springframework.web.client.RestClient;

class StripePaymentMethodIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-payment-method-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51PaymentMethodSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String PAYMENT_METHODS = "/v1/payment_methods";
  private static final String CUSTOMERS = "/v1/customers";
  private static final String PAYMENT_INTENTS = "/v1/payment_intents";

  private static final String A_CUSTOMER = "cus_with_two_cards";
  private static final String FIRST_CARD = "pm_the_first";
  private static final String SECOND_CARD = "pm_the_second";
  private static final String AN_UNATTACHED_CARD = "pm_saved_to_nobody";

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
  void two_cards_seeded_from_a_screen_for_one_customer_are_both_listed_by_both_published_routes() {
    seedFromTheScreen("first", FIRST_CARD, A_CUSTOMER);
    seedFromTheScreen("second", SECOND_CARD, A_CUSTOMER);

    HttpResponse<byte[]> ofTheCustomer = get(customerMethods(A_CUSTOMER));
    HttpResponse<byte[]> filtered = get(PAYMENT_METHODS + "?customer=" + A_CUSTOMER);

    assertThat(ofTheCustomer.statusCode()).isEqualTo(200);
    assertThat(idsIn(bodyOf(ofTheCustomer))).containsExactlyInAnyOrder(FIRST_CARD, SECOND_CARD);
    assertThat(idsIn(bodyOf(filtered))).containsExactlyInAnyOrder(FIRST_CARD, SECOND_CARD);
  }

  @Test
  void a_card_saved_to_no_customer_is_retrieved_with_a_customer_of_null_over_the_wire() {
    seedFromTheScreen("unattached", AN_UNATTACHED_CARD, null);

    HttpResponse<byte[]> retrieved = get(PAYMENT_METHODS + "/" + AN_UNATTACHED_CARD);

    assertThat(retrieved.statusCode()).isEqualTo(200);
    assertThat(bodyOf(retrieved))
        .contains("\"object\":\"payment_method\"")
        .contains("\"customer\":null")
        .contains("\"last4\":\"1111\"");
  }

  @Test
  void a_detached_card_leaves_its_customers_list_and_cannot_be_used_for_a_payment_again() {
    seedFromTheScreen("first", FIRST_CARD, A_CUSTOMER);
    seedFromTheScreen("second", SECOND_CARD, A_CUSTOMER);

    HttpResponse<byte[]> detached = post(PAYMENT_METHODS + "/" + FIRST_CARD + "/detach", Map.of());
    HttpResponse<byte[]> listed = get(customerMethods(A_CUSTOMER));
    HttpResponse<byte[]> again = post(PAYMENT_METHODS + "/" + FIRST_CARD + "/detach", Map.of());
    HttpResponse<byte[]> charged =
        post(
            PAYMENT_INTENTS,
            Map.of(
                "amount", "1000",
                "currency", "usd",
                "confirm", "true",
                "payment_method", FIRST_CARD));

    assertThat(detached.statusCode()).isEqualTo(200);
    assertThat(bodyOf(detached)).contains("\"customer\":null");
    assertThat(idsIn(bodyOf(listed))).containsExactly(SECOND_CARD);
    assertThat(again.statusCode()).isEqualTo(400);
    assertThat(bodyOf(again)).contains("permanent and irreversible");
    assertThat(charged.statusCode()).isEqualTo(400);
    assertThat(bodyOf(charged)).contains("can no longer be used for payments");
  }

  @Test
  void deleting_the_customer_detaches_both_of_its_cards_and_leaves_them_readable_by_their_own_id() {
    seedFromTheScreen("first", FIRST_CARD, A_CUSTOMER);
    seedFromTheScreen("second", SECOND_CARD, A_CUSTOMER);

    HttpResponse<byte[]> deleted = delete(CUSTOMERS + "/" + A_CUSTOMER);

    assertThat(deleted.statusCode()).isEqualTo(200);
    assertThat(idsIn(bodyOf(get(customerMethods(A_CUSTOMER))))).isEmpty();
    assertThat(bodyOf(get(PAYMENT_METHODS + "/" + FIRST_CARD))).contains("\"customer\":null");
    assertThat(bodyOf(get(PAYMENT_METHODS + "/" + SECOND_CARD))).contains("\"customer\":null");
  }

  @Test
  void the_payment_methods_screen_keeps_a_detached_card_and_shows_that_it_was_ended() {
    seedFromTheScreen("first", FIRST_CARD, A_CUSTOMER);
    post(PAYMENT_METHODS + "/" + FIRST_CARD + "/detach", Map.of());

    String screen = screen("/ui/payment-methods");

    assertThat(screen).contains(FIRST_CARD).contains(A_CUSTOMER);
    assertThat(screen).contains(StripePaymentMethodObject.A_METHOD_NO_CUSTOMER_HOLDS);
    assertThat(screen).contains("CANCELLED");
  }

  @Test
  void the_payment_methods_screen_shows_the_one_customer_both_seeded_cards_can_be_listed_by() {
    seedFromTheScreen("first", FIRST_CARD, A_CUSTOMER);
    seedFromTheScreen("second", SECOND_CARD, A_CUSTOMER);

    String screen = screen("/ui/payment-methods");

    assertThat(screen).contains(FIRST_CARD).contains(SECOND_CARD);
    assertThat(countOf(screen, A_CUSTOMER)).isEqualTo(2);
  }

  @Test
  void a_customer_this_account_holds_none_of_is_refused_by_the_route_that_addresses_one() {
    seedFromTheScreen("first", FIRST_CARD, A_CUSTOMER);

    HttpResponse<byte[]> addressed = get(customerMethods("cus_nobody_created"));
    HttpResponse<byte[]> filtered = get(PAYMENT_METHODS + "?customer=cus_nobody_created");

    assertThat(addressed.statusCode()).isEqualTo(404);
    assertThat(bodyOf(addressed)).contains("\"code\":\"resource_missing\"");
    assertThat(filtered.statusCode()).isEqualTo(200);
    assertThat(idsIn(bodyOf(filtered))).isEmpty();
  }

  @Test
  void a_wallet_seeded_from_a_screen_is_served_as_paypal_with_no_payer_a_tester_could_have_set() {
    Map<String, String> form = aCardForm("a-wallet", "pm_a_wallet", A_CUSTOMER);
    form.put("type", "wallet");
    form.remove("brand");
    form.remove("last4");
    form.remove("expMonth");
    form.remove("expYear");
    form.remove("funding");
    post("/ui/payment-methods/seed", form);

    HttpResponse<byte[]> retrieved = get(PAYMENT_METHODS + "/pm_a_wallet");

    assertThat(retrieved.statusCode()).isEqualTo(200);
    assertThat(bodyOf(retrieved))
        .contains("\"type\":\"paypal\"")
        .contains("\"paypal\":{\"payer_email\":null,\"payer_id\":null,\"country\":null}");
    assertThat(screen("/ui/payment-methods")).doesNotContain("payer_email");
  }

  @Test
  void removing_the_fixture_from_the_screen_takes_the_payment_method_off_the_published_route() {
    seedFromTheScreen("first", FIRST_CARD, A_CUSTOMER);
    String fixtureId = theFixtureIdOf(FIRST_CARD);

    Map<String, String> removal = new LinkedHashMap<>();
    removal.put("provider", "stripe");
    removal.put("testRunId", RUN.value());
    removal.put("fixtureId", fixtureId);
    removal.put("confirm", "yes");
    post("/ui/payment-methods/remove", removal);

    assertThat(get(PAYMENT_METHODS + "/" + FIRST_CARD).statusCode()).isEqualTo(404);
    assertThat(get(CUSTOMERS + "/" + A_CUSTOMER).statusCode()).isEqualTo(200);
  }

  private String theFixtureIdOf(String paymentMethodId) {
    List<Map<String, Object>> seeded =
        http.get()
            .uri("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    for (Map<String, Object> fixture : seeded) {
      Object ids = fixture.get("providerObjects");
      if (ids instanceof Map<?, ?> named && paymentMethodId.equals(named.get("paymentMethodId"))) {
        return String.valueOf(fixture.get("fixtureId"));
      }
    }
    throw new IllegalStateException("no fixture was seeded for " + paymentMethodId);
  }

  private void seedFromTheScreen(String alias, String paymentMethodId, String customerId) {
    HttpResponse<byte[]> seeded =
        post("/ui/payment-methods/seed", aCardForm(alias, paymentMethodId, customerId));
    assertThat(seeded.statusCode()).isEqualTo(302);
  }

  private static Map<String, String> aCardForm(
      String alias, String paymentMethodId, String customerId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN.value());
    form.put("merchantId", MERCHANT);
    form.put("ownerId", "owner-1");
    form.put("type", "card");
    form.put("alias", alias);
    form.put("brand", "visa");
    form.put("last4", "1111");
    form.put("expMonth", "12");
    form.put("expYear", "2030");
    form.put("funding", "credit");
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", paymentMethodId);
    if (customerId != null) {
      form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "customerId", customerId);
    }
    return form;
  }

  private static List<String> idsIn(String body) {
    List<String> ids = new ArrayList<>();
    String marker = "{\"id\":\"";
    int from = 0;
    while (true) {
      int at = body.indexOf(marker, from);
      if (at < 0) {
        return List.copyOf(ids);
      }
      int start = at + marker.length();
      int end = body.indexOf('"', start);
      ids.add(body.substring(start, end));
      from = end;
    }
  }

  private static int countOf(String text, String fragment) {
    int found = 0;
    int from = 0;
    while (true) {
      int at = text.indexOf(fragment, from);
      if (at < 0) {
        return found;
      }
      found++;
      from = at + fragment.length();
    }
  }

  private static String customerMethods(String customerId) {
    return CUSTOMERS + "/" + customerId + "/payment_methods";
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

  private String screen(String path) {
    HttpResponse<byte[]> answer =
        sent(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + path
                            + "?provider=stripe&testRunId="
                            + RUN.value()))
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
}
