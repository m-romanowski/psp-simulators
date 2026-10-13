package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredReset;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookCall;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowACompletedHostedPageLeaves;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class RefusedActionsIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "refused-actions-it";

  private static final TestRunId TEST_RUN = TestRunId.of(RUN);

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final String SIMULATOR = "/__simulator/grow";

  private static final String CONFIG = SIMULATOR + "/config";

  private static final String A_URL_NOBODY_BOUND = "https://never.bound.example/api/create";

  private static final String PAYMENT_METHODS = SIMULATOR + "/fixtures/payment-methods";

  private static final String A_SECRET_A_HARNESS_WOULD_RECOGNISE = "grow-user-8f31c0b4a7";

  private static final String A_PAGE_CODE_A_HARNESS_WOULD_RECOGNISE = "grow-page-51de99";

  private static final String A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED = "whsec_9e2b71a4c6d8";

  private static final String THE_CONFIGURATION =
      ConfigurationAdmission.THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD;

  private static final String THE_ACCOUNT =
      ConfigurationAdmission.THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET;

  private static final String THE_ROUTE_ALIAS =
      ConfigurationAdmission.THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE;

  private static final String THE_FIXTURE_TO_SEED =
      PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED;

  private static final String THE_FIXTURE_TO_FORGET =
      PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET;

  private static final String THE_SCENARIO_RULE_TO_HOLD =
      ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD;

  private static final String THE_SCENARIO_RULE_TO_FORGET =
      ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_FORGET;

  private static final String THE_CALLBACK =
      DeclaredWebhookCall.THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE;

  @LocalServerPort private int port;

  @Autowired private AuditTrail trail;

  @Autowired private ProviderRegistry providers;

  @Value("${simulator.default-test-run-id}")
  private String theRunThisApiAppliesToABodyItRead;

  private RestClient http;

  @BeforeEach
  void emptyTheRun() {
    http = RestClient.create("http://localhost:" + port);
    reset();
  }

  @AfterEach
  void emptyItAgain() {
    send(HttpMethod.PUT, CONFIG, Map.of("testRunId", RUN, "accounts", List.of()));
    reset();
  }

  static Stream<Arguments> theRefusingArmsOfTheControlApi() {
    return Stream.of(
        Arguments.of(
            "a reset naming a scope this simulator does not publish",
            HttpMethod.POST,
            SIMULATOR + "/reset",
            Map.of("testRunId", RUN, "scope", "EVERYTHING"),
            SIMULATOR + "/reset",
            400,
            RefusalSubject.noObjectWasNamed(
                DeclaredReset.THE_RESET_IT_ASKED_THIS_PROVIDER_TO_PERFORM)),
        Arguments.of(
            "a configuration holding nothing where a configured value belongs",
            HttpMethod.PUT,
            CONFIG,
            aConfigurationWithoutAValue(),
            CONFIG,
            400,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "a configuration this provider cannot use",
            HttpMethod.PUT,
            CONFIG,
            Map.of(
                "testRunId",
                RUN,
                "accounts",
                List.of(Map.of("accountId", "merchant-1", "configuration", Map.of()))),
            CONFIG,
            422,
            RefusalSubject.noObjectWasNamed(THE_CONFIGURATION)),
        Arguments.of(
            "forgetting an account no configuration of this run named",
            HttpMethod.DELETE,
            CONFIG + "/accounts/merchant-9?testRunId=" + RUN,
            null,
            CONFIG + "/accounts/merchant-9",
            404,
            RefusalSubject.noObjectWasNamed(THE_ACCOUNT)),
        Arguments.of(
            "releasing a route alias without naming the URL it binds",
            HttpMethod.DELETE,
            CONFIG + "/route-aliases?testRunId=" + RUN,
            null,
            CONFIG + "/route-aliases",
            400,
            RefusalSubject.noObjectWasNamed(THE_ROUTE_ALIAS)),
        Arguments.of(
            "releasing a URL this test run never bound",
            HttpMethod.DELETE,
            CONFIG + "/route-aliases?testRunId=" + RUN + "&from=" + A_URL_NOBODY_BOUND,
            null,
            CONFIG + "/route-aliases",
            404,
            RefusalSubject.noObjectWasNamed(THE_ROUTE_ALIAS)),
        Arguments.of(
            "seeding a payment method this simulator cannot read",
            HttpMethod.POST,
            PAYMENT_METHODS,
            aVisaCardOfKind("credit-card"),
            PAYMENT_METHODS,
            400,
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_TO_SEED)),
        Arguments.of(
            "seeding a payment method under a provider-native id this provider reads nothing of",
            HttpMethod.POST,
            PAYMENT_METHODS,
            aVisaCardNaming("providerIds", Map.of("token", "tok_sim_123")),
            PAYMENT_METHODS,
            422,
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_TO_SEED)),
        Arguments.of(
            "forgetting a payment method no test run seeded",
            HttpMethod.DELETE,
            PAYMENT_METHODS + "/fx_nothing?testRunId=" + RUN,
            null,
            PAYMENT_METHODS + "/fx_nothing",
            404,
            RefusalSubject.noObjectWasNamed(THE_FIXTURE_TO_FORGET)),
        Arguments.of(
            "a scenario rule leaving out a property this simulator requires",
            HttpMethod.POST,
            SIMULATOR + "/scenarios",
            Map.of("testRunId", RUN, "action", Map.of("type", "TERMINAL_FAILURE")),
            SIMULATOR + "/scenarios",
            400,
            RefusalSubject.noObjectWasNamed(THE_SCENARIO_RULE_TO_HOLD)),
        Arguments.of(
            "a scenario rule naming an action this simulator cannot perform",
            HttpMethod.POST,
            SIMULATOR + "/scenarios",
            Map.of("testRunId", RUN, "action", Map.of("type", "SOMETHING_ELSE")),
            SIMULATOR + "/scenarios",
            422,
            RefusalSubject.noObjectWasNamed(THE_SCENARIO_RULE_TO_HOLD)),
        Arguments.of(
            "a preset this simulator does not publish",
            HttpMethod.POST,
            SIMULATOR + "/scenarios/presets",
            Map.of("testRunId", RUN, "preset", "cancel-hold-succes"),
            SIMULATOR + "/scenarios/presets",
            400,
            RefusalSubject.noObjectWasNamed(THE_SCENARIO_RULE_TO_HOLD)),
        Arguments.of(
            "deleting a scenario rule that was never written",
            HttpMethod.DELETE,
            SIMULATOR + "/scenarios/scn_never_written?testRunId=" + RUN,
            null,
            SIMULATOR + "/scenarios/scn_never_written",
            404,
            RefusalSubject.noObjectWasNamed(THE_SCENARIO_RULE_TO_FORGET)),
        Arguments.of(
            "a callback naming an operation this control API does not publish",
            HttpMethod.POST,
            SIMULATOR + "/webhooks",
            Map.of("testRunId", RUN, "operation", "DELETE"),
            SIMULATOR + "/webhooks",
            400,
            RefusalSubject.noObjectWasNamed(THE_CALLBACK)),
        Arguments.of(
            "retrying a callback job that was never scheduled",
            HttpMethod.POST,
            SIMULATOR + "/webhooks",
            Map.of("testRunId", RUN, "operation", "RETRY", "webhookJobId", "whk_never_scheduled"),
            SIMULATOR + "/webhooks",
            404,
            RefusalSubject.noObjectAnsweredTo(DeclaredWebhookCall.WEBHOOK_JOB_ID)));
  }

  @ParameterizedTest(name = "{0} leaves a row carrying its verdict and naming its subject")
  @MethodSource("theRefusingArmsOfTheControlApi")
  void a_refusing_arm_of_the_control_api_leaves_one_row_naming_what_it_refused_and_why(
      String what,
      HttpMethod method,
      String uri,
      @Nullable Map<String, Object> body,
      String recordedPath,
      int status,
      RefusalSubject subject) {
    ResponseEntity<Map<String, Object>> answered = theAnswerTo(method, uri, body);
    assertThat(answered.getStatusCode().value()).isEqualTo(status);

    AuditRecord row = theRowAt(recordedPath, status);
    assertThat(row.refusal()).isEqualTo(subject);
    assertThat(row.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(row.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(row.method()).isEqualTo(method.name());
    assertThat(row.sanitizedBody().get(ActionFacts.REFUSED_BECAUSE))
        .as(
            "a tester reading the trail after a suite ran is reading for the refusal, so the row"
                + " carries the words the caller was answered with and not only the subject the"
                + " refusal named and the status it carried")
        .containsExactly(String.valueOf(theMessageIn(answered)));
  }

  private static Object theMessageIn(ResponseEntity<Map<String, Object>> answered) {
    Map<String, Object> body = answered.getBody();
    assertThat(body).as("every refusal of this control api is answered in words").isNotNull();
    return body.get("message");
  }

  @Test
  void completing_a_hosted_page_no_object_of_this_run_backs_says_so_on_the_row_it_leaves() {
    ResponseEntity<Map<String, Object>> answered =
        theAnswerTo(
            HttpMethod.POST,
            SIMULATOR + "/hosted/no-such-process/complete",
            Map.of("testRunId", RUN, "outcome", "ABANDONED"));

    assertThat(answered.getStatusCode().value()).isEqualTo(404);
    AuditRecord row = theRowAt(SIMULATOR + "/hosted/no-such-process/complete", 404);
    assertThat(row.refusal())
        .isEqualTo(RefusalSubject.noObjectAnsweredTo(HostedCompletionOutcome.PROVIDER_OBJECT_ID));
    assertThat(row.sanitizedBody())
        .as(
            "this completion is recorded by the composer both surfaces write through, so the row"
                + " says why in words a tester reads the same whether they clicked or called,"
                + " while each surface still answers its own reader in its own words")
        .containsEntry(
            TheTrailRowACompletedHostedPageLeaves.PROVIDER_OBJECT_ID, List.of("no-such-process"))
        .containsEntry(
            ActionFacts.REFUSED_BECAUSE,
            List.of(TheTrailRowACompletedHostedPageLeaves.THIS_RUN_HOLDS_NO_SUCH_HOSTED_PAGE));
    assertThat(String.valueOf(theMessageIn(answered)))
        .as("a suite is answered in the words this control api publishes for a search")
        .contains("no hosted payment process with that providerObjectId");
  }

  @Test
  void
      a_completion_this_simulator_could_not_read_records_the_step_the_caller_named_as_they_sent_it() {
    ResponseEntity<Map<String, Object>> answered =
        theAnswerTo(
            HttpMethod.POST,
            SIMULATOR + "/hosted/no-such-process/complete",
            Map.of("testRunId", RUN, "outcome", "abandoned-by-the-payer", "card", "a-card"));

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(theRowAt(SIMULATOR + "/hosted/no-such-process/complete", 400).sanitizedBody())
        .as(
            "a row about a request this simulator could not read carries what the caller sent"
                + " and not a value read out of it, because the reading is the thing that failed")
        .containsEntry(
            TheTrailRowACompletedHostedPageLeaves.OUTCOME, List.of("abandoned-by-the-payer"))
        .containsKey(ActionFacts.REFUSED_BECAUSE);
  }

  @Test
  void a_refused_account_removal_names_the_account_a_tester_asked_it_to_forget() {
    send(HttpMethod.DELETE, CONFIG + "/accounts/merchant-9?testRunId=" + RUN, null);

    assertThat(theRowAt(CONFIG + "/accounts/merchant-9", 404).sanitizedBody())
        .containsEntry("accountId", List.of("merchant-9"));
  }

  @Test
  void a_refused_scenario_delete_names_the_rule_a_tester_asked_it_to_forget() {
    send(HttpMethod.DELETE, SIMULATOR + "/scenarios/scn_never_written?testRunId=" + RUN, null);

    assertThat(theRowAt(SIMULATOR + "/scenarios/scn_never_written", 404).sanitizedBody())
        .containsEntry("scenarioId", List.of("scn_never_written"));
  }

  @Test
  void a_refused_scenario_rule_names_the_properties_this_simulator_would_not_accept() {
    send(
        HttpMethod.POST,
        SIMULATOR + "/scenarios",
        Map.of("testRunId", RUN, "action", Map.of("type", "SOMETHING_ELSE")));

    assertThat(theRowAt(SIMULATOR + "/scenarios", 422).sanitizedBody())
        .containsEntry(ActionFacts.REFUSED_PROPERTIES, List.of("action.type"));
  }

  @Test
  void a_refused_configuration_discloses_neither_a_declared_secret_nor_an_undeclared_keys_value() {
    Map<String, String> configuration = new HashMap<>();
    configuration.put("userId", A_SECRET_A_HARNESS_WOULD_RECOGNISE);
    configuration.put("pageCode", A_PAGE_CODE_A_HARNESS_WOULD_RECOGNISE);
    configuration.put("webhookSecret", A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED);

    assertThat(
            send(
                HttpMethod.PUT,
                CONFIG,
                Map.of(
                    "testRunId",
                    RUN,
                    "accounts",
                    List.of(Map.of("accountId", "merchant-1", "configuration", configuration)))))
        .isEqualTo(422);

    AuditRecord row = theRowAt(CONFIG, 422);
    assertThat(String.valueOf(row))
        .doesNotContain(A_SECRET_A_HARNESS_WOULD_RECOGNISE)
        .doesNotContain(A_PAGE_CODE_A_HARNESS_WOULD_RECOGNISE)
        .doesNotContain(A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED);
    assertThat(row.sanitizedBody())
        .containsEntry("accounts[0].configuration.userId", List.of(Redaction.REDACTED))
        .containsEntry("accounts[0].configuration.pageCode", List.of(Redaction.REDACTED))
        .containsEntry("accounts[0].configuration.webhookSecret", List.of(Redaction.REDACTED));
  }

  @Test
  void an_action_whose_test_run_id_could_not_be_read_is_recorded_under_the_reserved_run() {
    assertThat(
            send(
                HttpMethod.POST,
                SIMULATOR + "/webhooks/deliver-due",
                Map.of("testRunId", "not a valid id")))
        .isEqualTo(400);

    assertThat(theTrailOf(TestRunId.UNKNOWN))
        .anySatisfy(
            row -> {
              assertThat(row.path()).isEqualTo(SIMULATOR + "/webhooks/deliver-due");
              assertThat(row.httpStatus()).isEqualTo(400);
              assertThat(row.refusal()).isEqualTo(RefusalSubject.noObjectWasNamed(THE_CALLBACK));
            });
  }

  @Test
  void a_body_this_api_could_not_read_is_answered_in_its_own_words_and_leaves_one_row() {
    ResponseEntity<Map<String, Object>> answered =
        theAnswerToTheBytes(
            HttpMethod.PUT,
            CONFIG + "?testRunId=" + RUN,
            "{\"accounts\":[{\"accountId\":\"merchant-1\",\"configuration\":\""
                + A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED
                + "\"}]}");

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(String.valueOf(answered.getBody()))
        .as(
            "a suite parsing what this control api refuses in reads one contract, and spring's"
                + " own envelope on this route is a second shape it would have to read too")
        .doesNotContain("timestamp")
        .doesNotContain("Bad Request")
        .doesNotContain("Exception")
        .doesNotContain("dev.marcinromanowski.pspsim")
        .doesNotContain(A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED);
    assertThat(theMessageIn(answered))
        .isEqualTo(ABodyTheControlApiCouldNotRead.WHAT_A_CALLER_IS_TOLD);
    assertThat(answered.getBody())
        .containsEntry("properties", List.of("accounts[0].configuration"));

    assertThat(theTrailOf(TEST_RUN))
        .as(
            "this route names its run in the body, so a run named in its query was named nowhere"
                + " this api reads, and a row under it would show a tester reading that run a"
                + " call that may have named another run where the route would have read it")
        .noneSatisfy(seen -> assertThat(seen.path()).isEqualTo(CONFIG));

    AuditRecord row = theRowAt(CONFIG, 400, TestRunId.UNKNOWN);
    assertThat(row.refusal())
        .as(
            "a reader of the trail asks which thing was refused, and nothing read this body, so"
                + " the row names the body itself rather than an object the request never reached")
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                ABodyTheControlApiCouldNotRead.THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ));
    assertThat(row.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(row.method()).isEqualTo("PUT");
    assertThat(row.sanitizedBody())
        .containsEntry(
            ActionFacts.REFUSED_BECAUSE,
            List.of(ABodyTheControlApiCouldNotRead.WHAT_A_CALLER_IS_TOLD))
        .containsEntry(ActionFacts.REFUSED_PROPERTIES, List.of("accounts[0].configuration"));
    assertThat(String.valueOf(row))
        .as(
            "the body of a configuration request carries credentials, and a body nothing parsed"
                + " is where one a caller mistyped is still sitting, so the row names the field"
                + " and never the value")
        .doesNotContain(A_SECRET_UNDER_A_KEY_GROW_NEVER_DECLARED);
  }

  @Test
  void a_body_nothing_parsed_leaves_its_row_under_the_run_nothing_could_be_read_out_of_it() {
    assertThat(
            theAnswerToTheBytes(HttpMethod.POST, SIMULATOR + "/scenarios", "{\"action\":")
                .getStatusCode()
                .value())
        .isEqualTo(400);

    assertThat(theTrailOf(TestRunId.UNKNOWN))
        .as(
            "a request whose body nothing parsed named no run this api could read, and filing it"
                + " under the default run would show it to a tester reading that run for their"
                + " own calls")
        .anySatisfy(
            row -> {
              assertThat(row.path()).isEqualTo(SIMULATOR + "/scenarios");
              assertThat(row.httpStatus()).isEqualTo(400);
              assertThat(row.refusal())
                  .isEqualTo(
                      RefusalSubject.noObjectWasNamed(
                          ABodyTheControlApiCouldNotRead.THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ));
              assertThat(row.sanitizedBody())
                  .as(
                      "the reading stopped before it named a field, and an empty properties fact"
                          + " would tell a tester this api named one and lost it")
                  .doesNotContainKey(ActionFacts.REFUSED_PROPERTIES);
            });
  }

  @Test
  void a_request_carrying_no_body_where_a_route_requires_one_is_refused_the_same_way() {
    ResponseEntity<Map<String, Object>> answered =
        theAnswerToTheBytes(HttpMethod.POST, SIMULATOR + "/scenarios?testRunId=" + RUN, "");

    assertThat(answered.getStatusCode().value()).isEqualTo(400);
    assertThat(theMessageIn(answered))
        .as(
            "no reader was handed bytes and one that stopped on them read nothing either, so both"
                + " are the one decision this api publishes for a body it could not read")
        .isEqualTo(ABodyTheControlApiCouldNotRead.WHAT_A_CALLER_IS_TOLD);
    assertThat(answered.getBody())
        .as(
            "no reader named a field, so the answer is the one-field shape this api refuses in"
                + " wherever it names no property, and an empty array would tell a tester this"
                + " api named a field and lost it")
        .doesNotContainKey("properties");
    assertThat(theRowAt(SIMULATOR + "/scenarios", 400, TestRunId.UNKNOWN).refusal())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                ABodyTheControlApiCouldNotRead.THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ));
  }

  @Test
  void a_body_nothing_parsed_on_the_one_route_that_reads_its_run_from_its_query_is_filed_there() {
    String copy = SIMULATOR + "/scenarios/scn_never_written/copy";

    assertThat(
            theAnswerToTheBytes(HttpMethod.POST, copy + "?testRunId=" + RUN, "{\"asItStands\":")
                .getStatusCode()
                .value())
        .isEqualTo(400);

    assertThat(theRowAt(copy, 400).refusal())
        .as(
            "this is the one route of this api carrying a body that reads its run from its query,"
                + " so the run was named where that route reads it and the row belongs to the"
                + " tester's own run rather than to the reserved one")
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                ABodyTheControlApiCouldNotRead.THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ));
  }

  @Test
  void
      a_content_type_no_route_of_this_api_reads_is_turned_away_before_any_reader_and_leaves_none() {
    ResponseEntity<String> answered =
        theAnswerToTheseBytes(HttpMethod.PUT, CONFIG + "?testRunId=" + RUN, MediaType.TEXT_PLAIN);

    assertThat(answered.getStatusCode().value())
        .as(
            "no reader of this api was ever chosen, so the request was turned away on what it"
                + " said it carried, the way an address this api does not serve and a method one"
                + " of its addresses does not answer are")
        .isEqualTo(415);
    assertThat(answered.getBody())
        .as(
            "this is the third fault of this namespace answered in a shape that is not this"
                + " api's own, beside an address it does not serve and a method one of its"
                + " addresses does not answer, and a ci suite parsing control-api errors reads"
                + " spring's envelope until this surface publishes an answer for all three")
        .contains("\"timestamp\"")
        .contains("\"status\":415")
        .contains("\"error\"")
        .contains("\"path\"")
        .doesNotContain(ABodyTheControlApiCouldNotRead.WHAT_A_CALLER_IS_TOLD);

    assertThat(everyRowOfTheRunsARowCouldHaveLandedIn())
        .as(
            "nothing of this request reached a route or a reader of this api, so this simulator"
                + " decided nothing to record, as it records nothing for the two faults above,"
                + " and the default run this api applies to a body it read holds no row either")
        .noneSatisfy(
            row -> {
              assertThat(row.path()).isEqualTo(CONFIG);
              assertThat(row.httpStatus()).isEqualTo(415);
            });
  }

  @Test
  void a_body_this_api_could_not_read_under_a_provider_it_does_not_serve_is_recorded_by_nobody() {
    assertThat(
            theAnswerToTheBytes(
                    HttpMethod.PUT, "/__simulator/nosuchprovider/config", "{\"accounts\":")
                .getStatusCode()
                .value())
        .isEqualTo(404);

    assertThat(everyRowEveryProviderHolds())
        .as(
            "the trail is keyed by provider, and a row filed under one the address never named is"
                + " a row no tester of that provider asked for")
        .noneSatisfy(row -> assertThat(row.path()).contains("nosuchprovider"));
  }

  @Test
  void a_request_naming_a_provider_this_simulator_does_not_serve_is_recorded_by_nobody() {
    assertThat(
            send(
                HttpMethod.POST,
                "/__simulator/nosuchprovider/reset",
                Map.of("testRunId", RUN, "scope", "TEST_RUN")))
        .isEqualTo(404);

    assertThat(everyRowEveryProviderHolds())
        .noneSatisfy(row -> assertThat(row.path()).contains("nosuchprovider"));
  }

  private List<AuditRecord> everyRowEveryProviderHolds() {
    List<AuditRecord> everywhere = new ArrayList<>();
    for (String known : providers.names()) {
      everywhere.addAll(trail.of(ProviderId.of(known), TEST_RUN));
      everywhere.addAll(trail.of(ProviderId.of(known), TestRunId.UNKNOWN));
    }
    return everywhere;
  }

  @Test
  void an_action_that_changed_something_is_recorded_as_it_was_before_refusals_were_recorded() {
    assertThat(
            send(
                HttpMethod.PUT,
                CONFIG,
                Map.of(
                    "testRunId",
                    RUN,
                    "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            "merchant-1",
                            "configuration",
                            Map.of(
                                "userId",
                                "refused-actions-user",
                                "pageCode",
                                "refused-actions-page"))))))
        .isEqualTo(200);

    AuditRecord row = theRowAt(CONFIG, 200);
    assertThat(row.refusal()).isNull();
    assertThat(row.providerObjectId()).isNull();
    assertThat(row.outcome()).isNull();
    assertThat(row.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(row.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(row.sanitizedBody()).containsEntry("accounts[0].accountId", List.of("merchant-1"));
  }

  @Test
  void the_published_read_carries_the_subject_a_refused_action_named_beside_its_status() {
    send(HttpMethod.DELETE, CONFIG + "/accounts/merchant-9?testRunId=" + RUN, null);

    Map<String, Object> row = thePublishedRowAt(CONFIG + "/accounts/merchant-9", 404);
    assertThat(theRefusalOf(row))
        .containsEntry("subject", "NO_OBJECT_WAS_NAMED")
        .containsEntry("whatItRefusedInstead", THE_ACCOUNT);
  }

  @Test
  void the_published_read_carries_the_subject_a_refusal_on_the_wire_named_beside_its_status() {
    send(
        HttpMethod.PUT,
        CONFIG,
        Map.of(
            "testRunId",
            RUN,
            "accounts",
            List.of(
                Map.of(
                    "accountId",
                    "merchant-1",
                    "configuration",
                    Map.of(
                        "userId", "refused-actions-user", "pageCode", "refused-actions-page")))));

    aGrowCallThisRunsAccountRefuses();

    assertThat(thePublishedTrail())
        .anySatisfy(
            row -> {
              assertThat(row).containsEntry("direction", "OUTBOUND_RESPONSE");
              assertThat(row).containsEntry("outcome", "FAILURE");
              assertThat(theRefusalOf(row)).containsKey("subject");
            });
  }

  private void aGrowCallThisRunsAccountRefuses() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("userId", "refused-actions-user");
    form.put("pageCode", "refused-actions-page");
    form.put("chargeType", "2");
    form.put("sum", "not a sum");
    form.put("description", "a call no account of this run answers");
    form.put("successUrl", "https://merchant.test/thanks");
    form.put("cancelUrl", "https://merchant.test/cancel");
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(
                URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8)));
    http.post()
        .uri("/api/light/server/1.0/createPaymentProcess")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(String.join("&", pairs))
        .retrieve()
        .toBodilessEntity();
  }

  private List<Map<String, Object>> thePublishedTrail() {
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + RUN)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return rows == null ? List.of() : rows;
  }

  private Map<String, Object> thePublishedRowAt(String path, int status) {
    for (Map<String, Object> row : thePublishedTrail()) {
      if (path.equals(row.get("path")) && Integer.valueOf(status).equals(row.get("status"))) {
        return row;
      }
    }
    throw new AssertionError("no published row at " + path + " carrying " + status);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> theRefusalOf(Map<String, Object> row) {
    return (Map<String, Object>) row.get("refusal");
  }

  private static Map<String, Object> aConfigurationWithoutAValue() {
    Map<String, Object> configuration = new HashMap<>();
    configuration.put("processLookupRejection", null);
    Map<String, Object> account = new HashMap<>();
    account.put("accountId", "merchant-1");
    account.put("configuration", configuration);
    Map<String, Object> body = new HashMap<>();
    body.put("testRunId", RUN);
    body.put("accounts", List.of(account));
    return body;
  }

  private static Map<String, Object> aVisaCardOfKind(String kind) {
    Map<String, Object> body = aVisaCard();
    body.put("type", kind);
    return body;
  }

  private static Map<String, Object> aVisaCardNaming(String property, Object value) {
    Map<String, Object> body = aVisaCard();
    body.put(property, value);
    return body;
  }

  private static Map<String, Object> aVisaCard() {
    Map<String, Object> body = new HashMap<>();
    body.put("testRunId", RUN);
    body.put("merchantId", "merchant-1");
    body.put("ownerId", "customer-123");
    body.put("type", "card");
    body.put("alias", "visa-success");
    body.put(
        "card",
        Map.of(
            "brand", "visa",
            "last4", "1111",
            "expMonth", 12,
            "expYear", 2030,
            "funding", "credit"));
    return body;
  }

  private AuditRecord theRowAt(String path, int status) {
    return theRowAt(path, status, TEST_RUN);
  }

  private AuditRecord theRowAt(String path, int status, TestRunId testRunId) {
    List<String> seen = new ArrayList<>();
    for (AuditRecord row : theTrailOf(testRunId)) {
      Integer answered = row.httpStatus();
      if (path.equals(row.path()) && answered != null && answered == status) {
        return row;
      }
      seen.add(row.method() + " " + row.path() + " " + row.httpStatus());
    }
    throw new AssertionError(
        "no row of the trail answered " + status + " on " + path + ": " + seen);
  }

  private List<AuditRecord> theTrailOf(TestRunId testRunId) {
    return trail.of(GROW, testRunId);
  }

  private int send(HttpMethod method, String uri, @Nullable Map<String, Object> body) {
    return theAnswerTo(method, uri, body).getStatusCode().value();
  }

  private List<AuditRecord> everyRowOfTheRunsARowCouldHaveLandedIn() {
    List<AuditRecord> rows = new ArrayList<>(theTrailOf(TEST_RUN));
    rows.addAll(theTrailOf(TestRunId.UNKNOWN));
    rows.addAll(theTrailOf(TestRunId.of(theRunThisApiAppliesToABodyItRead)));
    return rows;
  }

  private ResponseEntity<String> theAnswerToTheseBytes(
      HttpMethod method, String uri, MediaType type) {
    return http.method(method)
        .uri(uri)
        .contentType(type)
        .body("a body no reader of this api was ever offered")
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(String.class);
  }

  private ResponseEntity<Map<String, Object>> theAnswerToTheBytes(
      HttpMethod method, String uri, String body) {
    return http.method(method)
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<Map<String, Object>> theAnswerTo(
      HttpMethod method, String uri, @Nullable Map<String, Object> body) {
    RestClient.RequestBodySpec sending = http.method(method).uri(uri);
    return (body == null ? sending : sending.contentType(MediaType.APPLICATION_JSON).body(body))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private void reset() {
    send(HttpMethod.POST, SIMULATOR + "/reset", Map.of("testRunId", RUN, "scope", "TEST_RUN"));
  }
}
