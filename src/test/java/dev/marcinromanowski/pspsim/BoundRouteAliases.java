package dev.marcinromanowski.pspsim;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BoundRouteAliases implements RouteAliases {

  public static final String IS_HELD_BY_ANOTHER_TEST_RUN =
      " names a URL this simulator has bound to another test run";

  private final Map<String, RouteAlias> bound = new LinkedHashMap<>();

  @Override
  public void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> aliases) {
    for (int index = 0; index < aliases.size(); index++) {
      RouteAlias alias = aliases.get(index);
      RouteAlias held = bound.get(alias.key().value());
      if (held != null
          && !(held.provider().equals(provider) && held.testRunId().equals(testRunId))) {
        String from = "routeAliases[" + index + "].from";
        throw new ClaimedMeanwhileException(from, from + IS_HELD_BY_ANOTHER_TEST_RUN);
      }
      bound.put(alias.key().value(), alias);
    }
  }

  @Override
  public boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key) {
    RouteAlias held = bound.get(key.value());
    if (held == null || !held.provider().equals(provider) || !held.testRunId().equals(testRunId)) {
      return false;
    }
    bound.remove(key.value());
    return true;
  }

  @Override
  public List<RouteAlias> of(ProviderId provider, TestRunId testRunId) {
    List<RouteAlias> held = new ArrayList<>();
    for (RouteAlias alias : bound.values()) {
      if (alias.provider().equals(provider) && alias.testRunId().equals(testRunId)) {
        held.add(alias);
      }
    }
    held.sort((left, right) -> left.key().value().compareTo(right.key().value()));
    return List.copyOf(held);
  }

  @Override
  public Optional<RouteAlias> boundTo(RouteAliasKey key) {
    return Optional.ofNullable(bound.get(key.value()));
  }
}
