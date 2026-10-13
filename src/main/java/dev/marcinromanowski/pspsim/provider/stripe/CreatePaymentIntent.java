package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class CreatePaymentIntent {

  static final int ID_ATTEMPTS = 5;

  static final String ID_PREFIX = "pi_sim_";

  static final int ID_LENGTH = 16;

  private final StripePaymentIntents intents;
  private final StripePaymentMethods paymentMethods;
  private final StripeCharges charges;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;
  private final StripeScheduledOutcomes events;

  CreatePaymentIntent(
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
      Instant now,
      ObjectOrigin askedBy) {
    StripeError unreadable = whatThisRequestDoesNotCarry(form);
    if (unreadable != null) {
      return StripeOperationResult.refusedBeforeExecuting(
          StripeRefusals.theRequestWasRefused(), unreadable);
    }
    if (confirms(form)) {
      StripeError detached =
          StripePaymentMethodLookup.theRefusalADetachedMethodEarns(
              paymentMethods, testRunId, account, form.first(StripeFields.PAYMENT_METHOD));
      if (detached != null) {
        return StripeOperationResult.refusedBeforeExecuting(
            RefusalSubject.anObjectItWillNotName(
                StripeRefusals.THE_METHOD_IS_DETACHED_AND_NO_INTENT_EXISTS_YET),
            detached);
      }
    }
    String intentId = anIntentIdClaimedForThisDecision(testRunId);
    return StripeOperationResult.decidedBy(
        scenarios.decide(requestOf(form, headers, testRunId, account, intentId)),
        () -> create(form, testRunId, account, now, intentId, askedBy),
        decline -> createDeclined(form, testRunId, account, now, intentId, decline, askedBy),
        pending -> createPending(form, testRunId, account, now, intentId, pending, askedBy));
  }

  private static @Nullable StripeError whatThisRequestDoesNotCarry(StripeForm form) {
    if (!form.has(StripeFields.AMOUNT)) {
      return StripeErrors.parameterMissing(StripeFields.AMOUNT);
    }
    String amount = form.required(StripeFields.AMOUNT);
    if (StripeAmounts.isTooLongForAJsonReader(amount)) {
      return StripeErrors.amountTooLarge(StripeFields.AMOUNT);
    }
    if (!StripeAmounts.isPositiveMinorUnits(amount)) {
      return StripeErrors.invalidChargeAmount(StripeFields.AMOUNT);
    }
    if (!form.has(StripeFields.CURRENCY)) {
      return StripeErrors.parameterMissing(StripeFields.CURRENCY);
    }
    if (captureMethodOf(form) == null) {
      return StripeErrors.unreadableParameter(
          StripeFields.CAPTURE_METHOD, StripeCaptureMethod.theMethodsTheVendorPublishes());
    }
    Boolean confirming = StripeFlag.of(form.first(StripeFields.CONFIRM));
    if (confirming == null) {
      return StripeErrors.unreadableParameter(
          StripeFields.CONFIRM, StripeFlag.THE_VALUES_IT_ACCEPTS);
    }
    if (confirming && !form.has(StripeFields.PAYMENT_METHOD)) {
      return StripeErrors.parameterMissing(StripeFields.PAYMENT_METHOD);
    }
    return null;
  }

  private static @Nullable StripeCaptureMethod captureMethodOf(StripeForm form) {
    String declared = form.first(StripeFields.CAPTURE_METHOD);
    if (declared == null) {
      return StripeCaptureMethod.PUBLISHED_DEFAULT;
    }
    return StripeCaptureMethod.of(declared).orElse(null);
  }

  private static boolean confirms(StripeForm form) {
    return Boolean.TRUE.equals(StripeFlag.of(form.first(StripeFields.CONFIRM)));
  }

  private String anIntentIdClaimedForThisDecision(TestRunId testRunId) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String intentId = ID_PREFIX + ids.hex(ID_LENGTH);
      if (intents.claimIntentId(testRunId, intentId)) {
        return intentId;
      }
    }
    throw new IllegalStateException(
        "no Stripe PaymentIntent id could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private static ScenarioRequest requestOf(
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      String intentId) {
    return new ScenarioRequest(
        StripeApi.STRIPE,
        testRunId,
        StripeStoryKey.ofIntent(intentId),
        account.accountId(),
        null,
        null,
        form.first(StripeFields.CUSTOMER),
        form.first(StripeFields.PAYMENT_METHOD),
        Amount.of(form.required(StripeFields.AMOUNT)),
        form.required(StripeFields.CURRENCY),
        StripeOperations.CREATE,
        headers);
  }

  private StripeOperationResult create(
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String intentId,
      ObjectOrigin askedBy) {
    StripeCaptureMethod captureMethod = requireCaptureMethod(form);
    StripeIntentStatus status = statusOf(form, captureMethod);
    Ledger ledger =
        confirms(form)
            ? Ledger.empty()
                .with(
                    StripeConfirmation.operationOf(
                        1, captureMethod, Amount.of(form.required(StripeFields.AMOUNT)), now))
            : Ledger.empty();
    StripePaymentIntent intent =
        stored(
            form,
            testRunId,
            account,
            now,
            intentId,
            status,
            null,
            null,
            null,
            ledger,
            askedBy,
            theChargeStatusAConfirmingCreationMints(form, StripeChargeStatus.SUCCEEDED));
    return StripeOperationResult.succeeded(intentId, StripeIntentView.of(intent, now));
  }

  private StripeOperationResult createPending(
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String intentId,
      ScenarioDecision.Pend pending,
      ObjectOrigin askedBy) {
    if (!confirms(form)) {
      return create(form, testRunId, account, now, intentId, askedBy);
    }
    StripeDecline comingToRestWith = StripePending.theOutcomeItComesToRestWith(pending);
    Instant landing = now.plus(pending.settlesAfter());
    StripePaymentIntent intent =
        stored(
            form,
            testRunId,
            account,
            now,
            intentId,
            StripeIntentStatus.PROCESSING,
            null,
            landing,
            comingToRestWith,
            Ledger.empty(),
            askedBy,
            null);
    events.theOutcomeScheduledFor(intent, landing, pending.settlesAfter());
    return StripeOperationResult.succeeded(intentId, StripeIntentView.of(intent, now));
  }

  private StripeOperationResult createDeclined(
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String intentId,
      StripeDecline decline,
      ObjectOrigin askedBy) {
    StripePaymentIntent intent =
        stored(
            form,
            testRunId,
            account,
            now,
            intentId,
            StripeIntentStatus.REQUIRES_PAYMENT_METHOD,
            decline,
            null,
            null,
            Ledger.empty(),
            askedBy,
            theChargeStatusAConfirmingCreationMints(form, StripeChargeStatus.FAILED));
    return StripeOperationResult.refusedAfterChangingState(
        RefusalSubject.theObject(intent.intentId()),
        StripeErrors.declined(decline).about(StripeIntentView.of(intent, now)));
  }

  private StripePaymentIntent stored(
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String intentId,
      StripeIntentStatus status,
      @Nullable StripeDecline decline,
      @Nullable Instant settlesAt,
      @Nullable StripeDecline settlesWith,
      Ledger ledger,
      ObjectOrigin askedBy,
      @Nullable StripeChargeStatus chargeStatus) {
    StripePaymentIntent unconfirmed =
        new StripePaymentIntent(
            testRunId,
            intentId,
            account.accountId(),
            Amount.of(form.required(StripeFields.AMOUNT)),
            form.required(StripeFields.CURRENCY),
            requireCaptureMethod(form),
            status,
            form.first(StripeFields.PAYMENT_METHOD),
            form.first(StripeFields.CUSTOMER),
            form.first(StripeFields.RECEIPT_EMAIL),
            StripeClientSecret.of(intentId, ids),
            null,
            decline,
            null,
            account.captureWindow().deadlineFrom(now),
            settlesAt,
            settlesWith,
            ledger,
            askedBy,
            now,
            now);
    StripeCharge charge =
        chargeStatus == null
            ? null
            : StripeChargeMinting.of(
                charges, ids, unconfirmed, unconfirmed.paymentMethod(), chargeStatus, now);
    StripePaymentIntent intent =
        charge == null ? unconfirmed : unconfirmed.chargedBy(charge.chargeId());
    if (!intents.insertIfAbsent(intent, charge)) {
      throw new IllegalStateException(
          "the Stripe PaymentIntent id this decision was taken under was claimed by another"
              + " request before the intent could be stored");
    }
    return intent;
  }

  private static @Nullable StripeChargeStatus theChargeStatusAConfirmingCreationMints(
      StripeForm form, StripeChargeStatus reached) {
    return confirms(form) ? reached : null;
  }

  private static StripeCaptureMethod requireCaptureMethod(StripeForm form) {
    StripeCaptureMethod captureMethod = captureMethodOf(form);
    if (captureMethod == null) {
      throw new IllegalStateException("read capture_method before checking it was readable");
    }
    return captureMethod;
  }

  private static StripeIntentStatus statusOf(StripeForm form, StripeCaptureMethod captureMethod) {
    if (confirms(form)) {
      return captureMethod.afterASuccessfulConfirmation();
    }
    return form.has(StripeFields.PAYMENT_METHOD)
        ? StripeIntentStatus.REQUIRES_CONFIRMATION
        : StripeIntentStatus.REQUIRES_PAYMENT_METHOD;
  }
}
