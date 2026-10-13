package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredPersonaSeeding(
    @Nullable String testRunId, @Nullable String persona, @Nullable String merchantId) {}
