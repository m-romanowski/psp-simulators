package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

final class StripeChargeLookup {

  static final String A_CHARGE_THAT_DID_NOT_SUCCEED_HOLDS_NO_FUNDS =
      "This Charge did not succeed, so there are no funds on it to return. Provider stripe"
          + " publishes paid as true if the charge succeeded or was successfully authorized for"
          + " later capture, and this charge is neither.";

  static final String A_CHARGE_OUTLIVED_ITS_PAYMENT =
      "a Stripe charge names a PaymentIntent this simulator no longer stores, and a charge is"
          + " minted on a payment and deleted with it, so no answer about it could be true";

  private StripeChargeLookup() {}

  static Optional<StripeCharge> of(
      StripeCharges charges, TestRunId testRunId, StripeAccount account, String chargeId) {
    return charges
        .find(testRunId, chargeId)
        .filter(charge -> charge.accountId().equals(account.accountId()));
  }

  static StripePaymentIntent thePaymentOf(StripePaymentIntents intents, StripeCharge charge) {
    return intents
        .find(charge.testRunId(), charge.intentId())
        .orElseThrow(() -> new IllegalStateException(A_CHARGE_OUTLIVED_ITS_PAYMENT));
  }
}
