package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerSavesACardInAzericardsWebviewTest extends AnAzericardWebviewJourney {

  @Test
  @DisplayName("a payer saves a card in azericard's webview, and the shop charges it off-session")
  void aPayerSavesACardAndTheShopChargesItLater() {
    String order = shop().anOrder();
    Map<String, String> signed =
        shop()
            .aSignedCardSave(
                order,
                "0.01",
                merchant().whereAPayerLandsHavingPaid(),
                merchant().whereCallbacksGo());

    theWebviewPostsTheSignedForm(signed);
    thePayerTypes(A_PUBLISHED_TEST_CARD, "05/31", "796");

    thePayerReads("Payment successful");
    thePayerReturnsTo(merchant().whereAPayerLandsHavingPaid(), TheMerchant.THANKS);

    Map<String, String> saved = theSignedCallbackFor(order);
    assertThat(saved)
        .containsEntry("ACTION", "0")
        .containsEntry("CARD", "************2601")
        .containsKey("TOKEN")
        .containsKey("EXT_NET_REF");

    String status = shop().asksTheStatusOf(order, "1");
    assertThat(status)
        .as("the shop's server reads the card save back with TRTYPE=90")
        .contains("\"actionCode\":\"0\"")
        .contains("\"order\":\"" + order + "\"")
        .contains("\"intRef\":\"" + saved.get("INT_REF") + "\"");

    String offSession = shop().anOrder();
    String answer =
        shop()
            .paysOffSessionWith(
                saved.get("TOKEN"),
                saved.get("EXT_NET_REF"),
                offSession,
                "5.00",
                merchant().whereCallbacksGo());
    assertThat(AzericardShopBackend.element("action", answer)).isEqualTo("0");
    assertThat(AzericardShopBackend.element("rc", answer)).isEqualTo("00");
    assertThat(AzericardShopBackend.element("desc", answer)).isEqualTo("Approved");

    Map<String, String> charged = theSignedCallbackFor(offSession);
    assertThat(charged)
        .as("the off-session charge is called back under its own order, on the saved card")
        .containsEntry("TOKEN", saved.get("TOKEN"))
        .containsEntry("ACTION", "0")
        .containsEntry("AMOUNT", "5.00");
    noCallbackWentToTheAccountsOtherTarget();
    theWebviewSawNothingGoWrong();
  }

  @Test
  @DisplayName(
      "a payer enters a card azericard does not accept, returns to the shop, and the shop is told"
          + " it failed")
  void aPayerEntersACardTheGatewayDeclines() {
    String order = shop().anOrder();
    theWebviewPostsTheSignedForm(
        shop()
            .aSignedCardSave(
                order,
                "0.01",
                merchant().whereAPayerLandsHavingGivenUp(),
                merchant().whereCallbacksGo()));
    thePayerTypes(AN_UNKNOWN_CARD, "05/31", "123");

    thePayerReads("Payment failed");
    thePayerReturnsTo(merchant().whereAPayerLandsHavingGivenUp(), TheMerchant.NEVER_MIND);

    Map<String, String> failed = theSignedCallbackFor(order);
    assertThat(failed).containsEntry("ACTION", "2");
    assertThat(failed).doesNotContainKey("TOKEN");

    String status = shop().asksTheStatusOf(order, "1");
    assertThat(status)
        .as("the shop's server reads the declined card save back with TRTYPE=90")
        .contains("\"actionCode\":\"2\"");
    noCallbackWentToTheAccountsOtherTarget();
    theWebviewSawNothingGoWrong();
  }
}
