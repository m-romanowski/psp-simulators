package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

final class StripeLookup {

  static final String INTENT_PARAMETER = "intent";

  private StripeLookup() {}

  static Optional<StripePaymentIntent> of(
      StripePaymentIntents intents, TestRunId testRunId, StripeAccount account, String intentId) {
    return intents
        .find(testRunId, intentId)
        .filter(intent -> intent.accountId().equals(account.accountId()));
  }

  static StripeOperationResult unexpectedState(
      StripePaymentIntent intent, Instant now, String attempted) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(intent.intentId()),
        StripeErrors.unexpectedState(
                "A PaymentIntent with a status of "
                    + intent.statusAt(now).wireValue()
                    + " cannot be "
                    + attempted
                    + ".")
            .about(StripeIntentView.of(intent, now)));
  }

  static StripeOperationResult answeredFor(
      LedgerClaim applied,
      StripePaymentIntent intent,
      Instant now,
      Supplier<StripeOperationResult> whenApplied) {
    return switch (applied) {
      case APPLIED -> whenApplied.get();
      case CLAIM_TAKEN -> unexpectedState(intent, now, "changed by two requests at once");
      case OBJECT_GONE ->
          StripeOperationResult.refused(
              RefusalSubject.noObjectAnsweredTo(INTENT_PARAMETER),
              StripeErrors.resourceMissing(INTENT_PARAMETER));
    };
  }
}
