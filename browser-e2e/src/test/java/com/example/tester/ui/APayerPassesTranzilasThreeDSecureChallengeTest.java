package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerPassesTranzilasThreeDSecureChallengeTest extends AJourneyInAMobileAppsWebview {

  private static final String CHARGED = "42";

  private static final String THE_CHALLENGE_CARD = "4918914107195005";

  private static final String ITS_FIRST_SIX = "491891";

  private static final String ITS_EXPIRY = "12/30";

  private static final String ITS_CVV = "737";

  private static final String THE_CODE_THAT_PASSES = "555";

  private static final String THE_ORDER = "ORD-3DS-1";

  private static final Duration A_NOTIFY_ARRIVES_WITHIN = Duration.ofSeconds(20);

  private TranzilaShopBackend shop;

  @Override
  String provider() {
    return TranzilaShopBackend.TRANZILA;
  }

  @BeforeEach
  void theShopsTerminalHasTwoCallbackTargets() {
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
      "the shop's server asks tranzila for a 3-D Secure debit of the challenge card, the app's"
          + " webview opens the challengeUrl, the payer enters 555, the webview is posted back to"
          + " the shop's redirect address with its params and the track_id, and the shop's server"
          + " completes the debit by that track_id")
  void aPayerPassesTheChallengeInTheAppsWebviewAndTheShopCompletesIt() {
    String created =
        shop.aThreeDSecureDebit(
            testRunId(),
            THE_CHALLENGE_CARD,
            CHARGED,
            merchant().whereAPayerLandsHavingPaid(),
            THE_ORDER);
    assertThat(created)
        .as("tranzila answers the challenge it keeps waiting, with no transaction yet")
        .startsWith("{\"error_code\":0,\"message\":\"success\",\"challengeUrl\":\"")
        .doesNotContain("transaction_id");
    String challengeUrl = TranzilaShopBackend.theTextIn(created, "challengeUrl");
    String trackId = TranzilaShopBackend.theTextIn(created, "track_id");

    page().navigate(challengeUrl);

    PlaywrightAssertions.assertThat(page().getByText(CHARGED + " ILS")).isVisible();
    PlaywrightAssertions.assertThat(page().getByText("Visa ending 5005")).isVisible();
    thePageDoesNotScrollSideways("tranzila's 3-D Secure challenge");
    page().getByLabel("Code").fill(THE_CODE_THAT_PASSES);
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit").setExact(true))
        .click();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    assertThat(page().url())
        .as("the challenge posts the payer's webview to the auth_3ds_redirect url")
        .startsWith(merchant().whereAPayerLandsHavingPaid());
    assertThat(merchant().theOnlyReturn())
        .as("the redirect carries the params the create named and the track_id")
        .containsEntry("order", THE_ORDER)
        .containsEntry("track_id", trackId)
        .hasSize(2);

    String completed = shop.completes(testRunId(), trackId);
    assertThat(completed)
        .as("the completion answers the published complete shape of an approved debit")
        .startsWith("{\"error_code\":0,\"message\":\"Success\",\"transaction_result\":{")
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"last_4\":\"5005\"")
        .contains("\"t3ds_data\":{\"version\":\"1.0\",\"statusCode\":\"YA\"")
        .contains("\"cvv\":\"***\"");
    assertThat(shop.completes(testRunId(), trackId))
        .as("a challenge is completed once")
        .isEqualTo(
            "{\"error_code\":900,\"message\":\"Transaction failed during 3D Secure validation\"}");
    assertThat(everythingTheWebviewSaw())
        .as(
            "the webview the payer answered the challenge in never saw the card number the shop's"
                + " server sent, nor its first six")
        .allSatisfy(
            seen -> assertThat(seen).doesNotContain(THE_CHALLENGE_CARD).doesNotContain(ITS_FIRST_SIX));
    assertThat(String.join("\n", merchant().returnsReceived()))
        .doesNotContain(THE_CHALLENGE_CARD)
        .doesNotContain(ITS_FIRST_SIX);
    theWebviewSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "a payer types the challenge card into tranzila's hosted fields on the shop's own checkout,"
          + " answers the challenge in the frame the hosted fields open over the checkout, and the"
          + " charge callback is handed the hashed final answer, which the shop is also notified of")
  void aPayerPassesTheChallengeTheHostedFieldsOpenOverTheShopsCheckout() {
    thePayerHoldsTheCard(THE_CHALLENGE_CARD);
    String thtk = shop.aHandshakeLocking(testRunId(), CHARGED, "{\"order_id\":\"ORD-3DS-2\"}");
    page()
        .navigate(
            merchant()
                .whereTheShopChecksOutWithHostedFields(
                    shop.theHostedFields(), shop.terminal(), thtk, CHARGED));
    PlaywrightAssertions.assertThat(page().locator("body[data-hosted-fields=ready]")).isAttached();

    theField("Card number").fill(THE_CHALLENGE_CARD);
    theField("Expiry").fill(ITS_EXPIRY);
    theField("CVV").fill(ITS_CVV);
    pay();

    Locator challenge = page().locator("#hf-challenge-frame");
    PlaywrightAssertions.assertThat(challenge).isVisible();
    assertThat(challenge.getAttribute("title")).isEqualTo("3-D Secure verification");
    thePageDoesNotScrollSideways("the shop's checkout under tranzila's 3-D Secure challenge");
    assertThat(merchant().hostedFieldsAnswersReceived())
        .as("nothing reaches the charge callback while the challenge is open")
        .isEmpty();
    FrameLocator theChallenge = page().frameLocator("#hf-challenge-frame");
    PlaywrightAssertions.assertThat(theChallenge.getByText("Visa ending 5005")).isVisible();
    theChallenge.getByLabel("Code").fill(THE_CODE_THAT_PASSES);
    theChallenge
        .getByRole(AriaRole.BUTTON, new FrameLocator.GetByRoleOptions().setName("Submit"))
        .click();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    PlaywrightAssertions.assertThat(page().locator("#hf-challenge-cont")).hasCount(0);
    String answered = merchant().theLatestHostedFieldsAnswer();
    assertThat(answered)
        .as("the charge callback is handed the answer of the J5 the challenge let through")
        .startsWith("{\"errors\":null,\"transaction_response\":{\"success\":true,")
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"credit_card_last_4_digits\":\"5005\"")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"V\"");
    assertThat(shop.verifies(answered))
        .as("the shop's server recomputes response_hash with the account secret and it holds")
        .isTrue();
    String transaction = TranzilaShopBackend.theTextIn(answered, "transaction_id");
    Map<String, String> notified =
        merchant().theCallbackNaming("transaction_id", transaction, A_NOTIFY_ARRIVES_WITHIN);
    assertThat(notified)
        .as("the notify is sent once the challenge is completed")
        .containsEntry("supplier", shop.terminal())
        .containsEntry("Response", "777")
        .containsEntry("ccno", "5005")
        .containsEntry("order_id", "ORD-3DS-2");
    assertThat(merchant().callbacksReceived()).hasSize(1);
    assertThat(merchant().callbacksReceivedAtTheOtherTarget()).isEmpty();
    String heardOnTheShopsWindow =
        String.valueOf(page().evaluate("() => window.shopSaw.join('\\n')"));
    String heardByAThirdParty =
        page().frameLocator("iframe[title='a third party widget']").locator("#heard").textContent();
    assertThat(heardOnTheShopsWindow).contains("showChallenge");
    assertThat(heardByAThirdParty)
        .as("the challenge and its result are told to the frames of tranzila's origin only")
        .doesNotContain("challenge")
        .doesNotContain("track_id");
    assertThat(List.of(page().mainFrame().content(), heardOnTheShopsWindow, answered))
        .allSatisfy(
            seen -> assertThat(seen).doesNotContain(THE_CHALLENGE_CARD).doesNotContain(ITS_FIRST_SIX));
    theWebviewSawNothingGoWrong();
  }

  private void pay() {
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pay").setExact(true))
        .click();
  }

  private Locator theField(String named) {
    return page().frameLocator("iframe[title='" + named + "']").getByLabel(named);
  }
}
