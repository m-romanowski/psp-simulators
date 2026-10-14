package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerFinishesTranzilasHostedFieldsTest extends AJourneyInAMobileAppsWebview {

  private static final String CHARGED = "42";

  private static final String THE_CARD_THE_PAYER_TYPES = "4907639999909022";

  private static final String ITS_FIRST_SIX = "490763";

  private static final String ITS_EXPIRY = "12/30";

  private static final String ITS_CVV = "737";

  private TranzilaShopBackend shop;

  @Override
  String provider() {
    return TranzilaShopBackend.TRANZILA;
  }

  @BeforeEach
  void theShopsTerminalIsConfigured() {
    shop = new TranzilaShopBackend(at(""), providerApi());
    setup()
        .configureATranzilaTerminal(
            testRunId(),
            ACCOUNT,
            shop.terminal(),
            shop.appKey(),
            shop.appSecret(),
            List.of(
                merchant().anotherCallbackTargetOfTheSameAccount(), merchant().whereCallbacksGo()));
  }

  @Test
  @DisplayName(
      "a payer types a card into tranzila's hosted fields on the shop's own checkout in the app's"
          + " webview, the shop's page is answered through the charge callback, and the shop's"
          + " server verifies the response hash and captures the hold with the token over"
          + " tranzila's own API")
  void aPayerTypesACardIntoTheHostedFieldsOfTheShopsOwnCheckout() {
    thePayerHoldsTheCard(THE_CARD_THE_PAYER_TYPES);
    String thtk = shop.aHandshakeLocking(testRunId(), CHARGED, "{\"order_id\":\"ORD-77\"}");

    page()
        .navigate(
            merchant()
                .whereTheShopChecksOutWithHostedFields(
                    shop.theHostedFields(), shop.terminal(), thtk, CHARGED));
    PlaywrightAssertions.assertThat(page().locator("body[data-hosted-fields=ready]")).isAttached();
    thePageDoesNotScrollSideways("the shop's checkout carrying tranzila's hosted fields");
    theField("Card number").fill(THE_CARD_THE_PAYER_TYPES);
    theField("Expiry").fill(ITS_EXPIRY);
    theField("CVV").fill(ITS_CVV);
    PlaywrightAssertions.assertThat(page().locator("#credit_card_number"))
        .hasClass("field container-for-hostedfield hosted-fields-valid");
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pay").setExact(true))
        .click();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    String answered = merchant().theOnlyHostedFieldsAnswer();
    assertThat(answered)
        .as("the charge callback is handed tranzila's published answer for an approved J5")
        .startsWith("{\"errors\":null,\"transaction_response\":{\"success\":true,")
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"amount\":\"" + CHARGED + "\"")
        .contains("\"credit_card_last_4_digits\":\"9022\"")
        .contains("\"user_form_data\":{\"contact\":\"Dana Levi\"}")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"V\"");
    assertThat(shop.verifies(answered))
        .as("the shop's server recomputes response_hash with the account secret and it holds")
        .isTrue();
    assertThat(shop.verifies(answered.replace("\"777\"", "\"000\"")))
        .as("and an answer altered on its way through the browser does not hold")
        .isFalse();
    String transaction = TranzilaShopBackend.theTextIn(answered, "transaction_id");
    String token = TranzilaShopBackend.theTextIn(answered, "token");

    String captured = shop.forces(testRunId(), transaction, token, CHARGED, "1230");

    assertThat(captured)
        .as("the shop's server captures the J5 with a force naming it and the token")
        .contains("\"error_code\":0")
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"txn_type\":\"force\"");
    String theShopsOwnPage = page().mainFrame().content();
    assertThat(List.of(theShopsOwnPage, answered, captured))
        .as(
            "neither the shop's own page outside the frames, nor what its server was handed, nor"
                + " what tranzila answered it carries the card number, its first six or the CVV")
        .allSatisfy(
            seen ->
                assertThat(seen)
                    .doesNotContain(THE_CARD_THE_PAYER_TYPES)
                    .doesNotContain(ITS_FIRST_SIX)
                    .doesNotContain("\"" + ITS_CVV + "\"")
                    .doesNotContain("value=\"" + ITS_CVV));
    assertThat(String.join("\n", providerApi().everyAnswerItGot()))
        .doesNotContain(THE_CARD_THE_PAYER_TYPES)
        .doesNotContain(ITS_FIRST_SIX);
    assertThat(merchant().callbacksReceived())
        .as("no notify is sent for a hosted fields charge, to either callback target")
        .isEmpty();
    theWebviewSawNothingGoWrong();
  }

  private com.microsoft.playwright.Locator theField(String named) {
    FrameLocator frame = page().frameLocator("iframe[title='" + named + "']");
    return frame.getByLabel(named);
  }
}
