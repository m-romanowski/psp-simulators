package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class AJourneyInARealBrowser {

  static final String GROW = "grow";

  static final String ACCOUNT = "merchant-1";

  private static final double EVERY_ASSERTION_RETRIES_FOR_THIS_LONG = 15_000;

  private SimulatorUnderTest simulator;
  private TheBrowser browser;
  private TheMerchant merchant;
  private NotThroughTheBrowser outsideTheBrowser;
  private TheTestSetup setup;
  private String testRunId;

  @BeforeEach
  void openEverythingThisJourneyNeeds() {
    PlaywrightAssertions.setDefaultAssertionTimeout(EVERY_ASSERTION_RETRIES_FOR_THIS_LONG);
    simulator = SimulatorUnderTest.running();
    outsideTheBrowser = new NotThroughTheBrowser(simulator.baseUrl(), new TheProviderApi());
    setup = new TheTestSetup(simulator.baseUrl());
    merchant = new TheMerchant();
    browser = TheBrowser.opened();
    testRunId = "browser-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @AfterEach
  void closeEverythingAndLeaveNoAccountBehind() {
    if (browser != null) {
      browser.close();
    }
    if (merchant != null) {
      merchant.close();
    }
    if (setup != null) {
      setup.forgetTheTestRunAndItsAccount(GROW, testRunId, ACCOUNT);
    }
  }

  Page page() {
    return browser.page();
  }

  TheBrowser browser() {
    return browser;
  }

  TheMerchant merchant() {
    return merchant;
  }

  NotThroughTheBrowser outsideTheBrowser() {
    return outsideTheBrowser;
  }

  String testRunId() {
    return testRunId;
  }

  TheTestSetup setup() {
    return setup;
  }

  String at(String path) {
    return simulator.baseUrl() + path;
  }

  String theAccountForm() {
    return at("/ui/configuration/account?provider=" + GROW + "&testRunId=" + testRunId);
  }

  void theBrowserSawNothingGoWrong() {
    assertThat(browser.whatWentWrong())
        .as(
            "a browser is the only thing that can see a console error, an uncaught script"
                + " exception or a request this journey made that was answered 4xx or 5xx")
        .isEmpty();
  }

  void thePageDoesNotScrollSidewaysAtPhoneWidth(String whichScreen) {
    browser.asAPhoneWouldShowIt();
    int document = browser.howWideTheDocumentIs();
    int viewport = browser.howWideTheViewportIs();
    assertThat(document)
        .as(
            whichScreen
                + " declares width=device-width and carries a rule for widths under 840px, so at "
                + TheBrowser.A_PHONE_IS_THIS_WIDE
                + "px it must not push the page sideways; the document is "
                + document
                + "px wide in a "
                + viewport
                + "px viewport")
        .isLessThanOrEqualTo(viewport);
    browser.asADeskWouldShowIt();
  }

  void theGrowAccountIsConfiguredByClicking(String userId, String pageCode) {
    page().navigate(theAccountForm());
    page().getByLabel("account identity").fill(ACCOUNT);
    page().getByLabel("Grow user id").fill(userId);
    page().getByLabel("Grow page code").fill(pageCode);
    page().getByLabel("callback target for this account").fill(merchant.whereCallbacksGo());
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
  }

  void theScriptTheHeadLoadsActuallyRan() {
    assertThat(page().evaluate("() => typeof window.htmx"))
        .as(
            "every screen's head loads htmx from a webjar path Thymeleaf writes; only a browser"
                + " can say whether that path resolved and the script evaluated")
        .isEqualTo("object");
  }
}
