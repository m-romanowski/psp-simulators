package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class ConfirmPaymentIntent {

  private final StripePaymentIntents intents;
  private final StripePaymentMethods paymentMethods;
  private final StripeCharges charges;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;
  private final StripeScheduledOutcomes events;

  ConfirmPaymentIntent(
      StripePaymentIntents intents,
      StripePaymentMethods paymentMethods,
      StripeCharges charges,
      IdGenerator ids,
      ScenarioEngine scenarios,
      StripeScheduledOutcomes events) {
    this.intents = intents;
    this.paymentMethods = paymentMethods;
    this.charges = charges;
    this.ids = ids;
    this.scenarios = scenarios;
    this.events = events;
  }

  StripeOperationResult handle(
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      String intentId,
      Instant now) {
    Optional<StripePaymentIntent> addressed =
        StripeLookup.of(intents, testRunId, account, intentId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeLookup.INTENT_PARAMETER),
          StripeErrors.resourceMissing(StripeLookup.INTENT_PARAMETER));
    }
    StripePaymentIntent intent = addressed.get();
    if (!intent.statusAt(now).confirmable()) {
      return StripeLookup.unexpectedState(intent, now, "confirmed");
    }
    String paymentMethod = paymentMethodOf(form, intent);
    if (paymentMethod == null) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.intentId()),
          StripeErrors.parameterMissing(StripeFields.PAYMENT_METHOD));
    }
    StripeError detached =
        StripePaymentMethodLookup.theRefusalADetachedMethodEarns(
            paymentMethods, testRunId, account, paymentMethod);
    if (detached != null) {
      return StripeOperationResult.refused(RefusalSubject.theObject(intent.intentId()), detached);
    }
    return StripeOperationResult.decidedBy(
        scenarios.decide(requestOf(headers, testRunId, account, intent, paymentMethod)),
        () -> confirm(intent, paymentMethod, now),
        decline -> decline(intent, paymentMethod, decline, now),
        pending -> pend(intent, paymentMethod, pending, now));
  }

  private static @Nullable String paymentMethodOf(StripeForm form, StripePaymentIntent intent) {
    String sent = form.first(StripeFields.PAYMENT_METHOD);
    return sent == null ? intent.paymentMethod() : sent;
  }

  private static ScenarioRequest requestOf(
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      StripePaymentIntent intent,
      String paymentMethod) {
    return new ScenarioRequest(
        StripeApi.STRIPE,
        testRunId,
        StripeStoryKey.ofIntent(intent.intentId()),
        account.accountId(),
        null,
        intent.intentId(),
        null,
        paymentMethod,
        null,
        null,
        StripeOperations.CONFIRM,
        headers);
  }

  private StripeOperationResult confirm(
      StripePaymentIntent intent, String paymentMethod, Instant now) {
    StripeIntentStatus reached = intent.captureMethod().afterASuccessfulConfirmation();
    MonetaryOperation operation =
        StripeConfirmation.operationOf(
            intent.ledger().nextSequence(), intent.captureMethod(), intent.amount(), now);
    StripeCharge charge =
        theChargeThisAttemptMints(intent, paymentMethod, StripeChargeStatus.SUCCEEDED, now);
    LedgerClaim applied = intents.confirmed(intent, reached, paymentMethod, operation, charge, now);
    return StripeLookup.answeredFor(
        applied,
        intent,
        now,
        () ->
            StripeOperationResult.succeeded(
                intent.intentId(),
                StripeIntentView.of(
                    intent.confirmedAs(reached, paymentMethod, operation, charge.chargeId(), now),
                    now)));
  }

  private StripeOperationResult pend(
      StripePaymentIntent intent,
      String paymentMethod,
      ScenarioDecision.Pend pending,
      Instant now) {
    StripeDecline comingToRestWith = StripePending.theOutcomeItComesToRestWith(pending);
    Instant landing = now.plus(pending.settlesAfter());
    StripePaymentIntent settling =
        intent.pendingUntil(landing, comingToRestWith, paymentMethod, now);
    LedgerClaim applied = intents.pending(intent, paymentMethod, landing, comingToRestWith, now);
    return StripeLookup.answeredFor(
        applied,
        intent,
        now,
        () -> {
          events.theOutcomeScheduledFor(settling, landing, pending.settlesAfter());
          return StripeOperationResult.succeeded(
              intent.intentId(), StripeIntentView.of(settling, now));
        });
  }

  private StripeOperationResult decline(
      StripePaymentIntent intent, String paymentMethod, StripeDecline decline, Instant now) {
    StripeCharge charge =
        theChargeThisAttemptMints(intent, paymentMethod, StripeChargeStatus.FAILED, now);
    LedgerClaim applied = intents.declined(intent, decline, paymentMethod, charge, now);
    return StripeLookup.answeredFor(
        applied,
        intent,
        now,
        () ->
            StripeOperationResult.refusedAfterChangingState(
                RefusalSubject.theObject(intent.intentId()),
                StripeErrors.declined(decline)
                    .about(
                        StripeIntentView.of(
                            intent.declinedWith(decline, paymentMethod, charge.chargeId(), now),
                            now))));
  }

  private StripeCharge theChargeThisAttemptMints(
      StripePaymentIntent intent, String paymentMethod, StripeChargeStatus reached, Instant now) {
    return StripeChargeMinting.of(charges, ids, intent, paymentMethod, reached, now);
  }
}
