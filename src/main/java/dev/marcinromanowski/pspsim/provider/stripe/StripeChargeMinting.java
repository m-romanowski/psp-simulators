package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

final class StripeChargeMinting {

  static final int ID_ATTEMPTS = 5;

  static final String ID_PREFIX = "ch_sim_";

  static final int ID_LENGTH = 16;

  private StripeChargeMinting() {}

  static StripeCharge of(
      StripeCharges charges,
      IdGenerator ids,
      StripePaymentIntent intent,
      @Nullable String paymentMethod,
      StripeChargeStatus status,
      Instant at) {
    return new StripeCharge(
        intent.testRunId(),
        anIdClaimedFor(charges, ids, intent.testRunId()),
        intent.intentId(),
        intent.accountId(),
        intent.amount(),
        intent.currency(),
        paymentMethod,
        status,
        at);
  }

  private static String anIdClaimedFor(
      StripeCharges charges, IdGenerator ids, TestRunId testRunId) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String chargeId = ID_PREFIX + ids.hex(ID_LENGTH);
      if (charges.claimChargeId(testRunId, chargeId)) {
        return chargeId;
      }
    }
    throw new IllegalStateException(
        "no Stripe charge id could be claimed in " + ID_ATTEMPTS + " attempts");
  }
}
