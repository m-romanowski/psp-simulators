package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

interface TranzilaExchanges {

  void observed(KeyedObservation observation);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  int clear(TestRunId testRunId);

  int clearAll();
}
