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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class AuditUiIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-audit-open-it");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "ui-audit-open-user";
  private static final String PAGE_CODE = "ui-audit-open-page";
  private static final String MERCHANT = "merchant-audit-open";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String AUDIT = "/ui/audit";
  private static final String AFTER_MUST_NAME_AN_AUDIT_RECORD = "after must name an audit record";
  private static final String THE_RESERVED_RUN = "UNKNOWN";
  private static final String A_RUN_NOTHING_HOLDS = "ui-audit-open-no-such-run";
  private static final String THE_PANEL_THE_RESERVED_RUN_CARRIES =
      "The namespace of a failed resolution";
  private static final String THE_LINK_TO_THE_RESERVED_RUN = "Requests whose assignment failed";
  private static final String THE_ID_IS_RESERVED = "that id is reserved for a failed resolution";

  private static final Pattern OPEN_LINK =
      Pattern.compile("href=\"(/ui/audit\\?[^\"]*open=aud_[A-Za-z0-9]+)\"");

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
  void a_tester_opens_an_exchange_from_the_first_page_where_no_cursor_has_been_set_yet() {
    aHold();

    String firstPage = auditOf(RUN.value());
    String openLink = openLinkOn(firstPage);

    assertThat(openLink).doesNotContain("after=");

    String opened = page(openLink);

    assertThat(opened).doesNotContain(AFTER_MUST_NAME_AN_AUDIT_RECORD);
    assertThat(opened).contains("Show the values of this exchange");

    String closeLink = theLinkOn(opened, "close");
    String showValuesLink = theLinkOn(opened, "Show the values of this exchange");

    assertThat(closeLink).doesNotContain("after=");
    assertThat(showValuesLink).doesNotContain("after=");
  }

  @Test
  void the_open_link_a_page_carrying_no_narrowing_writes_names_a_blank_audit_id_and_still_opens() {
    aHold();

    String openLink = openLinkOn(auditOf(RUN.value()));

    assertThat(openLink).contains("auditId=&");

    String opened = page(openLink);

    assertThat(opened).contains("Show the values of this exchange");
    assertThat(opened).doesNotContain("was recorded under the audit id");
  }

  @Test
  void a_second_narrowing_that_empties_the_page_is_not_reported_as_an_exchange_that_is_gone() {
    aHold();
    String theExchange = theNewestAuditId();

    String narrowed = page(AUDIT + theRun() + "&auditId=" + theExchange);
    String andByMethod = page(AUDIT + theRun() + "&auditId=" + theExchange + "&method=GET");

    assertThat(narrowed).contains(theExchange);
    assertThat(narrowed).doesNotContain("was recorded under the audit id");
    assertThat(andByMethod).doesNotContain("was recorded under the audit id");
    assertThat(andByMethod).contains("No exchange of this test run matches.");
  }

  @Test
  void the_next_page_a_narrowed_read_offers_is_not_reported_as_an_exchange_the_trail_has_lost() {
    aHold();
    String theExchange = theNewestAuditId();

    String firstPage = page(AUDIT + theRun() + "&auditId=" + theExchange + "&limit=1");
    String nextPageLink = theLinkOn(firstPage, "Next page");
    String nextPage = page(nextPageLink);

    assertThat(firstPage).contains(theExchange);
    assertThat(nextPageLink).contains("after=" + theExchange).contains("auditId=" + theExchange);
    assertThat(nextPage).doesNotContain("was recorded under the audit id");
    assertThat(nextPage).contains("No exchange of this test run matches.");
  }

  @Test
  void the_trail_of_the_run_nothing_resolved_is_read_on_this_screen_rather_than_refused() {
    Instant beforeTheRequest = Instant.now().minusMillis(1);
    aHoldWhoseHeaderNamesARunNothingHolds();

    String reserved = auditOf(THE_RESERVED_RUN);
    String sinceTheRequest = page(AUDIT + theReservedRunSince(beforeTheRequest));

    assertThat(reserved)
        .as(
            "this screen resolved its run through a factory that refuses the reserved id, so the"
                + " rows of every request a tester's own application sent with the wrong test-run"
                + " header were readable on the control API and on the Correlation screen"
                + " and nowhere a tester clicks")
        .contains(THE_PANEL_THE_RESERVED_RUN_CARRIES)
        .doesNotContain(THE_ID_IS_RESERVED);
    assertThat(sinceTheRequest).contains(CREATE).contains("TEST_HEADER_UNMATCHED");
    assertThat(reserved)
        .as("the screen reading this namespace offers no link to the namespace it is reading")
        .doesNotContain(THE_LINK_TO_THE_RESERVED_RUN);
  }

  @Test
  void the_screen_reading_the_reserved_run_says_what_a_tester_changes_about_those_requests() {
    aHoldWhoseHeaderNamesARunNothingHolds();

    String reserved = auditOf(THE_RESERVED_RUN);

    assertThat(reserved)
        .as(
            "this assertion read \"a screen that writes refuses this namespace\" until that"
                + " classification was measured false, and now reads the way out the panel offers"
                + " a tester instead")
        .contains("none of them belongs to a test run a tester configured")
        .contains("a test-run header naming a run this simulator holds nothing under")
        .contains("Type a run id in the test run box at the top of this page");
    assertThat(page(theLinkOn(reserved, "Why each of these requests was assigned here")))
        .contains("TEST_HEADER_UNMATCHED");
  }

  @Test
  void a_tester_reading_their_own_run_is_one_click_from_the_requests_nothing_resolved() {
    aHoldWhoseHeaderNamesARunNothingHolds();
    aHold();

    String mine = auditOf(RUN.value());
    String theFailures = theLinkOn(mine, THE_LINK_TO_THE_RESERVED_RUN);

    assertThat(mine)
        .as("a run a tester configured is not the namespace of a failed resolution")
        .doesNotContain(THE_PANEL_THE_RESERVED_RUN_CARRIES);
    assertThat(theFailures).contains("testRunId=" + THE_RESERVED_RUN);
    assertThat(page(theFailures)).contains(THE_PANEL_THE_RESERVED_RUN_CARRIES);
  }

  @Test
  void a_tester_reads_the_header_their_own_application_sent_on_a_request_nothing_resolved() {
    Instant beforeTheRequest = Instant.now().minusMillis(1);
    aHoldWhoseHeaderNamesARunNothingHolds();

    String sinceTheRequest = page(AUDIT + theReservedRunSince(beforeTheRequest));
    String opened = page(openLinkOn(sinceTheRequest) + "&values=show");

    assertThat(opened)
        .as("the header a misconfigured application sent is what a tester asks this trail about")
        .contains("X-Simulator-Test-Run-Id")
        .contains(A_RUN_NOTHING_HOLDS);
  }

  @Test
  void a_request_whose_assignment_failed_is_no_row_of_the_run_a_tester_configured() {
    aHoldWhoseHeaderNamesARunNothingHolds();
    aHold();

    String mine = auditOf(RUN.value());

    assertThat(mine)
        .as("the trail of the run this test configured holds its own call and not the failed one")
        .contains(CREATE)
        .doesNotContain("TEST_HEADER_UNMATCHED");
  }

  @Test
  void an_audit_id_wider_than_a_row_holds_is_refused_and_the_refusal_never_prints_what_was_sent() {
    String tooWide = "z".repeat(4000);

    String refused = page(AUDIT + theRun() + "&auditId=" + tooWide);

    assertThat(theRefusalOn(refused)).contains("auditId must be at most 64 characters");
    assertThat(theRefusalOn(refused)).doesNotContain("zzzzzzzzzz");
    assertThat(refused).doesNotContain("<td class=\"stamp\"");
  }

  private static String theRefusalOn(String page) {
    Matcher refusal = Pattern.compile("<div class=\"refusal\">([^<]*)</div>").matcher(page);
    assertThat(refusal.find()).isTrue();
    return refusal.group(1);
  }

  private String theRun() {
    return "?provider=grow&testRunId=" + RUN.value();
  }

  private String theReservedRunSince(Instant written) {
    return "?provider=grow&testRunId=" + THE_RESERVED_RUN + "&from=" + written;
  }

  private void aHoldWhoseHeaderNamesARunNothingHolds() {
    HttpResponse<byte[]> answered =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + CREATE))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Simulator-Test-Run-Id", A_RUN_NOTHING_HOLDS)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(holdForm()))));
    assertThat(answered.statusCode()).isEqualTo(200);
  }

  private String theNewestAuditId() {
    List<Map<String, Object>> trail =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(trail).isNotNull();
    return String.valueOf(trail.get(trail.size() - 1).get("auditId"));
  }

  private static String openLinkOn(String page) {
    Matcher link = OPEN_LINK.matcher(page);
    assertThat(link.find()).isTrue();
    return link.group(1).replace("&amp;", "&");
  }

  private static String theLinkOn(String page, String text) {
    Matcher link =
        Pattern.compile("href=\"([^\"]+)\"\\s*>\\s*" + Pattern.quote(text)).matcher(page);
    assertThat(link.find()).isTrue();
    return link.group(1).replace("&amp;", "&");
  }

  private String auditOf(String testRunId) {
    return page(AUDIT + "?provider=grow&testRunId=" + testRunId);
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(
                    URI.create(uri.startsWith("http") ? uri : "http://localhost:" + port + uri))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private void aHold() {
    HttpResponse<byte[]> created =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + CREATE))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(holdForm()))));
    assertThat(created.statusCode()).isEqualTo(200);
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
