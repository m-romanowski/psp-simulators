package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.ConfigurationScreen;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class ClockAdvanceIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("clock-advance-it");

  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");

  private static final String MERCHANT = "merchant-1";

  private static final String CONFIG = "/__simulator/grow/config";

  private static final String RESET = "/__simulator/grow/reset";

  private static final String AUDIT = "/__simulator/grow/audit";

  private static final String ADVANCE = "/__simulator/grow/clock/advance";

  private static final String UI_ADVANCE = "/ui/configuration/clock/advance";

  private static final String UI_CONFIGURATION = "/ui/configuration";

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void pinTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    reset();
    pinTo(PINNED.toString());
  }

  @AfterEach
  void emptyTheTestRun() {
    reset();
  }

  @Test
  void an_advance_answers_the_instant_the_run_left_and_the_instant_it_reads_as_now() {
    Answer advanced = advance("PT1H");

    assertThat(advanced.status()).isEqualTo(200);
    assertThat(advanced.body())
        .contains("\"advancedBy\":\"PT1H\"")
        .contains("\"movedFrom\":\"2026-09-07T12:00:00Z\"")
        .contains("\"now\":\"2026-09-07T13:00:00Z\"");
    assertThat(theScreen()).contains("2026-09-07T13:00:00Z");
  }

  @Test
  void two_advances_of_an_hour_leave_the_run_two_hours_past_the_instant_it_was_pinned_to() {
    advance("PT1H");
    Answer second = advance("PT1H");

    assertThat(second.body())
        .contains("\"movedFrom\":\"2026-09-07T13:00:00Z\"")
        .contains("\"now\":\"2026-09-07T14:00:00Z\"");
    assertThat(theScreen()).contains("2026-09-07T14:00:00Z");
  }

  @Test
  void an_advance_of_a_run_that_reads_the_host_clock_is_refused_and_names_the_property_that_pins() {
    releaseTheClock();

    Answer refused = advance("PT1H");

    assertThat(refused.status()).isEqualTo(409);
    assertThat(refused.body()).contains("clock.fixedNow");
  }

  @Test
  void a_duration_the_simulator_cannot_read_is_refused_by_naming_the_property_never_the_value() {
    Answer refused = advance("one hour");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.body()).contains("advanceBy").doesNotContain("one hour");
    assertThat(theScreen()).contains("2026-09-07T12:00:00Z");
  }

  @Test
  void an_advance_that_would_move_the_clock_backwards_is_refused_and_the_run_stays_where_it_was() {
    Answer refused = advance("PT-1H");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(theScreen()).contains("2026-09-07T12:00:00Z");
  }

  @Test
  void an_advance_asked_of_a_provider_this_simulator_does_not_serve_reaches_no_clock_at_all() {
    Answer refused = post("/__simulator/nosuchprovider/clock/advance", "{\"advanceBy\":\"PT1H\"}");

    assertThat(refused.status()).isEqualTo(404);
    assertThat(theScreen()).contains("2026-09-07T12:00:00Z");
  }

  @Test
  void an_applied_advance_is_told_from_a_pin_in_the_trail_by_its_path_and_by_what_it_recorded() {
    advance("PT2H");

    Map<String, List<String>> jump = bodyOf(theRowAt(theTrailOf("?origin=CONTROL_API"), ADVANCE));
    Map<String, List<String>> pin = bodyOf(theRowAt(theTrailOf("?origin=CONTROL_API"), CONFIG));

    assertThat(jump)
        .containsEntry("clock.advanceBy", List.of("PT2H"))
        .containsEntry("clock.movedFrom", List.of("2026-09-07T12:00:00Z"))
        .containsEntry("clock.now", List.of("2026-09-07T14:00:00Z"))
        .doesNotContainKey("clock.fixedNow");
    assertThat(pin)
        .containsEntry("clock.fixedNow", List.of("2026-09-07T12:00:00Z"))
        .doesNotContainKey("clock.advanceBy");
  }

  @Test
  void an_advance_this_simulator_refused_is_in_the_trail_under_the_status_it_refused_it_with() {
    advance("one hour");
    releaseTheClock();
    advance("PT1H");

    List<Map<String, Object>> refusals = theRowsAt(theTrailOf("?origin=CONTROL_API"), ADVANCE);

    assertThat(statusesOf(refusals)).contains(400, 409);
    for (Map<String, Object> refusal : refusals) {
      assertThat(bodyOf(refusal)).doesNotContainKey("clock.now");
    }
  }

  @Test
  void a_tester_who_advances_the_clock_from_a_screen_reads_the_run_ahead_on_the_page_that_moved() {
    Answer moved = advanceFromTheScreen("PT1H");

    assertThat(moved.status()).isEqualTo(302);
    assertThat(page(moved.location())).contains("2026-09-07T13:00:00Z");
  }

  @Test
  void a_jump_a_tester_made_from_a_screen_is_in_the_trail_as_a_manual_action_and_not_a_control() {
    advanceFromTheScreen("PT1H");

    Map<String, Object> jump = theRowAt(theTrailOf("?origin=UI"), UI_ADVANCE);

    assertThat(jump).containsEntry("method", "POST").containsEntry("status", 302);
    assertThat(bodyOf(jump))
        .containsEntry("clock.advanceBy", List.of("PT1H"))
        .containsEntry("clock.now", List.of("2026-09-07T13:00:00Z"));
    assertThat(pathsOf(theTrailOf("?origin=CONTROL_API"))).doesNotContain(UI_ADVANCE);
  }

  @Test
  void a_jump_a_tester_stated_as_a_number_and_a_unit_moves_the_clock_by_the_span_they_name() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("advanceBy", "90");
    form.put(ConfigurationScreen.THE_UNIT_AN_ADVANCE_IS_STATED_IN, "MINUTES");

    Answer advanced = submitTheForm(form);

    assertThat(advanced.status()).isEqualTo(302);
    assertThat(bodyOf(theRowAt(theTrailOf("?origin=UI"), UI_ADVANCE)))
        .as(
            "the two controls name one span between them, and the audit records the span the"
                + " clock was moved by rather than the number the box held")
        .containsEntry("clock.advanceBy", List.of("PT1H30M"))
        .containsEntry("clock.now", List.of("2026-09-07T13:30:00Z"));
  }

  @Test
  void the_clock_form_offers_the_units_an_advance_is_stated_in_rather_than_asking_for_iso() {
    String screen = theScreen();

    assertThat(screen)
        .contains("name=\"advanceBy\"")
        .contains("name=\"unitOf.advanceBy\"")
        .contains(">hours</option>")
        .doesNotContain("placeholder=\"PT1H\"");
  }

  @Test
  void a_screen_refuses_a_duration_it_cannot_read_on_the_page_and_records_the_refusal_as_one() {
    Answer refused = advanceFromTheScreen("one hour");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("advanceBy").doesNotContain("one hour");
    assertThat(theRowAt(theTrailOf("?origin=UI"), UI_ADVANCE)).containsEntry("status", 400);
    assertThat(theScreen()).contains("2026-09-07T12:00:00Z");
  }

  @Test
  void a_screen_refuses_to_advance_a_run_that_reads_the_host_clock_and_records_that_refusal_too() {
    releaseTheClock();

    Answer refused = advanceFromTheScreen("PT1H");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("clock.fixedNow");
    assertThat(theRowAt(theTrailOf("?origin=UI"), UI_ADVANCE)).containsEntry("status", 409);
  }

  private Answer advance(String advanceBy) {
    return post(
        ADVANCE, "{\"testRunId\":\"" + RUN.value() + "\",\"advanceBy\":\"" + advanceBy + "\"}");
  }

  private Answer advanceFromTheScreen(String advanceBy) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("advanceBy", advanceBy);
    return submitTheForm(form);
  }

  private Answer submitTheForm(Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + UI_ADVANCE))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return answerOf(response);
  }

  private Answer post(String path, String body) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    return answerOf(response);
  }

  private String theScreen() {
    return page(UI_CONFIGURATION + "?provider=grow&testRunId=" + RUN.value());
  }

  private String page(String location) {
    HttpResponse<byte[]> response =
        send(HttpRequest.newBuilder(at(location)).timeout(Duration.ofSeconds(20)).GET());
    return new String(response.body(), UTF_8);
  }

  private URI at(String location) {
    return URI.create(
        location.startsWith("http") ? location : "http://localhost:" + port + location);
  }

  private static Answer answerOf(HttpResponse<byte[]> response) {
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
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
    List<Map<String, Object>> found = theRowsAt(rows, path);
    if (found.isEmpty()) {
      throw new AssertionError("no row of the trail names " + path + ", only " + pathsOf(rows));
    }
    return found.get(found.size() - 1);
  }

  private static List<Map<String, Object>> theRowsAt(List<Map<String, Object>> rows, String path) {
    List<Map<String, Object>> found = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      if (path.equals(row.get("path"))) {
        found.add(row);
      }
    }
    return List.copyOf(found);
  }

  private static List<String> pathsOf(List<Map<String, Object>> rows) {
    List<String> paths = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      paths.add(String.valueOf(row.get("path")));
    }
    return List.copyOf(paths);
  }

  private static List<Integer> statusesOf(List<Map<String, Object>> rows) {
    List<Integer> statuses = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      statuses.add((Integer) row.get("status"));
    }
    return List.copyOf(statuses);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> bodyOf(Map<String, Object> row) {
    return (Map<String, List<String>>) row.get("sanitizedBody");
  }

  private void pinTo(String fixedNow) {
    http.put()
        .uri(CONFIG)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", fixedNow),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of(
                            "userId",
                            "clock-advance-user-must-never-be-read-back",
                            "pageCode",
                            "clock-advance-page-must-never-be-read-back")))))
        .retrieve()
        .toBodilessEntity();
  }

  private void releaseTheClock() {
    http.put()
        .uri(CONFIG)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "clock", Map.of()))
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

  private record Answer(int status, String body, String location) {}
}
