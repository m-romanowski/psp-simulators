package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record AutraPayment(
    TestRunId testRunId,
    String paymentId,
    String accountId,
    String documentId,
    @Nullable String orderId,
    String type,
    String transactionType,
    Amount amount,
    String currency,
    int installments,
    String nativeStatus,
    String authorizationCode,
    String rrn,
    @Nullable String trackingNumber,
    @Nullable String slugToken,
    @Nullable String slugStoredCard,
    Ledger ledger,
    @Nullable Instant holdReleasedAt,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final String NOTHING_HAS_BEEN_RETURNED = "0";

  static final String A_SALE_HAS_NOTHING_RESERVED_LEFT_TO_TAKE =
      "this payment is a direct sale and was charged when provider autra accepted it, so there is"
          + " no reservation left for a capture to take: the vendor publishes capture on a"
          + " pre-authorization alone and types a payment as SALE or PREAUTH, where \"PREAUTH"
          + " permanece mesmo apos captura\"";

  NormalizedStatus normalizedStatus() {
    if (isAHoldNothingHasClosed()) {
      return NormalizedStatus.READY_FOR_CAPTURE;
    }
    return switch (nativeStatus) {
      case AutraStatus.ACCEPTED -> NormalizedStatus.SUCCEEDED;
      case AutraStatus.DECLINED -> NormalizedStatus.FAILED;
      case AutraStatus.CAPTURED -> NormalizedStatus.SUCCEEDED;
      case AutraStatus.VOIDED ->
          ledger.holds(OperationKind.CAPTURE)
              ? NormalizedStatus.REFUNDED
              : NormalizedStatus.CANCELLED;
      case AutraStatus.PARTIALLY_VOIDED -> NormalizedStatus.PARTIALLY_REFUNDED;
      default -> NormalizedStatus.UNKNOWN;
    };
  }

  boolean isAHoldNothingHasClosed() {
    return AutraTimeline.PREAUTH.equals(type)
        && ledger.holds(OperationKind.AUTHORIZATION)
        && !ledger.holds(OperationKind.CAPTURE)
        && holdReleasedAt == null;
  }

  boolean authorizationOpen() {
    return isAHoldNothingHasClosed();
  }

  boolean capturesAccepted() {
    return isAHoldNothingHasClosed();
  }

  boolean isAReservationOrSaleNothingHasClosed() {
    return isAHoldNothingHasClosed() || isASaleTheAcquirerAccepted();
  }

  boolean reversalsAccepted() {
    return isAHoldNothingHasClosed() || reversibleRemainder().signum() > 0;
  }

  String refundedAmount() {
    return ledger
        .balances(authorizationOpen())
        .map(balances -> balances.refundedAmount().value())
        .orElse(NOTHING_HAS_BEEN_RETURNED);
  }

  String transactionMethod() {
    return slugToken == null && slugStoredCard == null
        ? AutraFields.CARD_METHOD
        : AutraFields.TOKEN_METHOD;
  }

  BigDecimal capturableRemainder() {
    return ledger.capturableRemainder(authorizationOpen()).orElse(BigDecimal.ZERO);
  }

  BigDecimal reversibleRemainder() {
    return ledger.refundableRemainder().orElse(BigDecimal.ZERO);
  }

  boolean isASaleTheAcquirerAccepted() {
    return AutraTimeline.SALE.equals(type) && AutraStatus.ACCEPTED.equals(nativeStatus);
  }

  boolean isAtAStatusTheSaleRouteRefusesAnotherOrderUnder() {
    return AutraStatus.ACCEPTED.equals(nativeStatus) || AutraStatus.CAPTURED.equals(nativeStatus);
  }

  String whyItIsNotEligible() {
    if (holdReleasedAt != null || AutraStatus.VOIDED.equals(nativeStatus)) {
      return "this pre-authorization has already been cancelled";
    }
    if (AutraStatus.CAPTURED.equals(nativeStatus)
        || AutraStatus.PARTIALLY_VOIDED.equals(nativeStatus)) {
      return "this pre-authorization has already been captured";
    }
    if (AutraStatus.DECLINED.equals(nativeStatus)) {
      return "this pre-authorization failed and reserved nothing";
    }
    if (isASaleTheAcquirerAccepted()) {
      return A_SALE_HAS_NOTHING_RESERVED_LEFT_TO_TAKE;
    }
    return "this payment is not a pre-authorization that can be captured, and "
        + AutraStatus.describedAsTheVendorWouldHaveAReaderTreatIt(nativeStatus);
  }
}
