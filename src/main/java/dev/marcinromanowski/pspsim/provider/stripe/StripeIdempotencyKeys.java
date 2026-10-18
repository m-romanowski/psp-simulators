package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

interface StripeIdempotencyKeys {

  StripeIdempotencyClaim claim(
      TestRunId testRunId,
      String accountId,
      String key,
      String fingerprint,
      Instant now,
      Instant prunedBefore);

  void completed(TestRunId testRunId, String accountId, String key, StripeStoredAnswer answer);

  void released(TestRunId testRunId, String accountId, String key);

  String fingerprintOf(TestRunId testRunId, FingerprintedRequest request);

  void observed(KeyedObservation observation);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  int clear(TestRunId testRunId);

  int clearAll();
}
