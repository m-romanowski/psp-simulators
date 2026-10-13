package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredCard(
    @Nullable String brand,
    @Nullable String last4,
    @Nullable Integer expMonth,
    @Nullable Integer expYear,
    @Nullable String funding) {}
