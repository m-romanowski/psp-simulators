package dev.marcinromanowski.pspsim.kernel.fault;

import java.time.Duration;

@FunctionalInterface
public interface DelayScheduler {

  ScheduledDelivery after(Duration delay, Runnable delivery);
}
