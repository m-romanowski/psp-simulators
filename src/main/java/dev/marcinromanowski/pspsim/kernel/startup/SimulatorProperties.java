package dev.marcinromanowski.pspsim.kernel.startup;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
    String publicBaseUrl,
    String defaultTestRunId,
    Webhook webhook,
    Fault fault,
    Scheduler scheduler) {

  public record Webhook(
      List<Duration> retryDelays,
      Duration connectTimeout,
      Duration readTimeout,
      int maxResponsePreviewBytes,
      Duration pollInterval) {

    public int maxAttempts() {
      return retryDelays.size();
    }
  }

  public record Fault(Duration longestDelay) {}

  public record Scheduler(int poolSize) {}
}
