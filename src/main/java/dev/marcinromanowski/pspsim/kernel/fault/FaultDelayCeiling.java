package dev.marcinromanowski.pspsim.kernel.fault;

import java.time.Duration;

public record FaultDelayCeiling(Duration longest) {

  public static final Duration DELIVERY_MARGIN = Duration.ofSeconds(10);

  public FaultDelayCeiling {
    if (longest.isZero() || longest.isNegative()) {
      throw new IllegalArgumentException(
          "simulator.fault.longest-delay must be positive, or every delay a rule names is refused"
              + " at request time by a simulator that accepted it");
    }
    if (longest.compareTo(TransportFault.LONGEST_DELAY) > 0) {
      throw new IllegalArgumentException(
          "simulator.fault.longest-delay must not exceed "
              + TransportFault.LONGEST_DELAY
              + ", the longest a transport fault may hold a client's connection open");
    }
  }

  public Duration deliveryDeadline() {
    return longest.plus(DELIVERY_MARGIN);
  }

  public boolean admits(Duration delay) {
    return delay.compareTo(longest) <= 0;
  }

  public TransportFault.DelayedResponse holdingFor(Duration delay) {
    if (!admits(delay)) {
      throw new IllegalArgumentException(
          "action.delayMs must not exceed simulator.fault.longest-delay, which holds a client's"
              + " connection open for at most "
              + longest);
    }
    return new TransportFault.DelayedResponse(delay);
  }
}
