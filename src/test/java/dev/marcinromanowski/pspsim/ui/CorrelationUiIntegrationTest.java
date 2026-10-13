package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class CorrelationUiIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-correlation-it");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "ui-correlation-user";
  private static final String PAGE_CODE = "ui-correlation-page";
  private static final String MERCHANT = "merchant-correlation";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String CORRELATION = "/ui/correlation";
  private static final String NO_SUCH_RUN = "no-such-run";

  private static final Pattern OPENS_A_REQUEST = Pattern.compile("open=(aud_[A-Za-z0-9]+)");

  private static final Pattern REFUSES = Pattern.compile("<div class=\"refusal\">([^<]*)</div>");

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
  }

  @Test
  void a_request_no_header_named_says_its_credentials_assigned_it_and_which_rule_that_was() {
    aHold(null);

    String page = correlationOf(RUN.value());

    assertThat(page).contains("ACCOUNT_CREDENTIALS").contains(CREATE);
  }

  @Test
  void a_request_carrying_the_test_run_header_says_the_header_assigned_it_and_not_its_account() {
    aHold(RUN.value());

    String page = correlationOf(RUN.value());

    assertThat(page).contains("TEST_HEADER").contains(CREATE);
  }

  @Test
  void a_header_naming_no_run_ends_resolution_and_the_request_is_read_under_the_reserved_run() {
    aHold(NO_SUCH_RUN);

    String reserved = correlationOf("UNKNOWN");

    assertThat(reserved).contains("TEST_HEADER_UNMATCHED").contains(CREATE);
  }

  @Test
  void a_request_whose_assignment_failed_is_not_shown_among_the_requests_of_a_configured_run() {
    aHold(NO_SUCH_RUN);

    String configured = correlationOf(RUN.value());

    assertThat(configured).doesNotContain("TEST_HEADER_UNMATCHED");
  }

  @Test
  void the_reserved_namespace_says_every_request_it_holds_is_one_whose_assignment_failed() {
    aHold(NO_SUCH_RUN);

    String reserved = correlationOf("UNKNOWN");

    assertThat(reserved).contains("namespace this simulator keeps a request under when resolution");
  }

  @Test
  void an_explained_request_names_the_account_the_run_holds_now_rather_than_the_one_it_matched() {
    aHold(null);

    String explained = explainTheFirstRequestOf(RUN.value());

    assertThat(explained).contains(MERCHANT).contains("not the account it matched");
  }

  @Test
  void
      a_customer_this_provider_never_carries_is_reported_in_its_words_and_not_as_a_gap_of_this_build() {
    aHold(null);

    String explained = explainTheFirstRequestOf(RUN.value());

    assertThat(explained)
        .contains(
            "provider grow publishes no customer or owner id on any endpoint of this contract")
        .doesNotContain(CorrelationScreen.NO_OWNER_IS_RECORDED);
  }

  @Test
  void an_account_this_build_never_recorded_is_reported_as_a_gap_of_this_build_and_not_as_absent() {
    aHold(null);

    String explained = explainTheFirstRequestOf(RUN.value());

    assertThat(explained).contains("the account a request resolved to is not a column");
  }

  @Test
  void a_credential_that_resolved_the_account_is_not_rendered_even_when_values_are_asked_for() {
    aHold(null);

    String shown = explainTheFirstRequestOf(RUN.value()) + valuesShownFor(RUN.value());

    assertThat(shown).contains("userId").doesNotContain(USER_ID);
  }

  @Test
  void a_test_run_outside_the_published_shape_is_refused_on_the_screen_rather_than_breaking_it() {
    String refused = correlationOf("a%20run%2Fwith%20a%20slash");

    assertThat(theRefusalOn(refused))
        .as(
            "the property a tester typed into is named once, not twice, and the pattern a suite"
                + " author matches on is followed by the characters a run name is made of, for"
                + " the tester who typed a slash into the same box")
        .isEqualTo(
            "testRunId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}, which is at most 64"
                + " characters, the first a letter or a digit and the rest a letter, a digit, a"
                + " dot, a hyphen or an underscore, for example checkout-smoke");
  }

  private static String theRefusalOn(String page) {
    Matcher found = REFUSES.matcher(page);
    assertThat(found.find()).isTrue();
    return found.group(1);
  }

  private String explainTheFirstRequestOf(String testRunId) {
    return page(
        CORRELATION
            + "?provider=grow&testRunId="
            + testRunId
            + "&open="
            + theFirstAuditIdOf(testRunId));
  }

  private String valuesShownFor(String testRunId) {
    return page(
        CORRELATION
            + "?provider=grow&testRunId="
            + testRunId
            + "&open="
            + theFirstAuditIdOf(testRunId)
            + "&values=show");
  }

  private String theFirstAuditIdOf(String testRunId) {
    Matcher found = OPENS_A_REQUEST.matcher(correlationOf(testRunId));
    assertThat(found.find()).isTrue();
    return found.group(1);
  }

  private String correlationOf(String testRunId) {
    return page(CORRELATION + "?provider=grow&testRunId=" + testRunId);
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + uri))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private void aHold(String declaredTestRun) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + CREATE))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(holdForm())));
    if (declaredTestRun != null) {
      request = request.header("X-Simulator-Test-Run-Id", declaredTestRun);
    }
    send(request);
  }

  private Map<String, String> holdForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("userId", USER_ID);
    form.put("chargeType", "2");
    form.put("sum", "120.50");
    form.put("saveCardToken", "1");
    form.put("description", "A hold");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("pageField[fullName]", "Israel Israeli");
    form.put("pageField[phone]", "0505503144");
    return form;
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) {
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

  private void configure() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", PINNED.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", USER_ID, "pageCode", PAGE_CODE)))))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure() {
    http.method(HttpMethod.DELETE)
        .uri("/__simulator/grow/config/accounts/" + MERCHANT + "?testRunId=" + RUN.value())
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }
}
