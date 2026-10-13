package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;

final class RetrievePaymentIntent {

  private final StripePaymentIntents intents;

  RetrievePaymentIntent(StripePaymentIntents intents) {
    this.intents = intents;
  }

  StripeOperationResult handle(
      TestRunId testRunId, StripeAccount account, String intentId, Instant now) {
    Optional<StripePaymentIntent> addressed =
        StripeLookup.of(intents, testRunId, account, intentId);
    return addressed
        .map(
            intent ->
                StripeOperationResult.answered(intent.intentId(), StripeIntentView.of(intent, now)))
        .orElseGet(
            () ->
                StripeOperationResult.refusedBeforeExecuting(
                    RefusalSubject.noObjectAnsweredTo(StripeLookup.INTENT_PARAMETER),
                    StripeErrors.resourceMissing(StripeLookup.INTENT_PARAMETER)));
  }
}
