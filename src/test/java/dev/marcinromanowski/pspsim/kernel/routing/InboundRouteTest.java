package dev.marcinromanowski.pspsim.kernel.routing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class InboundRouteTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final ProviderId STRIPE = ProviderId.of("stripe");

  private static final TestRunId RUN = TestRunId.of("inbound-route");

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  @Test
  void an_alias_hands_the_resolver_the_one_test_run_it_binds() {
    InboundRoute route =
        InboundRoute.aliased("https://api.provider.example/pay", alias(GROW, "/pay"));

    assertThat(route.candidatesFor(GROW).rule()).isEqualTo(TestRunResolutionRule.ROUTE_ALIAS);
    assertThat(route.candidatesFor(GROW).testRunIds()).containsExactly(RUN);
    assertThat(route.candidatesFor(GROW).matchedNothing()).isFalse();
  }

  @Test
  void a_request_no_alias_matched_leaves_the_alias_rule_to_defer_to_the_next_one() {
    InboundRoute route = InboundRoute.notAliased(CREATE);

    assertThat(route.calledUrl()).isEqualTo(CREATE);
    assertThat(route.candidatesFor(GROW).evaluated()).isTrue();
    assertThat(route.candidatesFor(GROW).matchedNothing()).isTrue();
  }

  @Test
  void an_alias_configured_under_one_provider_resolves_no_request_of_another() {
    InboundRoute route =
        InboundRoute.aliased("https://api.provider.example/pay", alias(GROW, "/pay"));

    assertThat(route.candidatesFor(STRIPE).matchedNothing()).isTrue();
  }

  @Test
  void a_request_the_simulator_never_aliased_reports_the_path_it_arrived_on() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", CREATE);

    assertThat(InboundRoute.of(request).calledUrl()).isEqualTo(CREATE);
    assertThat(InboundRoute.of(request).alias()).isNull();
  }

  @Test
  void a_request_an_alias_rewrote_reports_the_url_the_application_called() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", CREATE);
    request.setAttribute(
        InboundRoute.ATTRIBUTE,
        InboundRoute.aliased("https://api.provider.example/pay", alias(GROW, CREATE)));

    assertThat(InboundRoute.of(request).calledUrl()).isEqualTo("https://api.provider.example/pay");
  }

  @Test
  void an_attribute_that_is_not_an_inbound_route_is_ignored_rather_than_trusted() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", CREATE);
    request.setAttribute(InboundRoute.ATTRIBUTE, "https://elsewhere.example/pay");

    assertThat(InboundRoute.of(request).calledUrl()).isEqualTo(CREATE);
  }

  private static RouteAlias alias(ProviderId provider, String to) {
    return new RouteAlias(
        provider,
        RUN,
        new RouteAliasKey("api.provider.example", "/pay"),
        "https://api.provider.example/pay",
        to);
  }
}
