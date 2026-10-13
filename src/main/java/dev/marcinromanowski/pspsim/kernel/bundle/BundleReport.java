package dev.marcinromanowski.pspsim.kernel.bundle;

import java.util.List;

public record BundleReport(
    String testRunId,
    String provider,
    boolean applied,
    int created,
    int updated,
    int skipped,
    List<BundleEntry> entries,
    List<String> secretsSupplied) {

  public BundleReport {
    entries = List.copyOf(entries);
    secretsSupplied = List.copyOf(secretsSupplied);
  }

  public static BundleReport of(
      String testRunId,
      String provider,
      boolean applied,
      List<BundleEntry> entries,
      List<String> secretsSupplied) {
    return new BundleReport(
        testRunId,
        provider,
        applied,
        howMany(entries, BundleVerdict.CREATED),
        howMany(entries, BundleVerdict.UPDATED),
        howMany(entries, BundleVerdict.SKIPPED),
        entries,
        secretsSupplied);
  }

  private static int howMany(List<BundleEntry> entries, BundleVerdict verdict) {
    int counted = 0;
    for (BundleEntry entry : entries) {
      if (entry.verdict() == verdict) {
        counted++;
      }
    }
    return counted;
  }
}
