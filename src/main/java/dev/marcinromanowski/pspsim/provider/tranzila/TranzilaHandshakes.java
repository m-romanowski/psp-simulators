package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;

interface TranzilaHandshakes {

  int insert(TranzilaHandshake handshake);

  Optional<TranzilaHandshake> find(TestRunId testRunId, String thtk);

  void retainUntil(TestRunId testRunId, String thtk, Instant expiresAt);

  int clear(TestRunId testRunId);

  int clearAll();
}
