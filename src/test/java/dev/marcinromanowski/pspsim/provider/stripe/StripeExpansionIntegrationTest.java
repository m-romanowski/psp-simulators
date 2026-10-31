package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.StripeCollection;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.PaymentMethodListParams;
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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class StripeExpansionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-expansion-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-10-31T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51ExpansionSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String A_CUSTOMER = "cus_expanded_over_the_wire";
  private static final String THE_CARD = "pm_expanded_over_the_wire";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @BeforeEach
  void configureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
    seedTheCardOfTheCustomer();
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
  void the_official_java_client_retrieves_an_intent_with_its_customer_and_payment_method_expanded()
      throws StripeException {
    PaymentIntent created = aHold(true);

    PaymentIntent retrieved =
        client()
            .v1()
            .paymentIntents()
            .retrieve(
                created.getId(),
                PaymentIntentRetrieveParams.builder()
                    .addExpand("customer")
                    .addExpand("payment_method")
                    .build());

    assertThat(retrieved.getCustomer()).isEqualTo(A_CUSTOMER);
    Customer customer = retrieved.getCustomerObject();
    assertThat(customer).isNotNull();
    assertThat(customer.getId()).isEqualTo(A_CUSTOMER);
    assertThat(customer.getObject()).isEqualTo("customer");
    assertThat(retrieved.getPaymentMethod()).isEqualTo(THE_CARD);
    PaymentMethod method = retrieved.getPaymentMethodObject();
    assertThat(method).isNotNull();
    assertThat(method.getId()).isEqualTo(THE_CARD);
    assertThat(method.getCard().getLast4()).isEqualTo("1111");
    assertThat(method.getCustomer()).isEqualTo(A_CUSTOMER);
    assertThat(retrieved.getLatestChargeObject()).isNull();
  }

  @Test
  void the_official_java_client_confirms_and_captures_an_intent_with_its_latest_charge_expanded()
      throws StripeException {
    PaymentIntent created = aHold(false);

    PaymentIntent confirmed =
        client()
            .v1()
            .paymentIntents()
            .confirm(
                created.getId(),
                PaymentIntentConfirmParams.builder()
                    .setPaymentMethod(THE_CARD)
                    .addExpand("latest_charge")
                    .build());
    PaymentIntent captured =
        client()
            .v1()
            .paymentIntents()
            .capture(
                created.getId(),
                PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(400L)
                    .addExpand("latest_charge")
                    .build());

    Charge authorized = confirmed.getLatestChargeObject();
    assertThat(confirmed.getStatus()).isEqualTo("requires_capture");
    assertThat(authorized).isNotNull();
    assertThat(authorized.getId()).startsWith("ch_sim_");
    assertThat(authorized.getCaptured()).isFalse();
    assertThat(authorized.getPaymentIntent()).isEqualTo(created.getId());
    Charge taken = captured.getLatestChargeObject();
    assertThat(captured.getStatus()).isEqualTo("succeeded");
    assertThat(taken).isNotNull();
    assertThat(taken.getId()).isEqualTo(authorized.getId());
    assertThat(taken.getAmountCaptured()).isEqualTo(400L);
    assertThat(captured.getCustomer()).isEqualTo(A_CUSTOMER);
    assertThat(captured.getCustomerObject()).isNull();
  }

  @Test
  void the_official_java_client_lists_payment_methods_with_the_customer_of_each_expanded()
      throws StripeException {
    StripeCollection<PaymentMethod> listed =
        client()
            .v1()
            .paymentMethods()
            .list(
                PaymentMethodListParams.builder()
                    .setCustomer(A_CUSTOMER)
                    .addExpand("data.customer")
                    .build());

    assertThat(listed.getData()).hasSize(1);
    PaymentMethod method = listed.getData().get(0);
    assertThat(method.getId()).isEqualTo(THE_CARD);
    assertThat(method.getCustomerObject()).isNotNull();
    assertThat(method.getCustomerObject().getId()).isEqualTo(A_CUSTOMER);
  }

  @Test
  void the_official_java_client_reads_a_refused_path_as_an_invalid_request_naming_it() {
    assertThatThrownBy(
            () ->
                client()
                    .v1()
                    .paymentIntents()
                    .retrieve(
                        aHold(true).getId(),
                        PaymentIntentRetrieveParams.builder().addExpand("no_such").build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("This property cannot be expanded (no_such).")
        .satisfies(
            refused ->
                assertThat(((InvalidRequestException) refused).getStatusCode()).isEqualTo(400));
  }

  @Test
  void a_query_expanding_a_nested_path_answers_the_nested_object_over_the_wire()
      throws StripeException {
    PaymentIntent created = aHold(true);

    HttpResponse<byte[]> retrieved =
        get(
            "/v1/payment_intents/"
                + created.getId()
                + "?expand%5B%5D=latest_charge.payment_intent.customer");

    assertThat(retrieved.statusCode()).isEqualTo(200);
    Map<String, List<String>> answered = StripeJsonReader.read(bodyOf(retrieved)).flatten();
    assertThat(answered)
        .containsEntry("latest_charge.payment_intent.id", List.of(created.getId()))
        .containsEntry("latest_charge.payment_intent.customer.id", List.of(A_CUSTOMER))
        .containsEntry("customer", List.of(A_CUSTOMER));
  }

  private PaymentIntent aHold(boolean confirmed) throws StripeException {
    PaymentIntentCreateParams.Builder hold =
        PaymentIntentCreateParams.builder()
            .setAmount(1000L)
            .setCurrency("pln")
            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
            .setCustomer(A_CUSTOMER);
    if (confirmed) {
      hold = hold.setPaymentMethod(THE_CARD).setConfirm(true);
    }
    return client().v1().paymentIntents().create(hold.build());
  }

  private StripeClient client() {
    return StripeClient.builder()
        .setApiKey(SECRET_KEY)
        .setApiBase("http://localhost:" + port)
        .setMaxNetworkRetries(0)
        .build();
  }

  private void seedTheCardOfTheCustomer() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN.value());
    form.put("merchantId", MERCHANT);
    form.put("ownerId", "owner-1");
    form.put("type", "card");
    form.put("alias", "expanded");
    form.put("brand", "visa");
    form.put("last4", "1111");
    form.put("expMonth", "12");
    form.put("expYear", "2030");
    form.put("funding", "credit");
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", THE_CARD);
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "customerId", A_CUSTOMER);
    HttpResponse<byte[]> seeded = post("/ui/payment-methods/seed", form);
    assertThat(seeded.statusCode()).isEqualTo(302);
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
}
