package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.SetupIntent;
import com.stripe.param.SetupIntentCreateParams;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class StripePaymentMethodSelectionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-selection-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-10-31T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51SelectionSimulator";

  private static final String THE_VERSION_THE_JAVA_CLIENT_PINS = "2026-08-26.dahlia";

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(List.of(anAccount()));
  }

  @AfterEach
  void unconfigureTheAccount() {
    reset();
    configure(List.of());
  }

  @Test
  void the_official_java_client_sets_a_card_up_naming_payment_method_types_at_the_version_it_pins()
      throws StripeException {
    StripeClient client =
        StripeClient.builder()
            .setApiKey(SECRET_KEY)
            .setApiBase("http://localhost:" + port)
            .setMaxNetworkRetries(0)
            .build();

    SetupIntent created =
        client
            .v1()
            .setupIntents()
            .create(SetupIntentCreateParams.builder().addPaymentMethodType("card").build());

    assertThat(com.stripe.Stripe.API_VERSION).isEqualTo(THE_VERSION_THE_JAVA_CLIENT_PINS);
    assertThat(created.getObject()).isEqualTo("setup_intent");
    assertThat(created.getStatus()).isEqualTo("requires_payment_method");
    assertThat(created.getPaymentMethodTypes()).containsExactly("card");
    assertThat(created.getAutomaticPaymentMethods()).isNull();
    assertThat(created.getAllowedPaymentMethodTypes()).isNull();
    assertThat(created.getExcludedPaymentMethodTypes()).isNull();
    assertThat(created.getPaymentMethodConfigurationDetails()).isNull();
    assertThat(created.getLastResponse().headers().firstValue("Stripe-Version"))
        .contains(THE_VERSION_THE_JAVA_CLIENT_PINS);
    assertThat(storedTypesOf(created.getId())).isEqualTo("{card}");
  }

  @Test
  void the_newest_release_refuses_payment_method_types_on_a_setup_over_the_wire() {
    HttpResponse<byte[]> refused =
        post("/v1/setup_intents", "2026-10-28.endive", "payment_method_types%5B0%5D=card");

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(refused.headers().firstValue("Stripe-Version")).contains("2026-10-28.endive");
    assertThat(new String(refused.body(), UTF_8))
        .contains("\"type\":\"invalid_request_error\"")
        .contains("\"code\":\"payment_method_types_no_longer_supported\"")
        .contains("\"param\":\"payment_method_types\"");
    assertThat(
            jdbc.sql("select count(*) from stripe_setup_intent where test_run_id = ?")
                .param(RUN.value())
                .query(Integer.class)
                .single())
        .isZero();
  }

  @Test
  void a_payment_keeps_the_types_allowed_over_the_wire_and_retrieve_answers_them() {
    HttpResponse<byte[]> created =
        post(
            "/v1/payment_intents",
            "2026-10-28.endive",
            "amount=1000&currency=jpy&allowed_payment_method_types%5B%5D=paypal"
                + "&allowed_payment_method_types%5B%5D=card");
    String intentId = theIdIn(new String(created.body(), UTF_8));
    HttpResponse<byte[]> retrieved = get("/v1/payment_intents/" + intentId, "2026-10-28.endive");

    for (HttpResponse<byte[]> answered : List.of(created, retrieved)) {
      assertThat(answered.statusCode()).isEqualTo(200);
      assertThat(new String(answered.body(), UTF_8))
          .contains("\"allowed_payment_method_types\":[\"paypal\",\"card\"]")
          .contains("\"automatic_payment_methods\":null")
          .contains("\"payment_method_types\":[\"card\"]");
    }
  }

  private String storedTypesOf(String setupIntentId) {
    return jdbc.sql(
            "select payment_method_types::text from stripe_setup_intent"
                + " where test_run_id = ? and setup_intent_id = ?")
        .params(RUN.value(), setupIntentId)
        .query(String.class)
        .single();
  }

  private static String theIdIn(String body) {
    String marker = "{\"id\":\"";
    int start = body.indexOf(marker) + marker.length();
    return body.substring(start, body.indexOf('"', start));
  }

  private HttpResponse<byte[]> post(String path, String version, String form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + SECRET_KEY)
            .header("Stripe-Version", version)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build());
  }

  private HttpResponse<byte[]> get(String path, String version) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + SECRET_KEY)
            .header("Stripe-Version", version)
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

  private static Map<String, Object> anAccount() {
    return Map.of(
        "accountId",
        MERCHANT,
        "configuration",
        Map.of(StripeAccount.SECRET_KEY_PROPERTY, SECRET_KEY, StripeCaptureWindow.SETTING, "P3D"));
  }

  private void configure(List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                accounts.isEmpty() ? Map.of() : Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                accounts,
                "webhooks",
                List.of()))
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
}
