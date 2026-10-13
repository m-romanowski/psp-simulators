package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.smoke.Applicability;
import dev.marcinromanowski.pspsim.kernel.smoke.CannedTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.FailureKind;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrowCompatibilityChecksTest {

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  private static final String A_HOLD_WAS_CREATED =
      "{\"status\":1,\"err\":\"\",\"data\":{\"processId\":\"123456\","
          + "\"processToken\":\"0123456789abcdef0123456789abcdef\",\"url\":\"http://host/far?l=x\"}}";

  private static final String THE_CARD_EXPIRED =
      "{\"status\":0,\"err\":{\"id\":103,\"message\":\"\"},\"data\":\"\"}";

  private static final String THE_CREDENTIAL_WAS_REFUSED =
      "{\"status\":0,\"err\":{\"id\":701,\"message\":\"\"},\"data\":\"\"}";

  private static final String A_CALLBACK_THE_MERCHANT_REFUSED =
      "[{\"webhookJobId\":\"whk_a\",\"state\":\"PENDING\",\"attemptsMade\":1,"
          + "\"attemptCap\":6}]";

  private static final String THE_TRAIL_OF_ONE_EXCHANGE =
      "[{\"direction\":\"INBOUND_REQUEST\",\"outcome\":null},"
          + "{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]";

  private final List<ContractCheck> checks = new GrowCompatibilityChecks(new CheckJson()).all();

  private final SmokeRunner runner = new SmokeRunner(Instant::now, provider -> List.of());

  @Test
  void
      the_provider_publishes_a_check_for_each_of_the_six_covered_operations_and_one_it_withholds() {
    assertThat(checks).hasSize(7);
    assertThat(
            checks.stream()
                .filter(check -> check.applicability() instanceof Applicability.DoesNotApply))
        .hasSize(1);
  }

  @Test
  void every_value_a_check_expects_is_the_one_the_grow_samples_publish() {
    assertThat(expectedBy(happyPath())).containsExactly("httpStatus=200", "status=1");
    assertThat(expectedBy(decline())).containsExactly("status=0", "err.id=103");
    assertThat(expectedBy(credentials())).containsExactly("httpStatus=200", "err.id=701");
    assertThat(expectedBy(callbackResends()))
        .containsExactly("delivery.attemptCap=6", "delivery.resendsAfterTheFirstAttempt=5");
    assertThat(expectedBy(controlApi()))
        .containsExactly("scenario.httpStatus=201", "fixture.httpStatus=201");
    assertThat(expectedBy(auditTrail()))
        .containsExactly("audit.direction=INBOUND_REQUEST", "audit.outcome=SUCCESS");
  }

  @Test
  void the_happy_path_holds_when_the_envelope_carries_the_published_success_status() {
    SmokeRun run =
        runner.run(
            List.of(happyPath()), prepared().answering("POST", CREATE, 200, A_HOLD_WAS_CREATED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void
      the_happy_path_reads_its_two_hundred_from_the_published_rule_and_its_status_from_the_example() {
    assertThat(evidenceBy(happyPath()))
        .containsExactly(
            "httpStatus from grow/every-answer-is-http-200, published rule",
            "status from grow/create-payment-process-approved, published example");
  }

  @Test
  void the_happy_path_fails_naming_the_status_field_and_the_sample_it_was_read_from() {
    String driftedToTwo = A_HOLD_WAS_CREATED.replace("\"status\":1", "\"status\":2");

    SmokeRun run =
        runner.run(List.of(happyPath()), prepared().answering("POST", CREATE, 200, driftedToTwo));

    assertThat(run.verdicts())
        .singleElement()
        .isInstanceOf(SmokeVerdict.Failed.class)
        .satisfies(
            verdict -> {
              SmokeVerdict.Failed failed = (SmokeVerdict.Failed) verdict;
              assertThat(failed.provider().value()).isEqualTo("grow");
              assertThat(failed.operation())
                  .isEqualTo(GrowCompatibilityChecks.CREATE_PAYMENT_PROCESS);
              assertThat(failed.field()).isEqualTo("status");
              assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
              assertThat(failed.provenance().sample())
                  .isEqualTo("grow/create-payment-process-approved");
            });
  }

  @Test
  void the_happy_path_fails_naming_the_http_status_when_the_route_stops_answering_two_hundred() {
    SmokeRun run =
        runner.run(
            List.of(happyPath()), prepared().answering("POST", CREATE, 500, A_HOLD_WAS_CREATED));

    assertThat(((SmokeVerdict.Failed) run.verdicts().get(0)).field()).isEqualTo("httpStatus");
  }

  @Test
  void the_decline_holds_when_the_envelope_carries_the_published_failure_id() {
    SmokeRun run =
        runner.run(List.of(decline()), prepared().answering("POST", CREATE, 200, THE_CARD_EXPIRED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_decline_fails_naming_the_error_id_field_and_the_published_decline() {
    String anotherCode = THE_CARD_EXPIRED.replace("\"id\":103", "\"id\":104");

    SmokeRun run =
        runner.run(List.of(decline()), prepared().answering("POST", CREATE, 200, anotherCode));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("err.id");
    assertThat(failed.provenance().sample()).isEqualTo("grow/token-charge-declined");
  }

  @Test
  void the_credential_check_holds_when_an_unknown_user_is_refused_with_the_published_id() {
    SmokeRun run =
        runner.run(
            List.of(credentials()),
            prepared().answering("POST", CREATE, 200, THE_CREDENTIAL_WAS_REFUSED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_credential_check_fails_when_an_unknown_user_is_accepted() {
    SmokeRun run =
        runner.run(
            List.of(credentials()), prepared().answering("POST", CREATE, 200, A_HOLD_WAS_CREATED));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("err.id");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_callback_signature_check_does_not_apply_and_says_why() {
    SmokeRun run = runner.run(List.of(callbackSignature()), prepared());

    SmokeVerdict.NotApplicable withheld = (SmokeVerdict.NotApplicable) run.verdicts().get(0);
    assertThat(withheld.operation()).isEqualTo(GrowCompatibilityChecks.THE_CALLBACK_CARRIES);
    assertThat(withheld.provenance().sample()).isEqualTo("grow/unsigned-callback");
    assertThat(withheld.reason()).contains("signs no server-to-server callback");
  }

  @Test
  void the_control_api_check_holds_when_a_scenario_and_a_fixture_are_both_created() {
    SmokeRun run = runner.run(List.of(controlApi()), prepared());

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_control_api_check_fails_naming_the_fixture_when_creation_stops_answering_created() {
    CannedTransport drifted =
        prepared().answering("POST", "/__simulator/grow/fixtures/payment-methods", 200, "{}");

    SmokeRun run = runner.run(List.of(controlApi()), drifted);

    assertThat(((SmokeVerdict.Failed) run.verdicts().get(0)).field())
        .isEqualTo("fixture.httpStatus");
  }

  @Test
  void the_audit_check_holds_when_the_trail_opens_with_the_request_and_carries_the_verdict() {
    SmokeRun run =
        runner.run(
            List.of(auditTrail()), prepared().answering("POST", CREATE, 200, A_HOLD_WAS_CREATED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_audit_check_fails_naming_the_direction_when_no_inbound_row_is_written() {
    CannedTransport withoutTheInboundRow =
        prepared()
            .answering("POST", CREATE, 200, A_HOLD_WAS_CREATED)
            .answering(
                "GET",
                "/__simulator/grow/audit",
                200,
                "[{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]");

    SmokeRun run = runner.run(List.of(auditTrail()), withoutTheInboundRow);

    assertThat(((SmokeVerdict.Failed) run.verdicts().get(0)).field()).isEqualTo("audit.direction");
  }

  @Test
  void the_resend_check_holds_when_a_refused_callback_is_still_owed_the_published_six_attempts() {
    SmokeRun run =
        runner.run(
            List.of(callbackResends()),
            prepared().answering("POST", CREATE, 200, A_HOLD_WAS_CREATED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_resend_check_fails_naming_the_attempt_cap_when_grow_stops_overriding_the_common_five() {
    String backOnTheCommonFive =
        A_CALLBACK_THE_MERCHANT_REFUSED.replace("\"attemptCap\":6", "\"attemptCap\":5");

    SmokeRun run =
        runner.run(
            List.of(callbackResends()),
            prepared()
                .answering("POST", CREATE, 200, A_HOLD_WAS_CREATED)
                .answering("GET", "/__simulator/grow/webhooks", 200, backOnTheCommonFive));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.operation()).isEqualTo(GrowCompatibilityChecks.THE_CALLBACK_IS_RESENT);
    assertThat(failed.field()).isEqualTo("delivery.attemptCap");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
    assertThat(failed.provenance().sample()).isEqualTo("grow/server-update-retry-schedule");
  }

  @Test
  void the_resend_check_fails_when_the_resends_left_after_the_first_attempt_are_not_the_five() {
    String spentTwoAttemptsAtOnce =
        A_CALLBACK_THE_MERCHANT_REFUSED.replace("\"attemptsMade\":1", "\"attemptsMade\":2");

    SmokeRun run =
        runner.run(
            List.of(callbackResends()),
            prepared()
                .answering("POST", CREATE, 200, A_HOLD_WAS_CREATED)
                .answering("GET", "/__simulator/grow/webhooks", 200, spentTwoAttemptsAtOnce));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("delivery.resendsAfterTheFirstAttempt");
    assertThat(failed.provenance().sample()).isEqualTo("grow/unacknowledged-update-resends");
  }

  @Test
  void the_resend_check_reports_nothing_observed_when_no_callback_of_the_check_run_was_queued() {
    SmokeRun run =
        runner.run(
            List.of(callbackResends()),
            prepared()
                .answering("POST", CREATE, 200, A_HOLD_WAS_CREATED)
                .answering("GET", "/__simulator/grow/webhooks", 200, "[]"));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("delivery.attemptCap");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_resend_check_reports_nothing_observed_when_the_delivery_names_no_attempt_it_has_made() {
    String withoutTheAttemptCount =
        A_CALLBACK_THE_MERCHANT_REFUSED.replace(",\"attemptsMade\":1", "");

    SmokeRun run =
        runner.run(
            List.of(callbackResends()),
            prepared()
                .answering("POST", CREATE, 200, A_HOLD_WAS_CREATED)
                .answering("GET", "/__simulator/grow/webhooks", 200, withoutTheAttemptCount));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("delivery.resendsAfterTheFirstAttempt");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_resend_check_reports_nothing_observed_when_a_count_it_read_is_not_a_number_at_all() {
    String countedInWords =
        A_CALLBACK_THE_MERCHANT_REFUSED.replace("\"attemptsMade\":1", "\"attemptsMade\":\"once\"");

    SmokeRun run =
        runner.run(
            List.of(callbackResends()),
            prepared()
                .answering("POST", CREATE, 200, A_HOLD_WAS_CREATED)
                .answering("GET", "/__simulator/grow/webhooks", 200, countedInWords));

    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("delivery.resendsAfterTheFirstAttempt");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_resend_check_queues_no_callback_when_the_hold_it_created_answered_with_no_process_id() {
    String withoutAProcessId = A_HOLD_WAS_CREATED.replace("\"processId\":\"123456\",", "");
    CannedTransport wire = prepared().answering("POST", CREATE, 200, withoutAProcessId);

    SmokeRun run = runner.run(List.of(callbackResends()), wire);

    assertThat(wire.sent())
        .noneMatch(request -> "/__simulator/grow/webhooks".equals(request.path()));
    SmokeVerdict.Failed failed = (SmokeVerdict.Failed) run.verdicts().get(0);
    assertThat(failed.field()).isEqualTo("delivery.attemptCap");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_resend_check_queues_the_callback_against_the_process_the_hold_created() {
    CannedTransport wire = prepared().answering("POST", CREATE, 200, A_HOLD_WAS_CREATED);

    runner.run(List.of(callbackResends()), wire);

    assertThat(wire.sent())
        .filteredOn(request -> "/__simulator/grow/webhooks".equals(request.path()))
        .first()
        .satisfies(queued -> assertThat(queued.body()).contains("\"providerObjectId\":\"123456\""));
  }

  @Test
  void every_check_resets_and_configures_the_check_run_before_it_dials_a_provider_route() {
    for (ContractCheck check : checks) {
      if (check.applicability() instanceof Applicability.DoesNotApply) {
        continue;
      }
      CannedTransport wire = prepared().answering("POST", CREATE, 200, A_HOLD_WAS_CREATED);

      runner.run(List.of(check), wire);

      assertThat(wire.sent().get(0).method() + " " + wire.sent().get(0).path())
          .isEqualTo("POST /__simulator/grow/reset");
      assertThat(wire.sent().get(1).method() + " " + wire.sent().get(1).path())
          .isEqualTo("PUT /__simulator/grow/config");
      assertThat(wire.sent().get(1).body()).contains("smoke-user").contains("smoke-page");
    }
  }

  @Test
  void the_hold_a_check_submits_carries_the_fields_grow_requires_of_a_suspended_charge() {
    CannedTransport wire = prepared().answering("POST", CREATE, 200, A_HOLD_WAS_CREATED);

    runner.run(List.of(happyPath()), wire);

    assertThat(wire.sent().get(2).body())
        .contains("chargeType=2")
        .contains("sum=120.50")
        .contains("userId=smoke-user")
        .contains("pageCode=smoke-page")
        .contains("pageField%5BfullName%5D")
        .contains("pageField%5Bphone%5D")
        .contains("successUrl")
        .contains("cancelUrl")
        .contains("description");
  }

  @Test
  void the_credential_check_sends_a_user_no_account_of_the_check_run_answers_to() {
    CannedTransport wire = prepared().answering("POST", CREATE, 200, THE_CREDENTIAL_WAS_REFUSED);

    runner.run(List.of(credentials()), wire);

    assertThat(wire.sent().get(2).body())
        .contains("userId=no-account-answers-to-this")
        .doesNotContain("userId=smoke-user");
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

  private ContractCheck happyPath() {
    return checks.get(0);
  }

  private ContractCheck decline() {
    return checks.get(1);
  }

  private ContractCheck credentials() {
    return checks.get(2);
  }

  private ContractCheck callbackSignature() {
    return checks.get(3);
  }

  private ContractCheck callbackResends() {
    return checks.get(4);
  }

  private ContractCheck controlApi() {
    return checks.get(5);
  }

  private ContractCheck auditTrail() {
    return checks.get(6);
  }

  private static CannedTransport prepared() {
    return new CannedTransport()
        .answering("POST", "/__simulator/grow/reset", 200, "{}")
        .answering("PUT", "/__simulator/grow/config", 200, "{}")
        .answering("POST", "/__simulator/grow/scenarios", 201, "{\"scenarioId\":\"scn_a\"}")
        .answering(
            "POST", "/__simulator/grow/fixtures/payment-methods", 201, "{\"fixtureId\":\"fx_a\"}")
        .answering("GET", "/__simulator/grow/audit", 200, THE_TRAIL_OF_ONE_EXCHANGE)
        .answering("POST", "/__simulator/grow/webhooks", 202, "{}")
        .answering("GET", "/__simulator/grow/webhooks", 200, A_CALLBACK_THE_MERCHANT_REFUSED);
  }
}
