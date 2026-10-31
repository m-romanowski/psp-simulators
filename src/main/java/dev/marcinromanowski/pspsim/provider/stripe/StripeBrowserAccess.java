package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

final class StripeBrowserAccess implements Filter {

  static final String OPTIONS = "OPTIONS";

  static final String ORIGIN = "Origin";

  static final String REQUEST_METHOD = "Access-Control-Request-Method";

  static final String REQUEST_HEADERS = "Access-Control-Request-Headers";

  static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";

  static final int PREFLIGHT_ANSWERED = 200;

  static final SequencedMap<String, String> WHAT_EVERY_ANSWER_CARRIES = whatEveryAnswerCarries();

  private final ProviderWireRoutes routes;

  StripeBrowserAccess(ProviderWireRoutes routes) {
    this.routes = routes;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest inbound)
        || !(response instanceof HttpServletResponse outbound)
        || routes.howDeeplyItCovers(inbound.getRequestURI()) == ProviderWireRoutes.COVERS_NOTHING) {
      chain.doFilter(request, response);
      return;
    }
    Map<String, String> answered = theHeadersOf(inbound);
    answered.forEach(outbound::setHeader);
    if (aPreflight(inbound)) {
      outbound.setStatus(PREFLIGHT_ANSWERED);
      outbound.setContentLength(0);
      return;
    }
    chain.doFilter(request, response);
  }

  static Map<String, String> theHeadersOf(HttpServletRequest inbound) {
    Map<String, String> headers = new LinkedHashMap<>(WHAT_EVERY_ANSWER_CARRIES);
    String asked = inbound.getHeader(REQUEST_HEADERS);
    if (aPreflight(inbound) && asked != null && !asked.isBlank()) {
      headers.put(ALLOW_HEADERS, asked.strip());
    }
    return headers;
  }

  static boolean aPreflight(HttpServletRequest inbound) {
    return OPTIONS.equalsIgnoreCase(inbound.getMethod())
        && inbound.getHeader(ORIGIN) != null
        && inbound.getHeader(REQUEST_METHOD) != null;
  }

  private static SequencedMap<String, String> whatEveryAnswerCarries() {
    LinkedHashMap<String, String> carried = new LinkedHashMap<>();
    carried.put("Access-Control-Allow-Origin", "*");
    carried.put("Access-Control-Allow-Credentials", "true");
    carried.put("Access-Control-Allow-Methods", "GET, HEAD, PUT, PATCH, POST, DELETE");
    carried.put(
        "Access-Control-Expose-Headers",
        String.join(
            ", ",
            List.of(
                "Request-Id",
                "Stripe-Manage-Version",
                "Stripe-Should-Retry",
                "X-Stripe-External-Auth-Required",
                "X-Stripe-Privileged-Session-Required")));
    carried.put("Access-Control-Max-Age", "300");
    carried.put("Vary", ORIGIN);
    return Collections.unmodifiableSequencedMap(carried);
  }
}
