package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

interface WorldlineIdempotenceKeys {

  WorldlineIdempotenceClaim claim(
      TestRunId testRunId,
      String accountId,
      String key,
      String headerName,
      String operation,
      String fingerprint,
      Instant now);

  void completed(TestRunId testRunId, String accountId, String key, WorldlineStoredAnswer answer);

  void released(TestRunId testRunId, String accountId, String key);

  void observed(WorldlineObservedRequest request);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  int clear(TestRunId testRunId);

  int clearAll();
}
