package dev.marcinromanowski.pspsim.kernel.repro;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ReproductionEvidence(
    @Nullable String key,
    @Nullable String foundBy,
    @Nullable String effectiveClock,
    @Nullable RecordedObject object,
    @Nullable List<RecordedExchange> trail,
    @Nullable List<RecordedCallback> callbacks,
    boolean trailTruncated) {

  public ReproductionEvidence {
    trail = trail == null ? null : List.copyOf(trail);
    callbacks = callbacks == null ? null : List.copyOf(callbacks);
  }

  public List<RecordedExchange> carriedTrail() {
    List<RecordedExchange> carried = trail;
    return carried == null ? List.of() : carried;
  }

  public List<RecordedCallback> carriedCallbacks() {
    List<RecordedCallback> carried = callbacks;
    return carried == null ? List.of() : carried;
  }

  public int theExchangesThatSayWhatWasOnTheWire() {
    int counted = 0;
    for (RecordedExchange exchange : carriedTrail()) {
      if (exchange.saysWhatWasOnTheWire()) {
        counted++;
      }
    }
    return counted;
  }

  public int theAttemptsItCarries() {
    int counted = 0;
    for (RecordedCallback callback : carriedCallbacks()) {
      counted += callback.carriedAttempts().size();
    }
    return counted;
  }

  public int theOperationsItCarries() {
    RecordedObject carried = object;
    return carried == null ? 0 : carried.carriedLedger().size();
  }
}
