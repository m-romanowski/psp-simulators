package com.example.checkout.e2e;

import java.util.Locale;
import java.util.UUID;

public final class TheShopsWorld {

  private final SimulatorUnderTest simulator = SimulatorUnderTest.running();
  private final MerchantEndpoint endpoint = MerchantEndpoint.listening();
  private final String testRunId =
      "e2e" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

  private Shop shop;
  private String clockPinnedTo = "";
  private String paymentId = "";
  private Shop.Answer lastAnswer;
  private Shop.Reconciled lastReconciliation;
  private int furtherRefundsAskedFor;

  MerchantEndpoint endpoint() {
    return endpoint;
  }

  String testRunId() {
    return testRunId;
  }

  void theClockIsPinnedTo(String instant) {
    this.clockPinnedTo = instant;
  }

  String clockPinnedTo() {
    return clockPinnedTo;
  }

  void theShopIs(Shop shop) {
    this.shop = shop;
  }

  Shop shop() {
    if (shop == null) {
      throw new IllegalStateException("no shop was opened for business in this scenario");
    }
    return shop;
  }

  GrowShop growShop() {
    if (shop instanceof GrowShop grow) {
      return grow;
    }
    throw new IllegalStateException("this step is Grow's and the shop is " + shop.providerName());
  }

  StripeShop stripeShop() {
    if (shop instanceof StripeShop stripe) {
      return stripe;
    }
    throw new IllegalStateException("this step is Stripe's and the shop is " + shop.providerName());
  }

  Shop newShopFor(String provider) {
    return switch (provider.toLowerCase(Locale.ROOT)) {
      case "grow" -> new GrowShop(simulator.baseUrl(), testRunId, endpoint);
      case "stripe" -> new StripeShop(simulator.baseUrl(), testRunId);
      default ->
          throw new IllegalArgumentException(
              "this example covers Grow and Stripe, and the scenario named " + provider);
    };
  }

  void thePaymentIs(String paymentId) {
    this.paymentId = paymentId;
  }

  String paymentId() {
    return paymentId;
  }

  void theShopWasAnswered(Shop.Answer answer) {
    this.lastAnswer = answer;
  }

  Shop.Answer lastAnswer() {
    if (lastAnswer == null) {
      throw new IllegalStateException("the shop has not called the provider yet in this scenario");
    }
    return lastAnswer;
  }

  void theShopAskedForAFurtherRefund() {
    furtherRefundsAskedFor++;
  }

  int furtherRefundsTheShopAskedFor() {
    return furtherRefundsAskedFor;
  }

  void theReconciliationSaid(Shop.Reconciled reconciled) {
    this.lastReconciliation = reconciled;
  }

  Shop.Reconciled lastReconciliation() {
    if (lastReconciliation == null) {
      throw new IllegalStateException("the shop has reconciled nothing in this scenario");
    }
    return lastReconciliation;
  }

  String theVendorClientTranscript() {
    return shop instanceof StripeShop stripe ? stripe.transcript().rendered() : "";
  }

  void everythingIsPutBack() {
    if (shop != null) {
      shop.tidiesUp();
    }
    endpoint.close();
  }
}
