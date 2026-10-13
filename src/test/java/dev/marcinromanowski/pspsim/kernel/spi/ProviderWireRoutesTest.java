package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderWireRoutesTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  @Test
  void an_address_inside_a_namespace_belongs_to_it_and_one_that_only_starts_like_it_does_not() {
    ProviderWireRoutes routes = new ProviderWireRoutes(GROW, List.of("/token", "/api/light"));

    assertThat(routes.covers("/token")).isTrue();
    assertThat(routes.covers("/token/cgi_link")).isTrue();
    assertThat(routes.covers("/api/light/server/1.0/createPaymentProcess")).isTrue();
    assertThat(routes.covers("/tokenize/sessions/abc"))
        .as(
            "two providers of this build own addresses that begin with the same letters, and a"
                + " plain prefix match would hand one provider's address to the other, whose"
                + " envelope and status are not the same")
        .isFalse();
    assertThat(routes.covers("/tokens")).isFalse();
    assertThat(routes.covers("/api/lightly")).isFalse();
    assertThat(routes.covers("/ui/objects")).isFalse();
    assertThat(routes.covers("/")).isFalse();
    assertThat(routes.covers("")).isFalse();
  }

  @Test
  void a_namespace_that_already_ends_in_a_separator_covers_what_is_under_it_and_itself() {
    ProviderWireRoutes routes =
        new ProviderWireRoutes(GROW, List.of("/hostedtokenization/tokenization/form/"));

    assertThat(routes.covers("/hostedtokenization/tokenization/form/")).isTrue();
    assertThat(routes.covers("/hostedtokenization/tokenization/form/abc")).isTrue();
    assertThat(routes.covers("/hostedtokenization/tokenization/formed")).isFalse();
  }

  @Test
  void a_namespace_that_is_not_an_absolute_path_under_this_provider_alone_is_refused() {
    assertThatThrownBy(() -> new ProviderWireRoutes(GROW, List.of("api/light")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("api/light");
    assertThatCode(() -> new ProviderWireRoutes(GROW, List.of("/")))
        .as("a namespace of / would claim every address of this application, the screens included")
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProviderWireRoutes(GROW, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProviderWireRoutes(GROW, List.of("/api/light", "/api/light")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("twice");
  }

  @Test
  void a_namespace_under_a_path_is_what_makes_that_path_reachable_only_through_this_provider() {
    ProviderWireRoutes routes =
        new ProviderWireRoutes(GROW, List.of("/api/light", "/far", "/v1/oauth"));

    assertThat(routes.theNamespacesUnder("/api")).containsExactly("/api/light");
    assertThat(routes.theNamespacesUnder("/api/light")).containsExactly("/api/light");
    assertThat(routes.theNamespacesUnder("/v1"))
        .as(
            "the path a mistyped second segment lands on is the one every namespace of this"
                + " provider under it has to be read off, and the namespaces of this provider"
                + " elsewhere are not under it")
        .containsExactly("/v1/oauth");
    assertThat(routes.theNamespacesUnder("/apix")).isEmpty();
    assertThat(routes.theNamespacesUnder("/api/lightly")).isEmpty();
    assertThat(routes.theNamespacesUnder("/far")).containsExactly("/far");
    assertThat(routes.theNamespacesUnder("/f")).isEmpty();
    assertThat(routes.theNamespacesUnder("/api/light/server")).isEmpty();
  }

  @Test
  void a_namespace_that_ends_in_a_separator_is_read_off_the_path_without_one_too() {
    ProviderWireRoutes routes =
        new ProviderWireRoutes(GROW, List.of("/hostedtokenization/tokenization/form/"));

    assertThat(routes.theNamespacesUnder("/hostedtokenization/tokenization/form"))
        .as(
            "the published form path ends in a separator, and the address a client reaches it"
                + " through without one is covered by nothing")
        .containsExactly("/hostedtokenization/tokenization/form/");
    assertThat(routes.theNamespacesUnder("/hostedtokenization/tokenization/form/"))
        .containsExactly("/hostedtokenization/tokenization/form/");
    assertThat(routes.theNamespacesUnder("/hostedtokenization/tokenization/formed")).isEmpty();
  }

  @Test
  void a_namespace_inside_an_address_this_simulator_serves_itself_is_refused() {
    for (String reserved :
        List.of(
            "/__simulator",
            "/__simulator/grow",
            "/__simulator/grow/audit",
            "/ui",
            "/ui/objects",
            "/actuator",
            "/actuator/health")) {
      assertThatCode(() -> new ProviderWireRoutes(ProviderId.of("sixth"), List.of("/v9", reserved)))
          .as(
              "the module whose namespace covers an address is asked what that address answers,"
                  + " and is asked before any surface of this simulator, so a module naming %s"
                  + " would hand a tester on the control api or on a screen a vendor envelope",
              reserved)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(reserved)
          .hasMessageContaining("this simulator serves itself");
    }
  }

  @Test
  void a_namespace_that_only_begins_like_one_this_simulator_serves_is_left_alone() {
    ProviderWireRoutes beside =
        new ProviderWireRoutes(
            ProviderId.of("sixth"), List.of("/__simulatorx", "/uiz", "/actuators"));

    assertThat(beside.namespaces()).hasSize(3);
    assertThat(beside.covers("/__simulator")).isFalse();
    assertThat(beside.covers("/ui/objects")).isFalse();
  }
}
