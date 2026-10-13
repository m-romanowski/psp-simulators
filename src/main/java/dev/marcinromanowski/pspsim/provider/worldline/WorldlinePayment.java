package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

record WorldlinePayment(
    TestRunId testRunId,
    String paymentId,
    List<String> maintenanceIds,
    String accountId,
    String merchantId,
    @Nullable String merchantReference,
    @Nullable String merchantCustomerId,
    @Nullable String token,
    @Nullable String hostedTokenizationId,
    @Nullable String cardNumber,
    @Nullable String expiryDate,
    Amount amount,
    String currencyCode,
    WorldlineAuthorizationMode authorizationMode,
    String nativeStatus,
    String statusCategory,
    int statusCode,
    boolean authorizationClosed,
    Ledger ledger,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final String EVERY_MAINTENANCE_OPERATION_MINTS_AN_IDENTIFIER =
      "provider worldline mints a new payment.id for every maintenance operation — \"Once you"
          + " perform such a maintenance operation, the payment.id of this transaction changes,"
          + " as you change the transaction status\" — so one payment of this simulator answers"
          + " to the identifier it was created under and to every identifier a capture or a"
          + " cancel has minted since. The identifier it was created under is the canonical one,"
          + " because that is the one the vendor's own GetPayment accepts: it \"accepts only the"
          + " original payment.id from the initial CreatePayment/CreateHostedCheckout request\"";

  WorldlinePayment {
    maintenanceIds = List.copyOf(maintenanceIds);
  }

  ProviderObjectIdentity identity() {
    ProviderObjectIdentity identity = ProviderObjectIdentity.of(paymentId);
    for (String minted : maintenanceIds) {
      identity = identity.answeringAlsoTo(minted);
    }
    return identity;
  }

  String theNextIdentifierAMaintenanceOperationMints() {
    return WorldlineIdentifiers.paymentIdOf(
        WorldlineIdentifiers.thePayIdIn(paymentId),
        WorldlineIdentifiers.THE_FIRST_PAYIDSUB + maintenanceIds.size() + 1);
  }

  String theIdentifierItIsAnsweringUnder() {
    return maintenanceIds.isEmpty() ? paymentId : maintenanceIds.get(maintenanceIds.size() - 1);
  }

  NormalizedStatus normalizedStatus() {
    if (WorldlineStatus.PENDING_CAPTURE.equals(nativeStatus)
        && ledger.holds(OperationKind.CAPTURE)
        && capturableRemainder().signum() > 0) {
      return NormalizedStatus.PARTIALLY_CAPTURED;
    }
    return WorldlineStatus.normalizedPaymentStatus(nativeStatus);
  }

  boolean isAnAuthorizationWaitingToBeCaptured() {
    return WorldlineStatus.PENDING_CAPTURE.equals(nativeStatus) && !authorizationClosed;
  }

  boolean authorizationOpen() {
    return !authorizationClosed && capturableRemainder().signum() > 0;
  }

  boolean capturesAccepted() {
    return authorizationOpen();
  }

  boolean refundsAccepted() {
    return false;
  }

  BigDecimal capturableRemainder() {
    return ledger.capturableRemainder(!authorizationClosed).orElse(BigDecimal.ZERO);
  }

  String whyNoMaintenanceOperationIsAllowed() {
    if (authorizationClosed) {
      return "this payment is closed: a capture with isFinal true, a cancellation with isFinal"
          + " true or a capture that took the full authorized amount has already closed it, and"
          + " provider worldline publishes that \"The transaction will be closed for any further"
          + " capture requests.\"";
    }
    return "this payment holds "
        + nativeStatus
        + " at statusOutput.statusCode "
        + statusCode
        + ", and provider worldline publishes the capture route as one a payment reaches from"
        + " PENDING_CAPTURE alone: \"When you want to capture the funds on a payment with a"
        + " PENDING_CAPTURE state you can call this API.\"";
  }
}
