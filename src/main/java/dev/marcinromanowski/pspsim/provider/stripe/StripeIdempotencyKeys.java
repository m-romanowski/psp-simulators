package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
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

  void observed(StripeObservedRequest request);

  String theAnswerTheTrailAlreadyHolds(TestRunId testRunId, String accountId, String key);

  KeyedExchangePage exchanges(KeyedExchangeQuery query);

  int clear(TestRunId testRunId);

  int clearAll();
}
