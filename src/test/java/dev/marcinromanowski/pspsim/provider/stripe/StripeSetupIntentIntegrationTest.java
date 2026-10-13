package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class StripeSetupIntentIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-setup-intent-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-21T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51SetupIntentSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String SETUP_INTENTS = "/v1/setup_intents";
  private static final String PAYMENT_INTENTS = "/v1/payment_intents";
  private static final String PAYMENT_METHODS = "/v1/payment_methods";
  private static final String CUSTOMERS = "/v1/customers";

  private static final String THE_CUSTOMER = "cus_saving_a_card";
  private static final String ANOTHER_CUSTOMER = "cus_with_a_card_already";
  private static final String THE_CARD = "pm_the_card_to_save";
  private static final String A_CARD_THEY_ALREADY_HAD = "pm_they_already_had";
  private static final String ANOTHER_CUSTOMERS_CARD = "pm_belonging_elsewhere";

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
  void a_card_saved_by_a_confirmed_setup_is_listed_under_the_customer_and_charged_off_session() {
    aCustomerThisAccountHolds();
    seedFromTheScreen("to-save", THE_CARD, null);

    HttpResponse<byte[]> created =
        post(
            SETUP_INTENTS,
            Map.of("customer", THE_CUSTOMER, "payment_method", THE_CARD, "usage", "off_session"));
    String setup = idIn(bodyOf(created));
    HttpResponse<byte[]> confirmed = post(SETUP_INTENTS + "/" + setup + "/confirm", Map.of());
    HttpResponse<byte[]> listed = get(CUSTOMERS + "/" + THE_CUSTOMER + "/payment_methods");
    HttpResponse<byte[]> charged =
        post(
            PAYMENT_INTENTS,
            Map.of(
                "amount", "1000",
                "currency", "usd",
                "confirm", "true",
                "off_session", "true",
                "customer", THE_CUSTOMER,
                "payment_method", THE_CARD));

    assertThat(confirmed.statusCode()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"status\":\"succeeded\"")
        .contains("\"usage\":\"off_session\"");
    assertThat(idsIn(bodyOf(listed))).containsExactlyInAnyOrder(THE_CARD, A_CARD_THEY_ALREADY_HAD);
    assertThat(charged.statusCode()).isEqualTo(200);
    assertThat(bodyOf(charged)).contains("\"status\":\"succeeded\"");
  }

  @Test
  void a_tester_saves_a_card_from_the_wizard_and_the_object_screen_shows_the_setup_that_did_it() {
    aCustomerThisAccountHolds();
    seedFromTheScreen("to-save", THE_CARD, null);

    HttpResponse<byte[]> started =
        post(
            "/ui/objects/start",
            Map.of(
                "provider",
                "stripe",
                "testRunId",
                RUN.value(),
                "accountId",
                MERCHANT,
                "startedAs",
                StripeOrigination.A_CARD_SET_UP_AND_CONFIRMED,
                TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "customer",
                THE_CUSTOMER,
                TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "payment_method",
                THE_CARD));

    assertThat(started.statusCode()).isEqualTo(302);
    String setup = theStartedIdIn(locationOf(started));
    assertThat(follow(started)).contains("This payment exists now").contains(setup);
    assertThat(objectScreen(setup))
        .contains("setup_intent succeeded")
        .contains(THE_CUSTOMER)
        .contains(THE_CARD)
        .doesNotContain(StripeClientSecret.INFIX);
    assertThat(idsIn(bodyOf(get(CUSTOMERS + "/" + THE_CUSTOMER + "/payment_methods"))))
        .contains(THE_CARD);
  }

  @Test
  void the_wizard_offers_setting_a_card_up_and_says_it_never_puts_a_client_secret_on_a_screen() {
    String whichKind = screen("/ui/objects/new");
    String whatItSays =
        screen(
            "/ui/objects/new",
            "&accountId="
                + MERCHANT
                + "&startedAs="
                + URLEncoder.encode(StripeOrigination.A_CARD_SET_UP_AND_CONFIRMED, UTF_8));

    assertThat(whichKind)
        .contains("Set a card up for a later charge and confirm it now")
        .contains("Set a card up for a later charge and leave the setup waiting");
    assertThat(whatItSays)
        .contains("serves no publishable-key surface")
        .contains("the payment method to set up")
        .doesNotContain(StripeClientSecret.INFIX);
  }

  @Test
  void a_setup_naming_a_card_another_customer_holds_is_refused_and_moves_no_attachment() {
    aCustomerThisAccountHolds();
    seedFromTheScreen("elsewhere", ANOTHER_CUSTOMERS_CARD, ANOTHER_CUSTOMER);

    String setup =
        idIn(
            bodyOf(
                post(
                    SETUP_INTENTS,
                    Map.of("customer", THE_CUSTOMER, "payment_method", ANOTHER_CUSTOMERS_CARD))));
    HttpResponse<byte[]> refused = post(SETUP_INTENTS + "/" + setup + "/confirm", Map.of());
    HttpResponse<byte[]> stillTheirs = get(PAYMENT_METHODS + "/" + ANOTHER_CUSTOMERS_CARD);

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(
            "Payment methods attached to other Customers cannot be used with this SetupIntent.");
    assertThat(bodyOf(stillTheirs)).contains("\"customer\":\"" + ANOTHER_CUSTOMER + "\"");
  }

  @Test
  void a_setup_naming_a_detached_card_is_refused_over_the_wire_before_anything_is_written() {
    seedFromTheScreen("to-detach", THE_CARD, ANOTHER_CUSTOMER);
    post(PAYMENT_METHODS + "/" + THE_CARD + "/detach", Map.of());

    String setup =
        idIn(
            bodyOf(
                post(
                    SETUP_INTENTS,
                    Map.of("customer", ANOTHER_CUSTOMER, "payment_method", THE_CARD))));
    HttpResponse<byte[]> refused = post(SETUP_INTENTS + "/" + setup + "/confirm", Map.of());

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(refused)).contains("permanent and irreversible");
    assertThat(bodyOf(get(PAYMENT_METHODS + "/" + THE_CARD))).contains("\"customer\":null");
  }

  @Test
  void an_unknown_setup_intent_is_resource_missing_and_an_unknown_card_is_too() {
    seedFromTheScreen("to-save", THE_CARD, null);

    HttpResponse<byte[]> noSuchSetup =
        post(SETUP_INTENTS + "/seti_nobody_created/confirm", Map.of("payment_method", THE_CARD));
    String setup = idIn(bodyOf(post(SETUP_INTENTS, Map.of("customer", THE_CUSTOMER))));
    HttpResponse<byte[]> noSuchCard =
        post(
            SETUP_INTENTS + "/" + setup + "/confirm", Map.of("payment_method", "pm_nobody_seeded"));

    assertThat(noSuchSetup.statusCode()).isEqualTo(404);
    assertThat(bodyOf(noSuchSetup)).contains("\"param\":\"intent\"");
    assertThat(noSuchCard.statusCode()).isEqualTo(404);
    assertThat(bodyOf(noSuchCard)).contains("\"param\":\"payment_method\"");
  }

  @Test
  void a_setup_a_scenario_declined_with_a_published_test_card_code_attaches_nothing() {
    aCustomerThisAccountHolds();
    seedFromTheScreen("to-save", THE_CARD, null);
    aRuleDeclining(StripeDecline.EXPIRED_CARD);

    String setup =
        idIn(
            bodyOf(
                post(SETUP_INTENTS, Map.of("customer", THE_CUSTOMER, "payment_method", THE_CARD))));
    HttpResponse<byte[]> declined = post(SETUP_INTENTS + "/" + setup + "/confirm", Map.of());

    assertThat(declined.statusCode()).isEqualTo(402);
    assertThat(bodyOf(declined))
        .contains("\"code\":\"expired_card\"")
        .contains("\"status\":\"requires_payment_method\"");
    assertThat(idsIn(bodyOf(get(CUSTOMERS + "/" + THE_CUSTOMER + "/payment_methods"))))
        .doesNotContain(THE_CARD);
  }

  @Test
  void payment_method_types_is_refused_on_create_and_answered_on_the_object_it_never_set() {
    HttpResponse<byte[]> refused =
        post(SETUP_INTENTS, Map.of("customer", THE_CUSTOMER, "payment_method_types[]", "card"));
    HttpResponse<byte[]> created = post(SETUP_INTENTS, Map.of("customer", THE_CUSTOMER));

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(created)).contains("\"payment_method_types\":[\"card\",\"paypal\"]");
  }

  @Test
  void the_client_secret_a_setup_answers_is_never_shown_on_the_audit_screen() {
    HttpResponse<byte[]> created = post(SETUP_INTENTS, Map.of("customer", THE_CUSTOMER));

    String secret = between(bodyOf(created), "\"client_secret\":\"", "\"");

    assertThat(secret).isNotBlank();
    assertThat(screen("/ui/audit")).doesNotContain(secret);
  }

  @Test
  void a_confirmation_replayed_under_one_idempotency_key_attaches_the_card_once() {
    aCustomerThisAccountHolds();
    seedFromTheScreen("to-save", THE_CARD, null);
    String setup =
        idIn(
            bodyOf(
                post(SETUP_INTENTS, Map.of("customer", THE_CUSTOMER, "payment_method", THE_CARD))));

    HttpResponse<byte[]> first = keyed(SETUP_INTENTS + "/" + setup + "/confirm", "setup-key-1");
    HttpResponse<byte[]> replay = keyed(SETUP_INTENTS + "/" + setup + "/confirm", "setup-key-1");

    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(replay.statusCode()).isEqualTo(200);
    assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
    assertThat(bodyOf(replay)).isEqualTo(bodyOf(first));
    assertThat(idsIn(bodyOf(get(CUSTOMERS + "/" + THE_CUSTOMER + "/payment_methods"))))
        .containsExactlyInAnyOrder(THE_CARD, A_CARD_THEY_ALREADY_HAD);
  }

  private void aRuleDeclining(StripeDecline decline) {
    http.post()
        .uri("/__simulator/stripe/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "priority",
                0,
                "match",
                Map.of("operation", StripeOperations.CONFIRM_SETUP_INTENT),
                "action",
                Map.of("type", "TERMINAL_FAILURE", "providerCode", decline.scenarioCode())))
        .retrieve()
        .toBodilessEntity();
  }

  private void aCustomerThisAccountHolds() {
    seedFromTheScreen("already-theirs", A_CARD_THEY_ALREADY_HAD, THE_CUSTOMER);
  }

  @Test
  void a_setup_naming_a_customer_this_account_holds_none_of_cannot_be_confirmed() {
    seedFromTheScreen("to-save", THE_CARD, null);

    String setup =
        idIn(
            bodyOf(
                post(
                    SETUP_INTENTS,
                    Map.of("customer", "cus_nobody_created", "payment_method", THE_CARD))));
    HttpResponse<byte[]> refused = post(SETUP_INTENTS + "/" + setup + "/confirm", Map.of());

    assertThat(refused.statusCode()).isEqualTo(404);
    assertThat(bodyOf(refused))
        .contains("\"code\":\"resource_missing\"")
        .contains("\"param\":\"customer\"");
    assertThat(bodyOf(get(PAYMENT_METHODS + "/" + THE_CARD))).contains("\"customer\":null");
  }

  private void seedFromTheScreen(
      String alias, String paymentMethodId, @Nullable String customerId) {
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
    assertThat(post("/ui/payment-methods/seed", form).statusCode()).isEqualTo(302);
  }

  private static String idIn(String body) {
    return between(body, "\"id\":\"", "\"");
  }

  private static String between(String body, String opening, String closing) {
    int from = body.indexOf(opening);
    assertThat(from).isNotNegative();
    int start = from + opening.length();
    return body.substring(start, body.indexOf(closing, start));
  }

  private static String theStartedIdIn(String location) {
    return between(location + "&", "started=", "&");
  }

  private static List<String> idsIn(String body) {
    List<String> found = new ArrayList<>();
    String marker = "{\"id\":\"";
    int from = 0;
    while (true) {
      int at = body.indexOf(marker, from);
      if (at < 0) {
        return List.copyOf(found);
      }
      int start = at + marker.length();
      int end = body.indexOf('"', start);
      found.add(body.substring(start, end));
      from = end;
    }
  }

  private static String locationOf(HttpResponse<byte[]> response) {
    return response.headers().firstValue("Location").orElseThrow();
  }

  private String follow(HttpResponse<byte[]> redirect) {
    HttpResponse<byte[]> followed =
        sent(
            HttpRequest.newBuilder(URI.create(locationOf(redirect)))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build());
    assertThat(followed.statusCode()).isEqualTo(200);
    return bodyOf(followed);
  }

  private String objectScreen(String providerObjectId) {
    HttpResponse<byte[]> answer =
        sent(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + "/ui/objects?provider=stripe&testRunId="
                            + RUN.value()
                            + "&providerObjectId="
                            + providerObjectId))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build());
    assertThat(answer.statusCode()).isEqualTo(200);
    return bodyOf(answer);
  }

  private String screen(String path) {
    return screen(path, "");
  }

  private String screen(String path, String more) {
    HttpResponse<byte[]> answer =
        sent(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + path
                            + "?provider=stripe&testRunId="
                            + RUN.value()
                            + more))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build());
    assertThat(answer.statusCode()).isEqualTo(200);
    return bodyOf(answer);
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
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20));
    if (path.startsWith("/v1")) {
      request = request.header("Authorization", "Bearer " + SECRET_KEY);
    }
    return sent(request.POST(HttpRequest.BodyPublishers.ofString(encoded(form))).build());
  }

  private HttpResponse<byte[]> keyed(String path, String idempotencyKey) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY)
            .header("Idempotency-Key", idempotencyKey)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(""))
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
}
