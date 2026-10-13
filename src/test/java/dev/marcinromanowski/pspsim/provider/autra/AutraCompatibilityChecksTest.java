package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.smoke.CannedTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.smoke.FailureKind;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AutraCompatibilityChecksTest {

  private static final String TOKEN = "/v1/oauth/token";

  private static final String INITIALIZE = "/v1/acquiring/payments/initialize";

  private static final String SALE = "/v1/acquiring/payments";

  private static final String PIX_INITIALIZE = "/v1/acquiring/pix/initialize";

  private static final String PIX_CHECKOUT = "/v1/acquiring/pix/checkout";

  private static final String WEBHOOKS = "/__simulator/autra/webhooks";

  private static final String AUDIT = "/__simulator/autra/audit";

  private static final String THE_BEARER = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

  private static final String THE_SESSION_TOKEN = "eyJhbGciOi-a-session";

  private static final String A_TOKEN_WAS_MINTED =
      "{\"accessToken\":\"" + THE_BEARER + "\",\"tokenType\":\"Bearer\",\"expiresIn\":3600}";

  private static final String A_SESSION_WAS_OPENED =
      "{\"transactionalToken\":\"" + THE_SESSION_TOKEN + "\"}";

  private static final String THE_SALE_WAS_APPROVED =
      "{\"paymentID\":\"550e8400-e29b-41d4-a716-446655440000\",\"status\":\"ACCEPTED\","
          + "\"amount\":150.00,\"currency\":\"BRL\"}";

  private static final String THE_SALE_WAS_DECLINED =
      "{\"paymentID\":\"550e8400-e29b-41d4-a716-446655440001\",\"errors\":[{\"code\":"
          + "\"INSUFFICIENT_FUNDS\",\"msg\":\"insufficient funds\"}]}";

  private static final String A_PIX_WAS_CREATED =
      "{\"pixId\":\"cdb445694be7486e830f720658f9a1a1\",\"status\":\"PENDING\"}";

  private static final String THE_DELIVERY_IT_SIGNED =
      "[{\"attempts\":[{\"sentHeaders\":{\"X-Autra-Signature\":[\"sha256=3b1f\"],"
          + "\"User-Agent\":[\"Autra-Webhooks/1.0\"]}}]}]";

  private static final String THE_TRAIL_OF_ONE_EXCHANGE =
      "[{\"direction\":\"INBOUND_REQUEST\",\"outcome\":null},"
          + "{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]";

  private final List<ContractCheck> checks =
      new AutraCompatibilityChecks(new CheckJson(), new CountingIds()).all();

  private final SmokeRunner runner = new SmokeRunner(Instant::now, provider -> List.of());

  @Test
  void the_provider_publishes_a_check_for_six_operations_and_withholds_none() {
    assertThat(checks).hasSize(6);
    assertThat(checks).allSatisfy(check -> assertThat(check.expectations()).isNotEmpty());
  }

  @Test
  void every_value_a_check_expects_is_the_one_the_autra_samples_publish() {
    assertThat(expectedBy(check(0))).containsExactly("tokenType=Bearer", "expiresIn=3600");
    assertThat(expectedBy(check(1)))
        .containsExactly("httpStatus=400", "error=invalid clientSecret");
    assertThat(expectedBy(check(2)))
        .containsExactly("status=ACCEPTED", "currency=BRL", "identifier=paymentID");
    assertThat(expectedBy(check(3)))
        .containsExactly(
            "httpStatus=200", "errors.code=INSUFFICIENT_FUNDS", "errors.msg=insufficient funds");
    assertThat(expectedBy(check(4)))
        .containsExactly("signature.prefix=sha256=", "userAgent=Autra-Webhooks/1.0");
    assertThat(expectedBy(check(5)))
        .containsExactly("audit.direction=INBOUND_REQUEST", "audit.outcome=SUCCESS");
  }

  @Test
  void the_callback_check_reads_a_bare_separator_as_a_prefix_that_is_not_the_published_one() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            aPaidPixDelivering(THE_DELIVERY_IT_SIGNED.replace("sha256=3b1f", "=3b1f")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("signature.prefix");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
  }

  @Test
  void the_callback_check_reads_no_prefix_from_a_signature_that_names_no_algorithm() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            aPaidPixDelivering(THE_DELIVERY_IT_SIGNED.replace("sha256=3b1f", "3b1f")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("signature.prefix");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_token_check_holds_when_the_answer_carries_the_reference_spelling_and_the_hour() {
    SmokeRun run =
        runner.run(List.of(check(0)), prepared().answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_token_check_fails_naming_the_token_type_when_the_guides_snake_case_is_served() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared()
                .answering(
                    "POST",
                    TOKEN,
                    200,
                    "{\"access_token\":\""
                        + THE_BEARER
                        + "\",\"token_type\":\"Bearer\","
                        + "\"expires_in\":3600}"));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("tokenType");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
    assertThat(failed.provenance().sample()).startsWith("autra/oauth-");
  }

  @Test
  void the_token_check_fails_naming_the_validity_when_the_answer_publishes_another_lifetime() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared()
                .answering(
                    "POST",
                    TOKEN,
                    200,
                    A_TOKEN_WAS_MINTED.replace("\"expiresIn\":3600", "\"expiresIn\":900")));

    assertThat(theFailure(run).field()).isEqualTo("expiresIn");
  }

  @Test
  void the_credential_refusal_holds_when_a_wrong_secret_is_answered_400_with_the_published_text() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared().answering("POST", TOKEN, 400, "{\"error\":\"invalid clientSecret\"}"));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_credential_refusal_fails_naming_the_status_when_it_is_answered_the_guides_401() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared().answering("POST", TOKEN, 401, "{\"error\":\"invalid clientSecret\"}"));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("httpStatus");
    assertThat(failed.provenance().sample()).startsWith("autra/oauth-");
  }

  @Test
  void the_credential_refusal_fails_naming_the_error_when_another_published_message_is_served() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared()
                .answering(
                    "POST",
                    TOKEN,
                    400,
                    "{\"error\":\"invalid clientId or inactive credentials\"}"));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("error");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
  }

  @Test
  void the_sale_holds_when_the_answer_carries_the_published_status_currency_and_identifier() {
    SmokeRun run = runner.run(List.of(check(2)), aSaleAnswering(THE_SALE_WAS_APPROVED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_sale_fails_naming_the_status_when_the_acquirer_declined_it_in_the_status_field() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            aSaleAnswering(THE_SALE_WAS_APPROVED.replace("ACCEPTED", "DECLINED")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("status");
    assertThat(failed.provenance().sample()).isEqualTo("autra/sale-approved");
  }

  @Test
  void the_sale_fails_naming_the_identifier_when_the_answer_spells_it_with_a_lowercase_d() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            aSaleAnswering(THE_SALE_WAS_APPROVED.replace("paymentID", "paymentId")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("identifier");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
    assertThat(failed.provenance().sample()).isEqualTo("autra/sale-approved");
  }

  @Test
  void the_sale_fails_naming_the_identifier_when_the_answer_names_no_payment_at_all() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            aSaleAnswering("{\"status\":\"ACCEPTED\",\"amount\":150.00,\"currency\":\"BRL\"}"));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("identifier");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_sale_observes_no_identifier_in_an_answer_that_is_json_but_not_an_object() {
    assertThat(check(2).observe(aSaleAnswering("[\"paymentID\"]"))).isEmpty();
  }

  @Test
  void the_decline_holds_when_the_refusal_travels_in_the_body_of_a_two_hundred() {
    SmokeRun run = runner.run(List.of(check(3)), aSaleAnswering(THE_SALE_WAS_DECLINED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_decline_fails_naming_the_http_status_when_the_proses_422_is_served_instead() {
    SmokeRun run =
        runner.run(
            List.of(check(3)),
            prepared()
                .answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED)
                .answering("POST", INITIALIZE, 200, A_SESSION_WAS_OPENED)
                .answering("POST", SALE, 422, THE_SALE_WAS_DECLINED));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("httpStatus");
    assertThat(failed.provenance().sample()).isEqualTo("autra/sale-declined");
  }

  @Test
  void the_decline_fails_naming_the_message_when_the_published_code_carries_another_text() {
    SmokeRun run =
        runner.run(
            List.of(check(3)),
            aSaleAnswering(THE_SALE_WAS_DECLINED.replace("insufficient funds", "no funds")));

    assertThat(theFailure(run).field()).isEqualTo("errors.msg");
  }

  @Test
  void the_callback_check_holds_when_the_delivery_carries_the_signature_and_the_fixed_agent() {
    SmokeRun run = runner.run(List.of(check(4)), aPaidPixDelivering(THE_DELIVERY_IT_SIGNED));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_callback_check_fails_naming_the_prefix_when_the_delivery_carries_no_signature() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            aPaidPixDelivering(
                "[{\"attempts\":[{\"sentHeaders\":{\"User-Agent\":[\"Autra-Webhooks/1.0\"]}}]}]"));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("signature.prefix");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_callback_check_fails_naming_the_prefix_when_the_signature_names_another_algorithm() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            aPaidPixDelivering(THE_DELIVERY_IT_SIGNED.replace("sha256=", "sha512=")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("signature.prefix");
    assertThat(failed.provenance().sample()).isEqualTo("autra/acquiring-webhook-headers");
  }

  @Test
  void the_callback_check_fails_naming_the_agent_when_the_delivery_travels_under_another_one() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            aPaidPixDelivering(THE_DELIVERY_IT_SIGNED.replace("Autra-Webhooks/1.0", "curl/8.0")));

    assertThat(theFailure(run).field()).isEqualTo("userAgent");
  }

  @Test
  void the_callback_check_fails_naming_the_prefix_when_the_checkout_answers_no_pix_at_all() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            prepared()
                .answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED)
                .answering("POST", PIX_INITIALIZE, 200, A_SESSION_WAS_OPENED)
                .answering("POST", PIX_CHECKOUT, 200, "{}")
                .answering("GET", WEBHOOKS, 200, THE_DELIVERY_IT_SIGNED));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("signature.prefix");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_trail_check_holds_when_the_trail_opens_with_the_request_and_carries_the_verdict() {
    SmokeRun run =
        runner.run(
            List.of(check(5)),
            prepared()
                .answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED)
                .answering("GET", AUDIT, 200, THE_TRAIL_OF_ONE_EXCHANGE));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_trail_check_fails_naming_the_outcome_when_the_second_row_carries_another_verdict() {
    SmokeRun run =
        runner.run(
            List.of(check(5)),
            prepared()
                .answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED)
                .answering(
                    "GET",
                    AUDIT,
                    200,
                    THE_TRAIL_OF_ONE_EXCHANGE.replace("\"SUCCESS\"", "\"FAILURE\"")));

    assertThat(theFailure(run).field()).isEqualTo("audit.outcome");
  }

  @Test
  void the_sale_it_sends_carries_the_bearer_the_token_route_minted_and_the_session_it_opened() {
    CannedTransport transport = aSaleAnswering(THE_SALE_WAS_APPROVED);

    runner.run(List.of(check(2)), transport);

    CheckRequest sale = theSaleSentBy(transport);
    assertThat(sale.headers().get("Authorization")).isEqualTo("Bearer " + THE_BEARER);
    assertThat(sale.body()).contains("\"transactionalToken\":\"" + THE_SESSION_TOKEN + "\"");
    assertThat(sale.body()).contains("\"transactionType\":\"CREDIT\"");
    assertThat(sale.body()).contains("\"currency\":\"BRL\"");
  }

  @Test
  void the_sale_it_sends_charges_the_slug_of_the_card_it_seeded_first() {
    CannedTransport transport = aSaleAnswering(THE_SALE_WAS_APPROVED);

    runner.run(List.of(check(2)), transport);

    assertThat(
            String.valueOf(
                theSentTo(transport, "POST", "/__simulator/autra/fixtures/payment-methods").body()))
        .contains("\"token\":\"" + AutraCompatibilityChecks.THE_SMOKE_SLUG + "\"");
    assertThat(theSaleSentBy(transport).body())
        .contains("\"slugToken\":\"" + AutraCompatibilityChecks.THE_SMOKE_SLUG + "\"");
  }

  @Test
  void the_token_request_it_sends_carries_the_basic_credential_of_the_account_it_configured() {
    CannedTransport transport = prepared().answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED);

    runner.run(List.of(check(0)), transport);

    String configured =
        String.valueOf(theSentTo(transport, "PUT", "/__simulator/autra/config").body());
    Matcher clientId = Pattern.compile("\"clientId\":\"([^\"]+)\"").matcher(configured);
    Matcher clientSecret = Pattern.compile("\"clientSecret\":\"([^\"]+)\"").matcher(configured);
    assertThat(clientId.find()).isTrue();
    assertThat(clientSecret.find()).isTrue();

    assertThat(theSentTo(transport, "POST", TOKEN).headers().get("Authorization"))
        .isEqualTo(
            "Basic "
                + Base64.getEncoder()
                    .encodeToString(
                        (clientId.group(1) + ":" + clientSecret.group(1))
                            .getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void the_callback_check_completes_the_pix_the_checkout_answered_as_the_payer_would() {
    CannedTransport transport = aPaidPixDelivering(THE_DELIVERY_IT_SIGNED);

    runner.run(List.of(check(4)), transport);

    assertThat(transport.sent())
        .extracting(CheckRequest::path)
        .contains("/__simulator/autra/hosted/cdb445694be7486e830f720658f9a1a1/complete");
  }

  private static CheckRequest theSentTo(CannedTransport transport, String method, String path) {
    for (CheckRequest sent : transport.sent()) {
      if (method.equals(sent.method()) && sent.path().startsWith(path)) {
        return sent;
      }
    }
    throw new AssertionError("no " + method + " " + path + " was sent");
  }

  private static CheckRequest theSaleSentBy(CannedTransport transport) {
    for (CheckRequest sent : transport.sent()) {
      if (SALE.equals(sent.path())) {
        return sent;
      }
    }
    throw new AssertionError("no sale was sent");
  }

  private static CannedTransport aSaleAnswering(String body) {
    return prepared()
        .answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED)
        .answering("POST", INITIALIZE, 200, A_SESSION_WAS_OPENED)
        .answering("POST", SALE, 200, body);
  }

  private static CannedTransport aPaidPixDelivering(String deliveries) {
    return prepared()
        .answering("POST", TOKEN, 200, A_TOKEN_WAS_MINTED)
        .answering("POST", PIX_INITIALIZE, 200, A_SESSION_WAS_OPENED)
        .answering("POST", PIX_CHECKOUT, 200, A_PIX_WAS_CREATED)
        .answering("GET", WEBHOOKS, 200, deliveries);
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
        .answering("POST", "/__simulator/autra/reset", 200, "{}")
        .answering("PUT", "/__simulator/autra/config", 200, "{}")
        .answering("POST", "/__simulator/autra/fixtures/payment-methods", 201, "{}")
        .answering("POST", "/__simulator/autra/scenarios", 201, "{}")
        .answering("POST", "/__simulator/autra/hosted/", 200, "{}");
  }
}
