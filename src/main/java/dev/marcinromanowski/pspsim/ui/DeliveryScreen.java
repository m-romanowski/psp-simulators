package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class DeliveryScreen {

  public static final String AN_ATTEMPT_CARRIES_THE_HEADERS_IT_WAS_SIGNED_WITH =
      "the headers below an attempt are the ones that attempt put on the wire, and not the"
          + " headers stored with the delivery. A provider that signs each attempt afresh signs"
          + " it with the moment that attempt was made, so two attempts this test run's clock"
          + " separates carry two different signatures, and two attempts made at one pinned"
          + " moment carry the same one. A signature is shown as a field that was sent; its"
          + " value stays hidden until it is asked for, and the key that produced it is not a"
          + " field of the message and is on no screen";

  public static final String AN_ATTEMPT_NAMES_THE_TARGET_IT_WENT_TO =
      "an attempt is posted to the target the delivery was scheduled for unless a tester chose"
          + " another of this run's configured targets for that one attempt, so the ledger names"
          + " the address each attempt actually went to rather than the delivery's";

  public static final String AN_ATTEMPT_OLDER_THAN_THIS_LEDGER_RECORDED_NO_WIRE =
      "an attempt made before this simulator recorded what it put on the wire says so, and shows"
          + " no field at all. It is not an attempt that travelled without headers: every"
          + " attempt carries at least the content type it posted under, so an attempt whose"
          + " wire was recorded names that content type and an attempt that names nothing is one"
          + " this build cannot speak for. Read the delivery's own content type and headers"
          + " instead, which were stored when it was scheduled";

  public record Attempt(
      int number,
      Instant attemptedAt,
      String targetUrl,
      boolean wentElsewhere,
      boolean theWireWasRecorded,
      List<Disclosure.Field> sentHeaders,
      StatusBadge verdict,
      @Nullable Integer httpStatus,
      @Nullable String transportFailure,
      @Nullable String responsePreview) {

    public Attempt {
      sentHeaders = List.copyOf(sentHeaders);
    }
  }

  public record Row(
      String deliveryId,
      String eventId,
      @Nullable String eventType,
      @Nullable String providerObjectId,
      String merchantId,
      @Nullable String targetUrl,
      boolean canBeAttempted,
      StatusBadge state,
      boolean dropped,
      @Nullable String shapedBy,
      @Nullable String notSentBecause,
      boolean due,
      boolean acknowledgementOwed,
      int attemptsMade,
      int attemptCap,
      @Nullable Instant nextAttemptAt,
      Instant createdAt,
      boolean open,
      String contentType,
      List<Disclosure.Field> headers,
      List<Disclosure.Field> body,
      List<Attempt> attempts,
      List<DeliveryForm.Target> otherTargets) {

    public Row {
      headers = List.copyOf(headers);
      body = List.copyOf(body);
      attempts = List.copyOf(attempts);
      otherTargets = List.copyOf(otherTargets);
    }

    public boolean offersAnotherTarget() {
      return !otherTargets.isEmpty();
    }
  }

  public record Queue(
      List<Row> rows,
      List<Row> failing,
      int pending,
      int due,
      int delivered,
      int exhausted,
      int dropped,
      int undeliverable,
      int awaitingCall,
      boolean valuesShown,
      DeliveryForm.Composer composer) {

    public Queue {
      rows = List.copyOf(rows);
      failing = List.copyOf(failing);
    }
  }

  public interface Attempts {
    List<WebhookAttempt> of(String deliveryId);
  }

  private DeliveryScreen() {}

  public static Queue queueOf(
      List<WebhookDelivery> deliveries,
      Instant now,
      @Nullable String openDeliveryId,
      boolean valuesAsked,
      Attempts attempts,
      List<WebhookTarget> configured,
      DeliveryForm.Composer composer) {
    List<Row> rows = new ArrayList<>();
    List<Row> failing = new ArrayList<>();
    int pending = 0;
    int due = 0;
    int delivered = 0;
    int exhausted = 0;
    int dropped = 0;
    int undeliverable = 0;
    int awaitingCall = 0;
    for (WebhookDelivery delivery : deliveries) {
      boolean open = delivery.deliveryId().equals(openDeliveryId);
      Row row = rowOf(delivery, now, open, valuesAsked, attempts, configured);
      rows.add(row);
      if (aMerchantHasAlreadyRefusedIt(delivery)) {
        failing.add(row);
      }
      switch (delivery.state()) {
        case PENDING -> pending++;
        case DELIVERED -> delivered++;
        case EXHAUSTED -> exhausted++;
        case DROPPED -> dropped++;
        case UNDELIVERABLE -> undeliverable++;
      }
      if (delivery.isDueAt(now)) {
        due++;
      }
      if (delivery.acknowledgementOwed()) {
        awaitingCall++;
      }
    }
    return new Queue(
        rows,
        failing,
        pending,
        due,
        delivered,
        exhausted,
        dropped,
        undeliverable,
        awaitingCall,
        valuesAsked,
        composer);
  }

  private static boolean aMerchantHasAlreadyRefusedIt(WebhookDelivery delivery) {
    return delivery.state() != DeliveryState.DELIVERED && delivery.attemptsMade() > 0;
  }

  private static Row rowOf(
      WebhookDelivery delivery,
      Instant now,
      boolean open,
      boolean valuesAsked,
      Attempts attempts,
      List<WebhookTarget> configured) {
    return new Row(
        delivery.deliveryId(),
        delivery.eventId(),
        delivery.eventType(),
        delivery.providerObjectId(),
        delivery.merchantId(),
        delivery.targetUrl(),
        delivery.canBeAttempted(),
        StatusBadge.of(delivery.state()),
        delivery.state() == DeliveryState.DROPPED,
        delivery.shapedBy(),
        delivery.notSentBecause(),
        delivery.isDueAt(now),
        delivery.acknowledgementOwed(),
        delivery.attemptsMade(),
        delivery.retryPlan().attemptCap(),
        delivery.nextAttemptAt(),
        delivery.createdAt(),
        open,
        delivery.payload().contentType(),
        open ? Disclosure.of(delivery.payload().wireHeaders(), valuesAsked) : List.of(),
        open ? Disclosure.of(delivery.payload().sanitizedBody(), valuesAsked) : List.of(),
        open
            ? attemptsOf(attempts.of(delivery.deliveryId()), delivery.targetUrl(), valuesAsked)
            : List.of(),
        open ? anywhereElseThisRunConfigures(configured, delivery.targetUrl()) : List.of());
  }

  private static List<DeliveryForm.Target> anywhereElseThisRunConfigures(
      List<WebhookTarget> configured, @Nullable String scheduledFor) {
    List<WebhookTarget> elsewhere = new ArrayList<>();
    for (WebhookTarget target : configured) {
      if (!target.url().equals(scheduledFor)) {
        elsewhere.add(target);
      }
    }
    return DeliveryForm.targetsOffered(elsewhere);
  }

  private static List<Attempt> attemptsOf(
      List<WebhookAttempt> made, @Nullable String scheduledFor, boolean valuesAsked) {
    List<Attempt> attempts = new ArrayList<>();
    for (WebhookAttempt attempt : made) {
      TransportFailure failure = attempt.failure();
      Map<String, List<String>> onTheWire = attempt.sentHeaders();
      attempts.add(
          new Attempt(
              attempt.number(),
              attempt.attemptedAt(),
              attempt.targetUrl(),
              scheduledFor != null && !attempt.targetUrl().equals(scheduledFor),
              attempt.theWireWasRecorded(),
              onTheWire == null ? List.of() : Disclosure.of(onTheWire, valuesAsked),
              StatusBadge.of(attempt.verdict()),
              attempt.httpStatus(),
              failure == null ? null : failure.name(),
              attempt.responsePreview()));
    }
    return List.copyOf(attempts);
  }
}
