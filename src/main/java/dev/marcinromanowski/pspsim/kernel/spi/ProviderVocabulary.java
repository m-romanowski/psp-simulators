package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record ProviderVocabulary(
    ProviderId provider,
    List<ProviderOperation> operations,
    Map<MatchFact, String> factsItNeverCarries,
    FailureCodes failureCodes,
    ProcessingStates processingStates,
    Map<String, FailureCodes> theCodesAnOperationAnswers) {

  private static final String ANY_OPERATION = "a step naming no operation";

  public ProviderVocabulary(
      ProviderId provider,
      List<ProviderOperation> operations,
      Map<MatchFact, String> factsItNeverCarries,
      FailureCodes failureCodes,
      ProcessingStates processingStates) {
    this(provider, operations, factsItNeverCarries, failureCodes, processingStates, Map.of());
  }

  public ProviderVocabulary {
    if (operations.isEmpty()) {
      throw new IllegalArgumentException(
          "a provider that exposes no operation can be asked for nothing, and a scenario written"
              + " against it could never fire");
    }
    Map<String, ProviderOperation> byName = new LinkedHashMap<>();
    for (ProviderOperation operation : operations) {
      if (byName.put(operation.name(), operation) != null) {
        throw new IllegalArgumentException(
            "operation " + operation.name() + " is declared twice by one provider vocabulary");
      }
      for (MatchFact carried : operation.carries()) {
        if (factsItNeverCarries.containsKey(carried)) {
          throw new IllegalArgumentException(
              "operation "
                  + operation.name()
                  + " is declared to carry "
                  + carried.property()
                  + ", which the same vocabulary declares this provider never carries");
        }
      }
    }
    for (Map.Entry<MatchFact, String> absent : factsItNeverCarries.entrySet()) {
      if (absent.getValue().isBlank()) {
        throw new IllegalArgumentException(
            "a provider that refuses "
                + absent.getKey().property()
                + " must say, in its own words, what its contract has instead of it");
      }
    }
    refuseAProcessingStateOnAnOperationThisProviderDoesNotExpose(byName, processingStates);
    for (String operation : theCodesAnOperationAnswers.keySet()) {
      if (!byName.containsKey(operation)) {
        throw new IllegalArgumentException(
            "operation "
                + operation
                + " is declared to answer failure codes of its own, and the same vocabulary"
                + " exposes no operation of that name");
      }
    }
    if (!theCodesAnOperationAnswers.isEmpty()
        && !theCodesAnOperationAnswers.keySet().containsAll(byName.keySet())) {
      List<String> unnamed = new ArrayList<>(byName.keySet());
      unnamed.removeAll(theCodesAnOperationAnswers.keySet());
      throw new IllegalArgumentException(
          "a vocabulary that declares the failure codes of some operations declares them for every"
              + " operation it exposes, so that one it adds later cannot answer the provider-wide"
              + " codes unnoticed; it names none for "
              + String.join(", ", unnamed));
    }
    operations = List.copyOf(operations);
    factsItNeverCarries = Map.copyOf(factsItNeverCarries);
    theCodesAnOperationAnswers = Map.copyOf(theCodesAnOperationAnswers);
  }

  public boolean declaresTheCodesOf(String operation) {
    return theCodesAnOperationAnswers.containsKey(operation);
  }

  public FailureCodes theCodesOf(@Nullable String operation) {
    FailureCodes its = operation == null ? null : theCodesAnOperationAnswers.get(operation);
    return its == null ? failureCodes : its;
  }

  private static void refuseAProcessingStateOnAnOperationThisProviderDoesNotExpose(
      Map<String, ProviderOperation> byName, ProcessingStates processingStates) {
    if (!(processingStates instanceof ProcessingStates.OnTheseOperations named)) {
      return;
    }
    for (String operation : named.operations()) {
      if (!byName.containsKey(operation)) {
        throw new IllegalArgumentException(
            "operation "
                + operation
                + " is declared to answer with a processing state, and the same vocabulary exposes"
                + " no operation of that name");
      }
    }
  }

  public List<String> operationNames() {
    List<String> names = new ArrayList<>();
    for (ProviderOperation operation : operations) {
      names.add(operation.name());
    }
    return List.copyOf(names);
  }

  public List<String> theObjectsAnIdentifierAddresses() {
    List<String> named = new ArrayList<>();
    for (ProviderOperation operation : operations) {
      if (!named.contains(operation.storiesKeyedOn())) {
        named.add(operation.storiesKeyedOn());
      }
    }
    return List.copyOf(named);
  }

  public Optional<ProviderOperation> operation(String name) {
    for (ProviderOperation operation : operations) {
      if (operation.name().equals(name)) {
        return Optional.of(operation);
      }
    }
    return Optional.empty();
  }

  public List<ScenarioRefusal> refusals(NewScenarioRule rule) {
    List<ScenarioRefusal> refused = new ArrayList<>();
    refuseACodeThisProviderNeverAnswers(rule.action(), refused);
    refuseACodeTheNamedOperationNeverAnswers(rule, refused);
    refuseAProcessingStateThisProviderCannotReport(rule, refused);
    refuseAFactThisProviderNeverCarries(rule.match(), refused);
    refuseAnOperationThisProviderDoesNotExpose(rule.match().operation(), refused);
    refuseAFactTheNamedOperationNeverCarries(rule.match(), refused);
    return List.copyOf(refused);
  }

  public List<ScenarioRefusal> refusals(NewScenarioRule rule, List<ScenarioRule> alreadyWritten) {
    return refusals(List.of(rule), alreadyWritten);
  }

  public List<ScenarioRefusal> refusals(
      List<NewScenarioRule> rules, List<ScenarioRule> alreadyWritten) {
    List<ScenarioRefusal> refused = new ArrayList<>();
    for (NewScenarioRule rule : rules) {
      for (ScenarioRefusal refusal : refusals(rule)) {
        if (!refused.contains(refusal)) {
          refused.add(refusal);
        }
      }
    }
    refuseAStoryWhoseStepsCouldNeverSeeEachOther(theStepsOf(rules, alreadyWritten), refused);
    return List.copyOf(refused);
  }

  private static List<StoryStep> theStepsOf(
      List<NewScenarioRule> rules, List<ScenarioRule> alreadyWritten) {
    Set<StoryOfARun> joining = new LinkedHashSet<>();
    for (NewScenarioRule rule : rules) {
      ScenarioStory step = rule.story();
      if (step != null) {
        joining.add(new StoryOfARun(rule.testRunId().value(), step.name()));
      }
    }
    List<StoryStep> steps = new ArrayList<>();
    for (ScenarioRule written : alreadyWritten) {
      ScenarioStory step = written.story();
      if (step != null
          && joining.contains(new StoryOfARun(written.testRunId().value(), step.name()))) {
        steps.add(
            new StoryStep(
                written.testRunId().value(),
                step.name(),
                step.keyedOn(),
                written.match().operation()));
      }
    }
    for (NewScenarioRule rule : rules) {
      ScenarioStory step = rule.story();
      if (step != null) {
        steps.add(
            new StoryStep(
                rule.testRunId().value(), step.name(), step.keyedOn(), rule.match().operation()));
      }
    }
    return List.copyOf(steps);
  }

  private void refuseACodeThisProviderNeverAnswers(
      ScenarioAction action, List<ScenarioRefusal> refused) {
    String named = action.theProviderCodeItComesToRestWith();
    if (named != null && !failureCodes.admits(named)) {
      refused.add(new ScenarioRefusal("action.providerCode", failureCodes.describedAs()));
    }
  }

  private void refuseACodeTheNamedOperationNeverAnswers(
      NewScenarioRule rule, List<ScenarioRefusal> refused) {
    String named = rule.action().theProviderCodeItComesToRestWith();
    String operation = rule.match().operation();
    if (named == null || operation == null || !failureCodes.admits(named)) {
      return;
    }
    FailureCodes its = theCodesOf(operation);
    if (!its.admits(named)) {
      refused.add(
          new ScenarioRefusal(
              "action.providerCode",
              "operation "
                  + operation
                  + " of provider "
                  + provider.value()
                  + " answers no failure code "
                  + named
                  + ": it answers "
                  + its.describedAs()));
    }
  }

  private void refuseAProcessingStateThisProviderCannotReport(
      NewScenarioRule rule, List<ScenarioRefusal> refused) {
    if (!(rule.action() instanceof ScenarioAction.ProcessingThenTerminal)) {
      return;
    }
    if (processingStates instanceof ProcessingStates.NoneOfThem none) {
      refused.add(new ScenarioRefusal("action.type", none.reason()));
      return;
    }
    String named = rule.match().operation();
    if (named != null && !processingStates.reachedBy(named)) {
      refused.add(
          new ScenarioRefusal(
              "match.operation",
              "provider "
                  + provider.value()
                  + " answers with a processing state on "
                  + String.join(", ", theOperationsThatReportAProcessingState())
                  + " and on no other operation of this contract, so a rule that asks "
                  + named
                  + " to report one could never be performed"));
    }
  }

  public List<String> theOperationsThatReportAProcessingState() {
    List<String> named = new ArrayList<>();
    for (ProviderOperation operation : operations) {
      if (processingStates.reachedBy(operation.name())) {
        named.add(operation.name());
      }
    }
    return List.copyOf(named);
  }

  private void refuseAFactThisProviderNeverCarries(
      ScenarioMatch match, List<ScenarioRefusal> refused) {
    for (MatchFact named : MatchFact.namedBy(match)) {
      String instead = factsItNeverCarries.get(named);
      if (instead != null) {
        refused.add(new ScenarioRefusal(named.property(), instead));
      }
    }
  }

  private void refuseAnOperationThisProviderDoesNotExpose(
      @Nullable String named, List<ScenarioRefusal> refused) {
    if (named != null && operation(named).isEmpty()) {
      refused.add(
          new ScenarioRefusal(
              "match.operation",
              "provider "
                  + provider.value()
                  + " exposes "
                  + String.join(", ", operationNames())
                  + " and nothing else on this contract"));
    }
  }

  private void refuseAFactTheNamedOperationNeverCarries(
      ScenarioMatch match, List<ScenarioRefusal> refused) {
    String named = match.operation();
    if (named == null) {
      return;
    }
    Optional<ProviderOperation> exposed = operation(named);
    if (exposed.isEmpty()) {
      return;
    }
    Set<MatchFact> carried = exposed.get().carries();
    for (MatchFact fact : MatchFact.namedBy(match)) {
      if (!carried.contains(fact) && !factsItNeverCarries.containsKey(fact)) {
        refused.add(
            new ScenarioRefusal(
                fact.property(),
                "provider "
                    + provider.value()
                    + " puts no "
                    + fact.property()
                    + " on a "
                    + named
                    + " request, so a rule that names both can never fire"));
      }
    }
  }

  private void refuseAStoryWhoseStepsCouldNeverSeeEachOther(
      List<StoryStep> steps, List<ScenarioRefusal> refused) {
    Map<StoryOfARun, List<StoryStep>> stepsPerStory = new LinkedHashMap<>();
    for (StoryStep step : steps) {
      stepsPerStory
          .computeIfAbsent(
              new StoryOfARun(step.testRun(), step.story()), story -> new ArrayList<>())
          .add(step);
    }
    for (Map.Entry<StoryOfARun, List<StoryStep>> story : stepsPerStory.entrySet()) {
      if (!refuseStepsThatDeclareDifferentKeys(story.getKey(), story.getValue(), refused)) {
        refuseStepsOfOneFlowThatNameDifferentFlows(story.getKey(), story.getValue(), refused);
        refuseStepsOnAKeyTheObjectMayReplace(story.getKey(), story.getValue(), refused);
      }
    }
  }

  private boolean refuseStepsThatDeclareDifferentKeys(
      StoryOfARun story, List<StoryStep> steps, List<ScenarioRefusal> refused) {
    Set<StoryKeying> declared = new LinkedHashSet<>();
    for (StoryStep step : steps) {
      declared.add(step.keyedOn());
    }
    if (declared.size() <= 1) {
      return false;
    }
    List<String> named = new ArrayList<>();
    declared.forEach(keying -> named.add(keying.name()));
    refused.add(
        new ScenarioRefusal(
            "keyedOn",
            "the steps of story "
                + story.story()
                + " declare "
                + String.join(" and ", named)
                + ", and a story keyed on two things stands on two instances, so the steps of"
                + " provider "
                + provider.value()
                + " could never see the state each other leaves"));
    return true;
  }

  private void refuseStepsOfOneFlowThatNameDifferentFlows(
      StoryOfARun story, List<StoryStep> steps, List<ScenarioRefusal> refused) {
    if (steps.get(0).keyedOn() != StoryKeying.FLOW) {
      return;
    }
    Map<String, Set<String>> keysPerStep = new LinkedHashMap<>();
    for (StoryStep step : steps) {
      String named = step.operation();
      if (named == null) {
        keysPerStep.put(ANY_OPERATION, everyKeyThisProviderDeclares());
      } else {
        operation(named)
            .ifPresent(exposed -> keysPerStep.put(named, Set.of(exposed.storiesKeyedOn())));
      }
    }
    Set<String> keys = new LinkedHashSet<>();
    keysPerStep.values().forEach(keys::addAll);
    if (keys.size() > 1) {
      refused.add(
          new ScenarioRefusal(
              "story",
              "provider "
                  + provider.value()
                  + " keys a story about "
                  + spelledOut(keysPerStep)
                  + ", so the steps of story "
                  + story.story()
                  + " could never see the state each other leaves"));
    }
  }

  private void refuseStepsOnAKeyTheObjectMayReplace(
      StoryOfARun story, List<StoryStep> steps, List<ScenarioRefusal> refused) {
    if (steps.get(0).keyedOn() != StoryKeying.FLOW) {
      return;
    }
    Set<String> replaceable = new LinkedHashSet<>();
    for (StoryStep step : steps) {
      String named = step.operation();
      if (named == null) {
        replaceable.addAll(theOperationsWhoseFlowKeyMayBeReplaced());
      } else {
        operation(named)
            .filter(ProviderOperation::itsFlowKeyMayBeReplaced)
            .ifPresent(exposed -> replaceable.add(exposed.name()));
      }
    }
    if (replaceable.isEmpty()) {
      return;
    }
    refused.add(
        new ScenarioRefusal(
            "story",
            "provider "
                + provider.value()
                + " draws the flow key of "
                + String.join(", ", replaceable)
                + " from an identifier the object it addresses may replace, so story "
                + story.story()
                + " would stand on one key until that identifier changed and on another"
                + " afterwards, leaving what its earlier steps decided where no later step can"
                + " read it"));
  }

  private Set<String> theOperationsWhoseFlowKeyMayBeReplaced() {
    Set<String> named = new LinkedHashSet<>();
    for (ProviderOperation operation : operations) {
      if (operation.itsFlowKeyMayBeReplaced()) {
        named.add(operation.name());
      }
    }
    return named;
  }

  private Set<String> everyKeyThisProviderDeclares() {
    Set<String> keys = new LinkedHashSet<>();
    for (ProviderOperation operation : operations) {
      keys.add(operation.storiesKeyedOn());
    }
    return keys;
  }

  private static String spelledOut(Map<String, Set<String>> keysPerStep) {
    List<String> named = new ArrayList<>();
    for (Map.Entry<String, Set<String>> step : keysPerStep.entrySet()) {
      Set<String> keys = step.getValue();
      named.add(
          keys.size() == 1
              ? step.getKey() + " on " + keys.iterator().next()
              : step.getKey() + " on any of " + String.join(", ", keys));
    }
    return String.join(" and ", named);
  }

  private record StoryStep(
      String testRun, String story, StoryKeying keyedOn, @Nullable String operation) {}

  private record StoryOfARun(String testRun, String story) {}
}
