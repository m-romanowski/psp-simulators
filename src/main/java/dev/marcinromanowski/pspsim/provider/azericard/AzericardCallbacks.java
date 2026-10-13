package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.UnaddressableDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveryRequest;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class AzericardCallbacks {

  static final int NONCE_LENGTH = 16;

  static final String NO_SUCH_PAYMENT =
      "no azericard payment with that providerObjectId in this test run. A callback of provider"
          + " azericard reports an authorization, and the providerObjectId is the INT_REF the"
          + " gateway drew while answering it";

  static final String NO_EVENT_NAME =
      "provider azericard names no callback event anywhere in its contract: BACKREF is the"
          + " \"Merchant URL for posting authorization result\" and the one thing posted to it is"
          + " that result, so eventType names nothing this gateway could send instead";

  static final String BACKREF_IS_NOT_A_CONFIGURED_TARGET =
      "the BACKREF the authorization carried is not among the callback targets configured for the"
          + " terminal's account in this test run, and a simulator that posted to it would deliver"
          + " to an address no test declared for that account";

  static final String NOT_A_TARGET_OF_THE_ACCOUNT =
      "the address this callback was to be posted to is not among the callback targets configured"
          + " for the terminal's account in this test run, and a simulator that posted to it would"
          + " deliver to an address no test declared for that account";

  static final String NO_SINGLE_TARGET =
      "the authorization carried no BACKREF and the terminal it was sent to has no single"
          + " configured callback target, so there is no address the signed result would be"
          + " posted to";

  static final String NO_SUCH_TERMINAL =
      "the terminal the payment belongs to is no longer configured in this test run, and provider"
          + " azericard signs a callback with that terminal's own gateway key";

  private final AzericardPayments payments;
  private final AzericardTerminals terminals;
  private final ProviderAccounts accounts;
  private final WebhookDispatcher dispatcher;
  private final SimulatorClock clock;
  private final IdGenerator ids;

  AzericardCallbacks(
      AzericardPayments payments,
      AzericardTerminals terminals,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      SimulatorClock clock,
      IdGenerator ids) {
    this.payments = payments;
    this.terminals = terminals;
    this.accounts = accounts;
    this.dispatcher = dispatcher;
    this.clock = clock;
    this.ids = ids;
  }

  sealed interface Target {

    record Addressed(String url) implements Target {}

    record Unaddressable(String inItsOwnWords) implements Target {}
  }

  List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    if (request.eventType() != null) {
      throw new WebhookNotSchedulableException(NO_EVENT_NAME);
    }
    TestRunId testRunId = request.testRunId();
    AzericardPayment payment =
        payments
            .findByIntRef(testRunId, request.providerObjectId())
            .orElseThrow(() -> new UnknownProviderObjectException(NO_SUCH_PAYMENT));
    AzericardTerminal terminal =
        terminals
            .find(testRunId, payment.merchantId())
            .orElseThrow(() -> new WebhookNotSchedulableException(NO_SUCH_TERMINAL));
    return switch (targetOf(testRunId, payment.merchantId(), payment.backref())) {
      case Target.Addressed addressed ->
          deliver(payment, terminal, addressed.url(), request.reuseEventId(), request.delay());
      case Target.Unaddressable unaddressable ->
          throw new WebhookNotSchedulableException(unaddressable.inItsOwnWords());
    };
  }

  List<ScheduledWebhook> deliver(
      AzericardPayment payment,
      AzericardTerminal terminal,
      String url,
      @Nullable String reuseEventId,
      @Nullable Duration delay) {
    TestRunId testRunId = payment.testRunId();
    Instant now = clock.now(testRunId);
    WebhookPayload payload =
        AzericardCallback.of(
            payment, terminal, ids.hex(NONCE_LENGTH).toUpperCase(Locale.ROOT), now);
    List<WebhookDelivery> scheduled =
        dispatcher.schedule(
            new WebhookDeliveryRequest(
                AzericardGateway.AZERICARD,
                testRunId,
                payment.merchantId(),
                null,
                payment.intRef(),
                url,
                payload,
                reuseEventId,
                null,
                delay));
    List<ScheduledWebhook> jobs = new ArrayList<>();
    for (WebhookDelivery delivery : scheduled) {
      jobs.add(new ScheduledWebhook(delivery.deliveryId(), delivery.eventId()));
    }
    return List.copyOf(jobs);
  }

  void recordUnaddressable(AzericardPayment payment, AzericardTerminal terminal, String reason) {
    Instant now = clock.now(payment.testRunId());
    dispatcher.recordUnaddressable(
        new UnaddressableDelivery(
            AzericardGateway.AZERICARD,
            payment.testRunId(),
            payment.merchantId(),
            null,
            payment.intRef(),
            AzericardCallback.of(
                payment, terminal, ids.hex(NONCE_LENGTH).toUpperCase(Locale.ROOT), now),
            reason));
  }

  Target targetOf(TestRunId testRunId, String merchantId, @Nullable String requested) {
    List<WebhookTarget> configured =
        accounts.webhookTargetsOf(AzericardGateway.AZERICARD, testRunId);
    if (requested != null) {
      for (WebhookTarget target : configured) {
        if (target.accountId().equals(merchantId) && target.url().equals(requested)) {
          return new Target.Addressed(target.url());
        }
      }
      return new Target.Unaddressable(BACKREF_IS_NOT_A_CONFIGURED_TARGET);
    }
    List<WebhookTarget> ofTerminal = new ArrayList<>();
    for (WebhookTarget target : configured) {
      if (target.accountId().equals(merchantId)) {
        ofTerminal.add(target);
      }
    }
    if (ofTerminal.size() != 1) {
      return new Target.Unaddressable(NO_SINGLE_TARGET);
    }
    return new Target.Addressed(ofTerminal.get(0).url());
  }
}
