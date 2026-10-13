package dev.marcinromanowski.pspsim.kernel.scenario;

import org.jspecify.annotations.Nullable;

public record DeclaredScenario(
    @Nullable String testRunId,
    @Nullable Integer priority,
    @Nullable String story,
    @Nullable String keyedOn,
    @Nullable String whenState,
    @Nullable String thenState,
    @Nullable DeclaredMatch match,
    @Nullable DeclaredAction action) {}
