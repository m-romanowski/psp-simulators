package dev.marcinromanowski.pspsim.kernel.scenario;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DeclaredMatch(
    @Nullable String merchantId,
    @Nullable String orderId,
    @Nullable String providerObjectId,
    @Nullable String customerId,
    @Nullable String paymentMethodId,
    @Nullable String amount,
    @Nullable String currency,
    @Nullable String operation,
    @Nullable String eventType,
    @Nullable Map<String, String> header) {}
