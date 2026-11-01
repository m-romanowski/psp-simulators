package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class StripeCardOutcomes {

  private StripeCardOutcomes() {}

  static StripeOperationResult decidedBy(
      ScenarioDecision decision,
      @Nullable StripePaymentMethod method,
      String operation,
      Supplier<StripeOperationResult> success,
      Function<StripeDecline, StripeOperationResult> failure) {
    return decidedBy(
        decision,
        method,
        operation,
        success,
        failure,
        pending -> {
          throw pending.theOperationAnswersNoProcessingState();
        });
  }

  static StripeOperationResult decidedBy(
      ScenarioDecision decision,
      @Nullable StripePaymentMethod method,
      String operation,
      Supplier<StripeOperationResult> success,
      Function<StripeDecline, StripeOperationResult> failure,
      Function<ScenarioDecision.Pend, StripeOperationResult> pending) {
    if (decision instanceof ScenarioDecision.NoScenario && method != null) {
      StripeCard card = method.card();
      StripeTestCardBehavior behavior = card == null ? null : card.testBehavior();
      if (behavior == null && card != null) {
        Optional<StripeTestCards.TestCard> published =
            StripeTestCards.byPaymentMethodId(method.theIdARuleNames());
        if (published.isPresent()) {
          behavior = StripeTestCardBehavior.ofNumber(published.get().number());
        }
      }

      StripeDecline decline = behavior == null ? null : behavior.outcomeOf(operation);
      if (decline != null) {
        return failure.apply(decline);
      }
    }
    return StripeOperationResult.decidedBy(decision, success, failure, pending);
  }
}
