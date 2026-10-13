package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import org.jspecify.annotations.Nullable;

public record PortableScenario(
    @Nullable Integer priority,
    @Nullable String story,
    @Nullable String keyedOn,
    @Nullable String whenState,
    @Nullable String thenState,
    @Nullable PortableMatch match,
    @Nullable DeclaredAction action) {

  public static PortableScenario of(NewScenarioRule rule) {
    ScenarioStory step = rule.story();
    return new PortableScenario(
        rule.priority(),
        step == null ? null : step.name(),
        step == null ? null : step.keyedOn().name(),
        step == null ? null : step.whenState(),
        step == null ? null : step.thenState(),
        PortableMatch.of(rule.match()),
        theActionItWasExpandedWith(rule.action()));
  }

  public DeclaredScenario asDeclared() {
    PortableMatch narrowing = match;
    return new DeclaredScenario(
        null,
        priority,
        story,
        keyedOn,
        whenState,
        thenState,
        narrowing == null ? null : narrowing.asDeclared(),
        action);
  }

  private static DeclaredAction theActionItWasExpandedWith(ScenarioAction action) {
    TransportFault fault = ScenarioGrammar.faultOf(action);
    return new DeclaredAction(
        ScenarioGrammar.typeOf(action),
        null,
        action.theProviderCodeItComesToRestWith(),
        fault == null ? null : fault.kind().name(),
        fault instanceof TransportFault.ServerError answered ? answered.httpStatus() : null,
        null,
        theMillisecondsItIsHeldBackBy(action, fault),
        theMillisecondsItSettlesAfter(action));
  }

  private static @Nullable Long theMillisecondsItIsHeldBackBy(
      ScenarioAction action, @Nullable TransportFault fault) {
    if (action instanceof ScenarioAction.DeliveryDelayed held) {
      return held.held().toMillis();
    }
    return fault instanceof TransportFault.DelayedResponse waited
        ? waited.delay().toMillis()
        : null;
  }

  private static @Nullable Long theMillisecondsItSettlesAfter(ScenarioAction action) {
    return action instanceof ScenarioAction.ProcessingThenTerminal settling
        ? settling.settlesAfter().toMillis()
        : null;
  }
}
