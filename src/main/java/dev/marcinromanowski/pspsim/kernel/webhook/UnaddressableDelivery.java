package dev.marcinromanowski.pspsim.kernel.webhook;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record UnaddressableDelivery(
    ProviderId provider,
    TestRunId testRunId,
    String merchantId,
    @Nullable String eventType,
    @Nullable String providerObjectId,
    WebhookPayload payload,
    String notSentBecause) {}
