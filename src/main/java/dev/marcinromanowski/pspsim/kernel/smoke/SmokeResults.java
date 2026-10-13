package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class SmokeResults {

  private final AtomicReference<@Nullable SmokeRun> last = new AtomicReference<>();

  public void hold(SmokeRun run) {
    last.set(run);
  }

  public Optional<SmokeRun> last() {
    return Optional.ofNullable(last.get());
  }
}
