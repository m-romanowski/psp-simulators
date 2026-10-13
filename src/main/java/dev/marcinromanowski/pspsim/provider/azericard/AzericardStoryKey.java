package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;

final class AzericardStoryKey {

  private static final String OF_A_PAYMENT = "payment";

  private static final String AFTER_THE_TERMINALS_LENGTH = "-";

  private static final String THE_LONGEST_ORDER_THE_GATEWAY_ANSWERS =
      "9".repeat(AzericardOrderTrace.LONGEST_ORDER);

  private AzericardStoryKey() {}

  static String ofPayment(String terminalId, String order) {
    return StoryKey.inSpace(
        OF_A_PAYMENT, terminalId.length() + AFTER_THE_TERMINALS_LENGTH + terminalId + order);
  }

  static String theLongestPaymentKeyOf(String terminalId) {
    return ofPayment(terminalId, THE_LONGEST_ORDER_THE_GATEWAY_ANSWERS);
  }
}
