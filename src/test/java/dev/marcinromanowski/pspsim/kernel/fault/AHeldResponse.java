package dev.marcinromanowski.pspsim.kernel.fault;

import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public final class AHeldResponse {

  private static final Duration LONGEST_WAIT = Duration.ofSeconds(30);

  private static final long BETWEEN_READS_NANOS = Duration.ofMillis(1).toNanos();

  private AHeldResponse() {}

  public static void isHeldBy(DelayedDeliveries deliveries) {
    long deadline = System.nanoTime() + LONGEST_WAIT.toNanos();
    while (deliveries.stillHeld() == 0) {
      if (System.nanoTime() - deadline >= 0) {
        fail(
            "no response was being held after "
                + LONGEST_WAIT
                + ", so the request this test holds open never reached the scenario delay and a"
                + " reset now would prove nothing about a held response");
      }
      LockSupport.parkNanos(BETWEEN_READS_NANOS);
    }
  }
}
