package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DeclaredPaymentMethodFixture(
    @Nullable String testRunId,
    @Nullable String merchantId,
    @Nullable String ownerId,
    @Nullable String type,
    @Nullable String alias,
    @Nullable DeclaredCard card,
    @Nullable Map<String, String> providerIds,
    @Nullable String defaultScenario) {}
