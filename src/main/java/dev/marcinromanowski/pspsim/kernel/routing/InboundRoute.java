package dev.marcinromanowski.pspsim.kernel.routing;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunCandidates;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record InboundRoute(String calledUrl, @Nullable RouteAlias alias) {

  public static final String ATTRIBUTE = InboundRoute.class.getName();

  public static InboundRoute of(HttpServletRequest request) {
    Object aliased = request.getAttribute(ATTRIBUTE);
    if (aliased instanceof InboundRoute route) {
      return route;
    }
    return notAliased(request.getRequestURI());
  }

  public static InboundRoute notAliased(String requestUri) {
    return new InboundRoute(requestUri, null);
  }

  public static InboundRoute aliased(String calledUrl, RouteAlias alias) {
    return new InboundRoute(calledUrl, alias);
  }

  public TestRunCandidates candidatesFor(ProviderId provider) {
    return TestRunCandidates.of(TestRunResolutionRule.ROUTE_ALIAS, boundTestRuns(provider));
  }

  private List<TestRunId> boundTestRuns(ProviderId provider) {
    RouteAlias bound = alias;
    if (bound == null || !bound.provider().equals(provider)) {
      return List.of();
    }
    return List.of(bound.testRunId());
  }
}
