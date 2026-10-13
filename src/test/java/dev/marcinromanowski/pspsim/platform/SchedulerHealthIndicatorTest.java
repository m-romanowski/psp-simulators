package dev.marcinromanowski.pspsim.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class SchedulerHealthIndicatorTest {

  @Test
  void is_up_and_reports_pool_details_while_the_scheduler_runs() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.initialize();
    try {
      Health health = new SchedulerHealthIndicator(scheduler).health();

      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
          .containsKeys("corePoolSize", "liveThreads", "activeCount", "queuedTasks");
    } finally {
      scheduler.shutdown();
    }
  }

  @Test
  void reports_the_configured_pool_size_even_though_an_idle_pool_has_no_threads_yet() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(4);
    scheduler.initialize();
    try {
      Health health = new SchedulerHealthIndicator(scheduler).health();

      assertThat(health.getDetails()).containsEntry("corePoolSize", 4);
      assertThat(health.getDetails()).containsEntry("liveThreads", 0);
    } finally {
      scheduler.shutdown();
    }
  }

  @Test
  void is_down_once_the_scheduler_has_shut_down() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.initialize();
    scheduler.shutdown();

    Health health = new SchedulerHealthIndicator(scheduler).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("reason", "scheduler is shut down");
  }

  @Test
  void is_down_before_the_scheduler_is_initialized() {
    Health health = new SchedulerHealthIndicator(new ThreadPoolTaskScheduler()).health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("reason", "scheduler is not initialized");
  }
}
