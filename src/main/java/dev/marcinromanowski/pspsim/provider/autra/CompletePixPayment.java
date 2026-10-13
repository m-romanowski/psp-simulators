package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CompletePixPayment {

  static final String THE_PAYER_PAYS_IN_THEIR_BANK_APP =
      "provider autra collects a Pix from the payer's own bank app and never from a card: the"
          + " checkout hands back a copy-and-paste QR payload and the money arrives out of band, so"
          + " this Pix names no card and a payer step on it names none either. A payer who paid"
          + " moves the Pix to AUTHORIZED, which autra instructs a merchant to treat as paid"
          + " — \"Trate AUTHORIZED como pago\", treat AUTHORIZED as paid — and a payer who left"
          + " moves it to EXPIRED, which autra publishes as the only way to cancel a Pix:"
          + " \"Sem cancelamento — Pix Checkout não tem rota de void. Para 'cancelar', basta"
          + " deixar expirar (expiresAt)\", there is no cancellation, Pix Checkout has no void"
          + " route, and letting it expire is the whole of cancelling one";

  static final String ONLY_A_PIX_HAS_A_PAYER_STEP =
      "provider autra takes every payment of this contract server to server and publishes no payer"
          + " step on one: a pre-authorization is decided inside the answer to"
          + " POST /v1/acquiring/payments/pre-authorization and a sale inside the answer to"
          + " POST /v1/acquiring/payments, so neither waits for anybody. The two objects of this"
          + " contract that do wait for a payer are a Pix, which the payer pays in their own bank"
          + " app, and a hosted card session, on which the payer types a card that is tokenized"
          + " and never charged. This test run holds neither under that id";

  static final String NO_PAYER_IS_DECLINED_ON_A_PIX_QR =
      "provider autra publishes the Pix state machine in full — PENDING to AUTHORIZED to SETTLED,"
          + " or PENDING to EXPIRED, with \"Estados terminais: SETTLED, EXPIRED\" — and it has no"
          + " declined state at all. A payer who does not pay a Pix QR leaves it to expire, so name"
          + " that outcome instead of a decline autra never answers with";

  static final String WHAT_TAKING_A_STEP_ON_A_PIX_DOES =
      "it records the payer of this Pix as having paid the payload from their own bank app or as"
          + " having left it to expire, moves the Pix to the state autra publishes for that, and"
          + " sends the callback autra sends the merchant; a Pix collects no card, so none is named"
          + " here and nothing is charged";

  static final PayerSteps THE_PAYER_STEPS =
      new PayerSteps(
          AutraApi.AUTRA,
          Map.of(
              PayerOutcome.PAID,
              "the payer pasted the payload into their own bank app and paid it",
              PayerOutcome.ABANDONED,
              "the payer never paid the payload and let it expire"),
          Map.of(PayerOutcome.DECLINED, NO_PAYER_IS_DECLINED_ON_A_PIX_QR),
          WHAT_TAKING_A_STEP_ON_A_PIX_DOES);

  private final AutraPixPayments pixPayments;
  private final AutraCallbacks callbacks;
  private final UnitOfWork unitOfWork;

  CompletePixPayment(
      AutraPixPayments pixPayments, AutraCallbacks callbacks, UnitOfWork unitOfWork) {
    this.pixPayments = pixPayments;
    this.callbacks = callbacks;
    this.unitOfWork = unitOfWork;
  }

  HostedCompletionOffer offer(TestRunId testRunId, String providerObjectId, Instant now) {
    Optional<AutraPixPayment> found = pixPayments.findById(testRunId, providerObjectId);
    if (found.isEmpty()) {
      return new HostedCompletionOffer.NotOffered(AutraApi.AUTRA, ONLY_A_PIX_HAS_A_PAYER_STEP);
    }
    AutraPixPayment pix = found.get();
    if (!pix.isAwaitingThePayerAt(now)) {
      return new HostedCompletionOffer.NotOffered(
          AutraApi.AUTRA, pix.whyItIsNoLongerWaitingAt(now));
    }
    return new HostedCompletionOffer.OfferedWithoutACard(
        AutraApi.AUTRA, THE_PAYER_PAYS_IN_THEIR_BANK_APP, THE_PAYER_STEPS);
  }

  HostedCompletionOutcome complete(HostedCompletionRequest request, Instant now) {
    Optional<AutraPixPayment> found =
        pixPayments.findById(request.testRunId(), request.providerObjectId());
    if (found.isEmpty()) {
      return new HostedCompletionOutcome.NoSuchObject();
    }
    if (!THE_PAYER_STEPS.offers(request.outcome())) {
      return new HostedCompletionOutcome.RefusedByTheProvider(
          THE_PAYER_STEPS.whatItHasInsteadOf(request.outcome()));
    }
    AutraPixPayment pix = found.get();
    if (!pix.isAwaitingThePayerAt(now)) {
      return new HostedCompletionOutcome.RefusedByTheProvider(pix.whyItIsNoLongerWaitingAt(now));
    }
    String reached =
        request.outcome() == PayerOutcome.PAID ? AutraStatus.AUTHORIZED : AutraStatus.EXPIRED;
    return unitOfWork.keepingOnly(
        HostedCompletionOutcome.Completed.class::isInstance,
        () -> moveAndQueueTheCallback(request, pix, reached, now));
  }

  private HostedCompletionOutcome moveAndQueueTheCallback(
      HostedCompletionRequest request, AutraPixPayment pix, String reached, Instant now) {
    if (!pixPayments.moveFromPendingTo(pix, reached, now)) {
      return pixPayments
          .findById(request.testRunId(), request.providerObjectId())
          .<HostedCompletionOutcome>map(
              fresh ->
                  new HostedCompletionOutcome.RefusedByTheProvider(
                      fresh.whyItIsNoLongerWaitingAt(now)))
          .orElseGet(HostedCompletionOutcome.NoSuchObject::new);
    }
    AutraPixPayment moved = pix.movedTo(reached, now);
    return new HostedCompletionOutcome.Completed(
        moved.pixId(),
        null,
        request.outcome(),
        theJobIdsOf(callbacks.queue(moved, AutraPixEvent.FINANCIAL_TRANSACTION, null, null, now)));
  }

  private static List<String> theJobIdsOf(List<ScheduledWebhook> scheduled) {
    List<String> jobIds = new ArrayList<>();
    for (ScheduledWebhook job : scheduled) {
      jobIds.add(job.webhookJobId());
    }
    return List.copyOf(jobIds);
  }
}
