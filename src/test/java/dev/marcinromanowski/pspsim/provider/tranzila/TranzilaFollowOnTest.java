package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TranzilaFollowOnTest {

  private static final String NOT_FOUND =
      "{\"error_code\":20112,\"message\":\"Original transaction not found\"}";

  private static final String INDEX_EMPTY =
      "{\"error_code\":21101,\"message\":\"Provided index was empty\"}";

  private static final String AUTHORIZATION_DIFFERS =
      "{\"error_code\":22100,\"message\":\"Authorization number is not equal to the transaction"
          + " value\"}";

  private static final String AUTHORIZATION_EMPTY =
      "{\"error_code\":22101,\"message\":\"Empty authorization number\"}";

  private static final String ALREADY_CANCELED =
      "{\"error_code\":23001,\"message\":\"Attempting to credit a transaction that has already been"
          + " canceled\"}";

  private static final String CREDITED_ABOVE =
      "{\"error_code\":23002,\"message\":\"Sum is larger than the original debit transaction\"}";

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void a_partial_force_of_a_live_j5_is_approved_000_and_closes_it() {
    Held held = aJ5Of("100");

    String forced = wired.answered(TranzilaWire.aForce(held.id(), held.token(), "60"));

    assertThat(forced)
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"amount\":60,\"txn_type\":\"force\",\"tranmode\":\"F\"");
    TranzilaTransaction force = wired.theLastStored();
    assertThat(force.rootTxnId()).isEqualTo(held.id());
    assertThat(force.referenceTxnId()).isEqualTo(held.id());
    assertThat(force.currencyCode()).isEqualTo("ILS");
    assertThat(wired.thePaymentOf(held.id()).normalizedStatusAt(TranzilaWiring.NOW))
        .isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(wired.answered(TranzilaWire.aForce(held.id(), held.token(), "10")))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void a_force_of_the_whole_j5_amount_is_approved() {
    Held held = aJ5Of("100");

    assertThat(wired.answered(TranzilaWire.aForce(held.id(), held.token(), "100.00")))
        .contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_force_above_the_j5_amount_is_declined_507_and_kept_as_a_declined_force() {
    Held held = aJ5Of("100");

    TranzilaOperationResult declined =
        wired.create(TranzilaWire.aForce(held.id(), held.token(), "100.01"));

    assertThat(declined.body().text())
        .isEqualTo(
            "{\"error_code\":507,\"message\":\"actual transaction amount higher than the approved"
                + " amount\",\"transaction_result\":{\"processor_response_code\":\"507\","
                + "\"transaction_id\":10000002}}");
    assertThat(wired.theLastStored().processorResponseCode()).isEqualTo("507");
    assertThat(wired.thePaymentOf(held.id()).isAJ5StillHeldAt(TranzilaWiring.NOW)).isTrue();
  }

  @Test
  void a_force_naming_no_original_is_refused_21101() {
    String body = TranzilaWire.withCard("\"txn_type\":\"force\"", TranzilaWire.A_VISA, "10");

    assertThat(wired.answered(body)).isEqualTo(INDEX_EMPTY);
  }

  @Test
  void a_force_naming_a_transaction_this_terminal_never_made_is_refused_20112() {
    TranzilaOperationResult refused =
        wired.create(TranzilaWire.aForce(99999999L, TranzilaWire.A_VISA, "10"));

    assertThat(refused.body().text()).isEqualTo(NOT_FOUND);
    assertThat(refused.refusal()).isEqualTo(RefusalSubject.noObjectAnsweredTo("reference_txn_id"));
  }

  @Test
  void a_force_of_a_debit_is_refused_20112() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    TranzilaOperationResult refused =
        wired.create(TranzilaWire.aForce(debit, TranzilaWire.A_VISA, "1"));

    assertThat(refused.body().text()).isEqualTo(NOT_FOUND);
    assertThat(refused.refusal()).isEqualTo(RefusalSubject.theObject(Long.toString(debit)));
  }

  @Test
  void a_force_of_a_j2_is_refused_20112() {
    long j2 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ2()));

    assertThat(wired.answered(TranzilaWire.aForce(j2, TranzilaWire.A_VISA, "1")))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void a_force_of_a_force_is_refused_20112() {
    Held held = aJ5Of("100");
    long force =
        TranzilaWire.transactionIdIn(
            wired.answered(TranzilaWire.aForce(held.id(), held.token(), "10")));

    assertThat(wired.answered(TranzilaWire.aForce(force, held.token(), "1"))).isEqualTo(NOT_FOUND);
  }

  @Test
  void a_force_of_a_declined_j5_is_refused_20112() {
    wired.writes(TranzilaOperations.VERIFY, new ScenarioAction.TerminalFailure("004"));
    long j5 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ5("100")));

    assertThat(wired.answered(TranzilaWire.aForce(j5, TranzilaWire.A_VISA, "10")))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void a_force_carrying_another_authorization_number_is_refused_22100_and_its_own_is_not() {
    Held held = aJ5Of("100");
    String withAnother =
        TranzilaWire.aForce(held.id(), held.token(), "10")
            .replace("\"txn_type\"", "\"authorization_number\":\"7654321\",\"txn_type\"");
    String withItsOwn =
        TranzilaWire.aForce(held.id(), held.token(), "10")
            .replace(
                "\"txn_type\"", "\"authorization_number\":\"" + held.auth() + "\",\"txn_type\"");

    assertThat(wired.answered(withAnother)).isEqualTo(AUTHORIZATION_DIFFERS);
    assertThat(wired.answered(withItsOwn)).contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_force_carrying_an_empty_authorization_number_is_not_checked() {
    Held held = aJ5Of("100");
    String withAnEmptyOne =
        TranzilaWire.aForce(held.id(), held.token(), "10")
            .replace("\"txn_type\"", "\"authorization_number\":\"\",\"txn_type\"");

    assertThat(wired.answered(withAnEmptyOne)).contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_force_after_the_j5_validity_is_refused_20112_and_one_just_before_it_is_approved() {
    Held expired = aJ5Of("100");
    Held live = aJ5Of("100");
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofDays(7)));

    assertThat(wired.answered(TranzilaWire.aForce(expired.id(), expired.token(), "1")))
        .isEqualTo(NOT_FOUND);
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofDays(7)).minusSeconds(1));
    assertThat(wired.answered(TranzilaWire.aForce(live.id(), live.token(), "1")))
        .contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_force_another_request_closed_the_j5_with_first_is_refused_20112() {
    Held held = aJ5Of("100");
    wired.transactions.closedByAnotherRequestFirst();

    TranzilaOperationResult refused =
        wired.create(TranzilaWire.aForce(held.id(), held.token(), "10"));

    assertThat(refused.body().text()).isEqualTo(NOT_FOUND);
    assertThat(refused.refusal()).isEqualTo(RefusalSubject.theObject(Long.toString(held.id())));
  }

  @Test
  void an_original_whose_id_another_request_closed_first_names_the_id_it_drew() {
    wired.transactions.closedByAnotherRequestFirst();

    TranzilaOperationResult refused = wired.create(TranzilaWire.aDebit());

    assertThat(refused.refusal()).isEqualTo(RefusalSubject.theObject("10000001"));
  }

  @Test
  void a_force_with_a_token_this_run_never_answered_is_refused_20111() {
    Held held = aJ5Of("100");

    assertThat(wired.answered(TranzilaWire.aForce(held.id(), "tnosuchtoken4245", "1")))
        .contains("\"error_code\":20111");
  }

  @Test
  void a_force_a_scenario_declines_is_kept_and_leaves_the_j5_held() {
    Held held = aJ5Of("100");
    wired.writes(TranzilaOperations.FORCE, new ScenarioAction.TerminalFailure("004"));

    TranzilaOperationResult declined =
        wired.create(TranzilaWire.aForce(held.id(), held.token(), "10"));

    assertThat(declined.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(wired.theLastStored().referenceTxnId()).isEqualTo(held.id());
    assertThat(wired.thePaymentOf(held.id()).isAJ5StillHeldAt(TranzilaWiring.NOW)).isTrue();
  }

  @Test
  void a_reversal_releases_a_live_j5_in_full_and_answers_no_tranmode() {
    Held held = aJ5Of("100");

    String reversed = wired.answered(TranzilaWire.aReversal(held.id(), held.auth()));

    assertThat(reversed)
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"amount\":100,\"txn_type\":\"reversal\"}")
        .contains("\"card_mask\":\"458021xxxxxx4245\"")
        .contains("\"token\":\"" + held.token() + "\"");
    assertThat(wired.thePaymentOf(held.id()).normalizedStatusAt(TranzilaWiring.NOW))
        .isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(wired.answered(TranzilaWire.aForce(held.id(), held.token(), "1")))
        .isEqualTo(NOT_FOUND);
    assertThat(wired.answered(TranzilaWire.aReversal(held.id(), held.auth()))).isEqualTo(NOT_FOUND);
  }

  @Test
  void a_reversal_naming_no_original_is_refused_21101() {
    assertThat(
            wired.answered(
                "{\"terminal_name\":\"myterminal\",\"txn_type\":\"reversal\","
                    + "\"authorization_number\":\"1\"}"))
        .isEqualTo(INDEX_EMPTY);
  }

  @Test
  void a_reversal_without_an_authorization_number_or_with_an_empty_one_is_refused_22101() {
    Held held = aJ5Of("100");

    assertThat(
            wired.answered(
                "{\"terminal_name\":\"myterminal\",\"txn_type\":\"reversal\","
                    + "\"reference_txn_id\":"
                    + held.id()
                    + "}"))
        .isEqualTo(AUTHORIZATION_EMPTY);
    assertThat(wired.answered(TranzilaWire.aReversal(held.id(), "")))
        .isEqualTo(AUTHORIZATION_EMPTY);
  }

  @Test
  void a_reversal_naming_an_unknown_original_is_refused_20112() {
    assertThat(wired.answered(TranzilaWire.aReversal(99999999L, "1"))).isEqualTo(NOT_FOUND);
  }

  @Test
  void a_reversal_carrying_another_authorization_number_is_refused_22100() {
    Held held = aJ5Of("100");

    assertThat(wired.answered(TranzilaWire.aReversal(held.id(), "7654321")))
        .isEqualTo(AUTHORIZATION_DIFFERS);
  }

  @Test
  void a_reversal_of_a_debit_of_a_forced_j5_or_of_an_expired_j5_is_refused_20112() {
    long debit = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));
    String debitAuth = String.valueOf(wired.theLastStored().authNumber());
    Held forced = aJ5Of("100");
    wired.create(TranzilaWire.aForce(forced.id(), forced.token(), "10"));
    long force = wired.theLastStored().transactionId();
    String forceAuth = String.valueOf(wired.theLastStored().authNumber());
    Held expired = aJ5Of("100");
    wired.clock.moveTo(TranzilaWiring.NOW.plus(Duration.ofDays(8)));

    assertThat(wired.answered(TranzilaWire.aReversal(debit, debitAuth))).isEqualTo(NOT_FOUND);
    assertThat(wired.answered(TranzilaWire.aReversal(forced.id(), forced.auth())))
        .isEqualTo(NOT_FOUND);
    assertThat(wired.answered(TranzilaWire.aReversal(force, forceAuth))).isEqualTo(NOT_FOUND);
    assertThat(wired.answered(TranzilaWire.aReversal(expired.id(), expired.auth())))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void a_reversal_of_a_j2_is_refused_20112() {
    long j2 = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aJ2()));
    String auth = String.valueOf(wired.theLastStored().authNumber());

    assertThat(wired.answered(TranzilaWire.aReversal(j2, auth))).isEqualTo(NOT_FOUND);
  }

  @Test
  void a_reversal_naming_a_declined_transaction_is_refused_20112() {
    Held held = aJ5Of("100");
    wired.create(TranzilaWire.aForce(held.id(), held.token(), "1000"));
    long declined = wired.theLastStored().transactionId();

    assertThat(wired.answered(TranzilaWire.aReversal(declined, held.auth()))).isEqualTo(NOT_FOUND);
  }

  @Test
  void partial_and_repeated_credits_of_a_debit_are_approved_while_their_total_fits() {
    Charged debit = aDebitOf("100");

    String first =
        wired.answered(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "40"));
    String second =
        wired.answered(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "60"));
    String third =
        wired.answered(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "0.01"));

    assertThat(first)
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"amount\":40,\"txn_type\":\"credit\",\"tranmode\":\"C\"");
    assertThat(second).contains("\"processor_response_code\":\"000\"");
    assertThat(third).isEqualTo(CREDITED_ABOVE);
    assertThat(wired.thePaymentOf(debit.id()).normalizedStatusAt(TranzilaWiring.NOW))
        .isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void a_credit_above_the_original_is_refused_23002() {
    Charged debit = aDebitOf("100");

    assertThat(
            wired.answered(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "100.01")))
        .isEqualTo(CREDITED_ABOVE);
  }

  @Test
  void a_credit_of_a_force_is_approved_and_one_of_its_j5_is_refused_20112() {
    Held held = aJ5Of("100");
    wired.create(TranzilaWire.aForce(held.id(), held.token(), "60"));
    TranzilaTransaction force = wired.theLastStored();

    String ofTheForce =
        wired.answered(
            TranzilaWire.aCredit(
                force.transactionId(), String.valueOf(force.authNumber()), held.token(), "60"));
    String ofTheJ5 =
        wired.answered(TranzilaWire.aCredit(held.id(), held.auth(), held.token(), "1"));

    assertThat(ofTheForce).contains("\"processor_response_code\":\"000\"");
    assertThat(wired.theLastStored().rootTxnId()).isEqualTo(held.id());
    assertThat(ofTheJ5).isEqualTo(NOT_FOUND);
  }

  @Test
  void a_credit_of_a_cancelled_debit_is_refused_23001() {
    Charged debit = aDebitOf("100");
    wired.create(TranzilaWire.aCancel(debit.id(), debit.auth()));

    assertThat(wired.answered(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "1")))
        .isEqualTo(ALREADY_CANCELED);
  }

  @Test
  void a_credit_to_another_card_is_refused_20112() {
    Charged debit = aDebitOf("100");

    assertThat(
            wired.answered(
                TranzilaWire.aCredit(debit.id(), debit.auth(), TranzilaWire.A_MASTERCARD, "1")))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void a_credit_to_the_card_number_the_debit_charged_is_approved() {
    Charged debit = aDebitOf("100");

    assertThat(
            wired.answered(
                TranzilaWire.aCredit(debit.id(), debit.auth(), TranzilaWire.A_VISA, "1")))
        .contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_credit_of_a_credit_is_refused_20112() {
    Charged debit = aDebitOf("100");
    wired.create(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "1"));
    TranzilaTransaction credit = wired.theLastStored();

    assertThat(
            wired.answered(
                TranzilaWire.aCredit(
                    credit.transactionId(),
                    String.valueOf(credit.authNumber()),
                    debit.token(),
                    "1")))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void a_credit_with_an_unknown_token_is_refused_20111_before_its_original_is_looked_up() {
    assertThat(wired.answered(TranzilaWire.aCredit(99999999L, "1", "tnosuchtoken4245", "1")))
        .contains("\"error_code\":20111");
  }

  @Test
  void a_credit_carrying_another_authorization_number_is_refused_22100() {
    Charged debit = aDebitOf("100");

    assertThat(wired.answered(TranzilaWire.aCredit(debit.id(), "7654321", debit.token(), "1")))
        .isEqualTo(AUTHORIZATION_DIFFERS);
  }

  @Test
  void a_cancel_of_a_debit_cancels_it_in_full_once() {
    Charged debit = aDebitOf("100");

    String cancelled = wired.answered(TranzilaWire.aCancel(debit.id(), debit.auth()));

    assertThat(cancelled)
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"amount\":100,\"txn_type\":\"cancel\",\"tranmode\":\"D\"")
        .contains("\"token\":\"" + debit.token() + "\"");
    assertThat(wired.thePaymentOf(debit.id()).normalizedStatusAt(TranzilaWiring.NOW))
        .isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(wired.answered(TranzilaWire.aCancel(debit.id(), debit.auth()))).isEqualTo(NOT_FOUND);
  }

  @Test
  void a_cancel_of_a_force_and_of_a_credit_are_approved() {
    Held held = aJ5Of("100");
    wired.create(TranzilaWire.aForce(held.id(), held.token(), "60"));
    TranzilaTransaction force = wired.theLastStored();
    wired.create(
        TranzilaWire.aCredit(
            force.transactionId(), String.valueOf(force.authNumber()), held.token(), "10"));
    TranzilaTransaction credit = wired.theLastStored();

    String ofTheCredit =
        wired.answered(
            TranzilaWire.aCancel(credit.transactionId(), String.valueOf(credit.authNumber())));
    String ofTheForce =
        wired.answered(
            TranzilaWire.aCancel(force.transactionId(), String.valueOf(force.authNumber())));

    assertThat(ofTheCredit).contains("\"amount\":10,\"txn_type\":\"cancel\"");
    assertThat(ofTheForce).contains("\"amount\":60,\"txn_type\":\"cancel\"");
    assertThat(wired.thePaymentOf(held.id()).normalizedStatusAt(TranzilaWiring.NOW))
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_cancel_of_a_j5_or_of_a_reversal_is_refused_20112() {
    Held held = aJ5Of("100");
    String ofTheJ5 = wired.answered(TranzilaWire.aCancel(held.id(), held.auth()));
    wired.create(TranzilaWire.aReversal(held.id(), held.auth()));
    TranzilaTransaction reversal = wired.theLastStored();

    assertThat(ofTheJ5).isEqualTo(NOT_FOUND);
    assertThat(
            wired.answered(
                TranzilaWire.aCancel(
                    reversal.transactionId(), String.valueOf(reversal.authNumber()))))
        .isEqualTo(NOT_FOUND);
  }

  @Test
  void a_cancel_naming_no_original_or_no_authorization_number_is_refused_with_its_own_code() {
    assertThat(
            wired.answered(
                "{\"terminal_name\":\"myterminal\",\"txn_type\":\"cancel\","
                    + "\"authorization_number\":\"1\"}"))
        .isEqualTo(INDEX_EMPTY);
    assertThat(wired.answered(TranzilaWire.aCancel(1L, ""))).isEqualTo(AUTHORIZATION_EMPTY);
  }

  @Test
  void a_follow_on_of_another_accounts_transaction_is_refused_20112() {
    Charged debit = aDebitOf("100");
    wired.terminals.with(
        TranzilaDoubles.terminal(
            TranzilaWiring.RUN,
            "account-2",
            "otherterminal",
            java.util.Map.of(
                TranzilaAuthentication.SETTING,
                TranzilaAuthentication.THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name())));

    TranzilaOperationResult refused =
        wired.create.handle(
            TranzilaApiCall.of(
                "POST",
                TranzilaApiController.CREATE,
                java.util.Map.of(),
                TranzilaWire.aCancel(debit.id(), debit.auth())
                    .replace("myterminal", "otherterminal")),
            java.util.Map.of(),
            TranzilaWiring.RUN,
            wired.terminals.find(TranzilaWiring.RUN, "account-2").orElseThrow(),
            TranzilaWiring.NOW,
            dev.marcinromanowski.pspsim.kernel.ObjectOrigin.PROVIDER_API);

    assertThat(refused.body().text()).isEqualTo(NOT_FOUND);
  }

  @Test
  void a_follow_on_a_scenario_declines_is_kept_against_its_original_without_an_authorization() {
    Charged debit = aDebitOf("100");
    wired.writes(TranzilaOperations.CANCEL, new ScenarioAction.TerminalFailure("004"));

    String declined = wired.answered(TranzilaWire.aCancel(debit.id(), debit.auth()));

    assertThat(declined).contains("\"error_code\":4");
    TranzilaTransaction kept = wired.theLastStored();
    assertThat(kept.rootTxnId()).isEqualTo(debit.id());
    assertThat(kept.referenceTxnId()).isEqualTo(debit.id());
    assertThat(kept.authNumber()).isNull();
    assertThat(kept.token()).isNull();
    assertThat(wired.thePaymentOf(debit.id()).normalizedStatusAt(TranzilaWiring.NOW))
        .isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  private Held aJ5Of(String amount) {
    String answered = wired.answered(TranzilaWire.aJ5(amount));
    return new Held(
        TranzilaWire.transactionIdIn(answered),
        TranzilaWire.textIn(answered, "token"),
        TranzilaWire.textIn(answered, "auth_number"));
  }

  private Charged aDebitOf(String amount) {
    String answered =
        wired.answered(
            TranzilaWire.withCard("\"txn_type\":\"debit\"", TranzilaWire.A_VISA, amount));
    return new Charged(
        TranzilaWire.transactionIdIn(answered),
        TranzilaWire.textIn(answered, "token"),
        TranzilaWire.textIn(answered, "auth_number"));
  }

  record Held(long id, String token, String auth) {}

  record Charged(long id, String token, String auth) {}
}
