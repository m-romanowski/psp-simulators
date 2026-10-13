package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.documentation.PayerPageDeclarations.APage;
import dev.marcinromanowski.pspsim.documentation.PayerPageDeclarations.Directive;
import dev.marcinromanowski.pspsim.documentation.PayerPagePolicyConvention.AFurtherDirective;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PayerPagePolicyConventionTest {

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final List<String> THE_PAGES_THIS_SIMULATOR_RENDERS =
      List.of(
          "dev.marcinromanowski.pspsim.provider.autra.AutraCardPage",
          "dev.marcinromanowski.pspsim.provider.azericard.AzericardErrorPage",
          "dev.marcinromanowski.pspsim.provider.azericard.AzericardPayerPage",
          "dev.marcinromanowski.pspsim.provider.grow.GrowHostedPage",
          "dev.marcinromanowski.pspsim.provider.worldline.WorldlineHostedTokenizationPage");

  private static final String THE_FLOOR = "default-src 'none'; style-src 'unsafe-inline'";

  private static final String A_DOCUMENT = "<!DOCTYPE html><html lang=\"en\"><head>";

  private static final String A_STYLED_DOCUMENT = "<!DOCTYPE html><html lang=\"en\"><style>";

  @Test
  void the_compiled_build_renders_five_pages_and_every_one_of_them_declares_a_policy() {
    List<APage> pages = PayerPageDeclarations.inTheBuild(COMPILED_MAIN);

    assertThat(PayerPagePolicyConvention.theNamesOf(pages))
        .as(
            "the denominator of this gate: a sweep that matched nothing would pass forever, so"
                + " the pages it examined are named one by one")
        .containsExactlyElementsOf(THE_PAGES_THIS_SIMULATOR_RENDERS);
    assertThat(pages).hasSize(5);
    assertThat(PayerPagePolicyConvention.howManyPolicies(pages))
        .as("one policy read out of each page, and no page declaring two of them")
        .isEqualTo(5);
    assertThat(PayerPagePolicyConvention.howManyCarryTheirOwnStyle(pages))
        .as("four of the five carry their own style; azericard's error page carries none")
        .isEqualTo(4);
    assertThat(PayerPagePolicyConvention.theProvidersOf(pages))
        .containsExactly("autra", "azericard", "grow", "worldline");
    assertThat(pages)
        .allSatisfy(
            page ->
                assertThat(PayerPageDeclarations.theCanonicalFormOf(page.policies().get(0)))
                    .containsAll(PayerPageDeclarations.theFloor()));
    assertThat(PayerPagePolicyConvention.verdictOn(COMPILED_MAIN).offences())
        .as("the build under test keeps the rule this gate enforces")
        .isEmpty();
  }

  @Test
  void the_read_lines_name_the_pages_the_policies_the_registrations_and_the_providers() {
    List<String> read = PayerPagePolicyConvention.verdictOn(COMPILED_MAIN).read();

    assertThat(read).hasSize(5);
    assertThat(read.get(0)).contains("examined the 5 that render an HTML document");
    assertThat(read.get(1)).contains("read 5 policies").contains("4 of which carry their own");
    assertThat(read.get(2)).contains("8 directive(s) beyond that floor are registered, over 4");
    assertThat(read.get(3)).contains("[autra, azericard, grow, worldline]");
    assertThat(read.get(4)).contains(PayerPageDeclarations.THE_CLASS_THAT_OWNS_THE_DECLARATION);
  }

  @Test
  void every_directive_beyond_the_floor_is_registered_against_one_page_with_its_reason() {
    assertThat(PayerPagePolicyConvention.THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME)
        .as("the registry is the whole of what any payer page may name beyond the floor")
        .hasSize(8)
        .allSatisfy(admitted -> assertThat(admitted.why()).isNotBlank())
        .extracting(AFurtherDirective::directive)
        .containsOnly("form-action 'self'", "frame-ancestors 'none'", "base-uri 'none'");
    assertThat(PayerPagePolicyConvention.thePagesThatRegisterADirective())
        .containsExactly(
            "dev.marcinromanowski.pspsim.provider.autra.AutraCardPage",
            "dev.marcinromanowski.pspsim.provider.azericard.AzericardErrorPage",
            "dev.marcinromanowski.pspsim.provider.azericard.AzericardPayerPage",
            "dev.marcinromanowski.pspsim.provider.worldline.WorldlineHostedTokenizationPage");
    assertThat(PayerPagePolicyConvention.THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME)
        .as("grow's page registers nothing, because its policy is the floor and nothing more")
        .extracting(AFurtherDirective::page)
        .doesNotContain("dev.marcinromanowski.pspsim.provider.grow.GrowHostedPage");
  }

  @Test
  void a_registration_no_policy_of_that_page_names_is_an_admission_nothing_asks_for() {
    List<APage> pages = List.of(new APage("a.Page", List.of(A_STYLED_DOCUMENT, THE_FLOOR)));

    assertThat(PayerPagePolicyConvention.theRegistrationsNoPageUses(pages))
        .as("every registration of the build under test is used by the page it names")
        .hasSize(PayerPagePolicyConvention.THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME.size());
    assertThat(
            PayerPagePolicyConvention.theRegistrationsNoPageUses(
                PayerPageDeclarations.inTheBuild(COMPILED_MAIN)))
        .isEmpty();
  }

  @Test
  void a_csp3_variant_that_overrides_the_floor_is_a_directive_beyond_it() {
    String widened = THE_FLOOR + "; script-src-elem https://cdn";
    APage page = new APage("a.Page", List.of(A_STYLED_DOCUMENT, widened));

    assertThat(PayerPageDeclarations.theDirectivesBeyondTheFloor(widened))
        .as(
            "script-src-elem overrides default-src for exactly the resources default-src 'none'"
                + " exists to block, and its name does not end in -src")
        .extracting(Directive::name)
        .containsExactly("script-src-elem");
    assertThat(PayerPagePolicyConvention.theOffenceOf(page))
        .singleElement()
        .asString()
        .contains("script-src-elem https://cdn")
        .contains("nothing registers it against this page");
  }

  @Test
  void a_directive_registered_against_the_page_that_needs_it_is_no_offence() {
    APage page =
        new APage(
            "dev.marcinromanowski.pspsim.provider.azericard.AzericardPayerPage",
            List.of(A_STYLED_DOCUMENT, THE_FLOOR + "; frame-ancestors 'none'"));
    APage another =
        new APage(
            "dev.marcinromanowski.pspsim.provider.grow.GrowHostedPage",
            List.of(A_STYLED_DOCUMENT, THE_FLOOR + "; frame-ancestors 'none'"));

    assertThat(PayerPagePolicyConvention.theOffenceOf(page)).isEmpty();
    assertThat(PayerPagePolicyConvention.theOffenceOf(another))
        .as("a registration admits a directive for one page and never for its siblings")
        .singleElement()
        .asString()
        .contains("frame-ancestors 'none'");
  }

  @Test
  void the_floor_is_compared_as_a_directive_set_and_not_as_a_substring() {
    assertThat(
            PayerPageDeclarations.theCanonicalFormOf(
                "style-src  'unsafe-inline' ;default-src 'none'"))
        .as("a policy reordered and respaced holds the same directives")
        .isEqualTo(PayerPageDeclarations.theFloor());
    assertThat(
            PayerPageDeclarations.namesTheDirective("default-src 'none-ish'", "default-src 'none'"))
        .as("a value a substring check would admit is a different directive")
        .isFalse();
    assertThat(PayerPageDeclarations.namesTheDirective("DEFAULT-SRC 'none'", "default-src 'none'"))
        .as("a directive name is case-insensitive in CSP and is read that way")
        .isTrue();
  }

  @Test
  void the_class_that_owns_the_declaration_names_the_header_and_the_same_floor() {
    assertThat(
            PayerPageDeclarations.theConstantsOf(
                COMPILED_MAIN, PayerPageDeclarations.THE_CLASS_THAT_OWNS_THE_DECLARATION))
        .contains(PayerPageDeclarations.THE_HEADER_A_POLICY_IS_DECLARED_IN, THE_FLOOR);
    assertThat(PayerPagePolicyConvention.theOffenceOfTheClassThatOwnsTheDeclaration(COMPILED_MAIN))
        .isEmpty();
  }

  @Test
  void a_declaration_owner_that_widened_its_floor_or_renamed_the_header_is_an_offence() {
    assertThat(PayerPagePolicyConvention.theFloorTheDeclarationOwnerNames(List.of(THE_FLOOR)))
        .isTrue();
    assertThat(
            PayerPagePolicyConvention.theFloorTheDeclarationOwnerNames(
                List.of("default-src 'none'; style-src 'unsafe-inline'; script-src 'self'")))
        .as("a floor with a directive more is not the floor this gate requires")
        .isFalse();
    assertThat(
            PayerPagePolicyConvention.theFloorTheDeclarationOwnerNames(
                List.of("default-src 'none'")))
        .isFalse();
    assertThat(
            PayerPagePolicyConvention.theOffenceOfTheClassThatOwnsTheDeclaration(Path.of("none")))
        .singleElement()
        .asString()
        .contains("is not in this build");
  }

  @Test
  void a_build_rendering_no_page_is_a_broken_classpath_rather_than_a_clean_tree(
      @TempDir Path nothingCompiled) {
    assertThatThrownBy(() -> PayerPagePolicyConvention.verdictOn(nothingCompiled))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("renders an HTML document, so this gate would examine nothing");
  }

  @Test
  void a_directory_of_no_classes_at_all_is_a_sweep_that_read_nothing() {
    assertThatThrownBy(() -> PayerPageDeclarations.inTheBuild(Path.of("no-such-classes-root")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not a directory of compiled classes");
  }

  @Test
  void a_page_that_declares_no_policy_declares_nothing_this_gate_can_read() {
    APage page = new APage("a.Page", List.of(A_DOCUMENT, "Content-Security-Policy"));

    assertThat(page.policies()).isEmpty();
    assertThat(PayerPagePolicyConvention.theOffenceOf(page))
        .singleElement()
        .asString()
        .contains("declares no policy this gate can read");
  }

  @Test
  void a_policy_that_blocks_nothing_by_default_leaves_what_it_does_not_mention_loaded() {
    APage page = new APage("a.Page", List.of(A_STYLED_DOCUMENT, "style-src 'self'"));

    List<String> offences = PayerPagePolicyConvention.theOffenceOf(page);

    assertThat(offences).hasSize(3);
    assertThat(offences.get(0)).contains("do not include default-src 'none'");
    assertThat(offences.get(1)).contains("would block the page's own style");
    assertThat(offences.get(2)).contains("style-src 'self'").contains("beyond the floor");
  }

  @Test
  void a_page_with_its_own_style_whose_policy_permits_none_would_be_read_unstyled() {
    APage styled = new APage("a.Page", List.of(A_STYLED_DOCUMENT, THE_FLOOR));
    APage styleless = new APage("b.Page", List.of("<html><body>", "default-src 'none'"));
    APage bare = new APage("c.Page", List.of("<html><style>", "default-src 'none'"));

    assertThat(PayerPagePolicyConvention.theOffenceOf(styled)).isEmpty();
    assertThat(PayerPagePolicyConvention.theOffenceOf(styleless))
        .as("a page that carries no style of its own is owed no style-src")
        .isEmpty();
    assertThat(PayerPagePolicyConvention.theOffenceOf(bare))
        .singleElement()
        .asString()
        .contains("would block the page's own style");
  }

  @Test
  void a_document_that_loads_a_subresource_is_named_rather_than_answered_by_a_wider_policy() {
    APage page =
        new APage(
            "a.Page", List.of(A_STYLED_DOCUMENT, "<script src=\"/app.js\"></script>", THE_FLOOR));

    assertThat(page.theElementsItLoadsASubresourceFor()).containsExactly("script");
    assertThat(PayerPagePolicyConvention.theOffenceOf(page))
        .singleElement()
        .asString()
        .contains("renders a document naming [script]")
        .contains("say what the page loads");
  }

  @Test
  void an_element_is_named_only_where_its_own_name_ends() {
    assertThat(PayerPageDeclarations.namesTheElement("<input name=\"card\">", "img")).isFalse();
    assertThat(PayerPageDeclarations.namesTheElement("<label for=\"card\">", "link")).isFalse();
    assertThat(PayerPageDeclarations.namesTheElement("<section>", "source")).isFalse();
    assertThat(PayerPageDeclarations.namesTheElement("<images>", "img")).isFalse();
    assertThat(PayerPageDeclarations.namesTheElement("<IMG SRC=\"a\">", "img")).isTrue();
    assertThat(PayerPageDeclarations.namesTheElement("</title><style>", "style")).isTrue();
    assertThat(PayerPageDeclarations.namesTheElement("<link rel=\"icon\"", "link")).isTrue();
  }

  @Test
  void only_a_constant_that_opens_a_document_makes_the_class_that_holds_it_a_page() {
    assertThat(PayerPageDeclarations.opensAnHtmlDocument(A_DOCUMENT)).isTrue();
    assertThat(PayerPageDeclarations.opensAnHtmlDocument("<html><body><p>")).isTrue();
    assertThat(PayerPageDeclarations.opensAnHtmlDocument("  <!doctype html>")).isTrue();
    assertThat(PayerPageDeclarations.opensAnHtmlDocument("<section><h1>")).isFalse();
    assertThat(PayerPageDeclarations.opensAnHtmlDocument("text/html; charset=utf-8")).isFalse();
  }

  @Test
  void a_policy_is_a_constant_naming_a_directive_and_a_source_expression() {
    assertThat(PayerPageDeclarations.aPolicy(THE_FLOOR)).isTrue();
    assertThat(PayerPageDeclarations.aPolicy("frame-ancestors 'none'")).isTrue();
    assertThat(PayerPageDeclarations.aPolicy("Content-Security-Policy")).isFalse();
    assertThat(PayerPageDeclarations.aPolicy("text/html; charset=utf-8")).isFalse();
    assertThat(PayerPageDeclarations.theDirectivesOf(THE_FLOOR + "; form-action 'self'"))
        .extracting(Directive::name)
        .containsExactly("default-src", "style-src", "form-action");
    assertThat(PayerPageDeclarations.theDirectivesOf("default-src"))
        .as("a directive naming no source is still the directive it named")
        .containsExactly(new Directive("default-src", ""));
  }

  @Test
  void a_provider_that_declares_a_payer_page_and_renders_none_holds_that_page_to_nothing() {
    List<APage> pages =
        List.of(
            new APage(
                "dev.marcinromanowski.pspsim.provider.grow.GrowHostedPage",
                List.of(A_STYLED_DOCUMENT, THE_FLOOR)));

    assertThat(
            PayerPagePolicyConvention.theProvidersThatDeclareAPageAndRenderNone(
                pages, List.of("grow")))
        .isEmpty();
    assertThat(
            PayerPagePolicyConvention.theProvidersThatDeclareAPageAndRenderNone(
                pages, List.of("grow", "sixth")))
        .singleElement()
        .asString()
        .contains("provider sixth")
        .contains("renders no HTML document this gate can read");
  }

  @Test
  void the_offences_of_every_page_are_gathered_rather_than_the_first_page_reported_alone() {
    List<APage> pages =
        List.of(
            new APage("a.Page", List.of(A_DOCUMENT)),
            new APage("b.Page", List.of("<html>", "img-src 'self'")));

    List<String> offences = PayerPagePolicyConvention.theOffencesOf(pages);

    assertThat(offences).hasSize(3);
    assertThat(offences.get(0)).startsWith("a.Page");
    assertThat(offences.get(1)).startsWith("b.Page");
    assertThat(offences.get(2)).startsWith("b.Page");
  }
}
