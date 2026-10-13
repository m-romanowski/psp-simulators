package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ATesterReadsTheRunNothingResolvedTest extends AJourneyInARealBrowser {

  private static final String A_USER_ID = "a-run-nothing-resolved-user";

  private static final String A_PAGE_CODE = "a-run-nothing-resolved-page";

  private static final String THE_RUN_THE_APP_NAMED = "a-run-nothing-of-this-simulator-holds";

  private static final String THE_RESERVED_RUN = "UNKNOWN";

  private static final String THE_FAILED_RESOLUTIONS = "Requests whose assignment failed";

  private static final String THE_PANEL_THE_RESERVED_RUN_CARRIES =
      "The namespace of a failed resolution";

  private static final String THE_EXCHANGES_OF_THE_TRAIL =
      "section.panel:has(h2:text-is('Exchanges, oldest first')) tbody tr";

  private static final String A_HEADER_THAT_NAMED_NOTHING = "TEST_HEADER_UNMATCHED";

  @BeforeEach
  void aGrowAccountAndOneRequestWhoseHeaderNamedARunNothingHolds() {
    theGrowAccountIsConfiguredByClicking(A_USER_ID, A_PAGE_CODE);
    assertThat(
            outsideTheBrowser()
                .whatGrowAnswersAnAppWhoseHeaderNamesARunNothingHolds(
                    THE_RUN_THE_APP_NAMED, theFormAMisconfiguredAppSends()))
        .as("grow answers this request in its own words, and the trail is where it is read back")
        .isEqualTo(200);
  }

  @Test
  @DisplayName(
      "a tester reading their own run's trail clicks through to the requests whose assignment"
          + " failed, and reads there the test-run header their own application sent")
  void theTrailOfTheReservedNamespaceIsReachedByClickingAndNamesTheHeaderThatWasSent() {
    page().navigate(theTrailOf(testRunId()));

    assertThat(theHeadingOfTheReservedPanel().count())
        .as("the run a tester configured is not the namespace of a failed resolution")
        .isZero();

    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(THE_FAILED_RESOLUTIONS))
        .click();

    assertThat(page().url()).contains("testRunId=" + THE_RESERVED_RUN);
    PlaywrightAssertions.assertThat(theHeadingOfTheReservedPanel()).isVisible();
    assertThat(page().innerText("body"))
        .as(
            "this assertion read \"a screen that writes refuses this namespace\" until that"
                + " classification was measured false, and now reads the way out this panel hands"
                + " a tester instead")
        .contains("none of them belongs to a test run a tester configured")
        .contains("a test-run header naming a run this simulator holds nothing under")
        .contains("Type a run id in the test run box at the top of this page");
    assertThat(
            page()
                .getByRole(
                    AriaRole.LINK, new Page.GetByRoleOptions().setName(THE_FAILED_RESOLUTIONS))
                .count())
        .as("the screen reading this namespace offers no link to the namespace it is reading")
        .isZero();

    Locator theRequestOfTheMisconfiguredApp =
        page()
            .locator(THE_EXCHANGES_OF_THE_TRAIL)
            .filter(new Locator.FilterOptions().setHasText(A_HEADER_THAT_NAMED_NOTHING))
            .filter(new Locator.FilterOptions().setHasText("createPaymentProcess"))
            .filter(new Locator.FilterOptions().setHasText("INBOUND_REQUEST"))
            .last();
    theRequestOfTheMisconfiguredApp
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("open").setExact(true))
        .click();
    page()
        .getByRole(
            AriaRole.LINK, new Page.GetByRoleOptions().setName("Show the values of this exchange"))
        .click();

    assertThat(page().innerText("body"))
        .as(
            "a tester asks this trail which request their application sent with the wrong test-run"
                + " header, and the answer is the header it carried")
        .contains("X-Simulator-Test-Run-Id")
        .contains(THE_RUN_THE_APP_NAMED);
    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "the screen reading the reserved namespace hands a tester on to the one that explains each"
          + " request, and a screen that namespace is no test run of refuses it and says how to"
          + " reach a run")
  void theTwoScreensThatReadThisNamespaceLinkEachOtherAndAScreenItIsNoRunOfRefusesIt() {
    page().navigate(theTrailOf(THE_RESERVED_RUN));

    page()
        .getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions().setName("Why each of these requests was assigned here"))
        .click();

    assertThat(page().url()).contains("/ui/correlation").contains("testRunId=" + THE_RESERVED_RUN);
    PlaywrightAssertions.assertThat(theHeadingOfTheReservedPanel()).isVisible();
    assertThat(page().innerText("body")).contains(A_HEADER_THAT_NAMED_NOTHING);

    page()
        .getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions()
                .setName("The whole trail of this namespace, requests and answers"))
        .click();

    assertThat(page().url()).contains("/ui/audit").contains("testRunId=" + THE_RESERVED_RUN);

    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Scenarios").setExact(true))
        .click();

    assertThat(page().url()).contains("testRunId=" + THE_RESERVED_RUN);
    PlaywrightAssertions.assertThat(page().locator("div.refusal")).isVisible();
    assertThat(page().innerText("div.refusal"))
        .as(
            "the rail carries the namespace being read to every screen, and a screen whose subject"
                + " is what one test run holds refuses it rather than offering a tester a write the"
                + " store would throw on, or a page with nothing of a run on it")
        .contains("reserved for a failed resolution")
        .contains("Type a run id in the test run box at the top of this page")
        .contains("the Audit screen and the Correlation screen");
    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "a tester who landed on the namespace of a failed resolution leaves it the way the panel"
          + " names, by emptying the test run box of the bar")
  void theWayOutTheReservedNamespaceNamesIsOneATesterCanTake() {
    page().navigate(theTrailOf(THE_RESERVED_RUN));
    PlaywrightAssertions.assertThat(theHeadingOfTheReservedPanel()).isVisible();

    page().getByLabel("test run").fill("");
    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Switch")).click();

    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.LINK, new Page.GetByRoleOptions().setName(THE_FAILED_RESOLUTIONS)))
        .isVisible();
    PlaywrightAssertions.assertThat(theHeadingOfTheReservedPanel()).hasCount(0);
    assertThat(page().locator("div.refusal").count())
        .as(
            "an empty box reads the run this simulator uses when a request names none, and"
                + " leaving the namespace is no refusal of its own")
        .isZero();
    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("a tester types the reserved namespace into the bar of the trail and reads it there")
  void theBarOfTheTrailSwitchesToTheNamespaceOfAFailedResolution() {
    page().navigate(theTrailOf(testRunId()));

    page().getByLabel("test run").fill(THE_RESERVED_RUN);
    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Switch")).click();

    PlaywrightAssertions.assertThat(theHeadingOfTheReservedPanel()).isVisible();
    assertThat(page().locator("div.refusal").count())
        .as("the bar of a screen that only reads switches to this namespace without a refusal")
        .isZero();
    theBrowserSawNothingGoWrong();
  }

  private Locator theHeadingOfTheReservedPanel() {
    return page()
        .getByRole(
            AriaRole.HEADING,
            new Page.GetByRoleOptions().setName(THE_PANEL_THE_RESERVED_RUN_CARRIES));
  }

  private String theTrailOf(String testRunId) {
    return at("/ui/audit?provider=" + GROW + "&testRunId=" + testRunId);
  }

  private Map<String, String> theFormAMisconfiguredAppSends() {
    return NotThroughTheBrowser.form(
        "userId",
        A_USER_ID,
        "pageCode",
        A_PAGE_CODE,
        "chargeType",
        "2",
        "sum",
        "42.00",
        "description",
        "one seat, row B",
        "successUrl",
        merchant().whereAPayerLandsHavingPaid(),
        "cancelUrl",
        merchant().whereAPayerLandsHavingGivenUp(),
        "pageField[fullName]",
        "Noa Katz",
        "pageField[phone]",
        "0501234567");
  }
}
