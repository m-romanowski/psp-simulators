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

class APayerFinishesTranzilasPaymentPageTest extends AJourneyInAMobileAppsWebview {

  private static final String CHARGED = "42";

  private static final String WHAT_IS_BEING_PAID_FOR = "two seats, row C";

  private static final String THE_PAYER = "Dana Levi";

  private static final String THE_CARD_THE_PAYER_PICKS = "published-9022";

  private static final String THE_NUMBER_THAT_CARD_STANDS_FOR = "4907639999909022";

  private static final String THE_ORDER = "ORD-9871";

  private static final String THE_SHOPS_ORDER = "SHOP-77";

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
      "a payer holds an amount on tranzila's payment page in the app's webview, is posted back to"
          + " the shop's success address, the shop is notified and captures the hold over"
          + " tranzila's own API")
  void aPayerFinishesThePaymentPageTranzilaServesToTheAppsWebview() {
    thePayerHoldsTheCard(THE_NUMBER_THAT_CARD_STANDS_FOR);
    String thtk =
        shop.aHandshakeLocking(testRunId(), CHARGED, "{\"order_id\":\"" + THE_ORDER + "\"}");

    page().navigate(shop.thePaymentPage() + "?" + query(thtk));

    PlaywrightAssertions.assertThat(page().getByText(WHAT_IS_BEING_PAID_FOR)).isVisible();
    PlaywrightAssertions.assertThat(page().getByText(CHARGED + " ILS")).isVisible();
    thePageDoesNotScrollSideways("tranzila's payment page");
    page().getByLabel("Card").selectOption(THE_CARD_THE_PAYER_PICKS);
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Pay").setExact(true))
        .click();

    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
    assertThat(page().url())
        .as("tranzila posts the payer's webview to the success_url_address the page was given")
        .startsWith(merchant().whereAPayerLandsHavingPaid());
    assertThat(page().content()).doesNotContain(THE_NUMBER_THAT_CARD_STANDS_FOR);
    Map<String, String> returned = merchant().theOnlyReturn();
    assertThat(returned)
        .as("the return carries the published fields of an approved J5 with a token")
        .containsEntry("supplier", shop.terminal())
        .containsEntry("sum", CHARGED)
        .containsEntry("currency", "1")
        .containsEntry("Response", "777")
        .containsEntry("contact", THE_PAYER)
        .containsEntry("pdesc", WHAT_IS_BEING_PAID_FOR)
        .containsEntry("ccno", "9022")
        .containsEntry("txn_type", "verify")
        .containsEntry("tranmode", "VK")
        .containsEntry("order_id", THE_ORDER)
        .containsEntry("orderid", THE_SHOPS_ORDER)
        .doesNotContainKey("thtk")
        .containsKey("TranzilaTK")
        .containsKey("transaction_id");
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

    String captured =
        shop.forces(testRunId(), transaction, returned.get("TranzilaTK"), CHARGED, "1230");
    assertThat(captured)
        .as("the shop's server captures the J5 with a force naming it and the token")
        .contains("\"error_code\":0")
        .contains("\"processor_response_code\":\"000\"")
        .contains("\"txn_type\":\"force\"")
        .doesNotContain(THE_NUMBER_THAT_CARD_STANDS_FOR);
    theWebviewSawNothingGoWrong();
  }

  private String query(String thtk) {
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("sum", CHARGED);
    parameters.put("currency", "1");
    parameters.put("tranmode", "VK");
    parameters.put("thtk", thtk);
    parameters.put("success_url_address", merchant().whereAPayerLandsHavingPaid());
    parameters.put("fail_url_address", merchant().whereAPayerLandsHavingGivenUp());
    parameters.put("notify_url_address", merchant().whereCallbacksGo());
    parameters.put("contact", THE_PAYER);
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
