package dev.marcinromanowski.pspsim.kernel.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DeliveryShaping {

  private final ScenarioRules rules;

  public DeliveryShaping(ScenarioRules rules) {
    this.rules = rules;
  }

  public DeliveryShape shapeOf(DeliveryFacts facts) {
    List<ScenarioRule> matching = new ArrayList<>();
    for (ScenarioRule rule : rules.of(facts.provider(), facts.testRunId())) {
      if (rule.action() instanceof ScenarioAction.OnTheDelivery && rule.match().matches(facts)) {
        matching.add(rule);
      }
    }
    Optional<ScenarioRule> selected =
        matching.stream().max(ScenarioRule.THE_ORDER_THE_ENGINE_CONSULTS_THEM);
    if (selected.isEmpty()) {
      return new DeliveryShape.AsScheduled();
    }
    ScenarioRule rule = selected.get();
    return switch ((ScenarioAction.OnTheDelivery) rule.action()) {
      case ScenarioAction.DeliveryDelayed held ->
          new DeliveryShape.HeldBack(rule.scenarioId(), held.held());
      case ScenarioAction.DeliveryDuplicated _ -> new DeliveryShape.Repeated(rule.scenarioId());
      case ScenarioAction.DeliveryDropped _ -> new DeliveryShape.Dropped(rule.scenarioId());
    };
  }
}
