package dev.marcinromanowski.pspsim.kernel.testrun;

import java.util.Optional;

public interface FingerprintKeyStore {

  Optional<FingerprintKey> held(TestRunId testRunId);

  FingerprintKey keptOr(TestRunId testRunId, FingerprintKey minted);
}
