package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TheRailTest {

  @Test
  void the_rail_carries_every_screen_of_this_simulator_in_the_order_a_tester_reads_them() {
    List<String> asItReads = new ArrayList<>();
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      asItReads.add(
          destination.group().label()
              + " | "
              + destination.label()
              + " | "
              + destination.path()
              + " | "
              + destination.screen());
    }

    assertThat(asItReads)
        .containsExactly(
            "Daily work | Payments | /ui/objects | objects",
            "Daily work | Start a payment | /ui/objects/new | origination",
            "Daily work | Look a payment up | /ui/reads | reads",
            "Daily work | Webhooks | /ui/webhooks | webhooks",
            "Daily work | Retries | /ui/idempotency | inspector",
            "Set up | Configuration | /ui/configuration | configuration",
            "Set up | Payment methods | /ui/payment-methods | payment-methods",
            "Set up | Scenarios | /ui/scenarios | scenarios",
            "Set up | Compare scenarios | /ui/scenarios/diff | scenario-diff",
            "Set up | Personas | /ui/personas | personas",
            "Evidence | This test run | /ui | workbench",
            "Evidence | Dashboard | /ui/dashboard | dashboard",
            "Evidence | Audit | /ui/audit | audit",
            "Evidence | Timeline | /ui/timeline | timeline",
            "Evidence | Correlation | /ui/correlation | correlation",
            "Take it elsewhere | Run bundle | /ui/run-bundle | run-bundle",
            "Take it elsewhere | Reproduction bundle | /ui/repro-bundle | repro-bundle",
            "Take it elsewhere | Checks against the real PSP | /ui/smoke | smoke",
            "Take it elsewhere | Connect my app | /ui/pairing | pairing",
            "Take it elsewhere | API | /openapi/index.html | ");
  }

  @Test
  void every_screen_of_this_simulator_takes_the_chosen_provider_and_test_run_with_it() {
    List<String> withoutTheSelection = new ArrayList<>();
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      if (!destination.theSelectionTravelsWithIt()) {
        withoutTheSelection.add(destination.path());
      }
    }

    assertThat(withoutTheSelection)
        .as(
            "every screen of this simulator shows one provider and one test run at a time, so the"
                + " selection travels with each of its tabs; the published API document is the same"
                + " page whichever provider a tester is looking at")
        .containsExactly("/openapi/index.html");
  }

  @Test
  void the_rail_a_screen_draws_is_grouped_the_way_a_tester_reads_it() {
    List<RailGroupOnScreen> groups = aRailCarrying().of("autra");

    assertThat(groups)
        .extracting(RailGroupOnScreen::name)
        .containsExactly("Daily work", "Set up", "Evidence", "Take it elsewhere");
    assertThat(groups.get(0).items())
        .extracting(RailDestination::label)
        .containsExactly("Payments", "Start a payment", "Look a payment up", "Webhooks", "Retries");
    assertThat(groups.stream().mapToInt(group -> group.items().size()).sum())
        .isEqualTo(TheRail.theScreensThisSimulatorCarries().size());
  }

  @Test
  void a_group_puts_a_tab_where_its_order_says_and_settles_a_tie_by_its_label() {
    List<RailDestination> outOfOrder =
        List.of(
            new RailDestination(RailGroup.EVIDENCE, "Second", "/ui/b", "b", 20, true),
            new RailDestination(RailGroup.DAILY_WORK, "Only", "/ui/a", "a", 10, true),
            new RailDestination(RailGroup.EVIDENCE, "Zebra", "/ui/z", "z", 10, true),
            new RailDestination(RailGroup.EVIDENCE, "Apple", "/ui/x", "x", 10, true));

    List<RailGroupOnScreen> groups = TheRail.grouped(outOfOrder);

    assertThat(groups)
        .extracting(RailGroupOnScreen::name)
        .as("a group no tab belongs to is not printed as an empty heading")
        .containsExactly("Daily work", "Evidence");
    assertThat(groups.get(1).items())
        .extracting(RailDestination::label)
        .containsExactly("Apple", "Zebra", "Second");
  }

  @Test
  void the_order_of_a_tab_within_its_group_leaves_room_for_one_between_two_of_them() {
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      assertThat(destination.order())
          .as(
              destination.label()
                  + " sits at "
                  + destination.order()
                  + ", and a contributed screen names a whole number to sit between two tabs of a"
                  + " group, so the numbers this simulator uses are spaced")
          .isGreaterThanOrEqualTo(10);
      assertThat(destination.order() % 10).isZero();
    }
  }

  @Test
  void a_contributed_screen_is_a_tab_of_the_provider_whose_folder_it_sits_in_and_of_no_other() {
    TheRail rail = aRailCarrying(aPixScreen());

    assertThat(theLabelsOf(rail.of("autra")))
        .as(
            "a tab that named a vendor's screen on every provider's rail would be a list of"
                + " vendors, which is what the groups are there to stop")
        .contains("Pix");
    assertThat(theLabelsOf(rail.of("grow"))).doesNotContain("Pix");
    assertThat(theLabelsOf(rail.of(null))).doesNotContain("Pix");
  }

  @Test
  void a_contributed_tab_sits_where_its_own_order_puts_it_inside_the_group_it_named() {
    TheRail rail = aRailCarrying(aPixScreen());

    assertThat(rail.of("autra").get(0).items())
        .extracting(RailDestination::label)
        .containsExactly(
            "Payments", "Pix", "Start a payment", "Look a payment up", "Webhooks", "Retries");
  }

  @Test
  void a_screen_contributed_for_a_provider_this_build_does_not_carry_is_refused_by_its_file() {
    ContributedScreens forAProviderNobodyHas =
        new ContributedScreens(
            List.of(new ContributedScreen("blik", "code", "BLIK", RailGroup.DAILY_WORK, 15)));

    assertThatThrownBy(
            () -> new TheRail(forAProviderNobodyHas, new ProviderRegistry(List.of(anAutra()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("src/main/resources/templates/extensions/blik/code.html")
        .hasMessageContaining("this build carries no provider of that name");
  }

  @Test
  void a_contributed_tab_that_reads_like_a_tab_of_this_simulator_is_refused_by_its_file() {
    ContributedScreens impersonatingTheCoreTab =
        new ContributedScreens(
            List.of(new ContributedScreen("autra", "mine", " payments ", RailGroup.DAILY_WORK, 5)));

    assertThatThrownBy(() -> aRailCarrying(impersonatingTheCoreTab))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("src/main/resources/templates/extensions/autra/mine.html")
        .hasMessageContaining("content=\" payments \"")
        .hasMessageContaining("already offers a tab reading \"Payments\" under Daily work")
        .hasMessageContaining("/ui/objects")
        .hasMessageContaining("cannot tell which screen either one opens");
  }

  @Test
  void two_contributed_tabs_of_one_provider_that_read_alike_are_refused_by_the_second_file() {
    ContributedScreens twoOfThem =
        new ContributedScreens(
            List.of(
                new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15),
                new ContributedScreen("autra", "qr", "Pix", RailGroup.EVIDENCE, 15)));

    assertThatThrownBy(() -> aRailCarrying(twoOfThem))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("extensions/autra/qr.html")
        .hasMessageContaining("extensions/autra/pix.html")
        .hasMessageContaining("already offers a tab reading \"Pix\"");
  }

  @Test
  void one_tab_name_on_two_providers_rails_is_two_tabs_no_tester_sees_at_once() {
    ContributedScreens oneEach =
        new ContributedScreens(
            List.of(
                new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15),
                new ContributedScreen("grow", "pix", "Pix", RailGroup.DAILY_WORK, 15)));

    TheRail rail =
        new TheRail(
            oneEach, new ProviderRegistry(List.of(anAutra(), new ProviderModuleDouble("grow"))));

    assertThat(theLabelsOf(rail.of("autra"))).containsOnlyOnce("Pix");
    assertThat(theLabelsOf(rail.of("grow"))).containsOnlyOnce("Pix");
  }

  @Test
  void a_form_comes_back_to_a_screen_of_this_simulator_and_to_nowhere_else() {
    TheRail rail = aRailCarrying(aPixScreen());

    assertThat(rail.theScreenToReturnTo("/ui/x/autra/pix", "/ui/objects/new"))
        .isEqualTo("/ui/x/autra/pix");
    assertThat(rail.theScreenToReturnTo("/ui/audit", "/ui/objects/new")).isEqualTo("/ui/audit");
    assertThat(rail.theScreenToReturnTo(null, "/ui/objects/new")).isEqualTo("/ui/objects/new");
    assertThat(rail.theScreenToReturnTo("https://elsewhere.example/steal", "/ui/objects/new"))
        .as("a form that came back wherever a query string told it to is an open redirect")
        .isEqualTo("/ui/objects/new");
    assertThat(rail.theScreenToReturnTo("/ui/x/autra/unknown", "/ui/objects/new"))
        .isEqualTo("/ui/objects/new");
    assertThat(rail.theScreenToReturnTo("/openapi/index.html", "/ui/objects/new"))
        .as("the published API document is not a screen this simulator hands a payment back to")
        .isEqualTo("/ui/objects/new");
  }

  @Test
  void the_navigation_offers_every_screen_this_simulator_carries_and_every_one_contributed() {
    assertThat(TheRail.everyDestinationOnOffer(new ContributedScreens(List.of(aPixScreen()))))
        .contains("/ui/objects", "/openapi/index.html", "/ui/x/autra/pix")
        .hasSize(TheRail.theScreensThisSimulatorCarries().size() + 1);
  }

  private static ContributedScreen aPixScreen() {
    return new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15);
  }

  private static TheRail aRailCarrying(ContributedScreen... contributed) {
    return aRailCarrying(new ContributedScreens(List.of(contributed)));
  }

  private static TheRail aRailCarrying(ContributedScreens contributed) {
    return new TheRail(contributed, new ProviderRegistry(List.of(anAutra())));
  }

  private static ProviderModuleDouble anAutra() {
    return new ProviderModuleDouble("autra");
  }

  private static List<String> theLabelsOf(List<RailGroupOnScreen> groups) {
    List<String> labels = new ArrayList<>();
    for (RailGroupOnScreen group : groups) {
      for (RailDestination destination : group.items()) {
        labels.add(destination.label());
      }
    }
    return labels;
  }
}
