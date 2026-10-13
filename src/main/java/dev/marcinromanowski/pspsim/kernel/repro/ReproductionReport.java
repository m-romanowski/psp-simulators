package dev.marcinromanowski.pspsim.kernel.repro;

import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import java.util.List;

public record ReproductionReport(
    BundleReport setup,
    List<String> theContentsItWrote,
    List<String> theContentsItOnlyRead,
    List<String> theProviderNativeIdKeysItRemapped,
    boolean providerNativeIdsPreserved) {

  public ReproductionReport {
    theContentsItWrote = List.copyOf(theContentsItWrote);
    theContentsItOnlyRead = List.copyOf(theContentsItOnlyRead);
    theProviderNativeIdKeysItRemapped = List.copyOf(theProviderNativeIdKeysItRemapped);
  }

  public static ReproductionReport of(
      BundleReport setup, ReproductionManifest contents, List<String> remapped) {
    return new ReproductionReport(
        setup,
        contents.theContentNamesAnImportWrites(),
        contents.theContentNamesNoImportWrites(),
        remapped,
        remapped.isEmpty());
  }

  public boolean applied() {
    return setup.applied();
  }
}
