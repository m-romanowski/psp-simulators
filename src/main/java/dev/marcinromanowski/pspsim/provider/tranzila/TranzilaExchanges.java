package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

interface TranzilaExchanges {

  int howOftenSeen(TestRunId testRunId, String accountId, String keyName, String keyValue);

  int observed(TranzilaObservedRequest request);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  int clear(TestRunId testRunId);

  int clearAll();
}
