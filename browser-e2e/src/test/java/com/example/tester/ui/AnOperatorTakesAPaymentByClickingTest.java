package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnOperatorTakesAPaymentByClickingTest extends AJourneyInARealBrowser {

  private static final String A_USER_ID = "a-browser-grow-user";

  private static final String A_PAGE_CODE = "a-browser-grow-page";

  private static final String HELD = "120.50";

  private static final String A_HOLD = "Hold the money on the payer's card and settle it later";

  private static final String THE_CARD_THE_PAYER_PICKS = "sandbox-4580";

  private static final String HOW_MANY_REFUNDS = "how many refunds are allowed";

  private static final String TWO_REFUNDS_IN_WORDS =
      "two refunds per transaction, and a third one is refused";

  private static final String A_PANEL = "rgb(255, 255, 255)";

  private static final String DANGER_GROUND = "rgb(253, 237, 237)";

  private static final String DANGER = "rgb(143, 29, 29)";

  private static final String AN_ORDINARY_BUTTON = "rgb(21, 80, 127)";

  private static final String SUCCESS = "rgb(20, 83, 45)";

  private static final String SUCCESS_WASH = "rgb(236, 246, 240)";

  private static final String ATTENTION = "rgb(124, 71, 8)";

  private static final String PENDING = "rgb(30, 79, 138)";

  private static final String INK = "rgb(16, 21, 27)";

  private static final Pattern THE_OBJECT_THE_WIZARD_STARTED =
      Pattern.compile("[?&]started=([^&]+)");

  private static final String A_STEP_STILL_MOVES_IT = "a step still moves it: yes";

  private static final String NO_STEP_STILL_MOVES_IT = "a step still moves it: no";

  private static final String THE_PAYER_HAS_NOT_FINISHED =
      "because its payer has not finished with it";

  private static final String NOTHING_AT_ALL_IS_LEFT = "no payer step is left on it either";

  private static final String A_PAYER_IS_STILL_AWAITED =
      "What a payer has still to finish on this one";

  private static final String THE_PAGE_THE_PAYER_WAS_SENT_TO =
      "The page this payment's payer was sent to";

  @Test
  @DisplayName("a tester configures grow, starts a hold, finishes the payer's page and settles it")
  void anOperatorWalksTheWholePaymentWithNothingButClicks() {
    theAccountIsConfiguredAfterTheFormRefusesAnIncompleteOne();
    String process = aHoldIsStartedFromTheWizard();
    theHostedPageIsFinishedAsThePayerOnTheObjectScreen(process);
    String clickedThrough = theTransactionIsReachedByClickingTheObjectThisOneIsTiedTo();
    String transaction = theTransactionTheCompletionCreatedIsFoundOnThePaymentsScreen();
    assertThat(clickedThrough)
        .as(
            "the object the process names under transactionId and the object the list links to"
                + " are the same payment, reached by two roads")
        .isEqualTo(transaction);
    theHoldIsSettledOnlyAfterTheScreenRefusesAnUnconfirmedOne(transaction);

    theBrowserSawNothingGoWrong();
  }

  private void theAccountIsConfiguredAfterTheFormRefusesAnIncompleteOne() {
    page().navigate(theAccountForm());
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions()
                        .setName("Configure an account for this provider and test run")))
        .isVisible();
    theScriptTheHeadLoadsActuallyRan();
    thePageDoesNotScrollSidewaysAtPhoneWidth("the account form");

    page().getByLabel("account identity").fill(ACCOUNT);
    page().getByLabel("Grow user id").fill(A_USER_ID);
    page()
        .getByLabel(HOW_MANY_REFUNDS)
        .selectOption(new SelectOption().setLabel(TWO_REFUNDS_IN_WORDS));
    page().getByLabel("callback target for this account").fill(merchant().whereCallbacksGo());
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Configure this account"))
        .click();

    PlaywrightAssertions.assertThat(page().getByText("Grow page code is required by provider grow"))
        .isVisible();
    PlaywrightAssertions.assertThat(page().getByText("accounts[0].configuration.pageCode"))
        .isHidden();
    PlaywrightAssertions.assertThat(page().getByLabel("Grow user id")).hasValue("");
    PlaywrightAssertions.assertThat(page().getByText("retype this secret").first()).isVisible();
    PlaywrightAssertions.assertThat(page().getByLabel(HOW_MANY_REFUNDS)).hasValue("TWO_REFUNDS");

    page().getByLabel("account identity").fill(ACCOUNT);
    page().getByLabel("Grow user id").fill(A_USER_ID);
    page().getByLabel("Grow page code").fill(A_PAGE_CODE);
    page().getByLabel("callback target for this account").fill(merchant().whereCallbacksGo());
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Configure this account"))
        .click();

    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions()
                        .setName("Accounts configured for this provider and test run")))
        .isVisible();
    PlaywrightAssertions.assertThat(theRowOfTheAccount()).containsText("held by the provider");
    theChangeIsConfirmedInGreen("The account is configured for this test run.");
    theFormThatConfirmsItselfWarnsOfDanger(
        theFormWhoseButtonSays("Remove this account for good"), "removing an account");
    thePageDoesNotScrollSidewaysAtPhoneWidth("the Configuration screen");
  }

  private String aHoldIsStartedFromTheWizard() {
    page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Start a payment")).click();

    PlaywrightAssertions.assertThat(
            page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Step 1 of 2")))
        .isVisible();
    page().getByLabel("the account this payment is started on").selectOption(ACCOUNT);
    page().getByLabel(A_HOLD).check();
    aButtonThatMovesTheTesterOnIsPrimary("Next: what this payment says");
    page()
        .getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Next: what this payment says"))
        .click();

    PlaywrightAssertions.assertThat(
            page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Step 2 of 2")))
        .isVisible();
    thePageDoesNotScrollSidewaysAtPhoneWidth("the second step of the wizard");
    page().getByLabel("amount").fill(HELD);
    page().getByLabel("what the payer is paying for").fill("one seat, row F");
    page()
        .getByLabel("where the payer's browser goes after paying")
        .fill(merchant().whereAPayerLandsHavingPaid());
    page()
        .getByLabel("where the payer's browser goes if they give up")
        .fill(merchant().whereAPayerLandsHavingGivenUp());
    page().getByLabel("the payer's name").fill("Dana Levi");
    page().getByLabel("the payer's phone number").fill("0501234567");
    page().getByLabel("where grow posts the callback").selectOption(merchant().whereCallbacksGo());
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start this payment"))
        .click();

    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("This payment exists now")))
        .isVisible();
    return theObjectTheUrlNames();
  }

  private void theHostedPageIsFinishedAsThePayerOnTheObjectScreen(String process) {
    page()
        .getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions().setName("Look this payment up on the Payments screen"))
        .click();

    PlaywrightAssertions.assertThat(page().getByText(process).first()).isVisible();
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions()
                        .setName("Take the payer's own step on this payment")))
        .isVisible();
    thePageDoesNotScrollSidewaysAtPhoneWidth("the payment's own screen");
    aPaymentNobodyHasPaidYetSaysAStepStillMovesIt();

    Locator theFormThePayerFinishes = theFormWhoseButtonSays("Take this payer step");
    theConfirmIsOrdinary(theFormThePayerFinishes, "finishing a hosted page as the payer");
    theConfirmIsOrdinary(
        theFormWhoseButtonSays("Send a callback about this payment"),
        "sending a callback about a payment");
    theFormThePayerFinishes.getByLabel("the payer paid on the page").check();
    theFormThePayerFinishes
        .getByLabel("the card the payer used")
        .selectOption(THE_CARD_THE_PAYER_PICKS);
    theFormThePayerFinishes.getByRole(AriaRole.CHECKBOX).check();
    theFormThePayerFinishes.getByRole(AriaRole.BUTTON).click();

    PlaywrightAssertions.assertThat(page().getByText("already reached an outcome").first())
        .isVisible();
    theChangeIsConfirmedInGreen("This payer step is taken: the payer paid on the page.");
    aPaymentWhosePayerHasFinishedSaysSoAndSaysNoStepMovesIt();
  }

  private void aPaymentNobodyHasPaidYetSaysAStepStillMovesIt() {
    Locator stillMoves = theBadgeSaying(page().locator("main"), A_STEP_STILL_MOVES_IT);
    PlaywrightAssertions.assertThat(stillMoves).isVisible();
    assertThat(colourOf(stillMoves))
        .as(
            "that the payer's own step is still to be taken is a fact about this payment, not a"
                + " status, so it is written in the colour a fact is")
        .isEqualTo(INK)
        .isNotEqualTo(SUCCESS);
    PlaywrightAssertions.assertThat(theBadgeSaying(page().locator("main"), NO_STEP_STILL_MOVES_IT))
        .isHidden();
    PlaywrightAssertions.assertThat(page().getByText(THE_PAYER_HAS_NOT_FINISHED)).isVisible();
    PlaywrightAssertions.assertThat(page().getByText(NOTHING_AT_ALL_IS_LEFT)).isHidden();
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName(A_PAYER_IS_STILL_AWAITED)))
        .isVisible();
  }

  private void aPaymentWhosePayerHasFinishedSaysSoAndSaysNoStepMovesIt() {
    PlaywrightAssertions.assertThat(theBadgeSaying(page().locator("main"), NO_STEP_STILL_MOVES_IT))
        .isVisible();
    PlaywrightAssertions.assertThat(theBadgeSaying(page().locator("main"), A_STEP_STILL_MOVES_IT))
        .isHidden();
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName(THE_PAGE_THE_PAYER_WAS_SENT_TO)))
        .isVisible();
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName(A_PAYER_IS_STILL_AWAITED)))
        .isHidden();
    PlaywrightAssertions.assertThat(page().getByText(NOTHING_AT_ALL_IS_LEFT)).isVisible();
    PlaywrightAssertions.assertThat(page().getByText(THE_PAYER_HAS_NOT_FINISHED)).isHidden();
  }

  private String theTransactionIsReachedByClickingTheObjectThisOneIsTiedTo() {
    Locator tiedTo =
        page()
            .locator("section")
            .filter(
                new Locator.FilterOptions()
                    .setHas(
                        page()
                            .getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions()
                                    .setName("Objects this one is tied to"))))
            .first();
    Locator theTransactionItNames = theValueOfTheField(tiedTo, "transactionId");
    PlaywrightAssertions.assertThat(theTransactionItNames.getByRole(AriaRole.LINK)).isVisible();
    assertThat(theTransactionItNames.getByRole(AriaRole.LINK).count())
        .as(
            "the panel that names the object a tester now needs opens it, rather than printing an"
                + " identifier they have to find again further down the screen")
        .isEqualTo(1);
    assertThat(theTransactionItNames.innerText())
        .as("the object this payment names carries the status that object is at")
        .contains("READY_FOR_CAPTURE");
    String named = theTransactionItNames.getByRole(AriaRole.LINK).innerText().strip();

    theTransactionItNames.getByRole(AriaRole.LINK).click();

    PlaywrightAssertions.assertThat(page().getByText(named).first()).isVisible();
    Locator capturable = theBadgeSaying(page().locator("main"), "capturable: yes");
    PlaywrightAssertions.assertThat(capturable).isVisible();
    assertThat(capturable.count())
        .as("the object the tied-to panel opens is the one the capture is offered on")
        .isEqualTo(1);
    return named;
  }

  private Locator theValueOfTheField(Locator within, String field) {
    List<String> fields = within.locator("dt").allInnerTexts();
    int at = fields.indexOf(field);
    assertThat(at)
        .as("the panel that names the objects this one is tied to names " + field)
        .isNotNegative();
    return within.locator("dd").nth(at);
  }

  private String theTransactionTheCompletionCreatedIsFoundOnThePaymentsScreen() {
    page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Payments")).click();

    Locator theHold = theRowSaying("READY_FOR_CAPTURE");
    PlaywrightAssertions.assertThat(theHold).isVisible();
    assertThat(colourOf(theBadgeSaying(theHold, "READY_FOR_CAPTURE")))
        .as(
            "a hold waiting to be captured is the normal next step of a payment, not a problem,"
                + " so its status reads as pending and not as attention")
        .isEqualTo(PENDING)
        .isNotEqualTo(ATTENTION);
    aButtonThatOnlyReadsIsSecondary("Look up");
    aButtonThatOnlyReadsIsSecondary("Switch");
    String transaction = theHold.getByRole(AriaRole.LINK).first().innerText().strip();
    theHold.getByRole(AriaRole.LINK).first().click();
    PlaywrightAssertions.assertThat(page().getByText(transaction).first()).isVisible();
    Locator capturable = theBadgeSaying(page().locator("main"), "capturable: yes");
    PlaywrightAssertions.assertThat(capturable).isVisible();
    assertThat(colourOf(capturable))
        .as(
            "that a hold can still be captured is a fact about it, not a status, so it is not"
                + " written in the colour a success is")
        .isEqualTo(INK)
        .isNotEqualTo(SUCCESS);
    return transaction;
  }

  private void theHoldIsSettledOnlyAfterTheScreenRefusesAnUnconfirmedOne(String transaction) {
    Locator theFormThatSettles = theFormWhoseButtonSays("capture or complete the hold");
    PlaywrightAssertions.assertThat(theFormThatSettles.getByLabel("amount")).hasValue(HELD);
    theConfirmIsOrdinary(theFormThatSettles, "settling a hold");
    theFormThatSettles.getByRole(AriaRole.BUTTON).click();

    PlaywrightAssertions.assertThat(
            page().getByText("tick confirm before moving money on a payment"))
        .isVisible();

    Locator theFormThatSettlesAgain = theFormWhoseButtonSays("capture or complete the hold");
    theFormThatSettlesAgain.getByRole(AriaRole.CHECKBOX).check();
    theFormThatSettlesAgain.getByRole(AriaRole.BUTTON).click();

    PlaywrightAssertions.assertThat(page().getByText("SUCCEEDED").first()).isVisible();
    assertThat(page().url()).contains(transaction);
    theChangeIsConfirmedInGreen("The provider performed the action on this payment.");
    assertThat(colourOf(theBadgeSaying(page().locator("main"), "SUCCEEDED")))
        .as(
            "a settled payment reads as a success, in a colour of its own and not the one the"
                + " hold it was read in")
        .isEqualTo(SUCCESS)
        .isNotEqualTo(PENDING);
    theConfirmWarnsOfDanger(
        theFormWhoseButtonSays("refund what was captured"), "refunding a settled payment");
    theOperationListAnswersWhetherItClosedTheHoldInTheWordsOfTheRestOfTheScreen();
  }

  private void theOperationListAnswersWhetherItClosedTheHoldInTheWordsOfTheRestOfTheScreen() {
    Locator operations =
        page()
            .locator("section")
            .filter(
                new Locator.FilterOptions()
                    .setHas(
                        page()
                            .getByRole(
                                AriaRole.HEADING,
                                new Page.GetByRoleOptions()
                                    .setName("Operations, in the order they happened"))))
            .first();
    assertThat(theColumnUnder(operations, "closes the authorization"))
        .as(
            "a column of this screen answers yes or no as every other answer on it does, and"
                + " never in the words a boolean prints itself with")
        .containsExactly("no", "yes")
        .doesNotContain("true", "false");
  }

  private List<String> theColumnUnder(Locator table, String heading) {
    List<String> headings = new ArrayList<>();
    for (String printed : table.locator("thead th").allInnerTexts()) {
      headings.add(printed.strip().toLowerCase(Locale.ROOT));
    }
    int at = headings.indexOf(heading);
    assertThat(at).as("the operation list carries a " + heading + " column").isNotNegative();
    return table.locator("tbody tr").all().stream()
        .map(row -> row.locator("td").nth(at).innerText().strip())
        .toList();
  }

  private void theConfirmIsOrdinary(Locator form, String what) {
    PlaywrightAssertions.assertThat(form).isVisible();
    assertThat(backgroundOf(form.locator(".confirmGroup")))
        .as(what + " destroys nothing, so its confirm must not be painted as a danger")
        .isEqualTo(A_PANEL);
    assertThat(backgroundOf(form.getByRole(AriaRole.BUTTON)))
        .as(what + " destroys nothing, so its button is an ordinary one")
        .isEqualTo(AN_ORDINARY_BUTTON);
  }

  private void theConfirmWarnsOfDanger(Locator form, String what) {
    PlaywrightAssertions.assertThat(form).isVisible();
    assertThat(backgroundOf(form.locator(".confirmGroup")))
        .as(what + " undoes money that moved, so its confirm is painted as a danger")
        .isEqualTo(DANGER_GROUND);
    assertThat(backgroundOf(form.getByRole(AriaRole.BUTTON)))
        .as(what + " undoes money that moved, so its button is painted as a danger")
        .isEqualTo(DANGER);
  }

  private void theFormThatConfirmsItselfWarnsOfDanger(Locator form, String what) {
    PlaywrightAssertions.assertThat(form).isVisible();
    assertThat(backgroundOf(form))
        .as(
            what
                + " destroys something, so the form that is its own confirm is painted as a"
                + " danger")
        .isEqualTo(DANGER_GROUND);
    assertThat(backgroundOf(form.getByRole(AriaRole.BUTTON)))
        .as(what + " destroys something, so its button is painted as a danger")
        .isEqualTo(DANGER);
  }

  private void theChangeIsConfirmedInGreen(String said) {
    Locator notice = page().locator(".done");
    PlaywrightAssertions.assertThat(notice).hasText(said);
    assertThat(colourOf(notice))
        .as("the notice of a change that happened is written in the success colour")
        .isEqualTo(SUCCESS);
    assertThat(backgroundOf(notice))
        .as("the notice of a change that happened is washed in the success colour")
        .isEqualTo(SUCCESS_WASH);
  }

  private void aButtonThatOnlyReadsIsSecondary(String label) {
    Locator button =
        page()
            .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label).setExact(true));
    PlaywrightAssertions.assertThat(button).isVisible();
    assertThat(backgroundOf(button))
        .as(label + " submits a form that only reads, so it is painted on the panel, not filled")
        .isEqualTo(A_PANEL);
    assertThat(colourOf(button))
        .as(label + " keeps the accent for its label, so it still reads as a button")
        .isEqualTo(AN_ORDINARY_BUTTON);
  }

  private void aButtonThatMovesTheTesterOnIsPrimary(String label) {
    Locator button =
        page()
            .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label).setExact(true));
    PlaywrightAssertions.assertThat(button).isVisible();
    assertThat(backgroundOf(button))
        .as(
            label
                + " submits a form that only reads, but it is the one way forward on its screen,"
                + " so it is filled as the primary button and not left on the panel")
        .isEqualTo(AN_ORDINARY_BUTTON)
        .isNotEqualTo(A_PANEL);
  }

  private Locator theBadgeSaying(Locator within, String status) {
    return within.locator(".tag").filter(new Locator.FilterOptions().setHasText(status)).first();
  }

  private String colourOf(Locator element) {
    page().mouse().move(0, 0);
    return (String) element.evaluate("(element) => getComputedStyle(element).color");
  }

  private String backgroundOf(Locator element) {
    page().mouse().move(0, 0);
    return (String) element.evaluate("(element) => getComputedStyle(element).backgroundColor");
  }

  private Locator theRowOfTheAccount() {
    return page().locator("tr").filter(new Locator.FilterOptions().setHasText(ACCOUNT)).first();
  }

  private Locator theRowSaying(String status) {
    return page()
        .locator("tbody tr")
        .filter(new Locator.FilterOptions().setHasText(status))
        .first();
  }

  private Locator theFormWhoseButtonSays(String button) {
    return page()
        .locator("form")
        .filter(
            new Locator.FilterOptions()
                .setHas(
                    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(button))))
        .first();
  }

  private String theObjectTheUrlNames() {
    Matcher started = THE_OBJECT_THE_WIZARD_STARTED.matcher(page().url());
    assertThat(started.find())
        .as("the wizard names the object it started in the address it sends the browser to")
        .isTrue();
    return started.group(1);
  }
}
