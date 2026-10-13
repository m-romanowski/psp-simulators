package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ATrafficRowOpensTheExchangeItIsTest extends AJourneyInARealBrowser {

  private static final int A_LAPTOP_IS_THIS_WIDE = 1280;

  private static final int A_LAPTOP_IS_THIS_TALL = 700;

  private static final String A_USER_ID = "a-traffic-row-user";

  private static final String A_PAGE_CODE = "a-traffic-row-page";

  private static final String CHARGED = "42.00";

  private static final String WHAT_IS_BEING_PAID_FOR = "one seat, row A";

  private static final String THE_PAYER = "Noa Katz";

  private static final String THE_TRAFFIC_PANEL =
      "section.panel:has(h2:text-is('Recent traffic, in both directions')) tbody tr";

  private static final String THE_EXCHANGES_OF_THE_TRAIL =
      "section.panel:has(h2:text-is('Exchanges, oldest first')) td.stamp";

  private static final String NO_OBJECT_ANSWERS_TO_IT = "pay_no_object_answers_to_it";

  private static final int THE_EXCHANGES_THESE_TWO_CALLS_RECORD = 4;

  private static final int THE_ROWS_OF_THOSE_TWO_THAT_NAME_THE_PROCESS = 2;

  private static final int WHERE_A_TRAFFIC_ROW_NAMES_ITS_PATH = 5;

  private static final int WHERE_A_TRAFFIC_ROW_NAMES_ITS_OBJECT = 7;

  @BeforeEach
  void aGrowAccountAndTwoExchangesOfOneProcess() {
    theGrowAccountIsConfiguredByClicking(A_USER_ID, A_PAGE_CODE);
    NotThroughTheBrowser.GrowProcess process =
        outsideTheBrowser()
            .theHostedPaymentProcessGrowStarts(
                testRunId(),
                NotThroughTheBrowser.form(
                    "userId",
                    A_USER_ID,
                    "pageCode",
                    A_PAGE_CODE,
                    "chargeType",
                    "2",
                    "sum",
                    CHARGED,
                    "description",
                    WHAT_IS_BEING_PAID_FOR,
                    "successUrl",
                    merchant().whereAPayerLandsHavingPaid(),
                    "cancelUrl",
                    merchant().whereAPayerLandsHavingGivenUp(),
                    "pageField[fullName]",
                    THE_PAYER,
                    "pageField[phone]",
                    "0501234567",
                    "notifyUrl",
                    merchant().whereCallbacksGo()));
    outsideTheBrowser().theProcessGrowReadsBack(testRunId(), A_PAGE_CODE, process);
  }

  @Test
  @DisplayName(
      "a traffic row on the dashboard opens the one exchange it is, and not every exchange of the"
          + " object that exchange touched")
  void clickingATrafficRowLandsOnThatOneExchangeAndNotOnAPageThatMerelyHoldsIt() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(theDashboard());

    Locator newest = page().locator(THE_TRAFFIC_PANEL).first();
    String thePathItNames = whatTheRowNames(newest, WHERE_A_TRAFFIC_ROW_NAMES_ITS_PATH);
    String theObjectItTouched = whatTheRowNames(newest, WHERE_A_TRAFFIC_ROW_NAMES_ITS_OBJECT);

    assertThat(theObjectItTouched).isNotEmpty();
    newest
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("This exchange"))
        .click();

    PlaywrightAssertions.assertThat(page().locator(THE_EXCHANGES_OF_THE_TRAIL)).hasCount(1);
    PlaywrightAssertions.assertThat(page().getByText(thePathItNames).first()).isVisible();
    PlaywrightAssertions.assertThat(page().getByText("Show the values of this exchange"))
        .isVisible();
    assertThat(page().url()).contains("auditId=");

    page().goBack();
    Locator theSameRow = page().locator(THE_TRAFFIC_PANEL).first();
    theSameRow
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Exchanges"))
        .click();

    PlaywrightAssertions.assertThat(page().locator(THE_EXCHANGES_OF_THE_TRAIL))
        .hasCount(THE_ROWS_OF_THOSE_TWO_THAT_NAME_THE_PROCESS);
    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "a traffic row that touched no object is still one click away from the exchange it is")
  void anExchangeThatNamesNoObjectIsReachedByTheLinkOfItsOwnRow() {
    outsideTheBrowser()
        .theProcessGrowReadsBack(
            testRunId(),
            A_PAGE_CODE,
            new NotThroughTheBrowser.GrowProcess(NO_OBJECT_ANSWERS_TO_IT, "no-such-token", ""));
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(theDashboard());

    Locator touchingNoObject = theFirstTrafficRowNamingNoObject();
    String thePathItNames = whatTheRowNames(touchingNoObject, WHERE_A_TRAFFIC_ROW_NAMES_ITS_PATH);

    touchingNoObject
        .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("This exchange"))
        .click();

    PlaywrightAssertions.assertThat(page().locator(THE_EXCHANGES_OF_THE_TRAIL)).hasCount(1);
    PlaywrightAssertions.assertThat(page().getByText(thePathItNames).first()).isVisible();
    assertThat(page().url()).contains("auditId=");
    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "the panel offers one exchange link per traffic row, counted against the rows the two calls"
          + " of this journey recorded")
  void everyTrafficRowCarriesTheLinkToItselfAndNoRowCarriesASecondOne() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(theDashboard());

    PlaywrightAssertions.assertThat(page().locator(THE_TRAFFIC_PANEL))
        .hasCount(THE_EXCHANGES_THESE_TWO_CALLS_RECORD);
    PlaywrightAssertions.assertThat(
            page()
                .locator(THE_TRAFFIC_PANEL)
                .getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("This exchange")))
        .hasCount(THE_EXCHANGES_THESE_TWO_CALLS_RECORD);
    theBrowserSawNothingGoWrong();
  }

  private Locator theFirstTrafficRowNamingNoObject() {
    Locator rows = page().locator(THE_TRAFFIC_PANEL);
    int held = rows.count();
    for (int row = 0; row < held; row++) {
      Locator candidate = rows.nth(row);
      if (whatTheRowNames(candidate, WHERE_A_TRAFFIC_ROW_NAMES_ITS_OBJECT).isEmpty()) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "no traffic row of this run names no object, so this journey cannot show that such a row"
            + " is reachable; the panel holds "
            + held
            + " rows");
  }

  private static String whatTheRowNames(Locator row, int cell) {
    return row.locator("td").nth(cell).innerText().strip();
  }

  private String theDashboard() {
    return at("/ui/dashboard?provider=" + GROW + "&testRunId=" + testRunId());
  }
}
