package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitUntilState;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

abstract class AnAzericardWebviewJourney extends AJourneyInAMobileAppsWebview {

  static final String A_PUBLISHED_TEST_CARD = "4127208104942601";

  static final String AN_UNKNOWN_CARD = "4000000000000002";

  private static final String THE_APP_SCREEN = "http://the-app.example/checkout";

  private static final Duration A_CALLBACK_ARRIVES_WITHIN = Duration.ofSeconds(20);

  private AzericardShopBackend shop;

  @Override
  String provider() {
    return AzericardShopBackend.AZERICARD;
  }

  @BeforeEach
  void theShopsTerminalIsConfigured() {
    shop = new AzericardShopBackend(at(""), providerApi());
    setup()
        .configureAnAzericardTerminal(
            testRunId(),
            ACCOUNT,
            shop.terminal(),
            shop.itsPublicKey(),
            shop.theKeyTheGatewaySignsWith(),
            List.of(
                merchant().anotherCallbackTargetOfTheSameAccount(), merchant().whereCallbacksGo()));
  }

  void theWebviewPostsTheSignedForm(Map<String, String> signed) {
    theWebviewPostsTheSignedFormTo(AzericardShopBackend.CARD_SAVE_URL, signed);
  }

  void theWebviewPostsTheSignedFormTo(String path, Map<String, String> signed) {
    String html = AzericardShopBackend.theWebviewPosting(at(path), signed);
    page()
        .route(
            THE_APP_SCREEN,
            route ->
                route.fulfill(
                    new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType("text/html; charset=utf-8")
                        .setBody(html)));
    page().navigate(THE_APP_SCREEN, new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT));
    PlaywrightAssertions.assertThat(page().getByLabel("Card number")).isVisible();
    thePageDoesNotScrollSideways("azericard's card page");
  }

  AzericardShopBackend shop() {
    return shop;
  }

  void thePayerTypes(String number, String expiry, String cvc) {
    thePayerHoldsTheCard(number);
    page().getByLabel("Card number").fill(number);
    page().getByLabel("Expiry (MM/YY)").fill(expiry);
    page().getByLabel("CVC").fill(cvc);
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pay").setExact(true))
        .click();
  }

  void thePayerReads(String outcome) {
    PlaywrightAssertions.assertThat(
            page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(outcome)))
        .isVisible();
  }

  void thePayerReturnsTo(String merchantUrl, String whatTheShopSays) {
    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Return to the merchant"))
        .click();
    PlaywrightAssertions.assertThat(page().getByText(whatTheShopSays)).isVisible();
    assertThat(page().url())
        .as("azericard's page sends the payer's webview back to the MERCH_URL the shop signed")
        .isEqualTo(merchantUrl);
  }

  Map<String, String> theSignedCallbackFor(String order) {
    Map<String, String> callback =
        merchant().theCallbackNaming("ORDER", order, A_CALLBACK_ARRIVES_WITHIN);
    assertThat(shop.theGatewaySigned(callback))
        .as("the callback for order " + order + " carries a P_SIGN the gateway's key verifies")
        .isTrue();
    return callback;
  }

  void noCallbackWentToTheAccountsOtherTarget() {
    assertThat(merchant().callbacksReceivedAtTheOtherTarget())
        .as("the account's other callback target is sent nothing: azericard posts to BACKREF")
        .isEmpty();
  }
}
