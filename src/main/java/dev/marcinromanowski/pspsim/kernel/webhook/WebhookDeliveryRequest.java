package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record WebhookDeliveryRequest(
    ProviderId provider,
    TestRunId testRunId,
    String merchantId,
    @Nullable String eventType,
    @Nullable String providerObjectId,
    String targetUrl,
    WebhookPayload payload,
    @Nullable String eventId,
    @Nullable RetryPlan retryPlan,
    @Nullable Duration delay) {}
