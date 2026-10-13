package dev.marcinromanowski.pspsim.kernel.routing;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

public record RouteAlias(
    ProviderId provider, TestRunId testRunId, RouteAliasKey key, String from, String to) {}
