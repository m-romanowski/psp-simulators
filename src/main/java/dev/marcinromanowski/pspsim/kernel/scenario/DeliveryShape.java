package dev.marcinromanowski.pspsim.kernel.scenario;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public sealed interface DeliveryShape {

  @Nullable String scenarioId();

  record AsScheduled() implements DeliveryShape {

    @Override
    public @Nullable String scenarioId() {
      return null;
    }
  }

  record HeldBack(String scenarioId, Duration held) implements DeliveryShape {}

  record Repeated(String scenarioId) implements DeliveryShape {}

  record Dropped(String scenarioId) implements DeliveryShape {}
}
