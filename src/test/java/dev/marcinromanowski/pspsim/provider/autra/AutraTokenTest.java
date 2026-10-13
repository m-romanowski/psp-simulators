package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraTokenTest {

  private static final TestRunId RUN = TestRunId.of("autra-token");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String CLIENT_ID = "client-merchant-1";
  private static final String CLIENT_SECRET = "secret-merchant-1";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();

  private AutraApi apiFor(Map<String, String> configuration) {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, configuration));
    return AutraWiring.api(merchants, sessions, new AutraDoubles.Payments(), audit, RUN, NOW);
  }

  @Test
  void
      a_valid_basic_credential_is_answered_the_references_camel_case_fields_and_a_jwt_shaped_token() {
    AutraApiResponse response =
        apiFor(Map.of())
            .token(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, CLIENT_SECRET)));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.text(AutraFields.TOKEN_TYPE)).contains("Bearer");
    assertThat(body.number(AutraFields.EXPIRES_IN)).contains("3600");
    assertThat(body.text(AutraFields.ACCESS_TOKEN).orElseThrow().split("\\.")).hasSize(3);
  }

  @Test
  void the_guides_snake_case_spelling_is_emitted_when_the_account_asks_for_that_publication() {
    AutraApiResponse response =
        apiFor(Map.of(AutraOauthFieldCasing.SETTING, AutraOauthFieldCasing.SNAKE_CASE.name()))
            .token(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, CLIENT_SECRET)));

    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.text(AutraFields.ACCESS_TOKEN_SNAKE)).isPresent();
    assertThat(body.text(AutraFields.ACCESS_TOKEN)).isEmpty();
    assertThat(body.text(AutraFields.TOKEN_TYPE_SNAKE)).contains("Bearer");
    assertThat(body.number(AutraFields.EXPIRES_IN_SNAKE)).contains("3600");
  }

  @Test
  void each_published_credential_refusal_is_answered_with_that_message_under_error() {
    AutraApi api = apiFor(Map.of());

    assertThat(theErrorIn(api.token(AutraWire.tokenCall(AutraFields.CLIENT_CREDENTIALS, null))))
        .isEqualTo(AutraOauthRefusal.MISSING_AUTHORIZATION_HEADER);
    assertThat(
            theErrorIn(
                api.token(AutraWire.tokenCall(null, AutraWire.basic(CLIENT_ID, CLIENT_SECRET)))))
        .isEqualTo(AutraOauthRefusal.MISSING_GRANT_TYPE);
    assertThat(
            theErrorIn(
                api.token(
                    AutraWire.tokenCall(
                        "authorization_code", AutraWire.basic(CLIENT_ID, CLIENT_SECRET)))))
        .isEqualTo(AutraOauthRefusal.UNSUPPORTED_GRANT_TYPE);
    assertThat(
            theErrorIn(
                api.token(AutraWire.tokenCall(AutraFields.CLIENT_CREDENTIALS, "Bearer abc"))))
        .isEqualTo(AutraOauthRefusal.MISSING_AUTHORIZATION_BASIC);
    assertThat(
            theErrorIn(api.token(AutraWire.tokenCall(AutraFields.CLIENT_CREDENTIALS, "Basic !!!"))))
        .isEqualTo(AutraOauthRefusal.INVALID_BASE64);
    assertThat(
            theErrorIn(
                api.token(
                    AutraWire.tokenCall(
                        AutraFields.CLIENT_CREDENTIALS,
                        "Basic "
                            + Base64.getEncoder()
                                .encodeToString("noColon".getBytes(StandardCharsets.UTF_8))))))
        .isEqualTo(AutraOauthRefusal.INVALID_BASIC_FORMAT);
    assertThat(
            theErrorIn(
                api.token(
                    AutraWire.tokenCall(
                        AutraFields.CLIENT_CREDENTIALS,
                        AutraWire.basic("unknown", CLIENT_SECRET)))))
        .isEqualTo(AutraOauthRefusal.INVALID_CLIENT_ID);
    assertThat(
            theErrorIn(
                api.token(
                    AutraWire.tokenCall(
                        AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, "wrong")))))
        .isEqualTo(AutraOauthRefusal.INVALID_CLIENT_SECRET);
  }

  @Test
  void a_credential_whose_client_id_is_empty_is_refused_for_the_client_id_and_not_for_its_format() {
    AutraApiResponse response =
        apiFor(Map.of())
            .token(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS, AutraWire.basic("", CLIENT_SECRET)));

    assertThat(theErrorIn(response)).isEqualTo(AutraOauthRefusal.INVALID_CLIENT_ID);
  }

  @Test
  void a_credential_whose_client_id_is_empty_still_names_that_empty_client_id_to_resolve_against() {
    assertThat(
            IssueAccessToken.clientIdIn(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS, AutraWire.basic("", CLIENT_SECRET))))
        .contains("");
    assertThat(
            IssueAccessToken.clientIdIn(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, CLIENT_SECRET))))
        .contains(CLIENT_ID);
  }

  @Test
  void a_credential_with_no_separator_at_all_names_no_client_id_to_resolve_against() {
    assertThat(
            IssueAccessToken.clientIdIn(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS,
                    AutraFields.BASIC
                        + Base64.getEncoder()
                            .encodeToString("noColon".getBytes(StandardCharsets.UTF_8)))))
        .isEmpty();
    assertThat(
            IssueAccessToken.clientIdIn(
                AutraWire.tokenCall(AutraFields.CLIENT_CREDENTIALS, "Bearer something")))
        .isEmpty();
  }

  @Test
  void
      a_refused_credential_is_answered_400_on_the_reference_publication_this_simulator_defaults_to() {
    assertThat(
            apiFor(Map.of())
                .token(
                    AutraWire.tokenCall(
                        AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, "wrong")))
                .httpStatus())
        .isEqualTo(400);
  }

  @Test
  void
      the_setup_guides_401_invalid_client_is_answered_when_the_account_asks_for_that_publication() {
    AutraApiResponse response =
        apiFor(
                Map.of(
                    AutraOauthFailure.SETTING,
                    AutraOauthFailure.UNAUTHORIZED_INVALID_CLIENT.name()))
            .token(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, "wrong")));

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(theErrorIn(response)).isEqualTo(AutraOauthFailure.INVALID_CLIENT);
  }

  @Test
  void the_token_the_call_answered_with_is_never_written_into_the_audit_trail() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        api.token(
            AutraWire.tokenCall(
                AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, CLIENT_SECRET)));

    String token =
        AutraJsonReader.objectIn(AutraWire.bodyOf(response))
            .orElseThrow()
            .text(AutraFields.ACCESS_TOKEN)
            .orElseThrow();
    assertThat(audit.all())
        .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.sanitizedBody().get(AutraFields.ACCESS_TOKEN))
                    .isNotNull()
                    .isNotEqualTo(List.of(token)));
  }

  @Test
  void the_basic_credential_the_call_carried_is_never_written_into_the_audit_trail() {
    String authorization = AutraWire.basic(CLIENT_ID, CLIENT_SECRET);

    apiFor(Map.of()).token(AutraWire.tokenCall(AutraFields.CLIENT_CREDENTIALS, authorization));

    assertThat(audit.all())
        .filteredOn(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.sanitizedHeaders().get(AutraFields.AUTHORIZATION))
                    .isNotEqualTo(List.of(authorization)));
  }

  @Test
  void a_token_this_run_issued_authenticates_an_acquiring_call_of_the_account_it_was_issued_for() {
    AutraApi api = apiFor(Map.of());
    String token =
        theTokenIn(
            api.token(
                AutraWire.tokenCall(
                    AutraFields.CLIENT_CREDENTIALS, AutraWire.basic(CLIENT_ID, CLIENT_SECRET))));

    AutraApiResponse opened =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(token),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(opened.httpStatus()).isEqualTo(200);
  }

  @Test
  void an_acquiring_call_carrying_no_bearer_at_all_is_answered_401_before_the_body_is_read() {
    AutraApiResponse response =
        apiFor(Map.of())
            .initialize(AutraWire.call(AutraApiController.INITIALIZE, Map.of(), "not json"));

    assertThat(response.httpStatus()).isEqualTo(401);
    assertThat(AutraWire.bodyOf(response)).contains(AutraErrors.UNAUTHORIZED);
  }

  @Test
  void an_acquiring_call_carrying_a_bearer_this_run_never_issued_is_answered_401() {
    assertThat(
            apiFor(Map.of())
                .initialize(
                    AutraWire.call(
                        AutraApiController.INITIALIZE,
                        AutraWire.bearer("never-issued"),
                        "{\"documentId\":\"" + DOCUMENT + "\"}"))
                .httpStatus())
        .isEqualTo(401);
  }

  static String theErrorIn(AutraApiResponse response) {
    return AutraJsonReader.objectIn(AutraWire.bodyOf(response))
        .orElseThrow()
        .text(AutraFields.OAUTH_ERROR)
        .orElseThrow();
  }

  static String theTokenIn(AutraApiResponse response) {
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    return body.text(AutraFields.ACCESS_TOKEN)
        .orElseGet(() -> body.text(AutraFields.ACCESS_TOKEN_SNAKE).orElseThrow());
  }
}
