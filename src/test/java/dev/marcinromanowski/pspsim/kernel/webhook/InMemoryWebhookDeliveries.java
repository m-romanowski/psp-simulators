package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryWebhookDeliveries implements WebhookDeliveries {

  private final Map<String, WebhookDelivery> byId = new LinkedHashMap<>();
  private final List<WebhookAttempt> attempts = new ArrayList<>();
  private long sequence;
  private final Map<String, Long> order = new LinkedHashMap<>();
  private volatile Runnable afterEveryReadOfOne = () -> {};
  private volatile Runnable afterReadingOne = () -> {};

  public InMemoryWebhookDeliveries afterEveryReadOfOne(Runnable action) {
    this.afterEveryReadOfOne = action;
    return this;
  }

  public InMemoryWebhookDeliveries afterReadingOne(Runnable action) {
    this.afterReadingOne = action;
    return this;
  }

  @Override
  public synchronized void add(WebhookDelivery delivery) {
    byId.put(delivery.deliveryId(), delivery);
    order.put(delivery.deliveryId(), sequence++);
  }

  @Override
  public Optional<WebhookDelivery> find(
      ProviderId provider, TestRunId testRunId, String deliveryId) {
    Optional<WebhookDelivery> found = snapshotOf(provider, testRunId, deliveryId);
    Runnable once = afterReadingOne;
    afterReadingOne = () -> {};
    once.run();
    afterEveryReadOfOne.run();
    return found;
  }

  private synchronized Optional<WebhookDelivery> snapshotOf(
      ProviderId provider, TestRunId testRunId, String deliveryId) {
    return Optional.ofNullable(byId.get(deliveryId)).filter(d -> belongsTo(d, provider, testRunId));
  }

  @Override
  public synchronized List<WebhookDelivery> of(ProviderId provider, TestRunId testRunId) {
    return byId.values().stream()
        .filter(d -> belongsTo(d, provider, testRunId))
        .sorted(
            Comparator.comparingLong((WebhookDelivery d) -> order.get(d.deliveryId())).reversed())
        .toList();
  }

  @Override
  public List<PendingDelivery> pending(ProviderId provider, TestRunId testRunId) {
    return snapshotOfPending().stream()
        .filter(d -> d.provider().equals(provider) && d.testRunId().equals(testRunId))
        .toList();
  }

  @Override
  public List<PendingDelivery> pendingEverywhere() {
    return snapshotOfPending();
  }

  private synchronized List<PendingDelivery> snapshotOfPending() {
    return byId.values().stream()
        .filter(d -> d.state() == DeliveryState.PENDING && d.nextAttemptAt() != null)
        .sorted(Comparator.comparingLong(d -> order.get(d.deliveryId())))
        .map(
            d ->
                new PendingDelivery(
                    d.provider(),
                    d.testRunId(),
                    d.deliveryId(),
                    Objects.requireNonNull(d.nextAttemptAt())))
        .toList();
  }

  @Override
  public synchronized boolean claim(WebhookDelivery settled, int expectedAttemptsMade) {
    WebhookDelivery delivery = byId.get(settled.deliveryId());
    if (delivery == null || delivery.state() != DeliveryState.PENDING) {
      return false;
    }
    return reclaim(settled, expectedAttemptsMade);
  }

  @Override
  public synchronized boolean reclaim(WebhookDelivery settled, int expectedAttemptsMade) {
    WebhookDelivery delivery = byId.get(settled.deliveryId());
    if (delivery == null || delivery.attemptsMade() != expectedAttemptsMade) {
      return false;
    }
    byId.put(settled.deliveryId(), settled);
    return true;
  }

  @Override
  public synchronized void settle(WebhookDelivery delivery) {
    WebhookDelivery stored = byId.get(delivery.deliveryId());
    if (stored == null
        || stored.state() == DeliveryState.DELIVERED
        || stored.attemptsMade() != delivery.attemptsMade()) {
      return;
    }
    byId.put(delivery.deliveryId(), delivery);
  }

  @Override
  public synchronized List<String> owedAnAcknowledgement(
      ProviderId provider, TestRunId testRunId, List<String> answersTo) {
    return byId.values().stream()
        .filter(d -> belongsTo(d, provider, testRunId))
        .filter(d -> d.acknowledgementOwed())
        .filter(d -> d.providerObjectId() != null)
        .filter(d -> answersTo.contains(d.providerObjectId()))
        .sorted(Comparator.comparingLong(d -> order.get(d.deliveryId())))
        .map(WebhookDelivery::deliveryId)
        .toList();
  }

  @Override
  public synchronized boolean acknowledge(String deliveryId, Instant acknowledgedAt) {
    WebhookDelivery delivery = byId.get(deliveryId);
    if (delivery == null || delivery.state() == DeliveryState.DELIVERED) {
      return false;
    }
    byId.put(
        deliveryId,
        new WebhookDelivery(
            delivery.deliveryId(),
            delivery.provider(),
            delivery.testRunId(),
            delivery.merchantId(),
            delivery.eventId(),
            delivery.eventType(),
            delivery.providerObjectId(),
            delivery.targetUrl(),
            delivery.payload(),
            delivery.retryPlan(),
            DeliveryState.DELIVERED,
            false,
            delivery.attemptsMade(),
            null,
            delivery.createdAt(),
            acknowledgedAt,
            null,
            null));
    return true;
  }

  @Override
  public synchronized void recordAttempt(WebhookAttempt attempt) {
    attempts.add(attempt);
  }

  @Override
  public synchronized List<WebhookAttempt> attemptsOf(String deliveryId) {
    return attempts.stream()
        .filter(a -> a.deliveryId().equals(deliveryId))
        .sorted(Comparator.comparingInt(WebhookAttempt::number))
        .toList();
  }

  @Override
  public synchronized int clear(ProviderId provider, TestRunId testRunId) {
    List<WebhookDelivery> doomed = of(provider, testRunId);
    doomed.forEach(d -> remove(d.deliveryId()));
    return doomed.size();
  }

  @Override
  public synchronized int clearAll(ProviderId provider) {
    List<WebhookDelivery> doomed =
        byId.values().stream().filter(d -> d.provider().equals(provider)).toList();
    doomed.forEach(d -> remove(d.deliveryId()));
    return doomed.size();
  }

  private void remove(String deliveryId) {
    byId.remove(deliveryId);
    order.remove(deliveryId);
    attempts.removeIf(a -> a.deliveryId().equals(deliveryId));
  }

  private static boolean belongsTo(
      WebhookDelivery delivery, ProviderId provider, TestRunId testRunId) {
    return delivery.provider().equals(provider) && delivery.testRunId().equals(testRunId);
  }
}
