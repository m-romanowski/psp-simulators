package dev.marcinromanowski.pspsim.kernel.scenario;

import org.jspecify.annotations.Nullable;

public record DeclaredAction(
    @Nullable String type,
    @Nullable Integer failuresBeforeSuccess,
    @Nullable String providerCode,
    @Nullable String fault,
    @Nullable Integer httpStatus,
    @Nullable String terminalStatus,
    @Nullable Long delayMs,
    @Nullable Long settlesAfterMs) {}
