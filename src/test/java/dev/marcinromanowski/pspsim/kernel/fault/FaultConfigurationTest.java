package dev.marcinromanowski.pspsim.kernel.fault;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.AfterCommit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FaultConfigurationTest {

  private static final ProviderId GROW = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("fault-configuration");

  @Test
  void the_held_responses_a_reset_abandons_are_abandoned_by_whatever_runs_after_the_commit() {
    List<Runnable> afterTheCommit = new ArrayList<>();
    AfterCommit committing =
        new AfterCommit() {
          @Override
          public void run(Runnable work) {
            afterTheCommit.add(work);
          }

          @Override
          public <T> T outsideAnyTransaction(Supplier<T> work) {
            return work.get();
          }
        };
    FaultConfiguration configuration = new FaultConfiguration();
    ScheduledExecutorService scheduler = configuration.delayedDeliveryScheduler();
    List<DeliveryAbandonment> abandoned = new ArrayList<>();
    try {
      DelayedDeliveries deliveries = configuration.delayedDeliveries(scheduler, committing);
      deliveries.hold(GROW, RUN, Duration.ofHours(1), () -> {}, abandoned::add);

      assertThat(deliveries.cancel(GROW, RUN)).isEqualTo(1);
      assertThat(abandoned).isEmpty();
      assertThat(afterTheCommit).hasSize(1);
      afterTheCommit.get(0).run();

      assertThat(abandoned).containsExactly(DeliveryAbandonment.TEST_RUN_RESET);
      assertThat(deliveries.stillHeld()).isZero();
    } finally {
      scheduler.shutdownNow();
    }
  }
}
