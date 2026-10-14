package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaThreeDSecureTest {

  static final String FRICTIONLESS = "4907639999909022";

  static final String FAILS = "4907639999990022";

  static final String CHALLENGED = "4918914107195005";

  static final String THE_FIRST_TRACK = "3ds_0000000000000000000000000000000000000001";

  static final String THE_FIRST_CHALLENGE_URL =
      "https://simulator.test/tranzila/3ds/challenge/" + THE_FIRST_TRACK;

  static final String APP_KEY = "key-account-1";

  static final String A_SHOP = "https://shop.example/3ds/back";

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void the_frictionless_card_is_charged_and_answered_with_its_3ds_statuses_before_the_request() {
    String answered = wired.answered(aSecureDebit(FRICTIONLESS, ""));

    assertThat(answered)
        .isEqualTo(
            "{\"error_code\":0,\"message\":\"success\",\"transaction_result\":{"
                + "\"processor_response_code\":\"000\",\"transaction_id\":10000001,"
                + "\"auth_number\":\"1000001\",\"Responsecvv\":\"0\",\"Responseid\":\"0\","
                + "\"card_type\":2,\"card_type_name\":\"Visa\",\"currency_code\":\"ILS\","
                + "\"expiry_month\":7,\"expiry_year\":30,\"payment_plan\":1,"
                + "\"token\":\"t000000000000019022\",\"last_4\":\"9022\","
                + "\"card_mask\":\"490763xxxxxx9022\",\"card_locality\":\"domestic\","
                + "\"amount\":10.53,\"txn_type\":\"debit\",\"tranmode\":\"A\"},"
                + "\"t3ds_data\":{\"version\":\"1.0\",\"statusCode\":\"YA\","
                + "\"statusMessage\":\"Authenticated successfully.\","
                + "\"xid\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAE=\","
                + "\"cavv\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAI=\",\"eci\":\"05\"},"
                + "\"original_request\":{\"terminal_name\":\"myterminal\","
                + "\"activate_3ds\":\"Y\",\"txn_type\":\"debit\","
                + "\"card_number\":\"490763xxxxxx9022\",\"expire_month\":7,"
                + "\"expire_year\":2030,\"items\":[{\"name\":\"a\",\"units_number\":1,"
                + "\"unit_price\":10.53}]}}");
    assertThat(wired.theLastStored().processorResponseCode()).isEqualTo("000");
    assertThat(wired.challenges.all()).isEmpty();
  }

  @Test
  void the_failing_card_records_a_900_and_answers_it_as_a_decline_charging_nothing() {
    TranzilaOperationResult answered = wired.create(aSecureDebit(FAILS, ""));

    assertThat(answered.body().text())
        .isEqualTo(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\","
                + "\"transaction_result\":{\"processor_response_code\":\"900\","
                + "\"transaction_id\":10000001}}");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    TranzilaTransaction failed = wired.theLastStored();
    assertThat(failed.approved()).isFalse();
    assertThat(failed.authNumber()).isNull();
    assertThat(failed.token()).isNull();
    assertThat(failed.card().cardMask()).isEqualTo("490763xxxxxx0022");
    assertThat(wired.provider.state(TranzilaWiring.RUN, "10000001").orElseThrow())
        .extracting(ProviderObjectState::nativeStatus, ProviderObjectState::normalizedStatus)
        .containsExactly("debit 900", NormalizedStatus.FAILED);
  }

  @Test
  void the_failing_card_forced_on_failure_is_charged_without_any_3ds_statuses() {
    String answered =
        wired.answered(aSecureDebit(FAILS, ",\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"Y\"}"));

    assertThat(answered)
        .contains("\"processor_response_code\":\"000\"")
        .doesNotContain("t3ds_data");
    assertThat(wired.theLastStored().approved()).isTrue();
  }

  @Test
  void the_challenge_card_answers_a_challenge_and_keeps_it_waiting_without_a_transaction() {
    TranzilaOperationResult answered =
        wired.create(
            aSecureDebit(
                CHALLENGED,
                ",\"cvv\":\"123\",\"card_holder_id\":\"012345678\","
                    + "\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"o-1\"}]"));

    assertThat(answered.body().text())
        .isEqualTo(
            "{\"error_code\":0,\"message\":\"success\",\"challengeUrl\":\""
                + THE_FIRST_CHALLENGE_URL
                + "\",\"track_id\":\""
                + THE_FIRST_TRACK
                + "\"}");
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(answered.providerObjectId()).isEqualTo(THE_FIRST_TRACK);
    assertThat(wired.stored()).isEmpty();
    TranzilaChallenge waiting = wired.challenges.all().get(0);
    assertThat(waiting.transactionId()).isEqualTo(10000001L);
    assertThat(waiting.type()).isEqualTo(TranzilaTxnType.DEBIT);
    assertThat(waiting.verifyMode()).isNull();
    assertThat(waiting.amount()).isEqualTo("10.53");
    assertThat(waiting.currencyCode()).isEqualTo("ILS");
    assertThat(waiting.token()).isEqualTo("t000000000000015005");
    assertThat(waiting.card()).isEqualTo(new TranzilaCard("491891xxxxxx5005", 2, 7, 30));
    assertThat(waiting.responseCvv()).isEqualTo("1");
    assertThat(waiting.responseId()).isEqualTo("1");
    assertThat(waiting.dcDisable()).isEqualTo("o-1");
    assertThat(waiting.forceTxnOnFail()).isFalse();
    assertThat(waiting.originalRequest())
        .contains("\"card_number\":\"491891xxxxxx5005\"")
        .contains("\"cvv\":\"***\"")
        .doesNotContain(CHALLENGED);
    assertThat(waiting.redirectUrl()).isNull();
    assertThat(waiting.redirectParams()).isEmpty();
    assertThat(waiting.hostedFields()).isNull();
    assertThat(waiting.createdAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(waiting.isAnswered()).isFalse();
    assertThat(wired.provider.state(TranzilaWiring.RUN, THE_FIRST_TRACK).orElseThrow())
        .extracting(
            ProviderObjectState::nativeStatus,
            ProviderObjectState::normalizedStatus,
            ProviderObjectState::relatedObjects)
        .containsExactly(
            "challenge awaiting the payer",
            NormalizedStatus.NOT_INITIALIZED,
            Map.of("terminalName", "myterminal", "card_mask", "491891xxxxxx5005"));
  }

  @Test
  void force_challenge_one_challenges_a_card_no_published_rule_decides() {
    TranzilaOperationResult answered =
        wired.create(
            aSecureDebit(TranzilaWire.A_VISA, ",\"3ds_settings\":{\"force_challenge\":1}"));

    assertThat(answered.body().text()).contains("\"challengeUrl\":\"" + THE_FIRST_CHALLENGE_URL);
    assertThat(wired.challenges.all()).hasSize(1);
  }

  @Test
  void force_challenge_zero_lets_a_card_no_published_rule_decides_pass_frictionlessly() {
    String answered =
        wired.answered(
            aSecureDebit(TranzilaWire.A_VISA, ",\"3ds_settings\":{\"force_challenge\":0}"));

    assertThat(answered).contains("\"t3ds_data\":{").doesNotContain("challengeUrl");
  }

  @Test
  void the_redirect_of_both_published_shapes_is_kept_top_level_first() {
    wired.create(
        aSecureDebit(
            CHALLENGED,
            ",\"auth_3ds_redirect\":{\"url\":\""
                + A_SHOP
                + "\",\"params\":[{\"key\":\"order\",\"value\":\"o-9\"},"
                + "{\"key\":\"same\",\"value\":\"top\"}]},"
                + "\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"Y\",\"auth_3ds_redirect\":"
                + "[{\"key\":\"same\",\"value\":\"array\"},{\"key\":\"lang\",\"value\":\"he\"}]}"));

    TranzilaChallenge waiting = wired.challenges.all().get(0);
    assertThat(waiting.redirectUrl()).isEqualTo(A_SHOP);
    assertThat(waiting.redirectParams())
        .containsExactly(
            Map.entry("order", "o-9"), Map.entry("same", "top"), Map.entry("lang", "he"));
    assertThat(waiting.forceTxnOnFail()).isTrue();
  }

  @Test
  void the_settings_array_alone_supplies_params_and_no_address() {
    wired.create(
        aSecureDebit(
            CHALLENGED,
            ",\"3ds_settings\":{\"auth_3ds_redirect\":[{\"key\":\"k\",\"value\":\"v\"}]}"));

    TranzilaChallenge waiting = wired.challenges.all().get(0);
    assertThat(waiting.redirectUrl()).isNull();
    assertThat(waiting.redirectParams()).containsExactly(Map.entry("k", "v"));
  }

  @Test
  void a_scenario_failing_the_3ds_step_answers_its_code_and_its_text() {
    wired.writes(TranzilaOperations.THREE_D_SECURE, new ScenarioAction.TerminalFailure("901"));

    TranzilaOperationResult answered = wired.create(aSecureDebit(TranzilaWire.A_VISA, ""));

    assertThat(answered.body().text())
        .isEqualTo(
            "{\"error_code\":901,\"message\":\"Card authentication failed.\","
                + "\"transaction_result\":{\"processor_response_code\":\"901\","
                + "\"transaction_id\":10000001}}");
    assertThat(answered.scenarioId()).isNotNull();
    assertThat(wired.theLastStored().processorResponseCode()).isEqualTo("901");
  }

  @Test
  void a_scenario_failing_the_3ds_step_is_charged_anyway_when_forced_on_failure() {
    wired.writes(TranzilaOperations.THREE_D_SECURE, new ScenarioAction.TerminalFailure("901"));

    String answered =
        wired.answered(
            aSecureDebit(
                TranzilaWire.A_VISA, ",\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"Y\"}"));

    assertThat(answered).contains("\"processor_response_code\":\"000\"").doesNotContain("t3ds");
  }

  @Test
  void a_scenario_succeeding_the_3ds_step_still_challenges_when_force_challenge_asks() {
    wired.writes(TranzilaOperations.THREE_D_SECURE, new ScenarioAction.FixedSuccess());

    TranzilaOperationResult challenged =
        wired.create(
            aSecureDebit(TranzilaWire.A_VISA, ",\"3ds_settings\":{\"force_challenge\":1}"));
    TranzilaOperationResult frictionless = wired.create(aSecureDebit(TranzilaWire.A_VISA, ""));

    assertThat(challenged.body().text()).contains("\"challengeUrl\"");
    assertThat(challenged.scenarioId()).isNotNull();
    assertThat(frictionless.body().text()).contains("\"t3ds_data\"");
    assertThat(frictionless.scenarioId()).isNotNull();
  }

  @Test
  void a_scenario_disrupting_the_3ds_step_faults_the_answer_it_made() {
    wired.writes(
        TranzilaOperations.THREE_D_SECURE,
        new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()));

    TranzilaOperationResult answered = wired.create(aSecureDebit(TranzilaWire.A_VISA, ""));

    assertThat(answered.fault()).isEqualTo(new TransportFault.DroppedConnection());
    assertThat(wired.stored()).hasSize(1);
  }

  @Test
  void a_scenario_answering_instead_of_the_3ds_step_makes_nothing() {
    wired.writes(
        TranzilaOperations.THREE_D_SECURE,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));

    TranzilaOperationResult answered = wired.create(aSecureDebit(TranzilaWire.A_VISA, ""));

    assertThat(answered.insteadOfTheOperation()).isNotNull();
    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.challenges.all()).isEmpty();
  }

  @Test
  void a_published_test_card_is_decided_by_its_number_and_no_3ds_rule_is_read() {
    wired.writes(TranzilaOperations.THREE_D_SECURE, new ScenarioAction.TerminalFailure("901"));

    String answered = wired.answered(aSecureDebit(FRICTIONLESS, ""));

    assertThat(answered).contains("\"processor_response_code\":\"000\"").contains("t3ds_data");
  }

  @Test
  void a_token_of_the_challenge_card_is_challenged_as_the_card_is() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(aDebitOf(CHALLENGED, "")));
    String token = wired.thePaymentOf(debit).root().token();

    TranzilaOperationResult answered = wired.create(aSecureDebit(String.valueOf(token), ""));

    assertThat(answered.body().text()).contains("\"challengeUrl\"");
    assertThat(wired.challenges.all().get(0).token()).isEqualTo(token);
  }

  @Test
  void a_challenge_whose_track_id_is_taken_draws_another() {
    wired.challenges.theNextIdsAreTaken(1);

    TranzilaOperationResult answered = wired.create(aSecureDebit(CHALLENGED, ""));

    assertThat(answered.providerObjectId())
        .isEqualTo("3ds_0000000000000000000000000000000000000002");
  }

  @Test
  void a_challenge_no_track_id_could_be_claimed_for_is_a_failure_of_this_simulator() {
    wired.challenges.theNextIdsAreTaken(CreateTransaction.TRANSACTION_ID_ATTEMPTS);

    assertThatThrownBy(() -> wired.create(aSecureDebit(CHALLENGED, "")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("no Tranzila track_id could be claimed in 5 attempts");
  }

  @Test
  void a_j5_challenged_holds_its_amount_from_the_moment_it_is_completed() {
    wired.create(
        TranzilaWire.withCard(
            "\"activate_3ds\":\"Y\",\"txn_type\":\"verify\",\"verify_mode\":5", CHALLENGED, "42"));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofMinutes(3)));

    String completed = complete(THE_FIRST_TRACK);

    assertThat(completed).contains("\"processor_response_code\":\"777\"");
    TranzilaTransaction hold = wired.theLastStored();
    assertThat(hold.verifyMode()).isEqualTo(5);
    assertThat(hold.holdExpiresAt()).isEqualTo(Instant.parse("2026-10-20T12:03:00Z"));
    assertThat(hold.createdAt()).isEqualTo(Instant.parse("2026-10-13T12:03:00Z"));
  }

  @Test
  void a_passed_challenge_completes_into_the_published_complete_answer() {
    wired.create(aSecureDebit(CHALLENGED, ",\"cvv\":\"123\",\"card_holder_id\":\"012345678\""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);

    String completed = complete(THE_FIRST_TRACK);

    assertThat(completed)
        .isEqualTo(
            "{\"error_code\":0,\"message\":\"Success\",\"transaction_result\":{"
                + "\"processor_response_code\":\"000\",\"transaction_id\":\"10000001\","
                + "\"auth_number\":\"1000001\",\"card_type\":\"2\",\"card_type_name\":\"Visa\","
                + "\"amount\":10.53,\"currency_code\":\"ILS\",\"expiry_month\":\"07\","
                + "\"expiry_year\":\"30\",\"payment_plan\":\"1\","
                + "\"token\":\"t000000000000015005\",\"last_4\":\"5005\","
                + "\"card_mask\":\"491891******5005\",\"card_locality\":\"domestic\"},"
                + "\"t3ds_data\":{\"version\":\"1.0\",\"statusCode\":\"YA\","
                + "\"statusMessage\":\"Authenticated successfully.\","
                + "\"xid\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAI=\","
                + "\"cavv\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAM=\",\"eci\":\"05\"},"
                + "\"original_request\":{\"terminal_name\":\"myterminal\","
                + "\"activate_3ds\":\"Y\",\"txn_type\":\"debit\","
                + "\"card_number\":\"491891xxxxxx5005\",\"expire_month\":7,"
                + "\"expire_year\":2030,\"items\":[{\"name\":\"a\",\"units_number\":1,"
                + "\"unit_price\":10.53}],\"cvv\":\"***\",\"card_holder_id\":\"012345678\"}}");
    TranzilaTransaction made = wired.theLastStored();
    assertThat(made.transactionId()).isEqualTo(10000001L);
    assertThat(made.responseCvv()).isEqualTo("1");
    assertThat(made.responseId()).isEqualTo("1");
    assertThat(made.token()).isEqualTo("t000000000000015005");
    TranzilaChallenge completedChallenge = wired.challenges.all().get(0);
    assertThat(completedChallenge.completedAt()).isEqualTo(TranzilaWiring.NOW);
    assertThat(wired.provider.state(TranzilaWiring.RUN, THE_FIRST_TRACK).orElseThrow())
        .extracting(
            ProviderObjectState::nativeStatus,
            ProviderObjectState::normalizedStatus,
            ProviderObjectState::relatedObjects)
        .containsExactly(
            "debit 000",
            NormalizedStatus.SUCCEEDED,
            Map.of(
                "terminalName",
                "myterminal",
                "card_mask",
                "491891xxxxxx5005",
                "transaction_id",
                "10000001"));
  }

  @Test
  void a_challenge_completed_once_answers_900_the_second_time_and_is_kept_as_a_duplicate() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    complete(THE_FIRST_TRACK);

    TranzilaApiResponse again = completeAnswering(THE_FIRST_TRACK);

    assertThat(bodyOf(again))
        .isEqualTo(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\"}");
    assertThat(again.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(wired.stored()).hasSize(1);
    List<RecordedKeyedRequest> kept = underTheTrack(THE_FIRST_TRACK);
    assertThat(kept)
        .extracting(RecordedKeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
  }

  @Test
  void an_unknown_track_id_answers_900_and_keeps_nothing() {
    TranzilaApiResponse answered = completeAnswering("3ds_unknown");

    assertThat(bodyOf(answered))
        .isEqualTo(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\"}");
    assertThat(underTheTrack("3ds_unknown")).isEmpty();
    assertThat(wired.audit.all().get(1).refusal())
        .isEqualTo(RefusalSubject.noObjectAnsweredTo("track_id"));
  }

  @Test
  void a_challenge_the_payer_has_not_answered_answers_900_and_stays_open() {
    wired.create(aSecureDebit(CHALLENGED, ""));

    String early = complete(THE_FIRST_TRACK);
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    String completed = complete(THE_FIRST_TRACK);

    assertThat(early)
        .isEqualTo(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\"}");
    assertThat(completed).contains("\"message\":\"Success\"");
    assertThat(underTheTrack(THE_FIRST_TRACK)).hasSize(1);
  }

  @Test
  void a_failed_challenge_completes_into_a_900_decline_and_a_failed_transaction() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.FAILED);

    String completed = complete(THE_FIRST_TRACK);

    assertThat(completed)
        .isEqualTo(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\","
                + "\"transaction_result\":{\"processor_response_code\":\"900\","
                + "\"transaction_id\":10000001}}");
    assertThat(wired.provider.state(TranzilaWiring.RUN, THE_FIRST_TRACK).orElseThrow())
        .extracting(ProviderObjectState::nativeStatus, ProviderObjectState::normalizedStatus)
        .containsExactly("debit 900", NormalizedStatus.FAILED);
  }

  @Test
  void a_failed_challenge_forced_on_failure_completes_into_a_charge_without_3ds_statuses() {
    wired.create(aSecureDebit(CHALLENGED, ",\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"Y\"}"));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.FAILED);

    String completed = complete(THE_FIRST_TRACK);

    assertThat(completed)
        .startsWith("{\"error_code\":0,\"message\":\"Success\",\"transaction_result\":{")
        .contains("\"processor_response_code\":\"000\"")
        .doesNotContain("t3ds_data");
  }

  @Test
  void a_scenario_declining_the_debit_declines_the_completion() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));

    TranzilaApiResponse completed = completeAnswering(THE_FIRST_TRACK);

    assertThat(bodyOf(completed)).startsWith("{\"error_code\":4,\"message\":\"Refusal.");
    assertThat(completed.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_scenario_proceeding_or_disrupting_the_completion_is_carried_onto_the_complete_answer() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()));

    TranzilaOperationResult completed = completeResult(THE_FIRST_TRACK);

    assertThat(completed.fault()).isEqualTo(new TransportFault.DroppedConnection());
    assertThat(completed.scenarioId()).isNotNull();
    assertThat(completed.body().text()).contains("\"message\":\"Success\"");
  }

  @Test
  void a_scenario_proceeding_with_the_completion_names_itself_on_the_answer() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.FixedSuccess());

    TranzilaOperationResult completed = completeResult(THE_FIRST_TRACK);

    assertThat(completed.fault()).isNull();
    assertThat(completed.scenarioId()).isNotNull();
  }

  @Test
  void a_scenario_answering_instead_of_the_completion_leaves_the_challenge_open() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    wired.writes(
        TranzilaOperations.DEBIT,
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)));

    TranzilaOperationResult completed = completeResult(THE_FIRST_TRACK);

    assertThat(completed.insteadOfTheOperation()).isNotNull();
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.challenges.all().get(0).isCompleted()).isFalse();
    assertThat(underTheTrack(THE_FIRST_TRACK)).isEmpty();
  }

  @Test
  void a_challenge_whose_dc_disable_was_approved_meanwhile_completes_as_that_duplicate() {
    String dcDisable = ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"o-7\"}]";
    wired.create(aSecureDebit(CHALLENGED, dcDisable));
    long approved =
        TranzilaWire.transactionIdIn(wired.answered(aDebitOf(TranzilaWire.A_VISA, dcDisable)));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);

    String completed = complete(THE_FIRST_TRACK);

    assertThat(completed)
        .contains("\"message\":\"This payment has already been made, please contact the business\"")
        .contains("\"transaction_id\":" + approved);
    assertThat(wired.stored()).hasSize(1);
    assertThat(wired.challenges.all().get(0).transactionId()).isEqualTo(approved);
    assertThat(underTheTrack(THE_FIRST_TRACK)).hasSize(1);
  }

  @Test
  void a_challenge_with_a_dc_disable_nothing_approved_completes_into_its_own_transaction() {
    wired.create(
        aSecureDebit(
            CHALLENGED, ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"o-8\"}]"));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);

    String completed = complete(THE_FIRST_TRACK);

    assertThat(completed).contains("\"message\":\"Success\"");
    assertThat(wired.theLastStored().dcDisable()).isEqualTo("o-8");
  }

  @Test
  void a_challenge_whose_transaction_id_was_taken_meanwhile_completes_under_another() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    answer(THE_FIRST_TRACK, TranzilaChallenge.Verdict.PASSED);
    wired.transactions.theNextIdsAreTaken(1);

    String completed = complete(THE_FIRST_TRACK);

    assertThat(completed).contains("\"transaction_id\":\"10000002\"");
    assertThat(wired.challenges.all().get(0).transactionId()).isEqualTo(10000002L);
    assertThat(wired.theLastStored().transactionId()).isEqualTo(10000002L);
  }

  @Test
  void a_complete_names_its_terminal_and_nothing_else_the_schema_does_not_publish() {
    assertThat(bodyOf(call("{\"track_id\":\"x\",\"terminal_name\":\"otherterminal\"}")))
        .startsWith("{\"error_code\":\"20002\",\"message\":\"Authorization failed: ");
    assertThat(bodyOf(call("{\"track_id\":\"x\",\"terminal_name\":\"myterminal\",\"a\":1}")))
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: a is not a field of"
                + " the complete schema\"}");
    assertThat(bodyOf(call("{\"terminal_name\":\"myterminal\"}")))
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: track_id is required"
                + " and must be a string\"}");
    assertThat(bodyOf(call("{\"track_id\":1,\"terminal_name\":\"myterminal\"}")))
        .contains("track_id is required and must be a string");
    assertThat(bodyOf(call("{\"track_id\":\"x\"}")))
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: terminal_name is"
                + " required and must be a string\"}");
    assertThat(bodyOf(call("[]")))
        .isEqualTo(
            "{\"error_code\":\"20001\",\"message\":\"Invalid JSON Format: the request body is not"
                + " a JSON object\"}");
    assertThat(call("[]").httpStatus()).isEqualTo(400);
  }

  @Test
  void a_challenge_made_on_the_hosted_fields_is_not_completed_over_the_api() {
    wired.create(aSecureDebit(CHALLENGED, ""));
    TranzilaChallenge api = wired.challenges.all().get(0);
    wired.challenges.clearAll();
    wired.challenges.open(
        onTheHostedFields(api).answered(TranzilaChallenge.Verdict.PASSED, TranzilaWiring.NOW));

    assertThat(complete(THE_FIRST_TRACK)).contains("\"error_code\":900");
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void activate_3ds_on_a_follow_on_is_refused_as_outside_what_3ds_authenticates() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    String refused =
        wired.answered(
            TranzilaWire.aForce(debit, TranzilaWire.A_VISA, "1")
                .replace("{\"terminal", "{\"activate_3ds\":\"Y\",\"terminal"));

    assertThat(refused)
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: activate_3ds Y"
                + " authenticates the card holder of a debit or a verify, and a force acts on a"
                + " transaction already made\"}");
  }

  @Test
  void a_processor_decline_on_the_3ds_step_is_refused_naming_the_operation_and_makes_nothing() {
    String rule =
        aRule(TranzilaOperations.THREE_D_SECURE, new ScenarioAction.TerminalFailure("004"));

    TranzilaOperationResult answered = wired.create(aSecureDebit(TranzilaWire.A_VISA, ""));

    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(answered.scenarioId()).isEqualTo(rule);
    assertThat(answered.insteadOfTheOperation()).isNotNull();
    assertThat(answered.insteadOfTheOperation().httpStatus()).isEqualTo(500);
    assertThat(new String(answered.insteadOfTheOperation().body(), StandardCharsets.UTF_8))
        .isEqualTo(TranzilaResponseCodes.whyNotAnsweredBy("3ds", "004"))
        .startsWith("a scenario rule fails operation 3ds with 004,");
    assertThat(wired.stored()).isEmpty();
    assertThat(wired.challenges.all()).isEmpty();
  }

  @Test
  void a_3ds_code_on_a_transaction_type_is_refused_naming_the_operation_and_makes_nothing() {
    String rule = aRule(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("901"));

    TranzilaOperationResult answered = wired.create(TranzilaWire.aDebit());

    assertThat(answered.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(answered.scenarioId()).isEqualTo(rule);
    assertThat(new String(answered.insteadOfTheOperation().body(), StandardCharsets.UTF_8))
        .isEqualTo(
            "a scenario rule fails operation debit with 901, and provider tranzila answers the"
                + " codes of the 3DS table, 900 to 928 and 930, only on operation 3ds and the"
                + " processor's declines only on a transaction type, so operation debit cannot"
                + " answer 901; nothing was charged and nothing was recorded");
    assertThat(answered.insteadOfTheOperation().contentType())
        .isEqualTo("text/plain; charset=utf-8");
    assertThat(wired.stored()).isEmpty();
  }

  @Test
  void each_operation_answers_its_own_table_of_codes_only() {
    assertThat(TranzilaResponseCodes.answeredBy("3ds", "900")).isTrue();
    assertThat(TranzilaResponseCodes.answeredBy("3ds", "930")).isTrue();
    assertThat(TranzilaResponseCodes.answeredBy("3ds", "004")).isFalse();
    assertThat(TranzilaResponseCodes.answeredBy("debit", "004")).isTrue();
    assertThat(TranzilaResponseCodes.answeredBy("verify", "901")).isFalse();
    assertThat(TranzilaResponseCodes.answeredBy("force", "017")).isTrue();
  }

  @Test
  void the_forced_charge_after_a_failed_3ds_step_is_attributed_to_the_rule_that_decided_it() {
    String failing =
        aRule(TranzilaOperations.THREE_D_SECURE, new ScenarioAction.TerminalFailure("901"));
    String charging = aRule(TranzilaOperations.DEBIT, new ScenarioAction.FixedSuccess());

    TranzilaOperationResult answered =
        wired.create(
            aSecureDebit(
                TranzilaWire.A_VISA, ",\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"Y\"}"));
    TranzilaOperationResult alone =
        wired.create(
            aSecureDebit(
                TranzilaWire.A_VISA, ",\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"Y\"}"));

    assertThat(answered.scenarioId()).isEqualTo(charging).isNotEqualTo(failing);
    assertThat(alone.scenarioId()).isEqualTo(charging);
  }

  @Test
  void a_3ds_rule_names_the_answer_no_other_rule_decided_and_keeps_a_fault_another_made() {
    TranzilaOperationResult undecided =
        TranzilaOperationResult.succeeded("1", TranzilaBody.of(200, TranzilaJson.text("x")));
    TranzilaOperationResult decided = undecided.proceededUnder("debit-rule");
    TranzilaOperationResult faulted =
        undecided.faultedBy("debit-rule", new TransportFault.DroppedConnection());

    assertThat(CreateTransaction.proceededUnlessDecided(undecided, "3ds-rule").scenarioId())
        .isEqualTo("3ds-rule");
    assertThat(CreateTransaction.proceededUnlessDecided(decided, "3ds-rule").scenarioId())
        .isEqualTo("debit-rule");
    assertThat(CreateTransaction.declinedUnlessDecided(undecided, "3ds-rule").scenarioId())
        .isEqualTo("3ds-rule");
    assertThat(CreateTransaction.declinedUnlessDecided(decided, "3ds-rule").scenarioId())
        .isEqualTo("debit-rule");
    TranzilaOperationResult refaulted =
        CreateTransaction.faultedUnlessFaulted(
            faulted, "3ds-rule", new TransportFault.ServerError(502));
    assertThat(refaulted.fault()).isEqualTo(new TransportFault.DroppedConnection());
    assertThat(refaulted.scenarioId()).isEqualTo("debit-rule");
    TranzilaOperationResult firstFault =
        CreateTransaction.faultedUnlessFaulted(
            decided, "3ds-rule", new TransportFault.DroppedConnection());
    assertThat(firstFault.fault()).isEqualTo(new TransportFault.DroppedConnection());
    assertThat(firstFault.scenarioId()).isEqualTo("debit-rule");
    assertThat(
            CreateTransaction.faultedUnlessFaulted(
                    undecided, "3ds-rule", new TransportFault.DroppedConnection())
                .scenarioId())
        .isEqualTo("3ds-rule");
  }

  @Test
  void the_top_level_force_txn_on_3ds_fail_of_the_complete_example_is_admitted_and_forces() {
    String answered = wired.answered(aSecureDebit(FAILS, ",\"force_txn_on_3ds_fail\":\"Y\""));
    String settingsWin =
        wired.answered(
            aSecureDebit(
                FAILS,
                ",\"force_txn_on_3ds_fail\":\"Y\",\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"N\"}"));

    assertThat(answered).contains("\"processor_response_code\":\"000\"");
    assertThat(settingsWin).startsWith("{\"error_code\":900,");
  }

  private String aRule(String operation, ScenarioAction action) {
    return wired
        .rules
        .add(
            new dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule(
                TranzilaApi.TRANZILA,
                TranzilaWiring.RUN,
                1,
                dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch.anyRequest()
                    .naming(operation),
                action,
                null))
        .scenarioId();
  }

  @Test
  void the_published_cards_are_known_by_their_number_and_by_the_mask_a_token_keeps() {
    assertThat(TranzilaThreeDSecureCards.ofThePan(FRICTIONLESS))
        .contains(TranzilaThreeDSecureOutcome.AUTHENTICATED);
    assertThat(TranzilaThreeDSecureCards.ofThePan(FAILS))
        .contains(TranzilaThreeDSecureOutcome.FAILED);
    assertThat(TranzilaThreeDSecureCards.ofThePan(CHALLENGED))
        .contains(TranzilaThreeDSecureOutcome.CHALLENGED);
    assertThat(TranzilaThreeDSecureCards.ofThePan(TranzilaWire.A_VISA)).isEmpty();
    assertThat(TranzilaThreeDSecureCards.ofTheCard(new TranzilaCard("491891xxxxxx5005", 2, 1, 30)))
        .contains(TranzilaThreeDSecureOutcome.CHALLENGED);
    assertThat(TranzilaThreeDSecureCards.ofTheCard(new TranzilaCard("490763xxxxxx0022", 2, 1, 30)))
        .contains(TranzilaThreeDSecureOutcome.FAILED);
    assertThat(TranzilaThreeDSecureCards.ofTheCard(new TranzilaCard("xxxxxxxxxxxx5005", 2, 1, 30)))
        .isEmpty();
    assertThat(TranzilaThreeDSecureCards.passes("555")).isTrue();
    assertThat(TranzilaThreeDSecureCards.passes(" 555 ")).isTrue();
    assertThat(TranzilaThreeDSecureCards.passes("554")).isFalse();
    assertThat(TranzilaThreeDSecureCards.passes("5555")).isFalse();
  }

  @Test
  void a_cryptogram_is_the_base64_of_twenty_drawn_bytes() {
    assertThat(TranzilaThreeDSecureAnswers.aCryptogram(wired.ids))
        .isEqualTo("AAAAAAAAAAAAAAAAAAAAAAAAAAE=");
  }

  @Test
  void the_authentication_is_added_only_to_an_answer_that_echoes_its_request() {
    TranzilaOperationResult declined = TranzilaThreeDSecureAnswers.noChallengeToComplete();

    assertThat(TranzilaThreeDSecureAnswers.withTheAuthentication(declined, TranzilaJson.text("x")))
        .isSameAs(declined);
  }

  @Test
  void the_3ds_failure_codes_answer_the_texts_of_the_published_table() {
    assertThat(TranzilaResponseCodes.theTextOf("900"))
        .isEqualTo("Transaction failed during 3D Secure validation");
    assertThat(TranzilaResponseCodes.theTextOf("927"))
        .isEqualTo("Card authentication cancelled by user.");
    assertThat(TranzilaResponseCodes.theTextOf("930"))
        .isEqualTo("Authentication was attempted but not completed by the cardholder.");
    assertThat(TranzilaResponseCodes.theTextOf("004")).isEqualTo(TranzilaResponseCodes.REFUSAL);
    assertThat(TranzilaResponseCodes.theTextOf("929")).isEqualTo("929");
    assertThat(TranzilaResponseCodes.THE_3DS_FAILURES).hasSize(30);
    assertThat(TranzilaResponseCodes.failsTheAuthentication("913")).isTrue();
    assertThat(TranzilaResponseCodes.failsTheAuthentication("004")).isFalse();
  }

  static TranzilaChallenge onTheHostedFields(TranzilaChallenge made) {
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
        made.redirectParams(),
        new TranzilaHostedFieldsEcho(
            "wthtk",
            made.amount(),
            TranzilaPageCurrency.NIS,
            TranzilaTranmode.A,
            false,
            null,
            new java.util.LinkedHashMap<>()),
        made.origin(),
        made.createdAt(),
        made.payerVerdict(),
        made.answeredAt(),
        made.completedAt());
  }

  static String aSecureDebit(String card, String more) {
    String plain =
        TranzilaWire.withCard("\"activate_3ds\":\"Y\",\"txn_type\":\"debit\"", card, "10.53");
    return plain.substring(0, plain.length() - 1) + more + "}";
  }

  static String aDebitOf(String card, String more) {
    String plain = TranzilaWire.withCard("\"txn_type\":\"debit\"", card, "10.53");
    return plain.substring(0, plain.length() - 1) + more + "}";
  }

  private List<RecordedKeyedRequest> underTheTrack(String trackId) {
    return wired.exchanges.everythingItObserved().stream()
        .filter(kept -> kept.keyName().equals("track_id"))
        .filter(kept -> kept.keyValue().equals(trackId))
        .toList();
  }

  private void answer(String trackId, TranzilaChallenge.Verdict verdict) {
    TranzilaChallenge waiting = wired.challenges.find(trackId).orElseThrow();
    wired.challenges.answer(waiting.answered(verdict, wired.clock.now(TranzilaWiring.RUN)));
  }

  private String complete(String trackId) {
    return bodyOf(completeAnswering(trackId));
  }

  private TranzilaApiResponse completeAnswering(String trackId) {
    return call("{\"track_id\":\"" + trackId + "\",\"terminal_name\":\"myterminal\"}");
  }

  private TranzilaOperationResult completeResult(String trackId) {
    return wired.complete.handle(
        TranzilaApiCall.of(
            "POST",
            TranzilaApiController.COMPLETE,
            Map.of(),
            "{\"track_id\":\"" + trackId + "\",\"terminal_name\":\"myterminal\"}"),
        TranzilaWiring.RUN,
        wired.terminal(),
        wired.clock.now(TranzilaWiring.RUN));
  }

  private TranzilaApiResponse call(String body) {
    return wired.api.complete(
        TranzilaApiCall.of(
            "POST",
            TranzilaApiController.COMPLETE,
            Map.of(TranzilaFields.APP_KEY, List.of(APP_KEY)),
            body));
  }

  private static String bodyOf(TranzilaApiResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
