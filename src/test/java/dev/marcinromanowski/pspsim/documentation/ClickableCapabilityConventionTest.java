package dev.marcinromanowski.pspsim.documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.documentation.ClickableCapabilityConvention.ApiOnly;
import dev.marcinromanowski.pspsim.documentation.ClickableCapabilityConvention.Binding;
import dev.marcinromanowski.pspsim.documentation.ClickableCapabilityConvention.Finding;
import dev.marcinromanowski.pspsim.documentation.ClickableCapabilityConvention.Reach;
import dev.marcinromanowski.pspsim.documentation.ClickableCapabilityConvention.Reason;
import dev.marcinromanowski.pspsim.documentation.ClickableCapabilityConvention.Verdict;
import dev.marcinromanowski.pspsim.documentation.RoutesInTheBuild.Route;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.ContributedScreen;
import dev.marcinromanowski.pspsim.ui.ContributedScreens;
import dev.marcinromanowski.pspsim.ui.HostedCompletionForm;
import dev.marcinromanowski.pspsim.ui.RailDestination;
import dev.marcinromanowski.pspsim.ui.TheRail;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClickableCapabilityConventionTest {

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final Path COMPILED_TESTS = Path.of("build", "classes", "java", "test");

  private static final Reach REACH = Reach.under(COMPILED_MAIN);

  private static final List<Route> ROUTES = RoutesInTheBuild.under(COMPILED_MAIN);

  private static final String CONTROL_PREFIX = "/__simulator/{provider}";

  private static final String RESET = "POST " + CONTROL_PREFIX + "/reset";

  private static final String RESET_SCREEN = "POST /ui/dashboard/reset";

  private static final String RESET_EFFECT =
      "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#reset";

  private static final String RESET_HANDLER =
      "dev.marcinromanowski.pspsim.control.SimulatorControlController#reset";

  private static final String RESET_SCREEN_HANDLER =
      "dev.marcinromanowski.pspsim.ui.DashboardController#resetTheTestRun";

  private static final String AUDIT = "GET " + CONTROL_PREFIX + "/audit";

  private static final String AUDIT_SCREEN = "GET /ui/audit";

  private static final String AUDIT_EFFECT =
      "dev.marcinromanowski.pspsim.kernel.audit.AuditReads#pageOf";

  private static final String WRITE_A_RULE = "POST " + CONTROL_PREFIX + "/scenarios";

  private static final String READ_THE_RULES = "GET /ui/scenarios";

  private static final String RULES_OF_A_RUN =
      "dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules#of";

  private static final String COPY = "POST " + CONTROL_PREFIX + "/scenarios/{scenarioId}/copy";

  private static final String COPY_SCREEN = "POST /ui/scenarios/diff/copy";

  private static final String COPY_GRAMMAR =
      "dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy#of";

  private static final String COPY_SCREEN_HANDLER =
      "dev.marcinromanowski.pspsim.ui.ScenarioDiffController#copy";

  private static final String A_NOTE = "the diff screen copies a rule through a form";

  private static final String MINTED_IN_A_LAMBDA =
      "dev.marcinromanowski.pspsim.kernel.testrun.TestRunId#of";

  private static final String NO_SCREEN_OFFERS_IT =
      "` is a capability of the control API that no screen offers";

  private static final String OUTSIDE_THE_KERNEL =
      "dev.marcinromanowski.pspsim.ui.HostedCompletionForm#of";

  private static final String THE_CONTRIBUTED_ONES = "/ui/x/";

  @TempDir private Path templates;

  static final class ThroughAMethodReference {

    private ThroughAMethodReference() {}

    static List<TestRunId> runs() {
      return Stream.of("a-run").map(TestRunId::of).toList();
    }

    static List<TestRunId> behindAHelper() {
      return theSameRunsAgain();
    }

    private static List<TestRunId> theSameRunsAgain() {
      return List.of(TestRunId.of("another-run"));
    }

    static int nothingOfTheKernel() {
      return "a-run".length();
    }
  }

  @Test
  void a_control_capability_no_binding_names_is_named_as_reachable_by_no_click() {
    assertThat(messages(checked(List.of(), List.of())))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(RESET)
                    .contains("a capability of the control API that no screen offers")
                    .contains("screensThatOfferAControlCapability")
                    .contains("facetsOnlyTheApiOffers"));
  }

  @Test
  void a_binding_naming_a_control_route_this_build_has_not_got_fails() {
    Verdict verdict =
        checked(
            List.of(new Binding("POST " + CONTROL_PREFIX + "/wipe", RESET_SCREEN, RESET_EFFECT)),
            List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("POST " + CONTROL_PREFIX + "/wipe")
                    .contains(RESET_SCREEN)
                    .contains("no control route in this build answers it"));
  }

  @Test
  void a_binding_naming_a_screen_route_this_build_has_not_got_fails() {
    Verdict verdict =
        checked(List.of(new Binding(RESET, "POST /ui/dashboard/wipe", RESET_EFFECT)), List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(RESET)
                    .contains("POST /ui/dashboard/wipe")
                    .contains("no screen route in this build answers it"));
  }

  @Test
  void a_binding_held_to_a_kernel_method_this_build_has_not_got_fails() {
    Verdict verdict =
        checked(
            List.of(
                new Binding(
                    RESET,
                    RESET_SCREEN,
                    "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#obliterate")),
            List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#obliterate")
                    .contains(
                        "no class under dev.marcinromanowski.pspsim.kernel. in this build declares"));
  }

  @Test
  void a_binding_held_to_a_method_outside_the_kernel_fails_although_the_method_exists() {
    String offeredByTheScreenPackage = OUTSIDE_THE_KERNEL;

    assertThat(HostedCompletionForm.class.getDeclaredMethods())
        .anyMatch(declared -> declared.getName().equals("of"));
    assertThat(
            messages(
                checked(
                    List.of(new Binding(RESET, RESET_SCREEN, offeredByTheScreenPackage)),
                    List.of())))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(offeredByTheScreenPackage)
                    .contains(
                        "no class under dev.marcinromanowski.pspsim.kernel. in this build declares"));
  }

  @Test
  void a_capability_that_no_longer_performs_the_method_it_is_bound_by_fails() {
    Verdict verdict = checked(List.of(new Binding(AUDIT, RESET_SCREEN, RESET_EFFECT)), List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(AUDIT)
                    .contains(RESET_EFFECT)
                    .contains(
                        "dev.marcinromanowski.pspsim.control.SimulatorControlController#audit")
                    .contains("reaches no such method")
                    .contains("a capability this control route no longer performs"));
  }

  @Test
  void a_screen_that_no_longer_performs_the_capability_fails() {
    Verdict verdict =
        checked(List.of(new Binding(RESET, "POST /ui/scenarios/write", RESET_EFFECT)), List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(RESET)
                    .contains("POST /ui/scenarios/write")
                    .contains("dev.marcinromanowski.pspsim.ui.ScenarioController#write")
                    .contains("clicking that screen no longer performs this capability"));
  }

  @Test
  void a_capability_that_changes_the_simulator_bound_to_a_screen_that_only_reads_it_fails() {
    Verdict verdict =
        checked(List.of(new Binding(WRITE_A_RULE, READ_THE_RULES, RULES_OF_A_RUN)), List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(WRITE_A_RULE)
                    .contains(READ_THE_RULES)
                    .contains("changes the simulator")
                    .contains("only reads it"));
    assertThat(messages(verdict)).noneMatch(message -> message.contains("reaches no such method"));
  }

  @Test
  void one_capability_bound_to_one_screen_twice_fails() {
    Binding twice = new Binding(RESET, RESET_SCREEN, RESET_EFFECT);

    assertThat(messages(checked(List.of(twice, twice), List.of())))
        .anySatisfy(
            message ->
                assertThat(message).contains(RESET).contains(RESET_SCREEN).contains("twice"));
  }

  @Test
  void a_screen_route_no_page_links_to_fails() {
    Set<String> withoutTheResetButton = new TreeSet<>(everyScreenPath());
    withoutTheResetButton.remove("/ui/dashboard/reset");

    Verdict verdict =
        ClickableCapabilityConvention.check(
            REACH,
            ROUTES,
            Set.copyOf(withoutTheResetButton),
            List.of(),
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(RESET_SCREEN)
                    .contains("form target or a link target on no page")
                    .contains("/ui/dashboard/reset"));
  }

  @Test
  void a_screen_route_no_page_links_to_that_the_navigation_offers_as_a_tab_passes() {
    Set<String> withoutTheResetButton = new TreeSet<>(everyScreenPath());
    withoutTheResetButton.remove("/ui/dashboard/reset");

    Verdict verdict =
        ClickableCapabilityConvention.check(
            REACH,
            ROUTES,
            Set.copyOf(withoutTheResetButton),
            List.of("/ui/dashboard/reset"),
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of());

    assertThat(messages(verdict))
        .noneSatisfy(
            message -> assertThat(message).contains("/ui/dashboard/reset").contains("no page"));
  }

  @Test
  void a_tab_the_navigation_offers_reaches_a_route_that_names_a_path_variable() {
    Verdict verdict =
        ClickableCapabilityConvention.check(
            REACH,
            ROUTES,
            everyPathOfAScreenThisBuildSpellsOut(),
            List.of("/ui/x/autra/pix"),
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of());

    assertThat(messages(verdict))
        .as(
            "the route that serves a contributed screen is a form target and a link target on no"
                + " page, because the page it serves is the one being contributed: the only thing"
                + " that reaches it is a tab of the navigation, matched against a route that names"
                + " two path variables. Nothing else in this gate matches a variable to a segment,"
                + " so a check that reads the contributed route as literally linked holds that"
                + " matching to nothing, whichever side of it is named in the finding")
        .noneSatisfy(message -> assertThat(message).contains(THE_CONTRIBUTED_ONES));
  }

  @Test
  void a_screen_route_with_a_path_variable_that_no_tab_reaches_still_fails() {
    Verdict verdict =
        ClickableCapabilityConvention.check(
            REACH,
            ROUTES,
            everyPathOfAScreenThisBuildSpellsOut(),
            List.of("/ui/objects"),
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("/ui/x/{provider}/{screen}")
                    .contains("the destination of no tab of the navigation"));
  }

  @Test
  void a_tab_under_the_screen_namespace_that_no_route_answers_fails() {
    Verdict verdict =
        ClickableCapabilityConvention.check(
            REACH,
            theRoutesThatSpellThemselvesOut(),
            everyScreenPath(),
            List.of("/ui/x/autra/pix"),
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of());

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("the navigation offers `/ui/x/autra/pix` as a tab")
                    .contains("no screen route of this build answers it"));
  }

  @Test
  void a_tab_outside_the_screen_namespace_is_held_to_no_route_of_this_build() {
    Verdict verdict =
        ClickableCapabilityConvention.check(
            REACH,
            ROUTES,
            everyScreenPath(),
            List.of("/openapi/index.html"),
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of());

    assertThat(messages(verdict))
        .noneSatisfy(message -> assertThat(message).contains("/openapi/index.html"));
  }

  @Test
  void the_navigation_this_gate_reads_carries_the_contributed_screens_as_well_as_the_core_ones() {
    List<String> offered = ClickableCapabilityConvention.everyDestinationTheNavigationOffers();
    List<String> carried = new ArrayList<>();
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      carried.add(destination.path());
    }
    for (ContributedScreen contributed : new ContributedScreens().all()) {
      carried.add(contributed.path());
    }

    assertThat(offered)
        .as(
            "the destinations this gate holds the screen routes to are the ones the navigation"
                + " offers, discovered pages and all; a gate that read the written-out ones alone"
                + " would pass a contributed screen nothing serves")
        .containsExactlyInAnyOrderElementsOf(carried)
        .contains("/ui/objects", "/ui", "/openapi/index.html");
  }

  @Test
  void a_facet_declared_api_only_that_a_screen_reaches_after_all_fails() {
    Verdict verdict =
        checked(
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of(
                new ApiOnly(
                    RESET,
                    RESET_EFFECT,
                    Reason.A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD,
                    A_NOTE)));

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(RESET)
                    .contains(RESET_EFFECT)
                    .contains(RESET_SCREEN + " reaches that method")
                    .contains("the reason the declaration gives has stopped being true"));
  }

  @Test
  void a_facet_its_own_route_no_longer_reaches_fails() {
    Verdict verdict =
        checked(
            List.of(new Binding(AUDIT, AUDIT_SCREEN, AUDIT_EFFECT)),
            List.of(
                new ApiOnly(
                    AUDIT,
                    COPY_GRAMMAR,
                    Reason.A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD,
                    A_NOTE)));

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(AUDIT)
                    .contains(COPY_GRAMMAR)
                    .contains(
                        "dev.marcinromanowski.pspsim.control.SimulatorControlController#audit")
                    .contains("records a difference this build has not got"));
  }

  @Test
  void a_facet_naming_a_control_route_this_build_has_not_got_fails() {
    Verdict verdict =
        checked(
            List.of(),
            List.of(
                new ApiOnly(
                    "POST " + CONTROL_PREFIX + "/wipe",
                    COPY_GRAMMAR,
                    Reason.A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD,
                    A_NOTE)));

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("POST " + CONTROL_PREFIX + "/wipe")
                    .contains("the declaration excuses nothing that exists"));
  }

  @Test
  void a_facet_held_to_a_kernel_method_this_build_has_not_got_fails() {
    Verdict verdict =
        checked(
            List.of(),
            List.of(
                new ApiOnly(
                    COPY,
                    "dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy#obliterate",
                    Reason.A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD,
                    A_NOTE)));

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy#obliterate")
                    .contains("An API-only facet is named by the kernel method no screen reaches"));
  }

  @Test
  void a_facet_excused_by_another_road_fails_when_no_screen_is_bound_to_that_route_at_all() {
    Verdict verdict =
        checked(
            List.of(),
            List.of(
                new ApiOnly(
                    COPY,
                    COPY_GRAMMAR,
                    Reason.A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD,
                    A_NOTE)));

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(COPY)
                    .contains(COPY_GRAMMAR)
                    .contains(A_NOTE)
                    .contains("The reason rests on a binding that is not there"));
  }

  @Test
  void a_facet_recorded_as_a_gap_fails_until_someone_decides() {
    Verdict verdict =
        checked(
            List.of(
                new Binding(
                    COPY,
                    COPY_SCREEN,
                    "dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission#write")),
            List.of(
                new ApiOnly(
                    COPY, COPY_GRAMMAR, Reason.NO_SCREEN_OFFERS_IT_AND_ONE_SHOULD, A_NOTE)));

    assertThat(messages(verdict))
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains(COPY)
                    .contains(COPY_GRAMMAR)
                    .contains(A_NOTE)
                    .contains("This is the gap itself"));
  }

  @Test
  void a_binding_the_compiled_classes_bear_out_is_reported_about_at_all() {
    Verdict verdict =
        checked(
            List.of(new Binding(RESET, RESET_SCREEN, RESET_EFFECT)),
            List.of(
                new ApiOnly(
                    COPY,
                    COPY_GRAMMAR,
                    Reason.A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD,
                    A_NOTE)));

    Set<String> capabilities = signaturesIn(RoutesInTheBuild.CONTROL);
    Set<String> unbound = new TreeSet<>(capabilities);
    unbound.remove(RESET);
    assertThat(capabilities).contains(RESET, COPY);
    assertThat(messages(verdict)).noneMatch(message -> message.contains(RESET_SCREEN));
    assertThat(theCapabilitiesReportedWithNoScreen(verdict)).isEqualTo(unbound);
    assertThat(messages(verdict)).anySatisfy(message -> assertThat(message).contains(COPY));
    assertThat(verdict.capabilities()).isEqualTo(capabilities.size());
    assertThat(verdict.screens()).isEqualTo(signaturesIn(RoutesInTheBuild.SCREENS).size());
    assertThat(verdict.screens()).isPositive();
    assertThat(verdict.bindings()).isEqualTo(1);
    assertThat(verdict.facets()).isEqualTo(1);
  }

  @Test
  void the_control_route_and_the_screen_behind_a_reset_reach_the_same_kernel_method() {
    assertThat(REACH.from(RESET_HANDLER, RESET_EFFECT)).isTrue();
    assertThat(REACH.from(RESET_SCREEN_HANDLER, RESET_EFFECT)).isTrue();
  }

  @Test
  void a_screen_that_writes_a_copy_its_own_way_does_not_reach_the_copy_grammar() {
    assertThat(
            REACH.from(
                "dev.marcinromanowski.pspsim.control.SimulatorControlController#copyScenario",
                COPY_GRAMMAR))
        .isTrue();
    assertThat(REACH.from(COPY_SCREEN_HANDLER, COPY_GRAMMAR)).isFalse();
  }

  @Test
  void a_kernel_method_reached_only_through_a_method_reference_is_reached() {
    Reach ofTheTests = Reach.under(COMPILED_TESTS);
    String throughAMethodReference =
        ClickableCapabilityConventionTest.class.getName() + "$ThroughAMethodReference";

    assertThat(ofTheTests.from(throughAMethodReference + "#runs", MINTED_IN_A_LAMBDA)).isTrue();
    assertThat(ofTheTests.from(throughAMethodReference + "#behindAHelper", MINTED_IN_A_LAMBDA))
        .isTrue();
    assertThat(ofTheTests.from(throughAMethodReference + "#nothingOfTheKernel", MINTED_IN_A_LAMBDA))
        .isFalse();
  }

  @Test
  void a_method_of_the_kernel_is_told_apart_from_one_outside_it_and_from_one_that_is_gone() {
    assertThat(REACH.declaresAKernelMethod(RESET_EFFECT)).isTrue();
    assertThat(REACH.declaresAKernelMethod(OUTSIDE_THE_KERNEL)).isFalse();
    assertThat(
            REACH.declaresAKernelMethod(
                "dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider#obliterate"))
        .isFalse();
  }

  @Test
  void a_path_a_template_makes_a_form_target_or_a_link_target_of_is_read_out_of_it() {
    write(
        "page.html",
        """
        <form action="@{/ui/personas/seed}" method="post"></form>
        <a href="@{/ui/personas(provider=${provider})}">personas</a>
        <form action="@{${path}}" method="post"></form>
        """);

    Set<String> linked = ClickableCapabilityConvention.everyPathLinkedFrom(templates);

    assertThat(linked).contains("/ui/personas/seed", "/ui/personas");
    assertThat(linked).doesNotContain("/ui/smoke");
  }

  @Test
  void a_binding_that_is_not_three_fields_is_refused_where_it_is_read() {
    assertThatThrownBy(() -> Binding.parse("POST /a|GET /b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a binding is capability|screen|effect");
  }

  @Test
  void a_facet_that_is_not_four_fields_or_names_no_reason_is_refused_where_it_is_read() {
    assertThatThrownBy(
            () -> ApiOnly.parse("POST /a|x#y|A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("an API-only facet is capability|effect|reason|note");
    assertThatThrownBy(() -> ApiOnly.parse("POST /a|x#y|BECAUSE_I_SAY_SO|a note"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Set<String> signaturesIn(String family) {
    Set<String> signatures = new TreeSet<>();
    for (Route route : ROUTES) {
      if (route.family().equals(family)) {
        signatures.add(route.signature());
      }
    }
    return signatures;
  }

  private static Set<String> theCapabilitiesReportedWithNoScreen(Verdict verdict) {
    Set<String> named = new TreeSet<>();
    for (String message : messages(verdict)) {
      if (message.contains(NO_SCREEN_OFFERS_IT)) {
        named.add(message.substring(1, message.indexOf('`', 1)));
      }
    }
    return named;
  }

  private static Verdict checked(List<Binding> bindings, List<ApiOnly> facets) {
    return ClickableCapabilityConvention.check(
        REACH, ROUTES, everyScreenPath(), List.of(), bindings, facets);
  }

  private static List<Route> theRoutesThatSpellThemselvesOut() {
    return ROUTES.stream().filter(route -> !route.path().contains(THE_CONTRIBUTED_ONES)).toList();
  }

  private static Set<String> everyPathOfAScreenThisBuildSpellsOut() {
    Set<String> paths = new TreeSet<>(everyScreenPath());
    paths.removeIf(path -> path.contains(THE_CONTRIBUTED_ONES));
    return Set.copyOf(paths);
  }

  private static Set<String> everyScreenPath() {
    Set<String> paths = new LinkedHashSet<>();
    for (Route route : ROUTES) {
      if (route.family().equals(RoutesInTheBuild.SCREENS)) {
        paths.add(route.path());
      }
    }
    return Set.copyOf(paths);
  }

  private static List<String> messages(Verdict verdict) {
    return verdict.findings().stream().map(Finding::message).toList();
  }

  private void write(String name, String contents) {
    try {
      Files.writeString(templates.resolve(name), contents, StandardCharsets.UTF_8);
    } catch (IOException unwritable) {
      throw new UncheckedIOException(unwritable);
    }
  }
}
