package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class TranzilaSettlementTest {

  private static final String UNCREDITABLE =
      "{\"error_code\":20112,\"message\":\"An origin transaction index is linked to an"
          + " uncreditable origin transaction (e.g., rejected or non-existent index) or an attempt"
          + " to credit a different card than the one originally charged.\"}";

  private static final String ALREADY_CANCELED =
      "{\"error_code\":23001,\"message\":\"id 1 - Attempting to credit a transaction that has"
          + " already been canceled\"}";

  private static final String ALREADY_CREDITED =
      "{\"error_code\":23002,\"message\":\"Already Credited this transaction id\"}";

  private final TranzilaWiring wired = new TranzilaWiring();

  @Test
  void a_cancel_of_a_credited_debit_is_refused_as_an_uncreditable_origin() {
    Charged debit = aDebit();
    wired.create(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "10"));

    assertThat(wired.answered(TranzilaWire.aCancel(debit.id(), debit.auth())))
        .isEqualTo(UNCREDITABLE);
  }

  @Test
  void a_credit_that_loses_its_original_to_a_cancel_meanwhile_is_refused_23001() {
    Charged debit = aDebit();
    wired.transactions.closedFirstBy(aFollowOnOf(debit, 90000001L, TranzilaTxnType.CANCEL));

    assertThat(wired.answered(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "10")))
        .isEqualTo(ALREADY_CANCELED);
  }

  @Test
  void a_credit_that_loses_its_original_to_another_credit_meanwhile_is_refused_23002() {
    Charged debit = aDebit();
    wired.transactions.closedFirstBy(aFollowOnOf(debit, 90000001L, TranzilaTxnType.CREDIT));

    assertThat(wired.answered(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "10")))
        .isEqualTo(ALREADY_CREDITED);
  }

  @Test
  void a_cancel_that_loses_its_original_to_a_credit_meanwhile_is_refused_as_uncreditable() {
    Charged debit = aDebit();
    wired.transactions.closedFirstBy(aFollowOnOf(debit, 90000001L, TranzilaTxnType.CREDIT));

    assertThat(wired.answered(TranzilaWire.aCancel(debit.id(), debit.auth())))
        .isEqualTo(UNCREDITABLE);
  }

  @Test
  void a_debit_takes_a_refund_until_its_one_credit_and_none_once_that_credit_is_cancelled() {
    Charged debit = aDebit();
    assertThat(stateOf(debit.id()).refundsAccepted()).isTrue();

    wired.create(TranzilaWire.aCredit(debit.id(), debit.auth(), debit.token(), "10"));
    TranzilaTransaction credit = wired.theLastStored();
    assertThat(stateOf(debit.id()).refundsAccepted())
        .as("one credit per original, so a credited debit takes no further refund")
        .isFalse();

    wired.create(TranzilaWire.aCancel(credit.transactionId(), String.valueOf(credit.authNumber())));
    assertThat(stateOf(debit.id()).refundsAccepted())
        .as("a cancelled credit still holds the one credit of its original")
        .isFalse();
  }

  @Test
  void a_cancelled_or_declined_debit_takes_no_refund() {
    Charged cancelled = aDebit();
    wired.create(TranzilaWire.aCancel(cancelled.id(), cancelled.auth()));
    wired.writes(TranzilaOperations.DEBIT, new ScenarioAction.TerminalFailure("004"));
    long declined = TranzilaWire.transactionIdIn(wired.answered(TranzilaWire.aDebit()));

    assertThat(stateOf(cancelled.id()).refundsAccepted()).isFalse();
    assertThat(stateOf(declined).refundsAccepted()).isFalse();
  }

  @Test
  void a_follow_on_in_another_currency_than_its_original_is_refused_400_20004() {
    Charged debit = aDebit();
    String inDollars =
        TranzilaWire.aCancel(debit.id(), debit.auth())
            .replace("}", ",\"txn_currency_code\":\"USD\"}");
    String inShekels =
        TranzilaWire.aCancel(debit.id(), debit.auth())
            .replace("}", ",\"txn_currency_code\":\"ILS\"}");

    TranzilaOperationResult refused = wired.create(inDollars);

    assertThat(refused.body().httpStatus()).isEqualTo(400);
    assertThat(refused.body().text())
        .isEqualTo(
            "{\"error_code\":\"20004\",\"message\":\"JSON Schema mismatch: txn_currency_code must"
                + " be ILS, the currency of the transaction reference_txn_id names\"}");
    assertThat(wired.answered(inShekels)).contains("\"processor_response_code\":\"000\"");
  }

  @Test
  void a_force_in_another_currency_than_its_j5_is_refused_400_20004() {
    String held = wired.answered(TranzilaWire.aJ5("100"));
    long j5 = TranzilaWire.transactionIdIn(held);
    String force =
        TranzilaWire.aForce(j5, TranzilaWire.textIn(held, "token"), "10")
            .replace("{\"terminal", "{\"txn_currency_code\":\"EUR\",\"terminal");

    assertThat(wired.create(force).body().httpStatus()).isEqualTo(400);
  }

  @Test
  void a_dc_disable_is_a_duplicate_only_of_an_approval_of_the_same_type_and_verify_mode() {
    Charged debit = aDebitUnder("order-1");

    String cancel =
        wired.answered(
            TranzilaWire.aCancel(debit.id(), debit.auth())
                .replace(
                    "}",
                    ",\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"order-1\"}]}"));
    String j2 = wired.answered(underDcDisable(TranzilaWire.aJ2(), "order-2"));
    String j5 = wired.answered(underDcDisable(TranzilaWire.aJ5("5"), "order-2"));
    String anotherJ5 = wired.answered(underDcDisable(TranzilaWire.aJ5("5"), "order-2"));

    assertThat(cancel).contains("\"message\":\"success\"").contains("\"txn_type\":\"cancel\"");
    assertThat(j2).contains("\"message\":\"success\"");
    assertThat(j5).contains("\"message\":\"success\"");
    assertThat(anotherJ5).contains("already been made");
    assertThat(wired.transactions.theKeysHeld())
        .extracting(TranzilaDcDisableKey::type, TranzilaDcDisableKey::verifyMode)
        .containsExactly(
            Tuple.tuple(TranzilaTxnType.DEBIT, null),
            Tuple.tuple(TranzilaTxnType.CANCEL, null),
            Tuple.tuple(TranzilaTxnType.VERIFY, 2),
            Tuple.tuple(TranzilaTxnType.VERIFY, 5),
            Tuple.tuple(TranzilaTxnType.VERIFY, 5));
  }

  @Test
  void a_dc_disable_lock_is_one_per_terminal_type_mode_and_value() {
    TranzilaDcDisableKey key =
        new TranzilaDcDisableKey(TranzilaWiring.RUN, "a", "v", TranzilaTxnType.VERIFY, 5);

    assertThat(key.lock())
        .isEqualTo(
            ("tranzila-unit"
                    + '\u0000'
                    + "a"
                    + '\u0000'
                    + "verify"
                    + '\u0000'
                    + "5"
                    + '\u0000'
                    + "v")
                .hashCode());
    assertThat(TranzilaDcDisableKey.LOCK_SPACE).isEqualTo("tranzila DCdisable".hashCode());
  }

  private ProviderObjectState stateOf(long id) {
    return wired.provider.state(TranzilaWiring.RUN, Long.toString(id)).orElseThrow();
  }

  private TranzilaTransaction aFollowOnOf(Charged debit, long id, TranzilaTxnType type) {
    TranzilaTransaction original = wired.thePaymentOf(debit.id()).root();
    return new TranzilaTransaction(
        TranzilaWiring.RUN,
        id,
        debit.id(),
        TranzilaWiring.ACCOUNT,
        TranzilaWiring.TERMINAL,
        type,
        null,
        debit.id(),
        "000",
        "1",
        "ILS",
        "1234567",
        null,
        original.card(),
        "0",
        "0",
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        TranzilaWiring.NOW);
  }

  private Charged aDebit() {
    String answered = wired.answered(TranzilaWire.aDebit());
    return new Charged(
        TranzilaWire.transactionIdIn(answered),
        TranzilaWire.textIn(answered, "token"),
        TranzilaWire.textIn(answered, "auth_number"));
  }

  private Charged aDebitUnder(String dcDisable) {
    String answered = wired.answered(underDcDisable(TranzilaWire.aDebit(), dcDisable));
    return new Charged(
        TranzilaWire.transactionIdIn(answered),
        TranzilaWire.textIn(answered, "token"),
        TranzilaWire.textIn(answered, "auth_number"));
  }

  private static String underDcDisable(String body, String value) {
    return body.replace(
        "}]}",
        "}],\"user_defined_fields\":[{\"name\":\"DCdisable\",\"value\":\"" + value + "\"}]}");
  }

  record Charged(long id, String token, String auth) {}
}
