package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

public record NewScenarioRule(
    ProviderId provider,
    TestRunId testRunId,
    int priority,
    ScenarioMatch match,
    ScenarioAction action,
    @Nullable ScenarioStory story) {}
