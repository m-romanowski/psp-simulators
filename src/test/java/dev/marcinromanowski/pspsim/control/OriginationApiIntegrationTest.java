package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class OriginationApiIntegrationTest extends PostgresBackedTest {

  private static final String PROVIDER = "grow";

  private static final String RUN = "origination-api";

  private static final String ACCOUNT = "merchant-1";

  private static final String THE_CALLBACK_THIS_RUN_CONFIGURES = "https://merchant.example/hook";

  private static final String A_HOLD = "createPaymentProcess:chargeType=2";

  private static final String A_SAVED_CARD_CHARGE = "createTransactionWithToken";

  private static final String THE_PAYERS_NAME = "pageField[fullName]";

  private static final String THE_AMOUNT = "sum";

  private static final String THE_CALLBACK = "notifyUrl";

  private static final String THE_CODE_GROW_REFUSES_AN_AMOUNT_WITH = "707";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void configureTheBusiness() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure();
  }

  @Test
  void a_suite_discovers_every_way_grow_declares_with_the_fields_and_the_sentences_of_each() {
    Map<String, Object> declared = theWaysDeclared();

    assertThat(declared).containsEntry("testRunId", RUN).containsEntry("provider", PROVIDER);
    assertThat(declared).containsEntry("theAccountsThisRunConfigures", List.of(ACCOUNT));
    assertThat(theWayNamesIn(declared)).contains(A_HOLD, A_SAVED_CARD_CHARGE);
    Map<String, Object> hold = wayIn(declared, A_HOLD);
    assertThat(String.valueOf(hold.get("label"))).isNotBlank();
    assertThat(String.valueOf(hold.get("whatItLeavesBehind")))
        .contains("waiting for the payer on grow's hosted page");
    assertThat(String.valueOf(hold.get("whatThePayerDoesNext")))
        .contains("This simulator serves the payer's page itself");
    assertThat(String.valueOf(hold.get("thePayerPageThisSimulatorServes"))).startsWith("/");
    Map<String, Object> theName = fieldIn(hold, THE_PAYERS_NAME);
    assertThat(theName).containsEntry("required", true);
    assertThat(String.valueOf(theName.get("whatItIsFor"))).contains("requires a name");
    assertThat(theName)
        .as("a suite reads the spelling of the parameter rather than knowing the key in advance")
        .containsEntry("theQueryParameterItIsSentIn", "field=" + THE_PAYERS_NAME + "=<value>");
  }

  @Test
  void the_fields_grow_fills_in_itself_are_published_beside_the_ways_and_asked_of_nobody() {
    Map<String, Object> declared = theWaysDeclared();

    Map<String, String> filledIn = new LinkedHashMap<>();
    for (Object field : listIn(declared.get("theFieldsThisSimulatorFillsInItself"))) {
      filledIn.put(
          String.valueOf(mapIn(field).get("key")),
          String.valueOf(mapIn(field).get("whereTheValueComesFrom")));
    }
    assertThat(filledIn).containsKeys("userId", "pageCode", "chargeType");
    assertThat(filledIn.get("pageCode")).contains("never reads it back onto a screen");
    assertThat(theFieldKeysOf(wayIn(declared, A_HOLD)))
        .as("a credential of the account is asked of no caller")
        .doesNotContain("userId", "pageCode", "chargeType");
  }

  @Test
  void a_callback_field_offers_the_targets_this_run_configures_and_nothing_else() {
    Map<String, Object> declared = theWaysDeclared();

    Map<String, Object> callback = fieldIn(wayIn(declared, A_HOLD), THE_CALLBACK);
    assertThat(callback)
        .containsEntry("whereItsValuesComeFrom", "THE_CALLBACK_TARGETS_THIS_RUN_CONFIGURES");
    List<String> offered = new ArrayList<>();
    for (Object value : listIn(callback.get("theValuesItOffers"))) {
      offered.add(String.valueOf(mapIn(value).get("value")));
    }
    assertThat(offered).containsExactly(THE_CALLBACK_THIS_RUN_CONFIGURES);
  }

  @Test
  void a_suite_starts_a_payment_with_the_values_the_discovered_fields_suggest_and_reads_it_back() {
    Map<String, Object> hold = wayIn(theWaysDeclared(), A_HOLD);

    ResponseEntity<Map<String, Object>> started = start(A_HOLD, theValuesEachFieldSuggests(hold));

    assertThat(started.getStatusCode().value()).isEqualTo(201);
    Map<String, Object> body = started.getBody();
    assertThat(body)
        .containsEntry("testRunId", RUN)
        .containsEntry("provider", PROVIDER)
        .containsEntry("accountId", ACCOUNT)
        .containsEntry("startedAs", A_HOLD);
    assertThat(String.valueOf(body.get("whatExistsNow")))
        .isEqualTo(String.valueOf(hold.get("whatItLeavesBehind")));
    String providerObjectId = String.valueOf(body.get("providerObjectId"));
    assertThat(providerObjectId).isNotBlank();
    ResponseEntity<Map<String, Object>> read =
        get("/__simulator/" + PROVIDER + "/state/" + providerObjectId + "?testRunId=" + RUN);
    assertThat(read.getStatusCode().value()).isEqualTo(200);
    assertThat(read.getBody())
        .containsEntry("providerObjectId", providerObjectId)
        .containsEntry("origin", "CONTROL_API");
  }

  @Test
  void the_payment_a_suite_started_is_on_the_trail_as_an_action_of_the_control_api() {
    Map<String, Object> hold = wayIn(theWaysDeclared(), A_HOLD);
    String providerObjectId =
        String.valueOf(
            start(A_HOLD, theValuesEachFieldSuggests(hold)).getBody().get("providerObjectId"));

    List<Map<String, Object>> trail =
        http.get()
            .uri(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/__simulator/"
                        + PROVIDER
                        + "/audit?testRunId="
                        + RUN
                        + "&origin=CONTROL_API"))
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    assertThat(trail)
        .anySatisfy(
            row -> {
              assertThat(row).containsEntry("path", "/__simulator/grow/originations");
              assertThat(row).containsEntry("method", "POST");
              assertThat(row).containsEntry("status", 201);
              assertThat(row).containsEntry("providerObjectId", providerObjectId);
              assertThat(row).containsEntry("accountId", ACCOUNT);
            });
  }

  @Test
  void a_value_grow_refuses_is_answered_four_hundred_and_twenty_two_in_grows_own_words() {
    Map<String, String> values = theValuesEachFieldSuggests(wayIn(theWaysDeclared(), A_HOLD));
    values.put(THE_AMOUNT, "0");

    ResponseEntity<Map<String, Object>> refused = start(A_HOLD, values);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .as(
            "grow answers a non-positive amount with the code and the sentence its own wire"
                + " answers, and this route hands that back rather than rewording it")
        .contains(THE_CODE_GROW_REFUSES_AN_AMOUNT_WITH)
        .doesNotContain("simulator");
  }

  @Test
  void a_required_field_left_out_is_refused_four_hundred_naming_it_in_the_providers_words() {
    Map<String, String> values = theValuesEachFieldSuggests(wayIn(theWaysDeclared(), A_HOLD));
    values.remove(THE_AMOUNT);

    ResponseEntity<Map<String, Object>> refused = start(A_HOLD, values);

    assertThat(refused.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .contains("cannot start without amount")
        .contains("The amount grow puts on the payer");
  }

  @Test
  void a_callback_target_this_run_does_not_configure_is_refused_with_the_ones_it_does() {
    Map<String, String> values = theValuesEachFieldSuggests(wayIn(theWaysDeclared(), A_HOLD));
    values.put(THE_CALLBACK, "https://elsewhere.example/hook");

    ResponseEntity<Map<String, Object>> refused = start(A_HOLD, values);

    assertThat(refused.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(refused.getBody().get("message")))
        .contains(THE_CALLBACK_THIS_RUN_CONFIGURES);
  }

  @Test
  void a_field_no_way_of_this_provider_asks_for_is_refused_rather_than_dropped() {
    Map<String, String> values = theValuesEachFieldSuggests(wayIn(theWaysDeclared(), A_HOLD));
    values.put("invented", "1");

    ResponseEntity<Map<String, Object>> refused = start(A_HOLD, values);

    assertThat(refused.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(refused.getBody().get("message"))).contains("invented");
  }

  @Test
  void an_account_this_run_does_not_configure_is_answered_four_hundred_and_four() {
    Map<String, String> values = theValuesEachFieldSuggests(wayIn(theWaysDeclared(), A_HOLD));

    ResponseEntity<Map<String, Object>> refused =
        startOn("merchant-nobody-configured", A_HOLD, values);

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(String.valueOf(refused.getBody().get("message"))).contains("accountId");
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_four_hundred_and_four_on_both_routes() {
    ResponseEntity<Map<String, Object>> declared =
        get("/__simulator/nosuchprovider/originations?testRunId=" + RUN);
    ResponseEntity<Map<String, Object>> started =
        http.post()
            .uri(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/__simulator/nosuchprovider/originations?testRunId="
                        + RUN))
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(declared.getStatusCode().value()).isEqualTo(404);
    assertThat(started.getStatusCode().value()).isEqualTo(404);
  }

  private static Map<String, String> theValuesEachFieldSuggests(Map<String, Object> way) {
    Map<String, String> values = new LinkedHashMap<>();
    for (Object field : listIn(way.get("fields"))) {
      Map<String, Object> declared = mapIn(field);
      Object suggested = declared.get("theValueItSuggests");
      List<?> offered = listIn(declared.get("theValuesItOffers"));
      if (suggested != null) {
        values.put(String.valueOf(declared.get("key")), String.valueOf(suggested));
      } else if (!offered.isEmpty()) {
        values.put(
            String.valueOf(declared.get("key")),
            String.valueOf(mapIn(offered.getFirst()).get("value")));
      }
    }
    return values;
  }

  private Map<String, Object> theWaysDeclared() {
    ResponseEntity<Map<String, Object>> declared =
        get(
            "/__simulator/"
                + PROVIDER
                + "/originations?testRunId="
                + RUN
                + "&accountId="
                + ACCOUNT);
    assertThat(declared.getStatusCode().value()).isEqualTo(200);
    return declared.getBody();
  }

  private ResponseEntity<Map<String, Object>> start(String startedAs, Map<String, String> values) {
    return startOn(ACCOUNT, startedAs, values);
  }

  private ResponseEntity<Map<String, Object>> startOn(
      String accountId, String startedAs, Map<String, String> values) {
    StringBuilder asked =
        new StringBuilder("/__simulator/" + PROVIDER + "/originations?testRunId=" + RUN)
            .append("&accountId=")
            .append(encoded(accountId))
            .append("&startedAs=")
            .append(encoded(startedAs));
    for (Map.Entry<String, String> value : values.entrySet()) {
      asked.append("&field=").append(encoded(value.getKey() + "=" + value.getValue()));
    }
    return http.post()
        .uri(URI.create("http://localhost:" + port + asked))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<Map<String, Object>> get(String path) {
    return http.get()
        .uri(URI.create("http://localhost:" + port + path))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, UTF_8);
  }

  private static List<String> theWayNamesIn(Map<String, Object> declared) {
    List<String> named = new ArrayList<>();
    for (Object way : listIn(declared.get("waysToStartAPayment"))) {
      named.add(String.valueOf(mapIn(way).get("startedAs")));
    }
    return List.copyOf(named);
  }

  private static Map<String, Object> wayIn(Map<String, Object> declared, String startedAs) {
    for (Object way : listIn(declared.get("waysToStartAPayment"))) {
      if (startedAs.equals(mapIn(way).get("startedAs"))) {
        return mapIn(way);
      }
    }
    throw new IllegalStateException("no way named " + startedAs + " in " + declared);
  }

  private static List<String> theFieldKeysOf(Map<String, Object> way) {
    List<String> keys = new ArrayList<>();
    for (Object field : listIn(way.get("fields"))) {
      keys.add(String.valueOf(mapIn(field).get("key")));
    }
    return List.copyOf(keys);
  }

  private static Map<String, Object> fieldIn(Map<String, Object> way, String key) {
    for (Object field : listIn(way.get("fields"))) {
      if (key.equals(mapIn(field).get("key"))) {
        return mapIn(field);
      }
    }
    throw new IllegalStateException("no field " + key + " in " + way);
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

  private void configure() {
    http.put()
        .uri("/__simulator/" + PROVIDER + "/config")
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
                        Map.of("userId", "a-grow-user-id", "pageCode", "a-grow-page-code"))),
                "webhooks",
                List.of(Map.of("accountId", ACCOUNT, "url", THE_CALLBACK_THIS_RUN_CONFIGURES))))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/" + PROVIDER + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN))
        .retrieve()
        .toBodilessEntity();
  }
}
