package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeNamespace;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SmokeUiIntegrationTest extends PostgresBackedTest {

  private static final String SCREEN = "/ui/smoke";

  private static final String A_RUN_OF_THE_TESTERS_OWN = "a-tester-of-their-own";

  private static final String ON_THE_CHECKS_CREDENTIALS = "a-tester-on-the-checks-credentials";

  private static final String THE_USER_THE_GROW_CHECKS_CONFIGURE = "smoke-user";

  private static final String THE_PAGE_THE_GROW_CHECKS_CONFIGURE = "smoke-page";

  private static final Pattern MILLIS = Pattern.compile("<span>(\\d+)</span>\\s*ms");

  private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);

  private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

  private static final CheckJson JSON = new CheckJson();

  @LocalServerPort private int port;

  @Autowired private ProviderRegistry providers;

  @Autowired private ProviderAccounts accounts;

  @Autowired private ProviderConfigurations configurations;

  private HttpClient wire;

  @BeforeEach
  void connect() {
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Test
  void the_screen_is_named_in_the_navigation_bar_the_screens_share() {
    assertThat(screen()).contains(">Checks against the real PSP</a");
  }

  @Test
  @Order(1)
  void a_simulator_nobody_has_pressed_the_button_on_says_that_nothing_has_been_checked() {
    assertThat(screen()).contains("Nothing has been checked against a real PSP in this simulator");
  }

  @Test
  void the_screen_says_which_run_the_checks_write_to_so_a_tester_knows_it_is_not_theirs() {
    assertThat(screen()).contains("smoke");
  }

  @Test
  void every_applicable_check_holds_against_this_simulators_own_routes_and_none_is_left_empty() {
    assertThat(post(SCREEN + "/run", Map.of()).statusCode()).isEqualTo(302);

    String read = screen();
    assertThat(read).contains(SmokeScreen.EVERY_APPLICABLE_CHECK_HELD);
    assertThat(read).doesNotContain(SmokeScreen.A_CHECK_DID_NOT_HOLD);
    assertThat(read).doesNotContain("the check asserts nothing");
    assertThat(read).doesNotContain("the route did not answer");
  }

  @Test
  void the_run_covers_every_provider_this_build_registers_and_leaves_none_uncovered() {
    post(SCREEN + "/run", Map.of());

    String read = screen();
    assertThat(read)
        .contains(">grow</td>")
        .contains(">stripe</td>")
        .contains(">azericard</td>")
        .contains(">worldline</td>")
        .contains(">autra</td>");
    assertThat(read).doesNotContain("No check is published yet for");
  }

  @Test
  void a_check_the_grow_contract_publishes_no_signature_for_is_shown_as_not_applying() {
    post(SCREEN + "/run", Map.of());

    assertThat(theRowOf(screen(), "grow", "POST notifyUrl"))
        .containsExactly(
            "grow",
            "POST notifyUrl",
            "does not apply",
            "",
            "Grow signs no server-to-server callback, so a signature has nothing to be checked"
                + " against (grow/unsigned-callback: published example,"
                + " https://developers.grow.business/reference/server-to-server-callback"
                + " (grow.md, Merchant Callback Contract);"
                + " the published callback carries no signature)");
  }

  @Test
  void the_whole_run_finishes_inside_the_budget_a_tester_will_wait_at_a_screen() {
    post(SCREEN + "/run", Map.of());

    Matcher took = MILLIS.matcher(screen());
    assertThat(took.find()).isTrue();
    assertThat(Long.parseLong(took.group(1))).isLessThan(15_000);
  }

  @Test
  void a_press_leaves_the_testers_own_run_exactly_as_it_was() {
    configureTheTestersOwnRun();
    createAHoldIn(A_RUN_OF_THE_TESTERS_OWN);
    List<String> auditBefore = auditIdsOf(A_RUN_OF_THE_TESTERS_OWN);
    List<String> objectsBefore = objectIdsOf(A_RUN_OF_THE_TESTERS_OWN);
    assertThat(auditBefore).isNotEmpty();
    assertThat(objectsBefore).isNotEmpty();

    post(SCREEN + "/run", Map.of());

    assertThat(auditIdsOf(A_RUN_OF_THE_TESTERS_OWN)).isEqualTo(auditBefore);
    assertThat(objectIdsOf(A_RUN_OF_THE_TESTERS_OWN)).isEqualTo(objectsBefore);
  }

  @Test
  void a_press_leaves_the_default_run_exactly_as_it_was() {
    List<String> auditBefore = auditIdsOf("default");
    List<String> objectsBefore = objectIdsOf("default");

    post(SCREEN + "/run", Map.of());

    assertThat(auditIdsOf("default")).isEqualTo(auditBefore);
    assertThat(objectIdsOf("default")).isEqualTo(objectsBefore);
  }

  @Test
  void a_press_does_write_to_the_check_run_so_the_isolation_is_not_inertness() {
    post(SCREEN + "/run", Map.of());

    assertThat(auditIdsOf("smoke")).isNotEmpty();
    assertThat(objectIdsOf("smoke")).isNotEmpty();
  }

  @Test
  void a_press_leaves_no_account_or_route_alias_configured_in_the_check_run_once_it_finishes() {
    post(SCREEN + "/run", Map.of());

    for (SimulatorProvider provider : providers.modules()) {
      assertThat(accounts.identitiesOf(provider.id(), SmokeNamespace.RUN))
          .as("accounts %s keeps in the check run", provider.id().value())
          .isEmpty();
      assertThat(configurations.routeAliasesOf(provider, SmokeNamespace.RUN))
          .as("route aliases %s keeps in the check run", provider.id().value())
          .isEmpty();
    }
  }

  @Test
  void a_tester_on_the_checks_credentials_still_reaches_their_own_run_after_a_press() {
    put(
        "/__simulator/grow/config",
        "{\"testRunId\":\""
            + ON_THE_CHECKS_CREDENTIALS
            + "\",\"accounts\":[{\"accountId\":\"their-merchant\",\"configuration\":"
            + "{\"userId\":\""
            + THE_USER_THE_GROW_CHECKS_CONFIGURE
            + "\",\"pageCode\":\""
            + THE_PAGE_THE_GROW_CHECKS_CONFIGURE
            + "\"}}],\"webhooks\":[]}");

    post(SCREEN + "/run", Map.of());

    HttpResponse<byte[]> answer =
        send(aHoldFor(THE_USER_THE_GROW_CHECKS_CONFIGURE, THE_PAGE_THE_GROW_CHECKS_CONFIGURE));
    assertThat(JSON.at(new String(answer.body(), UTF_8), "/status")).isEqualTo("1");
    assertThat(objectIdsOf(ON_THE_CHECKS_CREDENTIALS)).hasSize(1);
  }

  private void configureTheTestersOwnRun() {
    put(
        "/__simulator/grow/config",
        "{\"testRunId\":\""
            + A_RUN_OF_THE_TESTERS_OWN
            + "\",\"accounts\":[{\"accountId\":"
            + "\"their-merchant\",\"configuration\":{\"userId\":\"their-user\",\"pageCode\":"
            + "\"their-page\"}}],\"webhooks\":[]}");
  }

  private void createAHoldIn(String testRunId) {
    HttpResponse<byte[]> answer =
        send(aHoldFor("their-user", "their-page").header("X-Simulator-Test-Run-Id", testRunId));
    assertThat(JSON.at(new String(answer.body(), UTF_8), "/status")).isEqualTo("1");
  }

  private HttpRequest.Builder aHoldFor(String userId, String pageCode) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", pageCode);
    form.put("userId", userId);
    form.put("chargeType", "2");
    form.put("sum", "77.00");
    form.put("description", "a payment of the tester's own");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("pageField[fullName]", "Their Payer");
    form.put("pageField[phone]", "0500000000");
    return HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/api/light/server/1.0/createPaymentProcess"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .timeout(Duration.ofSeconds(20))
        .POST(HttpRequest.BodyPublishers.ofString(encoded(form)));
  }

  private List<String> auditIdsOf(String testRunId) {
    return valuesUnder(get("/__simulator/grow/audit?limit=500&testRunId=" + testRunId), "auditId");
  }

  private List<String> objectIdsOf(String testRunId) {
    return valuesUnder(
        get("/__simulator/grow/objects?limit=500&testRunId=" + testRunId), "objects");
  }

  private static List<String> valuesUnder(String body, String field) {
    List<String> found = new ArrayList<>();
    Matcher named = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
    while (named.find()) {
      found.add(named.group(1));
    }
    if ("objects".equals(field)) {
      Matcher ids = Pattern.compile("\"providerObjectId\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
      found.clear();
      while (ids.find()) {
        found.add(ids.group(1));
      }
    }
    return found;
  }

  private static List<String> theRowOf(String page, String provider, String operation) {
    Matcher rows = A_ROW.matcher(page);
    while (rows.find()) {
      List<String> cells = new ArrayList<>();
      Matcher cell = A_CELL.matcher(rows.group(1));
      while (cell.find()) {
        cells.add(cell.group(1).strip());
      }
      if (cells.size() > 1 && cells.get(0).equals(provider) && cells.get(1).equals(operation)) {
        return cells;
      }
    }
    throw new AssertionError("the screen shows no row for " + provider + " " + operation);
  }

  private String screen() {
    HttpResponse<byte[]> answer =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + SCREEN))
                .timeout(Duration.ofSeconds(30))
                .GET());
    assertThat(answer.statusCode()).isEqualTo(200);
    return new String(answer.body(), UTF_8);
  }

  private String get(String path) {
    HttpResponse<byte[]> answer =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET());
    return new String(answer.body(), UTF_8);
  }

  private void put(String path, String body) {
    send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(20))
            .PUT(HttpRequest.BodyPublishers.ofString(body)));
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
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
}
