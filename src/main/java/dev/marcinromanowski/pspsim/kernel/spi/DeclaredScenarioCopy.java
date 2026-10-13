package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record DeclaredScenarioCopy(@Nullable DeclaredScenario edit, @Nullable List<String> clear) {

  public DeclaredScenarioCopy {
    clear = clear == null ? null : List.copyOf(clear);
  }

  public static DeclaredScenarioCopy asItStands() {
    return new DeclaredScenarioCopy(null, null);
  }

  public List<String> thePropertiesItClears() {
    List<String> cleared = clear;
    return cleared == null ? List.of() : cleared;
  }
}
