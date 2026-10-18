package dev.marcinromanowski.pspsim.kernel.testrun;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;

public final class MintedFingerprintKeys implements FingerprintKeys {

  private final FingerprintKeyStore store;
  private final IdGenerator ids;

  public MintedFingerprintKeys(FingerprintKeyStore store, IdGenerator ids) {
    this.store = store;
    this.ids = ids;
  }

  @Override
  public FingerprintKey of(TestRunId testRunId) {
    TestRunId kept = TestRunId.storableNamespace(testRunId);
    return store
        .held(kept)
        .orElseGet(
            () -> store.keptOr(kept, new FingerprintKey(ids.hex(FingerprintKey.HEX_DIGITS))));
  }
}
