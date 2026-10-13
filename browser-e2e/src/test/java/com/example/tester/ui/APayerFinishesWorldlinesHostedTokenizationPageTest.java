package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerFinishesWorldlinesHostedTokenizationPageTest extends AJourneyInAMobileAppsWebview {

  private static final String WORLDLINE = "worldline";

  private static final String A_MERCHANT_ID = "a-payer-worldline-pspid";

  private static final String AN_API_KEY = "a-payer-worldline-key";

  private static final String THE_CARD_THE_PAYER_PICKS = "published-1111";

  private static final String THE_CARD_AS_THE_PAYER_SEES_IT = "card ending 1111, expiring 1230";

  private static final String THE_CARD_AS_WORLDLINE_MASKS_IT = "************1111";

  private static final String STORE_THIS_CARD = "Store this card";

  private static final String THE_CARD_IS_STORED = "The card is stored";

  @Override
  String provider() {
    return WORLDLINE;
  }

  @BeforeEach
  void theWorldlineAccountIsConfigured() {
    setup().configureAWorldlineAccount(testRunId(), ACCOUNT, A_MERCHANT_ID, AN_API_KEY);
  }

  @Test
  @DisplayName(
      "a payer stores a card on the hosted tokenization url worldline hands a shop, opened in the"
          + " app's webview, and the shop reads the token back and charges it")
  void aPayerStoresACardOnTheHostedTokenizationPageOpenedInTheAppsWebview() {
    NotThroughTheBrowser.HostedTokenization opened = aSessionTheShopsServerOpens();

    page().navigate(opened.url());
    thePageDoesNotScrollSideways("worldline's hosted tokenization page");

    storeTheCard(page().getByLabel("Test card"), page().getByRole(AriaRole.BUTTON, aButton()));
    PlaywrightAssertions.assertThat(page().getByText(THE_CARD_IS_STORED)).isVisible();

    theShopReadsTheTokenAndChargesIt(opened);
    theWebviewSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "a payer stores a card inside the frame of the shop's own checkout in the app's webview and"
          + " never leaves the shop's page")
  void aPayerStoresACardInsideTheFrameOfTheShopsOwnCheckout() {
    NotThroughTheBrowser.HostedTokenization opened = aSessionTheShopsServerOpens();

    page().navigate(merchant().whereTheShopFrames(opened.url()));
    FrameLocator form = page().frameLocator("iframe");

    storeTheCard(form.getByLabel("Test card"), form.getByRole(AriaRole.BUTTON, aFramedButton()));
    PlaywrightAssertions.assertThat(form.getByText(THE_CARD_IS_STORED)).isVisible();
    PlaywrightAssertions.assertThat(page().getByText("Pay the shop")).isVisible();
    assertThat(page().url())
        .as("the frame sends the payer nowhere: the webview is still on the shop's own checkout")
        .startsWith(merchant().whereTheShopFrames(""));

    theShopReadsTheTokenAndChargesIt(opened);
    theWebviewSawNothingGoWrong();
  }

  private NotThroughTheBrowser.HostedTokenization aSessionTheShopsServerOpens() {
    NotThroughTheBrowser.HostedTokenization opened =
        shopsServer().theHostedTokenizationWorldlineOpens(testRunId(), A_MERCHANT_ID, AN_API_KEY);
    assertThat(opened.url())
        .as("worldline hands the shop a page this simulator serves, not the vendor's host")
        .isEqualTo(at("/hostedtokenization/tokenization/form/" + opened.id()));
    return opened;
  }

  private static void storeTheCard(Locator cards, Locator store) {
    PlaywrightAssertions.assertThat(
            cards.locator("option[value='" + THE_CARD_THE_PAYER_PICKS + "']"))
        .hasText(THE_CARD_AS_THE_PAYER_SEES_IT);
    cards.selectOption(THE_CARD_THE_PAYER_PICKS);
    store.click();
  }

  private static Page.GetByRoleOptions aButton() {
    return new Page.GetByRoleOptions().setName(STORE_THIS_CARD);
  }

  private static FrameLocator.GetByRoleOptions aFramedButton() {
    return new FrameLocator.GetByRoleOptions().setName(STORE_THIS_CARD);
  }

  private void theShopReadsTheTokenAndChargesIt(NotThroughTheBrowser.HostedTokenization opened) {
    String token =
        shopsServer()
            .theHostedTokenizationWorldlineReadsBack(
                testRunId(), A_MERCHANT_ID, AN_API_KEY, opened.id());
    assertThat(token)
        .as("the shop's server reads the token the payer's card was stored under")
        .contains("\"tokenStatus\":\"CREATED\"")
        .contains("\"cardNumber\":\"" + THE_CARD_AS_WORLDLINE_MASKS_IT + "\"")
        .containsPattern("\"id\":\"[0-9a-f]{32}\"");

    String payment =
        shopsServer()
            .thePaymentWorldlineCreatesFrom(testRunId(), A_MERCHANT_ID, AN_API_KEY, opened.id());
    assertThat(payment)
        .as("the shop's server charges the stored card by its hostedTokenizationId")
        .contains("\"status\":\"CAPTURED\"")
        .contains("\"cardNumber\":\"" + THE_CARD_AS_WORLDLINE_MASKS_IT + "\"");
  }
}
