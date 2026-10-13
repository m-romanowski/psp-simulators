package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class WebhookControl {

  public enum Operation {
    SEND,
    RETRY;

    private static final int A_NEW_JOB_WAS_SCHEDULED = 201;

    private static final int AN_EXISTING_JOB_WAS_RETRIED = 200;

    public int successStatusCode() {
      if (this == SEND) {
        return A_NEW_JOB_WAS_SCHEDULED;
      }
      return AN_EXISTING_JOB_WAS_RETRIED;
    }
  }

  private WebhookControl() {}

  public static Operation operationOf(@Nullable String operation) {
    if (operation == null || operation.isBlank()) {
      return Operation.SEND;
    }
    for (Operation known : Operation.values()) {
      if (known.name().equals(operation)) {
        return known;
      }
    }
    throw new IllegalArgumentException("operation must be one of SEND, RETRY");
  }

  public static List<String> send(
      SimulatorProvider module,
      TestRunId testRunId,
      DeclaredWebhookCall declared,
      ObjectOrigin askedBy) {
    List<String> jobIds = new ArrayList<>();
    for (ScheduledWebhook scheduled : module.schedule(scheduleOf(testRunId, declared, askedBy))) {
      jobIds.add(scheduled.webhookJobId());
    }
    return List.copyOf(jobIds);
  }

  private static WebhookScheduleRequest scheduleOf(
      TestRunId testRunId, DeclaredWebhookCall declared, ObjectOrigin askedBy) {
    String providerObjectId = declared.providerObjectId();
    if (providerObjectId == null || providerObjectId.isBlank()) {
      throw new IllegalArgumentException("providerObjectId is required to send a webhook");
    }
    return new WebhookScheduleRequest(
        testRunId,
        providerObjectId,
        declared.eventType(),
        declared.reuseEventId(),
        delayOf(declared.delayMs()),
        askedBy);
  }

  private static @Nullable Duration delayOf(@Nullable Long delayMs) {
    if (delayMs == null) {
      return null;
    }
    if (delayMs < 0) {
      throw new IllegalArgumentException("delayMs must not be negative");
    }
    return Duration.ofMillis(delayMs);
  }

  public static String requiredWebhookJobId(DeclaredWebhookCall declared) {
    String webhookJobId = declared.webhookJobId();
    if (webhookJobId == null || webhookJobId.isBlank()) {
      throw new IllegalArgumentException("webhookJobId is required to retry a webhook");
    }
    return webhookJobId;
  }
}
