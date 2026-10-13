package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraDeliveryTest {

  private static final TestRunId RUN = TestRunId.of("autra-delivery");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";

  private final AutraDoubles.Merchants merchants =
      new AutraDoubles.Merchants().with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT));
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();

  private AutraApi apiAt(Instant now) {
    return AutraWiring.api(
        merchants, sessions, payments, audit, RUN, now, new ScenarioEngine(rules));
  }

  @Test
  void a_json_answer_declares_the_media_type_the_length_and_the_run_it_belongs_to_and_no_delay() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.contentType()).isEqualTo(AutraFields.JSON);
    assertThat(response.declaredContentLength())
        .isEqualTo(
            new String(response.body(), StandardCharsets.UTF_8)
                .getBytes(StandardCharsets.UTF_8)
                .length);
    assertThat(response.testRunId()).isEqualTo(RUN);
    assertThat(response.delay()).isEqualTo(Duration.ZERO);
    assertThat(sessions.all())
        .singleElement()
        .satisfies(
            session -> {
              assertThat(session.accountId()).isEqualTo(ACCOUNT);
              assertThat(session.documentId()).isEqualTo(DOCUMENT);
              assertThat(session.createdAt()).isEqualTo(NOW);
              assertThat(session.testRunId()).isEqualTo(RUN);
            });
  }

  @Test
  void a_rule_that_holds_the_answer_back_reports_the_delay_on_the_response_it_shaped() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.INITIALIZE),
            new ScenarioAction.TransportFaultAlways(
                new TransportFault.DelayedResponse(Duration.ofSeconds(3))),
            null));
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.delay()).isEqualTo(Duration.ofSeconds(3));
  }

  @Test
  void an_answer_a_fault_replaced_the_operation_with_renders_no_provider_body_at_all() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.INITIALIZE),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(502);
    assertThat(sessions.all()).isEmpty();
  }

  @Test
  void an_answer_that_stops_short_of_its_declared_length_ends_the_connection_it_was_sent_on() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.INITIALIZE),
            new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()),
            null));
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.declaredContentLength()).isGreaterThan(response.body().length);
    assertThat(response.headers())
        .containsEntry(DeliveredHeaders.CONNECTION, List.of(DeliveredHeaders.CLOSE));
  }

  @Test
  void an_answer_that_writes_every_byte_it_declared_ends_no_connection() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.declaredContentLength()).isEqualTo(response.body().length);
    assertThat(response.headers())
        .containsExactly(Map.entry(DeliveredHeaders.CONTENT_TYPE, List.of(AutraFields.JSON)));
  }

  @Test
  void a_body_nothing_rendered_carries_no_media_type_no_bytes_no_text_and_no_field() {
    AutraBody nothing = AutraBody.nothingRendered();

    assertThat(nothing.contentType()).isNull();
    assertThat(nothing.bytes()).isEmpty();
    assertThat(nothing.text()).isEmpty();
    assertThat(nothing.fields()).isEmpty();
  }

  @Test
  void a_bearer_of_one_run_does_not_authenticate_a_call_a_header_resolved_to_another_run() {
    TestRunId elsewhere = TestRunId.of("autra-delivery-elsewhere");
    AutraDoubles.Runs runs = new AutraDoubles.Runs(RUN);
    runs.register(elsewhere);
    AutraApi api =
        AutraWiring.api(
            merchants, sessions, payments, audit, RUN, NOW, new ScenarioEngine(rules), runs);
    String token = AutraFlow.bearer(api, ACCOUNT);
    Map<String, List<String>> headers = new LinkedHashMap<>(AutraWire.bearer(token));
    headers.put(TestRunHeader.NAME, List.of(elsewhere.value()));

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE, headers, "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.testRunId()).isEqualTo(elsewhere);
    assertThat(response.httpStatus()).isEqualTo(401);
  }

  @Test
  void a_bearer_whose_hour_has_run_out_no_longer_authenticates_an_acquiring_call() {
    String token = AutraFlow.bearer(apiAt(NOW), ACCOUNT);

    AutraApiResponse response =
        apiAt(NOW.plus(AutraAccessToken.PUBLISHED_VALIDITY).plusSeconds(1))
            .initialize(
                AutraWire.call(
                    AutraApiController.INITIALIZE,
                    AutraWire.bearer(token),
                    "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(401);
  }

  @Test
  void a_bearer_at_the_very_instant_its_hour_runs_out_no_longer_authenticates_an_acquiring_call() {
    String token = AutraFlow.bearer(apiAt(NOW), ACCOUNT);

    AutraApiResponse response =
        apiAt(NOW.plus(AutraAccessToken.PUBLISHED_VALIDITY))
            .initialize(
                AutraWire.call(
                    AutraApiController.INITIALIZE,
                    AutraWire.bearer(token),
                    "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(401);
  }

  @Test
  void a_session_request_whose_document_is_nothing_but_blanks_is_refused_as_an_absent_one() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"   \"}"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(AutraPreAuthorizationTest.theCodeIn(response))
        .isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_bearer_still_inside_its_hour_authenticates_an_acquiring_call() {
    String token = AutraFlow.bearer(apiAt(NOW), ACCOUNT);

    AutraApiResponse response =
        apiAt(NOW.plus(AutraAccessToken.PUBLISHED_VALIDITY).minusSeconds(1))
            .initialize(
                AutraWire.call(
                    AutraApiController.INITIALIZE,
                    AutraWire.bearer(token),
                    "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(200);
  }

  @Test
  void a_token_value_the_store_will_not_take_is_drawn_again_until_one_is_claimed() {
    sessions.refusingTheNextMints(IssueAccessToken.TOKEN_ATTEMPTS - 1);

    assertThat(
            apiAt(NOW)
                .token(
                    AutraWire.tokenCall(
                        AutraFields.CLIENT_CREDENTIALS,
                        AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT)))
                .httpStatus())
        .isEqualTo(200);
  }

  @Test
  void a_token_no_attempt_could_claim_is_reported_as_a_call_this_simulator_cannot_answer() {
    sessions.refusingTheNextMints(IssueAccessToken.TOKEN_ATTEMPTS);
    AutraApi api = apiAt(NOW);

    AutraApiCall asking =
        AutraWire.tokenCall(
            AutraFields.CLIENT_CREDENTIALS,
            AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT));

    assertThatThrownBy(() -> api.token(asking))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("access token");
  }

  @Test
  void a_session_no_attempt_could_open_is_reported_as_a_call_this_simulator_cannot_answer() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);
    sessions.refusingTheNextOpens(OpenAcquiringSession.SESSION_ATTEMPTS);

    AutraApiCall initializing =
        AutraWire.call(
            AutraApiController.INITIALIZE,
            AutraWire.bearer(token),
            "{\"documentId\":\"" + DOCUMENT + "\"}");

    assertThatThrownBy(() -> api.initialize(initializing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transactionalToken");
  }

  @Test
  void a_session_token_the_store_will_not_take_is_drawn_again_until_one_is_opened() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);
    sessions.refusingTheNextOpens(OpenAcquiringSession.SESSION_ATTEMPTS - 1);

    assertThat(
            api.initialize(
                    AutraWire.call(
                        AutraApiController.INITIALIZE,
                        AutraWire.bearer(token),
                        "{\"documentId\":\"" + DOCUMENT + "\"}"))
                .httpStatus())
        .isEqualTo(200);
  }

  @Test
  void a_session_opened_for_a_document_that_is_not_the_merchants_is_answered_merchant_not_found() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE, AutraWire.bearer(token), "{\"documentId\":\"1\"}"));

    assertThat(response.httpStatus()).isEqualTo(404);
  }

  @Test
  void a_session_request_without_a_document_or_with_a_body_that_is_not_json_is_refused() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    assertThat(
            api.initialize(
                    AutraWire.call(AutraApiController.INITIALIZE, AutraWire.bearer(token), "{}"))
                .httpStatus())
        .isEqualTo(400);
    assertThat(
            api.initialize(
                    AutraWire.call(
                        AutraApiController.INITIALIZE, AutraWire.bearer(token), "not json"))
                .httpStatus())
        .isEqualTo(400);
  }

  @Test
  void a_rule_that_declines_a_session_is_answered_the_acquiring_envelope_at_422() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.INITIALIZE),
            new ScenarioAction.TerminalFailure(AutraErrors.MSG_GENERAL_ERROR),
            null));
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(AutraPreAuthorizationTest.theCodeIn(response))
        .isEqualTo(AutraErrors.MSG_GENERAL_ERROR);
    assertThat(sessions.all()).isEmpty();
  }

  @Test
  void
      the_audit_trail_keeps_the_fields_of_the_request_and_withholds_the_session_token_it_answered() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    String minted =
        AutraJsonReader.objectIn(AutraWire.bodyOf(response))
            .orElseThrow()
            .text(AutraFields.TRANSACTIONAL_TOKEN)
            .orElseThrow();
    assertThat(audit.all())
        .filteredOn(record -> AutraApiController.INITIALIZE.equals(record.path()))
        .anySatisfy(
            record ->
                assertThat(record.sanitizedBody())
                    .containsEntry(AutraFields.DOCUMENT_ID, List.of(DOCUMENT)))
        .filteredOn(record -> record.sanitizedBody().containsKey(AutraFields.TRANSACTIONAL_TOKEN))
        .isNotEmpty()
        .allSatisfy(
            record ->
                assertThat(record.sanitizedBody().get(AutraFields.TRANSACTIONAL_TOKEN))
                    .containsExactly(Redaction.REDACTED)
                    .doesNotContain(minted));
  }

  @Test
  void a_card_object_a_pre_authorization_carried_is_withheld_from_the_audit_trail() {
    AutraApi api = apiAt(NOW);
    String token = AutraFlow.bearer(api, ACCOUNT);
    String session = AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT);

    api.preAuthorization(
        AutraWire.call(
            AutraApiController.PRE_AUTHORIZATION,
            AutraWire.bearer(token),
            "{\"transactionalToken\":\""
                + session
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"transactionType\":\"CREDIT\",\"currency\":\"BRL\",\"amount\":500.00,"
                + "\"installments\":1,\"card\":{\"expirationDate\":\"122028\"}}"));

    assertThat(audit.all())
        .filteredOn(record -> AutraApiController.PRE_AUTHORIZATION.equals(record.path()))
        .filteredOn(
            record -> record.sanitizedBody().containsKey(AutraFields.CARD + ".expirationDate"))
        .isNotEmpty()
        .allSatisfy(
            record ->
                assertThat(record.sanitizedBody().get(AutraFields.CARD + ".expirationDate"))
                    .containsExactly(Redaction.REDACTED)
                    .doesNotContain("122028"));
  }

  @Test
  void a_payment_of_another_account_of_the_same_run_is_not_capturable_by_this_ones_credentials() {
    merchants.with(AutraDoubles.merchant(RUN, "merchant-2", "11111111111"));
    AutraApi api = apiAt(NOW);
    String paymentId =
        AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", null).providerObjectId();

    AutraApiResponse response =
        api.capture(
            AutraWire.call(
                AutraApiController.PAYMENTS + "/" + paymentId + "/capture",
                AutraWire.bearer(AutraFlow.bearer(api, "merchant-2")),
                "{\"transactionalToken\":\""
                    + AutraFlow.session(sessions, RUN, "merchant-2", "11111111111")
                    + "\",\"documentId\":\"11111111111\",\"amount\":100.00}"),
            paymentId);

    assertThat(response.httpStatus()).isEqualTo(404);
  }

  @Test
  void a_payment_of_another_account_of_the_same_run_is_not_reversible_by_this_ones_credentials() {
    merchants.with(AutraDoubles.merchant(RUN, "merchant-2", "11111111111"));
    AutraApi api = apiAt(NOW);
    String paymentId =
        AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", null).providerObjectId();

    AutraApiResponse response =
        api.voidPayment(
            AutraWire.call(
                AutraApiController.PAYMENTS + "/" + paymentId + "/void",
                AutraWire.bearer(AutraFlow.bearer(api, "merchant-2")),
                "{\"transactionalToken\":\""
                    + AutraFlow.session(sessions, RUN, "merchant-2", "11111111111")
                    + "\",\"documentId\":\"11111111111\",\"amount\":500.00}"),
            paymentId);

    assertThat(response.httpStatus()).isEqualTo(404);
  }

  @Test
  void the_merchant_not_found_status_may_be_the_403_autra_publishes_on_the_sale_endpoint() {
    AutraDoubles.Merchants only =
        new AutraDoubles.Merchants()
            .with(
                AutraDoubles.merchant(
                    RUN,
                    ACCOUNT,
                    DOCUMENT,
                    Map.of(AutraMerchantNotFound.SETTING, AutraMerchantNotFound.FORBIDDEN.name())));
    AutraApi api =
        AutraWiring.api(only, sessions, payments, audit, RUN, NOW, new ScenarioEngine(rules));
    String token = AutraFlow.bearer(api, ACCOUNT);

    assertThat(
            api.initialize(
                    AutraWire.call(
                        AutraApiController.INITIALIZE,
                        AutraWire.bearer(token),
                        "{\"documentId\":\"1\"}"))
                .httpStatus())
        .isEqualTo(403);
  }
}
