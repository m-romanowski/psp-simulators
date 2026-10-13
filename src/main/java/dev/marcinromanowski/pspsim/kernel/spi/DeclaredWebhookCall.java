package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredWebhookCall(
    @Nullable String testRunId,
    @Nullable String merchantId,
    @Nullable String providerObjectId,
    @Nullable String eventType,
    @Nullable String operation,
    @Nullable Long delayMs,
    @Nullable String reuseEventId,
    @Nullable Integer attemptOverride,
    @Nullable String webhookJobId) {

  public static final String WEBHOOK_JOB_ID = "webhookJobId";

  public static final String THE_CALLBACK_IT_ASKED_THIS_PROVIDER_TO_SCHEDULE =
      "the callback this request asked this provider to schedule";
}
