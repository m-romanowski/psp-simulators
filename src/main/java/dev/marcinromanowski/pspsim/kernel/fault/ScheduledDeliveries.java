package dev.marcinromanowski.pspsim.kernel.fault;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ScheduledDeliveries implements DelayedDeliveries, AutoCloseable {

  private final DelayScheduler scheduler;
  private final Consumer<Runnable> onceCommitted;
  private final Set<HeldDelivery> held = ConcurrentHashMap.newKeySet();

  public ScheduledDeliveries(DelayScheduler scheduler) {
    this(scheduler, Runnable::run);
  }

  public ScheduledDeliveries(DelayScheduler scheduler, Consumer<Runnable> onceCommitted) {
    this.scheduler = scheduler;
    this.onceCommitted = onceCommitted;
  }

  @Override
  public HeldDelivery hold(
      ProviderId provider,
      TestRunId testRunId,
      Duration delay,
      Runnable deliver,
      Consumer<DeliveryAbandonment> abandon) {
    HeldDelivery delivery = new HeldDelivery(provider, testRunId, deliver, abandon);
    held.add(delivery);
    delivery.scheduledAs(
        scheduler.after(
            delay,
            () -> {
              held.remove(delivery);
              delivery.deliverNow();
            }));
    return delivery;
  }

  @Override
  public int cancel(ProviderId provider, TestRunId testRunId) {
    return abandonedOnceCommitted(delivery -> delivery.belongsTo(provider, testRunId));
  }

  @Override
  public int cancelAll(ProviderId provider) {
    return abandonedOnceCommitted(delivery -> delivery.belongsTo(provider));
  }

  private int abandonedOnceCommitted(Predicate<HeldDelivery> doomed) {
    AtomicInteger abandoned = new AtomicInteger((int) held.stream().filter(doomed).count());
    onceCommitted.accept(() -> abandoned.set(abandon(doomed, DeliveryAbandonment.TEST_RUN_RESET)));
    return abandoned.get();
  }

  @Override
  public void close() {
    abandon(delivery -> true, DeliveryAbandonment.SIMULATOR_STOPPING);
  }

  @Override
  public int stillHeld() {
    return held.size();
  }

  private int abandon(Predicate<HeldDelivery> doomed, DeliveryAbandonment reason) {
    int abandoned = 0;
    for (HeldDelivery delivery : List.copyOf(held)) {
      if (doomed.test(delivery)) {
        held.remove(delivery);
        if (delivery.abandonNow(reason)) {
          abandoned++;
        }
      }
    }
    return abandoned;
  }
}
