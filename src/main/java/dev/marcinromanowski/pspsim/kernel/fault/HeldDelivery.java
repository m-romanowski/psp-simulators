package dev.marcinromanowski.pspsim.kernel.fault;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public final class HeldDelivery {

  private final ProviderId provider;
  private final TestRunId testRunId;
  private final Runnable deliver;
  private final Consumer<DeliveryAbandonment> abandon;
  private final AtomicBoolean settled = new AtomicBoolean();

  private volatile @Nullable ScheduledDelivery scheduled;

  HeldDelivery(
      ProviderId provider,
      TestRunId testRunId,
      Runnable deliver,
      Consumer<DeliveryAbandonment> abandon) {
    this.provider = provider;
    this.testRunId = testRunId;
    this.deliver = deliver;
    this.abandon = abandon;
  }

  public boolean settle() {
    return settled.compareAndSet(false, true);
  }

  boolean isStillUnsettled() {
    return !settled.get();
  }

  boolean belongsTo(ProviderId provider) {
    return this.provider.equals(provider);
  }

  boolean belongsTo(ProviderId provider, TestRunId testRunId) {
    return belongsTo(provider) && this.testRunId.equals(testRunId);
  }

  void scheduledAs(ScheduledDelivery ticket) {
    scheduled = ticket;
  }

  void deliverNow() {
    if (settle()) {
      deliver.run();
    }
  }

  boolean abandonNow(DeliveryAbandonment reason) {
    if (!settle()) {
      return false;
    }
    ScheduledDelivery ticket = scheduled;
    if (ticket != null) {
      ticket.cancel();
    }
    abandon.accept(reason);
    return true;
  }
}
