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

  private TranzilaObjectKinds() {}

  static ProviderObjectKind of(TranzilaPayment payment) {
    TranzilaTransaction root = payment.root();
    if (root.type() == TranzilaTxnType.DEBIT) {
      return A_DEBIT;
    }
    return root.isAJ5() ? A_J5 : A_J2;
  }
}
