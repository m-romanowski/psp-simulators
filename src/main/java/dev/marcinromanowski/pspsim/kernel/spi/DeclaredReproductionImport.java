package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DeclaredReproductionImport(
    @Nullable String testRunId,
    @Nullable Boolean dryRun,
    @Nullable ReproductionBundle bundle,
    @Nullable Map<String, Map<String, String>> secrets,
    @Nullable Map<String, String> remapProviderIds) {

  public boolean writesNothing() {
    return Boolean.TRUE.equals(dryRun);
  }

  public Map<String, Map<String, String>> theSecretsItSupplies() {
    Map<String, Map<String, String>> supplied = secrets;
    return supplied == null ? Map.of() : supplied;
  }

  public Map<String, String> theRemappingItAsksFor() {
    Map<String, String> asked = remapProviderIds;
    return asked == null ? Map.of() : asked;
  }

  public boolean preservesProviderNativeIds() {
    return theRemappingItAsksFor().isEmpty();
  }
}
