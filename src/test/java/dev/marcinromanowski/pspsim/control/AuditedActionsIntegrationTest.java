package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AuditedActionsIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("audited-actions-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final Instant PINNED_FROM_THE_SCREEN = Instant.parse("2026-09-08T09:30:00Z");
  private static final String MERCHANT = "merchant-1";
  private static final String USER_ID = "audited-actions-user-must-never-be-read-back";
  private static final String PAGE_CODE = "audited-actions-page-must-never-be-read-back";
  private static final String CONFIG = "/__simulator/grow/config";
  private static final String RESET = "/__simulator/grow/reset";
  private static final String AUDIT = "/__simulator/grow/audit";
  private static final String UI_CLOCK = "/ui/configuration/clock";
  private static final String UI_ACCOUNT_REMOVE = "/ui/configuration/account/remove";
  private static final String UI_ACCOUNT = "/ui/configuration/account";
  private static final String UI_DELIVER_DUE = "/ui/webhooks/deliver-due";

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;

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
    reset();
  }

  @Test
  void a_tester_who_pins_a_clock_from_the_screen_finds_it_named_as_a_manual_action() {
    assertThat(pinTheClockFromTheScreen().status()).isEqualTo(302);

    Map<String, Object> pinned = theRowAt(theTrailOf("?origin=UI"), UI_CLOCK);

    assertThat(pinned).containsEntry("method", "POST").containsEntry("origin", "UI");
    assertThat(bodyOf(pinned))
        .containsEntry("clock.fixedNow", List.of(PINNED_FROM_THE_SCREEN.toString()));
    assertThat(pathsOf(theTrailOf("?origin=PROVIDER_API"))).doesNotContain(UI_CLOCK);
  }

  @Test
  void a_harness_that_configured_an_account_finds_it_named_as_a_control_action() {
    Map<String, Object> configured = theRowAt(theTrailOf("?origin=CONTROL_API"), CONFIG);

    assertThat(configured)
        .containsEntry("method", "PUT")
        .containsEntry("origin", "CONTROL_API")
        .containsEntry("status", 200);
    assertThat(bodyOf(configured))
        .containsEntry("accounts[0].accountId", List.of(MERCHANT))
        .containsEntry("clock.fixedNow", List.of(FIXED_NOW.toString()));
    assertThat(pathsOf(theTrailOf("?origin=UI"))).doesNotContain(CONFIG);
  }

  @Test
  void the_secrets_a_configuration_carried_reach_the_trail_through_neither_surface() {
    configureFromTheScreen();
    pinTheClockFromTheScreen();

    String everything = json.writeValueAsString(theTrailOf(""));

    assertThat(everything).doesNotContain(USER_ID).doesNotContain(PAGE_CODE);
    assertThat(bodyOf(theRowAt(theTrailOf("?origin=CONTROL_API"), CONFIG)))
        .containsEntry("accounts[0].configuration.userId", List.of(Redaction.REDACTED))
        .containsEntry("accounts[0].configuration.pageCode", List.of(Redaction.REDACTED));
    assertThat(bodyOf(theRowAt(theTrailOf("?origin=UI"), UI_ACCOUNT)))
        .containsEntry("accounts[0].configuration.userId", List.of(Redaction.REDACTED))
        .containsEntry("accounts[0].configuration.pageCode", List.of(Redaction.REDACTED));
  }

  @Test
  void a_tester_who_removes_an_account_from_the_screen_can_find_that_removal_afterwards() {
    assertThat(removeTheAccountFromTheScreen().status()).isEqualTo(302);

    Map<String, Object> removed = theRowAt(theTrailOf("?origin=UI"), UI_ACCOUNT_REMOVE);

    assertThat(removed).containsEntry("method", "POST");
    assertThat(bodyOf(removed)).containsEntry("removedAccount", List.of(MERCHANT));
  }

  @Test
  void a_reset_is_the_one_row_left_in_the_trail_it_emptied() {
    int held = theTrailOf("").size();
    assertThat(held).isPositive();

    reset();

    List<Map<String, Object>> afterwards = theTrailOf("");
    assertThat(afterwards).hasSize(1);
    assertThat(afterwards.get(0))
        .containsEntry("path", RESET)
        .containsEntry("origin", "CONTROL_API")
        .containsEntry("method", "POST");
    assertThat(bodyOf(afterwards.get(0)))
        .containsEntry("clearedAuditRecords", List.of(String.valueOf(held)));
  }

  @Test
  void one_queue_run_asked_for_from_the_screen_is_told_from_the_same_run_asked_for_by_a_harness() {
    deliverDueThroughTheControlApi();
    assertThat(deliverDueFromTheScreen().status()).isEqualTo(302);

    assertThat(pathsOf(theTrailOf("?origin=UI"))).contains(UI_DELIVER_DUE);
    assertThat(pathsOf(theTrailOf("?origin=CONTROL_API")))
        .contains("/__simulator/grow/webhooks/deliver-due")
        .doesNotContain(UI_DELIVER_DUE);
  }

  @Test
  void a_harness_reading_the_trail_the_way_it_always_did_still_sees_only_provider_traffic() {
    pinTheClockFromTheScreen();

    List<Map<String, Object>> traffic = theTrailOf("?origin=PROVIDER_API");

    assertThat(traffic).isEmpty();
    assertThat(theTrailOf("")).isNotEmpty();
  }

  private Answer pinTheClockFromTheScreen() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("fixedNow", PINNED_FROM_THE_SCREEN.toString());
    return submit(UI_CLOCK, form);
  }

  private Answer configureFromTheScreen() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("accountId", MERCHANT);
    form.put("field.userId", USER_ID);
    form.put("field.pageCode", PAGE_CODE);
    return submit(UI_ACCOUNT, form);
  }

  private Answer removeTheAccountFromTheScreen() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("accountId", MERCHANT);
    form.put("confirm", "yes");
    return submit(UI_ACCOUNT_REMOVE, form);
  }

  private Answer deliverDueFromTheScreen() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    return submit(UI_DELIVER_DUE, form);
  }

  private void deliverDueThroughTheControlApi() {
    http.post()
        .uri("/__simulator/grow/webhooks/deliver-due")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return new Answer(response.statusCode(), new String(response.body(), UTF_8));
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

  private List<Map<String, Object>> theTrailOf(String query) {
    List<Map<String, Object>> rows =
        http.get()
            .uri(AUDIT + "?testRunId=" + RUN.value() + query.replaceFirst("^\\?", "&"))
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return rows == null ? List.of() : rows;
  }

  private static Map<String, Object> theRowAt(List<Map<String, Object>> rows, String path) {
    for (Map<String, Object> row : rows) {
      if (path.equals(row.get("path"))) {
        return row;
      }
    }
    throw new AssertionError("no row of the trail names " + path + ", only " + pathsOf(rows));
  }

  private static List<String> pathsOf(List<Map<String, Object>> rows) {
    List<String> paths = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      paths.add(String.valueOf(row.get("path")));
    }
    return List.copyOf(paths);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> bodyOf(Map<String, Object> row) {
    return (Map<String, List<String>>) row.get("sanitizedBody");
  }

  private void configure() {
    http.put()
        .uri(CONFIG)
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
                        Map.of("userId", USER_ID, "pageCode", PAGE_CODE)))))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri(RESET)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private record Answer(int status, String body) {}
}
