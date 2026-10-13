package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TheLinksIntoTheSimulatorTest {

  private static final String SIMULATOR = "http://localhost:8080";

  private final SimulatorScreens screens =
      new SimulatorScreens(
          new ShopSettings(
              "http://simulator:8080",
              SIMULATOR + "/",
              "http://shop:8081",
              Duration.ofSeconds(2),
              Duration.ofSeconds(10)));

  @Test
  @DisplayName(
      "a link to the payment opens that PaymentIntent of that run, at the browser's address")
  void aLinkToThePaymentNamesTheRunAndTheObject() {
    assertThat(screens.payment("shop-1", "pi_1"))
        .isEqualTo(
            SIMULATOR + "/ui/objects?provider=stripe&testRunId=shop-1&providerObjectId=pi_1");
  }

  @Test
  @DisplayName("a link to the timeline is keyed on the PaymentIntent")
  void aLinkToTheTimelineIsKeyedOnThePayment() {
    assertThat(screens.timeline("shop-1", "pi_1"))
        .isEqualTo(SIMULATOR + "/ui/timeline?provider=stripe&testRunId=shop-1&key=pi_1");
  }

  @Test
  @DisplayName("links to the trail and the webhooks are narrowed to the PaymentIntent")
  void linksToTheTrailAndTheWebhooksAreNarrowedToThePayment() {
    assertThat(screens.trailOf("shop-1", "pi_1"))
        .isEqualTo(SIMULATOR + "/ui/audit?provider=stripe&testRunId=shop-1&providerObjectId=pi_1");
    assertThat(screens.webhooks("shop-1", "pi_1"))
        .isEqualTo(
            SIMULATOR + "/ui/webhooks?provider=stripe&testRunId=shop-1&providerObjectId=pi_1");
  }
}
