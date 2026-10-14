package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

interface TranzilaNonces {

  int claim(TestRunId testRunId, String accountId, String nonce, Instant now, Instant seenBefore);

  int clear(TestRunId testRunId);

  int clearAll();
}
