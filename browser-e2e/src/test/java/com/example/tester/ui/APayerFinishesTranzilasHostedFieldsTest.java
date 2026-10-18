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
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerFinishesTranzilasHostedFieldsTest extends AJourneyInAMobileAppsWebview {

  private static final String CHARGED = "42";

  private static final String THE_CARD_THE_PAYER_TYPES = "4907639999909022";

  private static final String ITS_FIRST_SIX = "490763";

  private static final String ITS_EXPIRY = "12/30";

  private static final String ITS_CVV = "737";

  private static final String AN_ISRACARD_NUMBER_LUHN_REFUSES = "40000001";

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
      "a payer types a card into tranzila's hosted fields on the shop's own checkout in the app's"
          + " webview after the frames refused an empty one, the shop's page is answered through"
          + " the charge callback, the shop is notified, and the shop's server verifies the"
          + " response hash and captures the hold with the token over tranzila's own API")
  void aPayerTypesACardIntoTheHostedFieldsOfTheShopsOwnCheckout() {
    thePayerHoldsTheCard(THE_CARD_THE_PAYER_TYPES);
    String thtk = shop.aHandshakeLocking(testRunId(), CHARGED, "{\"order_id\":\"ORD-77\"}");
    theCheckoutIsOpenedFor(thtk, CHARGED);

    PlaywrightAssertions.assertThat(theWidgetsEar()).containsText(TheMerchant.A_PROBE);
    pay();
    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.NOT_MADE)).isVisible();
    assertThat(merchant().theLatestHostedFieldsAnswer())
        .as("the frames refuse an empty card before anything is posted, in the vendor's err shape")
        .startsWith(
            "refused {\"error\":\"invalid_resource\",\"error_description\":\"One or more"
                + " parameters were missing or invalid\",\"messages\":[")
        .contains("\"code\":\"credit_card_number_invalid\"")
        .contains("\"param\":\"credit_card_number\"")
        .contains("\"code\":\"expiry_invalid\"")
        .contains("\"code\":\"cvv_invalid\"");

    theField("Card number").fill(THE_CARD_THE_PAYER_TYPES);
    theField("Expiry").fill(ITS_EXPIRY);
    theField("CVV").fill(ITS_CVV);
    PlaywrightAssertions.assertThat(theField("Card number")).hasValue("4907 6399 9990 9022");
    PlaywrightAssertions.assertThat(theField("Card number"))
        .hasClass(Pattern.compile("\\bhosted-fields-valid\\b"));
    PlaywrightAssertions.assertThat(page().locator("#credit_card_number"))
        .hasClass(Pattern.compile("\\bhosted-fields-valid\\b"));
    assertThat(theEventsTheShopsPageHeard())
        .as("the shop's page hears the vendor script's events, card type in its own vocabulary")
        .contains("cardTypeChange:visa", "notEmpty:credit_card_number");
    pay();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    String answered = merchant().theLatestHostedFieldsAnswer();
    assertThat(answered)
        .as("the charge callback is handed tranzila's published answer for an approved J5")
        .startsWith("{\"errors\":null,\"transaction_response\":{\"success\":true,")
        .contains("\"processor_response_code\":\"777\"")
        .contains("\"amount\":\"42.00\"")
        .contains("\"credit_card_last_4_digits\":\"9022\"")
        .contains("\"txn_type\":\"verify\",\"tranmode\":\"V\"");
    assertThat(shop.verifies(answered))
        .as("the shop's server recomputes response_hash with the account secret and it holds")
        .isTrue();
    assertThat(shop.verifies(answered.replace("\"777\"", "\"000\"")))
        .as("and an answer altered on its way through the browser does not hold")
        .isFalse();
    String transaction = TranzilaShopBackend.theTextIn(answered, "transaction_id");
    String token = TranzilaShopBackend.theTextIn(answered, "token");

    Map<String, String> notified =
        merchant().theCallbackNaming("transaction_id", transaction, A_NOTIFY_ARRIVES_WITHIN);
    assertThat(notified)
        .as("the notify_url the charge named is posted the transaction data")
        .containsEntry("supplier", shop.terminal())
        .containsEntry("Response", "777")
        .containsEntry("ccno", "9022")
        .containsEntry("TranzilaTK", token)
        .containsEntry("order_id", "ORD-77")
        .containsKey("ConfirmationCode");
    assertThat(merchant().callbacksReceivedAtTheOtherTarget())
        .as("the account's other callback target is sent nothing")
        .isEmpty();

    String captured = shop.forces(testRunId(), transaction, token, CHARGED, "1230");

    assertThat(captured)
        .as("the shop's server captures the J5 with a force naming it and the token")
        .contains("\"error_code\":0")
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"txn_type\":\"force\"");
    String theShopsOwnPage = page().mainFrame().content();
    String heardOnTheShopsWindow = theMessagesTheShopsWindowHeard();
    String heardByAThirdParty = theMessagesAThirdPartyFrameHeard();
    assertThat(heardOnTheShopsWindow)
        .as("the collector on the shop's window did hear the frames talk")
        .contains("validityChange");
    assertThat(
            List.of(
                theShopsOwnPage,
                heardOnTheShopsWindow,
                heardByAThirdParty,
                answered,
                captured,
                String.join("\n", merchant().callbacksReceived())))
        .as(
            "neither the shop's page outside the frames, nor any message its window or a third"
                + " party's frame on it heard, nor what its server was handed or notified, nor what"
                + " tranzila answered it carries the card number, its first six or the CVV")
        .allSatisfy(
            seen ->
                assertThat(seen)
                    .doesNotContain(THE_CARD_THE_PAYER_TYPES)
                    .doesNotContain("4907 6399")
                    .doesNotContain(ITS_FIRST_SIX)
                    .doesNotContain(ITS_FIRST_SIX)
                    .satisfies(
                        text ->
                            assertThat(withoutWhatTheSimulatorMinted(text, transaction))
                                .doesNotContain(ITS_CVV)));
    assertThat(String.join("\n", providerApi().everyAnswerItGot()))
        .doesNotContain(THE_CARD_THE_PAYER_TYPES)
        .doesNotContain(ITS_FIRST_SIX);
    theWebviewSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "a charge of an amount the shop's handshake does not lock reaches the charge callback as"
          + " tranzila's server refusal, hashed, and nothing is charged")
  void aServerRefusalReachesTheChargeCallback() {
    thePayerHoldsTheCard(THE_CARD_THE_PAYER_TYPES);
    String thtk = shop.aHandshakeLocking(testRunId(), CHARGED, "{\"order_id\":\"ORD-78\"}");
    theCheckoutIsOpenedFor(thtk, "41");

    theField("Card number").fill(THE_CARD_THE_PAYER_TYPES);
    theField("Expiry").fill(ITS_EXPIRY);
    theField("CVV").fill(ITS_CVV);
    pay();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.NOT_MADE)).isVisible();
    String answered = merchant().theLatestHostedFieldsAnswer();
    assertThat(answered)
        .as("a server refusal is a response, not an err, and names the published refusal")
        .startsWith(
            "{\"errors\":[{\"code\":912791,\"param\":\"thtk\",\"message\":\"Illegal Operation"
                + " 912791\"}],\"transaction_response\":null,\"response_hash\":\"");
    assertThat(shop.verifies(answered)).isTrue();
    assertThat(merchant().callbacksReceived()).isEmpty();
    theWebviewSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "a payer types an isracard number Luhn refuses and the isracard check accepts, and it is"
          + " charged and answered as an isracard")
  void aPayerPaysWithAnIsracardNumberLuhnRefuses() {
    String thtk = shop.aHandshakeLocking(testRunId(), CHARGED, "{\"order_id\":\"ORD-79\"}");
    theCheckoutIsOpenedFor(thtk, CHARGED);

    theField("Card number").fill(AN_ISRACARD_NUMBER_LUHN_REFUSES);
    theField("Expiry").fill(ITS_EXPIRY);
    theField("CVV").fill(ITS_CVV);
    PlaywrightAssertions.assertThat(theField("Card number")).hasValue("4000 0001");
    PlaywrightAssertions.assertThat(theField("Card number"))
        .hasClass(Pattern.compile("\\bhosted-fields-valid\\b"));
    pay();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    String answered = merchant().theLatestHostedFieldsAnswer();
    assertThat(answered)
        .contains("\"credit_card_last_4_digits\":\"0001\"")
        .contains("\"card_type\":\"5\",\"card_mask\":\"****0001\"")
        .contains("\"card_type_name\":\"Isracard\"");
    assertThat(shop.verifies(answered)).isTrue();
    assertThat(theEventsTheShopsPageHeard()).contains("cardTypeChange:isracard");
    Map<String, String> notified =
        merchant()
            .theCallbackNaming(
                "transaction_id",
                TranzilaShopBackend.theTextIn(answered, "transaction_id"),
                A_NOTIFY_ARRIVES_WITHIN);
    assertThat(notified).containsEntry("cardtype", "5").containsEntry("cardacquirer", "1");
    theWebviewSawNothingGoWrong();
  }

  private static String withoutWhatTheSimulatorMinted(String text, String transaction) {
    return text.replace(transaction, "")
        .replaceAll("(:|%3A)[0-9]{2,5}", "")
        .replaceAll("[0-9a-f]{14,}", "")
        .replaceAll("\"(auth_number|ConfirmationCode)\":\"[0-9]+\"", "")
        .replaceAll("ConfirmationCode=[0-9]+", "")
        .replaceAll("\"(transaction_id|index)\":\"?[0-9]+\"?", "")
        .replaceAll("(transaction_id|index)=[0-9]+", "")
        .replaceAll("\"timestamp\":[0-9]+", "")
        .replaceAll("reference_txn_id\":[0-9]+", "");
  }

  private Locator theWidgetsEar() {
    return page().frameLocator("iframe[title='a third party widget']").locator("#heard");
  }

  private void theCheckoutIsOpenedFor(String thtk, String amount) {
    page()
        .navigate(
            merchant()
                .whereTheShopChecksOutWithHostedFields(
                    shop.theHostedFields(), shop.terminal(), thtk, amount));
    PlaywrightAssertions.assertThat(page().locator("body[data-hosted-fields=ready]")).isAttached();
    thePageDoesNotScrollSideways("the shop's checkout carrying tranzila's hosted fields");
  }

  private void pay() {
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pay").setExact(true))
        .click();
  }

  private Locator theField(String named) {
    FrameLocator frame = page().frameLocator("iframe[title='" + named + "']");
    return frame.getByLabel(named);
  }

  private String theEventsTheShopsPageHeard() {
    return String.valueOf(page().evaluate("() => window.shopEvents.join(' ')"));
  }

  private String theMessagesTheShopsWindowHeard() {
    return String.valueOf(page().evaluate("() => window.shopSaw.join('\\n')"));
  }

  private String theMessagesAThirdPartyFrameHeard() {
    return page()
        .frameLocator("iframe[title='a third party widget']")
        .locator("#heard")
        .textContent();
  }
}
