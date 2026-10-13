package dev.marcinromanowski.pspsim.kernel.routing.internal;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class RouteAliasFilter implements Filter {

  private final RouteAliases aliases;

  public RouteAliasFilter(RouteAliases aliases) {
    this.aliases = aliases;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    chain.doFilter(
        request instanceof HttpServletRequest inbound ? servedAs(inbound) : request, response);
  }

  private HttpServletRequest servedAs(HttpServletRequest inbound) {
    String requestUri = inbound.getRequestURI();
    if (RouteAliasGrammar.namesTheSimulatorsOwnNamespace(requestUri)) {
      return inbound;
    }
    String authority =
        RouteAliasGrammar.authorityOf(
            inbound.getHeader(RouteAliasGrammar.FORWARDED_HOST),
            inbound.getHeader(RouteAliasGrammar.HOST));
    String host =
        RouteAliasGrammar.hostOf(
            inbound.getHeader(RouteAliasGrammar.FORWARDED_HOST),
            inbound.getHeader(RouteAliasGrammar.HOST));
    if (authority == null || host == null) {
      return inbound;
    }
    Optional<RouteAlias> bound = aliases.boundTo(new RouteAliasKey(host, requestUri));
    if (bound.isEmpty()) {
      return inbound;
    }
    RouteAlias alias = bound.get();
    String scheme =
        RouteAliasGrammar.schemeOf(
            inbound.getHeader(RouteAliasGrammar.FORWARDED_PROTO), inbound.getScheme());
    inbound.setAttribute(
        InboundRoute.ATTRIBUTE,
        InboundRoute.aliased(RouteAliasGrammar.calledUrlOf(scheme, authority, requestUri), alias));
    return new AliasedRequest(inbound, alias.to());
  }

  private static final class AliasedRequest extends HttpServletRequestWrapper {

    private final String route;

    private AliasedRequest(HttpServletRequest aliased, String route) {
      super(aliased);
      this.route = route;
    }

    @Override
    public String getRequestURI() {
      return route;
    }

    @Override
    public @Nullable String getServletPath() {
      return route;
    }
  }
}
