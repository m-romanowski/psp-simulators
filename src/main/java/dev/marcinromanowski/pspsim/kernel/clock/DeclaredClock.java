package dev.marcinromanowski.pspsim.kernel.clock;

import org.jspecify.annotations.Nullable;

public record DeclaredClock(
    @Nullable String fixedNow, @Nullable String timezone, @Nullable Integer maxSkewSeconds) {}
