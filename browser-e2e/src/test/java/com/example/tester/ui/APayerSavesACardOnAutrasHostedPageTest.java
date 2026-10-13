package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerSavesACardOnAutrasHostedPageTest extends AJourneyInAMobileAppsWebview {

  private static final String AUTRA = "autra";

  private static final String A_CLIENT_ID = "a-payer-autra-client";

  private static final String A_CLIENT_SECRET = "a-payer-autra-secret";

  private static final String THE_MERCHANTS_DOCUMENT = "25055662000190";

  private static final String THE_CARD_THE_PAYER_PICKS = "published-4111";

  private static final String SAVE_THIS_CARD = "Save this card";

  private static final String THE_RESULT = "#autra-card-tokenized-result";

  @Override
  String provider() {
    return AUTRA;
  }

  @BeforeEach
  void theAutraMerchantIsConfigured() {
    setup()
        .configureAnAutraMerchant(
            testRunId(), ACCOUNT, A_CLIENT_ID, A_CLIENT_SECRET, THE_MERCHANTS_DOCUMENT);
  }

  @Test
  @DisplayName(
      "a payer saves a card on the page autra's session names, opened in the app's webview, and"
          + " the shop charges it over autra's own route")
  void aPayerSavesACardOnTheHostedPageAutraServesToTheAppsWebview() {
    String accessToken =
        shopsServer().anAutraAccessToken(testRunId(), A_CLIENT_ID, A_CLIENT_SECRET);
    String hostedCardUrl =
        shopsServer().theHostedCardUrlAutraMints(testRunId(), accessToken, THE_MERCHANTS_DOCUMENT);

    page().navigate(hostedCardUrl);

    thePageDoesNotScrollSideways("autra's hosted card page");
    page().getByLabel("Card").selectOption(THE_CARD_THE_PAYER_PICKS);
    page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(SAVE_THIS_CARD)).click();

    PlaywrightAssertions.assertThat(page().locator(THE_RESULT)).isVisible();
    String result = page().locator(THE_RESULT).textContent();
    assertThat(NotThroughTheBrowser.theTextFieldIn(result, "last4"))
        .as("the result names the card the payer picked, by its last four digits")
        .isEqualTo("1111");

    theShopChargesTheCardThePageHandedBack(
        accessToken, NotThroughTheBrowser.theTextFieldIn(result, "slugToken"));
    theWebviewSawNothingGoWrong();
  }

  private void theShopChargesTheCardThePageHandedBack(String accessToken, String slugToken) {
    String sale =
        shopsServer()
            .theSaleAutraAnswers(testRunId(), accessToken, THE_MERCHANTS_DOCUMENT, slugToken);
    assertThat(sale)
        .as("a sale over autra's own route accepts the slugToken the payer's page handed back")
        .contains("\"status\":\"ACCEPTED\"")
        .contains("\"amount\":150")
        .containsPattern("\"paymentID\":\"[^\"]+\"");

    assertThat(
            shopsServer()
                .theCodeOfARefusedAutraSaleOf(
                    testRunId(), accessToken, THE_MERCHANTS_DOCUMENT, slugToken + "0"))
        .as("a slug the page did not hand back names no card autra holds")
        .isEqualTo("422 TOKEN_NOT_AVAILABLE");
  }
}
