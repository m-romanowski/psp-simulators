package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class SimulatorControlApiIntegrationTest extends PostgresBackedTest {

  private static final String PINNED = "2026-09-07T12:00:00Z";

  @LocalServerPort private int port;

  @Autowired private TestRuns testRuns;
  @Autowired private JdbcClient jdbc;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
  }

  @AfterEach
  void clearWhatThisTestConfigured() {
    for (String testRunId : List.of("control-api-it", "default")) {
      post("grow", Map.of("testRunId", testRunId, "scope", "TEST_RUN"));
      put("grow", Map.of("testRunId", testRunId, "clock", Map.of()));
      forgetTheAccountsOf(testRunId);
    }
  }

  private void forgetTheAccountsOf(String testRunId) {
    jdbc.sql("delete from simulator_provider_account where test_run_id = ?")
        .param(testRunId)
        .update();
    jdbc.sql("delete from simulator_webhook_target where test_run_id = ?")
        .param(testRunId)
        .update();
    jdbc.sql("delete from grow_account where test_run_id = ?").param(testRunId).update();
  }

  @Test
  void configuring_a_provider_reports_what_was_configured_and_no_credential() {
    ResponseEntity<Map<String, Object>> response =
        put(
            "grow",
            Map.of(
                "testRunId", "control-api-it",
                "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            "merchant-1",
                            "configuration",
                            Map.of(
                                "userId", "sk-live-must-not-come-back",
                                "pageCode", "control-api-page"))),
                "webhooks",
                    List.of(
                        Map.of(
                            "accountId", "merchant-1",
                            "url", "https://merchant.example/hook"))));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody())
        .containsEntry("testRunId", "control-api-it")
        .containsEntry("provider", "grow")
        .containsEntry("configuredAccounts", List.of("merchant-1"))
        .containsEntry("configuredWebhookTargets", 1)
        .containsKey("updatedAt");
    assertThat(response.getBody().toString()).doesNotContain("sk-live-must-not-come-back");
  }

  @Test
  void a_request_without_a_test_run_lands_in_the_default_namespace() {
    ResponseEntity<Map<String, Object>> response = put("grow", Map.of("accounts", List.of()));

    assertThat(response.getBody()).containsEntry("testRunId", "default");
  }

  @Test
  void binds_a_route_alias_and_says_how_many_urls_the_test_run_now_answers_on() {
    ResponseEntity<Map<String, Object>> response =
        put(
            "grow",
            Map.of(
                "testRunId",
                "control-api-it",
                "routeAliases",
                List.of(
                    Map.of(
                        "from",
                        "https://sandbox.meshulam.co.il/api/light/server/1.0/createPaymentProcess",
                        "to",
                        "/api/light/server/1.0/createPaymentProcess"))));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("configuredRouteAliases", 1);
    assertThat(response.getBody()).containsEntry("configuredAccounts", List.of());
  }

  @Test
  void refuses_a_base_url_that_names_no_path_because_an_alias_binds_one_exact_url() {
    ResponseEntity<Map<String, Object>> response =
        put(
            "grow",
            Map.of(
                "testRunId",
                "control-api-it",
                "routeAliases",
                List.of(
                    Map.of(
                        "from", "https://sandbox.meshulam.co.il", "to", "/api/light/server/1.0"))));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().toString())
        .contains("routeAliases[0].from must name the path the application calls");
  }

  @Test
  void refuses_a_configuration_key_the_provider_reads_nothing_of_and_never_echoes_its_value() {
    ResponseEntity<Map<String, Object>> response =
        put(
            "grow",
            Map.of(
                "testRunId",
                "control-api-it",
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        "merchant-1",
                        "configuration",
                        Map.of(
                            "userId", "control-api-user",
                            "pageCode", "control-api-page",
                            "privateKeyPem", "-----BEGIN PRIVATE KEY-----must-not-come-back")))));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().toString())
        .contains("accounts[0].configuration.privateKeyPem")
        .doesNotContain("must-not-come-back");
  }

  @Test
  void pinning_the_clock_moves_the_time_the_control_api_reports() {
    ResponseEntity<Map<String, Object>> response = configureWithClock(Map.of("fixedNow", PINNED));

    assertThat(response.getBody()).containsEntry("updatedAt", PINNED);
  }

  @Test
  void a_config_call_with_no_clock_block_leaves_an_already_pinned_clock_alone() {
    configureWithClock(Map.of("fixedNow", PINNED));

    ResponseEntity<Map<String, Object>> withoutAClockBlock =
        put(
            "grow",
            Map.of(
                "testRunId",
                "control-api-it",
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        "merchant-1",
                        "configuration",
                        Map.of("userId", "control-api-user", "pageCode", "control-api-page")))));

    assertThat(withoutAClockBlock.getBody()).containsEntry("updatedAt", PINNED);
  }

  @Test
  void a_clock_block_with_no_fixed_now_returns_the_test_run_to_the_host_clock() {
    configureWithClock(Map.of("fixedNow", PINNED));

    ResponseEntity<Map<String, Object>> released = configureWithClock(Map.of());

    Instant reported = Instant.parse(String.valueOf(released.getBody().get("updatedAt")));
    assertThat(reported).isNotEqualTo(Instant.parse(PINNED));
  }

  @Test
  void reset_reports_what_it_cleared() {
    ResponseEntity<Map<String, Object>> response =
        post("grow", Map.of("testRunId", "control-api-reset-it", "scope", "TEST_RUN"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody())
        .containsEntry("testRunId", "control-api-reset-it")
        .containsEntry("provider", "grow")
        .containsEntry("clearedObjects", 0)
        .containsEntry("clearedAuditRecords", 0);
  }

  @Test
  void reset_accepts_a_request_with_no_body_at_all() {
    ResponseEntity<Map<String, Object>> response =
        http.post()
            .uri("/__simulator/grow/reset")
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("testRunId", "default");
  }

  @Test
  void reset_of_every_test_run_reports_no_single_test_run() {
    ResponseEntity<Map<String, Object>> response =
        post("grow", Map.of("testRunId", "control-api-reset-it", "scope", "ALL"));

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).containsEntry("scope", "ALL").containsEntry("provider", "grow");
    assertThat(response.getBody().get("testRunId")).isNull();
  }

  @Test
  void reset_leaves_the_configured_accounts_in_place() {
    put(
        "grow",
        Map.of(
            "testRunId",
            "control-api-it",
            "accounts",
            List.of(
                Map.of(
                    "accountId",
                    "merchant-1",
                    "configuration",
                    Map.of("userId", "control-api-user", "pageCode", "control-api-page")))));

    post("grow", Map.of("testRunId", "control-api-it", "scope", "TEST_RUN"));

    ResponseEntity<byte[]> created =
        http.post()
            .uri("/api/light/server/1.0/createPaymentProcess")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(hold())
            .retrieve()
            .toEntity(byte[].class);

    assertThat(new String(created.getBody(), StandardCharsets.UTF_8))
        .contains("\"status\":1")
        .doesNotContain("\"id\":701");
  }

  @Test
  void a_harness_configures_a_second_account_and_the_first_one_still_serves_a_payment() {
    put("grow", configuring("merchant-1", "control-api-user", "control-api-page"));

    ResponseEntity<Map<String, Object>> second =
        put("grow", configuring("merchant-2", "second-user", "second-page"));

    assertThat(second.getStatusCode().value()).isEqualTo(200);
    assertThat(second.getBody())
        .containsEntry("configuredAccounts", List.of("merchant-1", "merchant-2"));
    assertThat(theAnswerTo(hold())).contains("\"status\":1").doesNotContain("\"id\":701");
  }

  @Test
  void a_harness_that_omits_its_accounts_to_pin_a_clock_keeps_every_account_of_the_run() {
    put(
        "grow",
        configuringWithATarget(
            "merchant-1", "control-api-user", "control-api-page", "https://merchant.example/hook"));

    ResponseEntity<Map<String, Object>> pinned =
        configureWithClock(Map.of("fixedNow", "2026-09-12T10:00:00Z"));

    assertThat(pinned.getStatusCode().value()).isEqualTo(200);
    assertThat(pinned.getBody())
        .containsEntry("configuredAccounts", List.of("merchant-1"))
        .containsEntry("configuredWebhookTargets", 0)
        .containsEntry("updatedAt", "2026-09-12T10:00:00Z");
    assertThat(theAnswerTo(hold())).contains("\"status\":1").doesNotContain("\"id\":701");
  }

  @Test
  void a_credential_an_account_of_the_run_already_holds_is_unprocessable_and_stores_nothing() {
    put("grow", configuring("merchant-1", "control-api-user", "control-api-page"));

    ResponseEntity<Map<String, Object>> colliding =
        put("grow", configuring("merchant-2", "control-api-user", "second-page"));

    assertThat(colliding.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(colliding.getBody().get("message")))
        .contains("accounts[0].configuration.userId")
        .contains("already configured for another account");
    assertThat(put("grow", configWith("testRunId", "control-api-it")).getBody())
        .containsEntry("configuredAccounts", List.of("merchant-1"));
  }

  @Test
  void an_unknown_provider_is_not_found() {
    assertThat(put("no-such-psp", Map.of("accounts", List.of())).getStatusCode().value())
        .isEqualTo(404);
    assertThat(post("no-such-psp", Map.of()).getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void an_account_the_provider_cannot_use_is_unprocessable() {
    ResponseEntity<Map<String, Object>> response =
        put(
            "grow",
            Map.of(
                "accounts", List.of(Map.of("accountId", "merchant-1", "configuration", Map.of()))));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody())
        .containsEntry("message", "accounts[0].configuration.userId is required by provider grow");
  }

  @Test
  void a_configuration_the_provider_refuses_creates_no_test_run() {
    String testRunId = "control-api-it-rejected";
    assertThat(testRuns.exists(TestRunId.of(testRunId))).isFalse();

    ResponseEntity<Map<String, Object>> response =
        put(
            "grow",
            Map.of(
                "testRunId",
                testRunId,
                "accounts",
                List.of(Map.of("accountId", "merchant-1", "configuration", Map.of()))));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(testRuns.exists(TestRunId.of(testRunId))).isFalse();
  }

  @Test
  void a_configuration_the_provider_refuses_pins_no_clock() {
    Map<String, Object> refused = new HashMap<>();
    refused.put("testRunId", "control-api-it");
    refused.put("clock", Map.of("fixedNow", PINNED));
    refused.put("accounts", List.of(Map.of("accountId", "merchant-1", "configuration", Map.of())));

    assertThat(put("grow", refused).getStatusCode().value()).isEqualTo(422);

    ResponseEntity<Map<String, Object>> readingTheClockBack =
        put("grow", Map.of("testRunId", "control-api-it", "accounts", List.of()));
    assertThat(readingTheClockBack.getBody()).doesNotContainEntry("updatedAt", PINNED);
  }

  @Test
  void a_configuration_the_provider_refuses_leaves_the_clock_it_had_pinned_where_it_was() {
    assertThat(configureWithClock(Map.of("fixedNow", PINNED)).getBody())
        .containsEntry("updatedAt", PINNED);

    Map<String, Object> refused = new HashMap<>();
    refused.put("testRunId", "control-api-it");
    refused.put("clock", Map.of());
    refused.put("accounts", List.of(Map.of("accountId", "merchant-1", "configuration", Map.of())));

    assertThat(put("grow", refused).getStatusCode().value()).isEqualTo(422);

    ResponseEntity<Map<String, Object>> readingTheClockBack =
        put("grow", Map.of("testRunId", "control-api-it", "accounts", List.of()));
    assertThat(readingTheClockBack.getBody()).containsEntry("updatedAt", PINNED);
  }

  @Test
  void a_malformed_control_request_is_a_bad_request_naming_the_property() {
    assertThat(badRequestMessage(put("grow", Map.of("testRunId", "not a valid id"))))
        .contains("testRunId");
    assertThat(badRequestMessage(put("grow", Map.of("accounts", List.of(Map.of())))))
        .isEqualTo("accounts[0].accountId is required");
    assertThat(
            badRequestMessage(
                put(
                    "grow",
                    Map.of(
                        "accounts",
                        List.of(
                            Map.of(
                                "accountId",
                                "merchant-1",
                                "configuration",
                                Map.of("userId", "u1", "pageCode", "p1")),
                            Map.of(
                                "accountId",
                                "merchant-1",
                                "configuration",
                                Map.of("userId", "u2", "pageCode", "p2")))))))
        .isEqualTo("accounts[1].accountId is already configured by an earlier account");
    assertThat(
            badRequestMessage(
                put("grow", Map.of("webhooks", List.of(Map.of("accountId", "merchant-1"))))))
        .isEqualTo("webhooks[0].url is required");
    assertThat(badRequestMessage(put("grow", Map.of("webhooks", List.of(Map.of("url", "u"))))))
        .isEqualTo("webhooks[0].accountId is required");
    assertThat(badRequestMessage(put("grow", Map.of("routeAliases", List.of(Map.of("to", "/x"))))))
        .isEqualTo("routeAliases[0].from is required");
    assertThat(badRequestMessage(put("grow", Map.of("routeAliases", List.of(Map.of("from", "u"))))))
        .isEqualTo("routeAliases[0].to is required");
    assertThat(badRequestMessage(put("grow", Map.of("clock", Map.of("fixedNow", "yesterday")))))
        .isEqualTo("clock.fixedNow must be an ISO-8601 instant");
    assertThat(badRequestMessage(post("grow", Map.of("scope", "EVERYTHING"))))
        .isEqualTo("scope must be one of TEST_RUN, ALL");
  }

  @Test
  void a_control_body_holding_no_object_where_one_belongs_is_a_bad_request_naming_the_position() {
    assertThat(badRequestMessage(put("grow", configWith("accounts", nothingAtAll()))))
        .isEqualTo("accounts[0] must name an account");
    assertThat(badRequestMessage(put("grow", configWith("webhooks", nothingAtAll()))))
        .isEqualTo("webhooks[0] must name a webhook target");
    assertThat(badRequestMessage(put("grow", configWith("routeAliases", nothingAtAll()))))
        .isEqualTo("routeAliases[0] must name a route alias");
  }

  @Test
  void a_configured_value_sent_as_null_is_a_bad_request_naming_the_property_and_not_the_value() {
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("processLookupRejection", null);
    Map<String, Object> account = new HashMap<>();
    account.put("accountId", "merchant-1");
    account.put("configuration", configuration);

    assertThat(badRequestMessage(put("grow", configWith("accounts", List.of(account)))))
        .isEqualTo(
            "accounts[0].configuration.processLookupRejection must name the value the account is"
                + " configured with");
  }

  @Test
  void refuses_to_address_the_test_run_id_reserved_for_a_failed_resolution() {
    assertThat(badRequestMessage(put("grow", Map.of("testRunId", "UNKNOWN"))))
        .contains("testRunId")
        .contains("reserved");
    assertThat(badRequestMessage(post("grow", Map.of("testRunId", "UNKNOWN", "scope", "TEST_RUN"))))
        .contains("reserved");
  }

  @Test
  void a_rejection_never_echoes_the_value_it_rejected() {
    ResponseEntity<Map<String, Object>> response =
        put("grow", Map.of("testRunId", "sk-live-0123456789 leaked"));

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().toString()).doesNotContain("sk-live-0123456789");
  }

  @Test
  void creating_a_scenario_answers_201_with_the_published_scenario_response() {
    ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", aDecline());

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody())
        .containsEntry("testRunId", "control-api-it")
        .containsEntry("provider", "grow")
        .containsEntry("priority", 100)
        .containsKey("createdAt");
    assertThat(String.valueOf(response.getBody().get("scenarioId"))).startsWith("scn_");
  }

  @Test
  void creating_a_scenario_registers_the_test_run_it_names() {
    assertThat(testRuns.exists(TestRunId.of("control-api-it-scenario-only"))).isFalse();

    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("testRunId", "control-api-it-scenario-only");
    assertThat(post("grow", "/scenarios", body).getStatusCode().value()).isEqualTo(201);

    assertThat(testRuns.exists(TestRunId.of("control-api-it-scenario-only"))).isTrue();
    delete("grow", "/scenarios", "control-api-it-scenario-only");
  }

  @Test
  void a_created_scenario_is_read_back_by_its_id_and_listed_newest_first() {
    String first = createdScenarioId(aDecline());
    String second = createdScenarioId(aDecline());

    ResponseEntity<Map<String, Object>> read = get("grow", "/scenarios/" + second);
    assertThat(read.getStatusCode().value()).isEqualTo(200);
    assertThat(read.getBody()).containsEntry("scenarioId", second);

    assertThat(list("grow"))
        .extracting(scenario -> scenario.get("scenarioId"))
        .containsExactly(second, first);
  }

  @Test
  void an_unknown_scenario_id_is_not_found_and_neither_is_an_unknown_provider() {
    assertThat(get("grow", "/scenarios/scn_nothing").getStatusCode().value()).isEqualTo(404);
    assertThat(get("no-such-psp", "/scenarios").getStatusCode().value()).isEqualTo(404);
    assertThat(post("no-such-psp", "/scenarios", aDecline()).getStatusCode().value())
        .isEqualTo(404);
    assertThat(
            exchange(HttpMethod.DELETE, "/__simulator/no-such-psp/scenarios/scn_x", null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  void deleting_a_scenario_answers_200_once_and_404_after_that() {
    String scenarioId = createdScenarioId(aDecline());

    ResponseEntity<Map<String, Object>> deleted =
        delete("grow", "/scenarios/" + scenarioId, "control-api-it");

    assertThat(deleted.getStatusCode().value()).isEqualTo(200);
    assertThat(deleted.getBody()).containsEntry("scenarioId", scenarioId);
    assertThat(delete("grow", "/scenarios/" + scenarioId, "control-api-it").getStatusCode().value())
        .isEqualTo(404);
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_scenario_request_this_layer_cannot_read_is_rejected_by_the_property_that_broke_it() {
    assertThat(scenarioBadRequestMessage(scenarioWithout("action")))
        .isEqualTo("action is required");
    assertThat(scenarioBadRequestMessage(scenarioActing(Map.of("type", "TERMINAL_FAILURE"))))
        .isEqualTo("action.providerCode is required");
  }

  @Test
  void an_action_type_this_layer_does_not_know_is_refused_beside_every_field_it_can_judge() {
    ResponseEntity<Map<String, Object>> response =
        post("grow", "/scenarios", scenarioActing(Map.of("type", "SOMETHING_ELSE")));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties")).isEqualTo(List.of("action.type"));
    assertThat(String.valueOf(response.getBody().get("message")))
        .contains(
            "action.type must be one of FIXED_SUCCESS, TERMINAL_FAILURE, TRANSPORT_FAULT,"
                + " TRANSPORT_FAULT_INSTEAD_OF_OPERATION");
    assertThat(list("grow")).isEmpty();
  }

  @ParameterizedTest(name = "the removed action type {0} is refused and told what replaced it")
  @ValueSource(strings = {"TRANSIENT_THEN_SUCCESS", "TRANSPORT_FAULT_THEN_SUCCESS"})
  void an_action_type_the_story_grammar_replaced_is_refused_naming_what_replaced_it(String type) {
    ResponseEntity<Map<String, Object>> response =
        post("grow", "/scenarios", scenarioActing(Map.of("type", type, "providerCode", "813")));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties")).isEqualTo(List.of("action.type"));
    assertThat(String.valueOf(response.getBody().get("message")))
        .contains("story")
        .contains("whenState")
        .contains("thenState");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_state_named_without_the_story_it_is_a_state_of_is_a_bad_request_naming_the_property() {
    for (String property : List.of("whenState", "thenState")) {
      Map<String, Object> body = new HashMap<>(aDecline());
      body.put(property, "asked");

      ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(String.valueOf(response.getBody().get("message")))
          .contains("story is required by keyedOn, whenState and thenState");
    }
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_key_named_without_the_story_it_keys_is_a_bad_request_naming_the_property() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("keyedOn", "TEST_RUN");

    ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(response.getBody().get("message")))
        .contains("story is required by keyedOn, whenState and thenState");
    assertThat(list("grow")).isEmpty();
  }

  @ParameterizedTest(name = "the key {0} names nothing this simulator keys a story on")
  @ValueSource(strings = {"flow", "PROCESS", "", "OBJECT"})
  void a_key_no_story_can_be_keyed_on_is_a_bad_request_naming_what_may_be_declared(String named) {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("story", "capture-retries");
    body.put("keyedOn", named);

    ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(response.getBody().get("message")))
        .contains("keyedOn")
        .contains("FLOW, TEST_RUN");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_step_that_declares_no_key_reads_back_keyed_on_the_flow_the_provider_resolves() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("story", "capture-retries");
    body.put("whenState", "Started");
    body.put("thenState", "asked");

    ResponseEntity<Map<String, Object>> created = post("grow", "/scenarios", body);

    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(created.getBody()).containsEntry("keyedOn", "FLOW");
    assertThat(list("grow")).extracting("keyedOn").containsExactly("FLOW");
  }

  @Test
  void a_step_that_declares_the_test_run_reads_back_keyed_on_the_test_run() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("story", "hold-retries");
    body.put("keyedOn", "TEST_RUN");
    body.put("whenState", "Started");
    body.put("thenState", "asked");

    ResponseEntity<Map<String, Object>> created = post("grow", "/scenarios", body);

    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(created.getBody()).containsEntry("keyedOn", "TEST_RUN");
  }

  @Test
  void a_rule_that_is_no_step_of_a_story_reads_back_keyed_on_nothing() {
    ResponseEntity<Map<String, Object>> created = post("grow", "/scenarios", aDecline());

    assertThat(created.getStatusCode().value()).isEqualTo(201);
    assertThat(created.getBody().get("keyedOn")).isNull();
    assertThat(list("grow")).extracting("keyedOn").containsExactly((Object) null);
  }

  @ParameterizedTest(name = "a {0} that names nothing or is too long is refused at the wire")
  @ValueSource(strings = {"story", "whenState", "thenState"})
  void a_story_label_the_stored_rule_would_refuse_is_a_bad_request_naming_the_property(
      String property) {
    for (String label : List.of(" ", "\t", "\u3000", "s".repeat(65))) {
      Map<String, Object> body = new HashMap<>(aDecline());
      body.put("story", "capture-retries");
      body.put(property, label);

      ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(String.valueOf(response.getBody().get("message"))).contains(property);
    }
    assertThat(list("grow")).isEmpty();
  }

  @ParameterizedTest(name = "refuses to pretend it can apply action.{0}")
  @CsvSource({"httpStatus,504", "terminalStatus,SUCCEEDED", "delayMs,250"})
  void an_action_field_no_layer_can_apply_yet_is_refused_rather_than_dropped(
      String field, String value) {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TERMINAL_FAILURE");
    action.put("providerCode", "813");
    action.put(field, value);

    ResponseEntity<Map<String, Object>> response =
        post("grow", "/scenarios", scenarioActing(action));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(response.getBody().get("message"))).startsWith("action." + field);
    assertThat(response.getBody().get("properties")).isEqualTo(List.of("action." + field));
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_transport_fault_scenario_is_created_with_the_status_the_layer_now_performs() {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TRANSPORT_FAULT");
    action.put("fault", "SERVER_ERROR");
    action.put("httpStatus", 504);

    assertThat(post("grow", "/scenarios", scenarioActing(action)).getStatusCode().value())
        .isEqualTo(201);
    assertThat(list("grow")).hasSize(1);
  }

  @Test
  void a_scenario_that_answers_a_status_instead_of_running_the_operation_is_created() {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION");
    action.put("fault", "SERVER_ERROR");
    action.put("httpStatus", 502);

    assertThat(post("grow", "/scenarios", scenarioActing(action)).getStatusCode().value())
        .isEqualTo(201);
    assertThat(list("grow")).hasSize(1);
  }

  @ParameterizedTest(
      name = "{0} cannot be answered instead of the operation that renders its bytes")
  @MethodSource("theFaultsNoActionCanAnswerWithoutTheOperation")
  void a_fault_defined_on_rendered_bytes_is_refused_naming_what_it_does_with_them(
      String fault, @Nullable Long delayMs, String whatItDoesWithThem) {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION");
    action.put("fault", fault);
    if (delayMs != null) {
      action.put("delayMs", delayMs);
    }

    assertThat(scenarioBadRequestMessage(scenarioActing(action)))
        .isEqualTo(
            "action.fault "
                + fault
                + " "
                + whatItDoesWithThem
                + ", and an action applied instead of the operation renders none; only"
                + " SERVER_ERROR answers without them");
    assertThat(list("grow")).isEmpty();
  }

  private static List<Arguments> theFaultsNoActionCanAnswerWithoutTheOperation() {
    return List.of(
        Arguments.of(
            "TRUNCATED_BODY", null, "sends a genuine prefix of the bytes an operation rendered"),
        Arguments.of("CORRUPTED_BODY", null, "inverts one of the bytes an operation rendered"),
        Arguments.of(
            "DROPPED_CONNECTION",
            null,
            "writes part of the bytes an operation rendered under their full declared length"),
        Arguments.of("DELAYED_RESPONSE", 250L, "delivers the bytes an operation rendered late"));
  }

  @Test
  void an_action_that_replaces_the_operation_is_told_that_it_and_not_another_type_needs_a_fault() {
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(Map.of("type", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION"))))
        .isEqualTo(
            "action.fault is required by TRANSPORT_FAULT_INSTEAD_OF_OPERATION, and must be one of"
                + " SERVER_ERROR, TRUNCATED_BODY, CORRUPTED_BODY, DROPPED_CONNECTION,"
                + " DELAYED_RESPONSE");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void an_action_that_replaces_the_operation_answers_no_provider_code_of_its_own() {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION");
    action.put("fault", "SERVER_ERROR");
    action.put("httpStatus", 502);
    action.put("providerCode", "813");

    assertThat(scenarioBadRequestMessage(scenarioActing(action)))
        .contains("action.providerCode applies only to TERMINAL_FAILURE");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_delayed_response_scenario_is_created_with_the_delay_the_layer_now_performs() {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TRANSPORT_FAULT");
    action.put("fault", "DELAYED_RESPONSE");
    action.put("delayMs", 250);

    assertThat(post("grow", "/scenarios", scenarioActing(action)).getStatusCode().value())
        .isEqualTo(201);
    assertThat(list("grow")).hasSize(1);
  }

  @ParameterizedTest(name = "action.{0} stays refused on a fault that cannot perform it")
  @CsvSource({"httpStatus,502", "delayMs,250"})
  void a_field_no_fault_of_this_action_performs_is_still_refused_rather_than_dropped(
      String field, String value) {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TRANSPORT_FAULT");
    action.put("fault", "CORRUPTED_BODY");
    action.put(field, value);

    ResponseEntity<Map<String, Object>> response =
        post("grow", "/scenarios", scenarioActing(action));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties")).isEqualTo(List.of("action." + field));
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void
      a_transport_fault_request_this_layer_cannot_read_is_rejected_by_the_property_that_broke_it() {
    assertThat(scenarioBadRequestMessage(scenarioActing(Map.of("type", "TRANSPORT_FAULT"))))
        .isEqualTo(
            "action.fault is required by TRANSPORT_FAULT, and must be one of SERVER_ERROR,"
                + " TRUNCATED_BODY, CORRUPTED_BODY, DROPPED_CONNECTION, DELAYED_RESPONSE");
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(Map.of("type", "TRANSPORT_FAULT", "fault", "SOMETHING_ELSE"))))
        .isEqualTo(
            "action.fault must be one of SERVER_ERROR, TRUNCATED_BODY, CORRUPTED_BODY,"
                + " DROPPED_CONNECTION, DELAYED_RESPONSE");
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(
                    Map.of(
                        "type",
                        "TRANSPORT_FAULT",
                        "fault",
                        "SERVER_ERROR",
                        "providerCode",
                        "813"))))
        .contains("action.providerCode applies only to");
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(Map.of("type", "TRANSPORT_FAULT", "fault", "SERVER_ERROR"))))
        .isEqualTo("action.httpStatus is required by action.fault SERVER_ERROR");
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(
                    Map.of(
                        "type", "TRANSPORT_FAULT",
                        "fault", "SERVER_ERROR",
                        "httpStatus", 200))))
        .contains("action.httpStatus must be between 500 and 599");
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(Map.of("type", "TRANSPORT_FAULT", "fault", "DELAYED_RESPONSE"))))
        .isEqualTo("action.delayMs is required by action.fault DELAYED_RESPONSE");
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(
                    Map.of(
                        "type", "TRANSPORT_FAULT",
                        "fault", "DELAYED_RESPONSE",
                        "delayMs", 0))))
        .contains("action.delayMs must be at least one millisecond");
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(
                    Map.of(
                        "type", "TRANSPORT_FAULT",
                        "fault", "DELAYED_RESPONSE",
                        "delayMs", 60000))))
        .contains("simulator.fault.longest-delay");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_fault_named_on_an_action_that_cannot_perform_one_is_refused_rather_than_stored() {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TERMINAL_FAILURE");
    action.put("providerCode", "813");
    action.put("fault", "DROPPED_CONNECTION");

    ResponseEntity<Map<String, Object>> response =
        post("grow", "/scenarios", scenarioActing(action));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties")).isEqualTo(List.of("action.fault"));
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void every_field_a_decline_cannot_perform_is_named_in_one_answer_including_the_fault() {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TERMINAL_FAILURE");
    action.put("failuresBeforeSuccess", 1);
    action.put("httpStatus", 504);
    action.put("providerCode", "813");
    action.put("terminalStatus", "SUCCEEDED");
    action.put("delayMs", 250);
    action.put("fault", "SERVER_ERROR");

    ResponseEntity<Map<String, Object>> response =
        post("grow", "/scenarios", scenarioActing(action));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties"))
        .isEqualTo(
            List.of(
                "action.failuresBeforeSuccess",
                "action.httpStatus",
                "action.fault",
                "action.terminalStatus",
                "action.delayMs"));
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_count_of_failures_before_success_is_refused_and_told_it_is_a_story_of_two_steps() {
    ResponseEntity<Map<String, Object>> response =
        post(
            "grow",
            "/scenarios",
            scenarioActing(
                Map.of(
                    "type",
                    "TERMINAL_FAILURE",
                    "providerCode",
                    "813",
                    "failuresBeforeSuccess",
                    1)));

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties"))
        .isEqualTo(List.of("action.failuresBeforeSuccess"));
    assertThat(String.valueOf(response.getBody().get("message")))
        .contains("story")
        .contains("whenState")
        .contains("thenState");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void the_published_scenario_example_names_every_property_this_layer_refuses_in_one_answer() {
    Map<String, Object> action = new HashMap<>();
    action.put("type", "TRANSIENT_THEN_SUCCESS");
    action.put("failuresBeforeSuccess", 1);
    action.put("httpStatus", 504);
    action.put("providerCode", "GATEWAY_TIMEOUT");
    action.put("terminalStatus", "SUCCEEDED");
    action.put("delayMs", 0);
    Map<String, Object> body = new HashMap<>();
    body.put("testRunId", "control-api-it");
    body.put("priority", 100);
    body.put(
        "match",
        Map.of(
            "accountId",
            "merchant-1",
            "configuration",
            Map.of(
                "orderId",
                "order-123",
                "paymentMethodId",
                "pm_success",
                "amount",
                "1000",
                "currency",
                "PLN",
                "header",
                Map.of("X-Simulator-Scenario", "capture-timeout-then-success"))));
    body.put("action", action);

    ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties"))
        .isEqualTo(
            List.of(
                "action.type",
                "action.failuresBeforeSuccess",
                "action.httpStatus",
                "action.terminalStatus",
                "action.delayMs"));
    assertThat(String.valueOf(response.getBody().get("message")))
        .contains("story")
        .contains("whenState")
        .contains("thenState");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_scenario_matching_a_customer_grow_never_carries_is_refused_by_the_property() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("match", Map.of("customerId", "cus_sim_123"));

    ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody().get("properties")).isEqualTo(List.of("match.customerId"));
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_header_predicate_sent_with_no_value_is_a_bad_request_naming_the_header() {
    Map<String, String> header = new HashMap<>();
    header.put("X-Simulator-Scenario", null);
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("match", Map.of("header", header));

    ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(response.getBody().get("message")))
        .isEqualTo("match.header.X-Simulator-Scenario must name the value the request carries");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_scenario_naming_the_object_it_declines_is_created() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("match", Map.of("providerObjectId", "9000001"));

    assertThat(post("grow", "/scenarios", body).getStatusCode().value()).isEqualTo(201);
    assertThat(list("grow")).hasSize(1);
  }

  @Test
  void a_scenario_the_provider_cannot_apply_is_refused_with_422_and_never_stored() {
    ResponseEntity<Map<String, Object>> nonNumeric =
        post(
            "grow",
            "/scenarios",
            scenarioActing(Map.of("type", "TERMINAL_FAILURE", "providerCode", "GATEWAY_TIMEOUT")));

    assertThat(nonNumeric.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(nonNumeric.getBody().get("message"))).contains("action.providerCode");

    Map<String, Object> matchingCurrency = new HashMap<>(aDecline());
    matchingCurrency.put("match", Map.of("currency", "PLN"));
    ResponseEntity<Map<String, Object>> currency = post("grow", "/scenarios", matchingCurrency);

    assertThat(currency.getStatusCode().value()).isEqualTo(422);
    assertThat(String.valueOf(currency.getBody().get("message"))).contains("match.currency");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_scenario_request_naming_a_malformed_test_run_is_rejected_without_echoing_it() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("testRunId", "sk-live-0123456789 leaked");

    ResponseEntity<Map<String, Object>> response = post("grow", "/scenarios", body);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().toString()).doesNotContain("sk-live-0123456789");
    assertThat(get("grow", "/scenarios?testRunId=UNKNOWN").getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void a_scenario_naming_an_operation_reports_it_back_where_a_harness_reads_the_rule() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("match", Map.of("operation", "settleSuspendedTransaction"));

    String scenarioId = createdScenarioId(body);

    assertThat(get("grow", "/scenarios/" + scenarioId).getBody())
        .containsEntry("operation", "settleSuspendedTransaction");
    assertThat(list("grow"))
        .singleElement()
        .satisfies(
            rule -> assertThat(rule).containsEntry("operation", "settleSuspendedTransaction"));
  }

  @Test
  void a_scenario_naming_no_operation_reports_none_rather_than_inventing_one() {
    assertThat(get("grow", "/scenarios/" + createdScenarioId(aDecline())).getBody())
        .containsEntry("operation", null);
  }

  @Test
  void a_scenario_naming_an_operation_the_provider_does_not_expose_is_refused_and_not_stored() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("match", Map.of("operation", "cancelBitTransaction"));

    ResponseEntity<Map<String, Object>> refused = post("grow", "/scenarios", body);

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refused.getBody().get("properties")).isEqualTo(List.of("match.operation"));
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void an_operation_predicate_that_names_nothing_is_rejected_by_the_property_that_broke_it() {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.put("match", Map.of("operation", " "));

    assertThat(scenarioBadRequestMessage(body)).startsWith("match.operation");
  }

  @Test
  void a_fixed_success_scenario_is_created_and_refuses_the_fields_it_could_never_answer_with() {
    assertThat(
            post("grow", "/scenarios", scenarioActing(Map.of("type", "FIXED_SUCCESS")))
                .getStatusCode()
                .value())
        .isEqualTo(201);
    assertThat(
            scenarioBadRequestMessage(
                scenarioActing(Map.of("type", "FIXED_SUCCESS", "providerCode", "813"))))
        .contains("action.providerCode applies only to");
    ResponseEntity<Map<String, Object>> withAFault =
        post(
            "grow",
            "/scenarios",
            scenarioActing(Map.of("type", "FIXED_SUCCESS", "fault", "DROPPED_CONNECTION")));

    assertThat(withAFault.getStatusCode().value()).isEqualTo(422);
    assertThat(withAFault.getBody().get("properties")).isEqualTo(List.of("action.fault"));
  }

  @Test
  void a_preset_request_this_layer_cannot_read_is_rejected_by_the_property_that_broke_it() {
    assertThat(badRequestMessage(post("grow", "/scenarios/presets", Map.of())))
        .startsWith("preset is required");
    assertThat(
            badRequestMessage(
                post("grow", "/scenarios/presets", Map.of("preset", "cancel-hold-succes"))))
        .startsWith("preset must be one of card-add-success");
    assertThat(
            badRequestMessage(
                post(
                    "grow",
                    "/scenarios/presets",
                    Map.of(
                        "preset",
                        "cancel-hold-success",
                        "match",
                        Map.of("operation", "refundTransaction")))))
        .startsWith("match.operation is not a narrowing a preset accepts");
    assertThat(list("grow")).isEmpty();
  }

  @Test
  void a_preset_is_unknown_on_a_provider_this_simulator_does_not_know() {
    assertThat(
            post("no-such-psp", "/scenarios/presets", Map.of("preset", "cancel-hold-success"))
                .getStatusCode()
                .value())
        .isEqualTo(404);
  }

  @Test
  void a_scenario_rule_this_test_run_never_held_refuses_the_rule_and_not_the_provider() {
    ResponseEntity<Map<String, Object>> refused = get("grow", "/scenarios/scn_never_written");

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(messageOf(refused))
        .isEqualTo(
            "no scenario rule with that scenarioId in test run control-api-it of provider grow");
  }

  @Test
  void deleting_a_scenario_rule_that_was_never_written_refuses_the_rule_not_the_provider() {
    ResponseEntity<Map<String, Object>> refused =
        delete("grow", "/scenarios/scn_never_written", "control-api-it");

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(messageOf(refused))
        .isEqualTo(
            "no scenario rule with that scenarioId in test run control-api-it of provider grow");
  }

  @Test
  void completing_a_hosted_page_no_process_backs_refuses_the_process_not_the_provider() {
    ResponseEntity<Map<String, Object>> refused =
        post(
            "grow",
            "/hosted/no-such-process/complete",
            Map.of("testRunId", "control-api-it", "outcome", "ABANDONED"));

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(messageOf(refused))
        .isEqualTo(
            "no hosted payment process with that providerObjectId in test run control-api-it of"
                + " provider grow");
  }

  @Test
  void reading_an_object_this_test_run_never_held_refuses_the_object_and_not_the_provider() {
    ResponseEntity<Map<String, Object>> refused = get("grow", "/state/no-such-object");

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(messageOf(refused))
        .isEqualTo(
            "no provider object with that providerObjectId in test run control-api-it of provider"
                + " grow");
  }

  @Test
  void retrying_a_webhook_job_that_was_never_scheduled_refuses_the_job_not_the_provider() {
    ResponseEntity<Map<String, Object>> refused =
        post(
            "grow",
            "/webhooks",
            Map.of(
                "testRunId",
                "control-api-it",
                "operation",
                "RETRY",
                "webhookJobId",
                "whk_never_scheduled"));

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(messageOf(refused))
        .isEqualTo(
            "no webhook job with that webhookJobId in test run control-api-it of provider grow");
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_still_refused_as_an_unknown_provider() {
    assertThat(messageOf(get("no-such-psp", "/state/no-such-object")))
        .isEqualTo(
            "unknown provider; this simulator knows [autra, azericard, grow, stripe, tranzila, worldline]");
    assertThat(messageOf(get("no-such-psp", "/scenarios/scn_never_written")))
        .isEqualTo(
            "unknown provider; this simulator knows [autra, azericard, grow, stripe, tranzila, worldline]");
    assertThat(
            messageOf(
                post(
                    "no-such-psp",
                    "/hosted/no-such-process/complete",
                    Map.of("testRunId", "control-api-it", "outcome", "ABANDONED"))))
        .isEqualTo(
            "unknown provider; this simulator knows [autra, azericard, grow, stripe, tranzila, worldline]");
  }

  @Test
  void no_refusal_of_a_missing_thing_repeats_the_identifier_the_caller_supplied() {
    assertThat(messageOf(get("grow", "/state/pan-4111111111111111")))
        .doesNotContain("4111111111111111");
    assertThat(messageOf(get("grow", "/scenarios/scn_4111111111111111")))
        .doesNotContain("4111111111111111");
  }

  private String messageOf(ResponseEntity<Map<String, Object>> response) {
    return String.valueOf(response.getBody().get("message"));
  }

  private static Map<String, Object> aDecline() {
    return Map.of(
        "testRunId",
        "control-api-it",
        "priority",
        100,
        "match",
        Map.of("accountId", "merchant-1", "configuration", Map.of("amount", "1000")),
        "action",
        Map.of("type", "TERMINAL_FAILURE", "providerCode", "813"));
  }

  private static Map<String, Object> scenarioActing(Map<String, Object> action) {
    Map<String, Object> body = new HashMap<>();
    body.put("testRunId", "control-api-it");
    body.put("action", action);
    return body;
  }

  private static Map<String, Object> scenarioWithout(String field) {
    Map<String, Object> body = new HashMap<>(aDecline());
    body.remove(field);
    return body;
  }

  private String scenarioBadRequestMessage(Map<String, Object> body) {
    return badRequestMessage(post("grow", "/scenarios", body));
  }

  private String createdScenarioId(Map<String, Object> body) {
    ResponseEntity<Map<String, Object>> created = post("grow", "/scenarios", body);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    return String.valueOf(created.getBody().get("scenarioId"));
  }

  private List<Map<String, Object>> list(String provider) {
    return http.get()
        .uri("/__simulator/" + provider + "/scenarios?testRunId=control-api-it")
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private ResponseEntity<Map<String, Object>> get(String provider, String path) {
    String uri = "/__simulator/" + provider + path;
    return http.get()
        .uri(uri.contains("?") ? uri : uri + "?testRunId=control-api-it")
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<Map<String, Object>> delete(
      String provider, String path, String testRunId) {
    return http.method(HttpMethod.DELETE)
        .uri("/__simulator/" + provider + path + "?testRunId=" + testRunId)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<Map<String, Object>> post(
      String provider, String path, Map<String, Object> body) {
    return exchange(HttpMethod.POST, "/__simulator/" + provider + path, body);
  }

  private ResponseEntity<Map<String, Object>> configureWithClock(Map<String, Object> clock) {
    Map<String, Object> body = new HashMap<>();
    body.put("testRunId", "control-api-it");
    body.put("clock", clock);
    return put("grow", body);
  }

  private static MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", "control-api-page");
    form.add("userId", "control-api-user");
    form.add("chargeType", "2");
    form.add("sum", "120.50");
    form.add("description", "A hold");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return form;
  }

  private static Map<String, Object> configuring(String accountId, String userId, String pageCode) {
    Map<String, Object> body = new HashMap<>();
    body.put("testRunId", "control-api-it");
    body.put(
        "accounts",
        List.of(
            Map.of(
                "accountId",
                accountId,
                "configuration",
                Map.of("userId", userId, "pageCode", pageCode))));
    return body;
  }

  private static Map<String, Object> configuringWithATarget(
      String accountId, String userId, String pageCode, String url) {
    Map<String, Object> body = configuring(accountId, userId, pageCode);
    body.put("webhooks", List.of(Map.of("accountId", accountId, "url", url)));
    return body;
  }

  private String theAnswerTo(MultiValueMap<String, Object> form) {
    ResponseEntity<byte[]> created =
        http.post()
            .uri("/api/light/server/1.0/createPaymentProcess")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(form)
            .retrieve()
            .toEntity(byte[].class);
    return new String(created.getBody(), StandardCharsets.UTF_8);
  }

  private static List<Object> nothingAtAll() {
    return Collections.singletonList(null);
  }

  private static Map<String, Object> configWith(String field, Object value) {
    Map<String, Object> body = new HashMap<>();
    body.put(field, value);
    return body;
  }

  private String badRequestMessage(ResponseEntity<Map<String, Object>> response) {
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    return String.valueOf(response.getBody().get("message"));
  }

  private ResponseEntity<Map<String, Object>> put(String provider, Map<String, Object> body) {
    return exchange(HttpMethod.PUT, "/__simulator/" + provider + "/config", body);
  }

  private ResponseEntity<Map<String, Object>> post(String provider, Map<String, Object> body) {
    return exchange(HttpMethod.POST, "/__simulator/" + provider + "/reset", body);
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
