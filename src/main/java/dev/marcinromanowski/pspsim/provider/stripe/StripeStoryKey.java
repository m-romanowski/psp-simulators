package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;

final class StripeStoryKey {

  private static final String OF_A_PAYMENT_INTENT = "payment-intent";

  private static final String OF_A_SETUP_INTENT = "setup-intent";

  private StripeStoryKey() {}

  static String ofIntent(String intentId) {
    return StoryKey.inSpace(OF_A_PAYMENT_INTENT, intentId);
  }

  static String ofSetupIntent(String setupIntentId) {
    return StoryKey.inSpace(OF_A_SETUP_INTENT, setupIntentId);
  }
}
