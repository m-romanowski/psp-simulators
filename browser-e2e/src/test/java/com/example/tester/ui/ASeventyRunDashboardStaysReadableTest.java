package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ASeventyRunDashboardStaysReadableTest extends AJourneyInARealBrowser {

  private static final int A_LAPTOP_IS_THIS_WIDE = 1280;

  private static final int A_LAPTOP_IS_THIS_TALL = 700;

  private static final int THE_RUNS_ONE_MACHINE_ACCUMULATED = 70;

  private static final String THE_RUN_A_TESTER_IS_LOOKING_FOR = "wizard-check";

  private static final String THE_RUN_A_TESTER_CLEANS_UP = "seeded-03";

  private static final String HOW_TALL_THE_LIST_IS_AND_HOW_TALL_A_WALL_OF_CHIPS_WOULD_BE =
      """
      ([seventy, sevenHundred]) => {
        const body = document.querySelector('section.panel > .body');
        const headings = [...body.querySelectorAll('h3')];
        const from = headings.find((h) => h.textContent.trim().startsWith('Test runs'));
        const to = headings.find((h) => h.textContent.trim().startsWith('Start another'));
        const span = () => Math.round(
          to.getBoundingClientRect().top - from.getBoundingClientRect().top);
        const now = span();
        const hidden = [];
        let node = from.nextElementSibling;
        while (node && node !== to) {
          hidden.push([node, node.style.display]);
          node.style.display = 'none';
          node = node.nextElementSibling;
        }
        const wall = document.createElement('ul');
        wall.className = 'choices';
        from.after(wall);
        const wallOf = (names) => {
          wall.innerHTML = names
            .map((name, at) => '<li><a href="#">' + name + '</a>'
              + (at === 0 ? '<span class="tag fact">active</span>' : '') + '</li>')
            .join('');
          return span();
        };
        const seventyChips = wallOf(seventy);
        const sevenHundredChips = wallOf(sevenHundred);
        wall.remove();
        hidden.forEach(([element, display]) => {
          element.style.display = display;
        });
        return {
          seventyChips: seventyChips,
          sevenHundredChips: sevenHundredChips,
          now: now,
          viewport: window.innerHeight,
        };
      }
      """;

  @Test
  @DisplayName("seventy test runs read as a short searchable list instead of a wall of chips")
  void seventyTestRunsReadAsAShortSearchableList() {
    List<String> seeded = theRunsOneMachineAccumulated();
    seedTheseTestRuns(seeded);

    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(theDashboard(testRunId(), null));

    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Test runs this simulator has registered")))
        .isVisible();
    assertThat(page().locator("p.rowsHeld").first().textContent())
        .as("the line above the list has to say how many runs there are and how many are listed")
        .contains(" test runs are known here")
        .contains(" have been touched and ")
        .contains("This list holds the 6 touched most recently")
        .contains("narrow the list to reach another");

    Map<String, Object> onALaptop = howTallTheListIs();
    say("a laptop", onALaptop);
    theListIsBoundedWhereAWallOfChipsGrows("a laptop", onALaptop);

    theRunATesterIsLookingForIsReachedByNarrowingTheList();
    theRunATesterJustLeftIsOneClickAwayFromAnother();
    aRunNobodyNeedsIsForgottenByClicking();

    browser().asAPhoneWouldShowIt();
    page().navigate(theDashboard(testRunId(), null));
    Map<String, Object> onAPhone = howTallTheListIs();
    say("a phone", onAPhone);
    theListIsBoundedWhereAWallOfChipsGrows("a phone", onAPhone);
    assertThat(numberOf(onAPhone, "now"))
        .as(
            "on a phone the run list is "
                + numberOf(onAPhone, "now")
                + "px tall where a wall of seventy chips is "
                + numberOf(onAPhone, "seventyChips")
                + "px")
        .isLessThan(numberOf(onAPhone, "seventyChips"));
    thePageDoesNotScrollSidewaysAtPhoneWidth("the dashboard with seventy test runs");

    theBrowserSawNothingGoWrong();
  }

  private void theRunATesterIsLookingForIsReachedByNarrowingTheList() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(theDashboard(testRunId(), null));

    PlaywrightAssertions.assertThat(theLinkTo(THE_RUN_A_TESTER_IS_LOOKING_FOR)).hasCount(0);

    page().getByLabel("test run name contains").fill("wizard");
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Narrow this list"))
        .click();
    PlaywrightAssertions.assertThat(theLinkTo(THE_RUN_A_TESTER_IS_LOOKING_FOR)).isVisible();
    theLinkTo(THE_RUN_A_TESTER_IS_LOOKING_FOR).click();

    PlaywrightAssertions.assertThat(page().locator("dl.pairs dd.id").first())
        .hasText(THE_RUN_A_TESTER_IS_LOOKING_FOR);
  }

  private void theRunATesterJustLeftIsOneClickAwayFromAnother() {
    page().navigate(theDashboard(THE_RUN_A_TESTER_IS_LOOKING_FOR, null));
    page()
        .getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Reset this test run in every provider"))
        .click();

    page().navigate(theDashboard(testRunId(), null));
    PlaywrightAssertions.assertThat(theLinkTo(THE_RUN_A_TESTER_IS_LOOKING_FOR)).isVisible();
    theLinkTo(THE_RUN_A_TESTER_IS_LOOKING_FOR).click();
    PlaywrightAssertions.assertThat(page().locator("dl.pairs dd.id").first())
        .hasText(THE_RUN_A_TESTER_IS_LOOKING_FOR);
  }

  private void aRunNobodyNeedsIsForgottenByClicking() {
    page().navigate(theDashboard(testRunId(), THE_RUN_A_TESTER_CLEANS_UP));

    Locator row = theRowOf(THE_RUN_A_TESTER_CLEANS_UP);
    PlaywrightAssertions.assertThat(row).isVisible();
    theForgetButtonIn(row).click();
    PlaywrightAssertions.assertThat(page().locator(".refusal")).isVisible();
    PlaywrightAssertions.assertThat(theRowOf(THE_RUN_A_TESTER_CLEANS_UP)).isVisible();

    theRowOf(THE_RUN_A_TESTER_CLEANS_UP).getByLabel("confirm").check();
    theForgetButtonIn(theRowOf(THE_RUN_A_TESTER_CLEANS_UP)).click();

    PlaywrightAssertions.assertThat(page().locator(".done")).isVisible();
    assertThat(page().locator(".done").textContent())
        .contains("Test run " + THE_RUN_A_TESTER_CLEANS_UP + " is forgotten")
        .contains("Its registration is dropped");

    PlaywrightAssertions.assertThat(page())
        .hasURL(Pattern.compile("runMatch=" + THE_RUN_A_TESTER_CLEANS_UP));
    PlaywrightAssertions.assertThat(theLinkTo(THE_RUN_A_TESTER_CLEANS_UP)).hasCount(0);
  }

  private static Locator theForgetButtonIn(Locator row) {
    return row.getByRole(
        AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Forget this test run for good"));
  }

  private Locator theRowOf(String testRunId) {
    return page().locator("tr").filter(new Locator.FilterOptions().setHas(theLinkTo(testRunId)));
  }

  private Locator theLinkTo(String testRunId) {
    return page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(testRunId).setExact(true));
  }

  private String theDashboard(String testRunId, String runMatch) {
    String narrowed = runMatch == null ? "" : "&runMatch=" + runMatch;
    return at("/ui/dashboard?provider=" + GROW + "&testRunId=" + testRunId + narrowed);
  }

  private Map<String, Object> howTallTheListIs() {
    @SuppressWarnings("unchecked")
    Map<String, Object> measured =
        (Map<String, Object>)
            page()
                .evaluate(
                    HOW_TALL_THE_LIST_IS_AND_HOW_TALL_A_WALL_OF_CHIPS_WOULD_BE,
                    List.of(theRunsOneMachineAccumulated(), aNamePerRun(700)));
    return measured;
  }

  private static void theListIsBoundedWhereAWallOfChipsGrows(
      String screen, Map<String, Object> measured) {
    assertThat(numberOf(measured, "now"))
        .as(
            "on "
                + screen
                + " the run list has to stay inside one screen however many runs there are; it is "
                + numberOf(measured, "now")
                + "px in a viewport "
                + numberOf(measured, "viewport")
                + "px tall")
        .isLessThanOrEqualTo(numberOf(measured, "viewport"));
    assertThat(numberOf(measured, "sevenHundredChips"))
        .as(
            "a wall of chips grows with every run registered, which is the shape this list"
                + " replaced; on "
                + screen
                + " seven hundred of them are "
                + numberOf(measured, "sevenHundredChips")
                + "px")
        .isGreaterThan(5 * numberOf(measured, "seventyChips"));
    assertThat(numberOf(measured, "now")).isLessThan(numberOf(measured, "sevenHundredChips") / 5);
  }

  private static void say(String screen, Map<String, Object> measured) {
    System.out.println(
        "on "
            + screen
            + " the run list is "
            + numberOf(measured, "now")
            + "px tall; a wall of seventy chips is "
            + numberOf(measured, "seventyChips")
            + "px and a wall of seven hundred is "
            + numberOf(measured, "sevenHundredChips")
            + "px, in a viewport "
            + numberOf(measured, "viewport")
            + "px tall");
  }

  private static int numberOf(Map<String, Object> measured, String key) {
    return ((Number) measured.get(key)).intValue();
  }

  private void seedTheseTestRuns(List<String> runs) {
    TheTestSetup seeding = new TheTestSetup(at(""));
    for (String run : runs) {
      seeding.configureAGrowAccount(run, ACCOUNT, "seeded-user", "seeded-page", List.of());
    }
  }

  private static List<String> theRunsOneMachineAccumulated() {
    List<String> runs = new ArrayList<>();
    runs.add(THE_RUN_A_TESTER_IS_LOOKING_FOR);
    for (int nth = 0; runs.size() < THE_RUNS_ONE_MACHINE_ACCUMULATED; nth++) {
      runs.add(String.format("seeded-%02d", nth));
    }
    return List.copyOf(runs);
  }

  private static List<String> aNamePerRun(int runs) {
    List<String> named = new ArrayList<>();
    for (int nth = 0; nth < runs; nth++) {
      named.add(String.format("seeded-%03d", nth));
    }
    return List.copyOf(named);
  }
}
