package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record StripePaymentIntent(
    TestRunId testRunId,
    String intentId,
    String accountId,
    Amount amount,
    String currency,
    StripeCaptureMethod captureMethod,
    StripeIntentStatus status,
    @Nullable String paymentMethod,
    @Nullable String customer,
    @Nullable String receiptEmail,
    String clientSecret,
    @Nullable String latestCharge,
    @Nullable StripeDecline lastPaymentError,
    @Nullable StripeCancellationReason cancellationReason,
    Instant captureExpiresAt,
    @Nullable Instant settlesAt,
    @Nullable StripeDecline settlesWith,
    Ledger ledger,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  boolean theAuthorizationHasExpired(Instant now) {
    return status == StripeIntentStatus.REQUIRES_CAPTURE && !now.isBefore(captureExpiresAt);
  }

  boolean theScheduledOutcomeHasLanded(Instant now) {
    return settlesAt != null && status == StripeIntentStatus.PROCESSING && !now.isBefore(settlesAt);
  }

  StripeIntentStatus statusAt(Instant now) {
    if (theScheduledOutcomeHasLanded(now)) {
      return settlesWith == null
          ? StripeIntentStatus.SUCCEEDED
          : StripeIntentStatus.REQUIRES_PAYMENT_METHOD;
    }
    return theAuthorizationHasExpired(now) ? StripeIntentStatus.CANCELED : status;
  }

  @Nullable StripeDecline lastPaymentErrorAt(Instant now) {
    return theScheduledOutcomeHasLanded(now) && settlesWith != null
        ? settlesWith
        : lastPaymentError;
  }

  @Nullable StripeCancellationReason cancellationReasonAt(Instant now) {
    return theAuthorizationHasExpired(now) ? StripeCancellationReason.EXPIRED : cancellationReason;
  }

  NormalizedStatus normalizedStatusAt(Instant now) {
    if (authorizationOpenAt(now)) {
      return ledger.holds(OperationKind.CAPTURE)
          ? NormalizedStatus.PARTIALLY_CAPTURED
          : NormalizedStatus.READY_FOR_CAPTURE;
    }
    if (statusAt(now) != StripeIntentStatus.SUCCEEDED) {
      return statusAt(now).normalized();
    }
    BigDecimal settled = settledRefunds();
    if (settled.signum() == 0) {
      return NormalizedStatus.SUCCEEDED;
    }
    return settled.compareTo(capturedTotal()) < 0
        ? NormalizedStatus.PARTIALLY_REFUNDED
        : NormalizedStatus.REFUNDED;
  }

  BigDecimal settledRefunds() {
    return totalOf(OperationKind.REFUND, NormalizedStatus.SUCCEEDED);
  }

  BigDecimal capturedTotal() {
    return totalOf(OperationKind.CAPTURE, null);
  }

  private BigDecimal totalOf(OperationKind kind, @Nullable NormalizedStatus onlyWhen) {
    BigDecimal total = BigDecimal.ZERO;
    for (MonetaryOperation operation : ledger.operations()) {
      if (operation.kind() != kind
          || (onlyWhen != null && operation.normalizedStatus() != onlyWhen)) {
        continue;
      }
      total = total.add(operation.amount().number().orElse(BigDecimal.ZERO));
    }
    return total;
  }

  boolean authorizationOpenAt(Instant now) {
    return statusAt(now) == StripeIntentStatus.REQUIRES_CAPTURE;
  }

  boolean capturesAcceptedAt(Instant now) {
    return authorizationOpenAt(now);
  }

  BigDecimal capturableRemainderAt(Instant now) {
    return ledger.capturableRemainder(authorizationOpenAt(now)).orElse(BigDecimal.ZERO);
  }

  BigDecimal refundableRemainder() {
    return ledger.refundableRemainder().orElse(BigDecimal.ZERO);
  }

  boolean refundsAccepted() {
    return ledger.holds(OperationKind.CAPTURE) && refundableRemainder().signum() > 0;
  }

  int nonFinalCaptures() {
    int taken = 0;
    for (MonetaryOperation operation : ledger.operations()) {
      if (operation.kind() == OperationKind.CAPTURE
          && Boolean.FALSE.equals(operation.closesAuthorization())) {
        taken++;
      }
    }
    return taken;
  }

  StripePaymentIntent confirmedAs(
      StripeIntentStatus reached,
      String usingPaymentMethod,
      MonetaryOperation operation,
      String mintedCharge,
      Instant at) {
    return changed(
        reached,
        usingPaymentMethod,
        null,
        cancellationReason,
        ledger.with(operation),
        mintedCharge,
        at);
  }

  StripePaymentIntent declinedWith(
      StripeDecline decline, String usingPaymentMethod, String mintedCharge, Instant at) {
    return changed(
        StripeIntentStatus.REQUIRES_PAYMENT_METHOD,
        usingPaymentMethod,
        decline,
        cancellationReason,
        ledger,
        mintedCharge,
        at);
  }

  StripePaymentIntent capturedBy(
      StripeIntentStatus reached, MonetaryOperation operation, Instant at) {
    return changed(
        reached,
        paymentMethod,
        lastPaymentError,
        cancellationReason,
        ledger.with(operation),
        latestCharge,
        at);
  }

  StripePaymentIntent refundedBy(MonetaryOperation operation, Instant at) {
    return changed(
        status,
        paymentMethod,
        lastPaymentError,
        cancellationReason,
        ledger.with(operation),
        latestCharge,
        at);
  }

  StripePaymentIntent cancelledFor(@Nullable StripeCancellationReason reason, Instant at) {
    return changed(
        StripeIntentStatus.CANCELED,
        paymentMethod,
        lastPaymentError,
        reason,
        ledger,
        latestCharge,
        at);
  }

  StripePaymentIntent pendingUntil(
      Instant scheduledFor,
      @Nullable StripeDecline comingToRestWith,
      String usingPaymentMethod,
      Instant at) {
    return new StripePaymentIntent(
        testRunId,
        intentId,
        accountId,
        amount,
        currency,
        captureMethod,
        StripeIntentStatus.PROCESSING,
        usingPaymentMethod,
        customer,
        receiptEmail,
        clientSecret,
        latestCharge,
        null,
        cancellationReason,
        captureExpiresAt,
        scheduledFor,
        comingToRestWith,
        ledger,
        origin,
        createdAt,
        at);
  }

  StripePaymentIntent chargedBy(String mintedCharge) {
    return changed(
        status,
        paymentMethod,
        lastPaymentError,
        cancellationReason,
        ledger,
        mintedCharge,
        updatedAt);
  }

  private StripePaymentIntent changed(
      StripeIntentStatus reached,
      @Nullable String usingPaymentMethod,
      @Nullable StripeDecline decline,
      @Nullable StripeCancellationReason reason,
      Ledger reachedLedger,
      @Nullable String reachedCharge,
      Instant at) {
    return new StripePaymentIntent(
        testRunId,
        intentId,
        accountId,
        amount,
        currency,
        captureMethod,
        reached,
        usingPaymentMethod,
        customer,
        receiptEmail,
        clientSecret,
        reachedCharge,
        decline,
        reason,
        captureExpiresAt,
        settlesAt,
        settlesWith,
        reachedLedger,
        origin,
        createdAt,
        at);
  }
}
