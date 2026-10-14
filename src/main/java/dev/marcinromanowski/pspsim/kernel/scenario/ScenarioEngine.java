package dev.marcinromanowski.pspsim.kernel.scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class ScenarioEngine {

  private final ScenarioRules rules;
  private final OperationCodes codes;

  public ScenarioEngine(ScenarioRules rules) {
    this(rules, OperationCodes.EVERY_CODE);
  }

  public ScenarioEngine(ScenarioRules rules, OperationCodes codes) {
    this.rules = rules;
    this.codes = codes;
  }

  public ScenarioDecision decide(ScenarioRequest request) {
    List<ScenarioRule> matching =
        theRulesThisRequestMatches(
            request, code -> codes.answers(request.provider(), request.operation(), code));
    List<StoryKey> keys = theKeysTheStepsAmongThemStandOn(request, matching);
    return rules.decidingOneRequestAtATimeAbout(
        request.provider(),
        request.testRunId(),
        keys,
        () -> theStepThisRequestIsOwed(request, matching, keys));
  }

  private List<ScenarioRule> theRulesThisRequestMatches(
      ScenarioRequest request, Predicate<String> theCodesItsOperationAnswers) {
    List<ScenarioRule> matching = new ArrayList<>();
    for (ScenarioRule rule : rules.of(request.provider(), request.testRunId())) {
      if (rule.action() instanceof ScenarioAction.OnTheRequest
          && rule.match().matches(request)
          && itsCodeIsOneTheOperationAnswers(rule.action(), theCodesItsOperationAnswers)) {
        matching.add(rule);
      }
    }
    return List.copyOf(matching);
  }

  static boolean itsCodeIsOneTheOperationAnswers(
      ScenarioAction action, Predicate<String> theCodesItsOperationAnswers) {
    String code = action.theProviderCodeItComesToRestWith();
    return code == null || theCodesItsOperationAnswers.test(code);
  }

  private static List<StoryKey> theKeysTheStepsAmongThemStandOn(
      ScenarioRequest request, List<ScenarioRule> matching) {
    Set<StoryKey> keys = new LinkedHashSet<>();
    for (ScenarioRule rule : matching) {
      ScenarioStory step = rule.story();
      if (step != null) {
        keys.add(theKeyOf(request, step));
      }
    }
    return List.copyOf(keys);
  }

  private ScenarioDecision theStepThisRequestIsOwed(
      ScenarioRequest request, List<ScenarioRule> matching, List<StoryKey> keys) {
    Map<StoryKey, Map<String, String>> states = theStatesFacing(request, keys);
    Optional<ScenarioRule> selected =
        matching.stream()
            .filter(rule -> theStoryIsWhereTheStepExpectsIt(request, rule, states))
            .max(ScenarioRule.THE_ORDER_THE_ENGINE_CONSULTS_THEM);
    if (selected.isEmpty()) {
      return new ScenarioDecision.NoScenario();
    }
    ScenarioRule rule = selected.get();
    moveTheStoryOn(request, rule, states);
    return outcomeOf(rule);
  }

  private Map<StoryKey, Map<String, String>> theStatesFacing(
      ScenarioRequest request, List<StoryKey> keys) {
    Map<StoryKey, Map<String, String>> states = new LinkedHashMap<>();
    for (StoryKey key : keys) {
      states.put(key, rules.storyStatesOf(request.provider(), request.testRunId(), key));
    }
    return states;
  }

  private void moveTheStoryOn(
      ScenarioRequest request, ScenarioRule rule, Map<StoryKey, Map<String, String>> states) {
    ScenarioStory step = rule.story();
    if (step == null) {
      return;
    }
    String moved = step.thenState();
    if (moved == null) {
      return;
    }
    boolean took =
        rules.moveStory(
            request.provider(),
            request.testRunId(),
            theKeyOf(request, step),
            step.name(),
            ScenarioStory.stateOrInitial(theStateFacing(request, step, states)),
            moved);
    if (!took) {
      throw new IllegalStateException(
          "story "
              + step.name()
              + " was not where this decision had just read it, although the decision holds the"
              + " only right to move it");
    }
  }

  private static ScenarioDecision outcomeOf(ScenarioRule rule) {
    return switch ((ScenarioAction.OnTheRequest) rule.action()) {
      case ScenarioAction.FixedSuccess _ -> new ScenarioDecision.Proceed(rule.scenarioId());
      case ScenarioAction.TerminalFailure fixed ->
          new ScenarioDecision.Fail(rule.scenarioId(), fixed.providerCode());
      case ScenarioAction.TransportFaultAlways always ->
          new ScenarioDecision.Disrupt(rule.scenarioId(), always.fault());
      case ScenarioAction.TransportFaultInsteadOfOperation instead ->
          new ScenarioDecision.Preempt(rule.scenarioId(), instead.answer());
      case ScenarioAction.ProcessingThenTerminal settling ->
          new ScenarioDecision.Pend(
              rule.scenarioId(), settling.settlesAfter(), settling.providerCode());
    };
  }

  private static boolean theStoryIsWhereTheStepExpectsIt(
      ScenarioRequest request, ScenarioRule rule, Map<StoryKey, Map<String, String>> states) {
    ScenarioStory step = rule.story();
    return step == null || step.matches(theStateFacing(request, step, states));
  }

  private static @Nullable String theStateFacing(
      ScenarioRequest request, ScenarioStory step, Map<StoryKey, Map<String, String>> states) {
    return states.getOrDefault(theKeyOf(request, step), Map.of()).get(step.name());
  }

  private static StoryKey theKeyOf(ScenarioRequest request, ScenarioStory step) {
    return switch (step.keyedOn()) {
      case FLOW -> new StoryKey(StoryKeying.FLOW, request.storyKey());
      case TEST_RUN -> StoryKey.ofTestRun(request.testRunId());
    };
  }
}
