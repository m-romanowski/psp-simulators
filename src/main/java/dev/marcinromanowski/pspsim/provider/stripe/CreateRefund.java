package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
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

final class CreateRefund {

  static final int ID_ATTEMPTS = 5;

  static final String ID_PREFIX = "re_sim_";

  static final int ID_LENGTH = 16;

  static final String NOTHING_WAS_CAPTURED =
      " has captured nothing, and a refund returns captured funds.";

  static final String ALREADY_REFUNDED_IN_FULL =
      "Everything this PaymentIntent captured has already been refunded.";

  static final String MORE_THAN_IS_LEFT =
      "The refund amount is above what is left to refund on this PaymentIntent, which is its"
          + " captured amount less what has already been returned.";

  private final StripePaymentIntents intents;
  private final StripeRefunds refunds;
  private final StripeCharges charges;
  private final IdGenerator ids;
  private final ScenarioEngine scenarios;

  CreateRefund(
      StripePaymentIntents intents,
      StripeRefunds refunds,
      StripeCharges charges,
      IdGenerator ids,
      ScenarioEngine scenarios) {
    this.intents = intents;
    this.refunds = refunds;
    this.charges = charges;
    this.ids = ids;
    this.scenarios = scenarios;
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
    return form.has(StripeFields.CHARGE)
        ? theChargeItNames(form, headers, testRunId, account, now, askedBy)
        : thePaymentItNames(form, headers, testRunId, account, now, askedBy);
  }

  private StripeOperationResult theChargeItNames(
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    Optional<StripeCharge> addressed =
        StripeChargeLookup.of(charges, testRunId, account, form.required(StripeFields.CHARGE));
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.CHARGE),
          StripeErrors.resourceMissing(StripeFields.CHARGE));
    }
    StripeCharge charge = addressed.get();
    if (!charge.paid()) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(charge.intentId()),
          StripeErrors.chargeRefused(
              StripeChargeLookup.A_CHARGE_THAT_DID_NOT_SUCCEED_HOLDS_NO_FUNDS));
    }
    return refundOf(
        StripeChargeLookup.thePaymentOf(intents, charge),
        form,
        headers,
        testRunId,
        account,
        now,
        askedBy);
  }

  private StripeOperationResult thePaymentItNames(
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    String intentId = form.required(StripeFields.PAYMENT_INTENT);
    Optional<StripePaymentIntent> addressed =
        StripeLookup.of(intents, testRunId, account, intentId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.PAYMENT_INTENT),
          StripeErrors.resourceMissing(StripeFields.PAYMENT_INTENT));
    }
    return refundOf(addressed.get(), form, headers, testRunId, account, now, askedBy);
  }

  private StripeOperationResult refundOf(
      StripePaymentIntent intent,
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    StripeError refused = whatThisPaymentCannotReturn(intent, form, now);
    if (refused != null) {
      return StripeOperationResult.refused(RefusalSubject.theObject(intent.intentId()), refused);
    }
    BigDecimal requested = requestedBy(form, intent.refundableRemainder());
    StripeRefundReason reason = reasonIn(form);
    return StripeOperationResult.decidedBy(
        scenarios.decide(requestOf(form, headers, testRunId, account, intent)),
        () -> refund(intent, requested, reason, account, now, askedBy),
        decline -> decline(intent, decline, now));
  }

  private static @Nullable StripeError whatThisRequestDoesNotCarry(StripeForm form) {
    boolean namesACharge = form.has(StripeFields.CHARGE);
    boolean namesAPayment = form.has(StripeFields.PAYMENT_INTENT);
    if (namesACharge && namesAPayment) {
      return StripeErrors.twoRefundTargets();
    }
    if (!namesACharge && !namesAPayment) {
      return StripeErrors.noRefundTarget();
    }
    String declaredReason = form.first(StripeFields.REASON);
    if (declaredReason != null && StripeRefundReason.sentByACaller(declaredReason).isEmpty()) {
      return StripeErrors.unreadableParameter(
          StripeFields.REASON, StripeRefundReason.theReasonsACallerMaySend());
    }
    String amount = form.first(StripeFields.AMOUNT);
    if (amount == null) {
      return null;
    }
    if (StripeAmounts.isTooLongForAJsonReader(amount)) {
      return StripeErrors.amountTooLarge(StripeFields.AMOUNT);
    }
    return StripeAmounts.isPositiveMinorUnits(amount)
        ? null
        : StripeErrors.invalidChargeAmount(StripeFields.AMOUNT);
  }

  private static @Nullable StripeError whatThisPaymentCannotReturn(
      StripePaymentIntent intent, StripeForm form, Instant now) {
    if (!intent.ledger().holds(OperationKind.CAPTURE)) {
      return StripeErrors.unexpectedState(
              "A PaymentIntent with a status of "
                  + intent.statusAt(now).wireValue()
                  + NOTHING_WAS_CAPTURED)
          .about(StripeIntentView.of(intent, now));
    }
    BigDecimal remaining = intent.refundableRemainder();
    if (remaining.signum() == 0) {
      return StripeErrors.chargeAlreadyRefunded(ALREADY_REFUNDED_IN_FULL)
          .about(StripeIntentView.of(intent, now));
    }
    if (requestedBy(form, remaining).compareTo(remaining) > 0) {
      return StripeErrors.chargeAlreadyRefunded(MORE_THAN_IS_LEFT)
          .about(StripeIntentView.of(intent, now));
    }
    return null;
  }

  private static BigDecimal requestedBy(StripeForm form, BigDecimal remaining) {
    String amount = form.first(StripeFields.AMOUNT);
    return amount == null ? remaining : new BigDecimal(amount);
  }

  private static @Nullable StripeRefundReason reasonIn(StripeForm form) {
    String declared = form.first(StripeFields.REASON);
    return declared == null ? null : StripeRefundReason.sentByACaller(declared).orElseThrow();
  }

  private static ScenarioRequest requestOf(
      StripeForm form,
      Map<String, List<String>> headers,
      TestRunId testRunId,
      StripeAccount account,
      StripePaymentIntent intent) {
    String amount = form.first(StripeFields.AMOUNT);
    return new ScenarioRequest(
        StripeApi.STRIPE,
        testRunId,
        StripeStoryKey.ofIntent(intent.intentId()),
        account.accountId(),
        null,
        intent.intentId(),
        null,
        null,
        amount == null ? null : Amount.of(amount),
        null,
        StripeOperations.REFUND,
        headers);
  }

  private StripeOperationResult refund(
      StripePaymentIntent intent,
      BigDecimal requested,
      @Nullable StripeRefundReason reason,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    StripeRefundStatus status = account.refundOutcome().status();
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String refundId = ID_PREFIX + ids.hex(ID_LENGTH);
      StripeRefund refund = refundOf(intent, refundId, requested, reason, status, now, askedBy);
      MonetaryOperation operation = operationOf(intent, refund);
      switch (refunds.refund(intent, operation, refund, now)) {
        case APPLIED -> {
          return StripeOperationResult.succeeded(refundId, StripeRefundView.of(refund));
        }
        case ID_TAKEN -> {}
        case CLAIM_TAKEN -> {
          return StripeLookup.unexpectedState(intent, now, "changed by two requests at once");
        }
        case OBJECT_GONE -> {
          return StripeOperationResult.refused(
              RefusalSubject.noObjectAnsweredTo(StripeFields.PAYMENT_INTENT),
              StripeErrors.resourceMissing(StripeFields.PAYMENT_INTENT));
        }
      }
    }
    throw new IllegalStateException(
        "no Stripe refund id could be claimed in " + ID_ATTEMPTS + " attempts");
  }

  private static StripeRefund refundOf(
      StripePaymentIntent intent,
      String refundId,
      BigDecimal requested,
      @Nullable StripeRefundReason reason,
      StripeRefundStatus status,
      Instant now,
      ObjectOrigin askedBy) {
    return new StripeRefund(
        intent.testRunId(),
        refundId,
        intent.intentId(),
        intent.latestCharge(),
        intent.accountId(),
        StripeAmounts.of(requested),
        intent.currency(),
        reason,
        status,
        askedBy,
        now,
        now);
  }

  private static @Nullable MonetaryOperation operationOf(
      StripePaymentIntent intent, StripeRefund refund) {
    if (!refund.status().holdsTheMoney()) {
      return null;
    }
    return new MonetaryOperation(
        intent.ledger().nextSequence(),
        OperationKind.REFUND,
        refund.amount(),
        null,
        refund.refundId(),
        refund.status().wireValue(),
        refund.normalizedStatus(),
        refund.createdAt());
  }

  private static StripeOperationResult decline(
      StripePaymentIntent intent, StripeDecline decline, Instant now) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(intent.intentId()),
        StripeErrors.declined(decline).about(StripeIntentView.of(intent, now)));
  }
}
