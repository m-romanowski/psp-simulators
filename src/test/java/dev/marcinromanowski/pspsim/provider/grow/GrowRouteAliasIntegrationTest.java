package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.ProviderTraffic;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GrowRouteAliasIntegrationTest extends PostgresBackedTest {

  private static final TestRunId ALIASED_RUN = TestRunId.of("grow-route-alias-it");

  private static final TestRunId CREDENTIALS_RUN = TestRunId.of("grow-route-alias-it-credentials");

  private static final String ALIASED_USER = "grow-route-alias-user";

  private static final String ALIASED_PAGE = "grow-route-alias-page";

  private static final String OTHER_USER = "grow-route-alias-other-user";

  private static final String OTHER_PAGE = "grow-route-alias-other-page";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  private static final String PRODUCTION_PATH = "/merchant-gateway/1.0/merchant-1/payments";

  @LocalServerPort private int port;

  @Autowired private AuditTrail audit;

  @Autowired private GrowPaymentProcesses processes;

  @Autowired private ObjectMapper json;

  private final Map<String, TestRunId> bound = new LinkedHashMap<>();

  private RestClient http;

  @BeforeEach
  void configureTwoTestRunsWithCredentialsOfTheirOwn() {
    http = RestClient.create("http://localhost:" + port);
    configure(ALIASED_RUN, ALIASED_USER, ALIASED_PAGE, List.of());
    configure(CREDENTIALS_RUN, OTHER_USER, OTHER_PAGE, List.of());
    clearTheTrail();
  }

  @AfterEach
  void forgetWhatTheseTestsConfigured() {
    bound.forEach((url, testRunId) -> releasing(testRunId, url));
    bound.clear();
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("scope", "ALL"))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void a_request_on_a_configured_url_is_served_by_the_route_beside_it_and_lands_in_that_test_run() {
    bind(ALIASED_RUN, "rewrite.provider.example" + PRODUCTION_PATH, CREATE);

    ResponseEntity<byte[]> answered =
        post(
            PRODUCTION_PATH,
            "rewrite.provider.example",
            null,
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    Map<String, Object> data = dataOf(answered);
    assertThat(processes.findByProcessId(ALIASED_RUN, (String) data.get("processId"))).isPresent();
    AuditRecord inbound = inboundOf(ALIASED_RUN);
    assertThat(inbound.resolutionRule()).isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
    assertThat(inbound.path()).isEqualTo("http://rewrite.provider.example" + PRODUCTION_PATH);
  }

  @Test
  void a_production_url_nothing_bound_reaches_no_route_of_this_simulator() {
    ResponseEntity<byte[]> unbound =
        post(
            "/merchant-gateway/1.0/merchant-unbound/payments",
            "unbound.provider.example",
            null,
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(unbound.getStatusCode().value()).isEqualTo(404);
    assertThat(auditOf(ALIASED_RUN)).isEmpty();
  }

  @Test
  void the_same_request_lands_in_the_run_its_credentials_belong_to_until_an_alias_binds_its_url() {
    MultiValueMap<String, Object> hold = holdSignedBy(OTHER_USER, OTHER_PAGE);

    post(CREATE, "outranks.provider.example", null, hold);

    assertThat(inboundOf(CREDENTIALS_RUN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
    assertThat(auditOf(ALIASED_RUN)).isEmpty();

    clearTheTrail();
    bind(ALIASED_RUN, "outranks.provider.example" + CREATE, CREATE);

    ResponseEntity<byte[]> aliased = post(CREATE, "outranks.provider.example", null, hold);

    assertThat(inboundOf(ALIASED_RUN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
    assertThat(auditOf(CREDENTIALS_RUN)).isEmpty();
    assertThat(new String(aliased.getBody(), UTF_8)).contains("\"status\":0", "\"id\":701");
  }

  @Test
  void a_request_that_names_its_test_run_is_routed_by_the_header_although_an_alias_served_it() {
    bind(ALIASED_RUN, "header.provider.example" + PRODUCTION_PATH, CREATE);

    ResponseEntity<byte[]> answered =
        post(
            PRODUCTION_PATH,
            "header.provider.example",
            CREDENTIALS_RUN.value(),
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    assertThat(inboundOf(CREDENTIALS_RUN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.TEST_HEADER);
    assertThat(auditOf(ALIASED_RUN)).isEmpty();
  }

  @Test
  void a_header_naming_a_run_that_does_not_exist_fails_although_an_alias_bound_the_url() {
    bind(ALIASED_RUN, "typo.provider.example" + PRODUCTION_PATH, CREATE);

    ResponseEntity<byte[]> answered =
        post(
            PRODUCTION_PATH,
            "typo.provider.example",
            "grow-route-alias-it-typo",
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    assertThat(auditOf(ALIASED_RUN)).isEmpty();
    assertThat(auditOf(CREDENTIALS_RUN)).isEmpty();
    assertThat(inboundOf(TestRunId.UNKNOWN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.TEST_HEADER_UNMATCHED);
  }

  @Test
  void resetting_a_test_run_leaves_the_urls_it_answers_on_bound() {
    bind(ALIASED_RUN, "survives.provider.example" + PRODUCTION_PATH, CREATE);
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", ALIASED_RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();

    ResponseEntity<byte[]> answered =
        post(
            PRODUCTION_PATH,
            "survives.provider.example",
            null,
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    assertThat(inboundOf(ALIASED_RUN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
  }

  @Test
  void a_url_another_test_run_already_answers_on_is_refused_by_naming_the_field_and_that_run() {
    bind(ALIASED_RUN, "taken.provider.example" + PRODUCTION_PATH, CREATE);

    ResponseEntity<String> refused =
        configuring(
            CREDENTIALS_RUN,
            List.of(
                Map.of("from", "https://taken.provider.example" + PRODUCTION_PATH, "to", CREATE)));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refused.getBody()).contains("routeAliases[0].from").contains(ALIASED_RUN.value());
  }

  @Test
  void the_next_build_binds_the_production_url_once_the_finished_one_has_released_it() {
    String url = "https://ci.provider.example" + PRODUCTION_PATH;
    bind(ALIASED_RUN, "ci.provider.example" + PRODUCTION_PATH, CREATE);

    assertThat(
            configuring(CREDENTIALS_RUN, List.of(Map.of("from", url, "to", CREATE)))
                .getStatusCode()
                .value())
        .isEqualTo(422);

    ResponseEntity<String> released = releasing(ALIASED_RUN, url);

    assertThat(released.getStatusCode().value()).isEqualTo(200);
    assertThat(released.getBody())
        .contains("\"releasedRouteAlias\":\"" + url + "\"")
        .contains("\"configuredRouteAliases\":[]");
    assertThat(
            configuring(CREDENTIALS_RUN, List.of(Map.of("from", url, "to", CREATE)))
                .getStatusCode()
                .value())
        .isEqualTo(200);

    clearTheTrail();
    post(PRODUCTION_PATH, "ci.provider.example", null, holdSignedBy(OTHER_USER, OTHER_PAGE));

    assertThat(inboundOf(CREDENTIALS_RUN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
    assertThat(auditOf(ALIASED_RUN)).isEmpty();
  }

  @Test
  void a_released_url_is_answered_by_nothing_until_a_test_run_binds_it_again() {
    String url = "https://freed.provider.example" + PRODUCTION_PATH;
    bind(ALIASED_RUN, "freed.provider.example" + PRODUCTION_PATH, CREATE);

    releasing(ALIASED_RUN, url);

    ResponseEntity<byte[]> unbound =
        post(
            PRODUCTION_PATH,
            "freed.provider.example",
            null,
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(unbound.getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void no_test_run_releases_a_url_another_one_holds_and_the_call_says_it_found_nothing() {
    String url = "https://held.provider.example" + PRODUCTION_PATH;
    bind(ALIASED_RUN, "held.provider.example" + PRODUCTION_PATH, CREATE);

    ResponseEntity<String> refused = releasing(CREDENTIALS_RUN, url);

    assertThat(refused.getStatusCode().value()).isEqualTo(404);
    assertThat(refused.getBody()).contains("from must name a URL the named test run");

    clearTheTrail();
    post(PRODUCTION_PATH, "held.provider.example", null, holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(inboundOf(ALIASED_RUN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
  }

  @Test
  void a_release_that_names_no_url_is_a_bad_request_rather_than_a_run_emptied_of_its_urls() {
    String url = "https://named.provider.example" + PRODUCTION_PATH;
    bind(ALIASED_RUN, "named.provider.example" + PRODUCTION_PATH, CREATE);

    ResponseEntity<String> rejected = releasing(ALIASED_RUN, null);

    assertThat(rejected.getStatusCode().value()).isEqualTo(400);
    assertThat(releasing(ALIASED_RUN, url).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void
      a_url_bound_to_a_route_this_simulator_answers_nothing_on_is_answered_404_when_it_is_called() {
    bind(ALIASED_RUN, "unserved.provider.example" + PRODUCTION_PATH, "/v9/no-such-route");

    ResponseEntity<byte[]> answered =
        post(
            PRODUCTION_PATH,
            "unserved.provider.example",
            null,
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(answered.getStatusCode().value()).isEqualTo(404);
    assertThat(auditOf(ALIASED_RUN)).isEmpty();
  }

  @Test
  void removing_the_account_of_a_test_run_leaves_the_urls_it_answers_on_bound() {
    bind(ALIASED_RUN, "kept.provider.example" + PRODUCTION_PATH, CREATE);

    ResponseEntity<String> removed =
        http.method(HttpMethod.DELETE)
            .uri("/__simulator/grow/config/accounts/merchant-1?testRunId=" + ALIASED_RUN.value())
            .retrieve()
            .onStatus(status -> true, (sent, received) -> {})
            .toEntity(String.class);

    assertThat(removed.getStatusCode().value()).isEqualTo(200);
    clearTheTrail();
    ResponseEntity<byte[]> answered =
        post(
            PRODUCTION_PATH,
            "kept.provider.example",
            null,
            holdSignedBy(ALIASED_USER, ALIASED_PAGE));

    assertThat(answered.getStatusCode().value()).isEqualTo(200);
    assertThat(inboundOf(ALIASED_RUN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
  }

  @Test
  void a_url_that_is_not_absolute_is_refused_by_naming_the_field_rather_than_bound_to_nothing() {
    ResponseEntity<String> refused =
        configuring(ALIASED_RUN, List.of(Map.of("from", PRODUCTION_PATH, "to", CREATE)));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refused.getBody()).contains("routeAliases[0].from must be an absolute URL");
  }

  @Test
  void a_route_of_the_control_api_is_refused_as_the_target_of_an_alias() {
    ResponseEntity<String> refused =
        configuring(
            ALIASED_RUN,
            List.of(
                Map.of(
                    "from",
                    "https://reset.provider.example" + PRODUCTION_PATH,
                    "to",
                    "/__simulator/grow/reset")));

    assertThat(refused.getStatusCode().value()).isEqualTo(422);
    assertThat(refused.getBody()).contains("routeAliases[0].to must name a provider route");
  }

  @Test
  void a_configuration_call_answers_how_many_urls_this_test_run_now_answers_on() {
    Map<String, Object> answered =
        configure(
            ALIASED_RUN,
            ALIASED_USER,
            ALIASED_PAGE,
            List.of(
                Map.of(
                    "from", "https://counted.provider.example" + PRODUCTION_PATH, "to", CREATE)));

    assertThat(answered).containsEntry("configuredRouteAliases", 1);
  }

  private void bind(TestRunId testRunId, String hostAndPath, String to) {
    String user = testRunId.equals(ALIASED_RUN) ? ALIASED_USER : OTHER_USER;
    String page = testRunId.equals(ALIASED_RUN) ? ALIASED_PAGE : OTHER_PAGE;
    configure(testRunId, user, page, List.of(Map.of("from", "https://" + hostAndPath, "to", to)));
    bound.put("https://" + hostAndPath, testRunId);
  }

  private Map<String, Object> configure(
      TestRunId testRunId, String userId, String pageCode, List<Map<String, Object>> aliases) {
    return http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                testRunId.value(),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        "merchant-1",
                        "configuration",
                        Map.of("userId", userId, "pageCode", pageCode))),
                "routeAliases",
                aliases))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<String> configuring(
      TestRunId testRunId, List<Map<String, Object>> aliases) {
    ResponseEntity<String> answered =
        http.put()
            .uri("/__simulator/grow/config")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", testRunId.value(), "routeAliases", aliases))
            .retrieve()
            .onStatus(status -> true, (sent, received) -> {})
            .toEntity(String.class);
    if (answered.getStatusCode().is2xxSuccessful()) {
      for (Map<String, Object> alias : aliases) {
        bound.put(String.valueOf(alias.get("from")), testRunId);
      }
    }
    return answered;
  }

  private ResponseEntity<String> releasing(TestRunId testRunId, @Nullable String from) {
    return http.method(HttpMethod.DELETE)
        .uri(
            uri -> {
              uri.path("/__simulator/grow/config/route-aliases")
                  .queryParam("testRunId", testRunId.value());
              if (from != null) {
                uri.queryParam("from", from);
              }
              return uri.build();
            })
        .retrieve()
        .onStatus(status -> true, (sent, received) -> {})
        .toEntity(String.class);
  }

  private void clearTheTrail() {
    audit.clear(GrowLightApi.GROW, ALIASED_RUN);
    audit.clear(GrowLightApi.GROW, CREDENTIALS_RUN);
    audit.clear(GrowLightApi.GROW, TestRunId.UNKNOWN);
  }

  private List<AuditRecord> auditOf(TestRunId testRunId) {
    return ProviderTraffic.in(audit.of(GrowLightApi.GROW, testRunId));
  }

  private AuditRecord inboundOf(TestRunId testRunId) {
    return auditOf(testRunId).stream()
        .filter(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
        .findFirst()
        .orElseThrow();
  }

  private ResponseEntity<byte[]> post(
      String path, String host, @Nullable String testRunId, MultiValueMap<String, Object> form) {
    RestClient.RequestBodySpec request =
        http.post()
            .uri(path)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .header(RouteAliasGrammar.FORWARDED_HOST, host);
    if (testRunId != null) {
      request = request.header(TestRunHeader.NAME, testRunId);
    }
    return request
        .body(form)
        .retrieve()
        .onStatus(status -> true, (sent, received) -> {})
        .toEntity(byte[].class);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> dataOf(ResponseEntity<byte[]> response) {
    Map<String, Object> body =
        json.readValue(
            GrowWireText.readable(new String(response.getBody(), UTF_8)),
            new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
    return (Map<String, Object>) body.get("data");
  }

  private static MultiValueMap<String, Object> holdSignedBy(String userId, String pageCode) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", pageCode);
    form.add("userId", userId);
    form.add("chargeType", "2");
    form.add("sum", "120.50");
    form.add("description", "A hold an aliased URL created");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return form;
  }
}
