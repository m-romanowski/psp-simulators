package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DeclaredConfiguration(
    @Nullable String testRunId,
    @Nullable DeclaredClock clock,
    @Nullable List<DeclaredAccount> accounts,
    @Nullable List<DeclaredWebhookTarget> webhooks,
    @Nullable List<DeclaredRouteAlias> routeAliases,
    @Nullable Map<String, String> settings) {}
