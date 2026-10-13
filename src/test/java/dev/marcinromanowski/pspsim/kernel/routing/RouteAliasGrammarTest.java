package dev.marcinromanowski.pspsim.kernel.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.junit.jupiter.api.Test;

class RouteAliasGrammarTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final TestRunId RUN = TestRunId.of("route-alias-grammar");

  private static final String PROPERTY = "routeAliases[0]";

  private static final String FROM = PROPERTY + ".from";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  @Test
  void a_production_shaped_url_is_bound_to_the_host_and_the_path_the_application_will_send() {
    RouteAlias alias =
        RouteAliasGrammar.aliasOf(
            GROW, RUN, PROPERTY, "https://api.provider.example/v2/merchant-1/payments", CREATE);

    assertThat(alias.key())
        .isEqualTo(new RouteAliasKey("api.provider.example", "/v2/merchant-1/payments"));
    assertThat(alias.from()).isEqualTo("https://api.provider.example/v2/merchant-1/payments");
    assertThat(alias.to()).isEqualTo(CREATE);
  }

  @Test
  void a_host_the_application_spells_in_capitals_binds_the_same_url_as_one_spelled_in_lower_case() {
    assertThat(RouteAliasGrammar.keyOf(FROM, "https://API.Provider.Example/payments"))
        .isEqualTo(RouteAliasGrammar.keyOf(FROM, "https://api.provider.example/payments"));
  }

  @Test
  void the_path_is_bound_as_it_travels_on_the_wire_rather_than_decoded_first() {
    assertThat(RouteAliasGrammar.keyOf(FROM, "https://api.provider.example/a%2Fb").path())
        .isEqualTo("/a%2Fb");
  }

  @Test
  void a_url_that_names_no_host_is_refused_by_name_rather_than_bound_to_nothing() {
    assertThatThrownBy(() -> RouteAliasGrammar.keyOf(FROM, "/v2/merchant-1/payments"))
        .hasMessage("routeAliases[0].from must be an absolute URL carrying a scheme and a host");
  }

  @Test
  void a_url_this_simulator_is_never_reached_over_is_refused_by_name() {
    assertThatThrownBy(() -> RouteAliasGrammar.keyOf(FROM, "ftp://api.provider.example/pay"))
        .hasMessage("routeAliases[0].from must use the http or https scheme");
  }

  @Test
  void a_url_carrying_a_credential_before_its_host_is_refused_without_repeating_it() {
    assertThatThrownBy(
            () -> RouteAliasGrammar.keyOf(FROM, "https://user:secret@api.provider.example/pay"))
        .hasMessage("routeAliases[0].from must carry no user information before its host")
        .hasMessageNotContaining("secret");
  }

  @Test
  void a_url_carrying_a_query_string_is_refused_because_a_request_chooses_its_own() {
    assertThatThrownBy(() -> RouteAliasGrammar.keyOf(FROM, "https://api.provider.example/p?a=1"))
        .hasMessage("routeAliases[0].from must carry no query string and no fragment");
    assertThatThrownBy(() -> RouteAliasGrammar.keyOf(FROM, "https://api.provider.example/p#a"))
        .hasMessage("routeAliases[0].from must carry no query string and no fragment");
  }

  @Test
  void a_bare_host_is_refused_because_an_alias_binds_one_url_and_not_a_whole_base() {
    assertThatThrownBy(() -> RouteAliasGrammar.keyOf(FROM, "https://api.provider.example"))
        .hasMessageStartingWith("routeAliases[0].from must name the path the application calls");
  }

  @Test
  void a_url_longer_than_the_store_can_key_is_refused_at_configuration_time() {
    String tooLong = "https://api.provider.example/" + "p".repeat(RouteAliasGrammar.LONGEST_KEY);

    assertThatThrownBy(() -> RouteAliasGrammar.keyOf(FROM, tooLong))
        .hasMessage(
            "routeAliases[0].from must name a host and a path of at most 512 characters together");
  }

  @Test
  void a_url_exactly_as_long_as_the_store_can_key_is_bound() {
    String host = "api.provider.example";
    String path = "/" + "p".repeat(RouteAliasGrammar.LONGEST_KEY - host.length() - 1);

    assertThat(RouteAliasGrammar.keyOf(FROM, "https://" + host + path).length())
        .isEqualTo(RouteAliasGrammar.LONGEST_KEY);
  }

  @Test
  void a_target_that_is_not_a_route_of_this_simulator_is_refused_by_name() {
    assertThatThrownBy(
            () ->
                RouteAliasGrammar.aliasOf(
                    GROW, RUN, PROPERTY, "https://api.provider.example/pay", "pay"))
        .hasMessage("routeAliases[0].to must name a route of this simulator, beginning with '/'");
    assertThatThrownBy(
            () ->
                RouteAliasGrammar.aliasOf(
                    GROW,
                    RUN,
                    PROPERTY,
                    "https://api.provider.example/pay",
                    "//elsewhere.example/pay"))
        .hasMessage("routeAliases[0].to must name a route of this simulator, beginning with '/'");
  }

  @Test
  void a_target_inside_the_simulators_own_namespace_is_refused_so_no_alias_can_reset_a_test_run() {
    assertThatThrownBy(
            () ->
                RouteAliasGrammar.aliasOf(
                    GROW,
                    RUN,
                    PROPERTY,
                    "https://api.provider.example/pay",
                    "/__simulator/grow/reset"))
        .hasMessageStartingWith("routeAliases[0].to must name a provider route");
    assertThatThrownBy(
            () ->
                RouteAliasGrammar.aliasOf(
                    GROW, RUN, PROPERTY, "https://api.provider.example/pay", "/ui/configuration"))
        .hasMessageStartingWith("routeAliases[0].to must name a provider route");
  }

  @Test
  void a_provider_route_that_merely_begins_with_the_letters_of_a_reserved_one_is_bound() {
    assertThat(
            RouteAliasGrammar.aliasOf(
                    GROW, RUN, PROPERTY, "https://api.provider.example/pay", "/uiservice/pay")
                .to())
        .isEqualTo("/uiservice/pay");
    assertThat(RouteAliasGrammar.namesTheSimulatorsOwnNamespace("/uiservice/pay")).isFalse();
    assertThat(RouteAliasGrammar.namesTheSimulatorsOwnNamespace("/ui")).isTrue();
    assertThat(RouteAliasGrammar.namesTheSimulatorsOwnNamespace("/actuator/health")).isTrue();
  }

  @Test
  void a_target_carrying_a_query_string_is_refused_because_the_request_brings_its_own() {
    assertThatThrownBy(
            () ->
                RouteAliasGrammar.aliasOf(
                    GROW, RUN, PROPERTY, "https://api.provider.example/pay", "/pay?a=1"))
        .hasMessage("routeAliases[0].to must carry no query string and no fragment");
  }

  @Test
  void a_target_longer_than_the_store_can_hold_is_refused_at_configuration_time() {
    String tooLong = "/" + "p".repeat(RouteAliasGrammar.LONGEST_KEY);

    assertThatThrownBy(
            () ->
                RouteAliasGrammar.aliasOf(
                    GROW, RUN, PROPERTY, "https://api.provider.example/pay", tooLong))
        .hasMessage("routeAliases[0].to must be at most 512 characters long");
  }

  @Test
  void neither_side_of_an_alias_may_be_something_this_simulator_cannot_read_as_a_url() {
    assertThatThrownBy(() -> RouteAliasGrammar.keyOf(FROM, "https://api provider/pay"))
        .hasMessage("routeAliases[0].from is not a URL this simulator can read");
    assertThatThrownBy(
            () ->
                RouteAliasGrammar.aliasOf(
                    GROW, RUN, PROPERTY, "https://api.provider.example/pay", "/a b"))
        .hasMessage("routeAliases[0].to is not a URL this simulator can read");
  }

  @Test
  void the_host_a_request_arrived_on_is_read_from_the_proxy_header_when_a_proxy_sent_one() {
    assertThat(RouteAliasGrammar.hostOf("api.provider.example", "localhost:8080"))
        .isEqualTo("api.provider.example");
    assertThat(RouteAliasGrammar.hostOf(null, "api.provider.example"))
        .isEqualTo("api.provider.example");
    assertThat(RouteAliasGrammar.hostOf("  ", "api.provider.example"))
        .isEqualTo("api.provider.example");
  }

  @Test
  void the_port_the_simulator_happens_to_listen_on_is_not_part_of_the_host_an_alias_binds() {
    assertThat(RouteAliasGrammar.hostOf(null, "api.provider.example:8080"))
        .isEqualTo("api.provider.example");
    assertThat(RouteAliasGrammar.hostOf(null, "[2001:db8::1]:8443")).isEqualTo("[2001:db8::1]");
    assertThat(RouteAliasGrammar.hostOf(null, "[2001:db8::1]")).isEqualTo("[2001:db8::1]");
  }

  @Test
  void only_the_nearest_proxy_of_a_chain_decides_the_host_and_the_scheme() {
    assertThat(RouteAliasGrammar.hostOf("api.provider.example, gateway.internal", null))
        .isEqualTo("api.provider.example");
    assertThat(RouteAliasGrammar.schemeOf("https, http", "http")).isEqualTo("https");
  }

  @Test
  void a_forwarded_header_whose_nearest_proxy_named_nothing_names_no_host_and_no_scheme() {
    assertThat(RouteAliasGrammar.authorityOf(",api.provider.example", null)).isNull();
    assertThat(RouteAliasGrammar.schemeOf(",https", "http")).isEqualTo("http");
  }

  @Test
  void a_target_exactly_as_long_as_the_store_can_hold_is_bound() {
    String longest = "/" + "p".repeat(RouteAliasGrammar.LONGEST_KEY - 1);

    assertThat(
            RouteAliasGrammar.aliasOf(
                    GROW, RUN, PROPERTY, "https://api.provider.example/pay", longest)
                .to())
        .hasSize(RouteAliasGrammar.LONGEST_KEY);
  }

  @Test
  void a_request_that_names_no_host_at_all_binds_no_alias() {
    assertThat(RouteAliasGrammar.hostOf(null, null)).isNull();
    assertThat(RouteAliasGrammar.hostOf(null, "  ")).isNull();
    assertThat(RouteAliasGrammar.hostOf(null, ":8080")).isNull();
    assertThat(RouteAliasGrammar.authorityOf(null, null)).isNull();
    assertThat(RouteAliasGrammar.authorityOf(" , ", null)).isNull();
  }

  @Test
  void the_url_the_trail_records_is_the_one_the_application_sent_including_its_port() {
    assertThat(
            RouteAliasGrammar.calledUrlOf(
                RouteAliasGrammar.schemeOf(null, "http"),
                RouteAliasGrammar.authorityOf(null, "api.provider.example:8080"),
                "/v2/merchant-1/payments"))
        .isEqualTo("http://api.provider.example:8080/v2/merchant-1/payments");
  }

  @Test
  void the_scheme_a_proxy_terminated_is_the_one_the_trail_records() {
    assertThat(RouteAliasGrammar.schemeOf("https", "http")).isEqualTo("https");
    assertThat(RouteAliasGrammar.schemeOf(null, "http")).isEqualTo("http");
    assertThat(RouteAliasGrammar.schemeOf("  ", "http")).isEqualTo("http");
    assertThat(RouteAliasGrammar.schemeOf("HTTPS", "http")).isEqualTo("https");
  }
}
