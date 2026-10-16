package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Supplier;

public interface ScenarioRules {

  ScenarioRule add(NewScenarioRule rule);

  List<ScenarioRule> addAll(List<NewScenarioRule> rules);

  Optional<ScenarioRule> find(ProviderId provider, TestRunId testRunId, String scenarioId);

  List<ScenarioRule> of(ProviderId provider, TestRunId testRunId);

  boolean remove(ProviderId provider, TestRunId testRunId, String scenarioId);

  <T> T decidingOneRequestAtATimeAbout(
      ProviderId provider, TestRunId testRunId, List<StoryKey> keys, Supplier<T> decision);

  SequencedMap<String, String> storyStatesOf(
      ProviderId provider, TestRunId testRunId, StoryKey key);

  boolean moveStory(
      ProviderId provider, TestRunId testRunId, StoryKey key, String story, String from, String to);

  int clear(ProviderId provider, TestRunId testRunId);

  int holdForAReset(ProviderId provider, TestRunId testRunId);

  int holdEveryRunForAReset(ProviderId provider);

  int clearAll(ProviderId provider);
}
