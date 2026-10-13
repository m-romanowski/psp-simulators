package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record DeclaredBundleImport(
    @Nullable String testRunId,
    @Nullable Boolean dryRun,
    @Nullable RunBundle bundle,
    @Nullable Map<String, Map<String, String>> secrets) {

  public boolean writesNothing() {
    Boolean asked = dryRun;
    return Boolean.TRUE.equals(asked);
  }

  public Map<String, Map<String, String>> theSecretsItSupplies() {
    Map<String, Map<String, String>> supplied = secrets;
    return supplied == null ? Map.of() : supplied;
  }
}
