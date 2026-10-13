package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CapturePaymentIntent {

  private final StripePaymentIntents intents;
  private final ScenarioEngine scenarios;

  CapturePaymentIntent(StripePaymentIntents intents, ScenarioEngine scenarios) {
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
    StripeError unreadable = whatThisRequestDoesNotCarry(form);
    if (unreadable != null) {
      return StripeOperationResult.refusedBeforeExecuting(
          StripeRefusals.theRequestWasRefused(), unreadable);
    }
    Optional<StripePaymentIntent> addressed =
        StripeLookup.of(intents, testRunId, account, intentId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeLookup.INTENT_PARAMETER),
          StripeErrors.resourceMissing(StripeLookup.INTENT_PARAMETER));
    }
    StripePaymentIntent intent = addressed.get();
    if (intent.theAuthorizationHasExpired(now)) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.intentId()),
          StripeErrors.chargeExpiredForCapture().about(StripeIntentView.of(intent, now)));
    }
    if (intent.statusAt(now) != StripeIntentStatus.REQUIRES_CAPTURE) {
      return StripeLookup.unexpectedState(intent, now, "captured");
    }
    BigDecimal capturable = intent.capturableRemainderAt(now);
    BigDecimal requested = requestedBy(form, capturable);
    if (requested.signum() == 0) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.intentId()),
          StripeErrors.invalidChargeAmount(StripeFields.AMOUNT_TO_CAPTURE)
              .about(StripeIntentView.of(intent, now)));
    }
    if (requested.compareTo(capturable) > 0) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.intentId()),
          StripeErrors.amountTooLarge(StripeFields.AMOUNT_TO_CAPTURE)
              .about(StripeIntentView.of(intent, now)));
    }
    boolean closing = closesTheAuthorization(form);
    if (!closing && !StripeMulticapture.hasRoomForANonFinalCapture(intent)) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(intent.intentId()),
          StripeErrors.nonFinalCaptureCeilingReached().about(StripeIntentView.of(intent, now)));
    }
    return StripeOperationResult.decidedBy(
        scenarios.decide(requestOf(form, headers, testRunId, account, intent)),
        () -> capture(intent, requested, closing, now),
        decline -> decline(intent, decline, now));
  }

  private static @Nullable StripeError whatThisRequestDoesNotCarry(StripeForm form) {
    if (StripeFlag.of(form.first(StripeFields.FINAL_CAPTURE)) == null) {
      return StripeErrors.unreadableParameter(
          StripeFields.FINAL_CAPTURE, StripeFlag.THE_VALUES_IT_ACCEPTS);
    }
    String requested = form.first(StripeFields.AMOUNT_TO_CAPTURE);
    if (requested == null) {
      return null;
    }
    if (StripeAmounts.isTooLongForAJsonReader(requested)) {
      return StripeErrors.amountTooLarge(StripeFields.AMOUNT_TO_CAPTURE);
    }
    return StripeAmounts.isPositiveMinorUnits(requested)
        ? null
        : StripeErrors.invalidChargeAmount(StripeFields.AMOUNT_TO_CAPTURE);
  }

  private static boolean closesTheAuthorization(StripeForm form) {
    String declared = form.first(StripeFields.FINAL_CAPTURE);
    return declared == null || Boolean.TRUE.equals(StripeFlag.of(declared));
  }

  private static BigDecimal requestedBy(StripeForm form, BigDecimal capturable) {
    String requested = form.first(StripeFields.AMOUNT_TO_CAPTURE);
    return requested == null ? capturable : new BigDecimal(requested);
  }

  private static ScenarioRequest requestOf(
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      StripePaymentIntent intent) {
    String requested = form.first(StripeFields.AMOUNT_TO_CAPTURE);
    return new ScenarioRequest(
        StripeApi.STRIPE,
        testRunId,
        StripeStoryKey.ofIntent(intent.intentId()),
        account.accountId(),
        null,
        intent.intentId(),
        null,
        null,
        requested == null ? null : Amount.of(requested),
        null,
        StripeOperations.CAPTURE,
        headers);
  }

  private StripeOperationResult capture(
      StripePaymentIntent intent, BigDecimal requested, boolean closing, Instant now) {
    StripeIntentStatus reached =
        closing ? StripeIntentStatus.SUCCEEDED : StripeIntentStatus.REQUIRES_CAPTURE;
    MonetaryOperation operation =
        new MonetaryOperation(
            intent.ledger().nextSequence(),
            OperationKind.CAPTURE,
            StripeAmounts.of(requested),
            closing,
            null,
            reached.wireValue(),
            closing ? NormalizedStatus.SUCCEEDED : NormalizedStatus.PARTIALLY_CAPTURED,
            now);
    LedgerClaim applied = intents.captured(intent, reached, operation, now);
    return StripeLookup.answeredFor(
        applied,
        intent,
        now,
        () ->
            StripeOperationResult.succeeded(
                intent.intentId(),
                StripeIntentView.of(intent.capturedBy(reached, operation, now), now)));
  }

  private static StripeOperationResult decline(
      StripePaymentIntent intent, StripeDecline decline, Instant now) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(intent.intentId()),
        StripeErrors.declined(decline).about(StripeIntentView.of(intent, now)));
  }
}
