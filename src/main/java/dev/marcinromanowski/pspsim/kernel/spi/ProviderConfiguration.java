package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;

public record ProviderConfiguration(
    TestRunId testRunId,
    List<ConfiguredAccount> accounts,
    List<WebhookTarget> webhookTargets,
    List<ConfiguredRouteAlias> routeAliases,
    Map<String, String> settings) {}
