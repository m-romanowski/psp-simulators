package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.model.StripeCollection;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerPaymentMethodListParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.SetupIntentConfirmParams;
import com.stripe.param.SetupIntentCreateParams;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class StripePaymentMethodCreationIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-payment-method-creation-it");
  private static final TestRunId ANOTHER_RUN =
      TestRunId.of("stripe-payment-method-creation-elsewhere-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-10-31T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51CreationSimulator";
  private static final String PUBLISHABLE_KEY = "pk_test_51CreationSimulator";
  private static final String ANOTHER_MERCHANT = "merchant-2";
  private static final String ANOTHER_SECRET_KEY = "sk_test_51AnotherCreationSimulator";
  private static final String ELSEWHERE_SECRET_KEY = "sk_test_51ElsewhereCreationSimulator";

  private static final String THE_NUMBER = "4000056655665556";
  private static final String THE_CVC = "737";

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
            anAccount(ANOTHER_MERCHANT, ANOTHER_SECRET_KEY, null)));
    configure(ANOTHER_RUN, List.of(anAccount(MERCHANT, ELSEWHERE_SECRET_KEY, null)));
  }

  @AfterEach
  void unconfigureTheAccounts() {
    reset(RUN);
    reset(ANOTHER_RUN);
    configure(RUN, List.of());
    configure(ANOTHER_RUN, List.of());
  }

  @Test
  void the_official_java_client_saves_a_card_it_created_to_a_customer_and_lists_it()
      throws StripeException {
    StripeClient client =
        StripeClient.builder()
            .setApiKey(SECRET_KEY)
            .setApiBase("http://localhost:" + port)
            .setMaxNetworkRetries(0)
            .build();

    Customer customer =
        client.v1().customers().create(CustomerCreateParams.builder().setEmail("a@b.c").build());
    PaymentMethod card =
        client
            .v1()
            .paymentMethods()
            .create(
                PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.CARD)
                    .setCard(
                        PaymentMethodCreateParams.CardDetails.builder()
                            .setNumber(THE_NUMBER)
                            .setExpMonth(12L)
                            .setExpYear(2034L)
                            .setCvc(THE_CVC)
                            .build())
                    .putMetadata("order", "o-1")
                    .build());
    SetupIntent setup =
        client
            .v1()
            .setupIntents()
            .create(
                SetupIntentCreateParams.builder()
                    .setCustomer(customer.getId())
                    .setAutomaticPaymentMethods(
                        SetupIntentCreateParams.AutomaticPaymentMethods.builder()
                            .setEnabled(true)
                            .setAllowRedirects(
                                SetupIntentCreateParams.AutomaticPaymentMethods.AllowRedirects
                                    .NEVER)
                            .build())
                    .build());
    SetupIntent confirmed =
        client
            .v1()
            .setupIntents()
            .confirm(
                setup.getId(),
                SetupIntentConfirmParams.builder().setPaymentMethod(card.getId()).build());
    StripeCollection<PaymentMethod> saved =
        client
            .v1()
            .customers()
            .paymentMethods()
            .list(customer.getId(), CustomerPaymentMethodListParams.builder().build());

    assertThat(card.getId()).matches("pm_sim_[0-9a-f]{20}");
    assertThat(card.getCustomer()).isNull();
    assertThat(card.getCard().getBrand()).isEqualTo("visa");
    assertThat(card.getCard().getFunding()).isEqualTo("debit");
    assertThat(card.getCard().getLast4()).isEqualTo("5556");
    assertThat(card.getCard().getExpYear()).isEqualTo(2034L);
    assertThat(card.getMetadata()).containsEntry("order", "o-1");
    assertThat(confirmed.getStatus()).isEqualTo("succeeded");
    assertThat(confirmed.getPaymentMethod()).isEqualTo(card.getId());
    assertThat(saved.getData())
        .singleElement()
        .satisfies(
            listed -> {
              assertThat(listed.getId()).isEqualTo(card.getId());
              assertThat(listed.getCustomer()).isEqualTo(customer.getId());
            });
  }

  @ParameterizedTest
  @CsvSource({
    "4000000000000002,card_declined,generic_decline",
    "4000000000009995,card_declined,insufficient_funds",
    "4000000000009987,card_declined,lost_card",
    "4000000000009979,card_declined,stolen_card",
    "4000000000000069,expired_card,",
    "4000000000000127,incorrect_cvc,",
    "4000000000000119,processing_error,",
    "4000000000006975,card_declined,card_velocity_exceeded",
    "4000000000000341,card_declined,generic_decline"
  })
  void a_raw_card_keeps_its_behavior_across_storage_setup_and_a_charge(
      String number, String code, @org.jspecify.annotations.Nullable String decline)
      throws StripeException {
    StripeClient client =
        StripeClient.builder()
            .setApiKey(SECRET_KEY)
            .setApiBase("http://localhost:" + port)
            .setMaxNetworkRetries(0)
            .build();
    Customer customer = client.v1().customers().create(CustomerCreateParams.builder().build());
    PaymentMethod method =
        client
            .v1()
            .paymentMethods()
            .create(
                PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.CARD)
                    .setCard(
                        PaymentMethodCreateParams.CardDetails.builder()
                            .setNumber(number)
                            .setExpMonth(12L)
                            .setExpYear(2034L)
                            .setCvc("737")
                            .build())
                    .build());
    SetupIntent setup =
        client
            .v1()
            .setupIntents()
            .create(
                SetupIntentCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addPaymentMethodType("card")
                    .build());
    if (number.equals("4000000000000341")) {
      assertThat(
              client
                  .v1()
                  .setupIntents()
                  .confirm(
                      setup.getId(),
                      SetupIntentConfirmParams.builder().setPaymentMethod(method.getId()).build())
                  .getStatus())
          .isEqualTo("succeeded");
      assertThat(client.v1().paymentMethods().retrieve(method.getId()).getCustomer())
          .isEqualTo(customer.getId());
    } else {
      CardException setupFailure =
          org.assertj.core.api.Assertions.catchThrowableOfType(
              CardException.class,
              () ->
                  client
                      .v1()
                      .setupIntents()
                      .confirm(
                          setup.getId(),
                          SetupIntentConfirmParams.builder()
                              .setPaymentMethod(method.getId())
                              .build()));
      assertThat(setupFailure.getCode()).isEqualTo(code);
      assertThat(setupFailure.getDeclineCode()).isEqualTo(decline);
      assertThat(client.v1().paymentMethods().retrieve(method.getId()).getCustomer()).isNull();
    }
    CardException chargeFailure =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            CardException.class,
            () ->
                client
                    .v1()
                    .paymentIntents()
                    .create(
                        PaymentIntentCreateParams.builder()
                            .setAmount(1050L)
                            .setCurrency("usd")
                            .setCustomer(customer.getId())
                            .addPaymentMethodType("card")
                            .setPaymentMethod(method.getId())
                            .setConfirm(true)
                            .build()));
    assertThat(chargeFailure.getCode()).isEqualTo(code);
    assertThat(chargeFailure.getDeclineCode()).isEqualTo(decline);
    String charge =
        client
            .v1()
            .paymentIntents()
            .retrieve(chargeFailure.getStripeError().getPaymentIntent().getId())
            .getLatestCharge();
    assertThat(client.v1().charges().retrieve(charge).getStatus()).isEqualTo("failed");
    assertThat(client.v1().paymentMethods().retrieve(method.getId()).getId())
        .isEqualTo(method.getId());
  }

  @Test
  void the_official_java_client_sets_up_charge_customer_fail_then_gets_its_off_session_decline()
      throws StripeException {
    StripeClient client =
        StripeClient.builder()
            .setApiKey(SECRET_KEY)
            .setApiBase("http://localhost:" + port)
            .setMaxNetworkRetries(0)
            .build();
    Customer customer = client.v1().customers().create(CustomerCreateParams.builder().build());
    SetupIntent setup =
        client
            .v1()
            .setupIntents()
            .create(
                SetupIntentCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addPaymentMethodType("card")
                    .build());
    SetupIntent confirmed =
        client
            .v1()
            .setupIntents()
            .confirm(
                setup.getId(),
                SetupIntentConfirmParams.builder()
                    .setPaymentMethod("pm_card_chargeCustomerFail")
                    .build());
    assertThat(confirmed.getStatus()).isEqualTo("succeeded");
    PaymentMethod saved = client.v1().paymentMethods().retrieve(confirmed.getPaymentMethod());
    assertThat(saved.getCustomer()).isEqualTo(customer.getId());
    CardException declined =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            CardException.class,
            () ->
                client
                    .v1()
                    .paymentIntents()
                    .create(
                        PaymentIntentCreateParams.builder()
                            .setAmount(1050L)
                            .setCurrency("usd")
                            .setCustomer(customer.getId())
                            .addPaymentMethodType("card")
                            .setPaymentMethod(saved.getId())
                            .setOffSession(true)
                            .setConfirm(true)
                            .build()));
    assertThat(declined.getStatusCode()).isEqualTo(402);
    assertThat(declined.getCode()).isEqualTo("card_declined");
    assertThat(declined.getDeclineCode()).isEqualTo("generic_decline");
    String failedCharge =
        client
            .v1()
            .paymentIntents()
            .retrieve(declined.getStripeError().getPaymentIntent().getId())
            .getLatestCharge();
    assertThat(client.v1().charges().retrieve(failedCharge).getOutcome().getReason())
        .isEqualTo("generic_decline");
    client
        .v1()
        .paymentIntents()
        .confirm(
            declined.getStripeError().getPaymentIntent().getId(),
            PaymentIntentConfirmParams.builder().setPaymentMethod("pm_card_visa").build());
    assertThat(client.v1().charges().retrieve(failedCharge).getOutcome().getReason())
        .isEqualTo("generic_decline");

    assertThat(declined.getStripeError().getPaymentMethod().getId()).isEqualTo(saved.getId());
    assertThat(
            declined
                .getStripeError()
                .getPaymentIntent()
                .getLastPaymentError()
                .getPaymentMethod()
                .getId())
        .isEqualTo(saved.getId());
    assertThat(client.v1().paymentMethods().retrieve(saved.getId()).getCustomer())
        .isEqualTo(customer.getId());
  }

  @Test
  void the_official_java_client_reads_a_refused_card_as_the_card_exception_stripe_raises() {
    StripeClient client =
        StripeClient.builder()
            .setApiKey(SECRET_KEY)
            .setApiBase("http://localhost:" + port)
            .setMaxNetworkRetries(0)
            .build();

    CardException refused =
        org.assertj.core.api.Assertions.catchThrowableOfType(
            CardException.class,
            () ->
                client
                    .v1()
                    .paymentMethods()
                    .create(
                        PaymentMethodCreateParams.builder()
                            .setType(PaymentMethodCreateParams.Type.CARD)
                            .setCard(
                                PaymentMethodCreateParams.CardDetails.builder()
                                    .setNumber("4242424242424241")
                                    .setExpMonth(12L)
                                    .setExpYear(2034L)
                                    .build())
                            .build()));

    assertThat(refused.getCode()).isEqualTo("incorrect_number");
    assertThat(refused.getParam()).isEqualTo("number");
    assertThat(refused.getStatusCode()).isEqualTo(402);
  }

  @Test
  void a_publishable_key_creates_a_card_over_the_wire_and_only_its_account_reads_it_back() {
    HttpResponse<byte[]> created =
        post(
            PUBLISHABLE_KEY,
            "/v1/payment_methods",
            theCard(),
            Map.of("Origin", "https://shop.example"));

    assertThat(created.statusCode()).as(bodyOf(created)).isEqualTo(200);
    assertThat(created.headers().firstValue("Access-Control-Allow-Origin")).contains("*");
    String id = valueIn(bodyOf(created), "id");
    assertThat(id).matches("pm_sim_[0-9a-f]{20}");
    assertThat(bodyOf(created)).doesNotContain("\"metadata\"");
    assertThat(get(SECRET_KEY, "/v1/payment_methods/" + id).statusCode()).isEqualTo(200);
    assertThat(bodyOf(get(SECRET_KEY, "/v1/payment_methods/" + id)))
        .contains("\"metadata\":{\"order\":\"o-1\"}");
    assertThat(get(ANOTHER_SECRET_KEY, "/v1/payment_methods/" + id).statusCode()).isEqualTo(404);
    assertThat(get(ELSEWHERE_SECRET_KEY, "/v1/payment_methods/" + id).statusCode()).isEqualTo(404);
    assertThat(get(PUBLISHABLE_KEY, "/v1/payment_methods/" + id).statusCode()).isEqualTo(403);
  }

  @Test
  void no_surface_that_records_a_request_or_shows_an_object_holds_the_number_or_the_cvc() {
    HttpResponse<byte[]> created =
        post(
            SECRET_KEY,
            "/v1/payment_methods",
            theCard(),
            Map.of("Idempotency-Key", "a-key-the-exchange-log-keeps"));
    String id = valueIn(bodyOf(created), "id");
    Map<String, String> payment = new LinkedHashMap<>();
    payment.put("amount", "1050");
    payment.put("currency", "usd");
    payment.put("allowed_payment_method_types[0]", "card");
    payment.put("payment_method_data[type]", "card");
    payment.put("payment_method_data[card][number]", THE_NUMBER);
    payment.put("payment_method_data[card][exp_month]", "12");
    payment.put("payment_method_data[card][exp_year]", "2034");
    payment.put("payment_method_data[card][cvc]", THE_CVC);
    payment.put("confirm", "true");
    HttpResponse<byte[]> charged = post(SECRET_KEY, "/v1/payment_intents", payment, Map.of());
    assertThat(charged.statusCode()).as(bodyOf(charged)).isEqualTo(200);

    for (String surface :
        List.of(
            "/__simulator/stripe/audit?testRunId=" + RUN.value(),
            "/__simulator/stripe/idempotency?testRunId=" + RUN.value(),
            "/__simulator/stripe/objects?testRunId=" + RUN.value(),
            "/__simulator/stripe/state/" + id + "?testRunId=" + RUN.value(),
            "/__simulator/stripe/run-bundle?testRunId=" + RUN.value(),
            "/ui/audit?provider=stripe&testRunId=" + RUN.value(),
            "/ui/objects?provider=stripe&testRunId=" + RUN.value(),
            "/ui/idempotency?provider=stripe&testRunId=" + RUN.value(),
            "/ui/timeline?provider=stripe&testRunId=" + RUN.value())) {
      HttpResponse<byte[]> shown = get(null, surface);
      assertThat(shown.statusCode()).as(surface).isEqualTo(200);
      assertThat(bodyOf(shown))
          .as(surface)
          .doesNotContain(THE_NUMBER)
          .doesNotContain("\"" + THE_CVC + "\"")
          .doesNotContain("[" + THE_CVC + "]");
    }
    assertThat(bodyOf(get(null, "/__simulator/stripe/audit?testRunId=" + RUN.value())))
        .contains("card[number]")
        .contains("payment_method_data[card][cvc]");
  }

  @Test
  void a_payment_method_another_run_created_is_not_this_runs() {
    HttpResponse<byte[]> theirs = post(ELSEWHERE_SECRET_KEY, "/v1/payment_methods", theCard());
    String id = valueIn(bodyOf(theirs), "id");

    assertThat(theirs.statusCode()).isEqualTo(200);
    assertThat(get(SECRET_KEY, "/v1/payment_methods/" + id).statusCode()).isEqualTo(404);
    assertThat(get(ELSEWHERE_SECRET_KEY, "/v1/payment_methods/" + id).statusCode()).isEqualTo(200);
  }

  private static Map<String, String> theCard() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("type", "card");
    form.put("card[number]", THE_NUMBER);
    form.put("card[exp_month]", "12");
    form.put("card[exp_year]", "2034");
    form.put("card[cvc]", THE_CVC);
    form.put("metadata[order]", "o-1");
    return form;
  }

  private static Map<String, Object> anAccount(
      String accountId, String secretKey, @org.jspecify.annotations.Nullable String publishable) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put(StripeAccount.SECRET_KEY_PROPERTY, secretKey);
    if (publishable != null) {
      configuration.put(StripeAccount.PUBLISHABLE_KEY_PROPERTY, publishable);
    }
    configuration.put(StripeCaptureWindow.SETTING, "P3D");
    return Map.of("accountId", accountId, "configuration", configuration);
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

  private HttpResponse<byte[]> post(String key, String path, Map<String, String> form) {
    return post(key, path, form, Map.of());
  }

  private HttpResponse<byte[]> post(
      String key, String path, Map<String, String> form, Map<String, String> headers) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + key)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)));
    headers.forEach(request::header);
    return sent(request.build());
  }

  private HttpResponse<byte[]> get(@org.jspecify.annotations.Nullable String key, String path) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20))
            .GET();
    if (key != null) {
      request.header("Authorization", "Bearer " + key);
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
    int from = json.indexOf(opening) + opening.length();
    return json.substring(from, json.indexOf('"', from));
  }
}
