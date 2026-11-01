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
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class CreatePaymentIntent {

  static final int ID_ATTEMPTS = 5;

  static final String ID_PREFIX = "pi_sim_";

  static final int ID_LENGTH = 16;

  private final StripePaymentIntents intents;
  private final StripePaymentMethodMinting minting;
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
    this.minting = new StripePaymentMethodMinting(paymentMethods, ids);
    this.charges = charges;
    this.ids = ids;
    this.scenarios = scenarios;
    this.events = events;
  }

  StripeOperationResult handle(
      StripeCoreRequest request,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    StripeForm form = request.form();
    StripeError unreadable = whatThisRequestDoesNotCarry(form);
    if (unreadable != null) {
      return StripeOperationResult.refusedBeforeExecuting(
          StripeRefusals.theRequestWasRefused(), unreadable);
    }
    StripeSelection selection;
    switch (StripeSelections.onCreatingAPayment(request, form.required(StripeFields.CURRENCY))) {
      case StripeSelections.Outcome.Refused refused -> {
        return StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theRequestWasRefused(), refused.error());
      }
      case StripeSelections.Outcome.Selected selected -> selection = selected.selection();
    }
    StripeNamedPaymentMethod named;
    switch (StripeNamedPaymentMethod.of(form, null, minting, testRunId, account, askedBy, now)) {
      case StripeNamedPaymentMethod.Outcome.Refused refused -> {
        return StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theRequestWasRefused(), refused.error());
      }
      case StripeNamedPaymentMethod.Outcome.Named theOne -> named = theOne.method();
    }
    StripeError notAnId = named.theRefusalOfAnIdThatReadsAsACardNumber();
    if (notAnId == null) {
      notAnId = StripeNamedPaymentMethod.theRefusalOfACustomerThatReadsAsACardNumber(form);
    }
    if (notAnId != null) {
      return StripeOperationResult.refusedBeforeExecuting(
          StripeRefusals.theRequestWasRefused(), notAnId);
    }
    if (confirms(form)) {
      StripeError nowhereToReturn =
          StripeReturnUrl.theRefusalAPaymentConfirmationEarns(form, selection);
      if (nowhereToReturn != null) {
        return StripeOperationResult.refusedBeforeExecuting(
            StripeRefusals.theRequestWasRefused(), nowhereToReturn);
      }
      StripeError heldByNoOne = named.theRefusalOfAMethodNoOneHolds();
      if (heldByNoOne != null) {
        return StripeOperationResult.refusedBeforeExecuting(
            RefusalSubject.anObjectItWillNotName(
                StripeRefusals.THE_METHOD_IS_NOT_THIS_ACCOUNTS_AND_NO_INTENT_EXISTS_YET),
            heldByNoOne);
      }
      StripeError ofItsHolder =
          named.theRefusalItsHolderEarnsOnAPayment(form.first(StripeFields.CUSTOMER));
      if (ofItsHolder != null) {
        return StripeOperationResult.refusedBeforeExecuting(
            RefusalSubject.anObjectItWillNotName(
                named.detached()
                    ? StripeRefusals.THE_METHOD_IS_DETACHED_AND_NO_INTENT_EXISTS_YET
                    : StripeRefusals.THE_METHOD_IS_NOT_THIS_ACCOUNTS_AND_NO_INTENT_EXISTS_YET),
            ofItsHolder);
      }
      Optional<StripePaymentMethodType> notOffered =
          named.type().filter(type -> !selection.offers(type));
      if (notOffered.isPresent()) {
        return StripeOperationResult.refusedBeforeExecuting(
            RefusalSubject.anObjectItWillNotName(
                StripeRefusals.THE_METHOD_IS_OF_A_TYPE_NOT_OFFERED_AND_NO_INTENT_EXISTS_YET),
            StripeErrors.paymentMethodNotAllowedForThePayment(
                notOffered.get().wireValue(), selection.offeredAsProse()));
      }
    }
    String intentId = anIntentIdClaimedForThisDecision(testRunId);
    return StripeCardOutcomes.decidedBy(
        scenarios.decide(requestOf(form, headers, testRunId, account, intentId, named)),
        confirms(form) ? named.method() : null,
        StripeOperations.CREATE,
        () -> create(form, selection, testRunId, account, now, intentId, askedBy, named),
        decline ->
            createDeclined(
                form, selection, testRunId, account, now, intentId, decline, askedBy, named),
        pending ->
            createPending(
                form, selection, testRunId, account, now, intentId, pending, askedBy, named));
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
    if (confirming
        && !form.has(StripeFields.PAYMENT_METHOD)
        && StripeNewPaymentMethod.thePaymentMethodDataIn(form) == null) {
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
      String intentId,
      StripeNamedPaymentMethod named) {
    return new ScenarioRequest(
        StripeApi.STRIPE,
        testRunId,
        StripeStoryKey.ofIntent(intentId),
        account.accountId(),
        null,
        null,
        form.first(StripeFields.CUSTOMER),
        named.theIdARuleNames(),
        Amount.of(form.required(StripeFields.AMOUNT)),
        form.required(StripeFields.CURRENCY),
        StripeOperations.CREATE,
        headers);
  }

  private StripeOperationResult create(
      StripeForm form,
      StripeSelection selection,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String intentId,
      ObjectOrigin askedBy,
      StripeNamedPaymentMethod named) {
    StripeCaptureMethod captureMethod = requireCaptureMethod(form);
    StripeIntentStatus status = statusOf(form, captureMethod, named);
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
            selection,
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
            theChargeStatusAConfirmingCreationMints(form, StripeChargeStatus.SUCCEEDED),
            named);
    return StripeOperationResult.succeeded(intentId, StripeIntentView.of(intent, now));
  }

  private StripeOperationResult createPending(
      StripeForm form,
      StripeSelection selection,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String intentId,
      ScenarioDecision.Pend pending,
      ObjectOrigin askedBy,
      StripeNamedPaymentMethod named) {
    if (!confirms(form)) {
      return create(form, selection, testRunId, account, now, intentId, askedBy, named);
    }
    StripeDecline comingToRestWith = StripePending.theOutcomeItComesToRestWith(pending);
    Instant landing = now.plus(pending.settlesAfter());
    StripePaymentIntent intent =
        stored(
            form,
            selection,
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
            null,
            named);
    events.theOutcomeScheduledFor(intent, landing, pending.settlesAfter());
    return StripeOperationResult.succeeded(intentId, StripeIntentView.of(intent, now));
  }

  private StripeOperationResult createDeclined(
      StripeForm form,
      StripeSelection selection,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      String intentId,
      StripeDecline decline,
      ObjectOrigin askedBy,
      StripeNamedPaymentMethod named) {
    StripePaymentIntent intent =
        stored(
            form,
            selection,
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
            theChargeStatusAConfirmingCreationMints(form, StripeChargeStatus.FAILED),
            named);
    return StripeOperationResult.refusedAfterChangingState(
        RefusalSubject.theObject(intent.intentId()),
        StripeErrors.declined(decline).about(StripeIntentView.of(intent, now)));
  }

  private StripePaymentIntent stored(
      StripeForm form,
      StripeSelection selection,
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
      @Nullable StripeChargeStatus chargeStatus,
      StripeNamedPaymentMethod named) {
    StripePaymentIntent unconfirmed =
        new StripePaymentIntent(
            testRunId,
            intentId,
            account.accountId(),
            Amount.of(form.required(StripeFields.AMOUNT)),
            form.required(StripeFields.CURRENCY),
            requireCaptureMethod(form),
            status,
            named.id(),
            form.first(StripeFields.CUSTOMER),
            form.first(StripeFields.RECEIPT_EMAIL),
            selection,
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
    if (charge != null && decline != null) {
      charge = charge.declinedWith(decline);
    }
    StripePaymentIntent intent =
        charge == null ? unconfirmed : unconfirmed.chargedBy(charge.chargeId());
    named.storedBy(minting);
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

  private static StripeIntentStatus statusOf(
      StripeForm form, StripeCaptureMethod captureMethod, StripeNamedPaymentMethod named) {
    if (confirms(form)) {
      return captureMethod.afterASuccessfulConfirmation();
    }
    return named.id() != null
        ? StripeIntentStatus.REQUIRES_CONFIRMATION
        : StripeIntentStatus.REQUIRES_PAYMENT_METHOD;
  }
}
