package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record DeliveryFacts(
    ProviderId provider,
    TestRunId testRunId,
    String merchantId,
    @Nullable String providerObjectId,
    @Nullable String eventType) {}
