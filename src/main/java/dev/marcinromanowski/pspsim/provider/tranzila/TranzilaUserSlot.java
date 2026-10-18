package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

record TranzilaUserSlot(int fieldId, String name, Instant firstRecordedAt) {

  static final int THE_MOST_SLOTS = 25;

  static final int LOCK_SPACE = "tranzila user-defined slots".hashCode();

  static int lockOf(TestRunId testRunId, String accountId) {
    return (testRunId.value() + '\u0000' + accountId).hashCode();
  }
}
