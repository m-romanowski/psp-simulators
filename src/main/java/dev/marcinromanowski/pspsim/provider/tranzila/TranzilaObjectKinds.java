package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;

final class TranzilaObjectKinds {

  static final ProviderObjectKind A_DEBIT =
      new ProviderObjectKind(
          "a debit",
          "Tranzila \"Charges the card\" in one call, so the money is taken already and there is"
              + " no hold to force or reverse; it can be credited back or cancelled.");

  static final ProviderObjectKind A_J5 =
      new ProviderObjectKind(
          "a J5 hold",
          "Tranzila \"Reserves credit for the specified amount\" and charges nothing yet: a force"
              + " charges up to that amount once, and a reversal releases it.");

  static final ProviderObjectKind A_J2 =
      new ProviderObjectKind(
          "a J2 check",
          "Tranzila \"Checks the card without capturing funds\", so nothing is held and nothing"
              + " is charged.");

  static final ProviderObjectKind A_FORCE =
      new ProviderObjectKind(
          "a force",
          "The charge of a J5 hold, a transaction of its own with its own transaction_id that names"
              + " the J5 in reference_txn_id; it can be credited back or cancelled.");

  static final ProviderObjectKind A_CREDIT =
      new ProviderObjectKind(
          "a credit",
          "Money returned to the card, a transaction of its own that names the debit or the force"
              + " it credits in reference_txn_id; it can be cancelled.");

  static final ProviderObjectKind A_REVERSAL =
      new ProviderObjectKind(
          "a reversal",
          "The release of a J5 hold in full, a transaction of its own that names the J5 in"
              + " reference_txn_id.");

  static final ProviderObjectKind A_CANCEL =
      new ProviderObjectKind(
          "a cancel",
          "The cancellation of a debit, a force or a credit in full, a transaction of its own that"
              + " names the one it cancels in reference_txn_id.");

  static final ProviderObjectKind A_PAYMENT_PAGE =
      new ProviderObjectKind(
          "a payment page",
          "The DirectNG page a payer is sent to with the merchant's parameters: it waits for the"
              + " payer, and once they pay it names the transaction it made, which is listed on"
              + " its own.");

  private TranzilaObjectKinds() {}

  static ProviderObjectKind of(TranzilaTransaction transaction) {
    return switch (transaction.type()) {
      case DEBIT -> A_DEBIT;
      case VERIFY -> transaction.isAJ5() ? A_J5 : A_J2;
      case FORCE -> A_FORCE;
      case CREDIT -> A_CREDIT;
      case REVERSAL -> A_REVERSAL;
      case CANCEL -> A_CANCEL;
    };
  }
}
