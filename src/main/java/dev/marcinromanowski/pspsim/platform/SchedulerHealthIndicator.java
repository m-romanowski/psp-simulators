package dev.marcinromanowski.pspsim.platform;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class SchedulerHealthIndicator implements HealthIndicator {

  private final ThreadPoolTaskScheduler scheduler;

  public SchedulerHealthIndicator(ThreadPoolTaskScheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Override
  public Health health() {
    ScheduledThreadPoolExecutor executor;
    try {
      executor = scheduler.getScheduledThreadPoolExecutor();
    } catch (IllegalStateException notInitialized) {
      return Health.down().withDetail("reason", "scheduler is not initialized").build();
    }
    if (executor.isShutdown() || executor.isTerminated()) {
      return Health.down().withDetail("reason", "scheduler is shut down").build();
    }
    return Health.up()
        .withDetail("corePoolSize", executor.getCorePoolSize())
        .withDetail("liveThreads", executor.getPoolSize())
        .withDetail("activeCount", executor.getActiveCount())
        .withDetail("queuedTasks", executor.getQueue().size())
        .build();
  }
}
