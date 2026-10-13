package dev.marcinromanowski.pspsim.kernel.clock;

import org.jspecify.annotations.Nullable;

public record DeclaredAdvance(@Nullable String testRunId, @Nullable String advanceBy) {}
