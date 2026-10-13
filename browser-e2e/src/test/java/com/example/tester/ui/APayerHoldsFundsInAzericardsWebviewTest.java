package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APayerHoldsFundsInAzericardsWebviewTest extends AnAzericardWebviewJourney {

  private static final String HELD = "10.00";

  @Test
  @DisplayName("a payer authorizes a hold in the webview and the shop completes all of it")
  void theShopCompletesTheWholeHold() {
    Map<String, String> held = aHoldThePayerAuthorized();

    String completed = shop().completes(held, HELD);

    assertThat(AzericardShopBackend.element("action", completed)).isEqualTo("0");
    assertThat(AzericardShopBackend.element("trtype", completed)).isEqualTo("21");
    theStatusReportsTheApprovedHold(held);
    theWebviewSawNothingGoWrong();
  }

  @Test
  @DisplayName("a payer authorizes a hold in the webview and the shop completes part of it")
  void theShopCompletesPartOfTheHold() {
    Map<String, String> held = aHoldThePayerAuthorized();

    String completed = shop().completes(held, "4.00");

    assertThat(AzericardShopBackend.element("action", completed)).isEqualTo("0");
    assertThat(AzericardShopBackend.element("amount", completed)).isEqualTo("4.00");
    String again = shop().completes(held, "6.00");
    assertThat(AzericardShopBackend.element("action", again))
        .as("a partial completion closes the authorization by default")
        .isEqualTo("3");
    theStatusReportsTheApprovedHold(held);
    theWebviewSawNothingGoWrong();
  }

  @Test
  @DisplayName("a payer authorizes a hold in the webview and the shop releases it")
  void theShopReleasesTheHold() {
    Map<String, String> held = aHoldThePayerAuthorized();

    String released = shop().releases(held, HELD);

    assertThat(AzericardShopBackend.element("action", released)).isEqualTo("0");
    assertThat(AzericardShopBackend.element("trtype", released)).isEqualTo("22");
    String completion = shop().completes(held, HELD);
    assertThat(AzericardShopBackend.element("rc", completion))
        .as("a released hold admits no completion")
        .isEqualTo("-3");
    theStatusReportsTheApprovedHold(held);
    theWebviewSawNothingGoWrong();
  }

  private Map<String, String> aHoldThePayerAuthorized() {
    String order = shop().anOrder();
    theWebviewPostsTheSignedFormTo(
        AzericardShopBackend.GATEWAY_URL,
        shop()
            .aSignedHold(
                order,
                HELD,
                merchant().whereAPayerLandsHavingPaid(),
                merchant().whereCallbacksGo()));
    thePayerTypes(A_PUBLISHED_TEST_CARD, "05/31", "796");
    thePayerReads("Payment successful");
    thePayerReturnsTo(merchant().whereAPayerLandsHavingPaid(), TheMerchant.THANKS);
    Map<String, String> held = theSignedCallbackFor(order);
    assertThat(held)
        .containsEntry("ACTION", "0")
        .containsEntry("TRTYPE", "0")
        .containsKey("RRN")
        .containsKey("INT_REF")
        .doesNotContainKey("TOKEN");
    noCallbackWentToTheAccountsOtherTarget();
    return held;
  }

  private void theStatusReportsTheApprovedHold(Map<String, String> held) {
    String status = shop().asksTheStatusOf(held.get("ORDER"), "0");
    assertThat(status)
        .contains("\"actionCode\":\"0\"")
        .contains("\"intRef\":\"" + held.get("INT_REF") + "\"");
  }
}
