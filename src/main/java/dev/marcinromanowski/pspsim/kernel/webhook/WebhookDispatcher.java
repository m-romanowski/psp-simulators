package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryFacts;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShape;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.AfterCommit;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class WebhookDispatcher {

  private static final int ID_LENGTH = 24;

  private interface Claim {
    boolean take(WebhookDelivery settled, int expectedAttemptsMade);
  }

  private interface Withholding {
    boolean withhold(
        WebhookDelivery delivery, String reason, Instant now, Claim claim, String targetUrl);
  }

  private final WebhookDeliveries deliveries;
  private final MerchantEndpoint endpoint;
  private final AuditTrail audit;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final CallbackRetryPlans retryPlans;
  private final DeliveryShaping shaping;
  private final Map<ProviderId, AcknowledgementPolicy> policies;
  private final Map<ProviderId, AttemptRendering> renderings;
  private final AfterCommit afterCommit;

  public WebhookDispatcher(
      WebhookDeliveries deliveries,
      MerchantEndpoint endpoint,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      CallbackRetryPlans retryPlans,
      DeliveryShaping shaping,
      List<AcknowledgementPolicy> policies,
      List<AttemptRendering> renderings,
      AfterCommit afterCommit) {
    this.afterCommit = afterCommit;
    this.shaping = shaping;
    this.deliveries = deliveries;
    this.endpoint = endpoint;
    this.audit = audit;
    this.clock = clock;
    this.ids = ids;
    this.retryPlans = retryPlans;
    Map<ProviderId, AcknowledgementPolicy> byProvider = new LinkedHashMap<>();
    for (AcknowledgementPolicy policy : policies) {
      AcknowledgementPolicy clash = byProvider.put(policy.provider(), policy);
      if (clash != null) {
        throw new IllegalStateException(
            "two acknowledgement policies claim the provider id " + policy.provider().value());
      }
    }
    this.policies = Map.copyOf(byProvider);
    Map<ProviderId, AttemptRendering> perProvider = new LinkedHashMap<>();
    for (AttemptRendering rendering : renderings) {
      AttemptRendering clash = perProvider.put(rendering.provider(), rendering);
      if (clash != null) {
        throw new IllegalStateException(
            "two attempt renderings claim the provider id " + rendering.provider().value());
      }
    }
    this.renderings = Map.copyOf(perProvider);
  }

  public List<WebhookDelivery> schedule(WebhookDeliveryRequest request) {
    Instant now = clock.now(request.testRunId());
    RetryPlan plan =
        request.retryPlan() == null
            ? retryPlans.of(request.provider(), request.testRunId())
            : request.retryPlan();
    Duration delay = request.delay() == null ? plan.delayBefore(1) : request.delay();
    String eventId = request.eventId() == null ? "evt_" + ids.hex(ID_LENGTH) : request.eventId();
    DeliveryShape shape =
        shaping.shapeOf(
            new DeliveryFacts(
                request.provider(),
                request.testRunId(),
                request.merchantId(),
                request.providerObjectId(),
                request.eventType()));
    if (shape instanceof DeliveryShape.Dropped) {
      WebhookDelivery withheld =
          put(request, plan, eventId, DeliveryState.DROPPED, null, shape.scenarioId(), now);
      recordTheDeliveryTheRuleWithheld(withheld, now, request.targetUrl());
      return List.of(withheld);
    }
    Instant leavingAt =
        shape instanceof DeliveryShape.HeldBack back
            ? now.plus(delay).plus(back.held())
            : now.plus(delay);
    List<WebhookDelivery> scheduled = new ArrayList<>();
    scheduled.add(sent(request, plan, eventId, leavingAt, shape.scenarioId(), now));
    if (shape instanceof DeliveryShape.Repeated) {
      scheduled.add(sent(request, plan, eventId, leavingAt, shape.scenarioId(), now));
    }
    return List.copyOf(scheduled);
  }

  private WebhookDelivery sent(
      WebhookDeliveryRequest request,
      RetryPlan plan,
      String eventId,
      Instant leavingAt,
      @Nullable String shapedBy,
      Instant now) {
    WebhookDelivery delivery =
        put(request, plan, eventId, DeliveryState.PENDING, leavingAt, shapedBy, now);
    if (delivery.isDueAt(now)) {
      afterCommit.run(
          () -> attempt(delivery, now, deliveries::claim, request.targetUrl(), this::withhold));
    }
    return delivery;
  }

  public WebhookDelivery recordUnaddressable(UnaddressableDelivery unaddressable) {
    Instant now = clock.now(unaddressable.testRunId());
    WebhookDelivery delivery =
        new WebhookDelivery(
            "whjob_" + ids.hex(ID_LENGTH),
            unaddressable.provider(),
            unaddressable.testRunId(),
            unaddressable.merchantId(),
            "evt_" + ids.hex(ID_LENGTH),
            unaddressable.eventType(),
            unaddressable.providerObjectId(),
            null,
            unaddressable.payload(),
            retryPlans.of(unaddressable.provider(), unaddressable.testRunId()),
            DeliveryState.UNDELIVERABLE,
            false,
            0,
            null,
            now,
            now,
            null,
            unaddressable.notSentBecause());
    deliveries.add(delivery);
    recordTheDeliveryTheProviderWouldNotSend(delivery, now, null);
    return delivery;
  }

  private WebhookDelivery put(
      WebhookDeliveryRequest request,
      RetryPlan plan,
      String eventId,
      DeliveryState state,
      @Nullable Instant nextAttemptAt,
      @Nullable String shapedBy,
      Instant now) {
    WebhookDelivery delivery =
        new WebhookDelivery(
            "whjob_" + ids.hex(ID_LENGTH),
            request.provider(),
            request.testRunId(),
            request.merchantId(),
            eventId,
            request.eventType(),
            request.providerObjectId(),
            request.targetUrl(),
            request.payload(),
            plan,
            state,
            false,
            0,
            nextAttemptAt,
            now,
            now,
            shapedBy,
            null);
    deliveries.add(delivery);
    return delivery;
  }

  public int deliverDue(ProviderId provider, TestRunId testRunId) {
    return afterCommit.outsideAnyTransaction(
        () -> attemptAll(deliveries.pending(provider, testRunId)));
  }

  public int deliverDueEverywhere() {
    return afterCommit.outsideAnyTransaction(() -> attemptAll(deliveries.pendingEverywhere()));
  }

  private int attemptAll(List<PendingDelivery> pending) {
    Map<TestRunId, Instant> nowOfRun = new HashMap<>();
    int attempted = 0;
    for (PendingDelivery candidate : pending) {
      Instant now = nowOfRun.computeIfAbsent(candidate.testRunId(), clock::now);
      if (!candidate.isDueAt(now)) {
        continue;
      }
      Optional<WebhookDelivery> due =
          deliveries.find(candidate.provider(), candidate.testRunId(), candidate.deliveryId());
      if (due.isPresent()
          && due.get().isDueAt(now)
          && attempt(
              due.get(),
              now,
              deliveries::claim,
              due.get().theTargetOfAnAttempt(null),
              this::withhold)) {
        attempted++;
      }
    }
    return attempted;
  }

  public Optional<WebhookDelivery> retry(
      ProviderId provider, TestRunId testRunId, String deliveryId) {
    return retry(provider, testRunId, deliveryId, null);
  }

  public Optional<WebhookDelivery> retry(
      ProviderId provider, TestRunId testRunId, String deliveryId, @Nullable String targetUrl) {
    return afterCommit.outsideAnyTransaction(
        () -> retryNow(provider, testRunId, deliveryId, targetUrl));
  }

  private Optional<WebhookDelivery> retryNow(
      ProviderId provider, TestRunId testRunId, String deliveryId, @Nullable String targetUrl) {
    Optional<WebhookDelivery> found = deliveries.find(provider, testRunId, deliveryId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    WebhookDelivery delivery = found.get();
    attempt(
        delivery,
        clock.now(testRunId),
        deliveries::reclaim,
        delivery.theTargetOfAnAttempt(targetUrl),
        delivery.isRetargetedBy(targetUrl) ? this::refuseTheAttempt : this::withhold);
    return deliveries.find(provider, testRunId, deliveryId);
  }

  public int acknowledgeTheDeliveriesOf(
      ProviderId provider, TestRunId testRunId, List<String> answersTo) {
    if (answersTo.isEmpty()) {
      return 0;
    }
    Instant now = clock.now(testRunId);
    int settled = 0;
    for (String deliveryId : deliveries.owedAnAcknowledgement(provider, testRunId, answersTo)) {
      if (deliveries.acknowledge(deliveryId, now)) {
        settled++;
      }
    }
    return settled;
  }

  public Optional<WebhookDelivery> find(
      ProviderId provider, TestRunId testRunId, String deliveryId) {
    return deliveries.find(provider, testRunId, deliveryId);
  }

  public List<WebhookDelivery> of(ProviderId provider, TestRunId testRunId) {
    return deliveries.of(provider, testRunId);
  }

  public List<WebhookAttempt> attemptsOf(String deliveryId) {
    return deliveries.attemptsOf(deliveryId);
  }

  public int clear(ProviderId provider, TestRunId testRunId) {
    return deliveries.clear(provider, testRunId);
  }

  public int clearAll(ProviderId provider) {
    return deliveries.clearAll(provider);
  }

  private boolean attempt(
      WebhookDelivery delivery,
      Instant now,
      Claim claim,
      String targetUrl,
      Withholding withholding) {
    return switch (renderingOf(delivery, targetUrl, now)) {
      case AttemptRendering.Rendering.NothingThisProviderWouldSend withheld ->
          withholding.withhold(delivery, withheld.reason(), now, claim, targetUrl);
      case AttemptRendering.Rendering.Sending sending ->
          post(delivery, sending.payload(), now, claim, targetUrl);
    };
  }

  private boolean refuseTheAttempt(
      WebhookDelivery delivery, String reason, Instant now, Claim claim, String targetUrl) {
    recordTheDeliveryTheProviderWouldNotSend(notSent(delivery, reason, now), now, targetUrl);
    throw new AttemptWithheldException(delivery.providerObjectId(), reason);
  }

  private boolean withhold(
      WebhookDelivery delivery, String reason, Instant now, Claim claim, String targetUrl) {
    WebhookDelivery undeliverable = notSent(delivery, reason, now);
    if (claim.take(undeliverable, delivery.attemptsMade())) {
      recordTheDeliveryTheProviderWouldNotSend(undeliverable, now, targetUrl);
    }
    return false;
  }

  private boolean post(
      WebhookDelivery delivery,
      WebhookPayload sending,
      Instant now,
      Claim claim,
      String targetUrl) {
    int number = delivery.attemptsMade() + 1;
    if (!claim.take(
        settled(delivery, number, Acknowledgement.REFUSED, now), delivery.attemptsMade())) {
      return false;
    }
    MerchantResponse response = endpoint.post(targetUrl, sending);
    Acknowledgement verdict = policyFor(delivery.provider()).judge(delivery, response);

    WebhookAttempt attempt =
        new WebhookAttempt(
            delivery.deliveryId(),
            number,
            now,
            targetUrl,
            sending.wireHeaders(),
            verdict,
            statusOf(response),
            failureOf(response),
            previewOf(response));
    deliveries.recordAttempt(attempt);

    WebhookDelivery settled = settled(delivery, number, verdict, now);
    deliveries.settle(settled);
    record(settled, attempt, sending);
    return true;
  }

  private AttemptRendering.Rendering renderingOf(
      WebhookDelivery delivery, String targetUrl, Instant now) {
    AttemptRendering rendering = renderings.get(delivery.provider());
    return rendering == null
        ? new AttemptRendering.Rendering.Sending(delivery.payload())
        : rendering.renderFor(delivery, targetUrl, now);
  }

  private static WebhookDelivery notSent(WebhookDelivery delivery, String reason, Instant now) {
    return new WebhookDelivery(
        delivery.deliveryId(),
        delivery.provider(),
        delivery.testRunId(),
        delivery.merchantId(),
        delivery.eventId(),
        delivery.eventType(),
        delivery.providerObjectId(),
        delivery.targetUrl(),
        delivery.payload(),
        delivery.retryPlan(),
        DeliveryState.UNDELIVERABLE,
        delivery.acknowledgementOwed(),
        delivery.attemptsMade(),
        null,
        delivery.createdAt(),
        now,
        delivery.shapedBy(),
        reason);
  }

  private static WebhookDelivery settled(
      WebhookDelivery delivery, int number, Acknowledgement verdict, Instant now) {
    boolean more =
        verdict.scheduling() == Acknowledgement.Scheduling.NEXT_ATTEMPT
            && delivery.retryPlan().hasAttemptAfter(number);
    DeliveryState state =
        switch (verdict.scheduling()) {
          case FINISHED -> DeliveryState.DELIVERED;
          case NEXT_ATTEMPT -> more ? DeliveryState.PENDING : DeliveryState.EXHAUSTED;
        };
    boolean acknowledgementOwed =
        verdict != Acknowledgement.ACKNOWLEDGED
            && (delivery.acknowledgementOwed()
                || verdict == Acknowledgement.AWAITING_SEPARATE_CALL);
    Instant nextAttemptAt = more ? now.plus(delivery.retryPlan().delayBefore(number + 1)) : null;
    return new WebhookDelivery(
        delivery.deliveryId(),
        delivery.provider(),
        delivery.testRunId(),
        delivery.merchantId(),
        delivery.eventId(),
        delivery.eventType(),
        delivery.providerObjectId(),
        delivery.targetUrl(),
        delivery.payload(),
        delivery.retryPlan(),
        state,
        acknowledgementOwed,
        number,
        nextAttemptAt,
        delivery.createdAt(),
        now,
        delivery.shapedBy(),
        null);
  }

  private void record(WebhookDelivery delivery, WebhookAttempt attempt, WebhookPayload sent) {
    audit.record(
        new AuditRecord(
            "aud_" + ids.hex(ID_LENGTH),
            delivery.testRunId(),
            null,
            delivery.provider(),
            delivery.merchantId(),
            ObjectOrigin.SCHEDULED_JOB,
            AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT,
            outcomeOf(attempt.verdict()),
            "POST",
            attempt.targetUrl(),
            attempt.httpStatus(),
            delivery.providerObjectId(),
            null,
            delivery.shapedBy(),
            attempt.number(),
            attempt.attemptedAt(),
            sent.wireHeaders(),
            sent.sanitizedBody(),
            null));
  }

  private void recordTheDeliveryTheProviderWouldNotSend(
      WebhookDelivery undeliverable, Instant now, @Nullable String targetUrl) {
    audit.record(
        new AuditRecord(
            "aud_" + ids.hex(ID_LENGTH),
            undeliverable.testRunId(),
            null,
            undeliverable.provider(),
            undeliverable.merchantId(),
            ObjectOrigin.SCHEDULED_JOB,
            AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT,
            ProviderOutcome.FAILURE,
            "POST",
            targetUrl,
            null,
            undeliverable.providerObjectId(),
            null,
            undeliverable.shapedBy(),
            undeliverable.attemptsMade(),
            now,
            undeliverable.payload().wireHeaders(),
            undeliverable.payload().sanitizedBody(),
            null));
  }

  private void recordTheDeliveryTheRuleWithheld(
      WebhookDelivery withheld, Instant now, String targetUrl) {
    audit.record(
        new AuditRecord(
            "aud_" + ids.hex(ID_LENGTH),
            withheld.testRunId(),
            null,
            withheld.provider(),
            withheld.merchantId(),
            ObjectOrigin.SCHEDULED_JOB,
            AuditDirection.OUTBOUND_WEBHOOK_DROPPED,
            null,
            "POST",
            targetUrl,
            null,
            withheld.providerObjectId(),
            null,
            withheld.shapedBy(),
            0,
            now,
            withheld.payload().wireHeaders(),
            withheld.payload().sanitizedBody(),
            null));
  }

  private static ProviderOutcome outcomeOf(Acknowledgement verdict) {
    return switch (verdict) {
      case ACKNOWLEDGED -> ProviderOutcome.SUCCESS;
      case REFUSED -> ProviderOutcome.FAILURE;
      case AWAITING_SEPARATE_CALL -> ProviderOutcome.PROCESSING;
    };
  }

  private AcknowledgementPolicy policyFor(ProviderId provider) {
    AcknowledgementPolicy policy = policies.get(provider);
    if (policy == null) {
      throw new IllegalStateException(
          "no acknowledgement policy is registered for provider " + provider.value());
    }
    return policy;
  }

  private static @Nullable Integer statusOf(MerchantResponse response) {
    return response instanceof MerchantResponse.Answered answered ? answered.httpStatus() : null;
  }

  private static @Nullable TransportFailure failureOf(MerchantResponse response) {
    return response instanceof MerchantResponse.NotAnswered silent ? silent.failure() : null;
  }

  private static @Nullable String previewOf(MerchantResponse response) {
    return response instanceof MerchantResponse.Answered answered ? answered.bodyPreview() : null;
  }
}
