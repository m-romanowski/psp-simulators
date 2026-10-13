package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record DeclaredObjectQuery(
    @Nullable String testRunId, @Nullable String after, @Nullable String limit) {}
