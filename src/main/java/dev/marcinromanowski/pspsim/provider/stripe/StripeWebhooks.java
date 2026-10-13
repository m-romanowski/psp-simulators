package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
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
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class StripeWebhooks implements StripeScheduledOutcomes {

  private static final int EVENT_ID_LENGTH = 24;

  static final String NO_SUCH_OBJECT =
      "no stripe PaymentIntent and no stripe Refund with that providerObjectId in this test run";

  static final String NO_EVENT_REPORTS_A_SETUP_INTENT =
      "the providerObjectId names a stripe SetupIntent, and provider stripe reports one with"
          + " setup_intent.succeeded and setup_intent.setup_failed, neither of which this build"
          + " emits. What it delivers is "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.PAYMENT_INTENT)
          + " and "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.REFUND)
          + ", so a callback of this test run is scheduled against a PaymentIntent or a Refund and"
          + " never against the setup that attached the payment method it charges";

  static final String NOTHING_TO_REPORT =
      "the PaymentIntent is in no state this simulator publishes an event for. It delivers "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.PAYMENT_INTENT)
          + ", and an intent awaiting its first confirmation has reached none of them";

  static final String NOT_A_PUBLISHED_EVENT = "eventType must be one of ";

  static final String NOT_WHAT_THE_INTENT_REACHED =
      " reports a state the PaymentIntent is not in, and a Stripe event reports what the object"
          + " already is rather than moving it there. The intent stands at ";

  static final String NOT_ABOUT_A_REFUND =
      " reports a PaymentIntent, and the providerObjectId names a Refund. A refund of provider"
          + " stripe is reported by "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.REFUND);

  static final String NO_EVENT_REPORTS_A_CUSTOMER =
      "the providerObjectId names a stripe customer, and provider stripe reports a customer with"
          + " its own customer.* events, none of which this build emits. What it delivers is "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.PAYMENT_INTENT)
          + " and "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.REFUND)
          + ", so a callback of this test run is scheduled against a PaymentIntent or a Refund"
          + " and never against the customer one belongs to";

  static final String NO_EVENT_REPORTS_A_PAYMENT_METHOD =
      "the providerObjectId names a stripe PaymentMethod, and provider stripe reports a payment"
          + " method with its own payment_method.* events, none of which this contract publishes"
          + " and none of which this build emits. What it delivers is "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.PAYMENT_INTENT)
          + " and "
          + StripeEventType.thePublishedNamesOf(StripeEventSubject.REFUND)
          + ", so a callback of this test run is scheduled against a PaymentIntent or a Refund and"
          + " never against the payment method one was charged with";

  static final String NO_SINGLE_TARGET =
      "the account the object belongs to has no single configured webhook target, so there"
          + " is no endpoint the signed event would be delivered to";

  private final StripePaymentIntents intents;
  private final StripeRefunds refunds;
  private final StripeCustomers customers;
  private final StripePaymentMethods paymentMethods;
  private final StripeSetupIntents setupIntents;
  private final ProviderAccounts accounts;
  private final WebhookDispatcher dispatcher;
  private final SimulatorClock clock;
  private final IdGenerator ids;

  StripeWebhooks(
      StripePaymentIntents intents,
      StripeRefunds refunds,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods,
      StripeSetupIntents setupIntents,
      ProviderAccounts accounts,
      WebhookDispatcher dispatcher,
      SimulatorClock clock,
      IdGenerator ids) {
    this.intents = intents;
    this.refunds = refunds;
    this.customers = customers;
    this.paymentMethods = paymentMethods;
    this.setupIntents = setupIntents;
    this.accounts = accounts;
    this.dispatcher = dispatcher;
    this.clock = clock;
    this.ids = ids;
  }

  List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    TestRunId testRunId = request.testRunId();
    Instant now = clock.now(testRunId);
    Optional<StripePaymentIntent> intent = intents.find(testRunId, request.providerObjectId());
    if (intent.isPresent()) {
      return scheduled(request, testRunId, ofIntent(request, intent.get(), now), now);
    }
    Optional<StripeRefund> refund = refunds.find(testRunId, request.providerObjectId());
    if (refund.isPresent()) {
      return scheduled(request, testRunId, ofRefund(request, refund.get()), now);
    }
    if (customers.find(testRunId, request.providerObjectId()).isPresent()) {
      throw new WebhookNotSchedulableException(NO_EVENT_REPORTS_A_CUSTOMER);
    }
    if (paymentMethods.find(testRunId, request.providerObjectId()).isPresent()) {
      throw new WebhookNotSchedulableException(NO_EVENT_REPORTS_A_PAYMENT_METHOD);
    }
    if (setupIntents.find(testRunId, request.providerObjectId()).isPresent()) {
      throw new WebhookNotSchedulableException(NO_EVENT_REPORTS_A_SETUP_INTENT);
    }
    throw new UnknownProviderObjectException(NO_SUCH_OBJECT);
  }

  @Override
  public void theOutcomeScheduledFor(
      StripePaymentIntent settling, Instant landing, Duration after) {
    Optional<String> target = theSingleTargetOf(settling.accountId(), settling.testRunId());
    if (target.isEmpty()) {
      return;
    }
    StripeEventType reporting =
        settling.settlesWith() == null ? StripeEventType.SUCCEEDED : StripeEventType.PAYMENT_FAILED;
    dispatcher.schedule(
        new WebhookDeliveryRequest(
            StripeApi.STRIPE,
            settling.testRunId(),
            settling.accountId(),
            reporting.wireValue(),
            settling.intentId(),
            target.get(),
            payloadOf(eventIdOf(null), reporting, StripeIntentView.of(settling, landing), landing),
            null,
            null,
            after));
  }

  private List<ScheduledWebhook> scheduled(
      WebhookScheduleRequest request, TestRunId testRunId, Reported reported, Instant now) {
    String target = targetOf(reported.accountId(), testRunId);
    String eventId = eventIdOf(request.reuseEventId());
    return List.copyOf(
        jobsOf(
            dispatcher.schedule(
                new WebhookDeliveryRequest(
                    StripeApi.STRIPE,
                    testRunId,
                    reported.accountId(),
                    reported.type().wireValue(),
                    reported.objectId(),
                    target,
                    payloadOf(eventId, reported.type(), reported.dataObject(), now),
                    eventId,
                    null,
                    request.delay()))));
  }

  private static WebhookPayload payloadOf(
      String eventId, StripeEventType type, StripeJson dataObject, Instant at) {
    StripeJson event = StripeEvent.of(eventId, type, dataObject, at);
    return new WebhookPayload(
        StripeEvent.CONTENT_TYPE,
        Map.of(),
        StripeJsonWriter.toBytes(event),
        Redaction.redact(event.flatten(), StripeFields.SENSITIVE_DELIVERY_FIELDS));
  }

  private static List<ScheduledWebhook> jobsOf(List<WebhookDelivery> scheduled) {
    List<ScheduledWebhook> jobs = new ArrayList<>();
    for (WebhookDelivery delivery : scheduled) {
      jobs.add(new ScheduledWebhook(delivery.deliveryId(), delivery.eventId()));
    }
    return jobs;
  }

  private static Reported ofIntent(
      WebhookScheduleRequest request, StripePaymentIntent intent, Instant now) {
    return new Reported(
        typeOf(request.eventType(), intent, now),
        intent.accountId(),
        intent.intentId(),
        StripeIntentView.of(intent, now));
  }

  private static Reported ofRefund(WebhookScheduleRequest request, StripeRefund refund) {
    return new Reported(
        typeOf(request.eventType()),
        refund.accountId(),
        refund.refundId(),
        StripeRefundView.of(refund));
  }

  private String eventIdOf(@Nullable String reuseEventId) {
    return reuseEventId == null ? "evt_" + ids.hex(EVENT_ID_LENGTH) : reuseEventId;
  }

  private static StripeEventType typeOf(
      @Nullable String eventType, StripePaymentIntent intent, Instant now) {
    if (eventType == null) {
      return StripeEventType.theOneReporting(intent, now)
          .orElseThrow(() -> new WebhookNotSchedulableException(NOTHING_TO_REPORT));
    }
    StripeEventType named = named(eventType);
    if (!named.reports(intent, now)) {
      throw new WebhookNotSchedulableException(
          named.wireValue() + NOT_WHAT_THE_INTENT_REACHED + intent.statusAt(now).wireValue());
    }
    return named;
  }

  private static StripeEventType typeOf(@Nullable String eventType) {
    if (eventType == null) {
      return StripeEventType.REFUND_CREATED;
    }
    StripeEventType named = named(eventType);
    if (!named.reportsACreatedRefund()) {
      throw new WebhookNotSchedulableException(named.wireValue() + NOT_ABOUT_A_REFUND);
    }
    return named;
  }

  private static StripeEventType named(String eventType) {
    return StripeEventType.of(eventType)
        .orElseThrow(
            () ->
                new WebhookNotSchedulableException(
                    NOT_A_PUBLISHED_EVENT + StripeEventType.thePublishedNames()));
  }

  private String targetOf(String accountId, TestRunId testRunId) {
    return theSingleTargetOf(accountId, testRunId)
        .orElseThrow(() -> new WebhookNotSchedulableException(NO_SINGLE_TARGET));
  }

  private Optional<String> theSingleTargetOf(String accountId, TestRunId testRunId) {
    List<WebhookTarget> ofAccount = new ArrayList<>();
    for (WebhookTarget target : accounts.webhookTargetsOf(StripeApi.STRIPE, testRunId)) {
      if (target.accountId().equals(accountId)) {
        ofAccount.add(target);
      }
    }
    return ofAccount.size() == 1 ? Optional.of(ofAccount.get(0).url()) : Optional.empty();
  }

  private record Reported(
      StripeEventType type, String accountId, String objectId, StripeJson dataObject) {}
}
