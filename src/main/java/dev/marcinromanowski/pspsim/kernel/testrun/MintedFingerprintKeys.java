package dev.marcinromanowski.pspsim.kernel.testrun;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;

public final class MintedFingerprintKeys implements FingerprintKeys {

  private final FingerprintKeyStore store;
  private final IdGenerator ids;
  private final TestRuns testRuns;

  public MintedFingerprintKeys(FingerprintKeyStore store, IdGenerator ids, TestRuns testRuns) {
    this.store = store;
    this.ids = ids;
    this.testRuns = testRuns;
  }

  @Override
  public FingerprintKey of(TestRunId testRunId) {
    TestRunId kept = TestRunId.storableNamespace(testRunId);
    return store.held(kept).orElseGet(() -> minted(kept));
  }

  private FingerprintKey minted(TestRunId testRunId) {
    FingerprintKey minted = new FingerprintKey(ids.hex(FingerprintKey.HEX_DIGITS));
    return testRuns.exists(testRunId) ? store.keptOr(testRunId, minted) : minted;
  }
}
