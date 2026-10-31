package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer.ReceivedCallback;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class StripeApiVersionIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-api-version-it");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51ApiVersionSimulator";
  private static final String THREE_DAYS = "P3D";

  private static final String VERSION_HEADER = "Stripe-Version";

  private static final String PAYMENT_INTENTS = "/v1/payment_intents";

  private static final String THE_NEWEST_RELEASE = "2026-10-28.endive";
  private static final String THE_JAVA_SDKS_RELEASE = "2026-08-26.dahlia";
  private static final String A_BASIL_RELEASE = "2025-06-30.basil";
  private static final String A_DAHLIA_RELEASE = "2026-05-27.dahlia";
  private static final String AN_ACACIA_RELEASE = "2025-02-24.acacia";
  private static final String A_DATE_ONLY_RELEASE = "2023-10-16";

  private static final String THE_RANGE = "2025-03-31.basil through " + THE_NEWEST_RELEASE;

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();
  private final LocalMerchantServer merchant = new LocalMerchantServer();

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    reset();
  }

  @AfterEach
  void unconfigure() {
    reset();
    merchant.close();
  }

  @Test
  void a_request_naming_a_version_in_the_range_is_answered_and_told_the_version_it_got() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> created = aHold(VERSION_HEADER, THE_JAVA_SDKS_RELEASE);

    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.headers().allValues(VERSION_HEADER)).containsExactly(THE_JAVA_SDKS_RELEASE);
    assertThat(theTrailOfThisRun()).contains("\"Stripe-Version\":[\"" + THE_JAVA_SDKS_RELEASE);
  }

  @Test
  void a_request_naming_no_version_is_answered_at_the_default_its_account_was_configured_with() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> created = aHold(null, null);

    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.headers().allValues(VERSION_HEADER)).containsExactly(A_BASIL_RELEASE);
  }

  @Test
  void a_request_naming_no_version_of_an_account_configured_without_one_gets_the_newest() {
    configure(null);

    HttpResponse<byte[]> created = aHold(null, null);

    assertThat(created.statusCode()).isEqualTo(200);
    assertThat(created.headers().allValues(VERSION_HEADER)).containsExactly(THE_NEWEST_RELEASE);
    assertThat(theStoredDefault()).isEqualTo(THE_NEWEST_RELEASE);
  }

  @Test
  void the_header_name_is_matched_whatever_case_the_client_spells_it_in() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> lower =
        aHold(VERSION_HEADER.toLowerCase(Locale.ROOT), THE_JAVA_SDKS_RELEASE);
    HttpResponse<byte[]> mixed = aHold(VERSION_HEADER.toUpperCase(Locale.ROOT), A_DAHLIA_RELEASE);

    assertThat(lower.statusCode()).isEqualTo(200);
    assertThat(lower.headers().allValues(VERSION_HEADER)).containsExactly(THE_JAVA_SDKS_RELEASE);
    assertThat(mixed.statusCode()).isEqualTo(200);
    assertThat(mixed.headers().allValues(VERSION_HEADER)).containsExactly(A_DAHLIA_RELEASE);
  }

  @Test
  void an_acacia_release_is_refused_with_an_answer_naming_the_range_and_creates_nothing() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> refused = aHold(VERSION_HEADER, AN_ACACIA_RELEASE);

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("\"type\":\"invalid_request_error\"")
        .contains(theRefusalOfAPublished(AN_ACACIA_RELEASE))
        .contains(THE_RANGE)
        .doesNotContain("\"code\"");
    assertThat(refused.headers().allValues(VERSION_HEADER)).isEmpty();
    assertThat(refused.headers().firstValue("Request-Id")).isPresent();
    assertThat(theTrailOfThisRun()).doesNotContain("pi_sim_");
  }

  @Test
  void a_date_only_release_is_refused_with_an_answer_naming_the_range() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> refused = aHold(VERSION_HEADER, A_DATE_ONLY_RELEASE);

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains(theRefusalOfAPublished(A_DATE_ONLY_RELEASE))
        .contains(THE_RANGE);
  }

  @Test
  void a_value_naming_no_published_version_is_refused_as_an_invalid_version() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> refused = aHold(VERSION_HEADER, "2026-13-01.yarrow");

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Invalid Stripe API"
                + " version: 2026-13-01.yarrow\"}}");
  }

  @Test
  void a_backend_mints_an_ephemeral_key_at_the_version_the_android_sdk_handed_it() {
    configure(A_BASIL_RELEASE);
    String customerId = valueIn(bodyOf(posted("/v1/customers", Map.of(), null)), "id");

    HttpResponse<byte[]> minted =
        posted("/v1/ephemeral_keys", Map.of("customer", customerId), "2020-03-02");

    assertThat(minted.statusCode()).isEqualTo(200);
    assertThat(minted.headers().allValues(VERSION_HEADER)).containsExactly("2020-03-02");
    assertThat(bodyOf(minted)).contains("\"object\":\"ephemeral_key\"");
  }

  @Test
  void a_form_this_api_cannot_read_is_refused_naming_the_version_it_was_answered_at() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> refused =
        posted(PAYMENT_INTENTS, Map.of("[amount", "1"), THE_JAVA_SDKS_RELEASE);

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(refused.headers().allValues(VERSION_HEADER)).containsExactly(THE_JAVA_SDKS_RELEASE);
  }

  @Test
  void a_release_dated_after_the_catalogue_is_refused_as_one_this_build_may_not_know() {
    configure(A_BASIL_RELEASE);

    HttpResponse<byte[]> refused = aHold(VERSION_HEADER, "2026-11-25.endive");

    assertThat(refused.statusCode()).isEqualTo(400);
    assertThat(bodyOf(refused))
        .contains("is dated after " + THE_NEWEST_RELEASE)
        .contains(THE_RANGE)
        .doesNotContain("Invalid Stripe API version");
  }

  @Test
  void an_event_is_rendered_at_the_default_version_of_the_account_it_belongs_to() {
    configure(A_DAHLIA_RELEASE);
    String intentId = valueIn(bodyOf(aHold(VERSION_HEADER, THE_JAVA_SDKS_RELEASE)), "id");

    sendWebhook(intentId);

    List<ReceivedCallback> delivered = merchant.received();
    assertThat(delivered).hasSize(1);
    assertThat(delivered.get(0).body())
        .contains("\"api_version\":\"" + A_DAHLIA_RELEASE + "\"")
        .doesNotContain(THE_JAVA_SDKS_RELEASE);
  }

  @Test
  void the_control_api_publishes_the_field_with_every_version_it_answers_as_a_choice() {
    Map<String, Object> fields =
        http.get()
            .uri("/__simulator/stripe/config/fields")
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(String.valueOf(fields))
        .contains("key=apiVersion")
        .contains("label=default API version")
        .contains("value=2025-03-31.basil")
        .contains("value=" + THE_NEWEST_RELEASE)
        .doesNotContain("value=" + AN_ACACIA_RELEASE);
  }

  @Test
  void the_control_api_refuses_an_account_default_this_simulator_does_not_answer() {
    HttpResponse<byte[]> refused = configured(AN_ACACIA_RELEASE);

    assertThat(refused.statusCode()).isBetween(400, 499);
    assertThat(bodyOf(refused))
        .contains("apiVersion must be one of the Stripe API versions this simulator answers")
        .contains(THE_RANGE);
  }

  @Test
  void the_account_form_offers_the_versions_as_a_list_and_stores_the_one_picked() {
    String form = page("/ui/configuration/account?provider=stripe&testRunId=" + RUN.value());

    Matcher list =
        Pattern.compile("<select[^>]*name=\"field\\.apiVersion\"(.*?)</select>", Pattern.DOTALL)
            .matcher(form);
    assertThat(list.find()).as("the form draws the default version as a list").isTrue();
    assertThat(list.group(1))
        .contains("value=\"2025-03-31.basil\"")
        .contains("value=\"" + THE_NEWEST_RELEASE + "\"")
        .contains("the release that opened basil and its breaks")
        .doesNotContain(AN_ACACIA_RELEASE);

    Map<String, String> submitted = new LinkedHashMap<>();
    submitted.put("provider", "stripe");
    submitted.put("testRunId", RUN.value());
    submitted.put("accountId", MERCHANT);
    submitted.put("field." + StripeAccount.SECRET_KEY_PROPERTY, SECRET_KEY);
    submitted.put("field." + StripeCaptureWindow.SETTING, "3");
    submitted.put("unitOf." + StripeCaptureWindow.SETTING, "DAYS");
    submitted.put("field." + StripeApiVersion.SETTING, A_DAHLIA_RELEASE);
    HttpResponse<byte[]> posted = postedForm("/ui/configuration/account", submitted);

    assertThat(posted.statusCode()).isEqualTo(302);
    assertThat(aHold(null, null).headers().allValues(VERSION_HEADER))
        .containsExactly(A_DAHLIA_RELEASE);
  }

  private String theStoredDefault() {
    return jdbc.sql(
            "select api_version from stripe_account where test_run_id = ? and account_id = ?")
        .param(RUN.value())
        .param(MERCHANT)
        .query(String.class)
        .single();
  }

  private void sendWebhook(String intentId) {
    http.post()
        .uri("/__simulator/stripe/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", intentId))
        .retrieve()
        .toBodilessEntity();
  }

  private String theTrailOfThisRun() {
    return http.get()
        .uri("/__simulator/stripe/audit?testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }

  private void configure(@Nullable String apiVersion) {
    assertThat(configured(apiVersion).statusCode()).isBetween(200, 299);
  }

  private HttpResponse<byte[]> configured(@Nullable String apiVersion) {
    Map<String, String> configuration = new LinkedHashMap<>();
    configuration.put(StripeAccount.SECRET_KEY_PROPERTY, SECRET_KEY);
    configuration.put(StripeCaptureWindow.SETTING, THREE_DAYS);
    if (apiVersion != null) {
      configuration.put(StripeApiVersion.SETTING, apiVersion);
    }
    String body =
        "{\"testRunId\":\""
            + RUN.value()
            + "\",\"clock\":{\"fixedNow\":\""
            + FIXED_NOW
            + "\"},\"accounts\":[{\"accountId\":\""
            + MERCHANT
            + "\",\"configuration\":"
            + jsonOf(configuration)
            + "}],\"webhooks\":[{\"accountId\":\""
            + MERCHANT
            + "\",\"url\":\""
            + merchant.url()
            + "\"}]}";
    return sent(
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/__simulator/stripe/config"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .PUT(HttpRequest.BodyPublishers.ofString(body)));
  }

  private static String jsonOf(Map<String, String> values) {
    List<String> members = new ArrayList<>();
    values.forEach((name, value) -> members.add("\"" + name + "\":\"" + value + "\""));
    return "{" + String.join(",", members) + "}";
  }

  private void reset() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private HttpResponse<byte[]> aHold(@Nullable String versionHeader, @Nullable String version) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    form.put("return_url", "https://shop.example/checkout/return");
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + PAYMENT_INTENTS))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)));
    if (versionHeader != null && version != null) {
      request = request.header(versionHeader, version);
    }
    return sent(request);
  }

  private HttpResponse<byte[]> posted(
      String path, Map<String, String> form, @Nullable String version) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + SECRET_KEY)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)));
    if (version != null) {
      request = request.header(VERSION_HEADER, version);
    }
    return sent(request);
  }

  private HttpResponse<byte[]> postedForm(String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
  }

  private String page(String path) {
    HttpResponse<byte[]> answer =
        sent(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(answer.statusCode()).isEqualTo(200);
    return bodyOf(answer);
  }

  private HttpResponse<byte[]> sent(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private static String theRefusalOfAPublished(String version) {
    return VERSION_HEADER + " " + version + " is a published Stripe API version";
  }

  private static String bodyOf(HttpResponse<byte[]> response) {
    return new String(response.body(), UTF_8);
  }

  private static String valueIn(String json, String member) {
    String opening = "\"" + member + "\":\"";
    int start = json.indexOf(opening);
    int from = start + opening.length();
    return json.substring(from, json.indexOf('"', from));
  }
}
