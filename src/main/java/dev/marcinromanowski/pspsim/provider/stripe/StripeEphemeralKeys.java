package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;

interface StripeEphemeralKeys {

  boolean insertIfAbsent(StripeEphemeralKey key);

  List<StripeEphemeralKey> bySecret(String secret);

  void clear(TestRunId testRunId);

  void clearAll();
}
