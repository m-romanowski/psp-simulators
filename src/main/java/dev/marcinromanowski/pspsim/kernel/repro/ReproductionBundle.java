package dev.marcinromanowski.pspsim.kernel.repro;

import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import org.jspecify.annotations.Nullable;

public record ReproductionBundle(
    @Nullable String testRunId,
    @Nullable String provider,
    @Nullable String capturedAt,
    @Nullable RunBundle setup,
    @Nullable ReproductionEvidence evidence,
    @Nullable ReproductionManifest contents) {

  public static ReproductionBundle of(
      String testRunId,
      String provider,
      String capturedAt,
      RunBundle setup,
      ReproductionEvidence evidence) {
    return new ReproductionBundle(
        testRunId, provider, capturedAt, setup, evidence, ReproductionManifest.of(setup, evidence));
  }
}
