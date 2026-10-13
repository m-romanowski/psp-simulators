package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.documentation.PayerPageAddresses.Declared;
import dev.marcinromanowski.pspsim.documentation.PayerPageAddresses.Sighting;
import dev.marcinromanowski.pspsim.documentation.PayerPageAddresses.TheBuild;
import dev.marcinromanowski.pspsim.documentation.RoutesInTheBuild.Route;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PayerPageAddressConventionTest {

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final String A_PAGE = "/far";

  private static final String A_PAGE_WITH_A_VARIABLE = "/tokenize/sessions/{hostedCardSessionId}";

  @Test
  void a_template_linking_a_tester_to_a_payer_page_is_named_with_its_line() {
    String template =
        "<table>\n  <tr><td>a hold</td>\n  <td><a href=\"/far?l=89a7\">open the page</a></td>\n";

    List<Sighting> seen =
        PayerPageAddresses.sightingsIn("templates/objects.html", template, List.of(A_PAGE));

    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).line()).isEqualTo(3);
    assertThat(seen.get(0).route()).isEqualTo(A_PAGE);
    assertThat(seen.get(0).file()).isEqualTo("templates/objects.html");
    assertThat(seen.get(0).asItReads()).contains("open the page");
  }

  @Test
  void a_payer_page_whose_address_carries_a_path_variable_is_found_however_it_is_filled() {
    List<String> addresses = List.of(A_PAGE_WITH_A_VARIABLE);

    assertThat(
            PayerPageAddresses.sightingsIn(
                "a.java", "link(\"/tokenize/sessions/\" + id);", addresses))
        .as("a screen that builds the address by joining its prefix to an id")
        .hasSize(1);
    assertThat(
            PayerPageAddresses.sightingsIn(
                "a.html", "<a th:href=\"@{/tokenize/sessions/{id}(id=${s})}\">go</a>", addresses))
        .as("a template that writes the address with the variable the route declares")
        .hasSize(1);
    assertThat(
            PayerPageAddresses.sightingsIn(
                "b.html", "<a href=\"/tokenize/sessions/abc\">go</a>", addresses))
        .as("a template that writes the address with the variable already filled")
        .hasSize(1);
    assertThat(PayerPageAddresses.sightingsIn("c.html", "<a href=\"/tokenize\">go</a>", addresses))
        .as("the namespace above the page is not the page")
        .isEmpty();
  }

  @Test
  void an_absolute_payer_url_is_the_shape_a_provider_mints_and_is_found() {
    List<String> grows = List.of(A_PAGE);

    assertThat(
            PayerPageAddresses.sightingsIn(
                "a.html", "<a href=\"http://localhost:8080/far?l=a\">", grows))
        .as("the form GrowHostedPage.urlFor builds, which is publicBaseUrl + the route")
        .hasSize(1);
    assertThat(PayerPageAddresses.sightingsIn("a.html", "<a href=\"//localhost:8080/far\">", grows))
        .as("the same address with the scheme left to the page")
        .hasSize(1);
    assertThat(
            PayerPageAddresses.sightingsIn(
                "a.java", "print(\"https://pspsim.example/far\");", grows))
        .as("a screen source printing it against a hostname")
        .hasSize(1);
    assertThat(
            PayerPageAddresses.sightingsIn(
                "a.html",
                "<a href=\"https://pspsim.example/hostedtokenization/tokenization/form/abc\">",
                List.of("/hostedtokenization/tokenization/form/{hostedTokenizationId}")))
        .as("worldline's address, absolute and with its variable filled")
        .hasSize(1);
  }

  @Test
  void an_address_that_merely_starts_the_same_is_not_a_payer_page() {
    assertThat(PayerPageAddresses.sightingsIn("a.html", "<a href=\"/farmers\">", List.of(A_PAGE)))
        .isEmpty();
    assertThat(PayerPageAddresses.sightingsIn("a.html", "<a href=\"/unfar\">", List.of(A_PAGE)))
        .isEmpty();
    assertThat(PayerPageAddresses.sightingsIn("a.html", "<a href=\"/far\">", List.of(A_PAGE)))
        .hasSize(1);
  }

  @Test
  void an_address_carrying_two_path_variables_is_read_as_two_segments() {
    List<String> twoDeep = List.of("/v2/{merchantId}/pages/{pageId}");

    assertThat(PayerPageAddresses.sightingsIn("a.html", "<a href=\"/v2/m-7/pages/p-3\">", twoDeep))
        .hasSize(1);
    assertThat(
            PayerPageAddresses.sightingsIn(
                "a.html", "<a href=\"https://host/v2/m-7/pages/p-3\">", twoDeep))
        .hasSize(1);
    assertThat(PayerPageAddresses.sightingsIn("a.html", "<a href=\"/v2/m-7/orders/p-3\">", twoDeep))
        .as("a different page under the same namespace is not this page")
        .isEmpty();
  }

  @Test
  void only_an_absolute_path_is_a_route_this_gate_will_read_out_of_a_declaration() {
    assertThat(PayerPageAddresses.aRouteThisGateCanRead(A_PAGE)).isTrue();
    assertThat(PayerPageAddresses.aRouteThisGateCanRead(A_PAGE_WITH_A_VARIABLE)).isTrue();
    assertThat(PayerPageAddresses.aRouteThisGateCanRead(null))
        .as("a route a provider computes leaves no string constant to read")
        .isFalse();
    assertThat(PayerPageAddresses.aRouteThisGateCanRead("two"))
        .as("a constant belonging to another call on the same line is not a route")
        .isFalse();
    assertThat(PayerPageAddresses.aRouteThisGateCanRead("https://cards.autra.io/tokenize"))
        .as("a page the vendor hosts on its own origin is no route of this build")
        .isFalse();
  }

  @Test
  void a_content_type_names_a_page_only_where_it_opens_the_value() {
    assertThat(PayerPageAddresses.aContentTypeOfAPage("text/html; charset=utf-8")).isTrue();
    assertThat(PayerPageAddresses.aContentTypeOfAPage("text/html")).isTrue();
    assertThat(PayerPageAddresses.aContentTypeOfAPage(" text/html; charset=UTF-8")).isTrue();
    assertThat(PayerPageAddresses.aContentTypeOfAPage("application/json")).isFalse();
    assertThat(
            PayerPageAddresses.aContentTypeOfAPage(
                "answer as the live wire does: content type text/html, and every non-ASCII"))
        .as("GrowWireForm's prose names the content type without serving a page")
        .isFalse();
    assertThat(
            PayerPageAddresses.aContentTypeOfAPage(
                " for a missing required field: HTTP 200, text/html, a human page carrying no"))
        .as("AzericardWireFaults' prose names it in the middle of a sentence")
        .isFalse();
  }

  @Test
  void a_page_served_through_springs_own_media_type_counts_as_a_page() {
    assertThat(
            PayerPageAddresses.theHtmlMediaType("org.springframework.http.MediaType", "TEXT_HTML"))
        .isTrue();
    assertThat(
            PayerPageAddresses.theHtmlMediaType(
                "org.springframework.http.MediaType", "TEXT_HTML_VALUE"))
        .isTrue();
    assertThat(
            PayerPageAddresses.theHtmlMediaType(
                "org.springframework.http.MediaType", "APPLICATION_JSON"))
        .isFalse();
    assertThat(
            PayerPageAddresses.theHtmlMediaType(
                "dev.marcinromanowski.pspsim.provider.grow.GrowFields", "TEXT_HTML"))
        .isFalse();
  }

  @Test
  void the_compiled_build_declares_one_payer_page_for_every_provider_that_serves_one() {
    PayerPageAddresses.TheBuild build = PayerPageAddresses.inTheBuild(COMPILED_MAIN);

    assertThat(build.declared()).isNotEmpty();
    assertThat(build.providersThatServeAPage()).isNotEmpty();
    assertThat(build.providersThatDeclareAPage())
        .as("every provider that serves a page as text/html declares the route it is served at")
        .containsAll(build.providersThatServeAPage());
    assertThat(build.routes())
        .as("an address is read out of each declaration, each one an absolute path")
        .isNotEmpty()
        .allSatisfy(route -> assertThat(route).startsWith("/"));
    assertThat(PayerPageAddressConvention.verdictOn(Path.of(""), COMPILED_MAIN).offences())
        .as("the build under test keeps the rule this gate enforces")
        .isEmpty();
  }

  @Test
  void a_build_declaring_no_payer_page_is_a_broken_classpath_rather_than_a_clean_tree(
      @TempDir Path nothingCompiled) {
    assertThatThrownBy(() -> PayerPageAddressConvention.verdictOn(Path.of(""), nothingCompiled))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares a payer page at all");
  }

  @Test
  void a_missing_screen_root_is_a_sweep_that_read_nothing_rather_than_one_that_found_nothing() {
    assertThatThrownBy(
            () -> PayerPageAddresses.theFilesAScreenIsMadeOf(Path.of("no-such-project-root")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PayerPageAddresses.TEMPLATES);
  }

  @Test
  void a_declaration_no_provider_route_answers_is_an_address_this_gate_would_sweep_in_vain() {
    TheBuild build =
        new TheBuild(
            List.of(
                new Declared(
                    "grow", "/paged", "dev.marcinromanowski.pspsim.provider.grow.GrowOrigination")),
            Set.of("grow"));

    List<String> offences =
        PayerPageAddressConvention.theDeclarationsNoRouteAnswers(build, List.of(aRoute(A_PAGE)));

    assertThat(offences).hasSize(1);
    assertThat(offences.get(0)).contains("/paged").contains("GrowOrigination").contains(A_PAGE);
  }

  @Test
  void a_declaration_a_provider_route_answers_is_no_offence() {
    TheBuild build =
        new TheBuild(
            List.of(
                new Declared(
                    "grow", A_PAGE, "dev.marcinromanowski.pspsim.provider.grow.GrowOrigination")),
            Set.of("grow"));

    assertThat(
            PayerPageAddressConvention.theDeclarationsNoRouteAnswers(
                build, List.of(aRoute(A_PAGE))))
        .isEmpty();
  }

  @Test
  void a_route_of_a_screen_at_the_same_path_does_not_answer_a_providers_declaration() {
    TheBuild build =
        new TheBuild(
            List.of(
                new Declared(
                    "grow", A_PAGE, "dev.marcinromanowski.pspsim.provider.grow.GrowOrigination")),
            Set.of("grow"));
    Route aScreen =
        new Route(
            "get",
            A_PAGE,
            "open",
            "dev.marcinromanowski.pspsim.ui.DashboardController",
            RoutesInTheBuild.SCREENS,
            List.of(),
            Optional.empty(),
            false,
            false);

    assertThat(PayerPageAddressConvention.theDeclarationsNoRouteAnswers(build, List.of(aScreen)))
        .hasSize(1);
  }

  @Test
  void a_provider_serving_a_page_and_declaring_none_leaves_an_address_this_gate_cannot_sweep() {
    TheBuild build =
        new TheBuild(
            List.of(
                new Declared(
                    "grow", A_PAGE, "dev.marcinromanowski.pspsim.provider.grow.GrowOrigination")),
            Set.of("grow", "sixth"));

    List<String> offences =
        PayerPageAddressConvention.theProvidersThatServeAPageAndDeclareNone(build);

    assertThat(offences).hasSize(1);
    assertThat(offences.get(0)).contains("sixth").contains(PayerPageAddresses.THE_DECLARATION);
  }

  @Test
  void a_provider_that_serves_no_page_owes_this_gate_no_declaration() {
    TheBuild build =
        new TheBuild(
            List.of(
                new Declared(
                    "grow", A_PAGE, "dev.marcinromanowski.pspsim.provider.grow.GrowOrigination")),
            Set.of("grow"));

    assertThat(PayerPageAddressConvention.theProvidersThatServeAPageAndDeclareNone(build))
        .isEmpty();
  }

  @Test
  void a_provider_is_named_by_the_one_package_segment_under_the_provider_package() {
    assertThat(
            PayerPageAddresses.providerOf(
                "dev.marcinromanowski.pspsim.provider.grow.GrowOrigination"))
        .isEqualTo("grow");
    assertThat(PayerPageAddresses.providerOf("dev.marcinromanowski.pspsim.provider.grow"))
        .isEqualTo("grow");
  }

  @Test
  void the_build_names_every_address_once_and_every_provider_that_declared_one() {
    TheBuild build =
        new TheBuild(
            List.of(
                new Declared("autra", A_PAGE_WITH_A_VARIABLE, "a.Origination"),
                new Declared("grow", A_PAGE, "b.Origination"),
                new Declared("grow", A_PAGE, "c.Fixture")),
            Set.of("grow", "autra"));

    assertThat(build.routes()).containsExactly(A_PAGE_WITH_A_VARIABLE, A_PAGE);
    assertThat(build.providersThatDeclareAPage()).containsExactlyInAnyOrder("autra", "grow");
  }

  private static Route aRoute(String path) {
    return new Route(
        "get",
        path,
        "open",
        "dev.marcinromanowski.pspsim.provider.grow.GrowHostedPageController",
        RoutesInTheBuild.PROVIDER + "grow",
        List.of(),
        Optional.empty(),
        false,
        false);
  }
}
