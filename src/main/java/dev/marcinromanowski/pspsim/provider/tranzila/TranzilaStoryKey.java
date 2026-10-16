package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;

final class TranzilaStoryKey {

  private static final String OF_A_TRANSACTION = "transaction";

  private TranzilaStoryKey() {}

  static String ofPayment(long originalTransactionId) {
    return StoryKey.inSpace(OF_A_TRANSACTION, Long.toString(originalTransactionId));
  }
}
