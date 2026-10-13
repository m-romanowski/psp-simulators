package dev.marcinromanowski.pspsim.kernel.scenario;

import org.jspecify.annotations.Nullable;

public record DeclaredPreset(
    @Nullable String testRunId,
    @Nullable String preset,
    @Nullable Integer priority,
    @Nullable DeclaredMatch match) {}
