package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import org.jspecify.annotations.Nullable;

record StripeStoredAnswer(
    int httpStatus,
    String contentType,
    String body,
    ProviderOutcome outcome,
    @Nullable String providerObjectId) {}
