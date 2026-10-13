package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerFinishesGrowsHostedPageTest extends AJourneyInAMobileAppsWebview {

  private static final String GROW = "grow";

  private static final String A_USER_ID = "a-payer-grow-user";

  private static final String A_PAGE_CODE = "a-payer-grow-page";

  private static final String CHARGED = "42.00";

  private static final String WHAT_IS_BEING_PAID_FOR = "two seats, row C";

  private static final String THE_PAYER = "Dana Levi";

  private static final String THE_CARD_THE_PAYER_PICKS = "sandbox-4580";

  private static final String THE_NUMBER_THAT_CARD_STANDS_FOR = "4580458045804580";

  private static final String PAID_ON_THE_PAGE = "paid on the page";

  private static final String A_HOLD_READY_FOR_CAPTURE = "11";

  @Override
  String provider() {
    return GROW;
  }

  @BeforeEach
  void theGrowAccountHasTwoCallbackTargets() {
    setup()
        .configureAGrowAccount(
            testRunId(),
            ACCOUNT,
            A_USER_ID,
            A_PAGE_CODE,
            List.of(
                merchant().anotherCallbackTargetOfTheSameAccount(), merchant().whereCallbacksGo()));
  }

  @Test
  @DisplayName(
      "a payer pays on grow's page in the app's webview, lands back at the shop, the shop is"
          + " called back and reads the paid process over grow's own route")
  void aPayerFinishesTheHostedPageGrowServesToTheAppsWebview() {
    thePayerHoldsTheCard(THE_NUMBER_THAT_CARD_STANDS_FOR);
    NotThroughTheBrowser.GrowProcess process =
        shopsServer()
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

    page().navigate(process.url());

    PlaywrightAssertions.assertThat(page().getByText(WHAT_IS_BEING_PAID_FOR)).isVisible();
    PlaywrightAssertions.assertThat(page().getByText(CHARGED)).isVisible();
    PlaywrightAssertions.assertThat(page().getByText(THE_PAYER)).isVisible();
    thePageDoesNotScrollSideways("grow's hosted page");

    page().locator("select").selectOption(THE_CARD_THE_PAYER_PICKS);
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(PAID_ON_THE_PAGE))
        .click();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    assertThat(page().url())
        .as("grow sends the payer's webview to the successUrl the process was started with")
        .startsWith(merchant().whereAPayerLandsHavingPaid());

    Map<String, String> callback =
        merchant().theCallbackNaming("data[processId]", process.id(), Duration.ofSeconds(20));
    assertThat(callback)
        .as("grow posts the paid process to the notifyUrl the shop's server gave it")
        .containsEntry("status", "1")
        .containsEntry("data[statusCode]", A_HOLD_READY_FOR_CAPTURE)
        .containsEntry("data[sum]", CHARGED)
        .containsEntry("data[cardSuffix]", "4580")
        .containsEntry("data[processId]", process.id())
        .containsEntry("data[processToken]", process.token())
        .containsKey("data[transactionId]");

    String readBack = shopsServer().theProcessGrowReadsBack(testRunId(), A_PAGE_CODE, process);
    assertThat(readBack)
        .as("the shop's server reads the paid process back with getPaymentProcessInfo")
        .containsPattern(field("statusCode", A_HOLD_READY_FOR_CAPTURE))
        .containsPattern(field("cardSuffix", "4580"))
        .containsPattern(field("transactionId", callback.get("data[transactionId]")))
        .containsPattern(field("processId", process.id()));

    assertThat(merchant().callbacksReceivedAtTheOtherTarget())
        .as("the account's other callback target is sent nothing: grow posts to the notifyUrl")
        .isEmpty();
    theWebviewSawNothingGoWrong();
  }

  private static String field(String name, String value) {
    return "\"" + name + "\"\\s*:\\s*\"?" + Pattern.quote(value) + "\"?[,}]";
  }
}
