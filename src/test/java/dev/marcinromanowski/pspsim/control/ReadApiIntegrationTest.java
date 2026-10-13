package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.ReadControl;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class ReadApiIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "read-api";
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "read-api-client-id";
  private static final String CLIENT_SECRET = "read-api-client-secret";

  private static final String ONE_PAYMENT = "GET /v1/acquiring/payments/{paymentId}";
  private static final String THE_LISTING = "GET /v1/acquiring/payments";
  private static final String THE_PAYMENT_IT_IS_ASKED_ABOUT = "paymentId";
  private static final String THE_PAYMENT_AUTRA_ANSWERS_IT_UNDER = "paymentID";
  private static final String THE_STEPS_IT_HAS_BEEN_THROUGH = "events";
  private static final String WHERE_THE_VALUE_GOES = "<value>";
  private static final String THE_CARD_AUTRA_PRINTS = "published-4111";
  private static final String A_PAYMENT_NOBODY_TOOK = "3f1b5d6e-0000-4000-8000-000000000000";

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheEstablishment() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
  }

  @Test
  void a_suite_discovers_both_questions_autra_publishes_with_the_fields_and_sentences_of_each() {
    Map<String, Object> published = published(Map.of("testRunId", RUN));

    assertThat(published).containsEntry("testRunId", RUN).containsEntry("provider", "autra");
    assertThat(published.get("whatThisProviderPublishesInstead")).isNull();
    assertThat(readNamesIn(published)).containsExactly(ONE_PAYMENT, THE_LISTING);
    Map<String, Object> onePayment = readIn(published, ONE_PAYMENT);
    assertThat(String.valueOf(onePayment.get("label"))).isNotBlank();
    assertThat(String.valueOf(onePayment.get("theQuestionItAnswers")))
        .contains("whose own call never answered");
    assertThat(onePayment)
        .as("autra answers the whole payment or refuses the call, and says so here")
        .containsEntry("itNeverAnswersNothingAtAll", true);
    Map<String, Object> theField = theOnlyFieldOf(onePayment);
    assertThat(theField).containsEntry("key", THE_PAYMENT_IT_IS_ASKED_ABOUT);
    assertThat(theField).containsEntry("required", true);
    assertThat(String.valueOf(theField.get("whatItIsFor"))).contains("uuid");
    assertThat(theField)
        .containsEntry("theQueryParameterItIsSentIn", "field=paymentId=" + WHERE_THE_VALUE_GOES)
        .containsEntry("whereItsValuesComeFrom", "THE_VALUES_THIS_TEST_RUN_HAS_ALREADY_PRODUCED");
    assertThat(fieldKeysOf(readIn(published, THE_LISTING)))
        .containsExactly("from", "to", "status", "type", "page", "limit");
  }

  @Test
  void a_provider_that_publishes_no_read_answers_two_hundred_with_what_it_has_instead() {
    ResponseEntity<Map<String, Object>> published =
        get("/__simulator/stripe/reads?testRunId=" + RUN);

    assertThat(published.getStatusCode().value()).isEqualTo(200);
    assertThat(published.getBody())
        .containsEntry(
            "whatThisProviderPublishesInstead",
            SimulatorProvider.THIS_MODULE_PUBLISHES_NO_READ_A_TESTER_ASKS);
    assertThat(readNamesIn(published.getBody())).isEmpty();
  }

  @Test
  void
      an_establishment_that_took_nothing_is_answered_no_row_and_what_the_provider_says_that_means() {
    ResponseEntity<Map<String, Object>> answered = answer(THE_LISTING, List.of());

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    Map<String, Object> body = answered.getBody();
    assertThat(body)
        .containsEntry("readNamed", THE_LISTING)
        .containsEntry("accountId", ACCOUNT)
        .containsEntry("testRunId", RUN);
    assertThat(rowsIn(body)).isEmpty();
    assertThat(String.valueOf(body.get("whatItMeansThatItAnsweredNothingAtAll")))
        .contains("empty items list");
    assertThat(factsIn(body, "whatItAnsweredAboutTheQuery"))
        .as("autra answers the window and the page it applied beside the payments themselves")
        .containsKeys("page", "limit", "hasMore", "from", "to");
  }

  @Test
  void a_payment_this_run_took_is_answered_with_the_members_and_the_elements_autra_answered() {
    String paymentId = aSale();

    ResponseEntity<Map<String, Object>> answered =
        answer(ONE_PAYMENT, List.of(THE_PAYMENT_IT_IS_ASKED_ABOUT + "=" + paymentId));

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    Map<String, Object> body = answered.getBody();
    assertThat(body.get("whatItMeansThatItAnsweredNothingAtAll")).isNull();
    assertThat(rowsIn(body)).hasSize(1);
    Map<String, Object> row = mapIn(rowsIn(body).getFirst());
    assertThat(row).containsEntry("providerObjectId", paymentId);
    Map<String, Object> facts = factsOf(row);
    assertThat(facts)
        .containsKey(THE_PAYMENT_AUTRA_ANSWERS_IT_UNDER)
        .containsKey(THE_STEPS_IT_HAS_BEEN_THROUGH);
    assertThat(mapIn(facts.get(THE_PAYMENT_AUTRA_ANSWERS_IT_UNDER)))
        .containsEntry("theValueItAnswered", paymentId)
        .containsEntry("itAnsweredNoValueAtAll", false);
    Map<String, Object> timeline = mapIn(facts.get(THE_STEPS_IT_HAS_BEEN_THROUGH));
    assertThat(timeline.get("theValueItAnswered")).isNull();
    assertThat(timeline.get("theMembersUnderIt")).isNull();
    List<?> steps = listIn(timeline.get("theElementsUnderIt"));
    assertThat(steps).isNotEmpty();
    assertThat(membersOf(mapIn(steps.getFirst())))
        .as("a step autra answers under the events of a payment keeps the members it carried")
        .containsKey("status");
  }

  @Test
  void the_query_parameter_the_discovery_route_published_is_the_one_a_value_is_sent_in() {
    String paymentId = aSale();
    String published =
        String.valueOf(
            theOnlyFieldOf(readIn(published(Map.of("testRunId", RUN)), ONE_PAYMENT))
                .get("theQueryParameterItIsSentIn"));

    ResponseEntity<Map<String, Object>> answered =
        get(
            "/__simulator/autra/reads/answer?testRunId="
                + RUN
                + "&accountId="
                + ACCOUNT
                + "&readNamed="
                + URLEncoder.encode(ONE_PAYMENT, UTF_8)
                + "&"
                + published.replace(WHERE_THE_VALUE_GOES, paymentId));

    assertThat(published).isEqualTo("field=paymentId=" + WHERE_THE_VALUE_GOES);
    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    assertThat(mapIn(rowsIn(answered.getBody()).getFirst()))
        .containsEntry("providerObjectId", paymentId);
  }

  @Test
  void the_payments_this_run_has_already_taken_are_offered_for_the_field_that_asks_for_one() {
    String paymentId = aSale();

    Map<String, Object> published =
        published(Map.of("testRunId", RUN, "accountId", ACCOUNT, "readNamed", ONE_PAYMENT));

    assertThat(published).containsEntry("accountId", ACCOUNT);
    assertThat(listIn(theOnlyFieldOf(readIn(published, ONE_PAYMENT)).get("theValuesItOffers")))
        .extracting(offered -> mapIn(offered).get("value"))
        .contains(paymentId);
  }

  @Test
  void an_identifier_this_establishment_holds_no_payment_under_is_the_refusal_autra_publishes() {
    ResponseEntity<Map<String, Object>> answered =
        answer(ONE_PAYMENT, List.of(THE_PAYMENT_IT_IS_ASKED_ABOUT + "=not-a-uuid"));

    assertThat(answered.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(answered.getBody().get("message"))).contains("INVALID_PAYMENT_ID");
  }

  @Test
  void a_read_asked_without_the_field_it_needs_is_four_hundred_in_that_fields_own_words() {
    ResponseEntity<Map<String, Object>> answered = answer(ONE_PAYMENT, List.of());

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(answered.getBody().get("message")))
        .contains("the payment you want read back");
  }

  @Test
  void a_value_sent_without_the_key_it_belongs_to_is_four_hundred_in_the_control_apis_own_words() {
    ResponseEntity<Map<String, Object>> answered = answer(ONE_PAYMENT, List.of("paymentId"));

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(answered.getBody().get("message")))
        .isEqualTo(
            TheReadsAProviderPublishes.A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT
                + "paymentId");
  }

  @Test
  void a_read_asked_on_no_account_at_all_is_four_hundred_because_nothing_could_sign_the_call() {
    ResponseEntity<Map<String, Object>> answered =
        get(
            "/__simulator/autra/reads/answer?testRunId="
                + RUN
                + "&readNamed="
                + URLEncoder.encode(THE_LISTING, UTF_8));

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(answered.getBody()).containsEntry("message", ReadControl.AN_ACCOUNT_IS_REQUIRED);
  }

  @Test
  void an_account_this_test_run_does_not_configure_is_four_hundred_and_four() {
    ResponseEntity<Map<String, Object>> answered =
        get(
            "/__simulator/autra/reads/answer?testRunId="
                + RUN
                + "&accountId=merchant-nobody-configured&readNamed="
                + URLEncoder.encode(THE_LISTING, UTF_8));

    assertThat(answered.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(answered.getBody().get("message"))).contains("accountId");
  }

  @Test
  void a_question_autra_does_not_publish_is_four_hundred_naming_the_ones_it_does() {
    ResponseEntity<Map<String, Object>> answered = answer("GET /v1/acquiring/invented", List.of());

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(answered.getBody().get("message")))
        .contains(ONE_PAYMENT)
        .contains(THE_LISTING);
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_four_hundred_and_four_on_both_routes() {
    ResponseEntity<Map<String, Object>> published = get("/__simulator/invented/reads");
    ResponseEntity<Map<String, Object>> answered =
        get("/__simulator/invented/reads/answer?accountId=" + ACCOUNT);

    assertThat(published.getStatusCode().value()).isEqualTo(404);
    assertThat(answered.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(published.getBody().get("message"))).contains("unknown provider");
  }

  @Test
  void a_read_of_autras_leaves_every_object_this_test_run_holds_exactly_as_it_was() {
    String paymentId = aSale();
    List<?> before = objects();

    answer(ONE_PAYMENT, List.of(THE_PAYMENT_IT_IS_ASKED_ABOUT + "=" + paymentId));
    answer(THE_LISTING, List.of());

    assertThat(objects())
        .as(
            "autra names neither read among the operations a scenario rule may be declared on, so"
                + " no rule of this run can decide one and no story of this run moves when one is"
                + " asked; this list is what a read leaves untouched on every provider and not"
                + " the whole of what a read leaves untouched on any")
        .isEqualTo(before);
  }

  @Test
  void a_read_a_suite_asked_is_found_on_the_trail_of_the_run_it_was_asked_in() {
    String paymentId = aSale();

    answer(ONE_PAYMENT, List.of(THE_PAYMENT_IT_IS_ASKED_ABOUT + "=" + paymentId));

    Map<String, Object> asked = theRowAt("/__simulator/autra/reads/answer");
    assertThat(asked)
        .as(
            "a read reaches the provider's own operation through this route, so a tester asking"
                + " afterwards what their suite did is told that it asked, what it asked with and"
                + " what came back")
        .containsEntry("method", "GET")
        .containsEntry("origin", "CONTROL_API")
        .containsEntry("status", 200)
        .containsEntry("providerObjectId", paymentId);
    assertThat(bodyOf(asked))
        .containsEntry("readNamed", List.of(ONE_PAYMENT))
        .containsEntry("accountId", List.of(ACCOUNT))
        .containsEntry(THE_PAYMENT_IT_IS_ASKED_ABOUT, List.of(paymentId))
        .containsEntry("rowsAnswered", List.of("1"));
  }

  @Test
  void a_read_this_provider_refused_is_found_on_the_trail_in_the_words_it_was_refused_in() {
    answer(ONE_PAYMENT, List.of(THE_PAYMENT_IT_IS_ASKED_ABOUT + "=" + A_PAYMENT_NOBODY_TOOK));

    Map<String, Object> refused = theRowAt("/__simulator/autra/reads/answer");

    assertThat(refused).containsEntry("status", 422);
    assertThat(bodyOf(refused).get("refusedBecause"))
        .as(
            "the row carries the words autra refused in and not merely the fact that it refused,"
                + " because a tester reading the trail afterwards is reading for the refusal")
        .singleElement(InstanceOfAssertFactories.STRING)
        .contains("PAYMENT_NOT_FOUND")
        .contains("inexistente, de outro tenant ou de outro estabelecimento");
  }

  @Test
  void discovering_what_a_provider_publishes_leaves_no_row_on_the_trail_at_all() {
    published(Map.of("testRunId", RUN, "accountId", ACCOUNT));

    assertThat(pathsOnTheTrail())
        .as("reading what a provider publishes moves nothing, so the trail records no action")
        .doesNotContain("/__simulator/autra/reads", "/__simulator/autra/reads/answer");
  }

  private Map<String, Object> theRowAt(String path) {
    for (Map<String, Object> row : theTrail()) {
      if (path.equals(row.get("path"))) {
        return row;
      }
    }
    throw new AssertionError("no row of the trail names " + path + ", only " + pathsOnTheTrail());
  }

  private List<String> pathsOnTheTrail() {
    List<String> paths = new ArrayList<>();
    for (Map<String, Object> row : theTrail()) {
      paths.add(String.valueOf(row.get("path")));
    }
    return List.copyOf(paths);
  }

  private List<Map<String, Object>> theTrail() {
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/autra/audit?testRunId=" + RUN + "&origin=CONTROL_API")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return rows == null ? List.of() : rows;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> bodyOf(Map<String, Object> row) {
    return (Map<String, List<String>>) row.get("sanitizedBody");
  }

  private Map<String, Object> published(Map<String, String> query) {
    List<String> asked = new ArrayList<>();
    for (Map.Entry<String, String> parameter : query.entrySet()) {
      asked.add(parameter.getKey() + "=" + URLEncoder.encode(parameter.getValue(), UTF_8));
    }
    return get("/__simulator/autra/reads?" + String.join("&", asked)).getBody();
  }

  private ResponseEntity<Map<String, Object>> answer(String readNamed, List<String> fields) {
    StringBuilder asked =
        new StringBuilder("/__simulator/autra/reads/answer?testRunId=")
            .append(RUN)
            .append("&accountId=")
            .append(ACCOUNT)
            .append("&readNamed=")
            .append(URLEncoder.encode(readNamed, UTF_8));
    for (String field : fields) {
      asked.append("&field=").append(URLEncoder.encode(field, UTF_8));
    }
    return get(asked.toString());
  }

  private ResponseEntity<Map<String, Object>> get(String path) {
    return http.get()
        .uri(URI.create("http://localhost:" + port + path))
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private List<?> objects() {
    return http.get()
        .uri("/__simulator/autra/objects?testRunId=" + RUN)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private static List<String> readNamesIn(Map<String, Object> published) {
    List<String> named = new ArrayList<>();
    for (Object read : listIn(published.get("reads"))) {
      named.add(String.valueOf(mapIn(read).get("readNamed")));
    }
    return List.copyOf(named);
  }

  private static Map<String, Object> readIn(Map<String, Object> published, String readNamed) {
    for (Object read : listIn(published.get("reads"))) {
      if (readNamed.equals(mapIn(read).get("readNamed"))) {
        return mapIn(read);
      }
    }
    throw new IllegalStateException("no read named " + readNamed + " in " + published);
  }

  private static List<String> fieldKeysOf(Map<String, Object> read) {
    List<String> keys = new ArrayList<>();
    for (Object field : listIn(read.get("fields"))) {
      keys.add(String.valueOf(mapIn(field).get("key")));
    }
    return List.copyOf(keys);
  }

  private static Map<String, Object> theOnlyFieldOf(Map<String, Object> read) {
    List<?> fields = listIn(read.get("fields"));
    assertThat(fields).hasSize(1);
    return mapIn(fields.getFirst());
  }

  private static List<?> rowsIn(Map<String, Object> answered) {
    return listIn(answered.get("rows"));
  }

  private static Map<String, Object> factsOf(Map<String, Object> row) {
    return factsUnder(listIn(row.get("facts")));
  }

  private static Map<String, Object> factsIn(Map<String, Object> answered, String under) {
    return factsUnder(listIn(answered.get(under)));
  }

  private static Map<String, Object> membersOf(Map<String, Object> value) {
    return factsUnder(listIn(value.get("theMembersUnderIt")));
  }

  private static Map<String, Object> factsUnder(List<?> facts) {
    Map<String, Object> named = new LinkedHashMap<>();
    for (Object fact : facts) {
      named.put(String.valueOf(mapIn(fact).get("asTheProviderNamesIt")), mapIn(fact).get("value"));
    }
    return named;
  }

  private static Map<String, Object> mapIn(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> read = new LinkedHashMap<>();
      map.forEach((named, held) -> read.put(String.valueOf(named), held));
      return read;
    }
    throw new IllegalStateException("expected a JSON object and read " + value);
  }

  private static List<?> listIn(Object value) {
    if (value instanceof List<?> list) {
      return list;
    }
    throw new IllegalStateException("expected a JSON array and read " + value);
  }

  private String aSale() {
    String token = anAccessToken();
    String sessionId =
        valueIn(
            post("/v1/acquiring/card-sessions", token, "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            "hostedCardSessionId");
    http.post()
        .uri("/__simulator/autra/hosted/" + sessionId + "/complete")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN, "outcome", "PAID", "card", THE_CARD_AUTRA_PRINTS))
        .retrieve()
        .toBodilessEntity();
    Map<String, Object> session =
        get("/__simulator/autra/state/" + sessionId + "?testRunId=" + RUN).getBody();
    Map<String, Object> related = mapIn(session.get("relatedObjects"));
    String transactionalToken =
        valueIn(
            post(
                    "/v1/acquiring/payments/initialize",
                    token,
                    "{\"documentId\":\"" + DOCUMENT + "\"}")
                .body(),
            "transactionalToken");
    String charged =
        post(
                "/v1/acquiring/payments",
                token,
                "{\"transactionalToken\":\""
                    + transactionalToken
                    + "\",\"documentId\":\""
                    + DOCUMENT
                    + "\",\"transactionType\":\"CREDIT\",\"amount\":150.00,"
                    + "\"currency\":\"BRL\",\"installments\":1,\"tokenData\":{\"slugToken\":\""
                    + related.get("slugToken")
                    + "\",\"slugStoredCard\":\""
                    + related.get("slugStoredCard")
                    + "\"}}")
            .body();
    String paymentId = valueIn(charged, THE_PAYMENT_AUTRA_ANSWERS_IT_UNDER);
    assertThat(paymentId)
        .as("the sale this read is asked about was taken: %s", charged)
        .isNotEmpty();
    return paymentId;
  }

  private String anAccessToken() {
    HttpResponse<String> minted =
        post(
            "/v1/oauth/token?grant_type=client_credentials",
            null,
            "",
            "Basic "
                + Base64.getEncoder()
                    .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(UTF_8)));
    assertThat(minted.statusCode()).isEqualTo(200);
    return valueIn(minted.body(), "accessToken");
  }

  private void configure() {
    http.put()
        .uri("/__simulator/autra/config")
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
                        Map.of(
                            "clientId",
                            CLIENT_ID,
                            "clientSecret",
                            CLIENT_SECRET,
                            "documentId",
                            DOCUMENT)))))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/autra/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN))
        .retrieve()
        .toBodilessEntity();
  }

  private HttpResponse<String> post(String path, String bearer, String body) {
    return post(path, bearer, body, null);
  }

  private HttpResponse<String> post(String path, String bearer, String body, String authorization) {
    try {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body));
      if (bearer != null) {
        request = request.header("Authorization", "Bearer " + bearer);
      }
      if (authorization != null) {
        request = request.header("Authorization", authorization);
      }
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String valueIn(String json, String field) {
    String opening = "\"" + field + "\":\"";
    int at = json.indexOf(opening);
    if (at < 0) {
      return "";
    }
    return json.substring(at + opening.length(), json.indexOf('"', at + opening.length()));
  }
}
