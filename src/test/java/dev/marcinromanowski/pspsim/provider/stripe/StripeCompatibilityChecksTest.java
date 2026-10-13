package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.smoke.CannedTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.smoke.FailureKind;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeCompatibilityChecksTest {

  private static final String INTENTS = "/v1/payment_intents";

  private static final String A_HELD_INTENT =
      "{\"id\":\"pi_sim_0000000000000001\",\"object\":\"payment_intent\",\"amount\":1099,"
          + "\"currency\":\"pln\",\"status\":\"requires_capture\"}";

  private static final String THE_CARD_HAD_NO_FUNDS =
      "{\"error\":{\"type\":\"card_error\",\"code\":\"card_declined\","
          + "\"decline_code\":\"insufficient_funds\",\"message\":\"\"}}";

  private static final String THE_PROBED_MESSAGE =
      "You did not provide an API key. You need to provide your API key in the Authorization"
          + " header, using Bearer auth (e.g. 'Authorization: Bearer YOUR_SECRET_KEY'). See"
          + " https://stripe.com/docs/api#authentication for details, or we can help at"
          + " https://support.stripe.com/.";

  private static final String NO_KEY_WAS_PROVIDED =
      "{\"error\":{\"message\":\"" + THE_PROBED_MESSAGE + "\",\"type\":\"invalid_request_error\"}}";

  private static final Map<String, List<String>> THE_PROBED_HEADERS =
      Map.of(
          "content-type", List.of("application/json"),
          "www-authenticate", List.of("Basic realm=\"Stripe\""));

  private static final String A_SIGNED_ATTEMPT =
      "[{\"webhookJobId\":\"whjob_a\",\"attempts\":[{\"number\":1,\"sentHeaders\":"
          + "{\"Content-Type\":[\"application/json\"],\"Stripe-Signature\":"
          + "[\"t=1789992000,v1=abc123\"]}}]}]";

  private static final String THE_TRAIL_OF_ONE_EXCHANGE =
      "[{\"direction\":\"INBOUND_REQUEST\",\"outcome\":null},"
          + "{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]";

  private final List<ContractCheck> checks =
      new StripeCompatibilityChecks(new CheckJson(), new CountingIds()).all();

  private final SmokeRunner runner = new SmokeRunner(Instant::now, provider -> List.of());

  @Test
  void the_provider_publishes_all_seven_minimum_checks_and_withholds_none() {
    assertThat(checks).hasSize(7);
    assertThat(checks).allSatisfy(check -> assertThat(check.expectations()).isNotEmpty());
  }

  @Test
  void every_value_a_check_expects_is_the_one_the_stripe_samples_publish() {
    assertThat(expectedBy(checks.get(0)))
        .containsExactly("status=requires_capture", "retrieved.status=requires_capture");
    assertThat(expectedBy(checks.get(1)))
        .containsExactly(
            "error.type=card_error",
            "error.code=card_declined",
            "error.decline_code=insufficient_funds");
    assertThat(expectedBy(checks.get(2)))
        .containsExactly("Idempotent-Replayed=true", "replay.body=true");
    assertThat(expectedBy(checks.get(3)))
        .containsExactly("timestamp.prefix=t=", "signature.scheme=v1");
    assertThat(expectedBy(checks.get(4)))
        .containsExactly(
            "httpStatus=401",
            "error.type=invalid_request_error",
            "Content-Type=application/json",
            "WWW-Authenticate=Basic realm=\"Stripe\"",
            "error.message=" + THE_PROBED_MESSAGE);
    assertThat(expectedBy(checks.get(5)))
        .containsExactly("scenario.httpStatus=201", "fixture.httpStatus=201");
    assertThat(expectedBy(checks.get(6)))
        .containsExactly("audit.direction=INBOUND_REQUEST", "audit.outcome=SUCCESS");
  }

  @Test
  void the_authentication_check_reads_every_value_from_the_live_probe_of_a_keyless_request() {
    assertThat(evidenceBy(checks.get(4)))
        .containsExactly(
            "httpStatus from stripe/no-api-key, live probe on 2026-09-28",
            "error.type from stripe/no-api-key, live probe on 2026-09-28",
            "Content-Type from stripe/no-api-key, live probe on 2026-09-28",
            "WWW-Authenticate from stripe/no-api-key, live probe on 2026-09-28",
            "error.message from stripe/no-api-key, live probe on 2026-09-28");
  }

  @Test
  void the_webhook_check_reads_a_timestamp_shorter_than_its_prefix_as_it_was_sent() {
    CannedTransport aBareT =
        aSignedDelivery()
            .answering(
                "GET",
                "/__simulator/stripe/webhooks",
                200,
                A_SIGNED_ATTEMPT.replace("t=1789992000,v1=abc123", "t,v1=abc123"));

    SmokeRun run = runner.run(List.of(checks.get(3)), aBareT);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("timestamp.prefix");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
  }

  @Test
  void the_happy_path_holds_when_a_manual_capture_intent_reads_back_at_requires_capture() {
    SmokeRun run = runner.run(List.of(checks.get(0)), aHeldIntent());

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_happy_path_fails_naming_the_status_field_and_the_sample_it_was_read_from() {
    CannedTransport drifted =
        prepared()
            .answering("POST", INTENTS, 200, A_HELD_INTENT.replace("requires_capture", "succeeded"))
            .answering("GET", INTENTS, 200, A_HELD_INTENT);

    SmokeRun run = runner.run(List.of(checks.get(0)), drifted);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.provider().value()).isEqualTo("stripe");
    assertThat(failed.operation()).isEqualTo(StripeCompatibilityChecks.CREATE_PAYMENT_INTENT);
    assertThat(failed.field()).isEqualTo("status");
    assertThat(failed.provenance().sample()).isEqualTo("stripe/payment-intent-requires-capture");
  }

  @Test
  void the_happy_path_fails_naming_the_reread_when_the_intent_does_not_read_back() {
    CannedTransport drifted =
        prepared()
            .answering("POST", INTENTS, 200, A_HELD_INTENT)
            .answering("GET", INTENTS, 404, "{\"error\":{\"type\":\"invalid_request_error\"}}");

    SmokeRun run = runner.run(List.of(checks.get(0)), drifted);

    assertThat(((SmokeVerdict.Failed) run.verdicts().get(0)).field()).isEqualTo("retrieved.status");
  }

  @Test
  void the_decline_holds_when_the_error_carries_the_published_code_and_decline_code_pairing() {
    SmokeRun run =
        runner.run(
            List.of(checks.get(1)),
            prepared().answering("POST", INTENTS, 402, THE_CARD_HAD_NO_FUNDS));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_decline_fails_naming_the_decline_code_when_the_pairing_drifts() {
    String otherPairing = THE_CARD_HAD_NO_FUNDS.replace("insufficient_funds", "generic_decline");

    SmokeRun run =
        runner.run(
            List.of(checks.get(1)), prepared().answering("POST", INTENTS, 402, otherPairing));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("error.decline_code");
    assertThat(failed.provenance().sample()).isEqualTo("stripe/card-declined-insufficient-funds");
  }

  @Test
  void the_idempotency_check_holds_when_the_repeat_announces_the_replay_and_repeats_the_answer() {
    SmokeRun run = runner.run(List.of(checks.get(2)), aReplayedCreation());

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_idempotency_check_fails_naming_the_replay_header_when_the_repeat_is_silent() {
    CannedTransport silent =
        prepared()
            .answeringInTurn(
                "POST",
                INTENTS,
                new CheckResponse(200, Map.of(), A_HELD_INTENT),
                new CheckResponse(200, Map.of(), A_HELD_INTENT));

    SmokeRun run = runner.run(List.of(checks.get(2)), silent);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("Idempotent-Replayed");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_idempotency_check_fails_naming_the_body_when_the_repeat_answers_a_second_object() {
    CannedTransport aSecondObject =
        prepared()
            .answeringInTurn(
                "POST",
                INTENTS,
                new CheckResponse(200, Map.of(), A_HELD_INTENT),
                new CheckResponse(
                    200,
                    Map.of("Idempotent-Replayed", List.of("true")),
                    A_HELD_INTENT.replace("0000000000000001", "0000000000000002")));

    SmokeRun run = runner.run(List.of(checks.get(2)), aSecondObject);

    assertThat(((SmokeVerdict.Failed) run.verdicts().get(0)).field()).isEqualTo("replay.body");
  }

  @Test
  void the_webhook_check_holds_when_the_attempt_carried_the_published_header_and_schemes() {
    SmokeRun run = runner.run(List.of(checks.get(3)), aSignedDelivery());

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_webhook_check_fails_naming_the_signature_scheme_when_the_attempt_signs_under_v_two() {
    CannedTransport underVTwo =
        aSignedDelivery()
            .answering(
                "GET", "/__simulator/stripe/webhooks", 200, A_SIGNED_ATTEMPT.replace("v1=", "v2="));

    SmokeRun run = runner.run(List.of(checks.get(3)), underVTwo);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("signature.scheme");
    assertThat(failed.provenance().sample()).isEqualTo("stripe/webhook-signature");
  }

  @Test
  void the_webhook_check_fails_naming_the_timestamp_when_no_attempt_carried_a_signature() {
    CannedTransport unsigned =
        aSignedDelivery()
            .answering(
                "GET",
                "/__simulator/stripe/webhooks",
                200,
                "[{\"webhookJobId\":\"whjob_a\",\"attempts\":[{\"number\":1,"
                    + "\"sentHeaders\":{\"Content-Type\":[\"application/json\"]}}]}]");

    SmokeRun run = runner.run(List.of(checks.get(3)), unsigned);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("timestamp.prefix");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_authentication_check_holds_when_a_request_with_no_key_is_refused_as_probed() {
    SmokeRun run = runner.run(List.of(checks.get(4)), refusedWith(THE_PROBED_HEADERS));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_authentication_check_fails_naming_the_challenge_when_the_refusal_carries_none() {
    SmokeRun run =
        runner.run(
            List.of(checks.get(4)),
            refusedWith(Map.of("Content-Type", List.of("application/json"))));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("WWW-Authenticate");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_authentication_check_fails_naming_the_message_when_a_missing_key_is_called_invalid() {
    CannedTransport invalidRatherThanMissing =
        prepared()
            .answering(
                "POST",
                INTENTS,
                new CheckResponse(
                    401,
                    THE_PROBED_HEADERS,
                    NO_KEY_WAS_PROVIDED.replace(THE_PROBED_MESSAGE, "No valid API key provided.")));

    SmokeRun run = runner.run(List.of(checks.get(4)), invalidRatherThanMissing);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("error.message");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
  }

  @Test
  void the_authentication_check_fails_naming_the_content_type_when_the_refusal_is_not_json() {
    SmokeRun run =
        runner.run(
            List.of(checks.get(4)),
            refusedWith(
                Map.of(
                    "Content-Type", List.of("text/html"),
                    "WWW-Authenticate", List.of("Basic realm=\"Stripe\""))));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("Content-Type");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
  }

  @Test
  void the_authentication_check_fails_naming_the_http_status_when_an_unkeyed_request_is_served() {
    SmokeRun run =
        runner.run(
            List.of(checks.get(4)), prepared().answering("POST", INTENTS, 200, A_HELD_INTENT));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("httpStatus");
    assertThat(failed.provenance().sample()).isEqualTo("stripe/no-api-key");
  }

  @Test
  void the_control_api_check_holds_when_a_scenario_and_a_fixture_are_both_created() {
    SmokeRun run = runner.run(List.of(checks.get(5)), prepared());

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_audit_check_holds_when_the_trail_opens_with_the_request_and_carries_the_verdict() {
    SmokeRun run = runner.run(List.of(checks.get(6)), aHeldIntent());

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_audit_check_fails_naming_the_outcome_when_the_answer_is_not_recorded_as_a_success() {
    CannedTransport withoutTheVerdict =
        aHeldIntent()
            .answering(
                "GET",
                "/__simulator/stripe/audit",
                200,
                "[{\"direction\":\"INBOUND_REQUEST\",\"outcome\":null},"
                    + "{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"FAILURE\"}]");

    SmokeRun run = runner.run(List.of(checks.get(6)), withoutTheVerdict);

    assertThat(((SmokeVerdict.Failed) run.verdicts().get(0)).field()).isEqualTo("audit.outcome");
  }

  @Test
  void every_check_resets_and_configures_the_check_run_and_keys_the_call_it_then_makes() {
    for (ContractCheck check : checks) {
      CannedTransport wire = aSignedDelivery().answering("GET", INTENTS, 200, A_HELD_INTENT);

      runner.run(List.of(check), wire);

      assertThat(wire.sent().get(0).method() + " " + wire.sent().get(0).path())
          .isEqualTo("POST /__simulator/stripe/reset");
      assertThat(wire.sent().get(1).method() + " " + wire.sent().get(1).path())
          .isEqualTo("PUT /__simulator/stripe/config");
      assertThat(wire.sent().get(1).body()).contains("sk_test_").contains("whsec_");
    }
  }

  @Test
  void the_key_a_check_presents_is_the_test_mode_key_it_configured_a_moment_earlier() {
    CannedTransport wire = aHeldIntent();

    runner.run(List.of(checks.get(0)), wire);

    assertThat(wire.sent().get(2).headers().get("Authorization")).startsWith("Bearer sk_test_");
  }

  @Test
  void the_intent_a_check_creates_carries_the_amount_currency_and_manual_capture_it_asserts_on() {
    CannedTransport wire = aHeldIntent();

    runner.run(List.of(checks.get(0)), wire);

    assertThat(wire.sent().get(2).body())
        .contains("amount=1099")
        .contains("currency=pln")
        .contains("capture_method=manual")
        .contains("confirm=true");
  }

  @Test
  void the_authentication_check_presents_no_key_at_all() {
    CannedTransport wire = refusedWith(THE_PROBED_HEADERS);

    runner.run(List.of(checks.get(4)), wire);

    assertThat(wire.sent().get(2).headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Authorization"));
  }

  @Test
  void the_webhook_check_reports_the_timestamp_unobserved_when_the_creation_named_no_intent() {
    CannedTransport noIntent =
        prepared().answering("POST", INTENTS, 200, "{\"object\":\"payment_intent\"}");

    SmokeRun run = runner.run(List.of(checks.get(3)), noIntent);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("timestamp.prefix");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_webhook_check_reports_the_scheme_unobserved_when_the_header_carries_only_a_timestamp() {
    CannedTransport timestampAlone =
        aSignedDelivery()
            .answering(
                "GET",
                "/__simulator/stripe/webhooks",
                200,
                A_SIGNED_ATTEMPT.replace("t=1789992000,v1=abc123", "t=1789992000"));

    SmokeRun run = runner.run(List.of(checks.get(3)), timestampAlone);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("signature.scheme");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void a_one_character_scheme_beside_the_timestamp_is_read_and_fails_the_published_v_one() {
    CannedTransport aBareV =
        aSignedDelivery()
            .answering(
                "GET",
                "/__simulator/stripe/webhooks",
                200,
                A_SIGNED_ATTEMPT.replace("v1=abc123", "v=abc123"));

    SmokeRun run = runner.run(List.of(checks.get(3)), aBareV);

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("signature.scheme");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
  }

  @Test
  void an_element_carrying_no_scheme_at_all_is_stepped_over_rather_than_read_as_one() {
    CannedTransport anEmptyScheme =
        aSignedDelivery()
            .answering(
                "GET",
                "/__simulator/stripe/webhooks",
                200,
                A_SIGNED_ATTEMPT.replace("t=1789992000,v1=abc123", "t=1789992000,=x,v1=abc123"));

    SmokeRun run = runner.run(List.of(checks.get(3)), anEmptyScheme);

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  private static List<String> evidenceBy(ContractCheck check) {
    return check.expectations().stream()
        .map(
            expectation ->
                expectation.field()
                    + " from "
                    + expectation.published().provenance().sample()
                    + ", "
                    + expectation.published().provenance().evidence().rendered())
        .toList();
  }

  private static List<String> expectedBy(ContractCheck check) {
    return check.expectations().stream()
        .map(expectation -> expectation.field() + "=" + expectation.published().value())
        .toList();
  }

  private static CannedTransport refusedWith(Map<String, List<String>> headers) {
    return prepared()
        .answering("POST", INTENTS, new CheckResponse(401, headers, NO_KEY_WAS_PROVIDED));
  }

  private static CannedTransport aHeldIntent() {
    return prepared()
        .answering("POST", INTENTS, 200, A_HELD_INTENT)
        .answering("GET", INTENTS, 200, A_HELD_INTENT);
  }

  private static CannedTransport aReplayedCreation() {
    return prepared()
        .answeringInTurn(
            "POST",
            INTENTS,
            new CheckResponse(200, Map.of(), A_HELD_INTENT),
            new CheckResponse(200, Map.of("Idempotent-Replayed", List.of("true")), A_HELD_INTENT));
  }

  private static CannedTransport aSignedDelivery() {
    return prepared()
        .answering("POST", INTENTS, 200, A_HELD_INTENT)
        .answering("POST", "/__simulator/stripe/webhooks", 201, "{\"webhookJobId\":\"whjob_a\"}")
        .answering("GET", "/__simulator/stripe/webhooks", 200, A_SIGNED_ATTEMPT);
  }

  private static CannedTransport prepared() {
    return new CannedTransport()
        .answering("POST", "/__simulator/stripe/reset", 200, "{}")
        .answering("PUT", "/__simulator/stripe/config", 200, "{}")
        .answering("POST", "/__simulator/stripe/scenarios", 201, "{\"scenarioId\":\"scn_a\"}")
        .answering(
            "POST", "/__simulator/stripe/fixtures/payment-methods", 201, "{\"fixtureId\":\"fx_a\"}")
        .answering("GET", "/__simulator/stripe/audit", 200, THE_TRAIL_OF_ONE_EXCHANGE);
  }
}
