package dev.marcinromanowski.pspsim.kernel.routing;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

public interface RouteAliases {

  void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> aliases);

  List<RouteAlias> of(ProviderId provider, TestRunId testRunId);

  boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key);

  Optional<RouteAlias> boundTo(RouteAliasKey key);
}
