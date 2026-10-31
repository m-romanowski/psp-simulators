package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

final class RetrieveSetupIntent {

  private final StripeSetupIntents intents;

  RetrieveSetupIntent(StripeSetupIntents intents) {
    this.intents = intents;
  }

  StripeOperationResult handle(TestRunId testRunId, StripeAccount account, String setupIntentId) {
    return StripeSetupLookup.of(intents, testRunId, account, setupIntentId)
        .map(
            intent ->
                StripeOperationResult.answered(
                    intent.setupIntentId(), StripeSetupIntentView.of(intent)))
        .orElseGet(
            () ->
                StripeOperationResult.refusedBeforeExecuting(
                    RefusalSubject.noObjectAnsweredTo(StripeSetupLookup.SETUP_INTENT_PARAMETER),
                    StripeErrors.resourceMissing(StripeSetupLookup.SETUP_INTENT_PARAMETER)));
  }
}
