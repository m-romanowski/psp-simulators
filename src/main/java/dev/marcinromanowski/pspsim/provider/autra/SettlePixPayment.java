package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.FollowUp;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpNotOfferedException;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.time.Instant;
import java.util.List;

final class SettlePixPayment {

  static final String SETTLE = "settle";

  static final FollowUp THE_SETTLEMENT =
      new FollowUp(
          SETTLE,
          "settle (payout)",
          "moves this Pix from AUTHORIZED to SETTLED, the terminal state provider autra publishes"
              + " for money it has paid out, and queues the acquiring.payout event that reports it");

  static final String THE_ONLY_FOLLOW_UP_IS_THE_SETTLEMENT =
      "provider autra offers one follow-up on a Pix of this contract, settle, which moves an"
          + " AUTHORIZED Pix to SETTLED and queues acquiring.payout; its contract publishes no other"
          + " step it takes on a Pix after the payer has paid";

  static final String THE_RUN_HOLDS_NO_SUCH_PIX =
      "this test run holds no provider autra Pix under that id";

  private final AutraPixPayments pixPayments;
  private final AutraCallbacks callbacks;
  private final UnitOfWork unitOfWork;

  SettlePixPayment(AutraPixPayments pixPayments, AutraCallbacks callbacks, UnitOfWork unitOfWork) {
    this.pixPayments = pixPayments;
    this.callbacks = callbacks;
    this.unitOfWork = unitOfWork;
  }

  List<FollowUp> offeredOn(TestRunId testRunId, String providerObjectId, Instant now) {
    return pixPayments
        .findById(testRunId, providerObjectId)
        .filter(pix -> pix.settlesAt(now))
        .map(pix -> List.of(THE_SETTLEMENT))
        .orElse(List.of());
  }

  List<ScheduledWebhook> perform(FollowUpRequest request, Instant now) {
    if (!SETTLE.equals(request.name())) {
      throw new FollowUpNotOfferedException(THE_ONLY_FOLLOW_UP_IS_THE_SETTLEMENT);
    }
    AutraPixPayment pix =
        pixPayments
            .findById(request.testRunId(), request.providerObjectId())
            .orElseThrow(() -> new UnknownProviderObjectException(THE_RUN_HOLDS_NO_SUCH_PIX));
    if (!pix.settlesAt(now)) {
      throw new FollowUpNotOfferedException(pix.whyItDoesNotSettleAt(now));
    }
    return unitOfWork.keepingOnly(queued -> true, () -> settleAndQueueThePayout(pix, now));
  }

  private List<ScheduledWebhook> settleAndQueueThePayout(AutraPixPayment pix, Instant now) {
    if (!pixPayments.moveFromAuthorizedToSettled(pix, now)) {
      throw new FollowUpNotOfferedException(
          pixPayments
              .findById(pix.testRunId(), pix.pixId())
              .orElseThrow(() -> new UnknownProviderObjectException(THE_RUN_HOLDS_NO_SUCH_PIX))
              .whyItDoesNotSettleAt(now));
    }
    return callbacks.queue(
        pix.movedTo(AutraStatus.SETTLED, now), AutraPixEvent.PAYOUT, null, null, now);
  }
}
