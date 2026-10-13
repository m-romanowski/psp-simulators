package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record AutraPixPayment(
    TestRunId testRunId,
    String pixId,
    String accountId,
    String documentId,
    @Nullable String orderId,
    @Nullable String description,
    Amount amount,
    String currency,
    String nativeStatus,
    String qrCode,
    String slug,
    String muid,
    Instant expiresAt,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final String NO_ROUTE_CANCELS_A_PIX =
      "provider autra publishes no void route for Pix Checkout at all — \"Sem cancelamento — Pix"
          + " Checkout nao tem rota de void. Para 'cancelar', basta deixar expirar"
          + " (expiresAt)\" — so nothing this simulator holds against a Pix payment is ever"
          + " reversible and no amount of one is ever refundable";

  String statusAt(Instant now) {
    return isAwaitingThePayer() && hasExpiredBy(now) ? AutraStatus.EXPIRED : nativeStatus;
  }

  NormalizedStatus normalizedStatusAt(Instant now) {
    return switch (statusAt(now)) {
      case AutraStatus.PENDING -> NormalizedStatus.PROCESSING;
      case AutraStatus.AUTHORIZED, AutraStatus.SETTLED -> NormalizedStatus.SUCCEEDED;
      case AutraStatus.EXPIRED -> NormalizedStatus.CANCELLED;
      default -> NormalizedStatus.UNKNOWN;
    };
  }

  boolean isAwaitingThePayer() {
    return AutraStatus.PENDING.equals(nativeStatus);
  }

  boolean isAwaitingThePayerAt(Instant now) {
    return AutraStatus.PENDING.equals(statusAt(now));
  }

  boolean hasExpiredBy(Instant now) {
    return !now.isBefore(expiresAt);
  }

  Ledger ledgerAt(Instant now) {
    String status = statusAt(now);
    if (AutraStatus.PENDING.equals(status) || AutraStatus.EXPIRED.equals(status)) {
      return Ledger.empty();
    }
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                1,
                OperationKind.CAPTURE,
                amount,
                null,
                null,
                status,
                normalizedStatusAt(now),
                updatedAt));
  }

  AutraPixPayment movedTo(String status, Instant at) {
    return new AutraPixPayment(
        testRunId,
        pixId,
        accountId,
        documentId,
        orderId,
        description,
        amount,
        currency,
        status,
        qrCode,
        slug,
        muid,
        expiresAt,
        origin,
        createdAt,
        at);
  }

  boolean settlesAt(Instant now) {
    return AutraStatus.AUTHORIZED.equals(statusAt(now));
  }

  String whyItDoesNotSettleAt(Instant now) {
    String status = statusAt(now);
    if (AutraStatus.SETTLED.equals(status)) {
      return "this Pix has already settled, and provider autra publishes SETTLED as a terminal state"
          + " of the Pix state machine, so nothing settles it a second time. The acquiring.payout"
          + " its settlement queued is resent with POST /__simulator/autra/webhooks naming that"
          + " event";
    }
    if (AutraStatus.PENDING.equals(status)) {
      return "this Pix is still waiting for its payer, and provider autra settles only money a"
          + " payer has sent: finish the payer step as PAID first, which moves it to AUTHORIZED";
    }
    if (AutraStatus.EXPIRED.equals(status)) {
      return "this Pix QR expired unpaid, and provider autra publishes EXPIRED as a terminal state"
          + " of the Pix state machine, so there is no payment for a settlement to pay out";
    }
    return "this Pix is not AUTHORIZED, the one state provider autra settles from, and "
        + AutraStatus.describedAsTheVendorWouldHaveAReaderTreatIt(status);
  }

  String whyItIsNoLongerWaitingAt(Instant now) {
    String status = statusAt(now);
    if (AutraStatus.AUTHORIZED.equals(status) || AutraStatus.SETTLED.equals(status)) {
      return "this Pix was already paid and provider autra publishes its state machine as PENDING"
          + " to AUTHORIZED to SETTLED, so a payer cannot pay it a second time";
    }
    if (AutraStatus.EXPIRED.equals(status)) {
      return "this Pix QR expired unpaid, and provider autra publishes EXPIRED as a terminal state"
          + " of the Pix state machine, so nothing moves it anywhere else";
    }
    return "this Pix is not waiting for a payer, and "
        + AutraStatus.describedAsTheVendorWouldHaveAReaderTreatIt(status);
  }
}
