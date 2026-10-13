package dev.marcinromanowski.pspsim.kernel.fault;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FaultConfiguration {

  @Bean(destroyMethod = "shutdownNow")
  public ScheduledExecutorService delayedDeliveryScheduler() {
    return Executors.newSingleThreadScheduledExecutor(
        runnable -> {
          Thread thread = new Thread(runnable, "simulator-delayed-delivery");
          thread.setDaemon(true);
          return thread;
        });
  }

  @Bean
  public DelayedDeliveries delayedDeliveries(ScheduledExecutorService delayedDeliveryScheduler) {
    return new ScheduledDeliveries(
        (delay, delivery) -> {
          ScheduledFuture<?> scheduled =
              delayedDeliveryScheduler.schedule(delivery, delay.toMillis(), TimeUnit.MILLISECONDS);
          return () -> scheduled.cancel(false);
        });
  }
}
