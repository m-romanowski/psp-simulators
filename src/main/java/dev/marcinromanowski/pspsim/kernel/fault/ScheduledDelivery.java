package dev.marcinromanowski.pspsim.kernel.fault;

@FunctionalInterface
public interface ScheduledDelivery {

  void cancel();
}
