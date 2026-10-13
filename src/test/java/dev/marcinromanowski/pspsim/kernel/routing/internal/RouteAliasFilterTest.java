package dev.marcinromanowski.pspsim.kernel.routing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RouteAliasFilterTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final TestRunId RUN = TestRunId.of("route-alias-filter");

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  private static final String PRODUCTION_PATH = "/v2/merchant-1/payments";

  private final BoundRouteAliases aliases = new BoundRouteAliases();

  @Test
  void a_request_on_the_aliased_host_and_path_is_served_as_the_route_the_alias_names()
      throws IOException, ServletException {
    bind("https://api.provider.example" + PRODUCTION_PATH, CREATE);

    HttpServletRequest served = through(request(PRODUCTION_PATH, "api.provider.example"));

    assertThat(served.getRequestURI()).isEqualTo(CREATE);
    assertThat(served.getServletPath()).isEqualTo(CREATE);
  }

  @Test
  void the_trail_of_an_aliased_request_reads_the_url_the_application_called()
      throws IOException, ServletException {
    bind("https://api.provider.example" + PRODUCTION_PATH, CREATE);

    HttpServletRequest served = through(request(PRODUCTION_PATH, "api.provider.example"));

    assertThat(InboundRoute.of(served).calledUrl())
        .isEqualTo("http://api.provider.example" + PRODUCTION_PATH);
    assertThat(InboundRoute.of(served).candidatesFor(GROW).testRunIds()).containsExactly(RUN);
  }

  @Test
  void a_proxy_that_terminated_tls_decides_the_host_and_the_scheme_the_trail_records()
      throws IOException, ServletException {
    bind("https://api.provider.example" + PRODUCTION_PATH, CREATE);

    MockHttpServletRequest arriving = request(PRODUCTION_PATH, "simulator.internal:8080");
    arriving.addHeader(RouteAliasGrammar.FORWARDED_HOST, "api.provider.example");
    arriving.addHeader(RouteAliasGrammar.FORWARDED_PROTO, "https");

    HttpServletRequest served = through(arriving);

    assertThat(served.getRequestURI()).isEqualTo(CREATE);
    assertThat(InboundRoute.of(served).calledUrl())
        .isEqualTo("https://api.provider.example" + PRODUCTION_PATH);
  }

  @Test
  void the_port_the_simulator_listens_on_does_not_stop_an_alias_from_matching()
      throws IOException, ServletException {
    bind("https://api.provider.example" + PRODUCTION_PATH, CREATE);

    HttpServletRequest served = through(request(PRODUCTION_PATH, "api.provider.example:8080"));

    assertThat(served.getRequestURI()).isEqualTo(CREATE);
    assertThat(InboundRoute.of(served).calledUrl())
        .isEqualTo("http://api.provider.example:8080" + PRODUCTION_PATH);
  }

  @Test
  void a_request_on_a_path_no_alias_names_is_served_and_recorded_exactly_as_it_arrived()
      throws IOException, ServletException {
    bind("https://api.provider.example" + PRODUCTION_PATH, CREATE);

    HttpServletRequest served = through(request("/v2/merchant-2/payments", "api.provider.example"));

    assertThat(served.getRequestURI()).isEqualTo("/v2/merchant-2/payments");
    assertThat(InboundRoute.of(served).calledUrl()).isEqualTo("/v2/merchant-2/payments");
    assertThat(InboundRoute.of(served).candidatesFor(GROW).matchedNothing()).isTrue();
  }

  @Test
  void a_request_on_a_host_no_alias_names_is_served_exactly_as_it_arrived()
      throws IOException, ServletException {
    bind("https://api.provider.example" + PRODUCTION_PATH, CREATE);

    HttpServletRequest served = through(request(PRODUCTION_PATH, "localhost:8080"));

    assertThat(served.getRequestURI()).isEqualTo(PRODUCTION_PATH);
    assertThat(InboundRoute.of(served).alias()).isNull();
  }

  @Test
  void a_request_that_carries_no_host_at_all_is_served_exactly_as_it_arrived()
      throws IOException, ServletException {
    bind("https://api.provider.example" + PRODUCTION_PATH, CREATE);

    MockHttpServletRequest arriving = new MockHttpServletRequest("POST", PRODUCTION_PATH);

    assertThat(through(arriving).getRequestURI()).isEqualTo(PRODUCTION_PATH);
  }

  @Test
  void the_control_api_and_the_screens_are_reached_by_their_own_routes_and_never_by_an_alias()
      throws IOException, ServletException {
    aliases.merge(
        GROW,
        RUN,
        List.of(
            new RouteAlias(
                GROW,
                RUN,
                RouteAliasGrammar.keyOf("from", "https://localhost/__simulator/grow/reset"),
                "https://localhost/__simulator/grow/reset",
                CREATE)));

    HttpServletRequest served = through(request("/__simulator/grow/reset", "localhost"));

    assertThat(served.getRequestURI()).isEqualTo("/__simulator/grow/reset");
  }

  @Test
  void
      an_alias_whose_route_is_the_path_the_application_already_calls_binds_the_run_and_nothing_else()
          throws IOException, ServletException {
    bind("https://api.provider.example" + CREATE, CREATE);

    HttpServletRequest served = through(request(CREATE, "api.provider.example"));

    assertThat(served.getRequestURI()).isEqualTo(CREATE);
    assertThat(InboundRoute.of(served).candidatesFor(GROW).testRunIds()).containsExactly(RUN);
  }

  @Test
  void a_health_probe_asks_the_alias_store_nothing_so_a_database_failure_cannot_answer_it()
      throws IOException, ServletException {
    RouteAliases unreachable =
        new RouteAliases() {
          @Override
          public void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> aliases) {
            throw new IllegalStateException("the store was asked");
          }

          @Override
          public boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key) {
            throw new IllegalStateException("the store was asked");
          }

          @Override
          public List<RouteAlias> of(ProviderId provider, TestRunId testRunId) {
            throw new IllegalStateException("the store was asked");
          }

          @Override
          public Optional<RouteAlias> boundTo(RouteAliasKey key) {
            throw new IllegalStateException("the store was asked");
          }
        };
    MockHttpServletRequest probe = request("/actuator/health/readiness", "localhost:8080");
    MockFilterChain chain = new MockFilterChain();

    new RouteAliasFilter(unreachable).doFilter(probe, new MockHttpServletResponse(), chain);

    assertThat(((HttpServletRequest) chain.getRequest()).getRequestURI())
        .isEqualTo("/actuator/health/readiness");
  }

  private void bind(String from, String to) {
    aliases.merge(
        GROW, RUN, List.of(RouteAliasGrammar.aliasOf(GROW, RUN, "routeAliases[0]", from, to)));
  }

  private HttpServletRequest through(MockHttpServletRequest arriving)
      throws IOException, ServletException {
    MockFilterChain chain = new MockFilterChain();
    new RouteAliasFilter(aliases).doFilter(arriving, new MockHttpServletResponse(), chain);
    return (HttpServletRequest) chain.getRequest();
  }

  private static MockHttpServletRequest request(String path, String host) {
    MockHttpServletRequest arriving = new MockHttpServletRequest("POST", path);
    arriving.addHeader(RouteAliasGrammar.HOST, host);
    return arriving;
  }
}
