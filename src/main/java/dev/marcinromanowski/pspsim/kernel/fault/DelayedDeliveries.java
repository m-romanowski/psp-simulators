package dev.marcinromanowski.pspsim.kernel.fault;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.function.Consumer;

public interface DelayedDeliveries {

  HeldDelivery hold(
      ProviderId provider,
      TestRunId testRunId,
      Duration delay,
      Runnable deliver,
      Consumer<DeliveryAbandonment> abandon);

  int cancel(ProviderId provider, TestRunId testRunId);

  int cancelAll(ProviderId provider);

  int stillHeld();
}
