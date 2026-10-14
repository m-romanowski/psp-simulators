package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaChallengePageTest {

  static final String TRACK = TranzilaThreeDSecureTest.THE_FIRST_TRACK;

  static final String THE_PAGE = "/tranzila/3ds/challenge/" + TRACK;

  static final String A_THTK = "w0123456789abcdef0123456789abcdef01234567a";

  static final String A_NOTIFY_URL = "https://shop.example/notify";

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void the_challenge_asks_for_the_code_on_a_page_that_posts_back_to_itself_only() {
    challenged("");

    TranzilaApiResponse shown = wired.challengePage.show(aCall("GET", Map.of()), TRACK);

    assertThat(shown.httpStatus()).isEqualTo(200);
    assertThat(shown.headers())
        .containsEntry(
            "Content-Security-Policy",
            List.of("default-src 'none'; style-src 'unsafe-inline'; form-action 'self'"))
        .containsEntry("Cache-Control", List.of("no-store"))
        .containsEntry("Content-Type", List.of("text/html; charset=utf-8"));
    assertThat(bodyOf(shown))
        .contains("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        .contains("<dt>Amount</dt><dd>10.53 ILS</dd><dt>Card</dt><dd>Visa ending 5005</dd>")
        .contains("<form method=\"post\" action=\"" + THE_PAGE + "\">")
        .contains("name=\"code\" inputmode=\"numeric\" autocomplete=\"one-time-code\"")
        .doesNotContain("<script");
    assertThat(shown.providerObjectId()).isEqualTo(TRACK);
    assertThat(wired.audit.all())
        .extracting(AuditRecord::direction)
        .endsWith(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    AuditRecord inbound = theRow(AuditDirection.INBOUND_REQUEST);
    assertThat(inbound.resolutionRule()).isEqualTo(TestRunResolutionRule.OBJECT_CORRELATION);
    assertThat(inbound.providerObjectId()).isNull();
    assertThat(inbound.accountId()).isEqualTo(TranzilaWiring.ACCOUNT);
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).providerObjectId()).isEqualTo(TRACK);
  }

  @Test
  void an_unknown_challenge_is_a_404_page_of_this_simulators_words() {
    TranzilaApiResponse shown = wired.challengePage.show(aCall("GET", Map.of()), "3ds_x");
    TranzilaApiResponse answered = wired.challengePage.answer(aCall("POST", Map.of()), "3ds_x");

    assertThat(shown.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(shown))
        .contains(TranzilaChallengePage.escaped(TranzilaChallengePage.NO_SUCH_CHALLENGE));
    assertThat(answered.httpStatus()).isEqualTo(404);
    assertThat(wired.audit.all()).isEmpty();
  }

  @Test
  void the_code_555_passes_and_the_browser_is_posted_back_with_the_params_and_the_track_id() {
    challenged(
        ",\"auth_3ds_redirect\":{\"url\":\"https://shop.example/back?x=1&y=2\",\"params\":["
            + "{\"key\":\"order\",\"value\":\"o-1\"},{\"key\":\"track_id\",\"value\":\"spoofed\"}]}");

    TranzilaApiResponse answered = answer("555");

    assertThat(answered.httpStatus()).isEqualTo(200);
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.headers())
        .containsEntry(
            "Content-Security-Policy", List.of(TranzilaReturnPage.WHAT_THIS_PAGE_PERMITS));
    assertThat(bodyOf(answered))
        .contains("<form method=\"post\" action=\"https://shop.example/back?x=1&amp;y=2\">")
        .contains(
            "<input type=\"hidden\" name=\"order\" value=\"o-1\">"
                + "<input type=\"hidden\" name=\"track_id\" value=\""
                + TRACK
                + "\">")
        .doesNotContain("spoofed");
    TranzilaChallenge answeredChallenge = wired.challenges.all().get(0);
    assertThat(answeredChallenge.payerVerdict()).isEqualTo(TranzilaChallenge.Verdict.PASSED);
    assertThat(answeredChallenge.answeredAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).sanitizedBody())
        .containsEntry("order", List.of("o-1"))
        .containsEntry("track_id", List.of(TRACK));
    assertThat(wired.provider.state(TranzilaWiring.RUN, TRACK).orElseThrow())
        .extracting(ProviderObjectState::nativeStatus, ProviderObjectState::normalizedStatus)
        .containsExactly("challenge passed, not completed", NormalizedStatus.NOT_INITIALIZED);
  }

  @Test
  void any_other_code_fails_and_with_no_redirect_the_page_says_so_itself() {
    challenged("");

    TranzilaApiResponse answered = answer("554");

    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(answered)).contains(TranzilaChallengePage.FAILED).doesNotContain("<form");
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).refusal())
        .isEqualTo(RefusalSubject.theObject(TRACK));
    assertThat(wired.challenges.all().get(0).payerVerdict())
        .isEqualTo(TranzilaChallenge.Verdict.FAILED);
    assertThat(wired.provider.state(TranzilaWiring.RUN, TRACK).orElseThrow().nativeStatus())
        .isEqualTo("challenge failed, not completed");
  }

  @Test
  void a_challenge_answered_with_no_code_fails() {
    challenged("");

    TranzilaApiResponse answered = wired.challengePage.answer(aCall("POST", Map.of()), TRACK);

    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void the_passed_page_without_a_redirect_says_the_holder_was_authenticated() {
    challenged("");

    assertThat(bodyOf(answer("555"))).contains(TranzilaChallengePage.PASSED);
  }

  @Test
  void a_challenge_is_answered_once_and_shown_no_more_after_it() {
    challenged("");
    answer("555");

    TranzilaApiResponse again = answer("554");
    TranzilaApiResponse shown = wired.challengePage.show(aCall("GET", Map.of()), TRACK);

    assertThat(again.httpStatus()).isEqualTo(410);
    assertThat(shown.httpStatus()).isEqualTo(410);
    assertThat(bodyOf(again)).contains(TranzilaChallengePage.ALREADY_ANSWERED);
    assertThat(wired.challenges.all().get(0).payerVerdict())
        .isEqualTo(TranzilaChallenge.Verdict.PASSED);
  }

  @Test
  void the_fields_posted_back_are_the_params_then_the_track_id_the_simulator_sets() {
    TranzilaChallenge challenge =
        TranzilaThreeDSecureTest.onTheHostedFields(aChallengeWith(Map.of("track_id", "x")));

    assertThat(TranzilaThreeDSecureChallenge.theFieldsPostedTo(challenge))
        .containsExactly(Map.entry("track_id", TRACK));
  }

  @Test
  void a_hosted_fields_charge_of_the_challenge_card_answers_the_challenge_and_charges_nothing() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");

    TranzilaApiResponse answered = charge(aCharge("contact", "Dana", "notify_url", A_NOTIFY_URL));

    assertThat(bodyOf(answered))
        .isEqualTo(
            "{\"challengeUrl\":\"https://simulator.test/tranzila/3ds/challenge/"
                + TRACK
                + "\",\"track_id\":\""
                + TRACK
                + "\"}");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isEqualTo(TRACK);
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.posted).isEmpty();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).sanitizedBody())
        .containsEntry(
            "challengeUrl",
            List.of(dev.marcinromanowski.pspsim.kernel.credentials.Redaction.REDACTED))
        .containsEntry("track_id", List.of(TRACK));
    TranzilaChallenge waiting = wired.challenges.all().get(0);
    assertThat(waiting.forceTxnOnFail()).isTrue();
    TranzilaHostedFieldsEcho echo = waiting.hostedFields();
    assertThat(echo).isNotNull();
    assertThat(echo.thtk()).isEqualTo(A_THTK);
    assertThat(echo.notifyUrl()).isEqualTo(A_NOTIFY_URL);
    assertThat(echo.userFormData())
        .containsExactly(Map.entry("contact", "Dana"), Map.entry("notify_url", A_NOTIFY_URL));
    assertThat(waiting.card().cardMask()).isEqualTo("xxxxxxxxxxxx5005");
  }

  @Test
  void the_answered_page_of_a_hosted_fields_challenge_tells_its_frames_the_track_id() {
    aHandshake("10");
    charge(aCharge());

    TranzilaApiResponse answered = answer("555");

    assertThat(answered.headers())
        .containsEntry(
            "Content-Security-Policy",
            List.of(TranzilaChallengeAnsweredPage.WHAT_THIS_PAGE_PERMITS));
    assertThat(bodyOf(answered))
        .contains("<body data-track-id=\"" + TRACK + "\">")
        .contains("<script>" + TranzilaChallengeAnsweredPage.THE_SCRIPT + "</script>")
        .contains(TranzilaChallengePage.PASSED);
    assertThat(TranzilaChallengeAnsweredPage.WHAT_THIS_PAGE_PERMITS)
        .contains(
            "'sha256-" + PayerScriptHashes.of(TranzilaChallengeAnsweredPage.THE_SCRIPT) + "'");
  }

  @Test
  void a_passed_hosted_fields_challenge_continues_into_the_hashed_answer_and_its_notify() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");
    charge(aCharge("contact", "Dana", "notify_url", A_NOTIFY_URL, "tokenize", "true"));
    answer("555");
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofMinutes(1)));

    TranzilaApiResponse continued = continueOn(TRACK, TranzilaWiring.TERMINAL);

    assertThat(bodyOf(continued))
        .startsWith(
            "{\"errors\":null,\"transaction_response\":{\"success\":true,\"error\":null,"
                + "\"processor_response_code\":\"000\",\"transaction_id\":\"10000001\","
                + "\"auth_number\":\"1000001\",\"amount\":\"10.00\",\"currency_code\":1,"
                + "\"credit_card_last_4_digits\":\"5005\",\"expiry_month\":\"12\","
                + "\"expiry_year\":\"30\",\"user_form_data\":{\"contact\":\"Dana\","
                + "\"notify_url\":\"https://shop.example/notify\"},")
        .contains("\"token\":\"t000000000000015005\"")
        .contains("\"response_hash\":\"");
    assertThat(continued.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(wired.posted)
        .singleElement()
        .asString()
        .startsWith(A_NOTIFY_URL + " supplier=myterminal&sum=10&currency=1&Response=000")
        .contains("&transaction_id=10000001&")
        .contains("&TranzilaTK=t000000000000015005&")
        .endsWith("&ConfirmationCode=1000001");
    assertThat(wired.handshakes.find(TranzilaWiring.RUN, A_THTK).orElseThrow().expiresAt())
        .isEqualTo(TranzilaWiring.NOW.plus(Duration.ofMinutes(21)));
    assertThat(theRow(AuditDirection.INBOUND_REQUEST).resolutionRule())
        .isEqualTo(TestRunResolutionRule.OBJECT_CORRELATION);
  }

  @Test
  void a_continue_repeated_is_refused_900_and_notifies_nothing_more() {
    wired.accounts.callingBack(TranzilaWiring.RUN, TranzilaWiring.ACCOUNT, A_NOTIFY_URL);
    aHandshake("10");
    charge(aCharge("notify_url", A_NOTIFY_URL));
    answer("555");
    continueOn(TRACK, TranzilaWiring.TERMINAL);

    TranzilaApiResponse again = continueOn(TRACK, TranzilaWiring.TERMINAL);

    assertThat(bodyOf(again))
        .startsWith(
            "{\"errors\":[{\"code\":900,\"param\":\"track_id\",\"message\":\"Transaction failed"
                + " during 3D Secure validation\"}],\"transaction_response\":null,"
                + "\"response_hash\":\"");
    assertThat(again.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(wired.posted).hasSize(1);
  }

  @Test
  void a_continue_before_the_payer_answered_is_refused_900_and_the_challenge_stays_open() {
    aHandshake("10");
    charge(aCharge());

    TranzilaApiResponse early = continueOn(TRACK, TranzilaWiring.TERMINAL);

    assertThat(bodyOf(early)).contains("\"code\":900");
    assertThat(wired.challenges.all().get(0).isCompleted()).isFalse();
  }

  @Test
  void a_continue_naming_no_terminal_of_the_run_is_refused_10013_unhashed() {
    TranzilaApiResponse refused = continueOn(TRACK, "nobody");

    assertThat(bodyOf(refused))
        .isEqualTo(
            "{\"errors\":[{\"code\":10013,\"param\":\"supplier\",\"message\":\"TERMINAL NOT"
                + " FOUND OR BAD CONFIGURATION\"}],\"transaction_response\":null,"
                + "\"response_hash\":null}");
  }

  @Test
  void a_continue_naming_no_supplier_is_refused_10013() {
    Map<String, List<String>> asked = new LinkedHashMap<>();
    asked.put("track_id", List.of(TRACK));

    assertThat(bodyOf(charge(asked))).contains("\"code\":10013");
  }

  @Test
  void a_failed_hosted_fields_challenge_is_charged_anyway_under_the_hosted_fields_default() {
    aHandshake("10");
    charge(aCharge());
    answer("000");

    String continued = bodyOf(continueOn(TRACK, TranzilaWiring.TERMINAL));

    assertThat(continued)
        .contains("\"success\":true")
        .contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_failed_hosted_fields_challenge_told_n_continues_into_a_900_answer() {
    aHandshake("10");
    charge(aCharge("force_txn_on_3ds_fail", "N"));
    answer("000");

    String continued = bodyOf(continueOn(TRACK, TranzilaWiring.TERMINAL));

    assertThat(continued)
        .contains(
            "\"success\":false,\"error\":\"Transaction failed during 3D Secure validation\","
                + "\"processor_response_code\":\"900\"");
  }

  @Test
  void the_hosted_fields_frictionless_failure_is_charged_by_default_and_refused_when_told_n() {
    aHandshake("10");

    String forced = bodyOf(charge(aChargeOf(TranzilaThreeDSecureTest.FAILS)));
    String failed =
        bodyOf(charge(aChargeOf(TranzilaThreeDSecureTest.FAILS, "force_txn_on_3ds_fail", "N")));

    assertThat(forced).contains("\"success\":true");
    assertThat(failed)
        .contains("\"success\":false")
        .contains("\"processor_response_code\":\"900\"")
        .contains("\"force_txn_on_3ds_fail\":\"N\"");
  }

  @Test
  void force_challenge_one_on_the_hosted_fields_challenges_any_card() {
    aHandshake("10");

    String answered = bodyOf(charge(aChargeOf("4111111111111111", "force_challenge", "1")));

    assertThat(answered).startsWith("{\"challengeUrl\":");
  }

  @Test
  void a_decline_naming_no_operation_is_no_match_at_a_hosted_fields_3ds_step() {
    aHandshake("10");
    wired.rules.add(
        new dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            5,
            dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch.anyRequest(),
            new dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction.TerminalFailure("004"),
            null));
    wired.writes(
        TranzilaOperations.THREE_D_SECURE,
        new dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction.TerminalFailure("913"));

    String failed = bodyOf(charge(aChargeOf("4111111111111111", "force_txn_on_3ds_fail", "N")));
    String declined = bodyOf(charge(aChargeOf("4111111111111111")));

    assertThat(failed)
        .startsWith(
            "{\"errors\":null,\"transaction_response\":{\"success\":false,"
                + "\"error\":\"Cardholder not enrolled in the service.\","
                + "\"processor_response_code\":\"913\",");
    assertThat(declined)
        .startsWith(
            "{\"errors\":null,\"transaction_response\":{\"success\":false,"
                + "\"error\":\"Refusal. Please contact the card owner to check the reason with"
                + " his credit company.\",\"processor_response_code\":\"004\",");
  }

  @Test
  void a_hosted_fields_challenge_keeps_the_fault_and_the_rule_of_a_disrupting_3ds_step() {
    aHandshake("10");
    wired.writes(
        TranzilaOperations.THREE_D_SECURE,
        new dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction.TransportFaultAlways(
            new dev.marcinromanowski.pspsim.kernel.fault.TransportFault.DroppedConnection()));

    TranzilaApiResponse answered = charge(aChargeOf("4111111111111111", "force_challenge", "1"));

    assertThat(answered.delivery().fault()).isNotNull();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).scenarioId()).isNotNull();
    assertThat(wired.challenges.all()).hasSize(1);
  }

  @Test
  void a_hosted_fields_challenge_names_the_rule_that_let_its_3ds_step_proceed() {
    aHandshake("10");
    wired.writes(
        TranzilaOperations.THREE_D_SECURE,
        new dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction.FixedSuccess());

    TranzilaApiResponse answered = charge(aChargeOf("4111111111111111", "force_challenge", "1"));

    assertThat(bodyOf(answered)).startsWith("{\"challengeUrl\":");
    assertThat(answered.delivery().fault()).isNull();
    assertThat(theRow(AuditDirection.OUTBOUND_RESPONSE).scenarioId()).isNotNull();
  }

  @Test
  void a_challenge_is_listed_among_the_objects_by_its_arrival_and_cleared_with_its_run() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    challenged("");

    ProviderObjectPage.Listed listed =
        (ProviderObjectPage.Listed)
            wired.provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, null, 10));

    assertThat(listed.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(TRACK, Long.toString(debit));
    ProviderObjectPage.Listed after =
        (ProviderObjectPage.Listed)
            wired.provider.objects(new ProviderObjectQuery(TranzilaWiring.RUN, TRACK, 10));
    assertThat(after.objects())
        .extracting(ProviderObjectState::providerObjectId)
        .containsExactly(Long.toString(debit));
    assertThat(wired.provider.theObjectsItHoldsUnder(TranzilaWiring.RUN, List.of(TRACK)))
        .extracting(state -> state.kind().asThisProviderNamesIt())
        .containsExactly("a 3-D Secure challenge");
    assertThat(wired.provider.reset(TranzilaWiring.RUN, ResetScope.TEST_RUN).clearedObjects())
        .isEqualTo(2);
    assertThat(wired.challenges.all()).isEmpty();
  }

  @Test
  void a_challenge_offers_no_payer_step_on_a_screen_and_says_where_it_is_answered() {
    challenged("");

    assertThat(wired.provider.hostedCompletion(TranzilaWiring.RUN, TRACK))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                TranzilaApi.TRANZILA, TranzilaProvider.NO_CHALLENGE_STEP_ON_A_SCREEN));
    assertThat(
            wired.provider.complete(
                new HostedCompletionRequest(
                    TranzilaWiring.RUN,
                    TRACK,
                    PayerOutcome.PAID,
                    null,
                    dev.marcinromanowski.pspsim.kernel.ObjectOrigin.PROVIDER_API)))
        .isEqualTo(
            new HostedCompletionOutcome.NotOffered(TranzilaProvider.NO_CHALLENGE_STEP_ON_A_SCREEN));
  }

  @Test
  void a_reset_of_every_run_clears_every_challenge() {
    challenged("");

    wired.provider.reset(TranzilaWiring.RUN, ResetScope.ALL);

    assertThat(wired.challenges.all()).isEmpty();
  }

  private TranzilaChallenge aChallengeWith(Map<String, String> params) {
    challenged("");
    TranzilaChallenge made = wired.challenges.all().get(0);
    return new TranzilaChallenge(
        made.trackId(),
        made.testRunId(),
        made.accountId(),
        made.terminalName(),
        made.transactionId(),
        made.type(),
        made.verifyMode(),
        made.amount(),
        made.currencyCode(),
        made.token(),
        made.card(),
        made.responseCvv(),
        made.responseId(),
        made.dcDisable(),
        made.forceTxnOnFail(),
        made.originalRequest(),
        made.redirectUrl(),
        new LinkedHashMap<>(params),
        made.hostedFields(),
        made.origin(),
        made.createdAt(),
        made.payerVerdict(),
        made.answeredAt(),
        made.completedAt());
  }

  private void challenged(String more) {
    wired.create(TranzilaThreeDSecureTest.aSecureDebit(TranzilaThreeDSecureTest.CHALLENGED, more));
  }

  private TranzilaApiResponse answer(String code) {
    return wired.challengePage.answer(aCall("POST", Map.of("code", List.of(code))), TRACK);
  }

  private static TranzilaPageCall aCall(String method, Map<String, List<String>> parameters) {
    return TranzilaPageCall.of(method, THE_PAGE, parameters);
  }

  private void aHandshake(String sum) {
    wired.handshakes.insert(
        new TranzilaHandshake(
            A_THTK,
            TranzilaWiring.RUN,
            TranzilaWiring.ACCOUNT,
            TranzilaWiring.TERMINAL,
            sum,
            null,
            TranzilaWiring.NOW,
            TranzilaWiring.NOW.plus(Duration.ofMinutes(20))));
  }

  private static Map<String, List<String>> aCharge(String... more) {
    return aChargeOf(TranzilaThreeDSecureTest.CHALLENGED, more);
  }

  private static Map<String, List<String>> aChargeOf(String card, String... more) {
    Map<String, List<String>> asked = new LinkedHashMap<>();
    asked.put("terminal_name", List.of(TranzilaWiring.TERMINAL));
    asked.put("thtk", List.of(A_THTK));
    asked.put("amount", List.of("10"));
    asked.put("credit_card_number", List.of(card));
    asked.put("expiry_month", List.of("12"));
    asked.put("expiry_year", List.of("2030"));
    for (int at = 0; at < more.length; at += 2) {
      asked.put(more[at], List.of(more[at + 1]));
    }
    return asked;
  }

  private TranzilaApiResponse continueOn(String trackId, String supplier) {
    Map<String, List<String>> asked = new LinkedHashMap<>();
    asked.put("track_id", List.of(trackId));
    asked.put("supplier", List.of(supplier));
    return charge(asked);
  }

  private TranzilaApiResponse charge(Map<String, List<String>> asked) {
    return wired.hostedFields.charge(
        TranzilaPageCall.of("POST", "/tranzila/hf/process/ajax_process_transaction", asked));
  }

  private static String bodyOf(TranzilaApiResponse answered) {
    return new String(answered.body(), StandardCharsets.UTF_8);
  }

  private AuditRecord theRow(AuditDirection direction) {
    List<AuditRecord> rows =
        wired.audit.all().stream().filter(row -> row.direction() == direction).toList();
    return rows.get(rows.size() - 1);
  }
}
