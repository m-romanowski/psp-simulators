package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveryRequest;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class AutraCallbacks {

  static final String ONLY_A_PIX_HAS_A_CALLBACK =
      "this test run holds no provider autra Pix under that id. A payment taken on the card surface"
          + " of this contract reports through acquiring.financial_transaction as well, but"
          + " provider autra publishes no example of that event for a pre-authorization, a"
          + " capture, a void or a sale: the one card payload it prints carries a slugMerchant, a"
          + " payerName, a payerDocument and a payerEmail that no request of this contract"
          + " supplies, so rendering it would mean inventing four correlation fields. A hosted"
          + " card session sends nothing at all, because autra publishes no webhook domain for"
          + " tokenization: \"Domínios suportados\" lists nine and none of them covers it";

  static final String A_PIX_NOTHING_HAS_PAID_SENDS_NOTHING =
      "this Pix is still waiting for a payer, and provider autra publishes that intermediate states"
          + " send no webhook at all: \"Eventos intermediarios (PROCESSING, ACCEPTED,"
          + " FILES_PENDING etc.) nao disparam webhook\". Finish the payer step on this Pix and the"
          + " event it reaches is the one this surface sends";

  static final String A_PAYOUT_FOLLOWS_A_PAYMENT =
      "provider autra publishes acquiring.payout as the settlement of money a payer already sent,"
          + " and this Pix expired unpaid, so there is nothing for a payout of it to settle";

  static final String A_PAYOUT_FOLLOWS_A_SETTLEMENT =
      "this Pix is AUTHORIZED and has not settled, and provider autra publishes acquiring.payout as"
          + " the report of a settlement, so sending one now would tell the merchant about money"
          + " this simulator still reads as unsettled. Settle it first, with POST"
          + " /__simulator/autra/objects/{pixId}/follow-ups/settle or the settle (payout) button on"
          + " the payment screen: that moves the Pix to SETTLED and queues this event. Once it has"
          + " settled, this surface resends the payout";

  static final String THE_RUN_CONFIGURES_NO_TARGET_FOR_THIS_MERCHANT =
      "this test run configures no callback target for the provider autra account this Pix belongs"
          + " to, and provider autra delivers acquiring events only to merchants registered on the"
          + " platform: \"So sao entregues para merchants cadastrados/associados na plataforma"
          + " Autra.\" Configure a callback target for the account on the Configuration screen";

  private final AutraPixPayments pixPayments;
  private final AutraMerchants merchants;
  private final ProviderAccounts accounts;
  private final WebhookDispatcher dispatcher;
  private final IdGenerator ids;

  AutraCallbacks(
      AutraPixPayments pixPayments,
      AutraMerchants merchants,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      IdGenerator ids) {
    this.pixPayments = pixPayments;
    this.merchants = merchants;
    this.accounts = accounts;
    this.dispatcher = dispatcher;
    this.ids = ids;
  }

  List<ScheduledWebhook> schedule(WebhookScheduleRequest request, Instant now) {
    TestRunId testRunId = request.testRunId();
    AutraPixPayment pix =
        pixPayments
            .findById(testRunId, request.providerObjectId())
            .orElseThrow(() -> new UnknownProviderObjectException(ONLY_A_PIX_HAS_A_CALLBACK));
    if (pix.isAwaitingThePayerAt(now)) {
      throw new WebhookNotSchedulableException(A_PIX_NOTHING_HAS_PAID_SENDS_NOTHING);
    }
    AutraPixEvent event = theEventAsked(request.eventType());
    if (event == AutraPixEvent.PAYOUT) {
      refuseAPayoutNoSettlementPrecedes(pix.statusAt(now));
    }
    return queue(pix, event, request.reuseEventId(), request.delay(), now);
  }

  List<ScheduledWebhook> queue(
      AutraPixPayment pix,
      AutraPixEvent event,
      @Nullable String reuseEventId,
      @Nullable Duration delay,
      Instant now) {
    List<WebhookTarget> targets = theTargetsOf(pix);
    if (targets.isEmpty()) {
      return List.of();
    }
    String eventId = reuseEventId == null ? AutraIdentifiers.uuidOf(ids) : reuseEventId;
    WebhookPayload payload = AutraPixWebhook.of(event, pix, eventId, theSecretOf(pix), now);
    List<ScheduledWebhook> jobs = new ArrayList<>();
    for (WebhookTarget target : targets) {
      for (WebhookDelivery delivery :
          dispatcher.schedule(
              new WebhookDeliveryRequest(
                  AutraApi.AUTRA,
                  pix.testRunId(),
                  pix.accountId(),
                  event.eventType(),
                  pix.pixId(),
                  target.url(),
                  payload,
                  eventId,
                  null,
                  delay))) {
        jobs.add(new ScheduledWebhook(delivery.deliveryId(), delivery.eventId()));
      }
    }
    return List.copyOf(jobs);
  }

  private static void refuseAPayoutNoSettlementPrecedes(String status) {
    if (AutraStatus.AUTHORIZED.equals(status)) {
      throw new WebhookNotSchedulableException(A_PAYOUT_FOLLOWS_A_SETTLEMENT);
    }
    if (!AutraStatus.SETTLED.equals(status)) {
      throw new WebhookNotSchedulableException(A_PAYOUT_FOLLOWS_A_PAYMENT);
    }
  }

  String whyNothingWasSentFor(AutraPixPayment pix) {
    return theTargetsOf(pix).isEmpty() ? THE_RUN_CONFIGURES_NO_TARGET_FOR_THIS_MERCHANT : "";
  }

  private static AutraPixEvent theEventAsked(@Nullable String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return AutraPixEvent.FINANCIAL_TRANSACTION;
    }
    try {
      return AutraPixEvent.named(eventType.strip());
    } catch (IllegalArgumentException unknown) {
      throw new WebhookNotSchedulableException(String.valueOf(unknown.getMessage()));
    }
  }

  private @Nullable String theSecretOf(AutraPixPayment pix) {
    return merchants
        .find(pix.testRunId(), pix.accountId())
        .map(AutraMerchant::webhookSecret)
        .orElse(null);
  }

  private List<WebhookTarget> theTargetsOf(AutraPixPayment pix) {
    List<WebhookTarget> mine = new ArrayList<>();
    for (WebhookTarget target : accounts.webhookTargetsOf(AutraApi.AUTRA, pix.testRunId())) {
      if (target.accountId().equals(pix.accountId())) {
        mine.add(target);
      }
    }
    return List.copyOf(mine);
  }
}
