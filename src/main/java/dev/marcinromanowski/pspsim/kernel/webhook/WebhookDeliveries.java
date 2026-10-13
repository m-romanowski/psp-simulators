package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebhookDeliveries {

  void add(WebhookDelivery delivery);

  Optional<WebhookDelivery> find(ProviderId provider, TestRunId testRunId, String deliveryId);

  List<WebhookDelivery> of(ProviderId provider, TestRunId testRunId);

  List<PendingDelivery> pending(ProviderId provider, TestRunId testRunId);

  List<PendingDelivery> pendingEverywhere();

  boolean claim(WebhookDelivery settled, int expectedAttemptsMade);

  boolean reclaim(WebhookDelivery settled, int expectedAttemptsMade);

  void settle(WebhookDelivery delivery);

  List<String> owedAnAcknowledgement(
      ProviderId provider, TestRunId testRunId, List<String> answersTo);

  boolean acknowledge(String deliveryId, Instant acknowledgedAt);

  void recordAttempt(WebhookAttempt attempt);

  List<WebhookAttempt> attemptsOf(String deliveryId);

  int clear(ProviderId provider, TestRunId testRunId);

  int clearAll(ProviderId provider);
}
