package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;

final class RetrieveCharge {

  private final StripeCharges charges;
  private final StripePaymentIntents intents;

  RetrieveCharge(StripeCharges charges, StripePaymentIntents intents) {
    this.charges = charges;
    this.intents = intents;
  }

  StripeOperationResult handle(
      TestRunId testRunId, StripeAccount account, String chargeId, Instant now) {
    Optional<StripeCharge> addressed = StripeChargeLookup.of(charges, testRunId, account, chargeId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.CHARGE),
          StripeErrors.resourceMissing(StripeFields.CHARGE));
    }
    StripeCharge charge = addressed.get();
    StripePaymentIntent payment = StripeChargeLookup.thePaymentOf(intents, charge);
    return StripeOperationResult.answered(
        charge.chargeId(), StripeChargeView.of(charge, payment, now));
  }
}
