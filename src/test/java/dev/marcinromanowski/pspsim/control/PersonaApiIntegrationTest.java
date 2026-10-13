package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
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

class PersonaApiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "personas-it";

  private static final String OTHER_RUN = "personas-it-other";

  private static final String MERCHANT = "merchant-1";

  private static final String PERSONA = "stripe-customer-with-visa-success";

  private static final String ANOTHER_PERSONA = "stripe-customer-with-visa-that-is-declined";

  private static final String STRIPE_PERSONAS = "/__simulator/stripe/personas";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    for (String run : List.of(RUN, OTHER_RUN)) {
      configureTheStripeAccountAPersonaSeedsInto(run);
    }
  }

  @AfterEach
  void clearWhatThisTestSeeded() {
    for (String run : List.of(RUN, OTHER_RUN)) {
      exchange(HttpMethod.POST, "/__simulator/stripe/reset", Map.of("testRunId", run));
      exchange(
          HttpMethod.PUT,
          "/__simulator/stripe/config",
          Map.of(
              "testRunId", run, "clock", Map.of(), "accounts", List.of(), "webhooks", List.of()));
    }
  }

  @Test
  void the_catalogue_names_what_each_persona_creates_before_anything_is_created() {
    ResponseEntity<Map<String, Object>> response = exchange(HttpMethod.GET, STRIPE_PERSONAS, null);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("provider", "stripe");
    assertThat(personasIn(response))
        .anySatisfy(
            persona -> {
              assertThat(persona).containsEntry("name", PERSONA);
              assertThat(persona).containsEntry("intent", "a-saved-card-that-succeeds");
              assertThat(persona.get("presets"))
                  .isEqualTo(List.of("preauth-success-capture-success"));
              assertThat(instrumentsOf(persona))
                  .singleElement()
                  .satisfies(
                      instrument -> {
                        assertThat(instrument.get("providerNativeIds"))
                            .isEqualTo(List.of("paymentMethodId", "customerId"));
                        assertThat(String.valueOf(instrument.get("howThoseAreMinted")))
                            .contains("CreateCustomer.aCustomerId");
                      });
            });
    assertThat(list("/__simulator/stripe/scenarios?testRunId=" + RUN)).isEmpty();
  }

  @Test
  void a_preview_shows_the_rules_it_would_write_and_writes_none_of_them() {
    ResponseEntity<Map<String, Object>> response =
        exchange(HttpMethod.POST, STRIPE_PERSONAS + "/preview", seeding(RUN, PERSONA));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("persona", PERSONA);
    assertThat(response.getBody()).containsEntry("theRunAlreadyHoldsIt", false);
    assertThat(rulesOfThePreview(response)).isNotEmpty();
    assertThat(list("/__simulator/stripe/scenarios?testRunId=" + RUN)).isEmpty();
    assertThat(list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN)).isEmpty();
  }

  @Test
  void a_preview_foresees_the_refusal_the_published_payment_method_id_makes_for_the_second() {
    seed(RUN, PERSONA);

    ResponseEntity<Map<String, Object>> previewed =
        exchange(HttpMethod.POST, STRIPE_PERSONAS + "/preview", seeding(RUN, ANOTHER_PERSONA));

    assertThat(previewed.getStatusCode().value()).isEqualTo(200);
    assertThat(previewed.getBody()).containsEntry("theRunAlreadyHoldsIt", true);
    assertThat(previewed.getBody().get("theRefusalThisPlanCannotForesee")).isNull();

    ResponseEntity<Map<String, Object>> created = seed(RUN, ANOTHER_PERSONA);

    assertThat(created.getStatusCode().value()).isEqualTo(409);
    assertThat(String.valueOf(created.getBody().get("message")))
        .contains("providerIds.paymentMethodId pm_card_visa");
    assertThat(list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN)).hasSize(1);
  }

  @Test
  void a_preview_of_a_stripe_persona_states_no_refusal_it_cannot_foresee() {
    ResponseEntity<Map<String, Object>> previewed =
        exchange(HttpMethod.POST, STRIPE_PERSONAS + "/preview", seeding(RUN, PERSONA));

    assertThat(previewed.getBody()).containsEntry("theRunAlreadyHoldsIt", false);
    assertThat(previewed.getBody().get("theRefusalThisPlanCannotForesee")).isNull();
    assertThat(seed(RUN, PERSONA).getStatusCode().value()).isEqualTo(201);
  }

  @Test
  void seeding_a_persona_creates_the_rules_and_the_instrument_and_hands_back_both() {
    ResponseEntity<Map<String, Object>> response = seed(RUN, PERSONA);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody())
        .containsEntry("persona", PERSONA)
        .containsEntry("testRunId", RUN);
    List<Map<String, Object>> rules = listOf(response, "rules");
    List<Map<String, Object>> fixtures = listOf(response, "fixtures");
    assertThat(rules).isNotEmpty();
    assertThat(fixtures)
        .singleElement()
        .satisfies(
            fixture -> {
              assertThat(fixture).containsEntry("alias", PERSONA);
              assertThat(fixture).containsEntry("merchantId", MERCHANT);
              assertThat(fixture.get("defaultScenario")).isEqualTo(rules.get(0).get("scenarioId"));
              Map<?, ?> ids = (Map<?, ?>) fixture.get("providerObjects");
              assertThat(String.valueOf(ids.get("paymentMethodId"))).isEqualTo("pm_card_visa");
              assertThat(String.valueOf(ids.get("customerId"))).matches("cus_sim_[0-9a-f]{16}");
            });
  }

  @Test
  void every_object_a_persona_created_is_readable_and_deletable_on_its_own_route() {
    ResponseEntity<Map<String, Object>> seeded = seed(RUN, PERSONA);
    String scenarioId = String.valueOf(listOf(seeded, "rules").get(0).get("scenarioId"));
    String fixtureId = String.valueOf(listOf(seeded, "fixtures").get(0).get("fixtureId"));

    assertThat(
            exchange(
                    HttpMethod.GET,
                    "/__simulator/stripe/scenarios/" + scenarioId + "?testRunId=" + RUN,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(200);
    assertThat(
            exchange(
                    HttpMethod.DELETE,
                    "/__simulator/stripe/scenarios/" + scenarioId + "?testRunId=" + RUN,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(200);
    assertThat(
            exchange(
                    HttpMethod.DELETE,
                    "/__simulator/stripe/fixtures/payment-methods/"
                        + fixtureId
                        + "?testRunId="
                        + RUN,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(200);
    assertThat(list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN)).isEmpty();
  }

  @Test
  void a_persona_seeded_in_one_run_is_invisible_to_another_run() {
    seed(RUN, PERSONA);

    assertThat(list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN)).hasSize(1);
    assertThat(list("/__simulator/stripe/scenarios?testRunId=" + RUN)).isNotEmpty();
    assertThat(list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + OTHER_RUN))
        .isEmpty();
    assertThat(list("/__simulator/stripe/scenarios?testRunId=" + OTHER_RUN)).isEmpty();
  }

  @Test
  void two_runs_seeding_the_same_persona_do_not_see_each_others_objects() {
    seed(RUN, PERSONA);
    seed(OTHER_RUN, PERSONA);

    String mine =
        String.valueOf(
            list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN)
                .get(0)
                .get("fixtureId"));
    String theirs =
        String.valueOf(
            list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + OTHER_RUN)
                .get(0)
                .get("fixtureId"));

    assertThat(mine).isNotEqualTo(theirs);
    assertThat(
            exchange(
                    HttpMethod.DELETE,
                    "/__simulator/stripe/fixtures/payment-methods/" + theirs + "?testRunId=" + RUN,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  void a_name_this_provider_does_not_publish_is_answered_404_naming_the_ones_it_does() {
    ResponseEntity<Map<String, Object>> response = seed(RUN, "grow-user-with-tokenized-card");

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(response.getBody().get("message"))).contains(PERSONA);
    assertThat(list("/__simulator/stripe/scenarios?testRunId=" + RUN)).isEmpty();
  }

  @Test
  void a_run_that_already_holds_the_persona_is_answered_409_and_gains_no_second_rule() {
    seed(RUN, PERSONA);
    int rulesAfterTheFirst = list("/__simulator/stripe/scenarios?testRunId=" + RUN).size();

    ResponseEntity<Map<String, Object>> again = seed(RUN, PERSONA);

    assertThat(again.getStatusCode().value()).isEqualTo(409);
    assertThat(String.valueOf(again.getBody().get("message"))).contains(PERSONA);
    assertThat(list("/__simulator/stripe/scenarios?testRunId=" + RUN)).hasSize(rulesAfterTheFirst);
  }

  @Test
  void a_seeding_that_names_no_account_is_answered_400_and_writes_nothing() {
    ResponseEntity<Map<String, Object>> response =
        exchange(HttpMethod.POST, STRIPE_PERSONAS, Map.of("testRunId", RUN, "persona", PERSONA));

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(list("/__simulator/stripe/scenarios?testRunId=" + RUN)).isEmpty();
    assertThat(list("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN)).isEmpty();
  }

  @Test
  void a_provider_that_publishes_no_persona_answers_the_catalogue_with_every_intent_uncovered() {
    ResponseEntity<Map<String, Object>> response =
        exchange(HttpMethod.GET, "/__simulator/grow/personas", null);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(personasIn(response)).isEmpty();
    assertThat(uncoveredIn(response))
        .isNotEmpty()
        .allSatisfy(
            gap ->
                assertThat(String.valueOf(gap.get("instead")))
                    .contains("provider grow mints a cardToken only where a hosted process"));
  }

  @Test
  void a_persona_is_refused_for_a_provider_this_simulator_does_not_know() {
    assertThat(
            exchange(HttpMethod.GET, "/__simulator/nosuchprovider/personas", null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  void the_objects_a_persona_created_travel_in_the_run_bundle_this_build_already_exports() {
    seed(RUN, PERSONA);

    ResponseEntity<Map<String, Object>> exported =
        exchange(HttpMethod.GET, "/__simulator/stripe/run-bundle?testRunId=" + RUN, null);

    assertThat(exported.getStatusCode().value()).isEqualTo(200);
    List<?> carried = (List<?>) exported.getBody().get("fixtures");
    assertThat(carried).hasSize(1);
    assertThat(exported.getBody().get("scenarios")).isNotNull();
  }

  private void configureTheStripeAccountAPersonaSeedsInto(String testRunId) {
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
                        "sk_test_51Personas" + testRunId.replace("-", ""),
                        "captureWindow",
                        "P3D"))),
            "webhooks",
            List.of()));
  }

  private ResponseEntity<Map<String, Object>> seed(String testRunId, String persona) {
    return exchange(HttpMethod.POST, STRIPE_PERSONAS, seeding(testRunId, persona));
  }

  private static Map<String, Object> seeding(String testRunId, String persona) {
    return Map.of("testRunId", testRunId, "persona", persona, "merchantId", MERCHANT);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> personasIn(
      ResponseEntity<Map<String, Object>> response) {
    return (List<Map<String, Object>>) response.getBody().get("personas");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> uncoveredIn(
      ResponseEntity<Map<String, Object>> response) {
    return (List<Map<String, Object>>) response.getBody().get("uncovered");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> instrumentsOf(Map<String, Object> persona) {
    return (List<Map<String, Object>>) persona.get("instruments");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> listOf(
      ResponseEntity<Map<String, Object>> response, String key) {
    return (List<Map<String, Object>>) response.getBody().get(key);
  }

  @SuppressWarnings("unchecked")
  private static List<?> rulesOfThePreview(ResponseEntity<Map<String, Object>> response) {
    Map<String, Object> document = (Map<String, Object>) response.getBody().get("rules");
    return (List<?>) document.get("rules");
  }

  private List<Map<String, Object>> list(String uri) {
    return http.get()
        .uri(uri)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
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
