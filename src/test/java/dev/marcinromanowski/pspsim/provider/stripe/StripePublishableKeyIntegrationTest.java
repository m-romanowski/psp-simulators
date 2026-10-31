package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentRetrieveParams;
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
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class StripePublishableKeyIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-publishable-key-it");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("stripe-publishable-key-it-elsewhere");
  private static final Instant FIXED_NOW = Instant.parse("2026-10-31T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51PublishableSimulator";
  private static final String PUBLISHABLE_KEY = "pk_test_51PublishableSimulator";
  private static final String ANOTHER_MERCHANT = "merchant-2";
  private static final String ANOTHER_SECRET_KEY = "sk_test_51AnotherPublishableSimulator";
  private static final String ANOTHER_PUBLISHABLE_KEY = "pk_test_51AnotherPublishableSimulator";
  private static final String ELSEWHERE_SECRET_KEY = "sk_test_51ElsewherePublishableSimulator";
  private static final String ELSEWHERE_PUBLISHABLE_KEY = "pk_test_51ElsewherePublishableSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String A_CUSTOMER = "cus_paying_from_the_app";
  private static final String THE_CARD = "pm_confirmed_from_the_app";
  private static final String A_RETURN_URL = StripePaymentIntentTest.A_RETURN_URL;
  private static final String IOS_PINS = "2020-08-27";
  private static final String ANDROID_PINS = "2020-03-02";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @BeforeEach
  void configureTheAccounts() {
    http = RestClient.create("http://localhost:" + port);
    reset(RUN);
    reset(ANOTHER_RUN);
    configure(
        RUN,
        List.of(
            anAccount(MERCHANT, SECRET_KEY, PUBLISHABLE_KEY),
            anAccount(ANOTHER_MERCHANT, ANOTHER_SECRET_KEY, ANOTHER_PUBLISHABLE_KEY)));
    configure(
        ANOTHER_RUN, List.of(anAccount(MERCHANT, ELSEWHERE_SECRET_KEY, ELSEWHERE_PUBLISHABLE_KEY)));
    seedTheCardOfTheCustomer();
  }

  @AfterEach
  void unconfigureTheAccounts() {
    for (TestRunId run : List.of(RUN, ANOTHER_RUN)) {
      reset(run);
      configure(run, List.of());
    }
  }

  @Test
  void the_official_java_client_built_with_the_publishable_key_retrieves_an_intent_by_its_secret()
      throws StripeException {
    Map<String, String> created = aPayment(SECRET_KEY);
    StripeClient app =
        StripeClient.builder()
            .setApiKey(PUBLISHABLE_KEY)
            .setApiBase("http://localhost:" + port)
            .setMaxNetworkRetries(0)
            .build();

    PaymentIntent read =
        app.v1()
            .paymentIntents()
            .retrieve(
                created.get("id"),
                PaymentIntentRetrieveParams.builder()
                    .setClientSecret(created.get("client_secret"))
                    .build());

    assertThat(read.getId()).isEqualTo(created.get("id"));
    assertThat(read.getClientSecret()).isEqualTo(created.get("client_secret"));
    assertThat(read.getStatus()).isEqualTo("requires_payment_method");
    assertThat(read.getAmount()).isEqualTo(1099L);
    assertThat(read.getCustomer()).isNull();
    assertThat(read.getLatestCharge()).isNull();
    assertThat(read.getLastResponse().headers().firstValue("Stripe-Version"))
        .isEqualTo(Optional.of("2026-08-26.dahlia"));
  }

  @Test
  void an_app_confirms_a_payment_and_a_setup_by_their_secrets_and_the_server_reads_them_changed() {
    Map<String, String> payment = aPayment(SECRET_KEY);
    Map<String, String> setup = aSetup();

    HttpResponse<byte[]> paid =
        postAs(
            PUBLISHABLE_KEY,
            "/v1/payment_intents/" + payment.get("id") + "/confirm",
            confirming(payment),
            IOS_PINS);
    HttpResponse<byte[]> setUp =
        postAs(
            PUBLISHABLE_KEY,
            "/v1/setup_intents/" + setup.get("id") + "/confirm",
            confirming(setup),
            ANDROID_PINS);

    assertThat(paid.statusCode()).isEqualTo(200);
    assertThat(paid.headers().firstValue("Stripe-Version")).contains(IOS_PINS);
    assertThat(bodyOf(paid)).contains("\"status\":\"succeeded\"").doesNotContain(A_CUSTOMER);
    assertThat(setUp.statusCode()).isEqualTo(200);
    assertThat(setUp.headers().firstValue("Stripe-Version")).contains(ANDROID_PINS);
    assertThat(bodyOf(setUp)).contains("\"status\":\"succeeded\"").doesNotContain(A_CUSTOMER);
    assertThat(bodyOf(getAs(SECRET_KEY, "/v1/payment_intents/" + payment.get("id"), Map.of())))
        .contains("\"status\":\"succeeded\"")
        .contains("\"customer\":\"" + A_CUSTOMER + "\"")
        .contains("\"latest_charge\":\"ch_sim_");
    assertThat(bodyOf(getAs(SECRET_KEY, "/v1/setup_intents/" + setup.get("id"), Map.of())))
        .contains("\"status\":\"succeeded\"")
        .contains("\"customer\":\"" + A_CUSTOMER + "\"");
  }

  @Test
  void an_app_reads_both_intents_by_their_secrets_with_the_payment_method_expanded() {
    Map<String, String> payment = aPayment(SECRET_KEY);
    Map<String, String> setup = aSetup();

    HttpResponse<byte[]> readPayment =
        getAs(
            PUBLISHABLE_KEY,
            "/v1/payment_intents/" + payment.get("id"),
            Map.of("client_secret", payment.get("client_secret"), "expand[0]", "payment_method"),
            IOS_PINS);
    HttpResponse<byte[]> readSetup =
        getAs(
            PUBLISHABLE_KEY,
            "/v1/setup_intents/" + setup.get("id"),
            Map.of(
                "client_secret",
                setup.get("client_secret"),
                "expand[0]",
                "payment_method.customer"));

    assertThat(readPayment.statusCode()).isEqualTo(200);
    assertThat(bodyOf(readPayment))
        .contains("\"status\":\"requires_payment_method\"")
        .contains("\"payment_method\":null")
        .doesNotContain("\"customer\"");
    assertThat(readSetup.statusCode()).isEqualTo(200);
    assertThat(bodyOf(readSetup))
        .contains("\"payment_method\":{\"id\":\"" + THE_CARD + "\"")
        .contains("\"customer\":\"" + A_CUSTOMER + "\"")
        .doesNotContain("\"object\":\"customer\"");
  }

  @Test
  void a_missing_a_malformed_and_a_wrong_secret_and_one_of_another_intent_are_refused() {
    Map<String, String> payment = aPayment(SECRET_KEY);
    Map<String, String> another = aPayment(SECRET_KEY);
    String path = "/v1/payment_intents/" + payment.get("id");

    HttpResponse<byte[]> missing = getAs(PUBLISHABLE_KEY, path, Map.of());
    HttpResponse<byte[]> malformed =
        getAs(PUBLISHABLE_KEY, path, Map.of("client_secret", "not-a-client-secret"));
    HttpResponse<byte[]> wrong =
        getAs(
            PUBLISHABLE_KEY,
            path,
            Map.of("client_secret", payment.get("id") + "_secret_bad-secret"));
    HttpResponse<byte[]> ofAnotherIntent =
        getAs(PUBLISHABLE_KEY, path, Map.of("client_secret", another.get("client_secret")));

    assertThat(missing.statusCode()).isEqualTo(400);
    assertThat(bodyOf(missing))
        .contains("\"code\":\"parameter_missing\"")
        .contains("\"param\":\"client_secret\"");
    for (HttpResponse<byte[]> refused : List.of(malformed, wrong, ofAnotherIntent)) {
      assertThat(refused.statusCode()).isEqualTo(400);
      assertThat(bodyOf(refused))
          .contains("\"code\":\"payment_intent_invalid_parameter\"")
          .contains("\"param\":\"client_secret\"")
          .doesNotContain(another.get("client_secret"));
    }
  }

  @Test
  void a_secret_of_another_account_or_run_and_a_publishable_key_of_another_account_reach_nothing() {
    Map<String, String> mine = aPayment(SECRET_KEY);
    Map<String, String> theirs = aPayment(ANOTHER_SECRET_KEY);
    String path = "/v1/payment_intents/" + mine.get("id");

    HttpResponse<byte[]> theirSecret =
        getAs(PUBLISHABLE_KEY, path, Map.of("client_secret", theirs.get("client_secret")));
    HttpResponse<byte[]> theirKey =
        getAs(ANOTHER_PUBLISHABLE_KEY, path, Map.of("client_secret", mine.get("client_secret")));
    HttpResponse<byte[]> anotherRunsKey =
        postAs(ELSEWHERE_PUBLISHABLE_KEY, path + "/confirm", confirming(mine), IOS_PINS);

    assertThat(theirSecret.statusCode()).isEqualTo(400);
    assertThat(bodyOf(theirSecret)).contains("\"code\":\"payment_intent_invalid_parameter\"");
    for (HttpResponse<byte[]> refused : List.of(theirKey, anotherRunsKey)) {
      assertThat(refused.statusCode()).isEqualTo(404);
      assertThat(bodyOf(refused))
          .contains("\"code\":\"resource_missing\"")
          .contains("\"message\":\"No such payment_intent: '" + mine.get("id") + "'\"")
          .contains("\"param\":\"intent\"");
    }
    assertThat(bodyOf(getAs(SECRET_KEY, path, Map.of())))
        .contains("\"status\":\"requires_payment_method\"");
  }

  @Test
  void
      a_publishable_key_captures_cancels_refunds_and_reads_no_customer_or_listing_and_changes_nothing() {
    Map<String, String> hold = aConfirmedHold();
    String path = "/v1/payment_intents/" + hold.get("id");
    Map<String, String> carrying = Map.of("client_secret", hold.get("client_secret"));

    List<HttpResponse<byte[]>> refused =
        List.of(
            postAs(PUBLISHABLE_KEY, path + "/capture", carrying, null),
            postAs(PUBLISHABLE_KEY, path + "/cancel", carrying, null),
            postAs(PUBLISHABLE_KEY, "/v1/refunds", Map.of("payment_intent", hold.get("id")), null),
            getAs(PUBLISHABLE_KEY, "/v1/customers/" + A_CUSTOMER, Map.of()),
            getAs(PUBLISHABLE_KEY, "/v1/customers/" + A_CUSTOMER + "/payment_methods", Map.of()),
            getAs(PUBLISHABLE_KEY, "/v1/payment_methods", Map.of("customer", A_CUSTOMER)),
            getAs(PUBLISHABLE_KEY, "/v1/payment_methods/" + THE_CARD, Map.of()));

    for (HttpResponse<byte[]> answer : refused) {
      assertThat(answer.statusCode()).isEqualTo(403);
      assertThat(bodyOf(answer))
          .contains("\"code\":\"secret_key_required\"")
          .contains("The API key provided is a publishable key, but a secret key is required.")
          .doesNotContain(A_CUSTOMER);
    }
    assertThat(bodyOf(getAs(SECRET_KEY, path, Map.of())))
        .contains("\"status\":\"requires_capture\"")
        .contains("\"amount_capturable\":1099")
        .contains("\"amount_received\":0");
    assertThat(theOutboundStatusesOf("/v1/refunds")).containsExactly(403);
  }

  @Test
  void a_secret_key_still_reads_confirms_and_captures_and_reads_a_setup_it_created() {
    Map<String, String> payment = aPayment(SECRET_KEY);
    Map<String, String> setup = aSetup();
    String path = "/v1/payment_intents/" + payment.get("id");

    HttpResponse<byte[]> confirmed =
        postAs(
            SECRET_KEY,
            path + "/confirm",
            Map.of("payment_method", THE_CARD, "return_url", A_RETURN_URL),
            null);
    HttpResponse<byte[]> readSetup =
        getAs(SECRET_KEY, "/v1/setup_intents/" + setup.get("id"), Map.of());
    HttpResponse<byte[]> mobileVersionOnASecretKey = getAs(SECRET_KEY, path, Map.of(), IOS_PINS);

    assertThat(confirmed.statusCode()).isEqualTo(200);
    assertThat(bodyOf(confirmed))
        .contains("\"status\":\"succeeded\"")
        .contains("\"customer\":\"" + A_CUSTOMER + "\"");
    assertThat(readSetup.statusCode()).isEqualTo(200);
    assertThat(bodyOf(readSetup)).contains("\"customer\":\"" + A_CUSTOMER + "\"");
    assertThat(mobileVersionOnASecretKey.statusCode()).isEqualTo(400);
  }

  @Test
  void
      the_trail_shows_the_account_a_publishable_key_resolved_to_and_withholds_the_secret_it_sent() {
    Map<String, String> payment = aPayment(SECRET_KEY);

    getAs(
        PUBLISHABLE_KEY,
        "/v1/payment_intents/" + payment.get("id"),
        Map.of("client_secret", payment.get("client_secret")));

    String trail =
        bodyOf(getAs(null, "/__simulator/stripe/audit?testRunId=" + RUN.value(), Map.of()));
    assertThat(trail).doesNotContain(payment.get("client_secret"));
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/stripe/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(rows)
        .filteredOn(row -> ("/v1/payment_intents/" + payment.get("id")).equals(row.get("path")))
        .extracting(row -> row.get("accountId"))
        .containsOnly(MERCHANT);
  }

  @Test
  void a_configuration_naming_one_publishable_key_twice_in_one_run_is_refused() {
    HttpResponse<byte[]> refused =
        putJson(
            "/__simulator/stripe/config",
            "{\"testRunId\":\""
                + RUN.value()
                + "\",\"clock\":{},\"accounts\":[{\"accountId\":\"merchant-3\",\"configuration\":"
                + "{\"secretKey\":\"sk_test_51Third\",\"captureWindow\":\"P3D\",\"publishableKey\":\""
                + PUBLISHABLE_KEY
                + "\"}}]}");

    assertThat(refused.statusCode()).isBetween(400, 499);
    assertThat(bodyOf(refused))
        .contains("publishableKey is already configured for another account");
  }

  private Map<String, String> aPayment(String secretKey) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("customer", A_CUSTOMER);
    form.put("payment_method_types[0]", "card");
    return idAndSecretOf(postAs(secretKey, "/v1/payment_intents", form, "2026-08-26.dahlia"));
  }

  private Map<String, String> aConfirmedHold() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("customer", A_CUSTOMER);
    form.put("payment_method", THE_CARD);
    form.put("confirm", "true");
    form.put("return_url", A_RETURN_URL);
    return idAndSecretOf(postAs(SECRET_KEY, "/v1/payment_intents", form, null));
  }

  private Map<String, String> aSetup() {
    return idAndSecretOf(
        postAs(
            SECRET_KEY,
            "/v1/setup_intents",
            Map.of(
                "customer",
                A_CUSTOMER,
                "payment_method",
                THE_CARD,
                "payment_method_types[0]",
                "card"),
            "2026-08-26.dahlia"));
  }

  private static Map<String, String> confirming(Map<String, String> intent) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("client_secret", intent.get("client_secret"));
    form.put("payment_method", THE_CARD);
    form.put("use_stripe_sdk", "true");
    return form;
  }

  private static Map<String, String> idAndSecretOf(HttpResponse<byte[]> created) {
    assertThat(created.statusCode()).as(bodyOf(created)).isEqualTo(200);
    String body = bodyOf(created);
    return Map.of("id", valueIn(body, "id"), "client_secret", valueIn(body, "client_secret"));
  }

  private List<Integer> theOutboundStatusesOf(String path) {
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/stripe/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    List<Integer> statuses = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      if (path.equals(row.get("path")) && "OUTBOUND_RESPONSE".equals(row.get("direction"))) {
        statuses.add((Integer) row.get("status"));
      }
    }
    return statuses;
  }

  private void seedTheCardOfTheCustomer() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN.value());
    form.put("merchantId", MERCHANT);
    form.put("ownerId", "owner-1");
    form.put("type", "card");
    form.put("alias", "confirmed-from-the-app");
    form.put("brand", "visa");
    form.put("last4", "4242");
    form.put("expMonth", "12");
    form.put("expYear", "2030");
    form.put("funding", "credit");
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", THE_CARD);
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "customerId", A_CUSTOMER);
    HttpResponse<byte[]> seeded =
        sent(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/ui/payment-methods/seed"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
                .build());
    assertThat(seeded.statusCode()).isEqualTo(302);
  }

  private static Map<String, Object> anAccount(
      String accountId, String secretKey, String publishableKey) {
    return Map.of(
        "accountId",
        accountId,
        "configuration",
        Map.of(
            StripeAccount.SECRET_KEY_PROPERTY,
            secretKey,
            StripeAccount.PUBLISHABLE_KEY_PROPERTY,
            publishableKey,
            StripeCaptureWindow.SETTING,
            THREE_DAYS));
  }

  private void configure(TestRunId run, List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                run.value(),
                "clock",
                accounts.isEmpty() ? Map.of() : Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                accounts,
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset(TestRunId run) {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", run.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private HttpResponse<byte[]> putJson(String path, String json) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build());
  }

  private HttpResponse<byte[]> postAs(
      String key, String path, Map<String, String> form, @Nullable String version) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + key)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)));
    if (version != null) {
      request = request.header("Stripe-Version", version);
    }
    return sent(request.build());
  }

  private HttpResponse<byte[]> getAs(@Nullable String key, String path, Map<String, String> query) {
    return getAs(key, path, query, null);
  }

  private HttpResponse<byte[]> getAs(
      @Nullable String key, String path, Map<String, String> query, @Nullable String version) {
    String address = query.isEmpty() ? path : path + "?" + encoded(query);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + address))
            .timeout(Duration.ofSeconds(20))
            .GET();
    if (key != null) {
      request = request.header("Authorization", "Bearer " + key);
    }
    if (version != null) {
      request = request.header("Stripe-Version", version);
    }
    return sent(request.build());
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
