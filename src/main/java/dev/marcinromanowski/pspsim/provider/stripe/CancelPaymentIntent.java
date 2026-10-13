package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CancelPaymentIntent {

  private final StripePaymentIntents intents;
  private final ScenarioEngine scenarios;

  CancelPaymentIntent(StripePaymentIntents intents, ScenarioEngine scenarios) {
    this.intents = intents;
    this.scenarios = scenarios;
  }

  StripeOperationResult handle(
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      String intentId,
      Instant now) {
    String declared = form.first(StripeFields.CANCELLATION_REASON);
    StripeCancellationReason reason = null;
    if (declared != null) {
      Optional<StripeCancellationReason> sent = StripeCancellationReason.sentByACaller(declared);
      if (sent.isEmpty()) {
        return StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theRequestWasRefused(),
            StripeErrors.unreadableParameter(
                StripeFields.CANCELLATION_REASON,
                StripeCancellationReason.theReasonsACallerMaySend()));
      }
      reason = sent.get();
    }
    Optional<StripePaymentIntent> addressed =
        StripeLookup.of(intents, testRunId, account, intentId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeLookup.INTENT_PARAMETER),
          StripeErrors.resourceMissing(StripeLookup.INTENT_PARAMETER));
    }
    StripePaymentIntent intent = addressed.get();
    if (!intent.statusAt(now).cancellable()) {
      return StripeLookup.unexpectedState(intent, now, "canceled");
    }
    StripeCancellationReason cancellationReason = reason;
    return StripeOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account, intent)),
        () -> cancel(intent, cancellationReason, now),
        decline -> decline(intent, decline, now));
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      StripePaymentIntent intent) {
    return new ScenarioRequest(
        StripeApi.STRIPE,
        testRunId,
        StripeStoryKey.ofIntent(intent.intentId()),
        account.accountId(),
        null,
        intent.intentId(),
        null,
        null,
        null,
        null,
        StripeOperations.CANCEL,
        headers);
  }

  private StripeOperationResult cancel(
      StripePaymentIntent intent, @Nullable StripeCancellationReason reason, Instant now) {
    LedgerClaim applied = intents.cancelled(intent, reason, now);
    return StripeLookup.answeredFor(
        applied,
        intent,
        now,
        () ->
            StripeOperationResult.succeeded(
                intent.intentId(), StripeIntentView.of(intent.cancelledFor(reason, now), now)));
  }

  private static StripeOperationResult decline(
      StripePaymentIntent intent, StripeDecline decline, Instant now) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(intent.intentId()),
        StripeErrors.declined(decline).about(StripeIntentView.of(intent, now)));
  }
}
