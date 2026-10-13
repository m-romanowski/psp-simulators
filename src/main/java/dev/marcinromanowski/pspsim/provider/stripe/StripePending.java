package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import org.jspecify.annotations.Nullable;

final class StripePending {

  private StripePending() {}

  static @Nullable StripeDecline theOutcomeItComesToRestWith(ScenarioDecision.Pend pending) {
    String providerCode = pending.providerCode();
    return providerCode == null ? null : StripeDecline.of(providerCode).orElseThrow();
  }
}
