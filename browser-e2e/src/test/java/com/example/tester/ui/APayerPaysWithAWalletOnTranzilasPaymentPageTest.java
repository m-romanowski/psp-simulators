package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerPaysWithAWalletOnTranzilasPaymentPageTest extends AJourneyInAMobileAppsWebview {

  private static final String CHARGED = "18.5";

  private static final String WHAT_IS_BEING_PAID_FOR = "a museum ticket";

  private static final String THE_CARD_IN_THE_WALLET = "published-9022";

  private static final String THE_NUMBER_THAT_CARD_STANDS_FOR = "4907639999909022";

  private static final String THE_SHOPS_ORDER = "SHOP-301";

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
      "a payer taps the stand-in Apple Pay button on tranzila's payment page in the app's webview,"
          + " confirms in the stand-in sheet, is posted back to the shop's success address, the"
          + " shop is notified and its lookup over tranzila's own API names the broker")
  void aPayerPaysWithApplePayOnThePaymentPageTranzilaServesToTheAppsWebview() {
    thePayerHoldsTheCard(THE_NUMBER_THAT_CARD_STANDS_FOR);

    page().navigate(shop.thePaymentPage() + "?" + query());

    PlaywrightAssertions.assertThat(page().getByText(WHAT_IS_BEING_PAID_FOR)).isVisible();
    PlaywrightAssertions.assertThat(walletButton("Google Pay")).isVisible();
    thePageDoesNotScrollSideways("tranzila's payment page offering the wallets");
    walletButton("Apple Pay").click();

    PlaywrightAssertions.assertThat(
            page().getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Apple Pay")))
        .isVisible();
    PlaywrightAssertions.assertThat(page().getByText(CHARGED + " ILS")).isVisible();
    thePageDoesNotScrollSideways("the stand-in Apple Pay sheet");
    page().getByLabel("Card in the wallet").selectOption(THE_CARD_IN_THE_WALLET);
    walletButton("Pay with Apple Pay").click();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    assertThat(page().url())
        .as("tranzila posts the payer's webview to the success_url_address the page was given")
        .startsWith(merchant().whereAPayerLandsHavingPaid());
    assertThat(page().content()).doesNotContain(THE_NUMBER_THAT_CARD_STANDS_FOR);
    Map<String, String> returned = merchant().theOnlyReturn();
    assertThat(returned)
        .as("the return of a wallet payment carries the published fields of a card payment")
        .containsEntry("supplier", shop.terminal())
        .containsEntry("sum", CHARGED)
        .containsEntry("Response", "000")
        .containsEntry("apple_pay", "1")
        .containsEntry("google_pay", "1")
        .containsEntry("orderid", THE_SHOPS_ORDER)
        .containsEntry("ccno", "9022")
        .containsEntry("txn_type", "debit")
        .containsEntry("tranmode", "A")
        .containsKey("TranzilaTK")
        .containsKey("transaction_id")
        .doesNotContainKey("broker");
    String transaction = returned.get("transaction_id");

    Map<String, String> notified =
        merchant().theCallbackNaming("transaction_id", transaction, A_NOTIFY_ARRIVES_WITHIN);
    assertThat(notified)
        .as("the notify posts the return's fields and the authorization number to the shop")
        .containsAllEntriesOf(returned)
        .containsKey("ConfirmationCode");
    assertThat(merchant().callbacksReceivedAtTheOtherTarget())
        .as("the account's other callback target is sent nothing: tranzila posts to the notify")
        .isEmpty();

    String lookedUp = shop.looksUp(testRunId(), transaction);
    assertThat(lookedUp)
        .as("the shop's lookup over tranzila's own API names the payment's broker")
        .contains("\"index\":\"" + transaction + "\"")
        .contains("\"broker\":\"apple_pay\"")
        .doesNotContain(THE_NUMBER_THAT_CARD_STANDS_FOR);
    theWebviewSawNothingGoWrong();
  }

  private com.microsoft.playwright.Locator walletButton(String named) {
    return page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(named).setExact(true));
  }

  private String query() {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("sum", CHARGED);
    parameters.put("currency", "1");
    parameters.put("tranmode", "A");
    parameters.put("apple_pay", "1");
    parameters.put("google_pay", "1");
    parameters.put("success_url_address", merchant().whereAPayerLandsHavingPaid());
    parameters.put("fail_url_address", merchant().whereAPayerLandsHavingGivenUp());
    parameters.put("notify_url_address", merchant().whereCallbacksGo());
    parameters.put("pdesc", WHAT_IS_BEING_PAID_FOR);
    parameters.put("orderid", THE_SHOPS_ORDER);
    return parameters.entrySet().stream()
        .map(
            entry ->
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
