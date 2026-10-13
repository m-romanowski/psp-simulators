package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record AzericardPayment(
    TestRunId testRunId,
    String intRef,
    String terminalId,
    String merchantId,
    String order,
    AzericardTransactionType transactionType,
    Amount amount,
    String currency,
    @Nullable String approval,
    String rrn,
    String action,
    String responseCode,
    Ledger ledger,
    @Nullable Instant holdReversedAt,
    @Nullable String backref,
    @Nullable String email,
    @Nullable String token,
    @Nullable String cardLast4,
    @Nullable String extNetRef,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final String NATIVE_STATUS_SEPARATOR = "/";

  String nativeStatus() {
    return action + NATIVE_STATUS_SEPARATOR + responseCode;
  }

  boolean wasApproved() {
    return AzericardOutcomes.ACTION_SUCCEEDED.equals(action);
  }

  NormalizedStatus normalizedStatus() {
    if (!wasApproved()) {
      return NormalizedStatus.FAILED;
    }
    if (holdReversedAt != null) {
      return NormalizedStatus.CANCELLED;
    }
    if (ledger.holds(OperationKind.REFUND)) {
      return NormalizedStatus.REFUNDED;
    }
    if (!ledger.holds(OperationKind.CAPTURE)) {
      return transactionType.movement().whenApproved();
    }
    return authorizationOpen() ? NormalizedStatus.PARTIALLY_CAPTURED : NormalizedStatus.SUCCEEDED;
  }

  boolean authorizationOpen() {
    return wasApproved()
        && holdReversedAt == null
        && ledger.operations().stream()
            .noneMatch(operation -> Boolean.TRUE.equals(operation.closesAuthorization()));
  }

  boolean capturesAccepted() {
    return acceptsACompletion() && capturableRemainder().signum() > 0;
  }

  boolean reversalsAccepted() {
    return reversibleAmount().signum() > 0;
  }

  boolean acceptsACompletion() {
    return holdReversedAt == null && !ledger.holds(OperationKind.REFUND);
  }

  BigDecimal reversibleAmount() {
    BigDecimal captured = reversibleRemainder();
    return captured.signum() > 0 ? captured : capturableRemainder();
  }

  boolean aReversalWouldReleaseTheHold() {
    return reversibleRemainder().signum() == 0;
  }

  BigDecimal capturableRemainder() {
    return ledger.capturableRemainder(authorizationOpen()).orElse(BigDecimal.ZERO);
  }

  BigDecimal reversibleRemainder() {
    return ledger.refundableRemainder().orElse(BigDecimal.ZERO);
  }
}
