package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.ui.Disclosure;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class AutraAuditTest {

  private static final TestRunId RUN = TestRunId.of("autra-audit");
  private static final TestRunId THE_RUN_NOTHING_CONFIGURES = TestRunId.of("autra-audit-default");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final TestRunId ELSEWHERE = TestRunId.of("autra-audit-elsewhere");
  private static final String SHARED_CLIENT_ID = "shared-client";
  private static final String SHARED_CLIENT_SECRET = "shared-secret";

  private final AutraDoubles.Merchants merchants =
      new AutraDoubles.Merchants().with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT));
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();

  private final AutraApi api =
      AutraWiring.api(
          merchants,
          sessions,
          payments,
          audit,
          THE_RUN_NOTHING_CONFIGURES,
          NOW,
          new ScenarioEngine(rules),
          new AutraDoubles.Runs(THE_RUN_NOTHING_CONFIGURES));

  private String aBearer() {
    return AutraTokenTest.theTokenIn(
        api.token(
            AutraWire.tokenCall(
                AutraFields.CLIENT_CREDENTIALS,
                AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT))));
  }

  private AutraApiResponse initialize(String bearer) {
    return api.initialize(
        AutraWire.call(
            AutraApiController.INITIALIZE,
            AutraWire.bearer(bearer),
            "{\"documentId\":\"" + DOCUMENT + "\"}"));
  }

  @Test
  void
      the_credential_resolves_the_token_call_to_the_run_that_configures_it_and_not_to_the_default() {
    AutraApiResponse minted =
        api.token(
            AutraWire.tokenCall(
                AutraFields.CLIENT_CREDENTIALS,
                AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT)));

    assertThat(minted.httpStatus()).isEqualTo(200);
    assertThat(minted.testRunId()).isEqualTo(RUN);
    assertThat(audit.all())
        .allSatisfy(record -> assertThat(record.testRunId()).isEqualTo(RUN))
        .allSatisfy(
            record ->
                assertThat(record.resolutionRule())
                    .isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS));
  }

  @Test
  void every_row_of_an_exchange_one_merchant_answered_for_names_that_merchant_and_not_a_blank() {
    api.token(
        AutraWire.tokenCall(
            AutraFields.CLIENT_CREDENTIALS,
            AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT)));

    assertThat(audit.all()).isNotEmpty();
    assertThat(audit.all()).allSatisfy(record -> assertThat(record.accountId()).isEqualTo(ACCOUNT));
  }

  @Test
  void every_row_of_an_exchange_no_merchant_answered_for_names_no_account_at_all() {
    api.token(
        AutraWire.tokenCall(
            AutraFields.CLIENT_CREDENTIALS, AutraWire.basic("client-nobody", "secret-nobody")));

    assertThat(audit.all()).isNotEmpty();
    assertThat(audit.all()).allSatisfy(record -> assertThat(record.accountId()).isNull());
  }

  @Test
  void an_acquiring_call_a_bearer_opened_names_the_merchant_the_bearer_was_issued_for() {
    initialize(aBearer());

    assertThat(audit.all()).isNotEmpty();
    assertThat(audit.all()).allSatisfy(record -> assertThat(record.accountId()).isEqualTo(ACCOUNT));
  }

  @Test
  void the_bearer_resolves_an_acquiring_call_to_the_run_it_was_issued_in_and_not_to_the_default() {
    AutraApiResponse opened = initialize(aBearer());

    assertThat(opened.httpStatus()).isEqualTo(200);
    assertThat(opened.testRunId()).isEqualTo(RUN);
    assertThat(sessions.session(RUN, theSessionIn(opened))).isPresent();
  }

  @Test
  void a_credential_two_runs_share_mints_a_token_for_the_merchant_of_the_run_the_header_named() {
    AutraDoubles.Merchants shared =
        new AutraDoubles.Merchants()
            .with(aMerchantOf(RUN, "merchant-a", DOCUMENT))
            .with(aMerchantOf(ELSEWHERE, "merchant-b", "11111111111"));
    AutraDoubles.Runs runs = new AutraDoubles.Runs(THE_RUN_NOTHING_CONFIGURES);
    runs.register(RUN);
    runs.register(ELSEWHERE);
    AutraApi sharing =
        AutraWiring.api(
            shared,
            sessions,
            payments,
            audit,
            THE_RUN_NOTHING_CONFIGURES,
            NOW,
            new ScenarioEngine(rules),
            runs);

    AutraApiResponse minted = sharing.token(theTokenCallNaming(ELSEWHERE));

    assertThat(minted.testRunId()).isEqualTo(ELSEWHERE);
    assertThat(sessions.accessToken(AutraTokenTest.theTokenIn(minted)).orElseThrow())
        .satisfies(
            token -> {
              assertThat(token.testRunId()).isEqualTo(ELSEWHERE);
              assertThat(token.accountId()).isEqualTo("merchant-b");
            });
  }

  private static AutraMerchant aMerchantOf(TestRunId run, String accountId, String documentId) {
    return AutraDoubles.merchant(
        run,
        accountId,
        documentId,
        Map.of(
            AutraMerchant.CLIENT_ID_PROPERTY,
            SHARED_CLIENT_ID,
            AutraMerchant.CLIENT_SECRET_PROPERTY,
            SHARED_CLIENT_SECRET));
  }

  private static AutraApiCall theTokenCallNaming(TestRunId run) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(
        AutraFields.AUTHORIZATION,
        List.of(AutraWire.basic(SHARED_CLIENT_ID, SHARED_CLIENT_SECRET)));
    headers.put(TestRunHeader.NAME, List.of(run.value()));
    return AutraApiCall.of(
        AutraWire.POST,
        AutraApiController.OAUTH_TOKEN,
        headers,
        Map.of(AutraFields.GRANT_TYPE, List.of(AutraFields.CLIENT_CREDENTIALS)),
        "");
  }

  @Test
  void a_url_a_run_bound_as_its_own_resolves_a_token_call_to_that_run_without_any_header() {
    AutraDoubles.Runs runs = new AutraDoubles.Runs(THE_RUN_NOTHING_CONFIGURES);
    runs.register(RUN);
    AutraApi aliased =
        AutraWiring.api(
            merchants,
            sessions,
            payments,
            audit,
            THE_RUN_NOTHING_CONFIGURES,
            NOW,
            new ScenarioEngine(rules),
            runs);

    AutraApiResponse minted =
        aliased.token(
            new AutraApiCall(
                AutraWire.POST,
                InboundRoute.aliased(
                    AutraApiController.OAUTH_TOKEN,
                    new RouteAlias(
                        AutraApi.AUTRA,
                        RUN,
                        new RouteAliasKey("autra.example", AutraApiController.OAUTH_TOKEN),
                        "https://autra.example" + AutraApiController.OAUTH_TOKEN,
                        AutraApiController.OAUTH_TOKEN)),
                Map.of(
                    AutraFields.AUTHORIZATION,
                    List.of(AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT))),
                Map.of(AutraFields.GRANT_TYPE, List.of(AutraFields.CLIENT_CREDENTIALS)),
                ""));

    assertThat(minted.httpStatus()).isEqualTo(200);
    assertThat(minted.testRunId()).isEqualTo(RUN);
    assertThat(theOutbound(AutraApiController.OAUTH_TOKEN).resolutionRule())
        .isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
  }

  @Test
  void an_authorization_header_shorter_than_the_bearer_prefix_is_answered_401_and_never_raises() {
    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                Map.of(AutraFields.AUTHORIZATION, List.of("Basic")),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(401);
  }

  @Test
  void every_row_this_provider_writes_carries_an_audit_id_of_the_shape_the_trail_draws_them_in() {
    initialize(aBearer());

    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(record -> assertThat(record.auditId()).matches("aud_[0-9a-f]{24}"));
    assertThat(audit.all()).extracting(AuditRecord::auditId).doesNotHaveDuplicates();
  }

  @Test
  void a_json_answer_is_recorded_with_the_media_type_it_put_on_the_wire() {
    initialize(aBearer());

    assertThat(theOutbound())
        .satisfies(
            record -> {
              assertThat(record.sanitizedHeaders())
                  .containsEntry("Content-Type", List.of(AutraFields.JSON));
              assertThat(record.httpStatus()).isEqualTo(200);
              assertThat(record.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
              assertThat(record.method()).isEqualTo(AutraWire.POST);
              assertThat(record.path()).isEqualTo(AutraApiController.INITIALIZE);
            });
  }

  @Test
  void an_answer_a_server_error_replaced_is_recorded_under_the_gateways_own_media_type() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.INITIALIZE),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    initialize(aBearer());

    assertThat(theOutbound())
        .satisfies(
            record -> {
              assertThat(record.sanitizedHeaders())
                  .containsExactly(Map.entry("Content-Type", List.of("text/plain; charset=utf-8")));
              assertThat(record.httpStatus()).isEqualTo(502);
              assertThat(record.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
            });
  }

  @Test
  void the_rule_that_decided_a_call_is_named_on_the_row_that_recorded_the_answer() {
    ScenarioRule written =
        rules.add(
            new NewScenarioRule(
                AutraApi.AUTRA,
                RUN,
                10,
                ScenarioMatch.anyRequest().naming(AutraOperations.INITIALIZE),
                new ScenarioAction.FixedSuccess(),
                null));

    initialize(aBearer());

    assertThat(theOutbound().scenarioId()).isEqualTo(written.scenarioId());
  }

  @Test
  void a_call_no_rule_decided_names_no_rule_on_the_row_that_recorded_the_answer() {
    initialize(aBearer());

    assertThat(theOutbound().scenarioId()).isNull();
  }

  @Test
  void
      a_trail_row_and_the_header_list_a_screen_builds_from_it_keep_the_twelve_headers_a_caller_sent_in_the_order_it_sent_them() {
    Map<String, List<String>> sent = theTwelveHeadersAround(aBearer());

    api.initialize(
        AutraWire.call(
            AutraApiController.INITIALIZE, sent, "{\"documentId\":\"" + DOCUMENT + "\"}"));

    AuditRecord inbound = theInbound(AutraApiController.INITIALIZE);
    assertThat(sent).hasSize(12);
    assertThat(inbound.sanitizedHeaders().keySet())
        .as(
            "twelve headers have 479001600 orders, so a copy carrying no order of its own landing"
                + " on the order the caller sent is not something one run does by chance")
        .containsExactlyElementsOf(sent.keySet());
    assertThat(Disclosure.of(inbound.sanitizedHeaders(), false))
        .as("the trail screen lists the headers of that row in the same order")
        .extracting(Disclosure.Field::name)
        .containsExactlyElementsOf(sent.keySet());
  }

  @Test
  void a_call_hands_its_headers_and_its_query_over_as_a_sequenced_map_no_caller_can_write_to() {
    AutraApiCall call =
        AutraWire.get(
            AutraApiController.INITIALIZE,
            theTwelveHeadersAround("a-token"),
            AutraWire.query("first", "1", "second", "2"));

    assertThat(call.headers()).isInstanceOf(SequencedMap.class);
    assertThat(call.query()).isInstanceOf(SequencedMap.class);
    assertThatThrownBy(() -> call.headers().put("Extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> call.query().put("extra", List.of("value")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static Map<String, List<String>> theTwelveHeadersAround(String bearer) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(AutraFields.AUTHORIZATION, List.of(AutraFields.BEARER + bearer));
    headers.put("Accept", List.of("application/json"));
    headers.put("Accept-Encoding", List.of("gzip"));
    headers.put("Accept-Language", List.of("pt-BR"));
    headers.put("Cache-Control", List.of("no-cache"));
    headers.put("Connection", List.of("keep-alive"));
    headers.put("Content-Type", List.of("application/json"));
    headers.put("Host", List.of("api.autra.example"));
    headers.put("User-Agent", List.of("merchant-integration/1.0"));
    headers.put("X-Correlation-Id", List.of("corr-1"));
    headers.put("X-Forwarded-For", List.of("203.0.113.7"));
    headers.put("X-Request-Id", List.of("req-1"));
    return headers;
  }

  private AuditRecord theInbound(String path) {
    return audit.all().stream()
        .filter(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
        .filter(record -> path.equals(record.path()))
        .reduce((first, second) -> second)
        .orElseThrow();
  }

  private AuditRecord theOutbound() {
    return theOutbound(AutraApiController.INITIALIZE);
  }

  private AuditRecord theOutbound(String path) {
    return audit.all().stream()
        .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .filter(record -> path.equals(record.path()))
        .reduce((first, second) -> second)
        .orElseThrow();
  }

  private static String theSessionIn(AutraApiResponse response) {
    return AutraJsonReader.objectIn(AutraWire.bodyOf(response))
        .orElseThrow()
        .text(AutraFields.TRANSACTIONAL_TOKEN)
        .orElseThrow();
  }
}
