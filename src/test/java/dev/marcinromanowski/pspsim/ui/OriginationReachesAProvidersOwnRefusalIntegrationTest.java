package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class OriginationReachesAProvidersOwnRefusalIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "origination-provider-refusal-it";

  private static final String PROVIDER = "stripe";

  private static final String ACCOUNT = "merchant-1";

  private static final String SECRET_KEY = "sk_test_51OriginationRefusalSimulator";

  private static final String A_HOLD = "capture_method=manual";

  private static final String WIZARD = "/ui/objects/new";

  private static final String START = "/ui/objects/start";

  private static final String CONFIRM =
      TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "confirm";

  private static final String A_VALUE_STRIPE_DOES_NOT_PUBLISH = "perhaps";

  private static final String THE_WORDS_STRIPE_REFUSES_IT_IN =
      "A parameter carries a value this API does not accept. It accepts true, false.";

  private static final String THE_WORDS_THE_CORE_USED_TO_REFUSE_IT_IN =
      "which this provider does not publish";

  private static final String THE_LIST_THE_PUBLISHED_VALUES_ARE_SUGGESTED_FROM =
      "confirm-the-values-this-provider-publishes";

  private static final String AN_APOSTROPHE_AS_THE_TEMPLATE_ENGINE_WRITES_IT = "&#39;";

  private static final String THE_SENTENCE_BESIDE_THE_PUBLISHED_VALUES =
      TheFieldsAFormDraws.THE_VALUES_THIS_PROVIDER_PUBLISHES_ARE_OFFERED_AND_NONE_IS_IMPOSED
          .replace("'", AN_APOSTROPHE_AS_THE_TEMPLATE_ENGINE_WRITES_IT);

  private static final Pattern THE_SUGGESTIONS_BESIDE_THE_CONFIRM_FIELD =
      Pattern.compile("(?s)<datalist[^>]*id=\"confirm-the-values-this-provider-publishes\"");

  private static final Pattern A_BOX_FOR_THE_CONFIRM_FIELD =
      Pattern.compile("(?s)<input[^>]*name=\"field\\.confirm\"[^>]*>");

  private static final Pattern A_LIST_FOR_THE_CONFIRM_FIELD =
      Pattern.compile("(?s)<select[^>]*name=\"field\\.confirm\"[^>]*>");

  private static final Pattern THE_PAYMENT_IT_STARTED =
      Pattern.compile("started=(pi_sim_[0-9a-f]+)");

  @LocalServerPort private int port;

  private HttpClient wire;
  private RestClient http;

  @BeforeEach
  void configureTheAccountThisPaymentIsStartedOn() {
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    http = RestClient.create("http://localhost:" + port);
    http.put()
        .uri("/__simulator/" + PROVIDER + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN,
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        ACCOUNT,
                        "configuration",
                        Map.of("secretKey", SECRET_KEY, "captureWindow", "P3D")))))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void a_field_whose_values_this_provider_publishes_is_a_box_a_tester_types_any_value_into() {
    String wizard = bodyOf(get(theSecondStepOfTheWizard()));

    assertThat(A_BOX_FOR_THE_CONFIRM_FIELD.matcher(wizard).find())
        .as(
            "a tester reaches a provider's refusal of a value it does not publish by typing that"
                + " value, so the control this field is drawn as has to take one")
        .isTrue();
    assertThat(A_LIST_FOR_THE_CONFIRM_FIELD.matcher(wizard).find())
        .as(
            "a list a tester can only pick a published value from is the browser validating what"
                + " was typed, which leaves this provider's own refusal on no screen at all")
        .isFalse();
    assertThat(THE_SUGGESTIONS_BESIDE_THE_CONFIRM_FIELD.matcher(wizard).find())
        .as("the values this provider publishes are suggested on the box rather than imposed on it")
        .isTrue();
    assertThat(wizard)
        .contains("list=\"" + THE_LIST_THE_PUBLISHED_VALUES_ARE_SUGGESTED_FROM + "\"")
        .contains("yes, confirm it now with the payment method below")
        .contains("no, leave it waiting for a payment method")
        .contains(THE_SENTENCE_BESIDE_THE_PUBLISHED_VALUES);
  }

  @Test
  void a_value_this_provider_does_not_publish_is_refused_on_the_screen_in_this_providers_words() {
    HttpResponse<byte[]> answered = post(START, aHoldThatConfirms(A_VALUE_STRIPE_DOES_NOT_PUBLISH));

    assertThat(answered.statusCode()).isEqualTo(200);
    assertThat(bodyOf(answered))
        .as(
            "the provider is this simulator's only validator, so the refusal a tester reads is"
                + " the one stripe publishes for a confirm it cannot read")
        .contains(THE_WORDS_STRIPE_REFUSES_IT_IN)
        .doesNotContain(THE_WORDS_THE_CORE_USED_TO_REFUSE_IT_IN);
  }

  @Test
  void a_value_this_provider_does_publish_still_starts_the_payment_it_was_typed_into() {
    HttpResponse<byte[]> answered = post(START, aHoldThatConfirms("false"));

    assertThat(answered.statusCode()).isEqualTo(302);
    assertThat(
            THE_PAYMENT_IT_STARTED
                .matcher(answered.headers().firstValue("Location").orElseThrow())
                .find())
        .as("the values a provider publishes are still the ones a payment is usually started with")
        .isTrue();
  }

  private static Map<String, String> aHoldThatConfirms(String confirm) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("accountId", ACCOUNT);
    form.put("startedAs", A_HOLD);
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "amount", "1000");
    form.put(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "currency", "usd");
    form.put(CONFIRM, confirm);
    return form;
  }

  private String theSecondStepOfTheWizard() {
    return WIZARD
        + "?provider="
        + PROVIDER
        + "&testRunId="
        + RUN
        + "&accountId="
        + ACCOUNT
        + "&startedAs="
        + URLEncoder.encode(A_HOLD, UTF_8);
  }

  private HttpResponse<byte[]> get(String path) {
    return read(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(20))
            .GET());
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> fields) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", PROVIDER);
    form.put("testRunId", RUN);
    form.putAll(fields);
    return read(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
  }

  private HttpResponse<byte[]> read(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String bodyOf(HttpResponse<byte[]> answered) {
    return new String(answered.body(), UTF_8);
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }
}
