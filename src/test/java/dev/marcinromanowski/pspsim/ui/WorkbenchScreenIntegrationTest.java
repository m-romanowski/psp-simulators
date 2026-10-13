package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class WorkbenchScreenIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-workbench-it");

  private static final String PATH = "/ui";
  private static final String MERCHANT = "merchant-1";

  private static final String GROW_CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String GROW_BASE_URL = "http://localhost:8080/api/light/server/1.0";
  private static final String STRIPE_BASE_URL = "http://localhost:8080/v1";

  private static final String GROW_USER = "grow-user-must-not-be-read-off-a-monitor";
  private static final String GROW_PAGE = "grow-page-must-not-be-read-off-a-monitor";
  private static final String STRIPE_SECRET_KEY = "sk_test_must_not_be_read_off_a_monitor";
  private static final String STRIPE_SIGNING_SECRET = "whsec_must_not_be_read_off_a_monitor";
  private static final String STRIPE_CAPTURE_WINDOW = "PT72H";

  private static final String ALIASED_URL = "https://grow.screen.example/pay";

  private static final List<String> EVERY_DECLARED_SECRET =
      List.of(GROW_USER, GROW_PAGE, STRIPE_SECRET_KEY, STRIPE_SIGNING_SECRET);

  private static final String ACCOUNTS_PANEL = "Accounts this test run configures";
  private static final String ADDRESSES_PANEL = "Where a client application dials this run";
  private static final String OPERATIONS_PANEL = "Operations provider";
  private static final String OBJECTS_PANEL = "The newest objects of this test run";
  private static final String ABSENT_PANEL = "Not on this screen yet";

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
    configure("grow", Map.of("userId", GROW_USER, "pageCode", GROW_PAGE), List.of());
    configure(
        "stripe",
        Map.of(
            "secretKey", STRIPE_SECRET_KEY,
            "captureWindow", STRIPE_CAPTURE_WINDOW,
            "webhookSecret", STRIPE_SIGNING_SECRET),
        List.of());
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure("grow");
    unconfigure("stripe");
  }

  @Test
  void no_value_either_provider_declares_secret_reaches_either_providers_workbench() {
    for (String provider : List.of("grow", "stripe")) {
      String page = page(screen(provider));
      for (String secret : EVERY_DECLARED_SECRET) {
        assertThat(page).doesNotContain(secret);
      }
    }
  }

  @Test
  void a_field_a_provider_declares_secret_is_named_in_the_summary_and_carries_no_value_at_all() {
    String accounts = panel(page(screen("grow")), ACCOUNTS_PANEL);

    assertThat(cellsOf(rowOf(accounts, "userId"))).contains("userId");
    assertThat(rowOf(accounts, "userId"))
        .contains("named and never printed")
        .doesNotContain(GROW_USER)
        .doesNotContain(Redaction.REDACTED);
    assertThat(rowOf(accounts, "pageCode")).contains("named and never printed");
  }

  @Test
  void a_value_a_provider_declares_is_not_secret_is_printed_in_the_account_summary() {
    String accounts = panel(page(screen("stripe")), ACCOUNTS_PANEL);

    assertThat(cellsOf(rowOf(accounts, "captureWindow"))).contains(STRIPE_CAPTURE_WINDOW);
    assertThat(rowOf(accounts, "webhookSecret"))
        .contains("named and never printed")
        .doesNotContain(STRIPE_SIGNING_SECRET);
  }

  @Test
  void the_account_summary_names_the_account_this_test_run_configured() {
    assertThat(panel(page(screen("grow")), ACCOUNTS_PANEL)).contains(MERCHANT);
  }

  @Test
  void the_workbench_publishes_the_base_url_of_the_provider_it_shows_and_of_no_other() {
    String grow = panel(page(screen("grow")), ADDRESSES_PANEL);
    String stripe = panel(page(screen("stripe")), ADDRESSES_PANEL);

    assertThat(grow).contains(GROW_BASE_URL).contains(TestRunHeader.NAME);
    assertThat(stripe).contains(STRIPE_BASE_URL).doesNotContain(GROW_BASE_URL);
  }

  @Test
  void a_url_this_test_run_answers_on_is_listed_beside_the_route_it_reaches() {
    bindAnAliasFrom(ALIASED_URL, GROW_CREATE);

    String addresses = panel(page(screen("grow")), ADDRESSES_PANEL);

    assertThat(cellsOf(rowOf(addresses, ALIASED_URL))).contains(ALIASED_URL, GROW_CREATE);
  }

  @Test
  void a_test_run_that_bound_no_url_says_so_rather_than_showing_an_empty_alias_table() {
    assertThat(panel(page(screen("stripe")), ADDRESSES_PANEL))
        .contains("This test run answers on no URL of its own");
  }

  @Test
  void the_checklist_holds_every_operation_its_own_provider_exposes_and_no_other_providers() {
    String grow = panel(page(screen("grow")), OPERATIONS_PANEL);
    String stripe = panel(page(screen("stripe")), OPERATIONS_PANEL);

    assertThat(grow)
        .contains("createPaymentProcess")
        .contains("refundTransaction")
        .doesNotContain("/v1/payment_intents");
    assertThat(stripe).contains("POST /v1/payment_intents").doesNotContain("createPaymentProcess");
  }

  @Test
  void an_operation_no_rule_of_this_run_names_is_counted_on_the_checklist_as_named_by_none() {
    assertThat(cellsOf(rowOf(panel(page(screen("grow")), OPERATIONS_PANEL), "refundTransaction")))
        .contains("0");
  }

  @Test
  void an_operation_a_rule_of_this_run_names_is_counted_on_the_checklist_as_named_by_that_rule() {
    aRuleNaming("createPaymentProcess");

    String operations = panel(page(screen("grow")), OPERATIONS_PANEL);

    assertThat(cellsOf(rowOf(operations, "createPaymentProcess"))).contains("1");
    assertThat(cellsOf(rowOf(operations, "refundTransaction"))).contains("0");
  }

  @Test
  void the_checklist_says_a_tick_for_an_operation_already_exercised_is_absent_and_why() {
    assertThat(panel(page(screen("grow")), OPERATIONS_PANEL))
        .contains("the method and the path a call arrived on and never the operation it asked for");
  }

  @Test
  void an_object_this_test_run_holds_is_among_the_newest_objects_the_workbench_lists() {
    String processId = aPaymentProcess();

    String objects = panel(page(screen("grow")), OBJECTS_PANEL);

    assertThat(cellsOf(rowOf(objects, processId))).contains(processId);
    assertThat(objects).contains("PROVIDER_API");
  }

  @Test
  void a_test_run_holding_no_object_of_this_provider_says_so_rather_than_showing_an_empty_table() {
    assertThat(panel(page(screen("stripe")), OBJECTS_PANEL))
        .contains("holds no object of provider");
  }

  @Test
  void the_objects_panel_says_what_current_means_rather_than_reading_as_a_count_of_the_run() {
    assertThat(panel(page(screen("grow")), OBJECTS_PANEL))
        .contains("It is a summary and never a total");
  }

  @Test
  void an_object_waiting_for_a_payer_is_named_as_one_of_the_objects_this_panel_already_lists() {
    String objects = panel(page(screen("grow")), OBJECTS_PANEL);

    assertThat(objects).contains("is an ordinary object of this test run");
    assertThat(objects).contains("grow a payment process");
  }

  @Test
  void the_panel_of_what_is_absent_no_longer_claims_this_workbench_reads_no_list_of_objects() {
    String page = page(screen("grow"));

    assertThat(page).doesNotContain("does not read that list");
    assertThat(panel(page, ABSENT_PANEL))
        .doesNotContain("pending hosted sessions")
        .contains("pending captures and refunds")
        .contains("seeded payment methods")
        .contains("permissive-auth modes");
  }

  private String aPaymentProcess() {
    Map<String, Object> created = parse(post(GROW_CREATE, holdForm()));
    assertThat(created).containsEntry("status", 1);
    Map<?, ?> data = (Map<?, ?>) created.get("data");
    assertThat(data).isNotNull();
    return String.valueOf(data.get("processId"));
  }

  private Map<String, String> holdForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", GROW_PAGE);
    form.put("userId", GROW_USER);
    form.put("chargeType", "2");
    form.put("sum", "120.50");
    form.put("description", "A hold this workbench lists");
    form.put("pageField[fullName]", "Israel Israeli");
    form.put("pageField[phone]", "0505503144");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("notifyUrl", "https://merchant.example/callbacks");
    return form;
  }

  private void aRuleNaming(String operation) {
    http.post()
        .uri("/__simulator/grow/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "match",
                Map.of("operation", operation),
                "action",
                Map.of("type", "FIXED_SUCCESS")))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private void bindAnAliasFrom(String from, String to) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", GROW_USER, "pageCode", GROW_PAGE))),
                "webhooks",
                List.of(),
                "routeAliases",
                List.of(Map.of("from", from, "to", to))))
        .retrieve()
        .toBodilessEntity();
  }

  private static String screen(String provider) {
    return PATH + "?provider=" + provider + "&testRunId=" + RUN.value();
  }

  private static String panel(String page, String heading) {
    int from = page.indexOf(heading);
    assertThat(from).describedAs("panel headed %s is on this workbench", heading).isNotNegative();
    int until = page.indexOf("</section>", from);
    assertThat(until).describedAs("panel headed %s is closed", heading).isNotNegative();
    return page.substring(from, until);
  }

  private static String rowOf(String panel, String cell) {
    int named = panel.indexOf(">" + cell + "<");
    assertThat(named)
        .describedAs("a cell holding exactly %s is in this panel", cell)
        .isNotNegative();
    int from = panel.lastIndexOf("<tr", named);
    int until = panel.indexOf("</tr>", named);
    assertThat(from).isNotNegative();
    assertThat(until).isNotNegative();
    return panel.substring(from, until);
  }

  private static List<String> cellsOf(String row) {
    List<String> cells = new ArrayList<>();
    int at = row.indexOf('>');
    while (at >= 0) {
      int closing = row.indexOf('<', at);
      if (closing < 0) {
        break;
      }
      String text = row.substring(at + 1, closing).strip();
      if (!text.isEmpty()) {
        cells.add(text);
      }
      at = row.indexOf('>', closing);
    }
    return List.copyOf(cells);
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

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header(TestRunHeader.NAME, RUN.value())
            .timeout(Duration.ofSeconds(20))
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

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(
        new String(response.body(), UTF_8), new TypeReference<Map<String, Object>>() {});
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private void configure(
      String provider, Map<String, String> configuration, List<Map<String, String>> webhooks) {
    http.put()
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "accounts",
                List.of(Map.of("accountId", MERCHANT, "configuration", configuration)),
                "webhooks",
                webhooks))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure(String provider) {
    http.put()
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }
}
