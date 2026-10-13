package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.smoke.CannedTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.smoke.FailureKind;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WorldlineCompatibilityChecksTest {

  private static final String PAYMENTS = "/v2/pspid-1/payments";

  private static final String THE_HELD_PAYMENT = "3066019730_0";

  private static final String THE_CAPTURE = PAYMENTS + "/" + THE_HELD_PAYMENT + "/capture";

  private static final String CONFIG = "/__simulator/worldline/config";

  private static final String AUDIT = "/__simulator/worldline/audit";

  private static final String A_HOLD_WAS_CREATED =
      "{\"creationOutput\":{},\"payment\":{\"id\":\""
          + THE_HELD_PAYMENT
          + "\",\"status\":\"PENDING_CAPTURE\",\"statusOutput\":{\"statusCategory\":"
          + "\"PENDING_MERCHANT\",\"statusCode\":5,\"isCancellable\":true}}}";

  private static final String THE_CAPTURE_MINTED_AN_IDENTIFIER =
      "{\"captureOutput\":{},\"status\":\"CAPTURED\",\"statusOutput\":{\"statusCode\":9},"
          + "\"id\":\"3066019730_1\"}";

  private static final String THE_CREDENTIAL_WAS_REFUSED =
      "{\"errorId\":\"4dac7acb\",\"errors\":[{\"category\":\"DIRECT_PLATFORM_ERROR\","
          + "\"httpStatusCode\":403,\"id\":\"ACCESS_TO_MERCHANT_NOT_ALLOWED\",\"retriable\":false}]}";

  private static final String NO_PAYMENT_ANSWERS_TO_IT =
      "{\"errorId\":\"4dac7acb\",\"errors\":[{\"errorCode\":\"50001130\",\"category\":"
          + "\"DIRECT_PLATFORM_ERROR\",\"code\":\"1002\",\"httpStatusCode\":404,\"id\":"
          + "\"UNKNOWN_PAYMENT_ID\",\"propertyName\":\"paymentId\",\"retriable\":false}]}";

  private static final String THE_TRAIL_OF_ONE_EXCHANGE =
      "[{\"direction\":\"INBOUND_REQUEST\",\"outcome\":null},"
          + "{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]";

  private static final Pattern THE_CONFIGURED_KEY = Pattern.compile("\"apiKey\":\"([^\"]+)\"");

  private static final Pattern THE_CONFIGURED_SECRET =
      Pattern.compile("\"apiSecret\":\"([^\"]+)\"");

  private static final Instant AT = Instant.parse("2026-09-25T12:00:00Z");

  private final List<ContractCheck> checks =
      new WorldlineCompatibilityChecks(
              new CheckJson(), new CountingIds(), WorldlineDoubles.clockAt(AT))
          .all();

  private final SmokeRunner runner = new SmokeRunner(Instant::now, provider -> List.of());

  @Test
  void the_provider_publishes_a_check_for_six_operations_and_withholds_none() {
    assertThat(checks).hasSize(6);
    assertThat(checks).allSatisfy(check -> assertThat(check.expectations()).isNotEmpty());
  }

  @Test
  void the_refused_signature_reads_its_status_and_id_from_the_published_rule_and_no_answer_body() {
    assertThat(evidenceBy(check(2)))
        .containsExactly(
            "httpStatus from worldline/access-to-merchant-not-allowed, published rule",
            "errors.id from worldline/access-to-merchant-not-allowed, published rule");
  }

  @Test
  void every_value_a_check_expects_is_the_one_the_worldline_samples_publish() {
    assertThat(expectedBy(check(0)))
        .containsExactly(
            "payment.status=PENDING_CAPTURE",
            "payment.statusOutput.statusCategory=PENDING_MERCHANT",
            "payment.statusOutput.statusCode=5");
    assertThat(expectedBy(check(1))).containsExactly("payment.payIdSub=_0", "capture.payIdSub=_1");
    assertThat(expectedBy(check(2)))
        .containsExactly("httpStatus=403", "errors.id=ACCESS_TO_MERCHANT_NOT_ALLOWED");
    assertThat(expectedBy(check(3)))
        .containsExactly(
            "httpStatus=404",
            "errors.id=UNKNOWN_PAYMENT_ID",
            "errors.category=DIRECT_PLATFORM_ERROR",
            "errors.errorCode=50001130");
    assertThat(expectedBy(check(4)))
        .containsExactly("replay.body=true", "replay.header=X-GCS-Idempotence-Request-Timestamp");
    assertThat(expectedBy(check(5)))
        .containsExactly("audit.direction=INBOUND_REQUEST", "audit.outcome=SUCCESS");
  }

  @Test
  void the_hold_holds_when_the_answer_carries_the_published_status_category_and_code() {
    SmokeRun run =
        runner.run(
            List.of(check(0)), prepared().answering("POST", PAYMENTS, 201, A_HOLD_WAS_CREATED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_hold_fails_naming_the_status_when_the_payment_is_answered_as_merely_created() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared()
                .answering(
                    "POST",
                    PAYMENTS,
                    201,
                    A_HOLD_WAS_CREATED.replace("PENDING_CAPTURE", "CREATED")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("payment.status");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
    assertThat(failed.provenance().sample()).isEqualTo("worldline/create-payment-pending-capture");
  }

  @Test
  void the_hold_fails_naming_the_status_code_when_the_answer_carries_the_captured_code_instead() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared()
                .answering(
                    "POST",
                    PAYMENTS,
                    201,
                    A_HOLD_WAS_CREATED.replace("\"statusCode\":5", "\"statusCode\":9")));

    assertThat(theFailure(run).field()).isEqualTo("payment.statusOutput.statusCode");
  }

  @Test
  void the_maintenance_check_holds_when_the_capture_answers_the_next_payidsub() {
    SmokeRun run = runner.run(List.of(check(1)), aHoldAndItsCapture());

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void
      the_maintenance_check_fails_naming_the_capture_when_it_answers_the_identifier_it_was_given() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared()
                .answering("POST", PAYMENTS, 201, A_HOLD_WAS_CREATED)
                .answering(
                    "POST",
                    THE_CAPTURE,
                    201,
                    THE_CAPTURE_MINTED_AN_IDENTIFIER.replace("3066019730_1", THE_HELD_PAYMENT)));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("capture.payIdSub");
    assertThat(failed.provenance().sample()).isEqualTo("worldline/capture-response");
  }

  @Test
  void the_maintenance_check_fails_naming_the_payment_when_the_hold_answers_no_identifier() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared().answering("POST", PAYMENTS, 201, "{\"creationOutput\":{},\"payment\":{}}"));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("payment.payIdSub");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_maintenance_check_reads_a_payidsub_only_after_the_separator_the_vendor_formula_spells() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared()
                .answering(
                    "POST",
                    PAYMENTS,
                    201,
                    A_HOLD_WAS_CREATED.replace(THE_HELD_PAYMENT, "3066019730-0")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("payment.payIdSub");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_maintenance_check_fails_naming_the_payment_when_the_identifier_carries_no_payidsub() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared()
                .answering(
                    "POST",
                    PAYMENTS,
                    201,
                    A_HOLD_WAS_CREATED.replace(THE_HELD_PAYMENT, "3066019730")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("payment.payIdSub");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_signature_refusal_holds_when_a_wrong_signature_is_answered_403_with_the_published_id() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            prepared().answering("POST", PAYMENTS, 403, THE_CREDENTIAL_WAS_REFUSED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_signature_refusal_fails_naming_the_status_when_it_is_answered_the_401_direct_omits() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            prepared().answering("POST", PAYMENTS, 401, THE_CREDENTIAL_WAS_REFUSED));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("httpStatus");
    assertThat(failed.provenance().sample()).isEqualTo("worldline/access-to-merchant-not-allowed");
  }

  @Test
  void the_unknown_payment_check_holds_when_the_capture_is_answered_the_published_error_object() {
    SmokeRun run =
        runner.run(
            List.of(check(3)),
            prepared().answering("POST", PAYMENTS, 404, NO_PAYMENT_ANSWERS_TO_IT));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_unknown_payment_check_fails_naming_the_category_when_the_error_carries_the_other_one() {
    SmokeRun run =
        runner.run(
            List.of(check(3)),
            prepared()
                .answering(
                    "POST",
                    PAYMENTS,
                    404,
                    NO_PAYMENT_ANSWERS_TO_IT.replace(
                        "DIRECT_PLATFORM_ERROR", "PAYMENT_PLATFORM_ERROR")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("errors.category");
    assertThat(failed.provenance().sample()).isEqualTo("worldline/unknown-payment-id");
  }

  @Test
  void the_replay_check_holds_when_the_repeat_answers_the_same_body_and_the_timestamp_header() {
    SmokeRun run = runner.run(List.of(check(4)), prepared().answering("POST", PAYMENTS, aReplay()));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_replay_check_fails_naming_the_body_when_the_repeat_answers_a_second_payment() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            prepared()
                .answeringInTurn(
                    "POST",
                    PAYMENTS,
                    aReplay(),
                    new CheckResponse(
                        201,
                        Map.of(
                            WorldlineFields.IDEMPOTENCE_REQUEST_TIMESTAMP,
                            List.of("1600000000000")),
                        A_HOLD_WAS_CREATED.replace(THE_HELD_PAYMENT, "3066019731_0"))));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("replay.body");
    assertThat(failed.provenance().sample()).isEqualTo("worldline/idempotent-replay");
  }

  @Test
  void the_replay_check_fails_naming_the_header_when_the_repeat_announces_no_first_request() {
    SmokeRun run =
        runner.run(
            List.of(check(4)), prepared().answering("POST", PAYMENTS, 201, A_HOLD_WAS_CREATED));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("replay.header");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_trail_check_holds_when_the_trail_opens_with_the_request_and_carries_the_verdict() {
    SmokeRun run =
        runner.run(
            List.of(check(5)),
            prepared()
                .answering("POST", PAYMENTS, 201, A_HOLD_WAS_CREATED)
                .answering("GET", AUDIT, 200, THE_TRAIL_OF_ONE_EXCHANGE));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_trail_check_fails_naming_the_direction_when_the_trail_opens_with_the_response() {
    SmokeRun run =
        runner.run(
            List.of(check(5)),
            prepared()
                .answering("POST", PAYMENTS, 201, A_HOLD_WAS_CREATED)
                .answering(
                    "GET",
                    AUDIT,
                    200,
                    "[{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]"));

    assertThat(theFailure(run).field()).isEqualTo("audit.direction");
  }

  @Test
  void the_hold_it_sends_dates_itself_from_the_clock_in_the_rfc_1123_worldline_publishes() {
    CannedTransport transport = prepared().answering("POST", PAYMENTS, 201, A_HOLD_WAS_CREATED);

    runner.run(List.of(check(0)), transport);

    String date = theHoldSentBy(transport).headers().get(WorldlineFields.DATE);
    assertThat(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(date))).isEqualTo(AT);
  }

  @Test
  void the_signature_it_sends_is_the_canonical_string_worldline_publishes_for_that_request() {
    CannedTransport transport = prepared().answering("POST", PAYMENTS, aReplay());

    runner.run(List.of(check(4)), transport);

    CheckRequest sent = theHoldSentBy(transport);
    String published =
        "POST\n"
            + "application/json\n"
            + sent.headers().get(WorldlineFields.DATE)
            + "\n"
            + "x-gcs-idempotence-key:"
            + sent.headers()
                .get(WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT)
            + "\n"
            + PAYMENTS
            + "\n";

    assertThat(sent.headers().get(WorldlineFields.AUTHORIZATION))
        .isEqualTo(
            "GCS v1HMAC:"
                + theConfigured(transport, THE_CONFIGURED_KEY)
                + ":"
                + hmacOf(theConfigured(transport, THE_CONFIGURED_SECRET), published));
  }

  private static String hmacOf(String secret, String subject) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder()
          .encodeToString(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new AssertionError(unavailable);
    }
  }

  private static String theConfigured(CannedTransport transport, Pattern property) {
    for (CheckRequest sent : transport.sent()) {
      if (CONFIG.equals(sent.path())) {
        Matcher found = property.matcher(String.valueOf(sent.body()));
        assertThat(found.find()).isTrue();
        return found.group(1);
      }
    }
    throw new AssertionError("no configuration was written");
  }

  private static CheckRequest theHoldSentBy(CannedTransport transport) {
    for (CheckRequest sent : transport.sent()) {
      if (PAYMENTS.equals(sent.path())) {
        return sent;
      }
    }
    throw new AssertionError("no hold was sent");
  }

  private static CannedTransport aHoldAndItsCapture() {
    return prepared()
        .answering("POST", PAYMENTS, 201, A_HOLD_WAS_CREATED)
        .answering("POST", THE_CAPTURE, 201, THE_CAPTURE_MINTED_AN_IDENTIFIER);
  }

  private static CheckResponse aReplay() {
    return new CheckResponse(
        201,
        Map.of(WorldlineFields.IDEMPOTENCE_REQUEST_TIMESTAMP, List.of("1600000000000")),
        A_HOLD_WAS_CREATED);
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

  private ContractCheck check(int ordinal) {
    return checks.get(ordinal);
  }

  private static SmokeVerdict.Failed theFailure(SmokeRun run) {
    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Failed.class);
    return (SmokeVerdict.Failed) run.verdicts().get(0);
  }

  private static CannedTransport prepared() {
    return new CannedTransport()
        .answering("POST", "/__simulator/worldline/reset", 200, "{}")
        .answering("PUT", CONFIG, 200, "{}")
        .answering("POST", "/__simulator/worldline/fixtures/payment-methods", 201, "{}");
  }
}
