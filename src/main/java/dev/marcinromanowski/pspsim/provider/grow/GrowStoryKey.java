package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;

final class GrowStoryKey {

  private GrowStoryKey() {}

  private static final String OF_A_PROCESS = "process";

  private static final String OF_A_TRANSACTION = "transaction";

  static String ofProcess(String processId) {
    return StoryKey.inSpace(OF_A_PROCESS, processId);
  }

  static String ofTransactionId(String transactionId) {
    return StoryKey.inSpace(OF_A_TRANSACTION, transactionId);
  }

  static String ofTransaction(GrowTransaction transaction) {
    String processId = transaction.processId();
    return processId == null ? ofTransactionId(transaction.transactionId()) : ofProcess(processId);
  }
}
