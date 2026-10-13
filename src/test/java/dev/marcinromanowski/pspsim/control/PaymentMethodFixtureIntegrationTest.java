package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class PaymentMethodFixtureIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "fixtures-it";

  private static final String OTHER_RUN = "fixtures-it-other";

  private static final String MERCHANT = "merchant-1";

  private static final String PATH = "/fixtures/payment-methods";

  private static final String STRIPE = "/__simulator/stripe" + PATH;

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    for (String run : List.of(RUN, OTHER_RUN)) {
      configureTheStripeAccountTheseFixturesName(run);
    }
  }

  @AfterEach
  void clearWhatThisTestSeeded() {
    for (String run : List.of(RUN, OTHER_RUN)) {
      exchange(HttpMethod.POST, "/__simulator/stripe/reset", Map.of("testRunId", run));
      exchange(HttpMethod.POST, "/__simulator/grow/reset", Map.of("testRunId", run));
      exchange(
          HttpMethod.PUT,
          "/__simulator/stripe/config",
          Map.of(
              "testRunId", run, "clock", Map.of(), "accounts", List.of(), "webhooks", List.of()));
    }
  }

  private void configureTheStripeAccountTheseFixturesName(String testRunId) {
    exchange(
        HttpMethod.PUT,
        "/__simulator/stripe/config",
        Map.of(
            "testRunId",
            testRunId,
            "clock",
            Map.of(),
            "accounts",
            List.of(
                Map.of(
                    "accountId",
                    MERCHANT,
                    "configuration",
                    Map.of(
                        "secretKey",
                        "sk_test_51Fixtures" + testRunId.replace("-", ""),
                        "captureWindow",
                        "P3D"))),
            "webhooks",
            List.of()));
  }

  @Test
  void a_harness_seeds_a_card_and_is_answered_with_the_provider_native_ids_it_chose() {
    ResponseEntity<Map<String, Object>> response = seed(aVisaCard(RUN, "visa-success"));

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody())
        .containsEntry("provider", "stripe")
        .containsEntry("testRunId", RUN)
        .containsEntry("alias", "visa-success")
        .containsEntry("type", "card")
        .containsEntry("merchantId", MERCHANT)
        .containsEntry("ownerId", "customer-123")
        .containsEntry(
            "providerObjects",
            Map.of("paymentMethodId", "pm_sim_123", "customerId", "cus_sim_123"));
    assertThat(String.valueOf(response.getBody().get("fixtureId"))).startsWith("fx_");
    assertThat(response.getBody().get("card"))
        .isEqualTo(
            Map.of(
                "brand",
                "visa",
                "last4",
                "1111",
                "expMonth",
                12,
                "expYear",
                2030,
                "funding",
                "credit"));
  }

  @Test
  void a_card_seeded_in_one_test_run_is_invisible_to_another() {
    seed(aVisaCard(RUN, "visa-success"));

    assertThat(list(RUN)).hasSize(1);
    assertThat(list(OTHER_RUN)).isEmpty();
  }

  @Test
  void seeding_the_same_fixture_twice_is_refused_as_a_conflicting_fixture_id() {
    seed(aVisaCard(RUN, "visa-success"));

    Map<String, Object> again = aVisaCard(RUN, "visa-success");
    again.put("providerIds", Map.of("paymentMethodId", "pm_sim_999"));

    assertThat(seed(again).getStatusCode().value()).isEqualTo(409);
    assertThat(list(RUN)).hasSize(1);
  }

  @Test
  void two_fixtures_of_one_run_cannot_claim_one_provider_native_payment_method_id() {
    seed(aVisaCard(RUN, "visa-success"));

    Map<String, Object> again = aVisaCard(RUN, "visa-decline");

    assertThat(seed(again).getStatusCode().value()).isEqualTo(409);
  }

  @Test
  void a_fixture_this_simulator_cannot_read_is_refused_as_malformed() {
    Map<String, Object> malformed = aVisaCard(RUN, "visa-success");
    malformed.put("type", "credit-card");

    assertThat(seed(malformed).getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void a_provider_native_id_this_provider_reads_nothing_of_is_refused_as_inapplicable() {
    Map<String, Object> unusable = aVisaCard(RUN, "visa-success");
    unusable.put("providerIds", Map.of("paymentMethodId", "pm_sim_123", "token", "tok_sim_123"));

    ResponseEntity<Map<String, Object>> response = seed(unusable);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("properties", List.of("providerIds.token"));
    assertThat(String.valueOf(response.getBody().get("message"))).contains("no Token object");
  }

  @Test
  void a_fixture_naming_a_scenario_this_test_run_never_wrote_is_refused_as_inapplicable() {
    Map<String, Object> dangling = aVisaCard(RUN, "visa-success");
    dangling.put("defaultScenario", "scn_nothing");

    ResponseEntity<Map<String, Object>> response = seed(dangling);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).containsEntry("properties", List.of("defaultScenario"));
  }

  @Test
  void a_fixture_naming_a_rule_this_run_holds_is_seeded_and_reports_the_rule_it_names() {
    String scenarioId = aRuleNarrowedOnThePaymentMethod();
    Map<String, Object> naming = aVisaCard(RUN, "visa-decline");
    naming.put("defaultScenario", scenarioId);

    ResponseEntity<Map<String, Object>> response = seed(naming);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).containsEntry("defaultScenario", scenarioId);
  }

  @Test
  void a_provider_that_reads_no_provider_native_id_says_so_rather_than_seeding_a_dead_fixture() {
    Map<String, Object> forGrow = aVisaCard(RUN, "visa-success");
    forGrow.put("providerIds", Map.of("token", "tok_sim_123"));

    ResponseEntity<Map<String, Object>> response =
        exchange(HttpMethod.POST, "/__simulator/grow" + PATH, forGrow);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(response.getBody().get("message")))
        .contains("createTransactionWithToken");
  }

  @Test
  void seeding_a_card_for_a_provider_this_simulator_does_not_serve_is_not_found() {
    ResponseEntity<Map<String, Object>> response =
        exchange(
            HttpMethod.POST, "/__simulator/unknown-psp" + PATH, aVisaCard(RUN, "visa-success"));

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void a_tester_removes_a_seeded_card_and_the_list_no_longer_holds_it() {
    String fixtureId = fixtureIdOf(seed(aVisaCard(RUN, "visa-success")));

    ResponseEntity<Map<String, Object>> removed =
        exchange(HttpMethod.DELETE, STRIPE + "/" + fixtureId + "?testRunId=" + RUN, null);

    assertThat(removed.getStatusCode().value()).isEqualTo(200);
    assertThat(removed.getBody()).containsEntry("removedFixture", fixtureId);
    assertThat(list(RUN)).isEmpty();
  }

  @Test
  void removing_a_fixture_no_test_run_seeded_is_not_found() {
    ResponseEntity<Map<String, Object>> response =
        exchange(HttpMethod.DELETE, STRIPE + "/fx_nothing?testRunId=" + RUN, null);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void removing_a_fixture_of_another_test_run_leaves_it_where_it_is() {
    String fixtureId = fixtureIdOf(seed(aVisaCard(RUN, "visa-success")));

    ResponseEntity<Map<String, Object>> response =
        exchange(HttpMethod.DELETE, STRIPE + "/" + fixtureId + "?testRunId=" + OTHER_RUN, null);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(list(RUN)).hasSize(1);
  }

  @Test
  void resetting_a_test_run_clears_the_payment_methods_it_seeded() {
    seed(aVisaCard(RUN, "visa-success"));

    exchange(HttpMethod.POST, "/__simulator/stripe/reset", Map.of("testRunId", RUN));

    assertThat(list(RUN)).isEmpty();
  }

  @Test
  void seeding_a_card_leaves_an_action_row_that_names_the_fixture_and_no_card_number() {
    seed(aVisaCard(RUN, "visa-success"));

    assertThat(trailOf(RUN))
        .filteredOn(row -> String.valueOf(row.get("path")).endsWith(PATH))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).containsEntry("origin", "CONTROL_API").containsEntry("status", 201);
              assertThat(String.valueOf(row.get("sanitizedBody")))
                  .contains("visa-success")
                  .contains("1111")
                  .doesNotContain("pm_sim_123")
                  .doesNotContain("cus_sim_123");
            });
  }

  private String aRuleNarrowedOnThePaymentMethod() {
    ResponseEntity<Map<String, Object>> written =
        exchange(
            HttpMethod.POST,
            "/__simulator/stripe/scenarios",
            Map.of(
                "testRunId",
                RUN,
                "match",
                Map.of("paymentMethodId", "pm_sim_123"),
                "action",
                Map.of("type", "TERMINAL_FAILURE", "providerCode", "generic_decline")));
    assertThat(written.getStatusCode().value()).isEqualTo(201);
    return String.valueOf(written.getBody().get("scenarioId"));
  }

  private static String fixtureIdOf(ResponseEntity<Map<String, Object>> seeded) {
    return String.valueOf(seeded.getBody().get("fixtureId"));
  }

  private List<Map<String, Object>> list(String testRunId) {
    return http.get()
        .uri(STRIPE + "?testRunId=" + testRunId)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        .getBody();
  }

  private List<Map<String, Object>> trailOf(String testRunId) {
    return http.get()
        .uri("/__simulator/stripe/audit?testRunId=" + testRunId + "&origin=CONTROL_API")
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
        .getBody();
  }

  private ResponseEntity<Map<String, Object>> seed(Map<String, Object> body) {
    return exchange(HttpMethod.POST, STRIPE, body);
  }

  private static Map<String, Object> aVisaCard(String testRunId, String alias) {
    Map<String, Object> body = new HashMap<>();
    body.put("testRunId", testRunId);
    body.put("merchantId", MERCHANT);
    body.put("ownerId", "customer-123");
    body.put("type", "card");
    body.put("alias", alias);
    body.put(
        "card",
        Map.of(
            "brand",
            "visa",
            "last4",
            "1111",
            "expMonth",
            12,
            "expYear",
            2030,
            "funding",
            "credit"));
    body.put("providerIds", Map.of("paymentMethodId", "pm_sim_123", "customerId", "cus_sim_123"));
    return body;
  }

  private ResponseEntity<Map<String, Object>> exchange(
      HttpMethod method, String uri, @Nullable Map<String, Object> body) {
    RestClient.RequestBodySpec sending = http.method(method).uri(uri);
    return (body == null ? sending : sending.contentType(MediaType.APPLICATION_JSON).body(body))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }
}
