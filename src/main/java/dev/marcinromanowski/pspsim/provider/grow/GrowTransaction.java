package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

record GrowTransaction(
    TestRunId testRunId,
    GrowTransactionKind kind,
    String transactionId,
    @Nullable String transactionToken,
    String merchantId,
    @Nullable String processId,
    @Nullable String refundedTransactionId,
    String asmachta,
    String paymentDate,
    String description,
    String payerFullName,
    String payerPhone,
    @Nullable String payerEmail,
    GrowRecordedCard card,
    Amount amount,
    Ledger ledger,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  GrowTransaction with(MonetaryOperation operation, Instant at) {
    return new GrowTransaction(
        testRunId,
        kind,
        transactionId,
        transactionToken,
        merchantId,
        processId,
        refundedTransactionId,
        asmachta,
        paymentDate,
        description,
        payerFullName,
        payerPhone,
        payerEmail,
        card,
        amount,
        ledger.with(operation),
        origin,
        createdAt,
        at);
  }

  boolean holdsAnAuthorization() {
    return ledger.holds(OperationKind.AUTHORIZATION);
  }

  boolean isReleased() {
    return ledger.holds(OperationKind.CAPTURE);
  }

  boolean acceptsAnotherRefund(GrowRefundAllowance allowance) {
    return switch (kind) {
      case REFUND -> false;
      case PAYMENT, TOKEN_PAYMENT -> allowance.admits(ledger.count(OperationKind.REFUND));
    };
  }

  boolean wasChargedOnTheDayOf(Instant now) {
    return ledger.operations().stream()
        .filter(operation -> operation.kind() == OperationKind.CAPTURE)
        .anyMatch(capture -> onTheSameDay(capture.at(), now));
  }

  private static boolean onTheSameDay(Instant one, Instant other) {
    return one.atOffset(ZoneOffset.UTC)
        .toLocalDate()
        .equals(other.atOffset(ZoneOffset.UTC).toLocalDate());
  }

  boolean acceptsARefund(GrowRefundAllowance allowance) {
    return isReleased()
        && acceptsAnotherRefund(allowance)
        && ledger.refundableRemainder().filter(left -> left.signum() > 0).isPresent();
  }

  boolean acceptsASettlement(Instant now, GrowHoldWindow window) {
    return holdsAnAuthorization() && !isReleased() && !isPastEitherDeadline(now, window);
  }

  boolean isPastEitherDeadline(Instant now, GrowHoldWindow window) {
    return isPastTheCaptureWindow(now, window) || isAutomaticallyVoided(now, window);
  }

  private boolean isPastTheCaptureWindow(Instant now, GrowHoldWindow window) {
    return !now.isBefore(createdAt.plus(window.captureWindow()));
  }

  boolean isAutomaticallyVoided(Instant now, GrowHoldWindow window) {
    return !now.isBefore(createdAt.plus(window.automaticVoidAfter()));
  }

  record NativeStatus(String code, String label) {}

  NativeStatus nativeStatus() {
    return switch (kind) {
      case REFUND -> new NativeStatus(GrowNativeStatus.REFUNDED_CODE, GrowNativeStatus.REFUNDED);
      case PAYMENT, TOKEN_PAYMENT ->
          isReleased()
              ? new NativeStatus(GrowNativeStatus.PAID_CODE, GrowNativeStatus.PAID)
              : new NativeStatus(GrowNativeStatus.SUSPENDED_CODE, GrowNativeStatus.SUSPENDED);
    };
  }

  String nativeStatusCode() {
    return nativeStatus().code();
  }

  NormalizedStatus normalizedStatus(Instant now, GrowHoldWindow window) {
    return switch (kind) {
      case REFUND -> NormalizedStatus.SUCCEEDED;
      case PAYMENT, TOKEN_PAYMENT -> paymentStatus(now, window);
    };
  }

  private NormalizedStatus paymentStatus(Instant now, GrowHoldWindow window) {
    return switch (ledger.refundExtent()) {
      case FULL -> NormalizedStatus.REFUNDED;
      case PARTIAL -> NormalizedStatus.PARTIALLY_REFUNDED;
      case NONE -> unrefundedStatus(now, window);
    };
  }

  private NormalizedStatus unrefundedStatus(Instant now, GrowHoldWindow window) {
    if (isReleased()) {
      return NormalizedStatus.SUCCEEDED;
    }
    if (!holdsAnAuthorization()) {
      return NormalizedStatus.SUCCEEDED;
    }
    return isAutomaticallyVoided(now, window)
        ? NormalizedStatus.CANCELLED
        : NormalizedStatus.READY_FOR_CAPTURE;
  }
}
